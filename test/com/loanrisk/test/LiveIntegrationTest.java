package com.loanrisk.test;

import com.loanrisk.ml.HttpPredictionProvider;
import com.loanrisk.ml.PredictionException;
import com.loanrisk.model.LoanApplication;
import com.loanrisk.model.PredictionRecord;
import com.loanrisk.repository.memory.InMemoryLoanApplicationRepository;
import com.loanrisk.repository.memory.InMemoryPredictionRecordRepository;
import com.loanrisk.service.ApplicationProcessingResult;
import com.loanrisk.service.LoanApplicationService;

import java.math.BigDecimal;
import java.util.List;

/**
 * End-to-end integration test validating communication between the Java backend
 * (HttpPredictionProvider, LoanApplicationService, Repository) and the live Python FastAPI
 * service running on http://127.0.0.1:8000.
 */
public class LiveIntegrationTest {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("  LOANLENS LIVE JAVA-TO-PYTHON ML INTEGRATION TEST (v2.0)");
        System.out.println("================================================================================");

        String baseUrl = (args.length > 0) ? args[0] : "http://127.0.0.1:8000";
        System.out.println("Target ML Service Base URL: " + baseUrl);

        try {
            HttpPredictionProvider provider = new HttpPredictionProvider(baseUrl, 5, 10);

            // 1. Test Low-Risk Sample
            testLowRiskInference(provider);

            // 2. Test High-Risk Sample
            testHighRiskInference(provider);

            // 3. Test Full Vertical Service Persistence Workflow in HTTP Mode
            testFullWorkflowIntegration(provider);

            // 4. Test Strict Error Handling (No Silent Fallback)
            testUnavailableServiceHandling();

            System.out.println("================================================================================");
            System.out.println("  ALL LIVE JAVA-TO-PYTHON INTEGRATION CHECKS PASSED!");
            System.out.println("================================================================================");
        } catch (Throwable t) {
            System.err.println("FAILED: LiveIntegrationTest - " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void testLowRiskInference(HttpPredictionProvider provider) throws Exception {
        System.out.println("\n[1] Testing Low-Risk Preset Inference via HttpPredictionProvider...");
        LoanApplication app = new LoanApplication();
        app.setInvestmentTotal(new BigDecimal("50000.00"));
        app.setAccCurrentBalance(new BigDecimal("28500.00"));
        app.setInstallSize(new BigDecimal("1200.00"));
        app.setDuePayment(new BigDecimal("0.00"));
        app.setCompensationCharged("None");
        app.setClientType("Urban");
        app.setRepayMode("Normal");

        PredictionRecord record = provider.predict(app);
        System.out.println("    Response: Class=" + record.getPredictedClass()
                + ", Prob(B)=" + record.getProbabilityB()
                + ", Threshold=" + record.getThresholdUsed()
                + ", Model=" + record.getModelVersion()
                + ", Simulated=" + record.isSimulated());

        assertEquals("G", record.getPredictedClass(), "Low risk profile must predict class G");
        assertEquals("v2.0", record.getModelVersion(), "Model version must be v2.0");
        assertEquals(false, record.isSimulated(), "Genuine inference must set simulated=false");
        assertTrue(record.getProbabilityB().compareTo(record.getThresholdUsed()) < 0,
                "Probability B must be below decision threshold");
        System.out.println("    [PASS] testLowRiskInference verified successfully.");
    }

    private static void testHighRiskInference(HttpPredictionProvider provider) throws Exception {
        System.out.println("\n[2] Testing High-Risk Preset Inference via HttpPredictionProvider...");
        LoanApplication app = new LoanApplication();
        app.setInvestmentTotal(new BigDecimal("50000.00"));
        app.setAccCurrentBalance(new BigDecimal("0.00"));
        app.setInstallSize(new BigDecimal("1500.00"));
        app.setDuePayment(new BigDecimal("25000.00"));
        app.setCompensationCharged("Penalty Charged");
        app.setClientType("Rural");
        app.setRepayMode("Installment");

        PredictionRecord record = provider.predict(app);
        System.out.println("    Response: Class=" + record.getPredictedClass()
                + ", Prob(B)=" + record.getProbabilityB()
                + ", Threshold=" + record.getThresholdUsed()
                + ", Model=" + record.getModelVersion()
                + ", Simulated=" + record.isSimulated());

        assertEquals("B", record.getPredictedClass(), "High risk profile must predict class B");
        assertEquals("v2.0", record.getModelVersion(), "Model version must be v2.0");
        assertEquals(false, record.isSimulated(), "Genuine inference must set simulated=false");
        assertTrue(record.getProbabilityB().compareTo(record.getThresholdUsed()) >= 0,
                "Probability B must be at or above decision threshold");
        System.out.println("    [PASS] testHighRiskInference verified successfully.");
    }

    private static void testFullWorkflowIntegration(HttpPredictionProvider provider) throws Exception {
        System.out.println("\n[3] Testing Full Application Service Workflow with HTTP Provider...");
        InMemoryLoanApplicationRepository appRepo = new InMemoryLoanApplicationRepository();
        InMemoryPredictionRecordRepository predRepo = new InMemoryPredictionRecordRepository();
        LoanApplicationService service = new LoanApplicationService(appRepo, predRepo, provider);

        LoanApplication app = new LoanApplication();
        app.setInvestmentTotal(new BigDecimal("65000.00"));
        app.setAccCurrentBalance(new BigDecimal("30000.00"));
        app.setInstallSize(new BigDecimal("0.00")); // Testing zero installment acceptance
        app.setDuePayment(new BigDecimal("0.00"));
        app.setCompensationCharged("None");
        app.setClientType("Semi-urban");
        app.setRepayMode("Normal");

        ApplicationProcessingResult result = service.processApplication(app);
        assertEquals(ApplicationProcessingResult.Status.SUCCESS, result.getStatus(), "Status must be SUCCESS");
        assertTrue(result.getApplicationId() != null && result.getApplicationId() > 0, "applicationId assigned");
        assertTrue(result.getPredictionId() != null && result.getPredictionId() > 0, "predictionId assigned");

        // Verify repository persistence
        LoanApplication persistedApp = appRepo.findById(result.getApplicationId());
        assertTrue(persistedApp != null, "Persisted application found in repository");
        assertEquals(new BigDecimal("0.00"), persistedApp.getInstallSize(), "Zero installment persisted correctly");

        List<PredictionRecord> preds = predRepo.findByApplicationId(result.getApplicationId());
        assertEquals(1, preds.size(), "Exactly 1 prediction record persisted");
        assertEquals("v2.0", preds.get(0).getModelVersion(), "Persisted prediction model version is v2.0");
        assertEquals(false, preds.get(0).isSimulated(), "Persisted prediction is genuine (simulated=false)");
        System.out.println("    [PASS] testFullWorkflowIntegration verified successfully.");
    }

    private static void testUnavailableServiceHandling() {
        System.out.println("\n[4] Testing Service Unavailability & No-Silent-Fallback Guarantees...");
        // Point to a dead/unlistening port
        HttpPredictionProvider deadProvider = new HttpPredictionProvider("http://127.0.0.1:65432", 1, 1);
        InMemoryLoanApplicationRepository appRepo = new InMemoryLoanApplicationRepository();
        InMemoryPredictionRecordRepository predRepo = new InMemoryPredictionRecordRepository();
        LoanApplicationService service = new LoanApplicationService(appRepo, predRepo, deadProvider);

        LoanApplication app = new LoanApplication();
        app.setInvestmentTotal(new BigDecimal("50000.00"));
        app.setAccCurrentBalance(new BigDecimal("20000.00"));
        app.setInstallSize(new BigDecimal("1000.00"));
        app.setDuePayment(new BigDecimal("0.00"));
        app.setCompensationCharged("None");
        app.setClientType("Urban");
        app.setRepayMode("Normal");

        ApplicationProcessingResult result = service.processApplication(app);
        assertEquals(ApplicationProcessingResult.Status.PREDICTION_FAILED, result.getStatus(),
                "Must report PREDICTION_FAILED when Python service is down (NO silent fallback)");
        assertTrue(result.getApplicationId() != null, "Application record was committed");
        assertTrue(result.getPrediction() == null, "Prediction record was NOT generated");
        assertTrue(result.getErrorMessage().contains("connect") || result.getErrorMessage().contains("I/O")
                        || result.getErrorMessage().contains("failed"),
                "Error message truthfully explains connection failure");
        System.out.println("    [PASS] testUnavailableServiceHandling verified successfully.");
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
}
