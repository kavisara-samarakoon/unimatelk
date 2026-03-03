// src/main/resources/static/js/navbar.js
import { apiFetch, initCsrf } from "./api.js";

function ensureNavbarStyles() {
    // If theme.css is missing on some page, this prevents the navbar from looking like plain blue links.
    if (document.getElementById("navbarCoreStyles")) return;

    const style = document.createElement("style");
    style.id = "navbarCoreStyles";
    style.textContent = `
    /* Minimal navbar styling (compatible with theme.css). */
    .topbar{
      width: min(1050px, 96vw);
      margin: 0 auto 14px auto;
      padding: 12px 14px;
      border-radius: 22px;
      background: rgba(255,255,255,0.12);
      border: 1px solid rgba(255,255,255,0.22);
      box-shadow: 0 20px 60px rgba(0,0,0,0.35);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);

      display:flex;
      align-items:center;
      justify-content:space-between;
      gap: 10px;
      flex-wrap: wrap;
    }
    .brand{
      display:flex;
      align-items:center;
      gap:10px;
      font-weight:900;
      letter-spacing:0.4px;
      color: rgba(255,255,255,0.92);
      text-decoration:none;
      white-space: nowrap;
    }
    .brand-badge{
      width:10px; height:10px; border-radius:50%;
      background: linear-gradient(90deg, #7c4dff, #00d4ff);
      box-shadow: 0 0 18px rgba(0,212,255,0.35);
    }
    .navlinks{
      display:flex;
      gap:10px;
      flex-wrap:wrap;
      align-items:center;
      justify-content:center;
    }
    .navlinks a{
      text-decoration:none;
      color: rgba(255,255,255,0.92);
      opacity:0.92;
      padding:8px 10px;
      border-radius:999px;
      border: 1px solid rgba(255,255,255,0.14);
      background: rgba(255,255,255,0.06);
    }
    .navlinks a:hover{
      opacity:1;
      border-color: rgba(255,255,255,0.35);
    }
    .navlinks a.active{
      border-color: rgba(0,212,255,0.55);
      box-shadow: 0 0 0 3px rgba(0,212,255,0.12);
    }
    .top-actions{
      display:flex;
      gap:10px;
      align-items:center;
      flex-wrap: wrap;
      justify-content: flex-end;
    }
    .hello{
      color: rgba(255,255,255,0.82);
      font-size: 13px;
      white-space: nowrap;
    }
    .logout-btn{
      border:none;
      cursor:pointer;
      padding:8px 12px;
      border-radius:999px;
      background: rgba(255,255,255,0.10);
      border: 1px solid rgba(255,255,255,0.18);
      color: rgba(255,255,255,0.92);
      font-weight: 800;
    }
    .logout-btn:hover{
      border-color: rgba(255,255,255,0.35);
    }
    @media (max-width: 720px){
      .navlinks{
        flex-wrap: nowrap;
        overflow-x: auto;
        white-space: nowrap;
        width: 100%;
        justify-content: flex-start;
        padding-bottom: 8px;
        -webkit-overflow-scrolling: touch;
      }
      .navlinks a{ flex: 0 0 auto; }
      .top-actions{ width: 100%; justify-content: space-between; }
    }
  `;
    document.head.appendChild(style);
}

function getCookie(name) {
    const v = document.cookie.split("; ").find(x => x.startsWith(name + "="));
    return v ? decodeURIComponent(v.split("=").slice(1).join("=")) : null;
}

async function doLogout() {
    // Logout endpoint usually needs POST + CSRF
    const token = getCookie("XSRF-TOKEN");
    await fetch("/logout", {
        method: "POST",
        credentials: "include",
        headers: {
            // try both common CSRF header names to be safe
            ...(token ? { "X-XSRF-TOKEN": token, "X-CSRF-TOKEN": token } : {})
        }
    });

    window.location.href = "/index.html";
}

function markActiveLinks(root) {
    const path = window.location.pathname || "";
    root.querySelectorAll(".navlinks a").forEach(a => {
        const href = a.getAttribute("href") || "";
        // Match exact file path, ignore query
        if (href && path.endsWith(href)) a.classList.add("active");
    });
}

export async function mountNavbar() {
    ensureNavbarStyles();

    const mount =
        document.getElementById("navbarMount") ||
        document.querySelector("#navbarMount") ||
        null;

    if (!mount) return;

    // Create skeleton immediately
    mount.innerHTML = `
    <header class="topbar">
      <a class="brand" href="/home.html">
        <span class="brand-badge"></span>
        UniMateLK
      </a>

      <nav class="navlinks" id="navLinks">
        <a href="/home.html">Home</a>
      </nav>

      <div class="top-actions" id="navActions">
        <span class="hello">Loading…</span>
      </div>
    </header>
  `;

    // load auth state
    await initCsrf();

    let me = null;
    try {
        me = await apiFetch("/api/me");
    } catch (_) {
        me = null;
    }

    const navLinks = mount.querySelector("#navLinks");
    const navActions = mount.querySelector("#navActions");

    if (!navLinks || !navActions) return;

    if (!me || me.authenticated === false) {
        // not logged in
        navLinks.innerHTML = `
      <a href="/home.html">Home</a>
      <a href="/index.html">Login</a>
    `;
        navActions.innerHTML = `<span class="hello">Not logged in</span>`;
        markActiveLinks(mount);
        return;
    }

    const role = String(me.role || "").toUpperCase();
    const name = me.name || "User";

    // Logged in links
    navLinks.innerHTML = `
    <a href="/home.html">Home</a>
    <a href="/profile.html">Profile</a>
    <a href="/preferences.html">Preferences</a>
    <a href="/matches.html">Matches</a>
    <a href="/requests.html">Requests</a>
    <a href="/chat.html">Chat</a>
    <a href="/friends.html">Friends</a>
    ${role === "ADMIN" ? `<a href="/admin.html">Admin</a>` : ``}
  `;

    navActions.innerHTML = `
    <span class="hello">Hi, ${name}</span>
    <button class="logout-btn" type="button" id="logoutBtn">Logout</button>
  `;

    mount.querySelector("#logoutBtn")?.addEventListener("click", async () => {
        try {
            await doLogout();
        } catch (e) {
            // if logout fails, still redirect to index
            window.location.href = "/index.html";
        }
    });

    markActiveLinks(mount);
}