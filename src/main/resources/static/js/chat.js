import { apiFetch, initCsrf } from "./api.js";

let me = null;
let stompClient = null;
let currentRoomId = null;
let currentSub = null;

const $ = (id) => document.getElementById(id);

function setToast(text, type="info"){
    const el = $("msg");
    el.className = "toast " + type;
    el.textContent = text || "";
}

function qp(name){
    const u = new URL(window.location.href);
    return u.searchParams.get(name);
}

function setRoomInUrl(roomId){
    const u = new URL(window.location.href);
    u.searchParams.set("room", String(roomId));
    window.history.replaceState({}, "", u.toString());
}

function escapeHtml(str){
    return String(str || "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function renderMessage(m){
    const wrap = document.createElement("div");
    const mine = String(m.senderUserId) === String(me.userId || me.id);
    wrap.className = "chat-msg" + (mine ? " me" : "");

    const time = m.createdAt ? new Date(m.createdAt).toLocaleString() : "";
    const meta = `<div class="meta">${escapeHtml(m.senderName || "")} · ${escapeHtml(time)}</div>`;

    let body = "";
    if ((m.type || "").toUpperCase() === "IMAGE" && m.attachmentUrl){
        body = `<img src="${m.attachmentUrl}" alt="image"/>`;
    } else {
        body = `<div>${escapeHtml(m.content || "")}</div>`;
    }

    wrap.innerHTML = meta + body;
    $("chatBox").appendChild(wrap);
    $("chatBox").scrollTop = $("chatBox").scrollHeight;
}

async function connectWs(){
    if (stompClient && stompClient.connected) return;

    const socket = new SockJS("/ws");
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    return new Promise((resolve, reject) => {
        stompClient.connect({}, () => resolve(), (e) => reject(e));
    });
}

async function subscribeRoom(roomId){
    await connectWs();

    if (currentSub){
        currentSub.unsubscribe();
        currentSub = null;
    }

    currentSub = stompClient.subscribe(`/topic/rooms/${roomId}`, (frame) => {
        try{
            const msg = JSON.parse(frame.body);
            renderMessage(msg);
        }catch(e){
            console.error(e);
        }
    });
}

async function openRoom(roomId){
    currentRoomId = roomId;
    $("roomTitle").textContent = `Room #${roomId}`;
    setRoomInUrl(roomId);

    $("chatBox").innerHTML = "";

    // Load history (API returns newest first, so reverse to show oldest→newest)
    const page = await apiFetch(`/api/chat/rooms/${roomId}/messages?page=0&size=50`);
    const items = Array.isArray(page.items) ? [...page.items].reverse() : [];
    items.forEach(renderMessage);

    await subscribeRoom(roomId);
    setToast("✅ Connected. Start chatting!", "success");
}

function roomCardHtml(r){
    const name = r.otherName || "Chat";
    const last = r.lastMessage || "";
    const at = r.lastAt ? new Date(r.lastAt).toLocaleString() : "";
    const pic = r.otherPic ? `<img class="avatar" src="${r.otherPic}" alt="pic"/>` : `<div class="avatar"></div>`;

    return `
    <div class="row">
      ${pic}
      <div style="flex:1">
        <div><b>${escapeHtml(name)}</b></div>
        <div class="help">${escapeHtml(last)} ${at ? "· " + escapeHtml(at) : ""}</div>
      </div>
      <span class="badge">Open</span>
    </div>
  `;
}

async function loadRooms(){
    const rooms = await apiFetch("/api/chat/rooms");
    const box = $("roomsList");
    box.innerHTML = "";

    if (!rooms || rooms.length === 0){
        box.innerHTML = `<div class="toast info">No chats yet. Go to Requests → Accept a request first.</div>`;
        return;
    }

    rooms.forEach(r => {
        const div = document.createElement("div");
        div.className = "list-item";
        div.style.cursor = "pointer";
        div.innerHTML = roomCardHtml(r);

        div.addEventListener("click", () => openRoom(r.roomId));
        box.appendChild(div);
    });

    // Auto-open: room param if present, else open first room
    const qRoom = qp("room");
    const chosen = qRoom ? rooms.find(x => String(x.roomId) === String(qRoom)) : rooms[0];
    if (chosen) await openRoom(chosen.roomId);
}

async function sendText(){
    const text = $("messageInput").value || "";
    if (!currentRoomId){
        setToast("Pick a chat room first.", "error");
        return;
    }
    if (!text.trim()) return;

    await connectWs();
    stompClient.send(`/app/chat.send/${currentRoomId}`, {}, JSON.stringify({ content: text.trim() }));
    $("messageInput").value = "";
}

function getCookie(name){
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) return parts.pop().split(";").shift();
    return null;
}

async function sendImage(){
    if (!currentRoomId){
        setToast("Pick a chat room first.", "error");
        return;
    }
    const file = $("imageInput").files && $("imageInput").files[0];
    if (!file){
        setToast("Choose an image first.", "error");
        return;
    }

    const token = getCookie("XSRF-TOKEN");
    const form = new FormData();
    form.append("file", file);

    const res = await fetch(`/api/chat/rooms/${currentRoomId}/image`, {
        method: "POST",
        credentials: "same-origin",
        headers: token ? { "X-XSRF-TOKEN": token } : {},
        body: form
    });

    if (!res.ok){
        let err = "Upload failed";
        try{ const j = await res.json(); err = j.message || err; }catch(_){}
        throw new Error(err);
    }

    // Message will appear via WebSocket broadcast
    $("imageInput").value = "";
    setToast("✅ Image sent!", "success");
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();
    me = await apiFetch("/api/me");

    await loadRooms();

    $("sendBtn").addEventListener("click", () => {
        sendText().catch(e => setToast(e.message || "Send failed", "error"));
    });

    $("messageInput").addEventListener("keydown", (e) => {
        if (e.key === "Enter"){
            e.preventDefault();
            sendText().catch(e2 => setToast(e2.message || "Send failed", "error"));
        }
    });

    $("sendImageBtn").addEventListener("click", () => {
        sendImage().catch(e => setToast(e.message || "Image failed", "error"));
    });
});
