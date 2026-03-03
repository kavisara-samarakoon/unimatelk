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

let lastLoadedProfile = null; // used for cancel
let editMode = false;

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

function setEditMode(on) {
    editMode = on;

    // enable/disable inputs
    const inputs = ["fullName", "phone", "university", "faculty", "degree", "year", "bio"];
    inputs.forEach((id) => {
        const el = $(id);
        if (!el) return;
        el.disabled = !on;
        el.readOnly = !on; // for textarea compatibility
    });

    // enable/disable photo upload
    const photo = $("photo");
    if (photo) photo.disabled = !on;

    // toggle buttons
    if ($("editBtn")) $("editBtn").style.display = on ? "none" : "inline-block";
    if ($("saveBtn")) $("saveBtn").style.display = on ? "inline-block" : "none";
    if ($("cancelBtn")) $("cancelBtn").style.display = on ? "inline-block" : "none";

    showMsg(on ? "Edit mode enabled." : "View mode.", "info");
}

async function loadProfile() {
    try {
        showMsg("Loading profile...", "info");
        const data = await apiFetch(GET_URL, { method: "GET" });
        lastLoadedProfile = data;
        fillForm(data);
        showMsg("Profile loaded.", "success");

        // default to view mode when loaded
        setEditMode(false);
    } catch (e) {
        const msg = String(e?.message || "");

        if (msg.includes("401") || msg.includes("Failed to fetch")) {
            showMsg("Not logged in. Redirecting...", "error");
            setTimeout(goLogin, 400);
            return;
        }

        console.error(e);
        showMsg("Failed to load profile: " + msg, "error");
    }
}

async function saveProfile() {
    try {
        showMsg("Saving profile...", "info");
        const payload = collectPayload();
        const data = await apiFetch(SAVE_URL, { method: "PUT", body: payload });

        lastLoadedProfile = data;
        fillForm(data);

        showMsg("Profile saved successfully!", "success");
        setEditMode(false);
    } catch (e) {
        const msg = String(e?.message || "");
        if (msg.includes("401") || msg.includes("Failed to fetch")) {
            showMsg("Not logged in. Redirecting...", "error");
            setTimeout(goLogin, 400);
            return;
        }
        console.error(e);
        showMsg("Profile save failed: " + msg, "error");
    }
}

async function uploadPhoto() {
    if (!editMode) return; // upload only in edit mode

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

    try {
        showMsg("Uploading image...", "info");
        const res = await apiFetch(UPLOAD_URL, { method: "POST", body: fd });
        if (res && res.url && $("avatarPreview")) {
            $("avatarPreview").src = res.url;
        }
        showMsg("Image uploaded successfully!", "success");
    } catch (e) {
        const msg = String(e?.message || "");
        if (msg.includes("401") || msg.includes("Failed to fetch")) {
            showMsg("Not logged in. Redirecting...", "error");
            setTimeout(goLogin, 400);
            return;
        }
        console.error(e);
        showMsg("Image upload failed: " + msg, "error");
    }
}

function wireEvents() {
    if ($("editBtn")) $("editBtn").addEventListener("click", () => {
        setEditMode(true);
        // keep current values; just enable editing
    });

    if ($("cancelBtn")) $("cancelBtn").addEventListener("click", () => {
        if (lastLoadedProfile) fillForm(lastLoadedProfile);
        setEditMode(false);
        showMsg("Changes canceled.", "info");
    });

    if ($("saveBtn")) $("saveBtn").addEventListener("click", saveProfile);

    const photo = $("photo");
    if (photo) photo.addEventListener("change", uploadPhoto);
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();
    wireEvents();

    // verify session first
    try {
        await apiFetch("/api/me");
    } catch (_) {
        goLogin();
        return;
    }

    await loadProfile();
});