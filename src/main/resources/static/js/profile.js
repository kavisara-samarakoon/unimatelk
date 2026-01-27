import { initCsrf, apiFetch } from "./api.js";

const $ = (id) => document.getElementById(id);
const msg = (t) => { $("msg").textContent = t || ""; };

function fill(p) {
    $("campus").value = p.campus || "";
    $("degree").value = p.degree || "";
    $("yearOfStudy").value = p.yearOfStudy ?? "";
    $("gender").value = p.gender || "";
    $("genderPref").value = p.genderPreference || "";
    $("moveInMonth").value = p.moveInMonth || "";
    $("bio").value = p.bio || "";
    $("phone").value = p.phone || "";
    $("facebookUrl").value = p.facebookUrl || "";
    $("instagramUrl").value = p.instagramUrl || "";

    if (p.profilePhotoPath) { $("profileImg").src = p.profilePhotoPath; $("profileImg").style.display = "block"; }
    if (p.coverPhotoPath) { $("coverImg").src = p.coverPhotoPath; $("coverImg").style.display = "block"; }
}

async function load() {
    const p = await apiFetch("/api/profile/me");
    fill(p);
}

async function save() {
    const payload = {
        campus: $("campus").value,
        degree: $("degree").value,
        yearOfStudy: $("yearOfStudy").value ? Number($("yearOfStudy").value) : 1,
        gender: $("gender").value,
        genderPreference: $("genderPref").value,
        moveInMonth: $("moveInMonth").value,
        bio: $("bio").value,
        phone: $("phone").value,
        facebookUrl: $("facebookUrl").value,
        instagramUrl: $("instagramUrl").value
    };

    await apiFetch("/api/profile/me", { method: "PUT", body: JSON.stringify(payload) });
    msg("✅ Saved!");
    await load();
}

function getXsrfToken() {
    const m = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
    return m ? decodeURIComponent(m[1]) : "";
}

async function upload(kind) {
    const input = kind === "profile" ? $("profileFile") : $("coverFile");
    if (!input.files || !input.files[0]) return msg("Select an image first");

    const fd = new FormData();
    fd.append("file", input.files[0]);

    const endpoint = kind === "profile"
        ? "/api/profile/me/profile-photo"
        : "/api/profile/me/cover-photo";

    const res = await fetch(endpoint, {
        method: "POST",
        credentials: "same-origin",
        headers: { "X-XSRF-TOKEN": getXsrfToken() },
        body: fd
    });

    if (!res.ok) throw new Error(await res.text());
    msg("✅ Uploaded!");
    await load();
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();
    await load();

    $("saveBtn").addEventListener("click", () => save().catch(e => msg(e.message)));
    $("uploadProfileBtn").addEventListener("click", () => upload("profile").catch(e => msg(e.message)));
    $("uploadCoverBtn").addEventListener("click", () => upload("cover").catch(e => msg(e.message)));
});
