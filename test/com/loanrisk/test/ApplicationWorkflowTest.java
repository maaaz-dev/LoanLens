package com.loanrisk.test;

import com.loanrisk.ml.MockPredictionProvider;
import com.loanrisk.ml.PredictionException;
import com.loanrisk.ml.PredictionProvider;
import com.loanrisk.model.LoanApplication;
import com.loanrisk.model.PredictionRecord;
import com.loanrisk.repository.PredictionRecordRepository;
import com.loanrisk.repository.StorageException;
import com.loanrisk.repository.memory.InMemoryLoanApplicationRepository;
import com.loanrisk.repository.memory.InMemoryPredictionRecordRepository;
import com.loanrisk.service.ApplicationProcessingResult;
import com.loanrisk.service.LoanApplicationService;
import com.loanrisk.service.ValidationResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * Automated tests for full application service workflow in memory mode,
 * covering end-to-end processing, validation failures, partial persistence failures,
 * and prediction provider failure handling.
 */
public class ApplicationWorkflowTest {

    public static void runAll() throws Exception {
        System.out.println("--- Running Application Workflow Tests ---");
        testSuccessfulApplicationSubmissionInMemoryMode();
        testApplicationAndPredictionAssociation();
        testInvalidInputRejection();
        testInstallSizeZeroAllowedAndNegativeRejected();
        testPredictionProviderFailureHandling();
        testPredictionPersistenceFailureHandling();
        testDistinctionBetweenSimulatedAndGenuineOutput();
    }

    private static LoanApplication createValidApplication() {
        LoanApplication app = new LoanApplication();
        app.setInvestmentTotal(new BigDecimal("75000.00"));
        app.setAccCurrentBalance(new BigDecimal("18500.00"));
        app.setInstallSize(new BigDecimal("2100.00"));
        app.setDuePayment(new BigDecimal("0.00"));
        app.setCompensationCharged("None");
        app.setClientType("Urban");
        app.setRepayMode("Normal");
        return app;
    }

    private static void testSuccessfulApplicationSubmissionInMemoryMode() {
        InMemoryLoanApplicationRepository appRepo = new InMemoryLoanApplicationRepository();
        InMemoryPredictionRecordRepository predRepo = new InMemoryPredictionRecordRepository();
        LoanApplicationService service = new LoanApplicationService(appRepo, predRepo, new MockPredictionProvider());

        LoanApplication app = createValidApplication();
        ApplicationProcessingResult result = service.processApplication(app);

        assertEquals(ApplicationProcessingResult.Status.SUCCESS, result.getStatus(), "Status must be SUCCESS");
        assertTrue(result.getApplicationId() != null && result.getApplicationId() > 0, "Assigned applicationId > 0");
        assertTrue(result.getPredictionId() != null && result.getPredictionId() > 0, "Assigned predictionId > 0");
        assertEquals("mock-v1-simulated", result.getPrediction().getModelVersion(), "Model version must be mock-v1");
        System.out.println("  [PASS] testSuccessfulApplicationSubmissionInMemoryMode");
    }

    private static void testApplicationAndPredictionAssociation() throws Exception {
        InMemoryLoanApplicationRepository appRepo = new InMemoryLoanApplicationRepository();
        InMemoryPredictionRecordRepository predRepo = new InMemoryPredictionRecordRepository();
        LoanApplicationService service = new LoanApplicationService(appRepo, predRepo, new MockPredictionProvider());

        LoanApplication app = createValidApplication();
        ApplicationProcessingResult result = service.processApplication(app);

        Long appId = result.getApplicationId();
        Long predId = result.getPredictionId();

        // Verify retrieval from repositories
        LoanApplication savedApp = appRepo.findById(appId);
        assertTrue(savedApp != null, "Application found in repo");
        assertEquals(appId, savedApp.getApplicationId(), "Application ID matches");

        List<PredictionRecord> preds = predRepo.findByApplicationId(appId);
        assertEquals(1, preds.size(), "Exactly 1 prediction linked to application");
        assertEquals(predId, preds.get(0).getPredictionId(), "Prediction ID matches");
        assertEquals(appId, preds.get(0).getApplicationId(), "Foreign key applicationId matches");
        System.out.println("  [PASS] testApplicationAndPredictionAssociation");
    }

    private static void testInvalidInputRejection() {
        LoanApplicationService service = new LoanApplicationService(
                new InMemoryLoanApplicationRepository(),
                new InMemoryPredictionRecordRepository(),
                new MockPredictionProvider());

        // Negative investment and due payment, missing client type
        LoanApplication app = createValidApplication();
        app.setInvestmentTotal(new BigDecimal("-1000.00"));
        app.setDuePayment(new BigDecimal("-200.00"));
        app.setClientType(null);

        ValidationResult val = service.validate(app);
        assertTrue(val.hasErrors(), "Validation should detect errors");
        assertTrue(val.getErrors().containsKey("INVESTMENT_TOTAL"), "Errors on INVESTMENT_TOTAL");
        assertTrue(val.getErrors().containsKey("DUE_PAYMENT"), "Errors on DUE_PAYMENT");
        assertTrue(val.getErrors().containsKey("CLIENT_TYPE"), "Errors on CLIENT_TYPE");

        ApplicationProcessingResult result = service.processApplication(app);
        assertEquals(ApplicationProcessingResult.Status.VALIDATION_FAILED, result.getStatus(), "Returns VALIDATION_FAILED");
        System.out.println("  [PASS] testInvalidInputRejection");
    }

    private static void testInstallSizeZeroAllowedAndNegativeRejected() {
        LoanApplicationService service = new LoanApplicationService(
                new InMemoryLoanApplicationRepository(),
                new InMemoryPredictionRecordRepository(),
                new MockPredictionProvider());

        // Zero installment is valid (represents Normal schedule in dataset)
        LoanApplication validZeroApp = createValidApplication();
        validZeroApp.setInstallSize(BigDecimal.ZERO);
        ValidationResult valZero = service.validate(validZeroApp);
        assertTrue(!valZero.hasErrors(), "INSTALL_SIZE = 0 must be accepted as valid");

        // Negative installment must be rejected
        LoanApplication negApp = createValidApplication();
        negApp.setInstallSize(new BigDecimal("-150.00"));
        ValidationResult valNeg = service.validate(negApp);
        assertTrue(valNeg.hasErrors(), "Negative INSTALL_SIZE must produce validation error");
        assertTrue(valNeg.getErrors().containsKey("INSTALL_SIZE"), "Validation error on INSTALL_SIZE");
        System.out.println("  [PASS] testInstallSizeZeroAllowedAndNegativeRejected");
    }

    private static void testPredictionProviderFailureHandling() {
        InMemoryLoanApplicationRepository appRepo = new InMemoryLoanApplicationRepository();
        InMemoryPredictionRecordRepository predRepo = new InMemoryPredictionRecordRepository();

        // Faulty provider that always throws PredictionException
        PredictionProvider failingProvider = new PredictionProvider() {
            @Override
            public PredictionRecord predict(LoanApplication application) throws PredictionException {
                throw new PredictionException("Simulated upstream provider outage");
            }

            @Override
            public boolean isSimulated() {
                return false;
            }
        };

        LoanApplicationService service = new LoanApplicationService(appRepo, predRepo, failingProvider);
        LoanApplication app = createValidApplication();
        ApplicationProcessingResult result = service.processApplication(app);

        assertEquals(ApplicationProcessingResult.Status.PREDICTION_FAILED, result.getStatus(), "Reports PREDICTION_FAILED");
        assertTrue(result.getApplicationId() != null, "Application was committed with an ID");
        assertTrue(result.getErrorMessage().contains("Simulated upstream provider outage"), "Honest error reporting");
        System.out.println("  [PASS] testPredictionProviderFailureHandling");
    }

    private static void testPredictionPersistenceFailureHandling() {
        InMemoryLoanApplicationRepository appRepo = new InMemoryLoanApplicationRepository();

        // Faulty prediction repository that fails on save
        PredictionRecordRepository failingPredRepo = new PredictionRecordRepository() {
            @Override
            public Long save(PredictionRecord record) throws StorageException {
                throw new StorageException("Simulated prediction table storage failure");
            }

            @Override
            public List<PredictionRecord> findByApplicationId(Long applicationId) {
                return List.of();
            }

            @Override
            public void clear() {
            }
        };

        LoanApplicationService service = new LoanApplicationService(appRepo, failingPredRepo, new MockPredictionProvider());
        LoanApplication app = createValidApplication();
        ApplicationProcessingResult result = service.processApplication(app);

        assertEquals(ApplicationProcessingResult.Status.PREDICTION_PERSISTENCE_FAILED, result.getStatus(),
                "Reports PREDICTION_PERSISTENCE_FAILED");
        assertTrue(result.getApplicationId() != null, "Application ID is preserved and reported");
        assertTrue(result.getErrorMessage().contains("Simulated prediction table storage failure"), "Detailed failure explanation");
        System.out.println("  [PASS] testPredictionPersistenceFailureHandling");
    }

    private static void testDistinctionBetweenSimulatedAndGenuineOutput() {
        MockPredictionProvider mock = new MockPredictionProvider();
        assertEquals(true, mock.isSimulated(), "MockPredictionProvider.isSimulated() must be true");

        PredictionRecord mockRec = null;
        try {
            mockRec = mock.predict(createValidApplication());
        } catch (Exception ignored) {}
        assertTrue(mockRec != null && mockRec.isSimulated(), "Mock record flagged as simulated");

        System.out.println("  [PASS] testDistinctionBetweenSimulatedAndGenuineOutput");
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
