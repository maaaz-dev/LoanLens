package com.loanrisk.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Domain model representing a loan application with its ML input features
 * and audit timestamps, mapping to the LOAN_APPLICATION table.
 *
 * NOTE: As of v2.0, demographic features (infMaritalStatus, infGender) are deprecated
 * and optional (nullable) to preserve privacy and remove non-predictive personal attributes.
 * Active ML scoring uses the seven core financial and loan parameters.
 */
public class LoanApplication implements Serializable {

    private static final long serialVersionUID = 2L;

    private Long applicationId;
    private BigDecimal investmentTotal;
    private BigDecimal accCurrentBalance;

    /** @deprecated Demographic feature removed from active prediction in v2.0. Optional/nullable. */
    @Deprecated
    private String infMaritalStatus;

    /** @deprecated Demographic feature removed from active prediction in v2.0. Optional/nullable. */
    @Deprecated
    private String infGender;

    private BigDecimal installSize;
    private BigDecimal duePayment;
    private String compensationCharged;
    private String clientType;
    private String repayMode;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public LoanApplication() {
    }

    /**
     * Active constructor for the 7 core features.
     */
    public LoanApplication(Long applicationId, BigDecimal investmentTotal, BigDecimal accCurrentBalance,
                           BigDecimal installSize, BigDecimal duePayment, String compensationCharged,
                           String clientType, String repayMode, Timestamp createdAt, Timestamp updatedAt) {
        this(applicationId, investmentTotal, accCurrentBalance, null, null, installSize,
             duePayment, compensationCharged, clientType, repayMode, createdAt, updatedAt);
    }

    /**
     * Legacy constructor retaining optional demographic fields for backward compatibility.
     */
    public LoanApplication(Long applicationId, BigDecimal investmentTotal, BigDecimal accCurrentBalance,
                           String infMaritalStatus, String infGender, BigDecimal installSize,
                           BigDecimal duePayment, String compensationCharged, String clientType,
                           String repayMode, Timestamp createdAt, Timestamp updatedAt) {
        this.applicationId = applicationId;
        this.investmentTotal = investmentTotal;
        this.accCurrentBalance = accCurrentBalance;
        this.infMaritalStatus = infMaritalStatus;
        this.infGender = infGender;
        this.installSize = installSize;
        this.duePayment = duePayment;
        this.compensationCharged = compensationCharged;
        this.clientType = clientType;
        this.repayMode = repayMode;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public BigDecimal getInvestmentTotal() {
        return investmentTotal;
    }

    public void setInvestmentTotal(BigDecimal investmentTotal) {
        this.investmentTotal = investmentTotal;
    }

    public BigDecimal getAccCurrentBalance() {
        return accCurrentBalance;
    }

    public void setAccCurrentBalance(BigDecimal accCurrentBalance) {
        this.accCurrentBalance = accCurrentBalance;
    }

    @Deprecated
    public String getInfMaritalStatus() {
        return infMaritalStatus;
    }

    @Deprecated
    public void setInfMaritalStatus(String infMaritalStatus) {
        this.infMaritalStatus = infMaritalStatus;
    }

    @Deprecated
    public String getInfGender() {
        return infGender;
    }

    @Deprecated
    public void setInfGender(String infGender) {
        this.infGender = infGender;
    }

    public BigDecimal getInstallSize() {
        return installSize;
    }

    public void setInstallSize(BigDecimal installSize) {
        this.installSize = installSize;
    }

    public BigDecimal getDuePayment() {
        return duePayment;
    }

    public void setDuePayment(BigDecimal duePayment) {
        this.duePayment = duePayment;
    }

    public String getCompensationCharged() {
        return compensationCharged;
    }

    public void setCompensationCharged(String compensationCharged) {
        this.compensationCharged = compensationCharged;
    }

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public String getRepayMode() {
        return repayMode;
    }

    public void setRepayMode(String repayMode) {
        this.repayMode = repayMode;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "LoanApplication{" +
                "applicationId=" + applicationId +
                ", investmentTotal=" + investmentTotal +
                ", accCurrentBalance=" + accCurrentBalance +
                ", infMaritalStatus='" + infMaritalStatus + '\'' +
                ", infGender='" + infGender + '\'' +
                ", installSize=" + installSize +
                ", duePayment=" + duePayment +
                ", compensationCharged='" + compensationCharged + '\'' +
                ", clientType='" + clientType + '\'' +
                ", repayMode='" + repayMode + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
