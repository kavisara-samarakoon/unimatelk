import { apiFetch, initCsrf } from "./api.js";

function qp(name){
    const u = new URL(window.location.href);
    return u.searchParams.get(name);
}

document.addEventListener("DOMContentLoaded", async () => {
    await initCsrf();

    const reportedUserId = qp("id");
    const btn = document.getElementById("reportBtn");
    const msg = document.getElementById("reportMsg");

    if (!btn || !reportedUserId) return;

    btn.addEventListener("click", async () => {
        const reason = (document.getElementById("reportReason")?.value || "").trim();
        const details = (document.getElementById("reportDetails")?.value || "").trim();

        if (!reason) {
            msg.textContent = "Please select a reason.";
            return;
        }

        try {
            await apiFetch(`/api/reports/${reportedUserId}`, {
                method: "POST",
                body: JSON.stringify({ reason, details })
            });

            msg.textContent = "✅ Report submitted. Admin will review.";
            btn.disabled = true;
        } catch (e) {
            msg.textContent = "❌ Failed to submit report.";
            console.error(e);
        }
    });
});
