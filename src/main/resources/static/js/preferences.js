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

let lastLoaded = null;
let editMode = false;

function fillForm(p) {
    if (!p) return;
    el("sleepSchedule").value = p.sleepSchedule ?? "";
    el("cleanliness").value = p.cleanliness ?? "";
    el("noiseTolerance").value = p.noiseTolerance ?? "";
    el("guests").value = p.guests ?? "";
    el("smokingOk").value = (p.smokingOk === true) ? "true" : (p.smokingOk === false) ? "false" : "";
    el("drinkingOk").value = (p.drinkingOk === true) ? "true" : (p.drinkingOk === false) ? "false" : "";
    el("introvert").value = p.introvert ?? "";
}

function collectPayload() {
    return {
        sleepSchedule: el("sleepSchedule").value ? numOrNull(el("sleepSchedule").value) : null,
        cleanliness: el("cleanliness").value ? numOrNull(el("cleanliness").value) : null,
        noiseTolerance: el("noiseTolerance").value ? numOrNull(el("noiseTolerance").value) : null,
        guests: el("guests").value ? numOrNull(el("guests").value) : null,
        smokingOk: boolOrNull(el("smokingOk").value),
        drinkingOk: boolOrNull(el("drinkingOk").value),
        introvert: el("introvert").value ? numOrNull(el("introvert").value) : null
    };
}

function setEditMode(on) {
    editMode = on;

    const ids = [
        "sleepSchedule",
        "cleanliness",
        "noiseTolerance",
        "guests",
        "smokingOk",
        "drinkingOk",
        "introvert"
    ];

    ids.forEach((id) => {
        const x = el(id);
        if (!x) return;
        x.disabled = !on;
    });

    if (el("editBtn")) el("editBtn").style.display = on ? "none" : "inline-block";
    if (el("saveBtn")) el("saveBtn").style.display = on ? "inline-block" : "none";
    if (el("cancelBtn")) el("cancelBtn").style.display = on ? "inline-block" : "none";

    setMsg(on ? "Edit mode enabled." : "View mode.", "info");
}

async function load() {
    setMsg("Loading...", "info");
    const p = await apiFetch("/api/my/prefs");
    lastLoaded = p;
    fillForm(p);
    setMsg("Loaded.", "success");
    setEditMode(false);
}

async function save() {
    setMsg("Saving...", "info");
    const payload = collectPayload();
    await apiFetch("/api/my/prefs", { method: "PUT", body: payload });
    await load(); // reload from server for truth
    setMsg("Saved.", "success");
}

function goLogin() {
    window.location.href = "/oauth2/authorization/google";
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();

    // session check first
    try {
        await apiFetch("/api/me");
    } catch (_) {
        goLogin();
        return;
    }

    try {
        await load();
    } catch (e) {
        setMsg(e?.message || "Failed to load preferences", "error");
    }

    if (el("editBtn")) {
        el("editBtn").addEventListener("click", () => setEditMode(true));
    }

    if (el("cancelBtn")) {
        el("cancelBtn").addEventListener("click", () => {
            if (lastLoaded) fillForm(lastLoaded);
            setEditMode(false);
            setMsg("Changes canceled.", "info");
        });
    }

    if (el("saveBtn")) {
        el("saveBtn").addEventListener("click", async () => {
            try {
                await save();
            } catch (e) {
                const msg = String(e?.message || "");
                if (msg.includes("401") || msg.includes("Failed to fetch")) {
                    setMsg("Not logged in. Redirecting...", "error");
                    setTimeout(goLogin, 400);
                    return;
                }
                setMsg(msg || "Save failed", "error");
            }
        });
    }
});