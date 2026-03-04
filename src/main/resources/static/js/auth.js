const shell = document.getElementById("shell");
const toast = document.getElementById("toast");

const goSignup = document.getElementById("goSignup");
const goSignin = document.getElementById("goSignin");

const signinForm = document.getElementById("signinForm");
const signupForm = document.getElementById("signupForm");

function getContinueUrl(){
    const v = shell?.getAttribute("data-continue");
    return (v && v.trim()) ? v.trim() : "/home.html";
}

function showToast(msg){
    if (!toast) return;
    toast.textContent = msg;
    toast.classList.add("show");
    clearTimeout(showToast._t);
    showToast._t = setTimeout(() => toast.classList.remove("show"), 2400);
}

function setMode(signup){
    shell?.classList.toggle("right-active", !!signup);
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
        // Friendly errors if endpoints are not implemented
        if (res.status === 404){
            throw new Error("This feature is not enabled. Please use Google login.");
        }
        if (res.status === 401 || res.status === 403){
            throw new Error("Invalid credentials or not allowed.");
        }

        const msg = (body && body.message) ? body.message :
            (typeof body === "string" && body) ? body :
                `Request failed (${res.status})`;
        throw new Error(msg);
    }

    return body;
}

// ✅ Auto redirect if already logged in
async function redirectIfLoggedIn(){
    try{
        const res = await fetch("/api/me", { credentials: "same-origin" });

        if (!res.ok) return;
        const me = await res.json().catch(() => null);

        if (me && me.authenticated){
            window.location.href = getContinueUrl();   // ✅ HOME
        }
    }catch(_){}
}

signinForm?.addEventListener("submit", async (e) => {
    e.preventDefault();
    try{
        const email = document.getElementById("signinEmail")?.value?.trim() || "";
        const password = document.getElementById("signinPassword")?.value || "";

        if (!email || !password){
            showToast("❌ Enter email and password.");
            return;
        }

        await postJson("/api/auth/login", { email, password });
        showToast("✅ Signed in!");
        window.location.href = getContinueUrl();       // ✅ HOME
    }catch(err){
        showToast("❌ " + (err?.message || "Login failed"));
    }
});

signupForm?.addEventListener("submit", async (e) => {
    e.preventDefault();
    try{
        const name = document.getElementById("signupName")?.value?.trim() || "";
        const email = document.getElementById("signupEmail")?.value?.trim() || "";
        const password = document.getElementById("signupPassword")?.value || "";

        if (!name || !email || !password){
            showToast("❌ Please fill all fields.");
            return;
        }

        await postJson("/api/auth/signup", { name, email, password });
        showToast("✅ Account created!");
        window.location.href = "/profile.html";        // profile setup after signup
    }catch(err){
        showToast("❌ " + (err?.message || "Signup failed"));
    }
});

// ✅ Forgot password (UI safe)
document.getElementById("forgotBtn")?.addEventListener("click", () => {
    showToast("ℹ️ Password reset not available yet. Please use Google login.");
});

// Swipe support
let startX = null;
shell?.addEventListener("touchstart", (e) => {
    if (!e.touches || e.touches.length !== 1) return;
    startX = e.touches[0].clientX;
}, { passive: true });

shell?.addEventListener("touchend", (e) => {
    if (startX === null) return;
    const endX = (e.changedTouches && e.changedTouches[0]) ? e.changedTouches[0].clientX : startX;
    const dx = endX - startX;
    startX = null;
    if (Math.abs(dx) < 50) return;
    if (dx < 0) setMode(true);
    else setMode(false);
});

document.addEventListener("DOMContentLoaded", redirectIfLoggedIn);