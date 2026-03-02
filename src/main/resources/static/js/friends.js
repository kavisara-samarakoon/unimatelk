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

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();

    try {
        await apiFetch("/api/me");
    } catch (_) {
        window.location.href = "/oauth2/authorization/google";
        return;
    }

    let rooms;
    try {
        rooms = await apiFetch("/api/chat/rooms");
    } catch (e) {
        setMsg(e?.message || "Failed to load friends", "error");
        return;
    }

    const box = el("friendsList");
    box.innerHTML = "";

    if (!Array.isArray(rooms) || rooms.length === 0) {
        box.innerHTML = `<div class="toast info">No friends yet. Accept a request first.</div>`;
        return;
    }

    rooms.forEach(r => {
        const roomId = r.roomId ?? r.id;
        const name = r.otherName ?? "Friend";
        const last = r.lastMessage ?? "";
        const card = document.createElement("div");
        card.className = "card";
        card.style.marginTop = "10px";
        card.style.cursor = "pointer";
        card.innerHTML = `
      <div><b>${escapeHtml(name)}</b></div>
      <div style="opacity:0.85; margin-top:6px;">${escapeHtml(last)}</div>
      <div style="margin-top:10px;">
        <a class="btn primary" href="/chat.html?room=${encodeURIComponent(roomId)}">Open Chat</a>
      </div>
    `;
        box.appendChild(card);
    });

    setMsg("Friends loaded.", "success");
});