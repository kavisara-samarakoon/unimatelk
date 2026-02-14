import { apiFetch, initCsrf } from "./api.js";

const $ = (id) => document.getElementById(id);

function toast(text, type = "info") {
    const el = $("msg");
    if (!el) return;
    el.className = "toast " + type;
    el.textContent = text || "";
}

function esc(s) {
    return String(s ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;");
}

function showUsersTab() {
    $("usersSection").style.display = "";
    $("reportsSection").style.display = "none";
    $("tabUsers").className = "btn";
    $("tabReports").className = "btn secondary";
}

function showReportsTab() {
    $("usersSection").style.display = "none";
    $("reportsSection").style.display = "";
    $("tabUsers").className = "btn secondary";
    $("tabReports").className = "btn";
}

function userRow(u) {
    return `
    <tr>
      <td>${u.id}</td>
      <td>${esc(u.name)}</td>
      <td>${esc(u.email)}</td>
      <td>${esc(u.role)}</td>
      <td>${esc(u.status)}</td>
      <td style="display:flex; gap:6px; flex-wrap:wrap;">
        <button class="btn secondary" data-action="makeAdmin" data-id="${u.id}">Make ADMIN</button>
        <button class="btn secondary" data-action="makeStudent" data-id="${u.id}">Make STUDENT</button>
        <button class="btn secondary" data-action="active" data-id="${u.id}">ACTIVE</button>
        <button class="btn secondary" data-action="temp" data-id="${u.id}">TEMP_BLOCKED</button>
        <button class="btn secondary" data-action="ban" data-id="${u.id}">BANNED</button>
      </td>
    </tr>
  `;
}

function reportRow(r) {
    return `
    <tr>
      <td>${r.id}</td>
      <td>${esc(r.reporterEmail || r.reporterName || r.reporterUserId)}</td>
      <td>${esc(r.reportedEmail || r.reportedName || r.reportedUserId)}</td>
      <td>${esc(r.reason)}</td>
      <td>${esc(r.details)}</td>
      <td>${esc(r.status)}</td>
      <td>
        ${
        String(r.status).toUpperCase() === "OPEN"
            ? `<button class="btn secondary" data-action="resolveReport" data-id="${r.id}">Resolve</button>`
            : `<span class="muted">—</span>`
    }
      </td>
    </tr>
  `;
}

async function loadUsers() {
    const q = ($("userQuery").value || "").trim();
    const data = await apiFetch(`/api/admin/users?query=${encodeURIComponent(q)}&page=0&size=50`);

    $("usersBody").innerHTML = (data.items || []).map(userRow).join("");
    $("usersMeta").textContent = `Showing ${data.items?.length ?? 0} users`;
}

async function loadReports() {
    const status = $("reportStatus").value;
    const q = ($("reportQuery").value || "").trim();

    const data = await apiFetch(
        `/api/admin/reports?status=${encodeURIComponent(status)}&query=${encodeURIComponent(q)}&page=0&size=50`
    );

    $("reportsBody").innerHTML = (data.items || []).map(reportRow).join("");
    $("reportsMeta").textContent = `Showing ${data.items?.length ?? 0} reports`;
}

async function updateUserRole(userId, role) {
    await apiFetch(`/api/admin/users/${userId}/role`, {
        method: "PATCH",
        body: JSON.stringify({ role })
    });
}

async function updateUserStatus(userId, status) {
    await apiFetch(`/api/admin/users/${userId}/status`, {
        method: "PATCH",
        body: JSON.stringify({ status })
    });
}

async function resolveReport(reportId) {
    await apiFetch(`/api/admin/reports/${reportId}/resolve`, {
        method: "POST"
    });
}

async function refreshAll() {
    try {
        toast("Loading admin data…", "info");
        await loadUsers();
        await loadReports();
        toast("✅ Admin data loaded", "success");
    } catch (e) {
        toast("❌ " + (e.message || "Failed to load admin data"), "error");
    }
}

document.addEventListener("DOMContentLoaded", async () => {
    // ✅ CSRF init first (your backend uses CSRF)
    await initCsrf();

    // ✅ STEP 3: Check role from /api/me before loading admin dashboard
    // If not ADMIN, show clean message and stop.
    let me;
    try {
        me = await apiFetch("/api/me");
    } catch (e) {
        document.body.innerHTML = `
      <div style="padding:24px;font-family:Arial">
        <h2>Not logged in</h2>
        <p>Please login first, then open <b>/admin.html</b></p>
        <a href="/index.html">Go Home</a>
      </div>
    `;
        return;
    }

    if ((me.role || "").toUpperCase() !== "ADMIN") {
        document.body.innerHTML = `
      <div style="padding:24px;font-family:Arial">
        <h2>403 - Admin only</h2>
        <p>You are logged in as <b>${esc(me.email || "")}</b> with role <b>${esc(me.role || "")}</b>.</p>
        <p>To access admin panel, your role must be <b>ADMIN</b>.</p>
        <a href="/index.html">Go Home</a>
      </div>
    `;
        return;
    }

    // ✅ Normal admin dashboard setup
    $("tabUsers").addEventListener("click", showUsersTab);
    $("tabReports").addEventListener("click", showReportsTab);
    $("refreshBtn").addEventListener("click", refreshAll);
    $("searchUsersBtn").addEventListener("click", loadUsers);
    $("searchReportsBtn").addEventListener("click", loadReports);

    // User actions
    $("usersBody").addEventListener("click", async (e) => {
        const btn = e.target.closest("button");
        if (!btn) return;

        const action = btn.dataset.action;
        const id = btn.dataset.id;

        try {
            if (action === "makeAdmin") await updateUserRole(id, "ADMIN");
            if (action === "makeStudent") await updateUserRole(id, "STUDENT");
            if (action === "active") await updateUserStatus(id, "ACTIVE");
            if (action === "temp") await updateUserStatus(id, "TEMP_BLOCKED");
            if (action === "ban") await updateUserStatus(id, "BANNED");

            toast("✅ Updated user", "success");
            await loadUsers();
        } catch (err) {
            toast("❌ " + (err.message || "Update failed"), "error");
        }
    });

    // Report actions
    $("reportsBody").addEventListener("click", async (e) => {
        const btn = e.target.closest("button");
        if (!btn) return;

        if (btn.dataset.action !== "resolveReport") return;

        try {
            await resolveReport(btn.dataset.id);
            toast("✅ Report resolved", "success");
            await loadReports();
        } catch (err) {
            toast("❌ " + (err.message || "Resolve failed"), "error");
        }
    });

    // Default tab + initial load
    showUsersTab();
    await refreshAll();
});
