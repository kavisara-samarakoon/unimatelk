import { initCsrf, apiFetch } from "./api.js";

async function refreshStatus() {
    const statusEl = document.getElementById("statusText");
    const userLabel = document.getElementById("userLabel");
    const loginBtn = document.getElementById("loginBtn");
    const logoutBtn = document.getElementById("logoutBtn");
    const userPic = document.getElementById("userPic");
    const adminLink = document.getElementById("adminLink");

    try {
        const data = await apiFetch("/api/me", { method: "GET" });

        if (data.authenticated) {
            statusEl.textContent = `Logged in as ${data.name} (${data.email})`;
            userLabel.textContent = data.name || "";

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
        // Load CSRF token + correct header name from server
        const { data } = await initCsrf();

        // If token is null, SecurityConfig is still wrong (CSRF disabled for /api/**)
        if (!data || !data.token || !data.headerName) {
            console.error("CSRF data:", data);
            alert("CSRF token is missing. Fix SecurityConfig (do NOT ignore /api/**).");
            return;
        }

        // This POST will now include the correct CSRF header automatically via apiFetch()
        await apiFetch("/logout", { method: "POST" });

    } catch (e) {
        console.error("Logout failed:", e);
        alert("Logout failed. Open console and check Network → POST /logout.");
        return;
    }

    location.replace("/index.html?loggedOut=1&ts=" + Date.now());
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf().catch(() => {});
    await refreshStatus();

    const logoutBtn = document.getElementById("logoutBtn");
    if (logoutBtn) logoutBtn.addEventListener("click", doLogout);
});

window.addEventListener("pageshow", refreshStatus);
window.addEventListener("focus", refreshStatus);
