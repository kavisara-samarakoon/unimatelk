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
    el("incoming").innerHTML = "";
    el("outgoing").innerHTML = "";

    try {
        const [incoming, outgoing] = await Promise.all([
            apiFetch("/api/requests/incoming"),
            apiFetch("/api/requests/outgoing")
        ]);

        if (incoming.length === 0) {
            el("incoming").innerHTML = `<div class="muted">No incoming requests.</div>`;
        } else {
            for (const r of incoming) {
                const div = document.createElement("div");
                div.className = "card";
                div.innerHTML = `
                    <strong>${escapeHtml(r.fromName)}</strong>
                    <div class="muted">Request ID: ${r.id}</div>
                    <div style="margin-top:10px; display:flex; gap:10px; flex-wrap:wrap;">
                        <a class="btn secondary" href="/user.html?id=${r.fromUserId}">View</a>
                        <button class="btn" data-accept="${r.id}">Accept</button>
                        <button class="btn secondary" data-reject="${r.id}">Reject</button>
                    </div>
                `;
                el("incoming").appendChild(div);
            }
        }

        if (outgoing.length === 0) {
            el("outgoing").innerHTML = `<div class="muted">No outgoing requests.</div>`;
        } else {
            for (const r of outgoing) {
                const div = document.createElement("div");
                div.className = "card";
                div.innerHTML = `
                    <strong>To: ${escapeHtml(r.toName)}</strong>
                    <div class="muted">Request ID: ${r.id}</div>
                    <div style="margin-top:10px; display:flex; gap:10px; flex-wrap:wrap;">
                        <a class="btn secondary" href="/user.html?id=${r.toUserId}">View</a>
                        <button class="btn secondary" data-cancel="${r.id}">Cancel</button>
                    </div>
                `;
                el("outgoing").appendChild(div);
            }
        }

        // wire actions
        document.querySelectorAll("button[data-accept]").forEach(btn => {
            btn.addEventListener("click", async () => {
                btn.disabled = true;
                try {
                    await apiFetch(`/api/requests/${btn.getAttribute("data-accept")}/accept`, { method: "POST" });
                    setMsg("Accepted! Chat unlocked.");
                    load();
                } catch (e) {
                    setMsg(e?.message || "Failed to accept");
                } finally {
                    btn.disabled = false;
                }
            });
        });
        document.querySelectorAll("button[data-reject]").forEach(btn => {
            btn.addEventListener("click", async () => {
                btn.disabled = true;
                try {
                    await apiFetch(`/api/requests/${btn.getAttribute("data-reject")}/reject`, { method: "POST" });
                    setMsg("Rejected");
                    load();
                } catch (e) {
                    setMsg(e?.message || "Failed to reject");
                } finally {
                    btn.disabled = false;
                }
            });
        });
        document.querySelectorAll("button[data-cancel]").forEach(btn => {
            btn.addEventListener("click", async () => {
                btn.disabled = true;
                try {
                    await apiFetch(`/api/requests/${btn.getAttribute("data-cancel")}`, { method: "DELETE" });
                    setMsg("Cancelled");
                    load();
                } catch (e) {
                    setMsg(e?.message || "Failed to cancel");
                } finally {
                    btn.disabled = false;
                }
            });
        });

        setMsg("");
    } catch (e) {
        setMsg(e?.message || "Failed to load");
    }
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();
    load();
});
