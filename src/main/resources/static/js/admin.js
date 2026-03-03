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

function extractItems(res) {
    if (Array.isArray(res)) return res;
    if (Array.isArray(res?.items)) return res.items;
    if (Array.isArray(res?.content)) return res.content; // Spring Page default
    if (Array.isArray(res?.data)) return res.data;
    return [];
}

function extractTotal(res, fallback) {
    if (typeof res?.total === "number") return res.total;
    if (typeof res?.totalElements === "number") return res.totalElements;
    if (typeof res?.count === "number") return res.count;
    return fallback;
}

function updateStats(openTotal, resolvedTotal) {
    const sOpen = el("statOpen");
    const sResolved = el("statResolved");
    const sTotal = el("statTotal");

    if (sOpen) sOpen.textContent = String(openTotal ?? 0);
    if (sResolved) sResolved.textContent = String(resolvedTotal ?? 0);
    if (sTotal) sTotal.textContent = String((openTotal ?? 0) + (resolvedTotal ?? 0));
}

function reportCard(r) {
    const id = r.id ?? "";
    const reporter = r.reporterEmail ?? r.reporterName ?? r.reporter ?? "";
    const reported = r.reportedEmail ?? r.reportedName ?? r.reported ?? "";
    const reason = r.reason ?? "";
    const details = r.details ?? "";
    const createdAt = r.createdAt ? new Date(r.createdAt).toLocaleString() : "";

    return `
    <div class="admin-report">
      <div class="admin-report-head">
        <div class="admin-report-title">Report #${escapeHtml(String(id))}</div>
        <span class="badge admin-status">OPEN</span>
      </div>

      <div class="admin-report-body">
        <div class="kv"><span class="k">Reporter</span><span class="v">${escapeHtml(reporter)}</span></div>
        <div class="kv"><span class="k">Reported</span><span class="v">${escapeHtml(reported)}</span></div>
        <div class="kv"><span class="k">Reason</span><span class="v">${escapeHtml(reason)}</span></div>
        ${details ? `<div class="kv"><span class="k">Details</span><span class="v">${escapeHtml(details)}</span></div>` : ``}
        ${createdAt ? `<div class="kv"><span class="k">Time</span><span class="v">${escapeHtml(createdAt)}</span></div>` : ``}
      </div>

      <div class="admin-report-actions">
        <select id="action_${id}">
          <option value="NO_ACTION">No Action</option>
          <option value="TEMP_BLOCK">Temp Block</option>
          <option value="BAN">Ban</option>
          <option value="UNBLOCK">Unblock</option>
        </select>

        <input id="note_${id}" placeholder="Note (optional)">

        <button class="btn" data-id="${id}" data-action="resolve" type="button">Resolve</button>
      </div>
    </div>
  `;
}

async function loadReports() {
    const box = el("reports");
    if (!box) {
        setMsg("Admin UI error: #reports element not found in admin.html", "error");
        return;
    }

    box.innerHTML = "";
    setMsg("Loading reports...", "info");

    let openRes;
    let resolvedRes;

    // Load OPEN
    try {
        openRes = await apiFetch("/api/admin/reports?status=OPEN&page=0&size=50");
    } catch (e) {
        setMsg("Failed to load OPEN reports: " + (e?.message || ""), "error");
        return;
    }

    // Load RESOLVED count (optional)
    try {
        resolvedRes = await apiFetch("/api/admin/reports?status=RESOLVED&page=0&size=1");
    } catch (_) {
        resolvedRes = { items: [], total: 0 };
    }

    const openItems = extractItems(openRes);
    const resolvedItems = extractItems(resolvedRes);

    const openTotal = extractTotal(openRes, openItems.length);
    const resolvedTotal = extractTotal(resolvedRes, resolvedItems.length);

    updateStats(openTotal, resolvedTotal);

    if (!openItems || openItems.length === 0) {
        box.innerHTML = `<div class="toast info">No open reports.</div>`;
        setMsg("No open reports.", "info");
        return;
    }

    box.innerHTML = openItems.map(reportCard).join("");

    // bind resolve buttons
    box.querySelectorAll('button[data-action="resolve"]').forEach((btn) => {
        btn.addEventListener("click", async () => {
            const id = btn.getAttribute("data-id");
            const actionEl = el(`action_${id}`);
            const noteEl = el(`note_${id}`);

            const action = actionEl ? actionEl.value : "NO_ACTION";
            const note = noteEl ? (noteEl.value || "") : "";

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

    setMsg("Reports loaded.", "success");

    // Optional: search filter if adminSearch exists
    const search = el("adminSearch");
    if (search && !search.dataset.bound) {
        search.dataset.bound = "1";
        search.addEventListener("input", () => {
            const q = (search.value || "").trim().toLowerCase();
            const filtered = !q
                ? openItems
                : openItems.filter(r => {
                    const reporter = String(r.reporterEmail ?? r.reporterName ?? r.reporter ?? "").toLowerCase();
                    const reported = String(r.reportedEmail ?? r.reportedName ?? r.reported ?? "").toLowerCase();
                    const reason = String(r.reason ?? "").toLowerCase();
                    const details = String(r.details ?? "").toLowerCase();
                    return reporter.includes(q) || reported.includes(q) || reason.includes(q) || details.includes(q) || String(r.id ?? "").includes(q);
                });

            box.innerHTML = filtered.length
                ? filtered.map(reportCard).join("")
                : `<div class="toast info">No reports match your search.</div>`;
        });
    }
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

    await loadReports();
});