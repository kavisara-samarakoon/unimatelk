import { initCsrf, apiFetch } from "./api.js";

const $ = (id) => document.getElementById(id);
const msg = (t) => { $("msg").textContent = t || ""; };

function fill(p) {
    $("sleep").value = p.sleepSchedule ?? "";
    $("clean").value = p.cleanliness ?? "";
    $("noise").value = p.noiseTolerance ?? "";
    $("guests").value = p.guests ?? "";
    $("smoking").value = (p.smokingOk ?? true).toString();
    $("drinking").value = (p.drinkingOk ?? true).toString();
    $("intro").value = p.introvert ?? "";
}

async function load() {
    const p = await apiFetch("/api/preferences/me");
    fill(p);
}

async function save() {
    const payload = {
        sleepSchedule: Number($("sleep").value),
        cleanliness: Number($("clean").value),
        noiseTolerance: Number($("noise").value),
        guests: Number($("guests").value),
        smokingOk: $("smoking").value === "true",
        drinkingOk: $("drinking").value === "true",
        introvert: Number($("intro").value)
    };

    try {
        await apiFetch("/api/preferences/me", { method: "PUT", body: JSON.stringify(payload) });
        msg("✅ Saved!");
    } catch (e) {
        msg("❌ " + e.message);
    }
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();
    await load();
    $("saveBtn").addEventListener("click", () => save());
});