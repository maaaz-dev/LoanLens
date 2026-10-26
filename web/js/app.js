/* ==============================================================================
   Loan Default Risk Prediction - Client Application Logic
   Handles form submission, client validation, double-submission prevention,
   safe DOM result rendering, health/storage reporting, interactive cursor
   spotlights, and accessible scroll reveals.
   ============================================================================== */

document.addEventListener("DOMContentLoaded", function () {
    const loanForm = document.getElementById("loanForm");
    const submitBtn = document.getElementById("submitBtn");
    const submitText = document.getElementById("submitText");
    const submitSpinner = document.getElementById("submitSpinner");
    const resetBtn = document.getElementById("resetBtn");
    const globalAlert = document.getElementById("globalAlert");

    // Result elements
    const resultPlaceholder = document.getElementById("resultPlaceholder");
    const resultBox = document.getElementById("resultBox");
    const resAppId = document.getElementById("resAppId");
    const resPredId = document.getElementById("resPredId");
    const resClass = document.getElementById("resClass");
    const resProb = document.getElementById("resProb");
    const resThreshold = document.getElementById("resThreshold");
    const resModel = document.getElementById("resModel");
    const resTime = document.getElementById("resTime");
    const resProbBar = document.getElementById("resProbBar");

    // Status bar elements
    const storageModeBadge = document.getElementById("storageModeBadge");
    const storageDesc = document.getElementById("storageDesc");
    const mlProviderBadge = document.getElementById("mlProviderBadge");

    // Initialize subsystems
    fetchHealthStatus();
    initSpotlights();
    initScrollReveals();

    // Attach event listeners
    loanForm.addEventListener("submit", handleFormSubmit);
    resetBtn.addEventListener("click", handleReset);

    document.getElementById("btnSampleLow").addEventListener("click", fillSampleLow);
    document.getElementById("btnSampleHigh").addEventListener("click", fillSampleHigh);

    function fetchHealthStatus() {
        fetch("api/health")
            .then(function (res) { return res.json(); })
            .then(function (data) {
                if (data.storage) {
                    var mode = data.storage.mode || "unknown";
                    storageModeBadge.textContent = "STORAGE: " + mode.toUpperCase();
                    storageModeBadge.className = "status-badge " + (mode === "memory" ? "badge-memory" : "badge-oracle");
                    if (mode === "memory") {
                        storageDesc.textContent = "In-Memory Mode (Records disappear on restart)";
                    } else {
                        storageDesc.textContent = "Oracle Mode (" + (data.storage.connectivity || "unverified") + ")";
                    }
                }
                if (data.mlService) {
                    if (data.mlService.simulated) {
                        mlProviderBadge.textContent = "PROVIDER: SIMULATED (MOCK-V1)";
                        mlProviderBadge.className = "status-badge badge-mock";
                    } else {
                        var endpointStr = data.mlService.baseUrl ? "HTTP: " + data.mlService.baseUrl : "HTTP (PYTHON)";
                        mlProviderBadge.textContent = "PROVIDER: " + endpointStr;
                        mlProviderBadge.className = "status-badge badge-oracle";
                    }
                }
            })
            .catch(function (err) {
                console.warn("Could not fetch health status:", err);
                storageModeBadge.textContent = "STORAGE: MEMORY (DEFAULT)";
            });
    }

    function handleFormSubmit(e) {
        e.preventDefault();
        clearValidationErrors();
        hideAlert();

        // 1. Gather inputs (7 active features without unnecessary personal data)
        var payload = {
            INVESTMENT_TOTAL: getValue("investmentTotal"),
            ACCCURRENTBALANCE: getValue("accCurrentBalance"),
            INSTALL_SIZE: getValue("installSize"),
            DUE_PAYMENT: getValue("duePayment"),
            COMPENSATION_CHARGED: getValue("compensationCharged"),
            CLIENT_TYPE: getValue("clientType"),
            REPAY_MODE: getValue("repayMode")
        };

        // 2. Client-side sanity checks
        var clientErrors = validateInputs(payload);
        if (Object.keys(clientErrors).length > 0) {
            displayFieldErrors(clientErrors);
            showAlert("Please correct the highlighted fields before submitting.");
            return;
        }

        // 3. Set loading state to prevent double submissions
        setLoadingState(true);

        // 4. Send request to backend Servlet
        fetch("api/applications", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(payload)
        })
            .then(function (response) {
                return response.json().then(function (data) {
                    return { status: response.status, body: data };
                });
            })
            .then(function (result) {
                setLoadingState(false);
                if (result.status === 201 && result.body.status === "SUCCESS") {
                    renderPredictionResult(result.body);
                } else if (result.status === 400 && result.body.errors) {
                    displayFieldErrors(result.body.errors);
                    showAlert("Validation error: " + (result.body.message || "Invalid input data."));
                } else {
                    var errorMsg = result.body.message || "An error occurred while processing the application.";
                    if (result.body.applicationId) {
                        errorMsg += " (Application ID #" + result.body.applicationId + " was recorded, but prediction step failed).";
                    }
                    showAlert(errorMsg);
                }
            })
            .catch(function (error) {
                setLoadingState(false);
                showAlert("Network communication failure: " + error.message);
            });
    }

    function validateInputs(p) {
        var errors = {};

        if (!p.INVESTMENT_TOTAL || isNaN(p.INVESTMENT_TOTAL) || parseFloat(p.INVESTMENT_TOTAL) <= 0) {
            errors.investmentTotal = "Total investment requested must be a number greater than 0.";
        }
        if (p.ACCCURRENTBALANCE === undefined || p.ACCCURRENTBALANCE === null || p.ACCCURRENTBALANCE === "" || isNaN(p.ACCCURRENTBALANCE)) {
            errors.accCurrentBalance = "Current account balance must be a valid numeric value.";
        }
        if (p.INSTALL_SIZE === undefined || p.INSTALL_SIZE === null || p.INSTALL_SIZE === "" || isNaN(p.INSTALL_SIZE) || parseFloat(p.INSTALL_SIZE) < 0) {
            errors.installSize = "Installment size cannot be negative.";
        }
        if (p.DUE_PAYMENT === undefined || p.DUE_PAYMENT === null || p.DUE_PAYMENT === "" || isNaN(p.DUE_PAYMENT) || parseFloat(p.DUE_PAYMENT) < 0) {
            errors.duePayment = "Due payment cannot be negative.";
        }
        if (!p.COMPENSATION_CHARGED || p.COMPENSATION_CHARGED.trim().length === 0) {
            errors.compensationCharged = "Compensation charged status is required (max 30 chars).";
        }
        if (!p.CLIENT_TYPE || p.CLIENT_TYPE.trim().length === 0) {
            errors.clientType = "Branch area category (client type) is required (max 30 chars).";
        }
        if (!p.REPAY_MODE || p.REPAY_MODE.trim().length === 0) {
            errors.repayMode = "Repayment schedule mode is required (max 30 chars).";
        }

        return errors;
    }

    function renderPredictionResult(data) {
        var pred = data.prediction;
        if (!pred) return;

        resAppId.textContent = "#" + data.applicationId;
        resPredId.textContent = "#" + pred.predictionId;

        // Class badge styling
        resClass.textContent = "CLASS " + pred.predictedClass;
        if (pred.predictedClass === "G") {
            resClass.className = "result-tag tag-class-g";
        } else {
            resClass.className = "result-tag tag-class-b";
        }

        var probNum = parseFloat(pred.probabilityB);
        resProb.textContent = (probNum * 100).toFixed(2) + "% (" + probNum.toFixed(6) + ")";
        if (resThreshold) {
            resThreshold.textContent = parseFloat(pred.thresholdUsed).toFixed(4);
        }
        var resGaugeThreshold = document.getElementById("resGaugeThreshold");
        if (resGaugeThreshold) {
            resGaugeThreshold.textContent = parseFloat(pred.thresholdUsed).toFixed(4);
        }
        resModel.textContent = pred.modelVersion || "mock-v1-simulated";
        resTime.textContent = pred.timestamp || new Date().toISOString();

        // Update probability progress gauge bar with single consistent accent
        if (resProbBar && !isNaN(probNum)) {
            var pct = Math.max(0, Math.min(100, probNum * 100));
            resProbBar.style.width = pct.toFixed(1) + "%";
        }

        // Dynamically position threshold marker based on actual provider threshold
        if (pred.thresholdUsed && !isNaN(parseFloat(pred.thresholdUsed))) {
            var threshPct = Math.max(0, Math.min(100, parseFloat(pred.thresholdUsed) * 100));
            var marker = document.querySelector(".gauge-marker");
            if (marker) {
                marker.style.left = threshPct.toFixed(1) + "%";
                marker.title = "Decision Threshold: " + parseFloat(pred.thresholdUsed).toFixed(4);
            }
        }

        var resInferenceNature = document.getElementById("resInferenceNature");
        var resDisclaimerBox = document.getElementById("resDisclaimerBox");
        if (resInferenceNature) {
            if (pred.simulated) {
                resInferenceNature.textContent = "Simulated / Mock Provider";
                resInferenceNature.style.color = "#8293FF";
            } else {
                resInferenceNature.textContent = "Remote ML Service (FastAPI / Scikit-learn)";
                resInferenceNature.style.color = "#69E4D0";
            }
        }

        // Safe DOM rendering for disclaimer box - 0% innerHTML
        if (resDisclaimerBox) {
            resDisclaimerBox.textContent = "";
            var strongTag = document.createElement("strong");
            var codeTag = document.createElement("code");
            if (pred.simulated) {
                strongTag.textContent = "Demo Notice: ";
                codeTag.textContent = "MockPredictionProvider";
                resDisclaimerBox.appendChild(strongTag);
                resDisclaimerBox.appendChild(document.createTextNode("This output was generated by "));
                resDisclaimerBox.appendChild(codeTag);
                resDisclaimerBox.appendChild(document.createTextNode(" using a simulated heuristic. Class B reflects the experimental dataset label and does not represent an underwriting approval, rejection, or verified legal loan default."));
            } else {
                strongTag.textContent = "Model Notice: ";
                codeTag.textContent = String(pred.modelVersion || "Python");
                resDisclaimerBox.appendChild(strongTag);
                resDisclaimerBox.appendChild(document.createTextNode("Prediction returned by remote ML inference service ("));
                resDisclaimerBox.appendChild(codeTag);
                resDisclaimerBox.appendChild(document.createTextNode("). Class B reflects the experimental dataset label and does not represent an underwriting approval, rejection, or verified legal loan default."));
            }
        }

        resultPlaceholder.style.display = "none";
        resultBox.classList.add("active");
        resultBox.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }

    function setLoadingState(loading) {
        submitBtn.disabled = loading;
        resetBtn.disabled = loading;
        if (loading) {
            submitText.textContent = "Evaluating...";
            submitSpinner.style.display = "inline-block";
        } else {
            submitText.textContent = "Evaluate Application";
            submitSpinner.style.display = "none";
        }
    }

    function getValue(id) {
        var el = document.getElementById(id);
        return el ? el.value.trim() : "";
    }

    function setValue(id, val) {
        var el = document.getElementById(id);
        if (el) el.value = val;
    }

    function displayFieldErrors(errors) {
        // Map backend feature names to form control IDs
        var fieldMap = {
            "INVESTMENT_TOTAL": "investmentTotal",
            "ACCCURRENTBALANCE": "accCurrentBalance",
            "INSTALL_SIZE": "installSize",
            "DUE_PAYMENT": "duePayment",
            "COMPENSATION_CHARGED": "compensationCharged",
            "CLIENT_TYPE": "clientType",
            "REPAY_MODE": "repayMode"
        };

        for (var key in errors) {
            var fieldId = fieldMap[key] || key;
            var input = document.getElementById(fieldId);
            if (input) {
                input.classList.add("is-invalid");
                var feedback = document.getElementById(fieldId + "Feedback");
                if (feedback) {
                    feedback.textContent = errors[key];
                }
            }
        }
    }

    function clearValidationErrors() {
        var invalids = document.querySelectorAll(".form-control.is-invalid");
        invalids.forEach(function (el) {
            el.classList.remove("is-invalid");
        });
    }

    function showAlert(msg) {
        globalAlert.textContent = msg;
        globalAlert.classList.add("active");
    }

    function hideAlert() {
        globalAlert.textContent = "";
        globalAlert.classList.remove("active");
    }

    function handleReset() {
        loanForm.reset();
        clearValidationErrors();
        hideAlert();
        resultBox.classList.remove("active");
        resultPlaceholder.style.display = "flex";
        if (resProbBar) resProbBar.style.width = "0%";
    }

    function fillSampleLow() {
        clearValidationErrors();
        hideAlert();
        setValue("investmentTotal", "50000.00");
        setValue("accCurrentBalance", "28500.00");
        setValue("installSize", "1200.00");
        setValue("duePayment", "0.00");
        setValue("compensationCharged", "None");
        setValue("clientType", "Urban");
        setValue("repayMode", "Normal");
    }

    function fillSampleHigh() {
        clearValidationErrors();
        hideAlert();
        setValue("investmentTotal", "50000.00");
        setValue("accCurrentBalance", "0.00");
        setValue("installSize", "1500.00");
        setValue("duePayment", "25000.00");
        setValue("compensationCharged", "Penalty Charged");
        setValue("clientType", "Rural");
        setValue("repayMode", "Installment");
    }

    /* ==============================================================================
       Interactive Cursor Spotlight Effects
       Bounded to hero and cards; disabled on touch/coarse pointers.
       ============================================================================== */
    function initSpotlights() {
        if (!window.matchMedia("(pointer: fine)").matches) return;

        // Hero spotlight
        var hero = document.getElementById("gbHero");
        if (hero) {
            hero.addEventListener("mousemove", function (e) {
                var rect = hero.getBoundingClientRect();
                hero.style.setProperty("--spot-x", (e.clientX - rect.left) + "px");
                hero.style.setProperty("--spot-y", (e.clientY - rect.top) + "px");
            });
            hero.addEventListener("mouseleave", function () {
                hero.style.removeProperty("--spot-x");
                hero.style.removeProperty("--spot-y");
            });
        }

        // Card spotlight
        var cards = document.querySelectorAll(".spotlight-card");
        cards.forEach(function (card) {
            card.addEventListener("mousemove", function (e) {
                var rect = card.getBoundingClientRect();
                card.style.setProperty("--card-spot-x", (e.clientX - rect.left) + "px");
                card.style.setProperty("--card-spot-y", (e.clientY - rect.top) + "px");
            });
            card.addEventListener("mouseleave", function () {
                card.style.removeProperty("--card-spot-x");
                card.style.removeProperty("--card-spot-y");
            });
        });
    }

    /* ==============================================================================
       Scroll-Reveal Observer (Progressive Enhancement)
       ============================================================================== */
    function initScrollReveals() {
        // Respect prefers-reduced-motion
        if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
            return;
        }

        var revealElements = document.querySelectorAll("[data-reveal]");
        if (!revealElements.length || !("IntersectionObserver" in window)) {
            return;
        }

        // Enable reveal CSS transitions only after observer is ready
        document.documentElement.classList.add("reveal-ready");

        var observer = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (entry.isIntersecting) {
                    entry.target.classList.add("is-revealed");
                    observer.unobserve(entry.target);
                }
            });
        }, {
            threshold: 0.08,
            rootMargin: "0px 0px -30px 0px"
        });

        revealElements.forEach(function (el) {
            observer.observe(el);
        });
    }
});
