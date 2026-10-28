// Shared transport and view lifecycle. No automatic retries for mutations.
const PANEL_READS = new Set();
let panelSessionGeneration = 0;
let panelStatusTimer = null;
let panelStatusPending = false;
function stopPanelSession() {
    panelSessionGeneration++;
    CONFIG_STATE.isSaving = false;
    clearInterval(panelStatusTimer);
    panelStatusTimer = null;
    for (const controller of PANEL_READS) controller.abort();
    PANEL_READS.clear();
    hideMinecraftTooltip();
    if (typeof invalidateInventoryPreview === 'function') invalidateInventoryPreview();
    for (const overlay of document.querySelectorAll('.modal-overlay.active')) overlay.querySelector('.modal-close')?.click();
}
async function panelFetch(url, options = {}) {
    const session = panelSessionGeneration;
    const read = !options.method || options.method.toUpperCase() === 'GET';
    const controller = new AbortController();
    const abort = () => controller.abort();
    if (options.signal?.aborted) controller.abort();
    options.signal?.addEventListener('abort', abort, { once: true });
    if (read) PANEL_READS.add(controller);
    const timeout = read ? setTimeout(abort, 20000) : null;
    try {
        const response = await fetch(url, { credentials: 'include', ...options, signal: controller.signal });
        if (response.status === 401 && session === panelSessionGeneration && !url.includes('/auth/')) {
            stopPanelSession();
            isAuthenticated = false;
            currentPlayer = null;
            showLoginScreen();
        }
        // Keep timeout and cancellation active until the entire API body arrives.
        const body = await response.arrayBuffer();
        return new Response([204, 205, 304].includes(response.status) ? null : body,
            { status: response.status, statusText: response.statusText, headers: response.headers });
    } finally {
        clearTimeout(timeout);
        PANEL_READS.delete(controller);
        options.signal?.removeEventListener('abort', abort);
    }
}
async function panelRequest(url, options = {}) {
    const response = await panelFetch(url, options);
    let body;
    try { body = await response.json(); } catch (_) {
        const error = new Error(i18n.t('panel.invalidResponse'));
        error.status = response.status;
        throw error;
    }
    if (!response.ok || !body || typeof body !== 'object' || body.success === false) {
        const error = new Error(response.status === 401 ? i18n.t('auth.invalidToken') : i18n.t('panel.requestFailed', { status: String(response.status) }));
        error.status = response.status;
        error.body = body;
        throw error;
    }
    return body;
}
function startPanelStatus() {
    clearInterval(panelStatusTimer);
    panelStatusTimer = setInterval(() => { if (isAuthenticated) checkServerStatus(); }, 60000);
}
function normalizedBackup(data) {
    const items = (source, max) => {
        const slots = new Map();
        for (const item of Array.isArray(source) ? source : []) {
            if (!item || !Number.isInteger(item.slot) || item.slot < 0 || item.slot > max ||
                typeof item.material !== 'string' || !item.material || /^(AIR|CAVE_AIR|VOID_AIR)$/.test(item.material) ||
                !Number.isInteger(item.amount) || item.amount <= 0) continue;
            slots.set(item.slot, { ...item, amount: Math.floor(item.amount) });
        }
        return [...slots.values()];
    };
    const offhand = items(data.offhand ? [{ ...data.offhand, slot: 40 }] : [], 40)[0] || null;
    return { ...data, contents: items(data.contents, 35), armor: items(data.armor, 3), offhand,
        level: Number.isFinite(data.level) ? Math.max(0, Math.floor(data.level)) : 0,
        exp: Number.isFinite(data.exp) ? Math.min(1, Math.max(0, data.exp)) : 0 };
}

// The existing dialogs keep their own close handlers and validation.
const panelDialogFocus = new Map();
function syncPanelDialogs() {
    for (const [overlay, previous] of panelDialogFocus) {
        if (!overlay.isConnected) {
            panelDialogFocus.delete(overlay);
            if (previous?.isConnected) previous.focus();
        }
    }
    for (const overlay of document.querySelectorAll('.modal-overlay')) {
        const opened = overlay.classList.contains('active');
        if (opened && !panelDialogFocus.has(overlay)) {
            panelDialogFocus.set(overlay, document.activeElement);
            const dialog = overlay.querySelector('.modal') || overlay;
            dialog.setAttribute('role', 'dialog');
            dialog.setAttribute('aria-modal', 'true');
            dialog.tabIndex = -1;
            const title = dialog.querySelector('.modal-title');
            if (title) {
                if (!title.id) title.id = overlay.id + '-title';
                dialog.setAttribute('aria-labelledby', title.id);
            }
            const close = dialog.querySelector('.modal-close');
            if (close) close.setAttribute('aria-label', i18n.t('panel.close'));
            (dialog.querySelector('input:not([type="hidden"]):not([disabled]), select:not([disabled]), textarea:not([disabled])') || close || dialog).focus();
        } else if (!opened && panelDialogFocus.has(overlay)) {
            const previous = panelDialogFocus.get(overlay);
            panelDialogFocus.delete(overlay);
            if (previous?.isConnected) previous.focus();
        }
    }
}
document.addEventListener('DOMContentLoaded', () => {
    new MutationObserver(syncPanelDialogs).observe(document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['class'] });
});
document.addEventListener('keydown', event => {
    const overlay = [...document.querySelectorAll('.modal-overlay.active')].pop();
    if (event.key === 'Escape') hideMinecraftTooltip();
    if (!overlay) return;
    if (event.key === 'Escape') {
        event.preventDefault();
        overlay.querySelector('.modal-close')?.click();
    }
    if (event.key === 'Tab') {
        const controls = [...overlay.querySelectorAll('button, input, select, textarea, a[href], [tabindex="0"]')]
            .filter(element => !element.disabled && element.getClientRects().length);
        const first = controls[0], last = controls[controls.length - 1];
        if (!first) { event.preventDefault(); return; }
        if (event.shiftKey && (document.activeElement === first || !controls.includes(document.activeElement))) {
            event.preventDefault(); last.focus();
        } else if (!event.shiftKey && (document.activeElement === last || !controls.includes(document.activeElement))) {
            event.preventDefault(); first.focus();
        }
    }
});

document.addEventListener('click', event => {
    if (!event.target.closest('.inventory-slot, #minecraft-tooltip')) hideMinecraftTooltip();
});
window.addEventListener('resize', () => hideMinecraftTooltip());
