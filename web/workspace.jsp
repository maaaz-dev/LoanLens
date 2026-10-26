<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LoanLens — Loan Quality Analytics Workspace</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>

<!-- Ambient Ambient Technical Background Layers -->
<div class="ambient-bg" aria-hidden="true">
    <div class="bg-grid"></div>
    <div class="mesh-layer mesh-indigo"></div>
    <div class="mesh-layer mesh-teal"></div>
    <div class="mesh-layer mesh-ambient"></div>
</div>

<div class="app-shell">

    <!-- Section A: Top Navigation & Operational Status Header -->
    <header class="top-header" data-reveal="down">
        <div class="brand-group">
            <div class="brand-logo" aria-hidden="true">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>
                    <circle cx="12" cy="12" r="3"/>
                </svg>
            </div>
            <div class="brand-text">
                <div class="brand-title"><a href="index.jsp" style="color: inherit; text-decoration: none;">LoanLens</a> <span class="brand-divider">/</span> WORKSPACE</div>
                <div class="brand-subtitle">LOAN QUALITY ANALYTICS &bull; <a href="index.jsp" style="color: var(--accent-primary); text-decoration: none;">&larr; Introduction</a></div>
            </div>
        </div>

        <!-- Runtime Operational Status Indicators -->
        <div class="status-group">
            <div class="status-pill">
                <span class="status-dot dot-storage" aria-hidden="true"></span>
                <span id="storageModeBadge" class="status-badge badge-memory">STORAGE: INITIALIZING...</span>
                <span id="storageDesc" class="status-detail">Checking active persistence mode...</span>
            </div>
            <div class="status-pill">
                <span class="status-dot dot-ml" aria-hidden="true"></span>
                <span id="mlProviderBadge" class="status-badge badge-mock">PROVIDER: SIMULATED (MOCK-V1)</span>
            </div>
        </div>
    </header>

    <!-- Section B: Compact Animated Technical Hero -->
    <section class="gb-hero" id="gbHero">
        <div class="gb-grid" aria-hidden="true"></div>
        <div class="gb-spot" id="gbSpot" aria-hidden="true"></div>
        <div class="gb-fade" aria-hidden="true"></div>

        <div class="gb-content">
            <div class="hero-eyebrow">
                <span class="eyebrow-chip">FINANCIAL ANALYTICS</span>
                <span class="eyebrow-sep">/</span>
                <span>MODEL-ASSISTED EVALUATION</span>
            </div>

            <h1 class="hero-headline">A clearer view of loan evaluation.</h1>

            <p class="hero-subtitle">
                Explore loan application features, inspect a model response, and trace the recorded result in one transparent workflow.
            </p>

            <!-- Architectural sequence indicator -->
            <div class="workflow-steps" aria-label="Application Evaluation Lifecycle">
                <div class="step-item">
                    <span class="step-num">01</span>
                    <span class="step-name">INPUT</span>
                </div>
                <span class="step-arrow" aria-hidden="true">→</span>
                <div class="step-item">
                    <span class="step-num">02</span>
                    <span class="step-name">INFERENCE</span>
                </div>
                <span class="step-arrow" aria-hidden="true">→</span>
                <div class="step-item">
                    <span class="step-num">03</span>
                    <span class="step-name">RECORD</span>
                </div>
            </div>
        </div>
    </section>

    <!-- Persistent Demonstration & Simulation Disclaimer Banner -->
    <div class="disclaimer-banner" role="region" aria-label="Demonstration Notice" data-reveal="up">
        <div class="disclaimer-icon" aria-hidden="true">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/>
                <line x1="12" y1="9" x2="12" y2="13"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
        </div>
        <div class="disclaimer-text">
            <strong>DEMONSTRATION &amp; SIMULATED ENVIRONMENT NOTICE:</strong>
            This system operates in development mode using a simulated heuristic provider (<code>mock-v1-simulated</code>).
            Scores <strong>do not</strong> represent actual credit underwriting approvals or legal default determinations.
            Target class <code>B</code> reflects the training dataset label and is not a verified financial loan default.
        </div>
    </div>

    <!-- Global Alert Notification Area -->
    <div id="globalAlert" class="alert alert-danger" role="alert"></div>

    <!-- Main Interactive Two-Column Analytics Layout -->
    <main class="layout-grid">

        <!-- Column 1: Application Form Card -->
        <section class="card spotlight-card form-card" id="formCard" data-reveal="up">
            <div class="card-glow" aria-hidden="true"></div>
            <div class="card-header-clean">
                <div>
                    <h2 class="card-title">Loan Applicant Evaluation Form</h2>
                    <p class="card-subtitle">Submit the seven required financial and loan parameters to persist the application and receive model prediction output.</p>
                </div>
            </div>

            <!-- Quick-Fill Synthetic Sample Buttons -->
            <div class="quick-fill-bar" aria-label="Demo sample autofill">
                <span class="quick-fill-label">Synthetic Inputs:</span>
                <button type="button" id="btnSampleLow" class="btn-sample">
                    <span class="sample-dot" aria-hidden="true"></span>
                    Synthetic Example 1
                </button>
                <button type="button" id="btnSampleHigh" class="btn-sample">
                    <span class="sample-dot" aria-hidden="true"></span>
                    Synthetic Example 2
                </button>
            </div>

            <form id="loanForm" novalidate>

                <!-- Section 1: Financial & Loan Parameters -->
                <div class="form-section">
                    <div class="form-section-header">
                        <span class="section-badge">01</span>
                        <span class="form-section-title">Financial &amp; Loan Parameters</span>
                    </div>

                    <div class="form-grid form-grid-2">
                        <div class="form-group">
                            <label for="investmentTotal">Total Investment Requested <span class="req">*</span></label>
                            <span class="field-hint">Feature: <code>INVESTMENT_TOTAL</code> (Numeric &gt; 0)</span>
                            <div class="input-wrapper">
                                <span class="input-affix">$</span>
                                <input type="number" id="investmentTotal" class="form-control" placeholder="50000.00" step="0.01" min="0.01" required>
                            </div>
                            <div id="investmentTotalFeedback" class="invalid-feedback">Please enter a valid amount greater than 0.</div>
                        </div>

                        <div class="form-group">
                            <label for="accCurrentBalance">Current Account Balance <span class="req">*</span></label>
                            <span class="field-hint">Feature: <code>ACCCURRENTBALANCE</code> (May be negative)</span>
                            <div class="input-wrapper">
                                <span class="input-affix">$</span>
                                <input type="number" id="accCurrentBalance" class="form-control" placeholder="15000.00" step="0.01" required>
                            </div>
                            <div id="accCurrentBalanceFeedback" class="invalid-feedback">Please enter a valid numeric balance.</div>
                        </div>

                        <div class="form-group">
                            <label for="installSize">Installment Size <span class="req">*</span></label>
                            <span class="field-hint">Feature: <code>INSTALL_SIZE</code> (Numeric &ge; 0)</span>
                            <div class="input-wrapper">
                                <span class="input-affix">$</span>
                                <input type="number" id="installSize" class="form-control" placeholder="1200.00" step="0.01" min="0" required>
                            </div>
                            <div id="installSizeFeedback" class="invalid-feedback">Please enter a valid non-negative installment size.</div>
                        </div>

                        <div class="form-group">
                            <label for="duePayment">Due Payment Amount <span class="req">*</span></label>
                            <span class="field-hint">Feature: <code>DUE_PAYMENT</code> (Numeric &ge; 0)</span>
                            <div class="input-wrapper">
                                <span class="input-affix">$</span>
                                <input type="number" id="duePayment" class="form-control" placeholder="0.00" step="0.01" min="0" required>
                            </div>
                            <div id="duePaymentFeedback" class="invalid-feedback">Please enter a valid non-negative amount.</div>
                        </div>
                    </div>
                </div>

                <!-- Section 2: Loan & Branch Parameters -->
                <div class="form-section">
                    <div class="form-section-header">
                        <span class="section-badge">02</span>
                        <span class="form-section-title">Loan &amp; Branch Parameters</span>
                    </div>
                    <p class="section-note">
                        * Categorical values are preserved as raw strings to match model training without synthetic encoding discrepancies.
                    </p>

                    <div class="form-grid form-grid-2">
                        <div class="form-group">
                            <label for="compensationCharged">Compensation Charged <span class="req">*</span></label>
                            <span class="field-hint">Feature: <code>COMPENSATION_CHARGED</code> (e.g. None, Penalty Charged)</span>
                            <input type="text" id="compensationCharged" class="form-control" placeholder="e.g. None" maxlength="30" required>
                            <div id="compensationChargedFeedback" class="invalid-feedback">Compensation status is required.</div>
                        </div>

                        <div class="form-group">
                            <label for="clientType">Branch Area Category (Client Type) <span class="req">*</span></label>
                            <span class="field-hint">Feature: <code>CLIENT_TYPE</code> (Rural, Semi-urban, Urban)</span>
                            <input type="text" id="clientType" class="form-control" placeholder="e.g. Urban, Semi-urban, Rural" maxlength="30" required>
                            <div id="clientTypeFeedback" class="invalid-feedback">Branch area category is required.</div>
                        </div>

                        <div class="form-group full-span">
                            <label for="repayMode">Repayment Schedule Mode <span class="req">*</span></label>
                            <span class="field-hint">Feature: <code>REPAY_MODE</code> (Normal, Installment)</span>
                            <input type="text" id="repayMode" class="form-control" placeholder="e.g. Normal, Installment" maxlength="30" required>
                            <div id="repayModeFeedback" class="invalid-feedback">Repayment schedule mode is required.</div>
                        </div>
                    </div>
                </div>

                <!-- Form Action Controls -->
                <div class="form-actions">
                    <button type="submit" id="submitBtn" class="btn btn-primary">
                        <span id="submitSpinner" class="spinner" style="display: none;" aria-hidden="true"></span>
                        <span id="submitText">Evaluate Application</span>
                        <svg class="btn-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <line x1="5" y1="12" x2="19" y2="12"></line>
                            <polyline points="12 5 19 12 12 19"></polyline>
                        </svg>
                    </button>
                    <button type="button" id="resetBtn" class="btn btn-secondary">
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <path d="M3 12a9 9 0 109-9 9.75 9.75 0 00-6.74 2.74L3 8"/>
                            <path d="M3 3v5h5"/>
                        </svg>
                        Reset Form
                    </button>
                </div>

            </form>
        </section>

        <!-- Column 2: Prediction Results Panel -->
        <section class="card spotlight-card result-card" id="resultCard" data-reveal="up" data-reveal-stagger="1">
            <div class="card-glow" aria-hidden="true"></div>
            <div class="card-header-clean">
                <div>
                    <h2 class="card-title">Prediction &amp; Model Output</h2>
                    <p class="card-subtitle">Real-time response returned from the backend application workflow.</p>
                </div>
            </div>

            <!-- Empty / Standby Placeholder State -->
            <div id="resultPlaceholder" class="result-placeholder">
                <div class="placeholder-icon-wrap" aria-hidden="true">
                    <svg width="44" height="44" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.3">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
                    </svg>
                </div>
                <div class="placeholder-title">No evaluation submitted yet</div>
                <div class="placeholder-desc">
                    Enter the applicant details on the left and click <strong>Evaluate Application</strong> to generate an application record and request model prediction.
                </div>
            </div>

            <!-- Active Populated Result Container -->
            <div id="resultBox" class="result-box">

                <!-- Header Outcome Badge and Record Status -->
                <div class="result-banner-header">
                    <div>
                        <span class="outcome-label">PREDICTED CLASS</span>
                        <div id="resClass" class="result-tag tag-class-g">CLASS G</div>
                    </div>
                    <div class="record-status-wrap">
                        <span class="outcome-label">WORKFLOW STATUS</span>
                        <div class="record-status-pill">
                            <span class="status-dot dot-success" aria-hidden="true"></span>
                            RECORDED
                        </div>
                    </div>
                </div>

                <!-- Probability Visual Progress Gauge -->
                <div class="prob-gauge-container">
                    <div class="prob-gauge-labels">
                        <span class="gauge-caption">Model-Reported Class B Probability</span>
                        <span class="gauge-threshold">Threshold: <strong id="resGaugeThreshold">0.4200</strong></span>
                    </div>
                    <div class="gauge-track" aria-hidden="true">
                        <div id="resProbBar" class="gauge-bar" style="width: 0%;"></div>
                        <div class="gauge-marker" style="left: 42%;" title="Decision Threshold: 0.4200"></div>
                    </div>
                    <div class="gauge-note">Model-reported probability for Class B. This does not represent a verified financial default probability.</div>
                </div>

                <!-- 4 High-Contrast Analytics Metric Tiles -->
                <div class="stat-grid">
                    <div class="stat-item">
                        <div class="stat-label">Application ID</div>
                        <div id="resAppId" class="stat-value">#1001</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-label">Prediction ID</div>
                        <div id="resPredId" class="stat-value">#5001</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-label">Class B Probability</div>
                        <div id="resProb" class="stat-value">20.00%</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-label">Decision Threshold</div>
                        <div id="resThreshold" class="stat-value">0.4200</div>
                    </div>
                </div>

                <!-- Metadata Specification Table -->
                <div class="meta-section">
                    <div class="meta-row">
                        <span class="meta-label">Model Version:</span>
                        <span id="resModel" class="meta-val">mock-v1-simulated</span>
                    </div>
                    <div class="meta-row">
                        <span class="meta-label">Evaluation Timestamp:</span>
                        <span id="resTime" class="meta-val">2026-10-16 04:00:00</span>
                    </div>
                    <div class="meta-row">
                        <span class="meta-label">Inference Nature:</span>
                        <span class="meta-val"><span id="resInferenceNature" class="nature-chip">Simulated / Mock Provider</span></span>
                    </div>
                </div>

                <!-- Dynamic Disclaimer Notification Box -->
                <div id="resDisclaimerBox" class="result-disclaimer">
                    <strong>Demo Notice:</strong> This output was generated by <code>MockPredictionProvider</code> using a simulated heuristic. Class B reflects the experimental dataset label and does not represent an underwriting approval, rejection, or verified legal loan default.
                </div>

            </div>
        </section>

    </main>

    <!-- Clean Technical Footer -->
    <footer class="app-footer" data-reveal="up">
        <div class="footer-tech-stack">
            <span>Java 21 LTS</span>
            <span class="footer-sep">•</span>
            <span>Apache Tomcat 10.1</span>
            <span class="footer-sep">•</span>
            <span>Dual Storage (In-Memory / Oracle XE)</span>
            <span class="footer-sep">•</span>
            <span>Modular ML Provider</span>
        </div>
        <div class="footer-copy">
            Loan Risk Intelligence Workspace — Developed with strict separation of concerns &amp; zero external UI dependencies.
        </div>
    </footer>

</div>

<script src="js/app.js"></script>
</body>
</html>
