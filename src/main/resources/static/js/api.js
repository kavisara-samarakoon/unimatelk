// src/main/resources/static/js/api.js

let csrfToken = null;

export async function initCsrf() {
    // If /api/csrf exists, we grab token; if not, ignore.
    try {
        const res = await fetch("/api/csrf", { credentials: "include" });
        if (!res.ok) return;
        const data = await res.json();
        csrfToken = data.token || data.csrfToken || data?._csrf?.token || null;
    } catch (_) {
        // ignore
    }
}

export async function apiFetch(url, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const headers = new Headers(options.headers || {});
    let body = options.body;

    const isFormData = typeof FormData !== "undefined" && body instanceof FormData;

    // Auto-JSON encode plain objects
    if (body != null && !isFormData && typeof body === "object" && !(body instanceof Blob)) {
        headers.set("Content-Type", "application/json");
        body = JSON.stringify(body);
    }

    // CSRF for writes if available
    if (method !== "GET" && method !== "HEAD" && csrfToken) {
        headers.set("X-CSRF-TOKEN", csrfToken);
    }

    let res;
    try {
        res = await fetch(url, {
            ...options,
            method,
            headers,
            body,
            credentials: "include" // ✅ REQUIRED for session to persist
        });
    } catch (e) {
        // Network-level error (often happens when API redirects to Google)
        throw new Error("Failed to fetch");
    }

    if (res.status === 204) return null;

    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; }
    catch (_) { data = text; }

    if (!res.ok) {
        const msg = (data && typeof data === "object" && data.message)
            ? data.message
            : `${res.status} ${res.statusText}`;
        throw new Error(msg);
    }

    return data;
}