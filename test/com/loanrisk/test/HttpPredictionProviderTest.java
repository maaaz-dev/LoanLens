package com.loanrisk.test;

import com.loanrisk.ml.HttpPredictionProvider;
import com.loanrisk.ml.PredictionException;
import com.loanrisk.model.LoanApplication;
import com.loanrisk.model.PredictionRecord;
import com.loanrisk.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Automated contract and unit tests for HttpPredictionProvider using an in-process
 * local HTTP stub server. Does not require external Python service or model files.
 */
public class HttpPredictionProviderTest {

    private static HttpServer stubServer;
    private static int stubPort;
    private static final AtomicInteger responseStatusCode = new AtomicInteger(200);
    private static final AtomicReference<String> responseBodyRef = new AtomicReference<>("");
    private static final AtomicReference<String> lastReceivedBody = new AtomicReference<>("");

    public static void runAll() throws Exception {
        System.out.println("--- Running HTTP Prediction Provider Tests ---");
        startStubServer();
        try {
            testSuccessfulRequestAndValidResponse();
            testCorrectSerializationOfAllSevenFeaturesAndExclusionOfDemographics();
            testCategoricalStringsWithSpacesAndSpecialChars();
            testNonSuccessHttpStatus();
            testConnectionRefusal();
            testMalformedJsonAndMissingFields();
            testProbabilityOrThresholdOutOfRange();
            testUnsupportedPredictedClass();
            testPreservationOfModelVersionAndMetadata();
        } finally {
            stopStubServer();
        }
    }

    private static void startStubServer() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress(0), 0);
        stubPort = stubServer.getAddress().getPort();
        stubServer.createContext("/predict", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                InputStream is = exchange.getRequestBody();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int n;
                while ((n = is.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                lastReceivedBody.set(baos.toString(StandardCharsets.UTF_8));

                int code = responseStatusCode.get();
                byte[] respBytes = responseBodyRef.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(code, respBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(respBytes);
                }
            }
        });
        stubServer.setExecutor(null);
        stubServer.start();
    }

    private static void stopStubServer() {
        if (stubServer != null) {
            stubServer.stop(0);
        }
    }

    private static LoanApplication createSampleApplication() {
        LoanApplication app = new LoanApplication();
        app.setInvestmentTotal(new BigDecimal("60000.00"));
        app.setAccCurrentBalance(new BigDecimal("12500.50"));
        app.setInstallSize(new BigDecimal("1500.00"));
        app.setDuePayment(new BigDecimal("0.00"));
        app.setCompensationCharged("None");
        app.setClientType("Urban");
        app.setRepayMode("Normal");
        return app;
    }

    private static void testSuccessfulRequestAndValidResponse() throws Exception {
        responseStatusCode.set(200);
        responseBodyRef.set("{\"predicted_class\":\"G\",\"probability_b\":0.185000,\"threshold_used\":0.4200,\"model_version\":\"lgbm_v2.0\"}");

        HttpPredictionProvider provider = new HttpPredictionProvider("http://localhost:" + stubPort, 2, 2);
        LoanApplication app = createSampleApplication();
        PredictionRecord record = provider.predict(app);

        assertEquals("G", record.getPredictedClass(), "predictedClass must be G");
        assertEquals(new BigDecimal("0.185000"), record.getProbabilityB(), "probabilityB must be 0.185000");
        assertEquals(new BigDecimal("0.4200"), record.getThresholdUsed(), "thresholdUsed must be 0.4200");
        assertEquals("lgbm_v2.0", record.getModelVersion(), "modelVersion must be lgbm_v2.0");
        assertEquals(false, record.isSimulated(), "HTTP provider output must not be flagged as simulated");
        System.out.println("  [PASS] testSuccessfulRequestAndValidResponse");
    }

    private static void testCorrectSerializationOfAllSevenFeaturesAndExclusionOfDemographics() throws Exception {
        responseStatusCode.set(200);
        responseBodyRef.set("{\"predicted_class\":\"B\",\"probability_b\":0.720000,\"threshold_used\":0.4200,\"model_version\":\"lgbm_v2.0\"}");

        HttpPredictionProvider provider = new HttpPredictionProvider("http://localhost:" + stubPort, 2, 2);
        LoanApplication app = createSampleApplication();
        // Even if an app object had demographic values set, HTTP provider MUST NOT serialize them:
        app.setInfMaritalStatus("Married");
        app.setInfGender("F");
        provider.predict(app);

        String received = lastReceivedBody.get();
        Map<String, String> parsed = JsonUtil.parseFlatJsonObject(received);

        assertEquals("60000.00", parsed.get("INVESTMENT_TOTAL"), "INVESTMENT_TOTAL in payload");
        assertEquals("12500.50", parsed.get("ACCCURRENTBALANCE"), "ACCCURRENTBALANCE in payload");
        assertEquals("1500.00", parsed.get("INSTALL_SIZE"), "INSTALL_SIZE in payload");
        assertEquals("0.00", parsed.get("DUE_PAYMENT"), "DUE_PAYMENT in payload");
        assertEquals("None", parsed.get("COMPENSATION_CHARGED"), "COMPENSATION_CHARGED in payload");
        assertEquals("Urban", parsed.get("CLIENT_TYPE"), "CLIENT_TYPE in payload");
        assertEquals("Normal", parsed.get("REPAY_MODE"), "REPAY_MODE in payload");

        // Verify demographic features are strictly absent
        assertTrue(!parsed.containsKey("INF_MARITAL_STATUS"), "INF_MARITAL_STATUS must NOT be serialized in HTTP payload");
        assertTrue(!parsed.containsKey("INF_GENDER"), "INF_GENDER must NOT be serialized in HTTP payload");
        System.out.println("  [PASS] testCorrectSerializationOfAllSevenFeaturesAndExclusionOfDemographics");
    }

    private static void testCategoricalStringsWithSpacesAndSpecialChars() throws Exception {
        responseStatusCode.set(200);
        responseBodyRef.set("{\"predicted_class\":\"G\",\"probability_b\":0.250000,\"threshold_used\":0.4200,\"model_version\":\"lgbm_v2.0\"}");

        HttpPredictionProvider provider = new HttpPredictionProvider("http://localhost:" + stubPort, 2, 2);
        LoanApplication app = createSampleApplication();
        app.setCompensationCharged("Fee Charged: $100 / Waived");
        app.setClientType("Semi-urban Branch 01");
        app.setRepayMode("Installment Mode");

        provider.predict(app);

        String received = lastReceivedBody.get();
        Map<String, String> parsed = JsonUtil.parseFlatJsonObject(received);

        assertEquals("Fee Charged: $100 / Waived", parsed.get("COMPENSATION_CHARGED"), "Special chars in compensation");
        assertEquals("Semi-urban Branch 01", parsed.get("CLIENT_TYPE"), "Special chars in client type");
        assertEquals("Installment Mode", parsed.get("REPAY_MODE"), "Special chars in repay mode");
        System.out.println("  [PASS] testCategoricalStringsWithSpacesAndSpecialChars");
    }

    private static void testNonSuccessHttpStatus() {
        responseStatusCode.set(500);
        responseBodyRef.set("{\"error\":\"Internal model inference failure in Scikit-learn\"}");

        HttpPredictionProvider provider = new HttpPredictionProvider("http://localhost:" + stubPort, 2, 2);
        try {
            provider.predict(createSampleApplication());
            fail("Expected PredictionException on HTTP 500");
        } catch (PredictionException e) {
            assertTrue(e.getMessage().contains("500"), "Exception specifies HTTP 500 status");
        }
        System.out.println("  [PASS] testNonSuccessHttpStatus");
    }

    private static void testConnectionRefusal() {
        // Connect to a dead port that is not listening
        HttpPredictionProvider provider = new HttpPredictionProvider("http://localhost:65431", 1, 1);
        try {
            provider.predict(createSampleApplication());
            fail("Expected PredictionException on connection refusal");
        } catch (PredictionException e) {
            assertTrue(e.getMessage().contains("connect") || e.getMessage().contains("I/O"), "Reports connection refusal");
        }
        System.out.println("  [PASS] testConnectionRefusal");
    }

    private static void testMalformedJsonAndMissingFields() {
        HttpPredictionProvider provider = new HttpPredictionProvider("http://localhost:" + stubPort, 2, 2);

        // Malformed JSON
        responseStatusCode.set(200);
        responseBodyRef.set("{not valid json at all");
        try {
            provider.predict(createSampleApplication());
            fail("Expected PredictionException on malformed JSON");
        } catch (PredictionException e) {
            assertTrue(e.getMessage().contains("parse") || e.getMessage().contains("JSON"), "Reports malformed JSON");
        }

        // Missing predicted_class
        responseBodyRef.set("{\"probability_b\":0.200000,\"threshold_used\":0.4200,\"model_version\":\"lgbm_v2.0\"}");
        try {
            provider.predict(createSampleApplication());
            fail("Expected PredictionException on missing predicted_class");
        } catch (PredictionException e) {
            assertTrue(e.getMessage().contains("predicted_class"), "Reports missing predicted_class");
        }
        System.out.println("  [PASS] testMalformedJsonAndMissingFields");
    }

    private static void testProbabilityOrThresholdOutOfRange() {
        HttpPredictionProvider provider = new HttpPredictionProvider("http://localhost:" + stubPort, 2, 2);

        // Probability > 1.0
        responseStatusCode.set(200);
        responseBodyRef.set("{\"predicted_class\":\"B\",\"probability_b\":1.250000,\"threshold_used\":0.4200,\"model_version\":\"lgbm_v2.0\"}");
        try {
            provider.predict(createSampleApplication());
            fail("Expected PredictionException on probability_b > 1.0");
        } catch (PredictionException e) {
            assertTrue(e.getMessage().contains("between 0.0 and 1.0"), "Reports probability out of bounds");
        }

        // Threshold < 0.0
        responseBodyRef.set("{\"predicted_class\":\"G\",\"probability_b\":0.200000,\"threshold_used\":-0.1000,\"model_version\":\"lgbm_v2.0\"}");
        try {
            provider.predict(createSampleApplication());
            fail("Expected PredictionException on threshold_used < 0.0");
        } catch (PredictionException e) {
            assertTrue(e.getMessage().contains("between 0.0 and 1.0"), "Reports threshold out of bounds");
        }
        System.out.println("  [PASS] testProbabilityOrThresholdOutOfRange");
    }

    private static void testUnsupportedPredictedClass() {
        responseStatusCode.set(200);
        responseBodyRef.set("{\"predicted_class\":\"REJECTED\",\"probability_b\":0.800000,\"threshold_used\":0.4200,\"model_version\":\"lgbm_v2.0\"}");

        HttpPredictionProvider provider = new HttpPredictionProvider("http://localhost:" + stubPort, 2, 2);
        try {
            provider.predict(createSampleApplication());
            fail("Expected PredictionException on unsupported predicted_class 'REJECTED'");
        } catch (PredictionException e) {
            assertTrue(e.getMessage().contains("Permitted target classes are strictly 'G' or 'B'"), "Rejects unverified class labels");
        }
        System.out.println("  [PASS] testUnsupportedPredictedClass");
    }

    private static void testPreservationOfModelVersionAndMetadata() throws Exception {
        responseStatusCode.set(200);
        responseBodyRef.set("{\"predicted_class\":\"G\",\"probability_b\":0.120000,\"threshold_used\":0.4200,\"model_version\":\"custom-lightgbm-v2.0\"}");

        HttpPredictionProvider provider = new HttpPredictionProvider("http://localhost:" + stubPort, 2, 2);
        PredictionRecord record = provider.predict(createSampleApplication());

        assertEquals("custom-lightgbm-v2.0", record.getModelVersion(), "Preserves exact model version string");
        assertEquals(false, record.isSimulated(), "Not simulated");
        System.out.println("  [PASS] testPreservationOfModelVersionAndMetadata");
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("Assertion Failed: " + msg + " [Expected: " + expected + ", Actual: " + actual + "]");
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new AssertionError("Assertion Failed: " + msg);
        }
    }

    private static void fail(String msg) {
        throw new AssertionError("Test Failed: " + msg);
    }
}
