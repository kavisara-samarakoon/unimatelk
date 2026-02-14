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
    showToast._t = setTimeout(() => toast.classList.remove("show"), 2200);
}

function setMode(signup){
    shell.classList.toggle("right-active", !!signup);
}

// Buttons
goSignup?.addEventListener("click", () => setMode(true));
goSignin?.addEventListener("click", () => setMode(false));

// Demo forms (email/password not implemented unless you add backend)
signinForm.addEventListener("submit", (e) => {
    e.preventDefault();
    showToast("Email/password login is not implemented. Use Google (G).");
});

signupForm.addEventListener("submit", (e) => {
    e.preventDefault();
    showToast("Email/password sign up is not implemented. Use Google (G).");
});

// Forgot password (UI)
document.getElementById("forgotBtn")?.addEventListener("click", () => {
    showToast("Forgot password is UI only (not implemented).");
});

/* Swipe support (like your video) */
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

    // swipe threshold
    if (Math.abs(dx) < 50) return;

    // swipe left => signup, swipe right => signin
    if (dx < 0) setMode(true);
    else setMode(false);
});
