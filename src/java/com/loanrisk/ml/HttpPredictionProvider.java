package com.loanrisk.ml;

import com.loanrisk.config.MlConfig;
import com.loanrisk.model.LoanApplication;
import com.loanrisk.model.PredictionRecord;
import com.loanrisk.util.JsonUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP client adapter communicating with the remote Python/FastAPI ML inference service.
 * Implements PredictionProvider using Java 21's native java.net.http.HttpClient.
 *
 * NOTE: Never falls back automatically to MockPredictionProvider. A failed HTTP
 * inference call produces a PredictionException to report the failure truthfully.
 */
public class HttpPredictionProvider implements PredictionProvider {

    private static final Logger LOGGER = Logger.getLogger(HttpPredictionProvider.class.getName());

    private final String baseUrl;
    private final String endpointUrl;
    private final int connectTimeoutSeconds;
    private final int requestTimeoutSeconds;
    private final HttpClient httpClient;

    public HttpPredictionProvider() {
        this(MlConfig.getBaseUrl(), MlConfig.getConnectTimeoutSeconds(), MlConfig.getRequestTimeoutSeconds());
    }

    public HttpPredictionProvider(String baseUrl, int connectTimeoutSeconds, int requestTimeoutSeconds) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("baseUrl cannot be null or empty.");
        }
        if (connectTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("connectTimeoutSeconds must be greater than 0, got: " + connectTimeoutSeconds);
        }
        if (requestTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("requestTimeoutSeconds must be greater than 0, got: " + requestTimeoutSeconds);
        }

        this.baseUrl = baseUrl.trim();
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.requestTimeoutSeconds = requestTimeoutSeconds;

        if (this.baseUrl.endsWith("/predict")) {
            this.endpointUrl = this.baseUrl;
        } else if (this.baseUrl.endsWith("/")) {
            this.endpointUrl = this.baseUrl + "predict";
        } else {
            this.endpointUrl = this.baseUrl + "/predict";
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    @Override
    public PredictionRecord predict(LoanApplication application) throws PredictionException {
        if (application == null) {
            throw new PredictionException("Cannot execute prediction: LoanApplication is null.");
        }

        // 1. Serialize ONLY the seven active features matching the retrained v2.0 contract.
        // Demographic fields (INF_MARITAL_STATUS, INF_GENDER) are strictly excluded from transmission.
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("INVESTMENT_TOTAL", application.getInvestmentTotal());
        payloadMap.put("ACCCURRENTBALANCE", application.getAccCurrentBalance());
        payloadMap.put("INSTALL_SIZE", application.getInstallSize());
        payloadMap.put("DUE_PAYMENT", application.getDuePayment());
        payloadMap.put("COMPENSATION_CHARGED", application.getCompensationCharged());
        payloadMap.put("CLIENT_TYPE", application.getClientType());
        payloadMap.put("REPAY_MODE", application.getRepayMode());

        String requestJson = JsonUtil.toJson(payloadMap);

        // 2. Build HTTP request
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();
        } catch (IllegalArgumentException e) {
            throw new PredictionException("Failed to construct HTTP request for URI " + endpointUrl + ": " + e.getMessage(), e);
        }

        // 3. Dispatch HTTP request
        HttpResponse<String> response;
        try {
            LOGGER.log(Level.FINE, "Sending inference request to {0}", endpointUrl);
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpConnectTimeoutException e) {
            throw new PredictionException("Connection timeout after " + connectTimeoutSeconds
                    + "s connecting to ML service at " + endpointUrl, e);
        } catch (HttpTimeoutException e) {
            throw new PredictionException("Request timeout after " + requestTimeoutSeconds
                    + "s awaiting response from ML service at " + endpointUrl, e);
        } catch (IOException e) {
            throw new PredictionException("Network I/O error communicating with ML service at "
                    + endpointUrl + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PredictionException("Inference request was interrupted while communicating with "
                    + endpointUrl, e);
        }

        // 4. Validate HTTP response status
        int statusCode = response.statusCode();
        String responseBody = response.body();

        if (statusCode != 200 && statusCode != 201) {
            throw new PredictionException("ML service returned non-success HTTP status " + statusCode
                    + " from " + endpointUrl + ". Response body: " + responseBody);
        }

        if (responseBody == null || responseBody.trim().isEmpty()) {
            throw new PredictionException("ML service returned an empty response body from " + endpointUrl);
        }

        // 5. Parse and validate JSON response
        Map<String, String> resultMap = JsonUtil.parseFlatJsonObject(responseBody);
        if (resultMap.isEmpty()) {
            throw new PredictionException("Failed to parse ML service response JSON from "
                    + endpointUrl + ": " + responseBody);
        }

        // 6. Validate predicted_class ('G' or 'B')
        String predictedClass = getField(resultMap, "predicted_class", "predictedClass", "class");
        if (predictedClass == null) {
            throw new PredictionException("Missing 'predicted_class' in ML response: " + responseBody);
        }
        String normalizedClass = predictedClass.trim().toUpperCase();
        if (!"G".equals(normalizedClass) && !"B".equals(normalizedClass)) {
            throw new PredictionException("Invalid 'predicted_class' value: '" + predictedClass
                    + "'. Permitted target classes are strictly 'G' or 'B'.");
        }

        // 7. Validate probability_b (numeric, between 0.0 and 1.0)
        String probStr = getField(resultMap, "probability_b", "probabilityB", "prob_b");
        if (probStr == null || probStr.trim().isEmpty()) {
            throw new PredictionException("Missing 'probability_b' in ML response: " + responseBody);
        }
        BigDecimal probabilityB;
        try {
            probabilityB = new BigDecimal(probStr.trim());
        } catch (NumberFormatException e) {
            throw new PredictionException("Malformed numeric 'probability_b': '" + probStr + "'", e);
        }
        if (probabilityB.compareTo(BigDecimal.ZERO) < 0 || probabilityB.compareTo(BigDecimal.ONE) > 0) {
            throw new PredictionException("'probability_b' must be between 0.0 and 1.0, got: " + probabilityB);
        }

        // 8. Validate threshold_used (numeric, between 0.0 and 1.0)
        String threshStr = getField(resultMap, "threshold_used", "thresholdUsed", "threshold");
        if (threshStr == null || threshStr.trim().isEmpty()) {
            throw new PredictionException("Missing 'threshold_used' in ML response: " + responseBody);
        }
        BigDecimal thresholdUsed;
        try {
            thresholdUsed = new BigDecimal(threshStr.trim());
        } catch (NumberFormatException e) {
            throw new PredictionException("Malformed numeric 'threshold_used': '" + threshStr + "'", e);
        }
        if (thresholdUsed.compareTo(BigDecimal.ZERO) < 0 || thresholdUsed.compareTo(BigDecimal.ONE) > 0) {
            throw new PredictionException("'threshold_used' must be between 0.0 and 1.0, got: " + thresholdUsed);
        }

        // 9. Validate model_version
        String modelVersion = getField(resultMap, "model_version", "modelVersion", "version");
        if (modelVersion == null || modelVersion.trim().isEmpty()) {
            throw new PredictionException("Missing 'model_version' in ML response: " + responseBody);
        }

        // 10. Assemble validated PredictionRecord
        PredictionRecord record = new PredictionRecord();
        record.setModelVersion(modelVersion.trim());
        record.setPredictedClass(normalizedClass);
        record.setProbabilityB(probabilityB);
        record.setThresholdUsed(thresholdUsed);
        record.setPredictionTimestamp(new Timestamp(System.currentTimeMillis()));
        record.setSimulated(false); // Genuine adapter call

        return record;
    }

    @Override
    public boolean isSimulated() {
        return false;
    }

    private String getField(Map<String, String> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key);
            }
        }
        return null;
    }
}
