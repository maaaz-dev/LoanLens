package com.loanrisk.service;

import com.loanrisk.ml.MockPredictionProvider;
import com.loanrisk.ml.PredictionException;
import com.loanrisk.ml.PredictionProvider;
import com.loanrisk.ml.PredictionProviderFactory;
import com.loanrisk.model.LoanApplication;
import com.loanrisk.model.PredictionRecord;
import com.loanrisk.repository.LoanApplicationRepository;
import com.loanrisk.repository.PredictionRecordRepository;
import com.loanrisk.repository.RepositoryFactory;
import com.loanrisk.repository.StorageException;

import java.math.BigDecimal;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service orchestrating application validation, repository persistence (Memory or Oracle),
 * and ML inference via replaceable PredictionProvider.
 */
public class LoanApplicationService {

    private static final Logger LOGGER = Logger.getLogger(LoanApplicationService.class.getName());

    private final LoanApplicationRepository applicationRepository;
    private final PredictionRecordRepository predictionRepository;
    private final PredictionProvider predictionProvider;

    public LoanApplicationService() {
        this(RepositoryFactory.getLoanApplicationRepository(),
             RepositoryFactory.getPredictionRecordRepository(),
             PredictionProviderFactory.getPredictionProvider());
    }

    public LoanApplicationService(LoanApplicationRepository applicationRepository,
                                  PredictionRecordRepository predictionRepository,
                                  PredictionProvider predictionProvider) {
        this.applicationRepository = (applicationRepository != null)
                ? applicationRepository : RepositoryFactory.getLoanApplicationRepository();
        this.predictionRepository = (predictionRepository != null)
                ? predictionRepository : RepositoryFactory.getPredictionRecordRepository();
        this.predictionProvider = (predictionProvider != null)
                ? predictionProvider : new MockPredictionProvider();
    }

    public PredictionProvider getPredictionProvider() {
        return predictionProvider;
    }

    public LoanApplicationRepository getApplicationRepository() {
        return applicationRepository;
    }

    public PredictionRecordRepository getPredictionRepository() {
        return predictionRepository;
    }

    /**
     * Validates the seven required input features according to schema boundaries.
     * Demographic features are optional and ignored for risk scoring.
     * Categorical values are checked for presence and supported length without
     * inventing synthetic allowed-value constraints.
     */
    public ValidationResult validate(LoanApplication app) {
        ValidationResult result = new ValidationResult();

        if (app == null) {
            result.addError("application", "LoanApplication payload cannot be null.");
            return result;
        }

        // 1. INVESTMENT_TOTAL: numeric > 0 (loan principal must be strictly positive)
        if (app.getInvestmentTotal() == null) {
            result.addError("INVESTMENT_TOTAL", "INVESTMENT_TOTAL is required.");
        } else if (app.getInvestmentTotal().compareTo(BigDecimal.ZERO) <= 0) {
            result.addError("INVESTMENT_TOTAL", "INVESTMENT_TOTAL must be greater than zero.");
        }

        // 2. ACCCURRENTBALANCE: numeric, can be negative (overdraft accounts permitted)
        if (app.getAccCurrentBalance() == null) {
            result.addError("ACCCURRENTBALANCE", "ACCCURRENTBALANCE is required.");
        }

        // 3. INSTALL_SIZE: numeric >= 0 (zero installment size is valid for Normal repayment schedule, representing 81.34% of training data)
        if (app.getInstallSize() == null) {
            result.addError("INSTALL_SIZE", "INSTALL_SIZE is required.");
        } else if (app.getInstallSize().compareTo(BigDecimal.ZERO) < 0) {
            result.addError("INSTALL_SIZE", "INSTALL_SIZE cannot be negative.");
        }

        // 4. DUE_PAYMENT: numeric >= 0
        if (app.getDuePayment() == null) {
            result.addError("DUE_PAYMENT", "DUE_PAYMENT is required.");
        } else if (app.getDuePayment().compareTo(BigDecimal.ZERO) < 0) {
            result.addError("DUE_PAYMENT", "DUE_PAYMENT cannot be negative.");
        }

        // 5. COMPENSATION_CHARGED: categorical (VARCHAR2 30)
        if (app.getCompensationCharged() == null || app.getCompensationCharged().trim().isEmpty()) {
            result.addError("COMPENSATION_CHARGED", "COMPENSATION_CHARGED is required.");
        } else if (app.getCompensationCharged().trim().length() > 30) {
            result.addError("COMPENSATION_CHARGED", "COMPENSATION_CHARGED exceeds maximum supported length (30).");
        }

        // 6. CLIENT_TYPE: categorical (VARCHAR2 30)
        if (app.getClientType() == null || app.getClientType().trim().isEmpty()) {
            result.addError("CLIENT_TYPE", "CLIENT_TYPE is required.");
        } else if (app.getClientType().trim().length() > 30) {
            result.addError("CLIENT_TYPE", "CLIENT_TYPE exceeds maximum supported length (30).");
        }

        // 7. REPAY_MODE: categorical (VARCHAR2 30)
        if (app.getRepayMode() == null || app.getRepayMode().trim().isEmpty()) {
            result.addError("REPAY_MODE", "REPAY_MODE is required.");
        } else if (app.getRepayMode().trim().length() > 30) {
            result.addError("REPAY_MODE", "REPAY_MODE exceeds maximum supported length (30).");
        }

        return result;
    }

    /**
     * Executes the complete vertical processing flow:
     * 1. Validates inputs.
     * 2. Persists LoanApplication in configured repository.
     * 3. Dispatches prediction to PredictionProvider (uncoupled from repository transaction).
     * 4. Persists PredictionRecord in configured repository.
     * 5. Returns detailed ApplicationProcessingResult.
     */
    public ApplicationProcessingResult processApplication(LoanApplication app) {
        // Step 1: Input Validation
        ValidationResult validation = validate(app);
        if (validation.hasErrors()) {
            LOGGER.log(Level.WARNING, "Loan application validation failed: {0}", validation.getErrors());
            return ApplicationProcessingResult.validationFailed(validation);
        }

        // Step 2: Persist LoanApplication
        Long applicationId;
        try {
            applicationId = applicationRepository.save(app);
            LOGGER.log(Level.INFO, "Persisted loan application with ID: {0}", applicationId);
        } catch (StorageException e) {
            LOGGER.log(Level.SEVERE, "Failed to persist loan application in repository", e);
            return ApplicationProcessingResult.databaseError("Failed to persist loan application: " + e.getMessage());
        }

        // Step 3: Invoke PredictionProvider outside of any repository transaction
        PredictionRecord prediction;
        try {
            prediction = predictionProvider.predict(app);
            prediction.setApplicationId(applicationId);
        } catch (PredictionException e) {
            LOGGER.log(Level.SEVERE, "Prediction inference failed for applicationId " + applicationId, e);
            return ApplicationProcessingResult.predictionFailed(app,
                    "Application ID " + applicationId + " was committed, but prediction inference failed: " + e.getMessage());
        }

        // Step 4: Persist PredictionRecord
        try {
            Long predictionId = predictionRepository.save(prediction);
            LOGGER.log(Level.INFO, "Persisted prediction record with ID: {0} for application {1}",
                    new Object[]{predictionId, applicationId});
        } catch (StorageException e) {
            LOGGER.log(Level.SEVERE, "Failed to persist prediction record in repository", e);
            return ApplicationProcessingResult.predictionPersistenceFailed(app, prediction,
                    "Application ID " + applicationId + " was committed and prediction was computed, " +
                    "but failed to persist prediction record: " + e.getMessage());
        }

        return ApplicationProcessingResult.success(app, prediction);
    }
}
