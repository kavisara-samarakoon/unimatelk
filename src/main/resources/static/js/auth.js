const shell = document.getElementById("shell");
const toast = document.getElementById("toast");

const goSignup = document.getElementById("goSignup");
const goSignin = document.getElementById("goSignin");

const signinForm = document.getElementById("signinForm");
const signupForm = document.getElementById("signupForm");

function showToast(msg){
    toast.textContent = msg;
    toast.classList.add("show");
    clearTimeout(showToast._t);
    showToast._t = setTimeout(() => toast.classList.remove("show"), 2400);
}

function setMode(signup){
    shell.classList.toggle("right-active", !!signup);
}

goSignup?.addEventListener("click", () => setMode(true));
goSignin?.addEventListener("click", () => setMode(false));

async function postJson(url, data){
    const res = await fetch(url, {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data)
    });

    const ct = res.headers.get("content-type") || "";
    const body = ct.includes("application/json")
        ? await res.json().catch(() => null)
        : await res.text().catch(() => "");

    if (!res.ok){
        const msg = (body && body.message) ? body.message :
            (typeof body === "string" && body) ? body :
                `Request failed (${res.status})`;
        throw new Error(msg);
    }

    return body;
}

// ✅ Auto redirect if already logged in (Google or local)
async function redirectIfLoggedIn(){
    try{
        const res = await fetch("/api/me", { credentials: "same-origin" });
        const me = await res.json();
        if (me && me.authenticated){
            window.location.href = "/matches.html";
        }
    }catch(_){}
}

signinForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    try{
        const email = document.getElementById("signinEmail").value.trim();
        const password = document.getElementById("signinPassword").value;

        await postJson("/api/auth/login", { email, password });
        showToast("✅ Signed in!");
        window.location.href = "/matches.html";
    }catch(err){
        showToast("❌ " + err.message);
    }
});

signupForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    try{
        const name = document.getElementById("signupName").value.trim();
        const email = document.getElementById("signupEmail").value.trim();
        const password = document.getElementById("signupPassword").value;

        await postJson("/api/auth/signup", { name, email, password });
        showToast("✅ Account created!");
        window.location.href = "/profile.html";
    }catch(err){
        showToast("❌ " + err.message);
    }
});

// ✅ Forgot password works now
document.getElementById("forgotBtn")?.addEventListener("click", async () => {
    try{
        const email = prompt("Enter your email to reset password:");
        if (!email) return;

        const data = await postJson("/api/auth/forgot", { email: email.trim() });
        showToast("✅ Reset link created!");

        // Go to reset page
        window.location.href = data.resetUrl;
    }catch(err){
        showToast("❌ " + err.message);
    }
});

// Swipe support
let startX = null;
shell.addEventListener("touchstart", (e) => {
    if (!e.touches || e.touches.length !== 1) return;
    startX = e.touches[0].clientX;
}, { passive: true });

shell.addEventListener("touchend", (e) => {
    if (startX === null) return;
    const endX = (e.changedTouches && e.changedTouches[0]) ? e.changedTouches[0].clientX : startX;
    const dx = endX - startX;
    startX = null;
    if (Math.abs(dx) < 50) return;
    if (dx < 0) setMode(true);
    else setMode(false);
});

document.addEventListener("DOMContentLoaded", redirectIfLoggedIn);
