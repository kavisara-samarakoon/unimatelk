import { apiFetch, initCsrf } from "./api.js";

function esc(s){
    return String(s ?? "")
        .replaceAll("&","&amp;")
        .replaceAll("<","&lt;")
        .replaceAll(">","&gt;");
}

function friendCard(f){
    const pic = f.picture
        ? `<img class="avatar" src="${f.picture}" alt="pic"/>`
        : `<div class="avatar"></div>`;

    return `
    <div class="list-item">
      <div class="row">
        ${pic}
        <div style="flex:1">
          <div><b>${esc(f.name)}</b></div>
          <div class="help">${esc(f.email)}</div>
        </div>
        <a class="btn secondary" href="/chat.html">Chat</a>
        <a class="btn secondary" href="/user.html?id=${f.id}">View</a>
      </div>
    </div>
  `;
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();

    const list = document.getElementById("friendsList");
    const msg = document.getElementById("friendsMsg");
    if (!list) return;

    try {
        const friends = await apiFetch("/api/friends");

        if (!friends || friends.length === 0) {
            msg.textContent = "No friends/connections yet. Accept a match request first.";
            list.innerHTML = "";
            return;
        }

        msg.textContent = `You have ${friends.length} connection(s).`;
        list.innerHTML = friends.map(friendCard).join("");
    } catch (e) {
        msg.textContent = "Failed to load friends list.";
        console.error(e);
    }
});
