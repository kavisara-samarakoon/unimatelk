import { initCsrf, apiFetch } from "./api.js";

async function refreshStatus() {
    const statusEl = document.getElementById("statusText");
    const userLabel = document.getElementById("userLabel");
    const loginBtn = document.getElementById("loginBtn");
    const logoutBtn = document.getElementById("logoutBtn");
    const userPic = document.getElementById("userPic");
    const adminLink = document.getElementById("adminLink");

    try {
        // no-store avoids stale UI (back/forward cache issues)
        const data = await apiFetch("/api/me", { method: "GET", cache: "no-store" });

        if (data.authenticated) {
            statusEl.textContent = `Logged in as ${data.name} (${data.email})`;
            userLabel.textContent = `${data.name}`;

            loginBtn.style.display = "none";
            logoutBtn.style.display = "inline-block";

            if (data.picture) {
                userPic.src = data.picture;
                userPic.style.display = "inline-block";
            } else {
                userPic.style.display = "none";
            }

            adminLink.style.display = (data.role === "ADMIN") ? "inline-block" : "none";
        } else {
            statusEl.textContent = "Not logged in. Click 'Login with Google'.";
            userLabel.textContent = "";

            loginBtn.style.display = "inline-block";
            logoutBtn.style.display = "none";
            userPic.style.display = "none";
            adminLink.style.display = "none";
        }
    } catch (e) {
        statusEl.textContent = "Error loading status. Open console to see details.";
        console.error(e);
    }
}

async function doLogout() {
    try {
        // ensure XSRF-TOKEN cookie exists
        await initCsrf();

        // POST /logout with CSRF header (apiFetch handles it)
        await apiFetch("/logout", { method: "POST" });
    } catch (e) {
        console.error("Logout failed:", e);
    } finally {
        // force real reload (prevents stale UI)
        location.replace("/index.html?loggedOut=1&ts=" + Date.now());
    }
}

document.addEventListener("DOMContentLoaded", async () => {
    // generate CSRF cookie early (helps logout + future POSTs)
    await initCsrf().catch(() => {});
    await refreshStatus();

    const logoutBtn = document.getElementById("logoutBtn");
    if (logoutBtn) logoutBtn.addEventListener("click", doLogout);
});

// Fix “sometimes old UI” when returning using back button or switching tabs
window.addEventListener("pageshow", refreshStatus);
window.addEventListener("focus", refreshStatus);
