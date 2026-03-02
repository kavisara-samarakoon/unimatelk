// unimatelk/src/main/resources/static/js/profile.js
import { apiFetch, initCsrf } from "./api.js";

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

function collectPayload() {
    return {
        name: $("fullName")?.value?.trim() || "",
        phone: $("phone")?.value?.trim() || "",
        campus: $("university")?.value?.trim() || "",
        faculty: $("faculty")?.value?.trim() || "",
        degree: $("degree")?.value?.trim() || "",
        yearOfStudy: $("year")?.value ? Number($("year").value) : null,
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
        if (String(e.message || "").includes("401")) {
            showMsg("Not logged in. Redirecting...", "error");
            setTimeout(goLogin, 600);
            return;
        }
        console.error(e);
        showMsg("Failed to load profile: " + e.message, "error");
    }
}

async function saveProfile() {
    try {
        showMsg("Saving profile...", "info");
        const payload = collectPayload();
        const data = await apiFetch(SAVE_URL, { method: "PUT", body: payload });
        fillForm(data);
        showMsg("Profile saved successfully!", "success");
    } catch (e) {
        if (String(e.message || "").includes("401")) {
            showMsg("Not logged in. Redirecting...", "error");
            setTimeout(goLogin, 600);
            return;
        }
        console.error(e);
        showMsg("Profile save failed: " + e.message, "error");
    }
}

async function uploadPhoto() {
    const fileInput = $("photo");
    if (!fileInput || !fileInput.files || !fileInput.files[0]) {
        showMsg("Choose an image first.", "error");
        return;
    }

    const f = fileInput.files[0];
    const maxBytes = 5 * 1024 * 1024;
    if (f.size > maxBytes) {
        showMsg("Image too large. Choose under 5MB.", "error");
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
        showMsg("Image uploaded successfully!", "success");
    } catch (e) {
        if (String(e.message || "").includes("401")) {
            showMsg("Not logged in. Redirecting...", "error");
            setTimeout(goLogin, 600);
            return;
        }
        console.error(e);
        showMsg("Image upload failed: " + e.message, "error");
    }
}

function wireEvents() {
    const saveBtn = $("saveBtn");
    if (saveBtn) saveBtn.addEventListener("click", saveProfile);

    const photo = $("photo");
    if (photo) photo.addEventListener("change", uploadPhoto);
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();
    wireEvents();
    await loadProfile();
});