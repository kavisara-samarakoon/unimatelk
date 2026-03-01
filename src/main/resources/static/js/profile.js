// src/main/resources/static/js/profile.js
// FULL FILE - Module script (profile.html uses type="module")

const $ = (id) => document.getElementById(id);

function showMsg(text, type = "info") {
    const msg = $("msg");
    if (!msg) return;
    msg.textContent = text;
    msg.className = "toast " + type;
}

const GET_URL = "/api/my/profile";
const SAVE_URL = "/api/my/profile";
const UPLOAD_URL = "/api/my/profile/photo";

function goLogin() {
    window.location.href = "/oauth2/authorization/google";
}

async function apiFetch(url, options = {}) {
    const opts = { ...options };
    opts.headers = opts.headers ? { ...opts.headers } : {};
    opts.credentials = "same-origin";
    opts.redirect = "manual";

    const res = await fetch(url, opts);

    if (res.type === "opaqueredirect" || res.status === 0 || res.status === 401) {
        throw new Error("LOGIN_REQUIRED");
    }

    const text = await res.text();
    let data = text;
    const ct = (res.headers.get("content-type") || "").toLowerCase();
    if (ct.includes("application/json")) {
        try { data = text ? JSON.parse(text) : null; } catch {}
    }

    if (!res.ok) {
        const msg =
            data && data.message ? data.message :
                typeof data === "string" ? data :
                    JSON.stringify(data);
        throw new Error(`HTTP ${res.status}: ${msg}`);
    }

    return data;
}

function collectPayload() {
    return {
        fullName: $("fullName")?.value?.trim() || "",
        phone: $("phone")?.value?.trim() || "",
        university: $("university")?.value?.trim() || "",
        faculty: $("faculty")?.value?.trim() || "",
        degree: $("degree")?.value?.trim() || "",
        year: $("year")?.value ? Number($("year").value) : null,
        bio: $("bio")?.value?.trim() || ""
    };
}

function fillForm(p) {
    if (!p || typeof p !== "object") return;

    if ($("fullName")) $("fullName").value = p.name ?? "";
    if ($("phone")) $("phone").value = p.phone ?? "";
    if ($("university")) $("university").value = p.campus ?? "";
    if ($("faculty")) $("faculty").value = p.faculty ?? "";
    if ($("degree")) $("degree").value = p.degree ?? "";
    if ($("year")) $("year").value = p.yearOfStudy ?? "";
    if ($("bio")) $("bio").value = p.bio ?? "";

    // ✅ Keep photo always
    if (p.profilePhotoPath && $("avatarPreview")) {
        $("avatarPreview").src = p.profilePhotoPath;
    }
}

async function loadProfile() {
    try {
        showMsg("Loading profile...", "info");
        const data = await apiFetch(GET_URL, { method: "GET" });
        fillForm(data);
        showMsg("Profile loaded.", "success");
    } catch (e) {
        if (e.message === "LOGIN_REQUIRED") {
            showMsg("Not logged in. Redirecting...", "error");
            setTimeout(goLogin, 600);
            return;
        }
        console.error(e);
        showMsg("❌ Failed to load profile: " + e.message, "error");
    }
}

async function saveProfile() {
    try {
        showMsg("Saving profile...", "info");
        const payload = collectPayload();

        const data = await apiFetch(SAVE_URL, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        fillForm(data);
        showMsg("✅ Profile saved successfully!", "success");
    } catch (e) {
        if (e.message === "LOGIN_REQUIRED") {
            showMsg("Not logged in. Redirecting...", "error");
            setTimeout(goLogin, 600);
            return;
        }
        console.error(e);
        showMsg("❌ Profile save failed: " + e.message, "error");
    }
}

async function uploadPhoto() {
    const fileInput = $("photo");
    if (!fileInput || !fileInput.files || !fileInput.files[0]) {
        showMsg("❌ Choose an image first.", "error");
        return;
    }

    const f = fileInput.files[0];
    const maxBytes = 10 * 1024 * 1024;
    if (f.size > maxBytes) {
        showMsg("❌ Image too large. Choose under 10MB.", "error");
        return;
    }

    const fd = new FormData();
    fd.append("file", f);
    fd.append("image", f);
    fd.append("photo", f);

    try {
        showMsg("Uploading image...", "info");
        const res = await apiFetch(UPLOAD_URL, { method: "POST", body: fd });

        if (res && res.url && $("avatarPreview")) {
            $("avatarPreview").src = res.url;
        }

        showMsg("✅ Image uploaded successfully!", "success");
    } catch (e) {
        if (e.message === "LOGIN_REQUIRED") {
            showMsg("Not logged in. Redirecting...", "error");
            setTimeout(goLogin, 600);
            return;
        }
        console.error(e);
        showMsg("❌ Image upload failed: " + e.message, "error");
    }
}

function wireEvents() {
    const saveBtn = $("saveBtn");
    if (saveBtn) saveBtn.addEventListener("click", saveProfile);

    const photo = $("photo");
    if (photo) photo.addEventListener("change", uploadPhoto);
}

document.addEventListener("DOMContentLoaded", async () => {
    wireEvents();
    await loadProfile();
});