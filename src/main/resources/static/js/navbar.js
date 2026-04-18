import { apiFetch, initCsrf } from "./api.js";

function ensureNavbarStyles() {
    if (document.getElementById("navbarCoreStyles")) return;

    const style = document.createElement("style");
    style.id = "navbarCoreStyles";
    style.textContent = `
    .topbar{
      width: min(1380px, 98vw);
      margin: 0 auto 8px auto;
      padding: 12px 16px;
      border-radius: 28px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 14px;
      flex-wrap: nowrap;

      background: linear-gradient(135deg, rgba(255,255,255,0.12), rgba(255,255,255,0.06));
      border: 1px solid rgba(255,255,255,0.22);
      box-shadow: 0 18px 42px rgba(0,0,0,0.22);
      backdrop-filter: blur(18px) saturate(145%);
      -webkit-backdrop-filter: blur(18px) saturate(145%);
    }

    .brand,
    .navlinks,
    .top-actions{
      position: relative;
      z-index: 1;
    }

    .brand{
      display: flex;
      align-items: center;
      gap: 10px;
      min-width: max-content;
      text-decoration: none;
      color: rgba(255,255,255,0.95);
      font-weight: 900;
      font-size: 18px;
      letter-spacing: 0.2px;
      white-space: nowrap;
    }

    .brand-badge{
      width: 12px;
      height: 12px;
      border-radius: 50%;
      flex-shrink: 0;
      background: linear-gradient(90deg, #7c4dff, #00d4ff);
      box-shadow: 0 0 14px rgba(0,212,255,0.30);
    }

    .navlinks{
      flex: 1;
      min-width: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 10px;
      flex-wrap: wrap;
    }

    .navlinks a{
      text-decoration: none;
      color: rgba(255,255,255,0.92);
      padding: 10px 16px;
      border-radius: 999px;
      border: 1px solid rgba(255,255,255,0.14);
      background: rgba(255,255,255,0.07);
      font-weight: 800;
      font-size: 14px;
      line-height: 1;
      white-space: nowrap;
      transition: transform .18s ease, background .18s ease, border-color .18s ease, box-shadow .18s ease;
    }

    .navlinks a:hover{
      transform: translateY(-1px);
      background: rgba(255,255,255,0.10);
      border-color: rgba(255,255,255,0.24);
    }

    .navlinks a.active{
      background: rgba(255,255,255,0.12);
      border-color: rgba(0,212,255,0.46);
      box-shadow: 0 0 0 3px rgba(0,212,255,0.10);
    }

    .top-actions{
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 12px;
      min-width: max-content;
      flex-shrink: 0;
    }

    .hello{
      color: rgba(255,255,255,0.82);
      font-size: 14px;
      white-space: nowrap;
    }

    .logout-btn{
      border: none;
      cursor: pointer;
      padding: 10px 18px;
      border-radius: 999px;
      color: #ffffff;
      font-weight: 900;
      font-size: 14px;
      background: linear-gradient(90deg, #7c4dff, #00d4ff);
      box-shadow: 0 10px 24px rgba(0,212,255,0.18);
      transition: transform .18s ease, box-shadow .18s ease, opacity .18s ease;
    }

    .logout-btn:hover{
      transform: translateY(-1px);
      box-shadow: 0 12px 28px rgba(0,212,255,0.24);
      opacity: 0.96;
    }

    .navlinks::-webkit-scrollbar{
      display: none;
    }

    @media (max-width: 1180px){
      .topbar{
        flex-wrap: wrap;
        justify-content: space-between;
      }

      .navlinks{
        order: 3;
        width: 100%;
        justify-content: flex-start;
        flex-wrap: nowrap;
        overflow-x: auto;
        padding-bottom: 4px;
        -webkit-overflow-scrolling: touch;
      }

      .navlinks a{
        flex: 0 0 auto;
      }
    }

    @media (max-width: 780px){
      .topbar{
        width: min(100%, calc(100vw - 16px));
        padding: 12px 14px;
        gap: 10px;
      }

      .brand{
        font-size: 17px;
      }

      .top-actions{
        width: 100%;
        justify-content: space-between;
      }

      .hello{
        font-size: 13px;
      }

      .logout-btn{
        padding: 9px 14px;
      }
    }
  `;
    document.head.appendChild(style);
}

function getCookie(name) {
    const item = document.cookie.split("; ").find(x => x.startsWith(name + "="));
    return item ? decodeURIComponent(item.split("=").slice(1).join("=")) : null;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

async function doLogout() {
    const token = getCookie("XSRF-TOKEN");

    await fetch("/logout", {
        method: "POST",
        credentials: "include",
        headers: {
            ...(token ? { "X-XSRF-TOKEN": token, "X-CSRF-TOKEN": token } : {})
        }
    });

    window.location.href = "/index.html";
}

function normalizePath(path) {
    if (!path) return "";
    return path.split("?")[0].replace(/\/+$/, "");
}

function markActiveLinks(root) {
    const currentPath = normalizePath(window.location.pathname || "");

    root.querySelectorAll(".navlinks a").forEach((a) => {
        const href = normalizePath(a.getAttribute("href") || "");
        if (!href) return;

        if (currentPath.endsWith(href) || (currentPath === "" && href === "/home.html")) {
            a.classList.add("active");
        }
    });
}

export async function mountNavbar() {
    ensureNavbarStyles();

    const mount = document.getElementById("navbarMount");
    if (!mount) return;

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
          <span class="hello">Loading...</span>
        </div>
      </header>
    `;

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
        navLinks.innerHTML = `
          <a href="/home.html">Home</a>
          <a href="/index.html">Login</a>
        `;

        navActions.innerHTML = `<span class="hello">Not logged in</span>`;
        markActiveLinks(mount);
        return;
    }

    const role = String(me.role || "").toUpperCase();
    const safeName = escapeHtml(me.name || "User");

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
      <span class="hello">Hi, ${safeName}</span>
      <button class="logout-btn" type="button" id="logoutBtn">Logout</button>
    `;

    mount.querySelector("#logoutBtn")?.addEventListener("click", async () => {
        try {
            await doLogout();
        } catch (_) {
            window.location.href = "/index.html";
        }
    });

    markActiveLinks(mount);
}