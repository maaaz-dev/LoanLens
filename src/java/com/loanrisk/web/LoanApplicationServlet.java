package com.loanrisk.web;

import com.loanrisk.model.LoanApplication;
import com.loanrisk.model.PredictionRecord;
import com.loanrisk.service.ApplicationProcessingResult;
import com.loanrisk.service.LoanApplicationService;
import com.loanrisk.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Endpoint for submitting loan applications, persisting them in repository,
 * invoking risk prediction, and returning structured results.
 */
@WebServlet(name = "LoanApplicationServlet", urlPatterns = {"/api/applications"})
public class LoanApplicationServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(LoanApplicationServlet.class.getName());

    private LoanApplicationService applicationService;

    @Override
    public void init() throws ServletException {
        super.init();
        this.applicationService = new LoanApplicationService();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Map<String, String> params = extractParameters(req);
        LoanApplication app;
        try {
            app = parseApplication(params);
        } catch (NumberFormatException e) {
            sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    createErrorResponse("VALIDATION_FAILED", "Malformed numeric field: " + e.getMessage()));
            return;
        }

        ApplicationProcessingResult result = applicationService.processApplication(app);
        handleResult(resp, result);
    }

    private Map<String, String> extractParameters(HttpServletRequest req) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        String contentType = req.getContentType();

        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            params.putAll(JsonUtil.parseFlatJsonObject(sb.toString()));
        } else {
            // Read form-encoded parameters
            for (String paramName : req.getParameterMap().keySet()) {
                params.put(paramName, req.getParameter(paramName));
            }
        }
        return params;
    }

    private LoanApplication parseApplication(Map<String, String> p) {
        LoanApplication app = new LoanApplication();

        String invTotal = getParam(p, "INVESTMENT_TOTAL", "investmentTotal");
        if (invTotal != null && !invTotal.trim().isEmpty()) {
            app.setInvestmentTotal(new BigDecimal(invTotal.trim()));
        }

        String curBal = getParam(p, "ACCCURRENTBALANCE", "accCurrentBalance");
        if (curBal != null && !curBal.trim().isEmpty()) {
            app.setAccCurrentBalance(new BigDecimal(curBal.trim()));
        }

        // Optional demographic fields (null by default in v2.0)
        app.setInfMaritalStatus(getParam(p, "INF_MARITAL_STATUS", "infMaritalStatus"));
        app.setInfGender(getParam(p, "INF_GENDER", "infGender"));

        String install = getParam(p, "INSTALL_SIZE", "installSize");
        if (install != null && !install.trim().isEmpty()) {
            app.setInstallSize(new BigDecimal(install.trim()));
        }

        String due = getParam(p, "DUE_PAYMENT", "duePayment");
        if (due != null && !due.trim().isEmpty()) {
            app.setDuePayment(new BigDecimal(due.trim()));
        }

        app.setCompensationCharged(getParam(p, "COMPENSATION_CHARGED", "compensationCharged"));
        app.setClientType(getParam(p, "CLIENT_TYPE", "clientType"));
        app.setRepayMode(getParam(p, "REPAY_MODE", "repayMode"));

        return app;
    }

    private String getParam(Map<String, String> map, String key1, String key2) {
        if (map.containsKey(key1)) return map.get(key1);
        if (map.containsKey(key2)) return map.get(key2);
        return null;
    }

    private void handleResult(HttpServletResponse resp, ApplicationProcessingResult result) throws IOException {
        switch (result.getStatus()) {
            case SUCCESS:
                Map<String, Object> success = new LinkedHashMap<>();
                success.put("status", "SUCCESS");
                success.put("applicationId", result.getApplicationId());

                PredictionRecord pred = result.getPrediction();
                if (pred != null) {
                    Map<String, Object> predMap = new LinkedHashMap<>();
                    predMap.put("predictionId", pred.getPredictionId());
                    predMap.put("modelVersion", pred.getModelVersion());
                    predMap.put("predictedClass", pred.getPredictedClass());
                    predMap.put("probabilityB", pred.getProbabilityB());
                    predMap.put("thresholdUsed", pred.getThresholdUsed());
                    predMap.put("timestamp", (pred.getPredictionTimestamp() != null) ? pred.getPredictionTimestamp().toString() : null);
                    predMap.put("simulated", pred.isSimulated());
                    if (pred.isSimulated()) {
                        predMap.put("disclaimer", "Simulated mock prediction for development only. Not a trained model output.");
                    }
                    success.put("prediction", predMap);
                }
                sendJsonResponse(resp, HttpServletResponse.SC_CREATED, success);
                break;

            case VALIDATION_FAILED:
                Map<String, Object> valErr = new LinkedHashMap<>();
                valErr.put("status", "VALIDATION_FAILED");
                valErr.put("message", "Input validation failed. Please check the seven required features.");
                valErr.put("errors", result.getValidationResult().getErrors());
                sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, valErr);
                break;

            case DATABASE_ERROR:
                Map<String, Object> dbErr = new LinkedHashMap<>();
                dbErr.put("status", "DATABASE_ERROR");
                dbErr.put("message", result.getErrorMessage());
                sendJsonResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, dbErr);
                break;

            case PREDICTION_FAILED:
                Map<String, Object> predErr = new LinkedHashMap<>();
                predErr.put("status", "PREDICTION_FAILED");
                predErr.put("applicationId", result.getApplicationId());
                predErr.put("message", result.getErrorMessage());
                sendJsonResponse(resp, HttpServletResponse.SC_BAD_GATEWAY, predErr);
                break;

            case PREDICTION_PERSISTENCE_FAILED:
                Map<String, Object> persistErr = new LinkedHashMap<>();
                persistErr.put("status", "PREDICTION_PERSISTENCE_FAILED");
                persistErr.put("applicationId", result.getApplicationId());
                persistErr.put("message", result.getErrorMessage());
                sendJsonResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, persistErr);
                break;

            default:
                sendJsonResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        createErrorResponse("UNKNOWN_ERROR", "An unexpected state occurred."));
                break;
        }
    }

    private Map<String, Object> createErrorResponse(String status, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", status);
        map.put("message", message);
        return map;
    }

    private void sendJsonResponse(HttpServletResponse resp, int statusCode, Map<String, Object> payload)
            throws IOException {
        resp.setStatus(statusCode);
        try (PrintWriter out = resp.getWriter()) {
            out.print(JsonUtil.toJson(payload));
            out.flush();
        }
    }
}
