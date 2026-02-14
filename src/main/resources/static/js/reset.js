const toast = document.getElementById("toast");
const msg = document.getElementById("msg");

function showToast(t){
    toast.textContent = t;
    toast.classList.add("show");
    clearTimeout(showToast._t);
    showToast._t = setTimeout(() => toast.classList.remove("show"), 2400);
}

function qp(name){
    return new URL(window.location.href).searchParams.get(name);
}

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
        const m = (body && body.message) ? body.message :
            (typeof body === "string" && body) ? body :
                `Request failed (${res.status})`;
        throw new Error(m);
    }

    return body;
}

document.getElementById("resetForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    try{
        const token = qp("token");
        if (!token) throw new Error("Missing reset token.");

        const p1 = document.getElementById("newPassword").value;
        const p2 = document.getElementById("confirmPassword").value;

        if (p1.length < 8) throw new Error("Password must be at least 8 characters.");
        if (p1 !== p2) throw new Error("Passwords do not match.");

        await postJson("/api/auth/reset", { token, newPassword: p1 });
        showToast("✅ Password reset successful!");
        msg.textContent = "Password updated. Redirecting to login...";

        setTimeout(() => window.location.href = "/index.html", 1200);
    }catch(err){
        showToast("❌ " + err.message);
    }
});
