import { apiFetch, initCsrf } from "./api.js";

function el(id) { return document.getElementById(id); }
function setMsg(text) { el("msg").textContent = text || ""; }

function escapeHtml(str) {
    return String(str || "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

async function load() {
    setMsg("Loading...");
    el("cases").innerHTML = "";

    try {
        const res = await apiFetch("/api/admin/cases?status=OPEN&page=0&size=50");
        const items = res.items || [];

        if (items.length === 0) {
            el("cases").innerHTML = `<div class="muted">No open cases.</div>`;
            setMsg("");
            return;
        }

        for (const c of items) {
            const div = document.createElement("div");
            div.className = "card";
            div.innerHTML = `
                <div><strong>Case #${c.id}</strong></div>
                <div class="muted">Reported user: ${escapeHtml(c.reportedName)} (${escapeHtml(c.reportedEmail)})</div>
                <div class="muted">User status: ${escapeHtml(c.userStatus)} · Case status: ${escapeHtml(c.caseStatus)}</div>
                <label>Resolution note</label>
                <textarea rows="2" id="note-${c.id}" placeholder="What did you decide?"></textarea>
                <div style="margin-top:10px; display:flex; gap:10px; flex-wrap:wrap;">
                    <button class="btn" data-unblock="${c.id}">Unblock</button>
                    <button class="btn danger" data-ban="${c.id}">Ban</button>
                </div>
            `;
            el("cases").appendChild(div);
        }

        // wire actions
        document.querySelectorAll("button[data-unblock]").forEach(btn => {
            btn.addEventListener("click", async () => {
                const id = btn.getAttribute("data-unblock");
                const note = document.getElementById(`note-${id}`).value;
                btn.disabled = true;
                try {
                    await apiFetch(`/api/admin/cases/${id}/resolve`, {
                        method: "POST",
                        body: { action: "UNBLOCK", note }
                    });
                    setMsg("User unblocked");
                    load();
                } catch (e) {
                    setMsg(e?.message || "Failed");
                } finally {
                    btn.disabled = false;
                }
            });
        });
        document.querySelectorAll("button[data-ban]").forEach(btn => {
            btn.addEventListener("click", async () => {
                if (!confirm("Ban this user?")) return;
                const id = btn.getAttribute("data-ban");
                const note = document.getElementById(`note-${id}`).value;
                btn.disabled = true;
                try {
                    await apiFetch(`/api/admin/cases/${id}/resolve`, {
                        method: "POST",
                        body: { action: "BAN", note }
                    });
                    setMsg("User banned");
                    load();
                } catch (e) {
                    setMsg(e?.message || "Failed");
                } finally {
                    btn.disabled = false;
                }
            });
        });

        setMsg("");
    } catch (e) {
        setMsg(e?.message || "Failed to load admin panel");
    }
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();
    el("reloadBtn").addEventListener("click", load);
    load();
});
