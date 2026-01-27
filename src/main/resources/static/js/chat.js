import { apiFetch, initCsrf } from "./api.js";

let me = null;
let stompClient = null;
let currentRoomId = null;
let currentSubscription = null;

function el(id) { return document.getElementById(id); }
function setMsg(text) { el("msg").textContent = text || ""; }

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

function getCookie(name) {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) return parts.pop().split(';').shift();
    return null;
}

function renderMessage(msg) {
    const wrap = document.createElement("div");
    wrap.className = "msg-row" + (String(msg.senderUserId) === String(me.userId) ? " me" : "");

    const time = msg.createdAt ? new Date(msg.createdAt).toLocaleString() : "";
    let bodyHtml = "";
    if (msg.type === "IMAGE" && msg.attachmentUrl) {
        bodyHtml = `<div class="bubble"><img src="${msg.attachmentUrl}" alt="image" style="max-width:220px; border-radius:10px;"/></div>`;
    } else {
        bodyHtml = `<div class="bubble">${escapeHtml(msg.content || "")}</div>`;
    }

    wrap.innerHTML = `
        <div class="meta">${escapeHtml(msg.senderName || "")} · ${escapeHtml(time)}</div>
        ${bodyHtml}
    `;
    el("messages").appendChild(wrap);
    el("messages").scrollTop = el("messages").scrollHeight;
}

async function connectWs() {
    if (stompClient && stompClient.connected) return;

    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // silence logs

    return new Promise((resolve, reject) => {
        stompClient.connect({}, () => resolve(), (err) => reject(err));
    });
}

async function selectRoom(roomId) {
    currentRoomId = roomId;
    el("roomTitle").textContent = `Room #${roomId}`;
    el("messages").innerHTML = "";

    // load history (API returns newest-first)
    const page = await apiFetch(`/api/chat/rooms/${roomId}/messages?page=0&size=50`);
    const items = [...(page.items || [])].reverse();
    for (const m of items) renderMessage(m);

    // connect websocket + subscribe
    await connectWs();
    if (currentSubscription) {
        currentSubscription.unsubscribe();
        currentSubscription = null;
    }
    currentSubscription = stompClient.subscribe(`/topic/rooms/${roomId}`, (frame) => {
        try {
            const msg = JSON.parse(frame.body);
            // avoid duplicate render if already in history: optional
            renderMessage(msg);
        } catch (e) {
            console.error(e);
        }
    });
}

async function loadRooms() {
    const rooms = await apiFetch("/api/chat/rooms");
    el("rooms").innerHTML = "";

    if (!rooms || rooms.length === 0) {
        el("rooms").innerHTML = `<div class="muted">No chats yet. Accept a match request first.</div>`;
        return;
    }

    for (const r of rooms) {
        const div = document.createElement("div");
        div.className = "card";
        div.style.cursor = "pointer";
        div.innerHTML = `
            <strong>${escapeHtml(r.otherName || "Chat")}</strong>
            <div class="muted">${escapeHtml(r.lastMessage || "")}</div>
        `;
        div.addEventListener("click", () => selectRoom(r.roomId));
        el("rooms").appendChild(div);
    }

    // auto-open if query param present
    const qRoom = qp("room");
    const first = qRoom ? rooms.find(x => String(x.roomId) === String(qRoom)) : rooms[0];
    if (first) selectRoom(first.roomId);
}

async function sendText() {
    const text = el("text").value;
    if (!currentRoomId) { setMsg("Pick a room first"); return; }
    if (!text.trim()) return;

    await connectWs();
    stompClient.send(`/app/chat.send/${currentRoomId}`, {}, JSON.stringify({ content: text }));
    el("text").value = "";
}

async function sendImage(file) {
    if (!currentRoomId) { setMsg("Pick a room first"); return; }
    if (!file) return;

    const token = getCookie("XSRF-TOKEN");
    const form = new FormData();
    form.append("file", file);

    const res = await fetch(`/api/chat/rooms/${currentRoomId}/image`, {
        method: "POST",
        credentials: "same-origin",
        headers: token ? { "X-XSRF-TOKEN": token } : {},
        body: form
    });

    if (!res.ok) {
        let err = "Upload failed";
        try { const j = await res.json(); err = j.message || err; } catch (_) {}
        throw new Error(err);
    }

    // Message will also arrive via WS broadcast, so no need to manually render.
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();
    me = await apiFetch("/api/me");

    await loadRooms();

    el("sendBtn").addEventListener("click", () => {
        sendText().catch(e => setMsg(e?.message || "Send failed"));
    });
    el("text").addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
            e.preventDefault();
            sendText().catch(e2 => setMsg(e2?.message || "Send failed"));
        }
    });

    el("file").addEventListener("change", async () => {
        const file = el("file").files && el("file").files[0];
        if (!file) return;

        // preview
        const url = URL.createObjectURL(file);
        el("preview").innerHTML = `
            <div class="muted">Preview:</div>
            <img src="${url}" alt="preview" style="max-width:220px; border-radius:10px;"/>
            <div style="margin-top:8px;">
                <button class="btn" id="sendImgBtn">Send Image</button>
                <button class="btn secondary" id="cancelImgBtn">Cancel</button>
            </div>
        `;

        el("sendImgBtn").addEventListener("click", async () => {
            try {
                el("sendImgBtn").disabled = true;
                await sendImage(file);
                el("preview").innerHTML = "";
                el("file").value = "";
            } catch (e) {
                setMsg(e?.message || "Image upload failed");
            } finally {
                el("sendImgBtn").disabled = false;
            }
        });
        el("cancelImgBtn").addEventListener("click", () => {
            el("preview").innerHTML = "";
            el("file").value = "";
        });
    });
});
