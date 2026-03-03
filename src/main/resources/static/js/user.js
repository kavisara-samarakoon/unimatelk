import { apiFetch, initCsrf } from "./api.js";

const el = (id) => document.getElementById(id);

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

function safeImg(url) {
    // your old fallback was /img/default-avatar.png but your project uses /images/ default avatar often
    const u = String(url || "").trim();
    if (!u) return "/images/default-avatar.png";
    return u;
}

function buildProfileCard(u) {
    const name = escapeHtml(u?.name || "");
    const campus = escapeHtml(u?.campus || "");
    const faculty = escapeHtml(u?.faculty || "");
    const degree = escapeHtml(u?.degree || "");
    const bio = escapeHtml(u?.bio || "");
    const year = u?.yearOfStudy ? `Year ${escapeHtml(String(u.yearOfStudy))}` : "";
    const pic = safeImg(u?.pictureUrl);

    const meta1 = [campus, year].filter(Boolean).join(" • ");
    const meta2 = [faculty, degree].filter(Boolean).join(" • ");

    return `
      <div class="user-head">
        <img class="user-avatar" src="${escapeHtml(pic)}" alt="avatar"/>
        <div class="user-info">
          <div class="user-name">${name || "User"}</div>
          ${meta1 ? `<div class="user-meta">${meta1}</div>` : ""}
          ${meta2 ? `<div class="user-meta">${meta2}</div>` : ""}
        </div>
      </div>

      ${bio ? `
        <div class="divider"></div>
        <div class="user-bio">${bio}</div>
      ` : `
        <div class="divider"></div>
        <div class="user-bio muted">No bio provided.</div>
      `}
    `;
}

async function loadUser(userId) {
    const u = await apiFetch(`/api/users/${userId}`, { method: "GET" });

    el("title").textContent = u?.name ? u.name : "User";
    el("profileCard").innerHTML = buildProfileCard(u);

    setToast("User loaded.", "success");
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
    if (!confirm("Block this user?")) return;
    try {
        await apiFetch(`/api/safety/block/${userId}`, { method: "POST" });
        setToast("User blocked.", "success");
    } catch (e) {
        setToast(e?.message || "Failed to block", "error");
    }
}

async function reportUser(userId) {
    const reason = (el("reportReason")?.value || "").trim();
    if (!reason) {
        setReportMsg("Please write a reason.");
        return;
    }
    if (!confirm("Submit report?")) return;

    try {
        await apiFetch(`/api/safety/report/${userId}`, {
            method: "POST",
            body: { reason }
        });
        setReportMsg("Report submitted.");
        el("reportReason").value = "";
        setToast("Report submitted.", "success");
    } catch (e) {
        setReportMsg(e?.message || "Report failed");
        setToast(e?.message || "Report failed", "error");
    }
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();

    try {
        await apiFetch("/api/me");
    } catch (_) {
        window.location.href = "/oauth2/authorization/google";
        return;
    }

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