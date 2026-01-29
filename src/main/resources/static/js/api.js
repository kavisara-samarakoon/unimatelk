export function getCookie(name) {
    const parts = document.cookie.split(";").map(v => v.trim());
    for (const p of parts) {
        if (p.startsWith(name + "=")) return decodeURIComponent(p.substring(name.length + 1));
    }
    return null;
}

export async function initCsrf() {
    // Calling this endpoint forces Spring Security to generate/set the XSRF-TOKEN cookie
    await fetch("/api/csrf", { method: "GET", credentials: "same-origin" });
}

export async function apiFetch(url, options = {}) {
    const opts = {
        credentials: "same-origin",
        ...options
    };

    // Merge headers safely
    opts.headers = { ...(options.headers || {}) };

    // ---- Body handling (IMPORTANT FIX) ----
    // Allow callers to pass:
    //  - plain object (auto JSON.stringify)
    //  - JSON string
    //  - FormData (no JSON header)
    const hasBody = opts.body !== undefined && opts.body !== null;
    const isFormData = (typeof FormData !== "undefined") && (opts.body instanceof FormData);
    const isString = typeof opts.body === "string";

    if (hasBody && isFormData) {
        // Let the browser set the multipart boundary Content-Type automatically
        delete opts.headers["Content-Type"];
        delete opts.headers["content-type"];
    } else if (hasBody && !isString) {
        // Plain object -> JSON
        if (!opts.headers["Content-Type"] && !opts.headers["content-type"]) {
            opts.headers["Content-Type"] = "application/json";
        }
        opts.body = JSON.stringify(opts.body);
    } else if (hasBody && isString) {
        // If caller passed a JSON string but forgot header, set it
        if (!opts.headers["Content-Type"] && !opts.headers["content-type"]) {
            opts.headers["Content-Type"] = "application/json";
        }
    }

    // ---- CSRF header for state-changing requests ----
    const method = (opts.method || "GET").toUpperCase();
    if (["POST", "PUT", "PATCH", "DELETE"].includes(method)) {
        const token = getCookie("XSRF-TOKEN");
        if (token && !opts.headers["X-XSRF-TOKEN"]) {
            opts.headers["X-XSRF-TOKEN"] = token;
        }
    }

    const res = await fetch(url, opts);

    // Parse response
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
