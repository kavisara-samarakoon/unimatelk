import { apiFetch, initCsrf } from "./api.js";

const el = (id) => document.getElementById(id);

function setMsg(text, type = "") {
    const m = el("msg");
    if (!m) return;
    m.textContent = text || "";
    m.className = "toast " + type;
}

function escapeHtml(str) {
    return String(str || "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function reportCard(r) {
    const id = r.id ?? "";
    const reporter = r.reporterEmail ?? r.reporterName ?? r.reporter ?? "";
    const reported = r.reportedEmail ?? r.reportedName ?? r.reported ?? "";
    const reason = r.reason ?? "";
    const details = r.details ?? "";
    const createdAt = r.createdAt ? new Date(r.createdAt).toLocaleString() : "";

    return `
    <div class="card" style="margin-top:10px;">
      <div><b>Report #${escapeHtml(String(id))}</b></div>
      <div style="opacity:0.9; margin-top:6px;">
        <div><b>Reporter:</b> ${escapeHtml(reporter)}</div>
        <div><b>Reported:</b> ${escapeHtml(reported)}</div>
        <div><b>Reason:</b> ${escapeHtml(reason)}</div>
        ${details ? `<div><b>Details:</b> ${escapeHtml(details)}</div>` : ""}
        ${createdAt ? `<div><b>Time:</b> ${escapeHtml(createdAt)}</div>` : ""}
      </div>

      <div style="display:flex; gap:10px; flex-wrap:wrap; margin-top:10px;">
        <select id="action_${id}">
          <option value="NO_ACTION">No Action</option>
          <option value="TEMP_BLOCK">Temp Block</option>
          <option value="BAN">Ban</option>
          <option value="UNBLOCK">Unblock</option>
        </select>
        <input id="note_${id}" placeholder="Note (optional)" style="flex:1; min-width:220px;">
        <button class="btn primary" data-id="${id}" data-action="resolve">Resolve</button>
      </div>
    </div>
  `;
}

async function loadReports() {
    const box = el("reports");
    box.innerHTML = "";

    let res;
    try {
        // backend returns a paged object (NOT an array)
        res = await apiFetch("/api/admin/reports?status=OPEN&page=0&size=50");
    } catch (e) {
        setMsg("Failed to load admin reports: " + (e?.message || ""), "error");
        return;
    }

    // Accept both formats: array OR {items:[]}
    const reports = Array.isArray(res) ? res : (Array.isArray(res?.items) ? res.items : []);

    if (reports.length === 0) {
        box.innerHTML = `<div class="toast info">No open reports.</div>`;
        return;
    }

    box.innerHTML = reports.map(reportCard).join("");

    box.querySelectorAll('button[data-action="resolve"]').forEach((btn) => {
        btn.addEventListener("click", async () => {
            const id = btn.getAttribute("data-id");
            const action = el(`action_${id}`).value;
            const note = el(`note_${id}`).value || "";

            try {
                await apiFetch(`/api/admin/reports/${id}/resolve`, {
                    method: "POST",
                    body: { action, note }
                });
                setMsg("Resolved.", "success");
                await loadReports();
            } catch (e) {
                setMsg(e?.message || "Resolve failed", "error");
            }
        });
    });
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();

    let me;
    try {
        me = await apiFetch("/api/me");
    } catch (_) {
        window.location.href = "/oauth2/authorization/google";
        return;
    }

    const role = (me?.role || "").toUpperCase();
    if (role !== "ADMIN") {
        window.location.href = "/home.html";
        return;
    }

    setMsg("Loading reports...", "info");
    await loadReports();
});