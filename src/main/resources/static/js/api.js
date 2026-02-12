let csrfToken = null;
let csrfHeaderName = null;

export function getCookie(name) {
    const parts = document.cookie.split(";").map(v => v.trim());
    for (const p of parts) {
        if (p.startsWith(name + "=")) return decodeURIComponent(p.substring(name.length + 1));
    }
    return null;
}

export async function initCsrf() {
    // Fetch CSRF info from server (token + correct header name)
    const res = await fetch("/api/csrf", {
        method: "GET",
        credentials: "same-origin",
        cache: "no-store"
    });

    const data = await res.json().catch(() => ({}));

    csrfToken = data.token || null;
    csrfHeaderName = data.headerName || null;

    return { csrfToken, csrfHeaderName, data };
}

export async function apiFetch(url, options = {}) {
    const opts = {
        credentials: "same-origin",
        cache: "no-store",
        ...options
    };

    // Merge headers safely
    opts.headers = { ...(options.headers || {}) };

    // ---- Body handling ----
    const hasBody = opts.body !== undefined && opts.body !== null;
    const isFormData = (typeof FormData !== "undefined") && (opts.body instanceof FormData);
    const isString = typeof opts.body === "string";

    if (hasBody && isFormData) {
        delete opts.headers["Content-Type"];
        delete opts.headers["content-type"];
    } else if (hasBody && !isString) {
        if (!opts.headers["Content-Type"] && !opts.headers["content-type"]) {
            opts.headers["Content-Type"] = "application/json";
        }
        opts.body = JSON.stringify(opts.body);
    } else if (hasBody && isString) {
        if (!opts.headers["Content-Type"] && !opts.headers["content-type"]) {
            opts.headers["Content-Type"] = "application/json";
        }
    }

    // ---- CSRF header for state-changing requests ----
    const method = (opts.method || "GET").toUpperCase();
    if (["POST", "PUT", "PATCH", "DELETE"].includes(method)) {

        // 1) If we already loaded CSRF from /api/csrf, use the exact header name Spring expects
        if (csrfToken && csrfHeaderName && !opts.headers[csrfHeaderName]) {
            opts.headers[csrfHeaderName] = csrfToken;
        }

        // 2) Fallback: cookie-based token (XSRF-TOKEN -> X-XSRF-TOKEN)
        const cookieToken = getCookie("XSRF-TOKEN");
        if (cookieToken && !opts.headers["X-XSRF-TOKEN"]) {
            opts.headers["X-XSRF-TOKEN"] = cookieToken;
        }

        // 3) Extra fallback: some configs expect X-CSRF-TOKEN
        if ((csrfToken || cookieToken) && !opts.headers["X-CSRF-TOKEN"]) {
            opts.headers["X-CSRF-TOKEN"] = (csrfToken || cookieToken);
        }
    }

    const res = await fetch(url, opts);

    const ct = res.headers.get("content-type") || "";
    const body = ct.includes("application/json")
        ? await res.json().catch(() => null)
        : await res.text().catch(() => "");

    if (!res.ok) {
        const msg =
            (body && body.message) ? body.message :
                (typeof body === "string" && body) ? body :
                    `Request failed (${res.status})`;
        throw new Error(msg);
    }

    return body;
}
