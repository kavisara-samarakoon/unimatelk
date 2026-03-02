// unimatelk/src/main/resources/static/js/api.js

function getCookie(name) {
    const match = document.cookie.match(new RegExp("(^|;\\s*)" + name + "=([^;]+)"));
    return match ? decodeURIComponent(match[2]) : null;
}

export function getCsrfToken() {
    return getCookie("XSRF-TOKEN");
}

// Call once per page load. This makes sure Spring sets the CSRF cookie.
export async function initCsrf() {
    try {
        await fetch("/api/csrf", { credentials: "same-origin" });
    } catch (_) {
        // ignore
    }
}

export async function apiFetch(url, options = {}) {
    const opts = { ...options };
    opts.headers = opts.headers ? { ...opts.headers } : {};
    opts.credentials = "same-origin";

    const method = (opts.method || "GET").toUpperCase();
    const csrf = getCsrfToken();

    if (["POST", "PUT", "PATCH", "DELETE"].includes(method) && csrf) {
        opts.headers["X-XSRF-TOKEN"] = csrf;
    }

    // If body is a plain object, send JSON.
    if (opts.body && !(opts.body instanceof FormData) && typeof opts.body === "object") {
        opts.headers["Content-Type"] = "application/json";
        opts.body = JSON.stringify(opts.body);
    }

    // If body is FormData, DO NOT set Content-Type (browser sets boundary).
    const res = await fetch(url, opts);
    const text = await res.text();

    let data = text;
    const ct = (res.headers.get("content-type") || "").toLowerCase();
    if (ct.includes("application/json")) {
        try {
            data = text ? JSON.parse(text) : null;
        } catch (_) {
            data = text;
        }
    }

    if (!res.ok) {
        const msg =
            data && data.message ? data.message :
                typeof data === "string" ? data :
                    JSON.stringify(data);

        const err = new Error(`HTTP ${res.status}: ${msg}`);
        err.status = res.status;
        err.data = data;
        throw err;
    }

    return data;
}

// Optional: keep old global usage working too
window.apiFetch = apiFetch;
window.initCsrf = initCsrf;
window.getCsrfToken = getCsrfToken;