export function getCookie(name) {
    const parts = document.cookie.split(";").map(v => v.trim());
    for (const p of parts) {
        if (p.startsWith(name + "=")) return decodeURIComponent(p.substring(name.length + 1));
    }
    return null;
}

export async function initCsrf() {
    await fetch("/api/csrf", { method: "GET", credentials: "same-origin" });
}

export async function apiFetch(url, options = {}) {
    const opts = {
        credentials: "same-origin",
        headers: { "Content-Type": "application/json", ...(options.headers || {}) },
        ...options
    };

    const method = (opts.method || "GET").toUpperCase();
    if (["POST","PUT","PATCH","DELETE"].includes(method)) {
        const token = getCookie("XSRF-TOKEN");
        if (token) opts.headers["X-XSRF-TOKEN"] = token;
    }

    const res = await fetch(url, opts);
    const ct = res.headers.get("content-type") || "";
    const body = ct.includes("application/json") ? await res.json().catch(() => null) : await res.text();

    if (!res.ok) {
        const msg = (body && body.message) ? body.message : (typeof body === "string" ? body : "Request failed");
        throw new Error(msg);
    }
    return body;
}
