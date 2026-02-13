import { initCsrf, apiFetch } from "./api.js";

const $ = (id) => document.getElementById(id);

function show(text, type = "info") {
    const el = $("msg");
    el.className = "toast " + type;
    el.textContent = text;
}

// Read a number 1–5 from an input
function read1to5(id, label) {
    const v = parseInt($(id).value, 10);
    if (Number.isNaN(v)) throw new Error(`${label} is required (1–5).`);
    if (v < 1 || v > 5) throw new Error(`${label} must be between 1 and 5.`);
    return v;
}

// Load existing preferences from DB and fill the form
function fillForm(p) {
    $("sleep").value = p.sleepSchedule ?? "";
    $("clean").value = p.cleanliness ?? "";
    $("noise").value = p.noiseTolerance ?? "";
    $("guests").value = p.guests ?? "";
    $("intro").value = p.introvert ?? "";
    $("smoking").value = (p.smokingOk ?? true).toString();
    $("drinking").value = (p.drinkingOk ?? true).toString();
}

async function loadPreferences() {
    const p = await apiFetch("/api/preferences/me");
    fillForm(p);
}

async function savePreferences() {
    try {
        // Build JSON payload exactly as backend expects
        const payload = {
            sleepSchedule: read1to5("sleep", "Sleep Schedule"),
            cleanliness: read1to5("clean", "Cleanliness"),
            noiseTolerance: read1to5("noise", "Noise Tolerance"),
            guests: read1to5("guests", "Guest Frequency"),
            introvert: read1to5("intro", "Introvert/Extrovert"),
            smokingOk: $("smoking").value === "true",
            drinkingOk: $("drinking").value === "true"
        };

        await apiFetch("/api/preferences/me", {
            method: "PUT",
            body: JSON.stringify(payload)
        });

        show("✅ Preferences saved successfully!", "success");
    } catch (e) {
        show("❌ " + e.message, "error");
    }
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();        // needed because your backend uses CSRF
    await loadPreferences(); // shows saved values if any
    $("saveBtn").addEventListener("click", savePreferences);
});
