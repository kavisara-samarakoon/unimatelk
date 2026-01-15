async function refreshStatus() {
    const statusEl = document.getElementById("statusText");
    const userLabel = document.getElementById("userLabel");
    const loginBtn = document.getElementById("loginBtn");
    const logoutBtn = document.getElementById("logoutBtn");
    const userPic = document.getElementById("userPic");

    try {
        const res = await fetch("/api/me", { credentials: "same-origin" });
        const data = await res.json();

        if (data.authenticated) {
            statusEl.textContent = `Logged in as ${data.name} (${data.email})`;
            userLabel.textContent = `${data.name}`;

            loginBtn.style.display = "none";
            logoutBtn.style.display = "inline-block";

            if (data.picture) {
                userPic.src = data.picture;
                userPic.style.display = "inline-block";
            }
        } else {
            statusEl.textContent = "Not logged in. Click 'Login with Google'.";
            userLabel.textContent = "";

            loginBtn.style.display = "inline-block";
            logoutBtn.style.display = "none";
            userPic.style.display = "none";
        }
    } catch (e) {
        statusEl.textContent = "Error loading status. Check console logs.";
        console.error(e);
    }
}

document.addEventListener("DOMContentLoaded", refreshStatus);
