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

// Match the protected media controller's current and legacy image names.
const mediaId = "[1-9][0-9]{0,18}";
const mediaUuid = "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}";
const mediaKey = `(?:${mediaId}|${mediaUuid})`;
const mediaExtension = "\\.(?:jpg|jpeg|png|webp)";
const chatImagePath = new RegExp(`^/uploads/chat/${mediaId}/u${mediaId}-${mediaKey}${mediaExtension}$`, "i");
const profileImagePath = new RegExp(`^/uploads/(?:profiles/${mediaId}/(?:profile|cover)-${mediaKey}|profile-${mediaId}-${mediaId}|profile_${mediaId}_${mediaUuid})${mediaExtension}$`, "i");

function remoteImageUrl(value){
    if (typeof value !== "string" || /[\s\\]/.test(value)
            || !(value.startsWith("/") && !value.startsWith("//") || /^https?:\/\//i.test(value))) return null;
    try {
        const url = new URL(value, window.location.origin);
        if (!["http:", "https:"].includes(url.protocol) || url.username || url.password) return null;
        return url;
    } catch (_) {
        return null;
    }
}

function chatAttachmentUrl(value){
    const url = remoteImageUrl(value);
    if (!url || url.origin !== window.location.origin || url.search || url.hash
            || /%|(?:^|\/)\.{1,2}(?:\/|$)/.test(value) || !chatImagePath.test(url.pathname)) return null;
    // IDs must also fit the backend's signed Long range.
    const ids = url.pathname.match(/\d+/g);
    if (ids.slice(0, 2).some(id => BigInt(id) > 9223372036854775807n)) return null;
    return url.href;
}

function roomAvatarUrl(value){
    const url = remoteImageUrl(value);
    if (!url) return null;
    // OAuth profile pictures can be external HTTPS images; attachment URLs cannot.
    if (url.origin !== window.location.origin) return url.protocol === "https:" ? url.href : null;
    return !url.search && !url.hash && !/%|(?:^|\/)\.{1,2}(?:\/|$)/.test(value)
        && profileImagePath.test(url.pathname) ? url.href : null;
}

/* ===== Image preview helpers ===== */
function showImagePreview(file){
    const wrap = $("imagePreviewWrap");
    const img = $("imagePreview");
    const name = $("imagePreviewName");
    if (!wrap || !img) return;

    const url = URL.createObjectURL(file);
    img.src = url;
    if (name) name.textContent = file.name || "Selected image";
    wrap.style.display = "flex";
}

function hideImagePreview(){
    const wrap = $("imagePreviewWrap");
    const img = $("imagePreview");
    const name = $("imagePreviewName");

    if (img && img.src) {
        try { URL.revokeObjectURL(img.src); } catch (_) {}
    }
    if (img) img.removeAttribute("src");
    if (name) name.textContent = "";
    if (wrap) wrap.style.display = "none";
}

/* ===== Render chat message ===== */
function renderMessage(m){
    const wrap = document.createElement("div");
    const mine = String(m.senderUserId) === String(me.userId || me.id);
    wrap.className = "chat-msg" + (mine ? " me" : "");

    const time = m.createdAt ? new Date(m.createdAt).toLocaleString() : "";
    const meta = document.createElement("div");
    meta.className = "meta";
    meta.textContent = `${m.senderName || ""} · ${time}`;
    wrap.appendChild(meta);

    if ((m.type || "").toUpperCase() === "IMAGE" && m.attachmentUrl){
        const url = chatAttachmentUrl(m.attachmentUrl);
        if (url) {
            const image = document.createElement("img");
            image.src = url;
            image.alt = "image";
            wrap.appendChild(image);
        }
    } else {
        const body = document.createElement("div");
        body.textContent = m.content || "";
        wrap.appendChild(body);
    }

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

    $("chatBox").replaceChildren();

    // Load history (API returns newest first, so reverse to show oldest→newest)
    const page = await apiFetch(`/api/chat/rooms/${roomId}/messages?page=0&size=50`);
    const items = Array.isArray(page.items) ? [...page.items].reverse() : [];
    items.forEach(renderMessage);

    await subscribeRoom(roomId);
    setToast("✅ Connected. Start chatting!", "success");
}

function roomCard(r){
    const row = document.createElement("div");
    row.className = "row";
    const picture = roomAvatarUrl(r.otherPicture ?? r.otherPic);
    const avatar = document.createElement(picture ? "img" : "div");
    avatar.className = "avatar";
    if (picture) {
        avatar.src = picture;
        avatar.alt = "pic";
    }
    row.appendChild(avatar);

    const details = document.createElement("div");
    details.style.flex = "1";
    const title = document.createElement("div");
    const name = document.createElement("b");
    name.textContent = r.otherName || "Chat";
    title.appendChild(name);
    details.appendChild(title);
    const last = document.createElement("div");
    last.className = "help";
    const at = r.lastAt ? new Date(r.lastAt).toLocaleString() : "";
    last.textContent = `${r.lastMessage || ""} ${at ? "· " + at : ""}`;
    details.appendChild(last);
    row.appendChild(details);
    const badge = document.createElement("span");
    badge.className = "badge";
    badge.textContent = "Open";
    row.appendChild(badge);
    return row;
}

async function loadRooms(){
    const rooms = await apiFetch("/api/chat/rooms");
    const box = $("roomsList");
    box.replaceChildren();

    if (!rooms || rooms.length === 0){
        const empty = document.createElement("div");
        empty.className = "toast info";
        empty.textContent = "No chats yet. Go to Requests → Accept a request first.";
        box.appendChild(empty);
        return;
    }

    rooms.forEach(r => {
        const div = document.createElement("div");
        div.className = "list-item";
        div.style.cursor = "pointer";
        div.appendChild(roomCard(r));

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

    const { data: csrf } = await initCsrf();
    const form = new FormData();
    form.append("file", file);

    const res = await fetch(`/api/chat/rooms/${currentRoomId}/image`, {
        method: "POST",
        credentials: "same-origin",
        headers: { [csrf.headerName]: csrf.token },
        body: form
    });

    if (!res.ok){
        let err = "Upload failed";
        try{ const j = await res.json(); err = j.message || err; }catch(_){}
        throw new Error(err);
    }

    // Message will appear via WebSocket broadcast
    $("imageInput").value = "";
    hideImagePreview();
    setToast("✅ Image sent!", "success");
}

/* ✅ WhatsApp one-button behavior */
async function unifiedSend(){
    if (!currentRoomId){
        setToast("Pick a chat room first.", "error");
        return;
    }

    const text = ($("messageInput").value || "").trim();
    const file = $("imageInput").files && $("imageInput").files[0];

    if (!text && !file) return;

    // Send image first if chosen
    if (file){
        await sendImage();
    }

    // Then send text if typed
    if (text){
        await sendText();
    }
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();
    me = await apiFetch("/api/me");

    await loadRooms();

    // ✅ Preview when selecting an image
    $("imageInput").addEventListener("change", () => {
        const file = $("imageInput").files && $("imageInput").files[0];
        if (file) showImagePreview(file);
        else hideImagePreview();
    });

    // ✅ Remove selected image
    $("removeImageBtn")?.addEventListener("click", () => {
        $("imageInput").value = "";
        hideImagePreview();
    });

    // ✅ One send button for text + image
    $("sendBtn").addEventListener("click", () => {
        unifiedSend().catch(e => setToast(e.message || "Send failed", "error"));
    });

    // ✅ Enter sends text only (WhatsApp style)
    $("messageInput").addEventListener("keydown", (e) => {
        if (e.key === "Enter"){
            e.preventDefault();
            sendText().catch(e2 => setToast(e2.message || "Send failed", "error"));
        }
    });

    // Keep hidden button safe (if it still exists)
    const imgBtn = $("sendImageBtn");
    if (imgBtn){
        imgBtn.addEventListener("click", () => {
            unifiedSend().catch(e => setToast(e.message || "Send failed", "error"));
        });
    }
});