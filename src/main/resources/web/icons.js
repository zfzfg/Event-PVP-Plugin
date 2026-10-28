// Local, trusted SVG registry. Never interpret paths from configuration or user content.
const PANEL_ICON_PATHS = Object.freeze({
    "box": "M3 7l9-4 9 4v10l-9 4-9-4z M3 7l9 4 9-4 M12 11v10",
    "gear": "M9 3h6l1 4 4 2v6l-4 2-1 4H9l-1-4-4-2V9l4-2z M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
    "calendar": "M4 5h16v16H4z M4 10h16 M8 3v4 M16 3v4",
    "globe": "M3 12h18 M12 3c-6 5-6 13 0 18c6-5 6-13 0-18 M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0",
    "shield": "M12 3l8 3v6c0 5-8 9-8 9s-8-4-8-9V6z",
    "wrench": "M14 4a6 6 0 0 0-7 7l-4 7 3 3 7-7a6 6 0 0 0 7-7l-4 4-3-3z",
    "palette": "M12 3a9 9 0 1 0 0 18h2a2 2 0 0 0 0-4h-1a2 2 0 0 1 0-4h5c5 0 3-10-6-10 M7 8h.01 M12 6h.01 M17 8h.01 M6 13h.01",
    "save": "M4 3h13l4 4v14H3V3z M7 3v6h10V3 M7 21v-8h10v8",
    "plus": "M12 5v14 M5 12h14",
    "close": "M5 5l14 14 M19 5L5 19",
    "check": "M4 12l5 5L20 6",
    "warning": "M12 3L2 21h20z M12 9v5 M12 18h.01",
    "info": "M12 11v6 M12 7h.01 M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0",
    "person": "M8 7a4 4 0 1 0 8 0a4 4 0 1 0-8 0 M3 21v-2a9 7 0 0 1 18 0v2",
    "logout": "M10 3H4v18h6 M9 12h12 M17 8l4 4-4 4",
    "copy": "M8 8h13v13H8z M16 8V3H3v13h5",
    "edit": "M4 16L16 4l4 4L8 20H4z M13 7l4 4",
    "trash": "M3 6h18 M8 6V3h8v3 M5 6l1 15h12l1-15 M10 10v7 M14 10v7",
    "refresh": "M20 8a9 9 0 1 0 0 9 M20 3v6h-6",
    "chevron-down": "M5 9l7 7 7-7",
    "chevron-up": "M5 15l7-7 7 7",
    "arrow-up": "M12 21V3 M5 10l7-7 7 7",
    "arrow-down": "M12 3v18 M5 14l7 7 7-7",
    "sparkles": "M12 2l3 7 7 3-7 3-3 7-3-7-7-3 7-3z",
    "lock": "M5 10h14v11H5z M8 10V6a4 4 0 0 1 8 0v4",
    "search": "M3 10a7 7 0 1 0 14 0a7 7 0 1 0-14 0 M15 15l6 6",
    "eye": "M2 12s4-7 10-7 10 7 10 7-4 7-10 7-10-7-10-7 M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
    "clock": "M5 10h14v11H5z M8 10V6a4 4 0 0 1 8 0v4",
    "play": "M7 3l14 9-14 9z",
    "code": "M8 5l-6 7 6 7 M16 5l6 7-6 7 M14 3l-4 18",
    "flame": "M12 2c2 6 8 7 8 13a8 8 0 0 1-16 0c0-4 3-7 4-8l1 6z",
    "sword": "M19 3h-5L6 13l5 5L21 8V3z M3 12l9 9 M3 21l4-4",
    "bow": "M4 3c20 0 20 18 0 18l7-9z M3 12h18 M17 8l4 4-4 4",
    "helmet": "M4 14v-3a8 8 0 0 1 16 0v3 M2 14h20v5H2z M12 3v7",
    "vest": "M8 3l4 3 4-3 5 5-4 4v9H7v-9L3 8z",
    "leggings": "M6 3h12l2 18h-6l-2-12-2 12H4z",
    "boots": "M6 3h8v11l6 3v4H4v-7h2z",
    "hand": "M5 12V8a2 2 0 0 1 4 0v4-8a2 2 0 0 1 4 0v8-6a2 2 0 0 1 4 0v7-3a2 2 0 0 1 4 0v7c0 4-3 6-7 6h-3l-8-8a2 2 0 0 1 2-3l4 3",
    "filter": "M3 4h18l-7 8v8l-4-2v-6z",
    "link": "M10 14l4-4 M8 16l-2 2a4 4 0 0 1-6-6l5-5a4 4 0 0 1 6 0 M16 8l2-2a4 4 0 0 1 6 6l-5 5a4 4 0 0 1-6 0",
    "server": "M3 3h18v7H3z M3 14h18v7H3z M7 6h.01 M7 17h.01",
    "pause": "M7 4h3v16H7z M14 4h3v16h-3z",
    "upload": "M4 16v5h16v-5 M12 16V3 M7 8l5-5 5 5",
    "download": "M4 16v5h16v-5 M12 3v13 M7 11l5 5 5-5",
    "map": "M3 5l6-2 6 2 6-2v16l-6 2-6-2-6 2z M9 3v16 M15 5v16",
    "pin": "M12 22s8-8 8-13a8 8 0 0 0-16 0c0 5 8 13 8 13 M9 9a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
    "trophy": "M7 3h10v7a5 5 0 0 1-10 0z M7 5H3v3a5 5 0 0 0 5 5 M17 5h4v3a5 5 0 0 1-5 5 M12 15v6 M7 21h10",
    "undo": "M9 3L3 9l6 6 M3 9h12a6 6 0 0 1 0 12",
    "redo": "M15 3l6 6-6 6 M21 9H9a6 6 0 0 0 0 12",
    "coins": "M3 7a9 4 0 1 0 18 0a9 4 0 1 0-18 0 M3 7v5a9 4 0 0 0 18 0V7 M3 12v5a9 4 0 0 0 18 0v-5"
});
const PANEL_ICON_ALIASES = Object.freeze({
    "align-left": "code",
    "arrows-up-down": "info",
    "box-archive": "box",
    "box-open": "box",
    "boxes-packing": "box",
    "boxes-stacked": "box",
    "calendar-alt": "calendar",
    "check-circle": "check",
    "circle": "info",
    "circle-check": "check",
    "circle-exclamation": "warning",
    "circle-info": "info",
    "circle-notch": "refresh",
    "circle-pause": "pause",
    "circle-question": "info",
    "clock-rotate-left": "refresh",
    "cloud-download-alt": "download",
    "cog": "gear",
    "cube": "box",
    "door-open": "logout",
    "dot-circle": "info",
    "earth-europe": "globe",
    "eject": "logout",
    "exclamation-triangle": "warning",
    "file-export": "download",
    "file-import": "upload",
    "flask": "info",
    "globe-americas": "globe",
    "hammer": "wrench",
    "hat-wizard": "helmet",
    "info-circle": "info",
    "lightbulb": "info",
    "magnifying-glass": "search",
    "map-marker-alt": "pin",
    "map-signs": "map",
    "medal": "trophy",
    "paint-brush": "palette",
    "plug": "info",
    "plug-circle-xmark": "info",
    "random": "refresh",
    "rotate": "refresh",
    "rotate-left": "refresh",
    "shield-alt": "shield",
    "shield-check": "shield",
    "shield-halved": "shield",
    "shield-virus": "shield",
    "shoe-prints": "boots",
    "signal": "server",
    "skull-crossbones": "info",
    "sliders": "gear",
    "sliders-h": "gear",
    "socks": "leggings",
    "spinner": "refresh",
    "swords": "sword",
    "sync": "refresh",
    "sync-alt": "refresh",
    "tachometer-alt": "server",
    "terminal": "code",
    "times": "close",
    "tools": "wrench",
    "triangle-exclamation": "warning",
    "user": "person",
    "user-clock": "person",
    "user-shield": "shield",
    "user-tag": "person",
    "users": "person",
    "vector-square": "map",
    "vest": "info",
    "wand-magic-sparkles": "sparkles",
    "hard-hat": "helmet",
    "tshirt": "vest",
    "hand-paper": "hand",
    "exclamation-circle": "warning"
});
function panelIcon(name) {
    const path = PANEL_ICON_PATHS[PANEL_ICON_ALIASES[name] || name] || PANEL_ICON_PATHS.sparkles;
    return `<svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="${path}"/></svg>`;
}
function renderPanelIcons(root = document) {
    const icons = [...root.querySelectorAll('i[class*="ui-"]')];
    if (root.matches?.('i[class*="ui-"]')) icons.push(root);
    for (const icon of icons) {
        const name = [...icon.classList].find(c => c.startsWith('ui-') && c !== 'ui-spin')?.slice(3);
        if (!name || icon.dataset.renderedIcon === name) continue;
        icon.innerHTML = panelIcon(name);
        icon.dataset.renderedIcon = name;
        icon.setAttribute('aria-hidden', 'true');
    }
}
document.addEventListener('DOMContentLoaded', () => {
    renderPanelIcons();
    new MutationObserver(records => {
        for (const record of records) {
            if (record.type === 'attributes') renderPanelIcons(record.target);
            else for (const node of record.addedNodes) if (node.nodeType === 1) renderPanelIcons(node);
        }
    }).observe(document.body, {subtree:true, childList:true, attributes:true, attributeFilter:['class']});
});
