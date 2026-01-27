import { apiFetch, initCsrf } from "./api.js";

function el(id) { return document.getElementById(id); }
function setMsg(text) { el("msg").textContent = text || ""; }

function qp(name) {
    const u = new URL(window.location.href);
    return u.searchParams.get(name);
}

function escapeHtml(str) {
    return String(str || "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

async function load() {
    const id = qp("id");
    if (!id) {
        setMsg("Missing user id");
        return;
    }
    setMsg("Loading...");

    try {
        const [profile, explain, incoming, outgoing, rooms] = await Promise.all([
            apiFetch(`/api/profiles/${id}`),
            apiFetch(`/api/matches/explain/${id}`),
            apiFetch(`/api/requests/incoming`).catch(() => []),
            apiFetch(`/api/requests/outgoing`).catch(() => []),
            apiFetch(`/api/chat/rooms`).catch(() => [])
        ]);

        el("title").textContent = profile.name ? profile.name : "Profile";

        const img = profile.profilePhotoPath ? profile.profilePhotoPath : "/images/default-avatar.svg";
        el("profileCard").innerHTML = `
            <div class="match-card">
                <img src="${img}" alt="avatar" onerror="this.src='/images/default-avatar.svg'"/>
                <div style="flex:1">
                    <div><strong>${escapeHtml(profile.name || "")}</strong></div>
                    <div class="muted">${escapeHtml(profile.campus || "")} · ${escapeHtml(profile.degree || "")} · Year ${profile.yearOfStudy ?? ""}</div>
                    <div class="muted">Gender: ${escapeHtml(profile.gender || "")} · Prefers: ${escapeHtml(profile.genderPreference || "Any")}</div>
                    <div style="margin-top:8px;"><strong>Move-in:</strong> ${escapeHtml(profile.moveInMonth || "")}</div>
                    <div style="margin-top:8px;"><strong>Bio:</strong><br/>${escapeHtml(profile.bio || "")}</div>
                    ${profile.contactVisible ? `
                        <div style="margin-top:10px;"><strong>Contact</strong></div>
                        <div class="muted">Phone: ${escapeHtml(profile.phone || "")}</div>
                        <div class="muted">Facebook: ${escapeHtml(profile.facebookUrl || "")}</div>
                        <div class="muted">Instagram: ${escapeHtml(profile.instagramUrl || "")}</div>
                    ` : `
                        <div class="muted" style="margin-top:10px;">Contact hidden until you both match.</div>
                    `}
                </div>
            </div>
        `;

        const why = (explain.whyMatched || []).map(x => `<div class="muted">${escapeHtml(x)}</div>`).join("");
        el("matchCard").innerHTML = `
            <h3>Compatibility</h3>
            <div class="badge">${explain.matchPercent}% match</div>
            <div style="margin-top:10px;">${why}</div>
        `;

        // Determine request/chat state
        const incomingReq = incoming.find(r => String(r.fromUserId) === String(id) && r.status === "PENDING");
        const outgoingReq = outgoing.find(r => String(r.toUserId) === String(id) && r.status === "PENDING");
        const room = rooms.find(r => String(r.otherUserId) === String(id));

        let actionsHtml = `<h3>Actions</h3>`;

        if (room) {
            actionsHtml += `<a class="btn" href="/chat.html?room=${room.roomId}">Open Chat</a>`;
        } else if (incomingReq) {
            actionsHtml += `<div class="muted">This user requested to match with you.</div>`;
            actionsHtml += `
                <button class="btn" id="acceptBtn">Accept</button>
                <button class="btn secondary" id="rejectBtn">Reject</button>
            `;
        } else if (outgoingReq) {
            actionsHtml += `<div class="muted">Request pending...</div>`;
            actionsHtml += `<button class="btn secondary" id="cancelBtn">Cancel Request</button>`;
        } else {
            actionsHtml += `<button class="btn" id="requestBtn">Request Match</button>`;
        }
        el("actions").innerHTML = actionsHtml;

        if (el("requestBtn")) {
            el("requestBtn").addEventListener("click", async () => {
                setMsg("");
                el("requestBtn").disabled = true;
                try {
                    await apiFetch(`/api/requests/${id}`, { method: "POST" });
                    setMsg("Request sent");
                    await load();
                } catch (e) {
                    setMsg(e?.message || "Failed to request");
                } finally {
                    el("requestBtn").disabled = false;
                }
            });
        }
        if (el("acceptBtn")) {
            el("acceptBtn").addEventListener("click", async () => {
                el("acceptBtn").disabled = true;
                try {
                    await apiFetch(`/api/requests/${incomingReq.id}/accept`, { method: "POST" });
                    setMsg("Accepted! Chat unlocked.");
                    await load();
                } catch (e) {
                    setMsg(e?.message || "Failed to accept");
                } finally {
                    el("acceptBtn").disabled = false;
                }
            });
        }
        if (el("rejectBtn")) {
            el("rejectBtn").addEventListener("click", async () => {
                el("rejectBtn").disabled = true;
                try {
                    await apiFetch(`/api/requests/${incomingReq.id}/reject`, { method: "POST" });
                    setMsg("Rejected");
                    await load();
                } catch (e) {
                    setMsg(e?.message || "Failed to reject");
                } finally {
                    el("rejectBtn").disabled = false;
                }
            });
        }
        if (el("cancelBtn")) {
            el("cancelBtn").addEventListener("click", async () => {
                el("cancelBtn").disabled = true;
                try {
                    await apiFetch(`/api/requests/${outgoingReq.id}`, { method: "DELETE" });
                    setMsg("Cancelled");
                    await load();
                } catch (e) {
                    setMsg(e?.message || "Failed to cancel");
                } finally {
                    el("cancelBtn").disabled = false;
                }
            });
        }

        // Trust & Safety (Day 11)
        el("safety").innerHTML = `
            <h3>Trust & Safety</h3>
            <div style="display:flex; gap:10px; flex-wrap:wrap;">
                <button class="btn secondary" id="blockBtn">Block</button>
            </div>
            <div style="margin-top:10px;">
                <label>Report reason</label>
                <textarea id="reportReason" rows="3" placeholder="Explain what happened..."></textarea>
                <button class="btn danger" id="reportBtn" style="margin-top:8px;">Report</button>
            </div>
        `;

        el("blockBtn").addEventListener("click", async () => {
            if (!confirm("Block this user? They will disappear from your feed and chat.")) return;
            try {
                await apiFetch(`/api/safety/block/${id}`, { method: "POST" });
                setMsg("User blocked.");
            } catch (e) {
                setMsg(e?.message || "Failed to block");
            }
        });

        el("reportBtn").addEventListener("click", async () => {
            const reason = el("reportReason").value.trim();
            if (!reason) {
                setMsg("Please write a reason");
                return;
            }
            if (!confirm("Submit report?")) return;
            try {
                await apiFetch(`/api/safety/report/${id}`, {
                    method: "POST",
                    body: { reason }
                });
                setMsg("Report submitted.");
                el("reportReason").value = "";
            } catch (e) {
                setMsg(e?.message || "Failed to report");
            }
        });

        setMsg("");
    } catch (e) {
        setMsg(e?.message || "Failed to load profile");
    }
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();
    load();
});
