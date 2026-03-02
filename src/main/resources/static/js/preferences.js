import { apiFetch, initCsrf } from "./api.js";

const el = (id) => document.getElementById(id);

function setMsg(text, type = "") {
    const m = el("msg");
    if (!m) return;
    m.textContent = text || "";
    m.className = "toast " + type;
}

function numOrNull(v) {
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
}

function boolOrNull(v) {
    if (v === "true") return true;
    if (v === "false") return false;
    return null;
}

async function load() {
    const p = await apiFetch("/api/my/prefs");
    el("sleepSchedule").value = p.sleepSchedule ?? "";
    el("cleanliness").value = p.cleanliness ?? "";
    el("noiseTolerance").value = p.noiseTolerance ?? "";
    el("guests").value = p.guests ?? "";
    el("smokingOk").value = (p.smokingOk === true) ? "true" : (p.smokingOk === false) ? "false" : "";
    el("drinkingOk").value = (p.drinkingOk === true) ? "true" : (p.drinkingOk === false) ? "false" : "";
    el("introvert").value = p.introvert ?? "";
}

async function save() {
    await apiFetch("/api/my/prefs", {
        method: "PUT",
        body: {
            sleepSchedule: el("sleepSchedule").value ? numOrNull(el("sleepSchedule").value) : null,
            cleanliness: el("cleanliness").value ? numOrNull(el("cleanliness").value) : null,
            noiseTolerance: el("noiseTolerance").value ? numOrNull(el("noiseTolerance").value) : null,
            guests: el("guests").value ? numOrNull(el("guests").value) : null,
            smokingOk: boolOrNull(el("smokingOk").value),
            drinkingOk: boolOrNull(el("drinkingOk").value),
            introvert: el("introvert").value ? numOrNull(el("introvert").value) : null
        }
    });
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();

    try {
        await apiFetch("/api/me");
    } catch (_) {
        window.location.href = "/oauth2/authorization/google";
        return;
    }

    try {
        setMsg("Loading...", "info");
        await load();
        setMsg("Loaded.", "success");
    } catch (e) {
        setMsg(e?.message || "Failed to load preferences", "error");
    }

    el("saveBtn").addEventListener("click", async () => {
        try {
            setMsg("Saving...", "info");
            await save();
            setMsg("Saved.", "success");
        } catch (e) {
            setMsg(e?.message || "Save failed", "error");
        }
    });
});