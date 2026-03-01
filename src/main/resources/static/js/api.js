// src/main/resources/static/js/api.js
// Global (non-module) helper for fetch + cookies + CSRF

(function () {
    function getCookie(name) {
        const match = document.cookie.match(new RegExp("(^|;\\s*)" + name + "=([^;]+)"));
        return match ? decodeURIComponent(match[2]) : null;
    }

    function getCsrfToken() {
        return getCookie("XSRF-TOKEN");
    }

    async function apiFetch(url, options) {
        const opts = options ? Object.assign({}, options) : {};
        opts.headers = opts.headers ? Object.assign({}, opts.headers) : {};

        // always send session cookies
        opts.credentials = "same-origin";

        // attach CSRF for state changing requests
        const method = (opts.method || "GET").toUpperCase();
        const csrf = getCsrfToken();
        if (["POST", "PUT", "PATCH", "DELETE"].includes(method) && csrf) {
            opts.headers["X-XSRF-TOKEN"] = csrf;
        }

        // JSON for plain objects (not FormData)
        if (opts.body && !(opts.body instanceof FormData) && typeof opts.body === "object") {
            opts.headers["Content-Type"] = "application/json";
            opts.body = JSON.stringify(opts.body);
        }

        const res = await fetch(url, opts);
        const text = await res.text();

        let data = text;
        const ct = (res.headers.get("content-type") || "").toLowerCase();
        if (ct.includes("application/json")) {
            try { data = text ? JSON.parse(text) : null; } catch (e) {}
        }

        if (!res.ok) {
            const msg =
                data && data.message ? data.message :
                    typeof data === "string" ? data :
                        JSON.stringify(data);

            const err = new Error("HTTP " + res.status + ": " + msg);
            err.status = res.status;
            err.data = data;
            throw err;
        }

        return data;
    }

    // expose globally
    window.getCsrfToken = getCsrfToken;
    window.apiFetch = apiFetch;
})();