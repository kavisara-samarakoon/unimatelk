import { apiFetch, initCsrf } from "./api.js";

function el(id) { return document.getElementById(id); }

function qp(name) {
    const u = new URL(window.location.href);
    return u.searchParams.get(name);
}

function escapeHtml(str) {
    return String(str || "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function setToast(text, type = "") {
    const m = el("msg");
    if (!m) return;
    m.textContent = text || "";
    m.className = "toast " + type;
}

function setReportMsg(text) {
    const m = el("reportMsg");
    if (!m) return;
    m.textContent = text || "";
}

async function loadUser(userId) {
    const u = await apiFetch(`/api/users/${userId}`, { method: "GET" });

    el("title").textContent = u?.name ? u.name : "User";

    el("profileCard").innerHTML = `
    <div style="display:flex; gap:14px; align-items:center;">
      <img src="${escapeHtml(u?.pictureUrl || "/img/default-avatar.png")}" alt="avatar" style="width:72px;height:72px;border-radius:50%;object-fit:cover;">
      <div>
        <div style="font-size:20px;font-weight:700;">${escapeHtml(u?.name || "")}</div>
        <div style="opacity:0.85;">${escapeHtml(u?.campus || "")} ${u?.yearOfStudy ? "• Year " + escapeHtml(String(u.yearOfStudy)) : ""}</div>
        <div style="opacity:0.85;">${escapeHtml(u?.faculty || "")} ${escapeHtml(u?.degree || "")}</div>
      </div>
    </div>
    <div style="margin-top:12px;">${escapeHtml(u?.bio || "")}</div>
  `;
}

async function sendMatchRequest(userId) {
    if (!confirm("Send match request?")) return;
    try {
        await apiFetch(`/api/match/request/${userId}`, { method: "POST" });
        setToast("Match request sent.", "success");
    } catch (e) {
        setToast(e?.message || "Failed to send match request", "error");
    }
}

async function blockUser(userId) {
    if (!confirm("Block this user? They will disappear from your feed and chat.")) return;
    try {
        await apiFetch(`/api/safety/block/${userId}`, { method: "POST" });
        setToast("User blocked.", "success");
    } catch (e) {
        setToast(e?.message || "Failed to block", "error");
    }
}

async function reportUser(userId) {
    const reason = (el("reportReason")?.value || "").trim();
    const details = (el("reportDetails")?.value || "").trim();

    if (!reason) {
        setReportMsg("Reason is required.");
        return;
    }

    if (!confirm("Submit report?")) return;

    try {
        await apiFetch(`/api/safety/report/${userId}`, {
            method: "POST",
            body: { reason, details }
        });

        setReportMsg("Report submitted.");
        el("reportReason").value = "";
        el("reportDetails").value = "";
        setToast("Report submitted.", "success");
    } catch (e) {
        setReportMsg(e?.message || "Failed to report");
        setToast(e?.message || "Failed to report", "error");
    }
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();

    const userId = qp("id");
    if (!userId) {
        setToast("Missing user id in URL. Example: /user.html?id=5", "error");
        return;
    }

    try {
        await loadUser(userId);
    } catch (e) {
        setToast(e?.message || "Failed to load user", "error");
        return;
    }

    el("matchBtn").addEventListener("click", () => sendMatchRequest(userId));
    el("blockBtn").addEventListener("click", () => blockUser(userId));
    el("reportBtn").addEventListener("click", () => reportUser(userId));
});