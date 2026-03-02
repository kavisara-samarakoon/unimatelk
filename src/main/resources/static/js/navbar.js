import { apiFetch, initCsrf } from "./api.js";

export async function mountNavbar() {
    const mount = document.getElementById("navbarMount");
    if (!mount) return;

    mount.innerHTML = `
    <div class="nav">
      <a class="nav-link" href="/home.html">Home</a>
      <a class="nav-link" href="/profile.html">Profile</a>
      <a class="nav-link" href="/preferences.html">Preferences</a>
      <a class="nav-link" href="/matches.html">Matches</a>
      <a class="nav-link" href="/requests.html">Requests</a>
      <a class="nav-link" href="/chat.html">Chat</a>
      <a class="nav-link" href="/friends.html">Friends</a>
      <a class="nav-link" id="navAdmin" href="/admin.html">Admin</a>
      <a class="nav-link" href="/logout">Logout</a>
      <span class="nav-me" id="navMe"></span>
    </div>
  `;

    try {
        await initCsrf();
        const me = await apiFetch("/api/me");

        const role = (me?.role || "").toUpperCase();
        if (role !== "ADMIN") {
            const a = document.getElementById("navAdmin");
            if (a) a.remove();
        }

        const navMe = document.getElementById("navMe");
        if (navMe) {
            navMe.textContent = me?.name ? `Hi, ${me.name}` : "";
        }
    } catch (_) {
        const a = document.getElementById("navAdmin");
        if (a) a.remove();
    }
}