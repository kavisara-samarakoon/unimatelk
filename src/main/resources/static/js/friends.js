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

function getOtherUserId(room) {
    return (
        room?.otherUserId ??
        room?.other_user_id ??
        room?.otherUserID ??
        room?.otherId ??
        room?.other_id ??
        room?.otherUser?.userId ??
        room?.otherUser?.id ??
        null
    );
}

function fmtTime(ts) {
    if (!ts) return "";
    try { return new Date(ts).toLocaleString(); } catch (_) { return ""; }
}

let selected = null;

function openSheet(room) {
    selected = room;

    el("sheetTitle").textContent = room.otherName || "Friend";
    const at = fmtTime(room.lastAt);
    el("sheetSub").textContent = room.lastMessage
        ? `Last: ${room.lastMessage}${at ? " • " + at : ""}`
        : (at ? `Last activity: ${at}` : "");

    el("reportArea").style.display = "none";
    el("reportMsg").textContent = "";
    el("reportReason").value = "";

    const roomId = room.roomId ?? room.id;
    el("openChatBtn").href = `/chat.html?room=${encodeURIComponent(roomId)}`;

    const otherId = getOtherUserId(room);
    if (otherId) {
        const url = `/user.html?id=${encodeURIComponent(otherId)}`;
        el("viewUserBtn").href = url;
        el("viewUserBtn").style.pointerEvents = "auto";
        el("viewUserBtn").style.opacity = "1";
        el("viewUserBtn").onclick = (e) => {
            e.preventDefault();
            window.location.href = url;
        };
    } else {
        el("viewUserBtn").href = "#";
        el("viewUserBtn").style.pointerEvents = "none";
        el("viewUserBtn").style.opacity = "0.5";
        el("viewUserBtn").onclick = null;
    }

    el("sheetBackdrop").style.display = "flex";
}

function closeSheet() {
    selected = null;
    el("sheetBackdrop").style.display = "none";
}

async function submitReport() {
    if (!selected) return;

    const otherId = getOtherUserId(selected);
    if (!otherId) {
        el("reportMsg").textContent = "Cannot report here (missing user id).";
        return;
    }

    const reason = (el("reportReason").value || "").trim();
    if (!reason) {
        el("reportMsg").textContent = "Please write a reason.";
        return;
    }

    if (!confirm("Submit report?")) return;

    try {
        await apiFetch(`/api/safety/report/${otherId}`, {
            method: "POST",
            body: { reason }
        });
        el("reportMsg").textContent = "Report submitted.";
        el("reportReason").value = "";
        setMsg("✅ Report submitted.", "success");
    } catch (e) {
        el("reportMsg").textContent = e?.message || "Report failed";
        setMsg(e?.message || "Report failed", "error");
    }
}

async function blockUser() {
    if (!selected) return;

    const otherId = getOtherUserId(selected);
    if (!otherId) {
        setMsg("Cannot block here (missing user id).", "error");
        return;
    }

    if (!confirm("Block this user?")) return;

    try {
        await apiFetch(`/api/safety/block/${otherId}`, { method: "POST" });
        setMsg("✅ User blocked.", "success");
        closeSheet();
    } catch (e) {
        setMsg(e?.message || "Block failed", "error");
    }
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();

    // ✅ always set loading message from JS (not HTML)
    setMsg("Loading friends...", "info");

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
        setMsg("No friends yet.", "info");   // ✅ update toast
    } else {
        rooms.forEach(r => {
            const card = document.createElement("div");
            card.className = "friend-item";  // ✅ styled by friends.css

            const name = escapeHtml(r.otherName || "Friend");
            const last = escapeHtml(r.lastMessage || "");
            const at = fmtTime(r.lastAt);

            card.innerHTML = `
              <div class="friend-left">
                <div class="friend-text">
                  <div class="friend-name">${name}</div>
                  <div class="friend-last">${last || "No messages yet."}</div>
                </div>
              </div>
              <div class="friend-right">
                <div class="friend-time">${escapeHtml(at)}</div>
                <span class="friend-pill">Options</span>
              </div>
            `;

            card.addEventListener("click", () => openSheet(r));
            box.appendChild(card);
        });

        setMsg("✅ Friends loaded.", "success"); // ✅ update toast
    }

    // Sheet listeners
    el("sheetCloseBtn").addEventListener("click", closeSheet);
    el("sheetBackdrop").addEventListener("click", (e) => {
        if (e.target === el("sheetBackdrop")) closeSheet();
    });

    el("openReportBtn").addEventListener("click", () => {
        el("reportArea").style.display = "block";
    });

    el("cancelReportBtn").addEventListener("click", () => {
        el("reportArea").style.display = "none";
        el("reportMsg").textContent = "";
        el("reportReason").value = "";
    });

    el("submitReportBtn").addEventListener("click", submitReport);
    el("blockBtn").addEventListener("click", blockUser);
});