function getCookie(name){
    const m = document.cookie.match(new RegExp("(^| )" + name + "=([^;]+)"));
    return m ? decodeURIComponent(m[2]) : null;
}

function setCsrf(headers){
    const token = getCookie("XSRF-TOKEN");
    if (token) headers.set("X-XSRF-TOKEN", token);
}

async function api(url, options = {}) {
    const headers = new Headers(options.headers || {});
    headers.set("Accept", "application/json");
    headers.set("X-Requested-With", "XMLHttpRequest");
    if (options.body && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
    }
    setCsrf(headers);

    const res = await fetch(url, {
        credentials: "same-origin",
        ...options,
        headers
    });

    const contentType = res.headers.get("content-type") || "";
    const raw = await res.text();

    if (!res.ok) {
        throw new Error(`${res.status} ${res.statusText}\n${raw.substring(0, 250)}`);
    }

    if (contentType.includes("application/json")) {
        return raw ? JSON.parse(raw) : null;
    }

    return raw;
}

function showMsg(text, type="info"){
    const box = document.getElementById("adminMsg");
    box.className = `toast ${type}`;
    box.textContent = text;
    box.style.display = "block";
}

function hideMsg(){
    const box = document.getElementById("adminMsg");
    box.style.display = "none";
}

function escapeHtml(s){
    return String(s ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function fmtDate(v){
    try { return v ? new Date(v).toLocaleString() : ""; }
    catch { return String(v || ""); }
}

let current = null;

function renderList(items){
    const list = document.getElementById("reportsList");
    const badge = document.getElementById("countBadge");
    list.innerHTML = "";
    badge.textContent = String(items.length);

    if (!items.length){
        list.innerHTML = `<div class="muted">No OPEN reports found.</div>`;
        return;
    }

    for (const r of items){
        const el = document.createElement("div");
        el.className = "list-item";
        el.style.cursor = "pointer";
        el.innerHTML = `
      <div class="row">
        <div style="font-weight:700;">${escapeHtml(r.reason || "No reason")}</div>
        <span class="right badge">${escapeHtml(r.status || "OPEN")}</span>
      </div>
      <div class="help">
        Reported: <b>${escapeHtml(r.reportedEmail || "-")}</b> •
        Reporter: <b>${escapeHtml(r.reporterEmail || "-")}</b>
      </div>
      <div class="help">${escapeHtml(fmtDate(r.createdAt))}</div>
    `;
        el.addEventListener("click", () => selectReport(r));
        list.appendChild(el);
    }
}

function selectReport(r){
    current = r;
    document.getElementById("detailsHint").style.display = "none";
    document.getElementById("detailsBox").style.display = "block";

    document.getElementById("d_reason").textContent = r.reason || "Reason";
    document.getElementById("d_status").textContent = r.status || "OPEN";
    document.getElementById("d_meta").textContent = `Created: ${fmtDate(r.createdAt)}`;
    document.getElementById("d_reporter").textContent = r.reporterEmail || "-";
    document.getElementById("d_reported").textContent = r.reportedEmail || "-";
    document.getElementById("d_details").textContent = r.details || "(no details)";
    document.getElementById("actionMsg").textContent = "";
}

async function loadReports(){
    hideMsg();
    const q = (document.getElementById("search")?.value || "").trim();

    try{
        const data = await api(`/api/admin/reports?status=OPEN&query=${encodeURIComponent(q)}`);

        const items = data?.items || data?.reports || [];
        renderList(items);

    }catch(e){
        console.error(e);
        showMsg("Failed to load reports:\n" + e.message, "error");
        document.getElementById("reportsList").innerHTML = `<div class="muted">Error loading reports.</div>`;
        document.getElementById("countBadge").textContent = "0";
    }
}

document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("refreshBtn")?.addEventListener("click", loadReports);

    document.getElementById("search")?.addEventListener("input", () => {
        clearTimeout(window.__t);
        window.__t = setTimeout(loadReports, 250);
    });

    document.getElementById("resolveBtn")?.addEventListener("click", async () => {
        if (!current) return;

        const msg = document.getElementById("actionMsg");
        msg.textContent = "Resolving...";

        try{
            await api(`/api/admin/reports/${current.id}/resolve`, { method: "POST" });
            msg.textContent = "✅ Resolved.";
            current = null;
            document.getElementById("detailsBox").style.display = "none";
            document.getElementById("detailsHint").style.display = "block";
            await loadReports();
        }catch(e){
            console.error(e);
            msg.textContent = "❌ Failed: " + e.message;
        }
    });

    document.getElementById("openUserBtn")?.addEventListener("click", () => {
        if (!current) return;

        const email = encodeURIComponent(current.reportedEmail || "");
        window.location.href = `/user.html?email=${email}`;
    });

    loadReports();
});