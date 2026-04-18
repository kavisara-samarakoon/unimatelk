import { apiFetch, initCsrf } from "./api.js";

let page = 0;
const size = 10;

function el(id) {
    return document.getElementById(id);
}

function setMsg(text) {
    el("msg").textContent = text || "";
}

function buildParams(options = {}) {
    const params = new URLSearchParams();

    const campus = el("campus").value.trim();
    const degree = el("degree").value.trim();
    const year = el("year").value.trim();
    const genderPref = el("genderPref").value.trim();
    const keyword = el("keyword").value.trim();

    const relaxedCampus = options.relaxedCampus === true;

    // keep these exactly as before
    if (degree) params.set("degree", degree);
    if (year) params.set("year", year);
    if (genderPref) params.set("genderPref", genderPref);

    // normal mode = use campus as backend campus filter
    // relaxed mode = do not force exact campus matching
    if (!relaxedCampus) {
        if (campus) params.set("campus", campus);
        if (keyword) params.set("keyword", keyword);
    } else {
        // relaxed retry:
        // if user typed campus, use it as a broader keyword search
        if (keyword) {
            params.set("keyword", keyword);
        } else if (campus) {
            params.set("keyword", campus);
        }
    }

    params.set("page", String(page));
    params.set("size", String(size));
    return params.toString();
}

function escapeHtml(str) {
    return String(str || "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

async function fetchMatchBundle(relaxedCampus = false) {
    const query = buildParams({ relaxedCampus });

    const [feed, outgoing, rooms] = await Promise.all([
        apiFetch(`/api/matches/feed?${query}`),
        apiFetch(`/api/requests/outgoing`).catch(() => []),
        apiFetch(`/api/chat/rooms`).catch(() => [])
    ]);

    return { feed, outgoing, rooms };
}

async function load() {
    setMsg("Loading...");
    el("list").innerHTML = "";

    try {
        const campusValue = el("campus").value.trim();

        // 1) first try normal filter logic
        let { feed, outgoing, rooms } = await fetchMatchBundle(false);

        // 2) if no results and campus was entered, retry with relaxed campus search
        let usedRelaxedCampus = false;
        if (feed.items.length === 0 && campusValue) {
            const relaxedResult = await fetchMatchBundle(true);

            if (relaxedResult.feed.items.length > 0) {
                feed = relaxedResult.feed;
                outgoing = relaxedResult.outgoing;
                rooms = relaxedResult.rooms;
                usedRelaxedCampus = true;
            }
        }

        const outgoingByUser = new Map(outgoing.map(r => [r.toUserId, r]));
        const roomByUser = new Map(
            rooms
                .filter(r => r.otherUserId != null)
                .map(r => [r.otherUserId, r.roomId])
        );

        el("pageText").textContent = `Page ${feed.page + 1} · Showing ${feed.items.length} of ${feed.total}`;

        if (feed.items.length === 0) {
            setMsg("No matches found. Try broader filters or complete more profile/preference info.");
            el("list").innerHTML = "";
            return;
        }

        if (usedRelaxedCampus) {
            setMsg("Showing broader campus results for your search.");
        } else {
            setMsg("");
        }

        for (const m of feed.items) {
            const card = document.createElement("article");
            card.className = "match-card";

            const img = m.profilePhotoPath ? m.profilePhotoPath : "/images/default-avatar.svg";
            const reasons = (m.whyMatched || [])
                .map(x => `<span class="reason-chip">${escapeHtml(x)}</span>`)
                .join("");

            const outgoingReq = outgoingByUser.get(m.userId);
            const roomId = roomByUser.get(m.userId);

            let actionsHtml = `<a class="btn secondary" href="/user.html?id=${m.userId}">View Profile</a>`;

            if (roomId) {
                actionsHtml += ` <a class="btn ghost" href="/chat.html?room=${roomId}">Open Chat</a>`;
            } else if (outgoingReq && outgoingReq.status === "PENDING") {
                actionsHtml += ` <span class="badge">Request Pending</span>`;
            } else {
                actionsHtml += ` <button class="btn" data-request="${m.userId}">Request Match</button>`;
            }

            card.innerHTML = `
                <div class="match-media">
                    <img class="match-avatar" src="${img}" alt="avatar" onerror="this.src='/images/default-avatar.svg'"/>
                </div>

                <div class="match-body">
                    <div class="match-top">
                        <div>
                            <div class="match-name">${escapeHtml(m.name || "User")}</div>
                            <div class="match-meta">
                                ${escapeHtml(m.campus || "")} · ${escapeHtml(m.degree || "")} · Year ${m.yearOfStudy ?? ""}
                            </div>
                        </div>

                        <span class="badge match-badge">${m.matchPercent}% match</span>
                    </div>

                    ${reasons ? `<div class="reason-list">${reasons}</div>` : ``}

                    <div class="match-actions">
                        ${actionsHtml}
                    </div>
                </div>
            `;

            el("list").appendChild(card);
        }

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