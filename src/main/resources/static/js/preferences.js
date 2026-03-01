// src/main/resources/static/js/preferences.js
// Robust preferences load/save using window.apiFetch

(function () {
    const GET_PREF_URLS = ["/api/preferences/me", "/api/preferences", "/api/me/preferences"];
    const SAVE_PREF_URLS = ["/api/preferences/me", "/api/preferences", "/api/me/preferences"];

    function el(q) { return document.querySelector(q); }

    function setStatus(msg, isError) {
        let box = el("#prefStatusBox");
        if (!box) {
            box = document.createElement("div");
            box.id = "prefStatusBox";
            box.style.marginTop = "12px";
            box.style.padding = "10px";
            box.style.borderRadius = "10px";
            box.style.fontSize = "14px";
            box.style.background = isError ? "rgba(255,0,0,0.12)" : "rgba(0,255,0,0.10)";
            box.style.border = isError ? "1px solid rgba(255,0,0,0.25)" : "1px solid rgba(0,255,0,0.20)";
            const host = el("form") || document.body;
            host.appendChild(box);
        }
        box.textContent = msg;
    }

    async function tryGet(urls) {
        for (const u of urls) {
            try {
                const res = await window.apiFetch(u, { method: "GET" });
                return res;
            } catch (e) {
                if (String(e.message).includes("404") || String(e.message).includes("405")) continue;
                if (String(e.message).includes("401") || String(e.message).includes("403")) throw e;
            }
        }
        throw new Error("Could not load preferences.");
    }

    async function trySave(urls, payload) {
        const methods = ["PUT", "POST", "PATCH"];
        for (const u of urls) {
            for (const m of methods) {
                try {
                    const res = await window.apiFetch(u, { method: m, body: payload });
                    return res;
                } catch (e) {
                    if (String(e.message).includes("404") || String(e.message).includes("405")) continue;
                    if (String(e.message).includes("401") || String(e.message).includes("403")) throw e;
                    if (String(e.message).includes("400")) throw e;
                }
            }
        }
        throw new Error("Could not save preferences.");
    }

    function formToObject(form) {
        const fd = new FormData(form);
        const obj = {};
        for (const [k, v] of fd.entries()) {
            if (v instanceof File) continue;
            if (!k) continue;
            const val = String(v).trim();
            // try convert numbers automatically
            obj[k] = (val !== "" && !isNaN(val)) ? Number(val) : val;
        }
        return obj;
    }

    function fillForm(form, data) {
        const payload = (data && data.preferences) ? data.preferences : data;
        if (!payload || typeof payload !== "object") return;

        for (const [k, v] of Object.entries(payload)) {
            const field = form.querySelector(`[name="${k}"]`);
            if (!field) continue;
            if (field.type === "checkbox") field.checked = Boolean(v);
            else field.value = (v === null || v === undefined) ? "" : String(v);
        }
    }

    async function init() {
        const form = el("#preferencesForm") || el("form");
        if (!form) {
            console.warn("Preferences form not found. Add id='preferencesForm' to your form or ensure there is a form.");
            return;
        }

        try {
            const data = await tryGet(GET_PREF_URLS);
            fillForm(form, data);
            setStatus("Preferences loaded.", false);
        } catch (e) {
            console.error(e);
            setStatus("Failed to load preferences: " + e.message, true);
        }

        form.addEventListener("submit", async (ev) => {
            ev.preventDefault();
            const payload = formToObject(form);
            try {
                await trySave(SAVE_PREF_URLS, payload);
                setStatus("Preferences saved successfully.", false);
            } catch (e) {
                console.error(e);
                setStatus("Preferences save failed: " + e.message, true);
            }
        });
    }

    document.addEventListener("DOMContentLoaded", init);
})();