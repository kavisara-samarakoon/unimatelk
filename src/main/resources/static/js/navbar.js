// /src/main/resources/static/js/navbar.js

export function mountNavbar() {
    const mount = document.getElementById("navbarMount");
    if (!mount) return;

    const path = window.location.pathname.replace(/\/+$/, ""); // remove trailing /

    const isActive = (href) => {
        const cleanHref = href.replace(/\/+$/, "");
        return path === cleanHref || path.endsWith(cleanHref);
    };

    // If your backend uses /logout to logout
    // If not logged in, /logout may redirect - that's okay.
    const links = [
        { href: "/home.html", label: "Home" },
        { href: "/profile.html", label: "Profile" },
        { href: "/preferences.html", label: "Preferences" },
        { href: "/matches.html", label: "Matches" },
        { href: "/requests.html", label: "Requests" },
        { href: "/chat.html", label: "Chat" },
        { href: "/admin.html", label: "Admin" }, // ✅ keep always visible
    ];

    // Detect “logged in” (simple & safe):
    // If your backend sets a session cookie, this fetch will return 200.
    // If not logged in, it will usually return 401/302.
    // We will fail gracefully.
    checkLogin()
        .then((loggedIn) => {
            mount.innerHTML = navbarHtml(links, isActive, loggedIn);
        })
        .catch(() => {
            // even if API check fails, still render navbar
            mount.innerHTML = navbarHtml(links, isActive, true);
        });
}

function navbarHtml(links, isActive, loggedIn) {
    return `
    <header class="topbar">
      <div class="brand">
        <span class="brand-badge"></span>
        UniMateLK
      </div>

      <nav class="navlinks">
        ${links
        .map(
            (l) => `
            <a href="${l.href}" ${
                isActive(l.href) ? 'aria-current="page"' : ""
            }>
              ${l.label}
            </a>
          `
        )
        .join("")}
      </nav>

      <div class="top-actions">
        ${
        loggedIn
            ? `<a class="btn ghost" href="/logout">Logout</a>`
            : `<a class="btn ghost" href="/index.html">Login</a>`
    }
      </div>
    </header>
  `;
}

async function checkLogin() {
    // ✅ Use an endpoint you already have (best)
    // If you DON'T have one, this will fail and navbar still works.
    // Try /api/me or /api/auth/me if exists.
    const candidates = ["/api/me", "/api/auth/me", "/api/user/me"];

    for (const url of candidates) {
        try {
            const res = await fetch(url, {
                method: "GET",
                credentials: "same-origin",
                headers: { "X-Requested-With": "XMLHttpRequest" },
                redirect: "manual",
            });

            if (res.status === 200) return true;
            if (res.status === 401 || res.status === 403) return false;
        } catch {
            // try next
        }
    }

    // fallback: assume logged in (so Logout appears)
    return true;
}

// Auto-mount on every page
document.addEventListener("DOMContentLoaded", mountNavbar);
