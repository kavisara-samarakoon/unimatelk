// src/main/resources/static/js/api.js

let csrfToken = null;
let csrfHeaderName = null;

export async function initCsrf() {
    const res = await fetch("/api/csrf", {
        credentials: "same-origin",
        cache: "no-store"
    });

    if (!res.ok) {
        throw new Error(`CSRF initialization failed (${res.status})`);
    }

    const data = await res.json();

    if (!data?.token ||
        !["X-CSRF-TOKEN", "X-XSRF-TOKEN"].includes(data.headerName)) {
        throw new Error("Invalid CSRF response");
    }

    csrfToken = data.token;
    csrfHeaderName = data.headerName;

    return { data };
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

    // Require a valid CSRF token for unsafe requests.
    if (!["GET", "HEAD", "OPTIONS", "TRACE"].includes(method)) {
        if (!csrfToken || !csrfHeaderName) {
            await initCsrf();
        }

        headers.set(csrfHeaderName, csrfToken);
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