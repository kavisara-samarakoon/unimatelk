import { apiFetch, initCsrf } from "./api.js";

let page = 0;
const size = 10;

function el(id) { return document.getElementById(id); }

function setMsg(text) {
    el("msg").textContent = text || "";
}

function buildParams() {
    const params = new URLSearchParams();
    const campus = el("campus").value.trim();
    const degree = el("degree").value.trim();
    const year = el("year").value.trim();
    const genderPref = el("genderPref").value.trim();
    const keyword = el("keyword").value.trim();

    if (campus) params.set("campus", campus);
    if (degree) params.set("degree", degree);
    if (year) params.set("year", year);
    if (genderPref) params.set("genderPref", genderPref);
    if (keyword) params.set("keyword", keyword);

    params.set("page", String(page));
    params.set("size", String(size));
    return params.toString();
}

async function load() {
    setMsg("Loading...");
    el("list").innerHTML = "";

    try {
        const [feed, outgoing, rooms] = await Promise.all([
            apiFetch(`/api/matches/feed?${buildParams()}`),
            apiFetch(`/api/requests/outgoing`).catch(() => []),
            apiFetch(`/api/chat/rooms`).catch(() => [])
        ]);

        const outgoingByUser = new Map(outgoing.map(r => [r.toUserId, r]));
        const roomByUser = new Map(rooms.filter(r => r.otherUserId != null).map(r => [r.otherUserId, r.roomId]));

        el("pageText").textContent = `Page ${feed.page + 1} · Showing ${feed.items.length} of ${feed.total}`;

        if (feed.items.length === 0) {
            setMsg("No matches found. Try changing filters or completing more profile/preference info.");
            return;
        }
        setMsg("");

        for (const m of feed.items) {
            const card = document.createElement("div");
            card.className = "card";

            const img = m.profilePhotoPath ? m.profilePhotoPath : "/images/default-avatar.svg";
            const reasons = (m.whyMatched || []).map(x => `<div class="muted">${escapeHtml(x)}</div>`).join("");

            const outgoingReq = outgoingByUser.get(m.userId);
            const roomId = roomByUser.get(m.userId);

            let actionsHtml = `<a class="btn" href="/user.html?id=${m.userId}">View Profile</a>`;
            if (roomId) {
                actionsHtml += ` <a class="btn secondary" href="/chat.html?room=${roomId}">Open Chat</a>`;
            } else if (outgoingReq && outgoingReq.status === "PENDING") {
                actionsHtml += ` <span class="badge">Request Pending</span>`;
            } else {
                actionsHtml += ` <button class="btn" data-request="${m.userId}">Request Match</button>`;
            }

            card.innerHTML = `
                <div class="match-card">
                    <img src="${img}" alt="avatar" onerror="this.src='/images/default-avatar.svg'"/>
                    <div style="flex:1">
                        <div style="display:flex; justify-content:space-between; gap:12px; flex-wrap:wrap;">
                            <div>
                                <strong>${escapeHtml(m.name || "User")}</strong>
                                <div class="muted">${escapeHtml(m.campus || "")} · ${escapeHtml(m.degree || "")} · Year ${m.yearOfStudy ?? ""}</div>
                            </div>
                            <div class="badge">${m.matchPercent}% match</div>
                        </div>
                        <div style="margin-top:8px;">${reasons}</div>
                        <div style="margin-top:12px; display:flex; gap:10px; flex-wrap:wrap;">${actionsHtml}</div>
                    </div>
                </div>
            `;

            el("list").appendChild(card);
        }

        // wire request buttons
        document.querySelectorAll("button[data-request]").forEach(btn => {
            btn.addEventListener("click", async () => {
                const toId = btn.getAttribute("data-request");
                btn.disabled = true;
                try {
                    await apiFetch(`/api/requests/${toId}`, { method: "POST" });
                    setMsg("Match request sent.");
                    await load();
                } catch (e) {
                    setMsg(e?.message || "Failed to send request");
                } finally {
                    btn.disabled = false;
                }
            });
        });

    } catch (e) {
        setMsg(e?.message || "Failed to load matches");
    }
}

function escapeHtml(str) {
    return String(str || "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();

    el("searchBtn").addEventListener("click", () => {
        page = 0;
        load();
    });
    el("prevBtn").addEventListener("click", () => {
        if (page > 0) page--;
        load();
    });
    el("nextBtn").addEventListener("click", () => {
        page++;
        load();
    });

    load();
});
