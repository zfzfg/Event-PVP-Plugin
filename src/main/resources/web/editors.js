// ============================================
// Event-PVP Web Konfigurator - Erweiterte Editoren
// ============================================

console.log('editors.js loading...');

/**
 * Event Editor Modal
 * 
 * Verwendet window-Objekte für den Datenaustausch mit app.js
 */

// ============================================
// Item-Eigenschaften (aus dem Server-Katalog)
// ============================================
//
// Hier standen frueher drei gepflegte Tabellen: MINECRAFT_ENCHANTMENTS (rund 90 Zeilen),
// UNSTACKABLE_ITEMS und eine Kategorie-Heuristik aus Namensvergleichen. Alle drei waren
// Schaetzungen ueber den Server und veralteten mit jeder Minecraft-Version. Die Antworten
// kommen jetzt aus ITEM_CATALOG (items.js), gespeist von /api/materials - also vom Server
// selbst. Die Funktionsnamen bleiben, damit der uebrige Editor unveraendert weiterlaeuft.

/** Symbole fuer die gaengigen Verzauberungen; alles andere bekommt ein neutrales Zeichen. */
const ENCHANTMENT_ICONS = {
    PROTECTION: '🛡️', FIRE_PROTECTION: '🔥', BLAST_PROTECTION: '💥', PROJECTILE_PROTECTION: '🏹',
    FEATHER_FALLING: '🪶', RESPIRATION: '💨', AQUA_AFFINITY: '💧', THORNS: '🌵',
    DEPTH_STRIDER: '🏊', FROST_WALKER: '❄️', SOUL_SPEED: '👻', SWIFT_SNEAK: '🐈',
    SHARPNESS: '⚔️', SMITE: '☠️', BANE_OF_ARTHROPODS: '🕷️', KNOCKBACK: '👊',
    FIRE_ASPECT: '🔥', LOOTING: '💰', SWEEPING: '🌪️', SWEEPING_EDGE: '🌪️',
    EFFICIENCY: '⚡', SILK_TOUCH: '🪶', FORTUNE: '🍀',
    POWER: '🏹', PUNCH: '👊', FLAME: '🔥', INFINITY: '♾️',
    MULTISHOT: '🎯', QUICK_CHARGE: '⏩', PIERCING: '🗡️',
    LOYALTY: '🔁', IMPALING: '🔱', RIPTIDE: '🌊', CHANNELING: '⚡',
    LUCK_OF_THE_SEA: '🍀', LURE: '🎣', MENDING: '🔧', UNBREAKING: '🔨',
    VANISHING_CURSE: '💀', BINDING_CURSE: '⛓️', DENSITY: '🪨', BREACH: '🛠️', WIND_BURST: '🌬️'
};

/**
 * Verzauberungen, die auf dieses Item passen.
 *
 * Rueckgabeform bleibt {id, name, maxLevel, icon}, damit renderEnchantmentsList()
 * unveraendert bleibt. Ohne Server-Katalog eine leere Liste - das Panel zeigt dann
 * "keine Verzauberungen" statt geratener Vorschlaege.
 */
function getAvailableEnchantments(itemName) {
    return itemEnchantments(itemName).map(enchant => ({
        id: enchant.key,
        name: enchant.label,
        maxLevel: enchant.maxLevel || 1,
        icon: ENCHANTMENT_ICONS[enchant.key] || '✨'
    }));
}

/** Maximale Stapelgroesse laut Server. */
function getMaxStackSize(itemName) {
    return itemMaxStack(itemName);
}

// isItemStackable() stand hier und hatte keinen Aufrufer - die eine Stelle, die es wissen
// muss (das Item-Modal), vergleicht getMaxStackSize() direkt mit 1.

// Globale Editing-Variablen
var currentEditingEvent = null;
var currentEditingEventOriginal = null;
var currentEditingWorld = null;
var currentEditingWorldOriginal = null;
var currentEditingEquipment = null;
var currentEditingEquipmentOriginal = null;
var currentEditingSlot = null;
var selectedArmorSlot = null;

// Drag & Drop Variablen
var draggedItem = null;
var dragSourceSlot = null;

function createNewEvent() {
    currentEditingEventOriginal = null;
    currentEditingEvent = {
        id: `event_${Date.now()}`,
        enabled: true,
        command: '',
        'display-name': 'Neues Event',
        description: '',
        'min-players': 2,
        'max-players': 20,
        'countdown-time': 30,
        worlds: {
            'lobby-world': '',
            'event-world': '',
            'use-lobby': true,
            'build-allowed': false,
            'regenerate-event-world': false,
            'clone-source-event-world': ''
        },
        'spawn-settings': {
            'spawn-type': 'SINGLE_POINT',
            'single-spawn': { x: 0, y: 64, z: 0, yaw: 0, pitch: 0 }
        },
        'equipment-group': 'default',
        'give-equipment-in-lobby': false,
        'lobby-team-colored-armor': false,
        mechanics: {
            'game-mode': 'SOLO',
            'pvp-enabled': true,
            'hunger-enabled': true,
            'friendly-fire': false
        },
        rewards: {
            winner: { items: { enabled: false, items: [] }, commands: { enabled: false, commands: [] } },
            participation: { items: { enabled: false, items: [] }, commands: { enabled: false, commands: [] } }
        },
        messages: {
            start: '&e&lEvent starting!',
            winner: '&6&l{player} wins!',
            eliminated: '&7{player} was eliminated!',
            objective: '&7Goal: win the event'
        }
    };
    openEventEditor(currentEditingEvent);
}

function editEvent(eventId) {
    const event = CONFIG_STATE.config?.events?.[eventId];
    if (!event) {
        showToast(i18n.t('toast.eventNotFound'), 'error');
        return;
    }
    
    currentEditingEvent = JSON.parse(JSON.stringify(event));
    currentEditingEvent.id = eventId;
    currentEditingEventOriginal = JSON.parse(JSON.stringify(event));
    currentEditingEventOriginal.id = eventId;
    openEventEditor(currentEditingEvent);
}

function getConfiguredWorlds() {
    const worldsMap = CONFIG_STATE.worlds?.worlds || CONFIG_STATE.worlds || {};
    const result = [];
    const seen = new Set();

    for (const [id, cfg] of Object.entries(worldsMap)) {
        if (!id || typeof id !== 'string' || id === 'worlds' || seen.has(id.toLowerCase())) continue;
        seen.add(id.toLowerCase());
        const rawName = (cfg && typeof cfg === 'object' && cfg['display-name']) ? cfg['display-name'] : id;
        const cleanName = String(rawName).replace(/&[0-9a-fk-or]/gi, '').trim();
        const isRegen = Boolean(cfg && typeof cfg === 'object' && cfg['regenerate-world'] === true);
        result.push({
            id: id.trim(),
            displayName: cleanName,
            isRegen: isRegen
        });
    }

    return result;
}

function renderConfiguredWorldOptions(currentValue, excludeWorldId = null) {
    const worlds = getConfiguredWorlds();
    let hasCurrent = false;
    let html = '';

    for (const w of worlds) {
        if (excludeWorldId && w.id.toLowerCase() === excludeWorldId.toLowerCase()) continue;
        const selected = Boolean(currentValue && w.id.toLowerCase() === currentValue.toLowerCase());
        if (selected) hasCurrent = true;
        const regenIcon = w.isRegen ? ' 🔄' : '';
        const label = (w.displayName && w.displayName !== w.id) ? `${w.id} (${w.displayName})${regenIcon}` : `${w.id}${regenIcon}`;
        html += `<option value="${w.id}" ${selected ? 'selected' : ''}>${label}</option>`;
    }

    if (currentValue && !hasCurrent) {
        html += `<option value="${currentValue}" selected>${currentValue} (${i18n.t('card.custom') || 'Benutzerdefiniert'})</option>`;
    }

    return html;
}

function isWorldGlobalRegenEnabled(worldName) {
    if (!worldName) return false;
    const worldsMap = CONFIG_STATE.worlds?.worlds || CONFIG_STATE.worlds || {};
    const worldConfig = worldsMap[worldName];
    return Boolean(worldConfig && worldConfig['regenerate-world'] === true);
}

function updateEventWorldValidationUI() {
    const eventWorld = (currentEditingEvent?.worlds?.['event-world'] || '').trim();
    const lobbyWorld = (currentEditingEvent?.worlds?.['lobby-world'] || '').trim();
    const useLobby = currentEditingEvent?.worlds?.['use-lobby'] !== false;

    // Event World validation warning
    const eventWorldWarning = document.getElementById('event-world-required-warning');
    if (eventWorldWarning) {
        eventWorldWarning.style.display = !eventWorld ? 'flex' : 'none';
    }

    // Lobby World validation warning and disabled info
    const lobbyWarning = document.getElementById('lobby-world-required-warning');
    const lobbyDisabledInfo = document.getElementById('lobby-world-disabled-info');
    const lobbySelect = document.getElementById('event-lobby-select');
    
    if (lobbySelect) {
        lobbySelect.disabled = !useLobby;
        lobbySelect.style.opacity = useLobby ? '1' : '0.6';
    }

    if (lobbyWarning) {
        lobbyWarning.style.display = (useLobby && !lobbyWorld) ? 'flex' : 'none';
    }
    if (lobbyDisabledInfo) {
        lobbyDisabledInfo.style.display = !useLobby ? 'flex' : 'none';
    }

    updateEventWorldRegenUI();
}

function updateEventWorldRegenUI() {
    const worldName = (currentEditingEvent?.worlds?.['event-world'] || '').trim();
    const globalRegen = isWorldGlobalRegenEnabled(worldName);
    const checkbox = document.getElementById('event-regen-checkbox');
    const lockNotice = document.getElementById('event-world-regen-lock-notice');
    const lockNoticeText = document.getElementById('event-world-regen-lock-text');
    
    if (checkbox) {
        if (globalRegen) {
            checkbox.checked = true;
            checkbox.disabled = true;
            if (currentEditingEvent && currentEditingEvent.worlds) {
                currentEditingEvent.worlds['regenerate-event-world'] = true;
            }
        } else {
            checkbox.disabled = false;
            checkbox.checked = Boolean(currentEditingEvent?.worlds?.['regenerate-event-world']);
        }
    }
    if (lockNotice) {
        if (globalRegen) {
            lockNotice.style.display = 'flex';
            if (lockNoticeText) {
                lockNoticeText.textContent = i18n.t('editor.regenLockedByWorldHint', { world: worldName });
            }
        } else {
            lockNotice.style.display = 'none';
        }
    }
    updateEventCloneSourceUI();
}

function updateEventCloneSourceUI() {
    const cloneSource = (currentEditingEvent?.worlds?.['clone-source-event-world'] || '').trim();
    const warningBox = document.getElementById('event-no-clone-warning');
    const activeBox = document.getElementById('event-clone-active-info');
    if (warningBox && activeBox) {
        if (!cloneSource) {
            warningBox.style.display = 'flex';
            activeBox.style.display = 'none';
        } else {
            warningBox.style.display = 'none';
            activeBox.style.display = 'flex';
        }
    }
}

function updateWorldCloneSourceUI() {
    const cloneSource = (currentEditingWorld?.['clone-source-world'] || '').trim();
    const warningBox = document.getElementById('world-no-clone-warning');
    const activeBox = document.getElementById('world-clone-active-info');
    if (warningBox && activeBox) {
        if (!cloneSource) {
            warningBox.style.display = 'flex';
            activeBox.style.display = 'none';
        } else {
            warningBox.style.display = 'none';
            activeBox.style.display = 'flex';
        }
    }
}

function openEventEditor(eventConfig) {
    console.log('openEventEditor called with:', eventConfig);
    
    // Ensure all required properties exist with defaults
    eventConfig = eventConfig || {};
    eventConfig.id = eventConfig.id || `event_${Date.now()}`;
    eventConfig['display-name'] = eventConfig['display-name'] || 'Neues Event';
    eventConfig.description = eventConfig.description || '';
    eventConfig.command = eventConfig.command || '';
    eventConfig['min-players'] = eventConfig['min-players'] || 2;
    eventConfig['max-players'] = eventConfig['max-players'] || 20;
    eventConfig.enabled = eventConfig.enabled !== false;
    eventConfig.worlds = eventConfig.worlds || {};
    eventConfig.worlds['lobby-world'] = eventConfig.worlds['lobby-world'] || '';
    eventConfig.worlds['event-world'] = eventConfig.worlds['event-world'] || '';
    if (eventConfig.worlds['use-lobby'] === undefined) {
        eventConfig.worlds['use-lobby'] = !Boolean(eventConfig.worlds['skip-lobby']);
    }
    eventConfig.worlds['build-allowed'] = eventConfig.worlds['build-allowed'] || false;
    eventConfig.worlds['regenerate-event-world'] = eventConfig.worlds['regenerate-event-world'] || false;
    eventConfig.worlds['clone-source-event-world'] = eventConfig.worlds['clone-source-event-world'] || '';
    eventConfig['spawn-settings'] = eventConfig['spawn-settings'] || {};
    eventConfig['spawn-settings']['spawn-type'] = eventConfig['spawn-settings']['spawn-type'] || 'SINGLE_POINT';
    eventConfig.mechanics = eventConfig.mechanics || {};
    eventConfig.mechanics['game-mode'] = eventConfig.mechanics['game-mode'] || 'SOLO';
    eventConfig.messages = eventConfig.messages || {
        start: '&e&lEvent started!',
        winner: '&6&l{player} wins!',
        eliminated: '&7{player} was eliminated!',
        objective: '&7Objective: Win the event'
    };

    const globalRegen = isWorldGlobalRegenEnabled(eventConfig.worlds['event-world']);
    if (globalRegen) {
        eventConfig.worlds['regenerate-event-world'] = true;
    }
    
    try {
        const modal = document.createElement('div');
        modal.className = 'modal-overlay active';
        modal.id = 'event-editor-modal';
    
        modal.innerHTML = `
        <div class="modal" style="max-width: 900px; max-height: 90vh;">
            <div class="modal-header">
                <h3 class="modal-title">
                    <i class="fas fa-calendar-alt"></i>
                    Event Editor: ${eventConfig['display-name']}
                </h3>
                <button class="modal-close" onclick="closeEventEditor()">
                    <i class="fas fa-times"></i>
                </button>
            </div>
            <div class="modal-body" style="max-height: calc(90vh - 140px); overflow-y: auto;">
                <div class="tabs">
                    <div class="tab active" onclick="switchEventTab('basic')">${i18n.t('editor.tabBase')}</div>
                    <div class="tab" onclick="switchEventTab('worlds')">${i18n.t('editor.tabWorlds')}</div>
                    <div class="tab" onclick="switchEventTab('spawns')">${i18n.t('editor.tabSpawns')}</div>
                    <div class="tab" onclick="switchEventTab('equipment')">${i18n.t('nav.equipment')}</div>
                    <div class="tab" onclick="switchEventTab('mechanics')">${i18n.t('editor.tabMechanics')}</div>
                    <div class="tab" onclick="switchEventTab('messages')">${i18n.t('editor.tabMessages')}</div>
                    <div class="tab" onclick="switchEventTab('rewards')">${i18n.t('editor.tabRewards')}</div>
                    <div class="tab" onclick="switchEventTab('expert')"><i class="fas fa-wrench"></i> ${i18n.t('editor.tabExpert')}</div>
                </div>

                <div id="event-tab-basic" class="tab-content active">
                    <div class="form-group">
                        <label class="form-label">Event ID</label>
                        <input type="text" class="form-control" id="event-id" value="${eventConfig.id}" disabled>
                    </div>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('events.displayName')}</label>
                        <input type="text" class="form-control" id="event-name" value="${eventConfig['display-name']}"
                               onchange="currentEditingEvent['display-name'] = this.value">
                    </div>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('events.description')}</label>
                        <textarea class="form-control" id="event-desc" onchange="currentEditingEvent.description = this.value">${eventConfig.description}</textarea>
                    </div>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('events.command')}</label>
                        <div style="display: flex; gap: 0.5rem; align-items: center;">
                            <span style="color: var(--text-muted);">/event</span>
                            <input type="text" class="form-control" id="event-cmd" value="${eventConfig.command}"
                                   onchange="currentEditingEvent.command = this.value">
                        </div>
                    </div>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('events.minPlayers')}</label>
                        <input type="number" class="form-control" id="event-min" value="${eventConfig['min-players']}" min="1" max="200"
                               onchange="currentEditingEvent['min-players'] = parseInt(this.value)">
                    </div>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('events.maxPlayers')}</label>
                        <input type="number" class="form-control" id="event-max" value="${eventConfig['max-players']}" min="1" max="200"
                               onchange="currentEditingEvent['max-players'] = parseInt(this.value)">
                    </div>
                    <div class="toggle-wrapper">
                        <div class="toggle-label">
                            <span>${i18n.t('events.enabled')}</span>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" ${eventConfig.enabled !== false ? 'checked' : ''}
                                   onchange="currentEditingEvent.enabled = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>
                </div>

                <div id="event-tab-worlds" class="tab-content">
                    <div class="toggle-wrapper" style="margin-bottom: 1.25rem;">
                        <div class="toggle-label">
                            <span>${i18n.t('editor.useLobbyPhase')}</span>
                            <small class="form-help" style="margin-top: 0.2rem;">${i18n.t('editor.useLobbyPhaseHelp')}</small>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" id="event-use-lobby" ${eventConfig.worlds['use-lobby'] !== false ? 'checked' : ''}
                                   onchange="currentEditingEvent.worlds['use-lobby'] = this.checked; updateEventWorldValidationUI();">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>

                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.lobbyWorld')}</label>
                        <select class="form-control" id="event-lobby-select"
                                ${eventConfig.worlds['use-lobby'] === false ? 'disabled style="opacity: 0.6;"' : ''}
                                onchange="currentEditingEvent.worlds['lobby-world'] = this.value; updateEventWorldValidationUI();">
                            <option value="">-- ${i18n.t('editor.selectWorld')} --</option>
                            ${renderConfiguredWorldOptions(eventConfig.worlds['lobby-world'])}
                        </select>
                        <small class="form-help">${i18n.t('editor.worldMissingHint')}</small>

                        <!-- Validation warning if lobby enabled but no lobby world selected -->
                        <div id="lobby-world-required-warning" class="alert alert-warning" style="margin-top: 0.5rem; ${eventConfig.worlds['use-lobby'] !== false && !eventConfig.worlds['lobby-world'] ? 'display: flex;' : 'display: none;'}">
                            <i class="fas fa-exclamation-triangle" style="margin-right: 0.5rem; margin-top: 0.2rem; color: var(--warning, #f59e0b);"></i>
                            <span>${i18n.t('editor.lobbyWorldRequiredWarning')}</span>
                        </div>

                        <!-- Info if lobby phase disabled -->
                        <div id="lobby-world-disabled-info" class="alert alert-info" style="margin-top: 0.5rem; ${eventConfig.worlds['use-lobby'] === false ? 'display: flex;' : 'display: none;'}">
                            <i class="fas fa-info-circle" style="margin-right: 0.5rem; margin-top: 0.2rem;"></i>
                            <span>${i18n.t('editor.lobbyWorldDisabledInfo')}</span>
                        </div>
                    </div>

                    <div class="form-group" style="margin-top: 1rem;">
                        <label class="form-label">${i18n.t('editor.eventWorld')}</label>
                        <select class="form-control" id="event-world-select"
                                onchange="currentEditingEvent.worlds['event-world'] = this.value; updateEventWorldValidationUI();">
                            <option value="">-- ${i18n.t('editor.selectWorld')} --</option>
                            ${renderConfiguredWorldOptions(eventConfig.worlds['event-world'])}
                        </select>
                        <small class="form-help">${i18n.t('editor.worldMissingHint')}</small>

                        <!-- Validation warning if no event world selected -->
                        <div id="event-world-required-warning" class="alert alert-warning" style="margin-top: 0.5rem; ${!eventConfig.worlds['event-world'] ? 'display: flex;' : 'display: none;'}">
                            <i class="fas fa-exclamation-triangle" style="margin-right: 0.5rem; margin-top: 0.2rem; color: var(--warning, #f59e0b);"></i>
                            <span>${i18n.t('editor.eventWorldRequiredWarning')}</span>
                        </div>
                    </div>

                    <div class="toggle-wrapper" style="margin-top: 1rem;">
                        <div class="toggle-label">
                            <span>${i18n.t('editor.buildAllowed')}</span>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" ${eventConfig.worlds['build-allowed'] !== false ? 'checked' : ''}
                                   onchange="currentEditingEvent.worlds['build-allowed'] = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>
                </div>

                <div id="event-tab-spawns" class="tab-content">
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.spawnType')}</label>
                        <select class="form-control" id="event-spawn-type"
                                onchange="updateEventSpawnType(this.value)">
                            <option value="SINGLE_POINT" ${eventConfig['spawn-settings']?.['spawn-type'] === 'SINGLE_POINT' ? 'selected' : ''}>${i18n.t('editor.spawnTypeSinglePoint')}</option>
                            <option value="RANDOM_RADIUS" ${eventConfig['spawn-settings']?.['spawn-type'] === 'RANDOM_RADIUS' ? 'selected' : ''}>${i18n.t('editor.spawnTypeRandomRadius')}</option>
                            <option value="RANDOM_AREA" ${eventConfig['spawn-settings']?.['spawn-type'] === 'RANDOM_AREA' ? 'selected' : ''}>${i18n.t('editor.spawnTypeRandomArea')}</option>
                            <option value="RANDOM_CUBE" ${eventConfig['spawn-settings']?.['spawn-type'] === 'RANDOM_CUBE' ? 'selected' : ''}>${i18n.t('editor.spawnTypeRandomCube')}</option>
                            <option value="MULTIPLE_SPAWNS" ${eventConfig['spawn-settings']?.['spawn-type'] === 'MULTIPLE_SPAWNS' ? 'selected' : ''}>${i18n.t('editor.spawnTypeMultipleSpawns')}</option>
                            <option value="TEAM_SPAWNS" ${eventConfig['spawn-settings']?.['spawn-type'] === 'TEAM_SPAWNS' ? 'selected' : ''}>${i18n.t('editor.spawnTypeTeamSpawns')}</option>
                            <option value="COMMAND" ${eventConfig['spawn-settings']?.['spawn-type'] === 'COMMAND' ? 'selected' : ''}>${i18n.t('editor.spawnTypeCommand')}</option>
                        </select>
                    </div>
                    <div id="event-spawn-config">
                        ${renderEventSpawnConfig(eventConfig['spawn-settings']?.['spawn-type'] || 'SINGLE_POINT', eventConfig['spawn-settings'] || {})}
                    </div>
                </div>

                <div id="event-tab-equipment" class="tab-content">
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.equipmentGroup')}</label>
                        <input type="text" class="form-control" value="${eventConfig['equipment-group']}"
                               onchange="currentEditingEvent['equipment-group'] = this.value">
                    </div>
                    <div class="toggle-wrapper">
                        <div class="toggle-label">
                            <span>${i18n.t('editor.equipmentInLobby')}</span>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" ${eventConfig['give-equipment-in-lobby'] !== false ? 'checked' : ''}
                                   onchange="currentEditingEvent['give-equipment-in-lobby'] = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>
                    <div class="toggle-wrapper">
                        <div class="toggle-label">
                            <span>${i18n.t('editor.teamColoredArmor')}</span>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" ${eventConfig['lobby-team-colored-armor'] !== false ? 'checked' : ''}
                                   onchange="currentEditingEvent['lobby-team-colored-armor'] = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>
                </div>

                <div id="event-tab-mechanics" class="tab-content">
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.gameMode')}</label>
                        <select class="form-control" id="event-game-mode" value="${eventConfig.mechanics['game-mode']}"
                                onchange="currentEditingEvent.mechanics['game-mode'] = this.value; updateWinConditionUI()">
                            <option value="SOLO" ${eventConfig.mechanics['game-mode'] === 'SOLO' ? 'selected' : ''}>${i18n.t('editor.gameModeSolo')}</option>
                            <option value="TEAM_2" ${eventConfig.mechanics['game-mode'] === 'TEAM_2' ? 'selected' : ''}>${i18n.t('editor.gameModeTeam2')}</option>
                            <option value="TEAM_3" ${eventConfig.mechanics['game-mode'] === 'TEAM_3' ? 'selected' : ''}>${i18n.t('editor.gameModeTeam3')}</option>
                        </select>
                    </div>
                    
                    <!-- Win Condition Settings -->
                    <div class="card" style="margin: 1rem 0; background: var(--background);">
                        <div class="card-header" style="padding: 0.75rem;">
                            <div class="card-title" style="font-size: 0.9rem;">
                                <i class="fas fa-trophy"></i> ${i18n.t('editor.winCondition')}
                            </div>
                        </div>
                        <div class="card-body" style="padding: 0.75rem;">
                            <div class="form-group" style="margin-bottom: 0.75rem;">
                                <label class="form-label">${i18n.t('editor.winConditionType')}</label>
                                <select class="form-control" id="win-condition-type"
                                        onchange="currentEditingEvent['win-condition'] = currentEditingEvent['win-condition'] || {}; currentEditingEvent['win-condition'].type = this.value; updateWinConditionUI()">
                                    <option value="LAST_STANDING" ${(eventConfig['win-condition']?.type || 'LAST_STANDING') === 'LAST_STANDING' ? 'selected' : ''}>${i18n.t('editor.winConditionLastStanding')}</option>
                                    <option value="PICKUP_ITEM" ${eventConfig['win-condition']?.type === 'PICKUP_ITEM' ? 'selected' : ''}>${i18n.t('editor.winConditionPickupItem')}</option>
                                    <option value="KILL_COUNT" ${eventConfig['win-condition']?.type === 'KILL_COUNT' ? 'selected' : ''}>${i18n.t('editor.winConditionKillCount')}</option>
                                    <option value="TIME_SURVIVAL" ${eventConfig['win-condition']?.type === 'TIME_SURVIVAL' ? 'selected' : ''}>${i18n.t('editor.winConditionTimeSurvival')}</option>
                                </select>
                            </div>
                            
                            <!-- Dynamic Win Condition Options -->
                            <div id="win-condition-options">
                                ${renderWinConditionOptions(eventConfig['win-condition'] || { type: 'LAST_STANDING' })}
                            </div>
                        </div>
                    </div>
                    
                    <div class="toggle-wrapper">
                        <div class="toggle-label">
                            <span>${i18n.t('editor.pvpEnabled')}</span>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" ${eventConfig.mechanics['pvp-enabled'] !== false ? 'checked' : ''}
                                   onchange="currentEditingEvent.mechanics['pvp-enabled'] = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>
                    <div class="toggle-wrapper">
                        <div class="toggle-label">
                            <span>${i18n.t('editor.hungerEnabled')}</span>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" ${eventConfig.mechanics['hunger-enabled'] !== false ? 'checked' : ''}
                                   onchange="currentEditingEvent.mechanics['hunger-enabled'] = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>
                    <div class="toggle-wrapper">
                        <div class="toggle-label">
                            <span>${i18n.t('editor.friendlyFire')}</span>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" ${eventConfig.mechanics['friendly-fire'] !== false ? 'checked' : ''}
                                   onchange="currentEditingEvent.mechanics['friendly-fire'] = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>
                </div>

                <div id="event-tab-messages" class="tab-content">
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.messageStart')}</label>
                        <input type="text" class="form-control" value="${eventConfig.messages?.start || ''}"
                               onchange="if (!currentEditingEvent.messages) currentEditingEvent.messages = {}; currentEditingEvent.messages.start = this.value"
                               placeholder="&e&lEvent starting!">
                        <small class="form-help">${i18n.t('editor.messageStartHelp')}</small>
                    </div>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.messageWinner')}</label>
                        <input type="text" class="form-control" value="${eventConfig.messages?.winner || ''}"
                               onchange="if (!currentEditingEvent.messages) currentEditingEvent.messages = {}; currentEditingEvent.messages.winner = this.value"
                               placeholder="&6&l{player} wins!">
                        <small class="form-help">${i18n.t('editor.messageWinnerHelp')}</small>
                    </div>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.messageEliminated')}</label>
                        <input type="text" class="form-control" value="${eventConfig.messages?.eliminated || ''}"
                               onchange="if (!currentEditingEvent.messages) currentEditingEvent.messages = {}; currentEditingEvent.messages.eliminated = this.value"
                               placeholder="&7{player} was eliminated!">
                        <small class="form-help">${i18n.t('editor.messageEliminatedHelp')}</small>
                    </div>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.messageObjective')}</label>
                        <input type="text" class="form-control" value="${eventConfig.messages?.objective || ''}"
                               onchange="if (!currentEditingEvent.messages) currentEditingEvent.messages = {}; currentEditingEvent.messages.objective = this.value"
                               placeholder="&7Goal: win the event">
                        <small class="form-help">${i18n.t('editor.messageObjectiveHelp')}</small>
                    </div>
                </div>

                <div id="event-tab-expert" class="tab-content">
                    <p class="form-help" style="margin-bottom: 1rem;">${i18n.t('editor.expertHint')}</p>

                    <div class="toggle-wrapper">
                        <div class="toggle-label">
                            <span>${i18n.t('editor.regenerateEventWorld')}</span>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" id="event-regen-checkbox"
                                   ${globalRegen ? 'checked disabled' : (eventConfig.worlds['regenerate-event-world'] ? 'checked' : '')}
                                   onchange="currentEditingEvent.worlds['regenerate-event-world'] = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>

                    <div id="event-world-regen-lock-notice" class="alert alert-info" style="margin-top: 0.75rem; ${globalRegen ? 'display: flex;' : 'display: none;'}">
                        <i class="fas fa-lock" style="margin-right: 0.5rem; margin-top: 0.2rem;"></i>
                        <span id="event-world-regen-lock-text">${i18n.t('editor.regenLockedByWorldHint', { world: eventConfig.worlds['event-world'] || '' })}</span>
                    </div>

                    <div class="form-group" style="margin-top: 1rem;">
                        <label class="form-label">${i18n.t('editor.cloneSource')}</label>
                        <select class="form-control" id="event-clone-source-select"
                                onchange="currentEditingEvent.worlds['clone-source-event-world'] = this.value; updateEventCloneSourceUI();">
                            <option value="">${i18n.t('editor.noCloneSource')}</option>
                            ${renderConfiguredWorldOptions(eventConfig.worlds['clone-source-event-world'])}
                        </select>
                        <small class="form-help">${i18n.t('editor.worldMissingHint')}</small>
                    </div>

                    <!-- Warning if no clone source is configured -->
                    <div id="event-no-clone-warning" class="alert alert-warning" style="margin-top: 0.75rem; ${!eventConfig.worlds['clone-source-event-world'] ? 'display: flex;' : 'display: none;'}">
                        <i class="fas fa-exclamation-triangle" style="margin-right: 0.5rem; margin-top: 0.2rem; color: var(--warning, #f59e0b);"></i>
                        <div>
                            <strong>${i18n.t('editor.noCloneSourceWarningTitle')}</strong><br>
                            <span style="font-size: 0.85rem; opacity: 0.9;">${i18n.t('editor.noCloneSourceWarningText')}</span>
                        </div>
                    </div>

                    <!-- Info if clone source is configured -->
                    <div id="event-clone-active-info" class="alert alert-success" style="margin-top: 0.75rem; ${eventConfig.worlds['clone-source-event-world'] ? 'display: flex;' : 'display: none;'}">
                        <i class="fas fa-check-circle" style="margin-right: 0.5rem; margin-top: 0.2rem; color: var(--success, #10b981);"></i>
                        <div>
                            <strong>${i18n.t('editor.cloneSourceActiveTitle')}</strong><br>
                            <span style="font-size: 0.85rem; opacity: 0.9;">${i18n.t('editor.cloneSourceActiveText')}</span>
                        </div>
                    </div>
                </div>

                <div id="event-tab-rewards" class="tab-content">
                    ${renderRewardsEditor(eventConfig.rewards || {})}
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" onclick="closeEventEditor()">${i18n.t('button.cancel')}</button>
                <button class="btn btn-primary" onclick="saveEventEditor()">${i18n.t('button.save')}</button>
            </div>
        </div>
    `;
    
        document.body.appendChild(modal);
    } catch (error) {
        console.error('Error opening event editor:', error);
        showToast(i18n.t('editor.errorOpening') + ': ' + error.message, 'error');
    }
}

function closeEventEditor() {
    document.getElementById('event-editor-modal')?.remove();
    currentEditingEvent = null;
}

// Win Condition Helper Functions
function renderWinConditionOptions(winCondition) {
    const type = winCondition?.type || 'LAST_STANDING';
    const currentItem = winCondition?.item || 'IRON_INGOT';
    const isCustomItem = !['IRON_INGOT', 'GOLD_INGOT', 'DIAMOND', 'EMERALD', 'NETHERITE_INGOT', 'NETHER_STAR', 'DRAGON_EGG', 'TOTEM_OF_UNDYING', 'BEACON', 'HEART_OF_THE_SEA', 'GOLDEN_APPLE', 'ENCHANTED_GOLDEN_APPLE', 'ENDER_PEARL'].includes(currentItem);
    
    switch (type) {
        case 'PICKUP_ITEM':
            return `
                <div class="form-group" style="margin-bottom: 0.5rem;">
                    <label class="form-label" style="font-size: 0.85rem;">${i18n.t('editor.targetItem')}</label>
                    <select class="form-control" id="win-condition-item"
                            onchange="handleWinConditionItemChange(this.value)">
                        <optgroup label="${i18n.t('editor.oresMaterials')}">
                            <option value="IRON_INGOT" ${currentItem === 'IRON_INGOT' ? 'selected' : ''}>${i18n.t('editor.ironIngot')}</option>
                            <option value="GOLD_INGOT" ${currentItem === 'GOLD_INGOT' ? 'selected' : ''}>${i18n.t('editor.goldIngot')}</option>
                            <option value="DIAMOND" ${currentItem === 'DIAMOND' ? 'selected' : ''}>${i18n.t('editor.diamond')}</option>
                            <option value="EMERALD" ${currentItem === 'EMERALD' ? 'selected' : ''}>${i18n.t('editor.emerald')}</option>
                            <option value="NETHERITE_INGOT" ${currentItem === 'NETHERITE_INGOT' ? 'selected' : ''}>${i18n.t('editor.netheriteIngot')}</option>
                        </optgroup>
                        <optgroup label="${i18n.t('editor.specialItems')}">
                            <option value="NETHER_STAR" ${currentItem === 'NETHER_STAR' ? 'selected' : ''}>${i18n.t('editor.netherStar')}</option>
                            <option value="DRAGON_EGG" ${currentItem === 'DRAGON_EGG' ? 'selected' : ''}>${i18n.t('editor.dragonEgg')}</option>
                            <option value="TOTEM_OF_UNDYING" ${currentItem === 'TOTEM_OF_UNDYING' ? 'selected' : ''}>${i18n.t('editor.totemOfUndying')}</option>
                            <option value="BEACON" ${currentItem === 'BEACON' ? 'selected' : ''}>${i18n.t('editor.beacon')}</option>
                            <option value="HEART_OF_THE_SEA" ${currentItem === 'HEART_OF_THE_SEA' ? 'selected' : ''}>${i18n.t('editor.heartOfTheSea')}</option>
                        </optgroup>
                        <optgroup label="${i18n.t('editor.otherItems')}">
                            <option value="GOLDEN_APPLE" ${currentItem === 'GOLDEN_APPLE' ? 'selected' : ''}>${i18n.t('editor.goldenApple')}</option>
                            <option value="ENCHANTED_GOLDEN_APPLE" ${currentItem === 'ENCHANTED_GOLDEN_APPLE' ? 'selected' : ''}>${i18n.t('editor.enchantedGoldenApple')}</option>
                            <option value="ENDER_PEARL" ${currentItem === 'ENDER_PEARL' ? 'selected' : ''}>${i18n.t('editor.enderPearl')}</option>
                        </optgroup>
                        <optgroup label="${i18n.t('editor.customItem')}">
                            <option value="CUSTOM" ${isCustomItem ? 'selected' : ''}>${i18n.t('editor.customItem')}</option>
                        </optgroup>
                    </select>
                </div>
                <div class="form-group" id="custom-item-input-group" style="margin-bottom: 0.5rem; ${isCustomItem ? '' : 'display: none;'}">
                    <label class="form-label" style="font-size: 0.85rem;">${i18n.t('editor.minecraftItemId')}</label>
                    <input type="text" class="form-control" id="win-condition-custom-item" 
                           placeholder="${i18n.t('editor.customItemPlaceholder')}"
                           value="${isCustomItem ? currentItem : ''}"
                           onchange="currentEditingEvent['win-condition'].item = this.value.toUpperCase().trim()">
                    <small style="font-size: 0.7rem; color: var(--text-muted);">${i18n.t('editor.customItemHint')}</small>
                </div>
                <div class="form-group" style="margin-bottom: 0;">
                    <label class="form-label" style="font-size: 0.85rem;">${i18n.t('editor.requiredAmount')}</label>
                    <input type="number" class="form-control" id="win-condition-amount" min="1" max="64" 
                           value="${winCondition?.amount || 1}"
                           onchange="currentEditingEvent['win-condition'].amount = parseInt(this.value)">
                </div>
                <p style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.5rem;">
                    <i class="fas fa-info-circle"></i> ${i18n.t('editor.winConditionPickupDesc')}
                </p>
            `;
            
        case 'KILL_COUNT':
            return `
                <div class="form-group" style="margin-bottom: 0;">
                    <label class="form-label" style="font-size: 0.85rem;">${i18n.t('editor.requiredKills')}</label>
                    <input type="number" class="form-control" id="win-condition-kills" min="1" max="100" 
                           value="${winCondition?.kills || 5}"
                           onchange="currentEditingEvent['win-condition'].kills = parseInt(this.value)">
                </div>
                <p style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.5rem;">
                    <i class="fas fa-info-circle"></i> ${i18n.t('editor.winConditionKillDesc')}
                </p>
            `;
            
        case 'TIME_SURVIVAL':
            return `
                <div class="form-group" style="margin-bottom: 0;">
                    <label class="form-label" style="font-size: 0.85rem;">${i18n.t('editor.survivalTime')}</label>
                    <input type="number" class="form-control" id="win-condition-time" min="30" max="3600" step="30"
                           value="${winCondition?.time || 300}"
                           onchange="currentEditingEvent['win-condition'].time = parseInt(this.value)">
                </div>
                <p style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.5rem;">
                    <i class="fas fa-info-circle"></i> ${i18n.t('editor.winConditionTimeDesc')}
                </p>
            `;
            
        case 'LAST_STANDING':
        default:
            return `
                <p style="font-size: 0.85rem; color: var(--text-muted); margin: 0;">
                    <i class="fas fa-skull-crossbones"></i> ${i18n.t('editor.winConditionLastDesc')}
                </p>
            `;
    }
}

function updateWinConditionUI() {
    const optionsContainer = document.getElementById('win-condition-options');
    if (!optionsContainer) return;
    
    // Ensure win-condition object exists
    currentEditingEvent['win-condition'] = currentEditingEvent['win-condition'] || { type: 'LAST_STANDING' };
    
    const type = document.getElementById('win-condition-type')?.value || 'LAST_STANDING';
    currentEditingEvent['win-condition'].type = type;
    
    optionsContainer.innerHTML = renderWinConditionOptions(currentEditingEvent['win-condition']);
}

function handleWinConditionItemChange(value) {
    const customInputGroup = document.getElementById('custom-item-input-group');
    const customInput = document.getElementById('win-condition-custom-item');
    
    if (value === 'CUSTOM') {
        // Show custom input field
        if (customInputGroup) customInputGroup.style.display = '';
        if (customInput) customInput.focus();
    } else {
        // Hide custom input and set the selected item
        if (customInputGroup) customInputGroup.style.display = 'none';
        currentEditingEvent['win-condition'].item = value;
    }
}

function switchEventTab(tabName) {
    document.querySelectorAll('#event-editor-modal .tab-content').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('#event-editor-modal .tab').forEach(t => t.classList.remove('active'));
    
    document.getElementById(`event-tab-${tabName}`)?.classList.add('active');
    document.querySelector(`#event-editor-modal [onclick="switchEventTab('${tabName}')"]`)?.classList.add('active');
}

function saveEventEditor() {
    if (!currentEditingEvent.command) {
        showToast(i18n.t('error.fieldRequired'), 'error');
        return;
    }

    const eventId = currentEditingEvent.id;
    const eventData = JSON.parse(JSON.stringify(currentEditingEvent));

    // Prüfen, ob keine reale Änderung am Event vorgenommen wurde
    if (currentEditingEventOriginal && isDeepEqual(eventData, currentEditingEventOriginal)) {
        closeEventEditor();
        showToast(i18n.t('info.noChanges'), 'info');
        return;
    }

    CONFIG_STATE.config.events = CONFIG_STATE.config.events || {};
    CONFIG_STATE.config.events[eventId] = eventData;
    
    recordChange('settings', `events.${eventId}`, CONFIG_STATE.config.events[eventId]);
    renderEventsList();
    updateQuickActionsPanel();
    closeEventEditor();
    showToast(i18n.t('events.saved'), 'success');
}

function updateEventSpawnType(spawnType) {
    currentEditingEvent['spawn-settings'] = currentEditingEvent['spawn-settings'] || {};
    currentEditingEvent['spawn-settings']['spawn-type'] = spawnType;
    
    const configDiv = document.getElementById('event-spawn-config');
    if (!configDiv) return;
    
    configDiv.innerHTML = renderEventSpawnConfig(spawnType, currentEditingEvent['spawn-settings']);
}

function renderEventSpawnConfig(spawnType, spawnSettings) {
    switch (spawnType) {
        case 'SINGLE_POINT':
            const single = spawnSettings['single-spawn'] || { x: 0, y: 64, z: 0, yaw: 0, pitch: 0 };
            return `
                <div class="card">
                    <div class="card-header">
                        <div class="card-title">
                            <i class="fas fa-map-marker-alt"></i> ${i18n.t('spawn.singlePoint')}
                        </div>
                    </div>
                    <div class="card-body">
                        <p style="color: var(--text-secondary); margin-bottom: 1rem;">
                            ${i18n.t('spawn.singlePointDesc')}
                        </p>
                        <div class="coords-grid">
                            <div class="coord-input">
                                <label>X</label>
                                <input type="number" class="form-control" value="${single.x}" step="0.5"
                                       onchange="updateEventSpawnCoord('single-spawn', 'x', parseFloat(this.value))">
                            </div>
                            <div class="coord-input">
                                <label>Y</label>
                                <input type="number" class="form-control" value="${single.y}" step="0.5"
                                       onchange="updateEventSpawnCoord('single-spawn', 'y', parseFloat(this.value))">
                            </div>
                            <div class="coord-input">
                                <label>Z</label>
                                <input type="number" class="form-control" value="${single.z}" step="0.5"
                                       onchange="updateEventSpawnCoord('single-spawn', 'z', parseFloat(this.value))">
                            </div>
                            <div class="coord-input">
                                <label>YAW</label>
                                <input type="number" class="form-control" value="${single.yaw}" step="1"
                                       onchange="updateEventSpawnCoord('single-spawn', 'yaw', parseFloat(this.value))">
                            </div>
                            <div class="coord-input">
                                <label>PITCH</label>
                                <input type="number" class="form-control" value="${single.pitch}" step="1"
                                       onchange="updateEventSpawnCoord('single-spawn', 'pitch', parseFloat(this.value))">
                            </div>
                        </div>
                    </div>
                </div>
            `;
            
        case 'RANDOM_RADIUS':
            const center = spawnSettings.center || { x: 0, y: 64, z: 0 };
            const radius = spawnSettings.radius || 30;
            return `
                <div class="card">
                    <div class="card-header">
                        <div class="card-title">
                            <i class="fas fa-circle"></i> ${i18n.t('spawn.randomRadiusTitle')}
                        </div>
                    </div>
                    <div class="card-body">
                        <p style="color: var(--text-secondary); margin-bottom: 1rem;">
                            ${i18n.t('spawn.randomRadiusDesc')}
                        </p>
                        <div class="form-group">
                            <label class="form-label">${i18n.t('spawn.center')}</label>
                            <div class="coords-grid" style="grid-template-columns: repeat(3, 1fr);">
                                <div class="coord-input">
                                    <label>X</label>
                                    <input type="number" class="form-control" value="${center.x}" step="0.5"
                                           onchange="updateEventSpawnCoord('center', 'x', parseFloat(this.value))">
                                </div>
                                <div class="coord-input">
                                    <label>Y</label>
                                    <input type="number" class="form-control" value="${center.y}" step="0.5"
                                           onchange="updateEventSpawnCoord('center', 'y', parseFloat(this.value))">
                                </div>
                                <div class="coord-input">
                                    <label>Z</label>
                                    <input type="number" class="form-control" value="${center.z}" step="0.5"
                                           onchange="updateEventSpawnCoord('center', 'z', parseFloat(this.value))">
                                </div>
                            </div>
                        </div>
                        <div class="form-group">
                            <label class="form-label">${i18n.t('spawn.radius')}</label>
                            <input type="number" class="form-control" value="${radius}" min="1" max="1000"
                                   onchange="currentEditingEvent['spawn-settings'].radius = parseFloat(this.value)">
                        </div>
                    </div>
                </div>
            `;
            
        case 'RANDOM_AREA':
            const minA = spawnSettings.min || { x: -50, y: 64, z: -50 };
            const maxA = spawnSettings.max || { x: 50, y: 64, z: 50 };
            return `
                <div class="card">
                    <div class="card-header">
                        <div class="card-title">
                            <i class="fas fa-vector-square"></i> ${i18n.t('spawn.randomAreaTitle')}
                        </div>
                    </div>
                    <div class="card-body">
                        <p style="color: var(--text-secondary); margin-bottom: 1rem;">
                            ${i18n.t('spawn.randomAreaDesc')}
                        </p>
                        <div class="form-group">
                            <label class="form-label">${i18n.t('spawn.minimum')}</label>
                            <div class="coords-grid" style="grid-template-columns: repeat(3, 1fr);">
                                <div class="coord-input">
                                    <label>X</label>
                                    <input type="number" class="form-control" value="${minA.x}" step="0.5"
                                           onchange="updateEventSpawnCoord('min', 'x', parseFloat(this.value))">
                                </div>
                                <div class="coord-input">
                                    <label>Y</label>
                                    <input type="number" class="form-control" value="${minA.y}" step="0.5"
                                           onchange="updateEventSpawnCoord('min', 'y', parseFloat(this.value))">
                                </div>
                                <div class="coord-input">
                                    <label>Z</label>
                                    <input type="number" class="form-control" value="${minA.z}" step="0.5"
                                           onchange="updateEventSpawnCoord('min', 'z', parseFloat(this.value))">
                                </div>
                            </div>
                        </div>
                        <div class="form-group">
                            <label class="form-label">${i18n.t('spawn.maximum')}</label>
                            <div class="coords-grid" style="grid-template-columns: repeat(3, 1fr);">
                                <div class="coord-input">
                                    <label>X</label>
                                    <input type="number" class="form-control" value="${maxA.x}" step="0.5"
                                           onchange="updateEventSpawnCoord('max', 'x', parseFloat(this.value))">
                                </div>
                                <div class="coord-input">
                                    <label>Y</label>
                                    <input type="number" class="form-control" value="${maxA.y}" step="0.5"
                                           onchange="updateEventSpawnCoord('max', 'y', parseFloat(this.value))">
                                </div>
                                <div class="coord-input">
                                    <label>Z</label>
                                    <input type="number" class="form-control" value="${maxA.z}" step="0.5"
                                           onchange="updateEventSpawnCoord('max', 'z', parseFloat(this.value))">
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            
        case 'RANDOM_CUBE':
            const minC = spawnSettings.min || { x: -50, y: 50, z: -50 };
            const maxC = spawnSettings.max || { x: 50, y: 80, z: 50 };
            return `
                <div class="card">
                    <div class="card-header">
                        <div class="card-title">
                            <i class="fas fa-cube"></i> ${i18n.t('spawn.randomCubeTitle')}
                        </div>
                    </div>
                    <div class="card-body">
                        <p style="color: var(--text-secondary); margin-bottom: 1rem;">
                            ${i18n.t('spawn.randomCubeDesc')}
                        </p>
                        <div class="form-group">
                            <label class="form-label">${i18n.t('spawn.minimum')}</label>
                            <div class="coords-grid" style="grid-template-columns: repeat(3, 1fr);">
                                <div class="coord-input">
                                    <label>X</label>
                                    <input type="number" class="form-control" value="${minC.x}" step="0.5"
                                           onchange="updateEventSpawnCoord('min', 'x', parseFloat(this.value))">
                                </div>
                                <div class="coord-input">
                                    <label>Y</label>
                                    <input type="number" class="form-control" value="${minC.y}" step="0.5"
                                           onchange="updateEventSpawnCoord('min', 'y', parseFloat(this.value))">
                                </div>
                                <div class="coord-input">
                                    <label>Z</label>
                                    <input type="number" class="form-control" value="${minC.z}" step="0.5"
                                           onchange="updateEventSpawnCoord('min', 'z', parseFloat(this.value))">
                                </div>
                            </div>
                        </div>
                        <div class="form-group">
                            <label class="form-label">${i18n.t('spawn.maximum')}</label>
                            <div class="coords-grid" style="grid-template-columns: repeat(3, 1fr);">
                                <div class="coord-input">
                                    <label>X</label>
                                    <input type="number" class="form-control" value="${maxC.x}" step="0.5"
                                           onchange="updateEventSpawnCoord('max', 'x', parseFloat(this.value))">
                                </div>
                                <div class="coord-input">
                                    <label>Y</label>
                                    <input type="number" class="form-control" value="${maxC.y}" step="0.5"
                                           onchange="updateEventSpawnCoord('max', 'y', parseFloat(this.value))">
                                </div>
                                <div class="coord-input">
                                    <label>Z</label>
                                    <input type="number" class="form-control" value="${maxC.z}" step="0.5"
                                           onchange="updateEventSpawnCoord('max', 'z', parseFloat(this.value))">
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            
        case 'MULTIPLE_SPAWNS':
            const spawns = spawnSettings.spawns || [];
            return `
                <div class="card">
                    <div class="card-header">
                        <div class="card-title">
                            <i class="fas fa-map-signs"></i> ${i18n.t('spawn.multipleSpawnsTitle')}
                        </div>
                        <button class="btn btn-secondary" onclick="addEventSpawnPoint()">
                            <i class="fas fa-plus"></i> ${i18n.t('spawn.addSpawn')}
                        </button>
                    </div>
                    <div class="card-body">
                        <p style="color: var(--text-secondary); margin-bottom: 1rem;">
                            ${i18n.t('spawn.multipleSpawnsDesc')}
                        </p>
                        <div id="event-spawn-points">
                            ${spawns.map((spawn, i) => renderEventSpawnPoint(spawn, i)).join('')}
                            ${spawns.length === 0 ? '<p style="color: var(--text-muted);">' + i18n.t('spawn.addSpawn') + '</p>' : ''}
                        </div>
                    </div>
                </div>
            `;
            
        case 'TEAM_SPAWNS':
            const teamSpawns = spawnSettings['team-spawns'] || { team1: [], team2: [] };
            return `
                <div class="card">
                    <div class="card-header">
                        <div class="card-title">
                            <i class="fas fa-users"></i> ${i18n.t('spawn.teamSpawnsTitle')}
                        </div>
                    </div>
                    <div class="card-body">
                        <p style="color: var(--text-secondary); margin-bottom: 1rem;">
                            ${i18n.t('spawn.teamSpawnsDesc')}
                        </p>
                        <div class="collapsible open">
                            <div class="collapsible-header" onclick="toggleCollapsible(this)" style="background: rgba(33, 150, 243, 0.1);">
                                <div class="collapsible-title" style="color: var(--info);">
                                    <i class="fas fa-users"></i>
                                    <span>Team 1 Spawns</span>
                                    <span class="nav-badge">${teamSpawns.team1?.length || 0}</span>
                                </div>
                                <i class="fas fa-chevron-down collapsible-icon"></i>
                            </div>
                            <div class="collapsible-content">
                                <button class="btn btn-secondary" onclick="addTeamSpawnPoint('team1')" style="margin-bottom: 1rem;">
                                    <i class="fas fa-plus"></i> ${i18n.t('spawn.addSpawn')}
                                </button>
                                <div id="team1-spawns">
                                    ${(teamSpawns.team1 || []).map((spawn, i) => renderTeamSpawnPoint('team1', spawn, i)).join('')}
                                </div>
                            </div>
                        </div>
                        <div class="collapsible open">
                            <div class="collapsible-header" onclick="toggleCollapsible(this)" style="background: rgba(244, 67, 54, 0.1);">
                                <div class="collapsible-title" style="color: var(--error);">
                                    <i class="fas fa-users"></i>
                                    <span>Team 2 Spawns</span>
                                    <span class="nav-badge">${teamSpawns.team2?.length || 0}</span>
                                </div>
                                <i class="fas fa-chevron-down collapsible-icon"></i>
                            </div>
                            <div class="collapsible-content">
                                <button class="btn btn-secondary" onclick="addTeamSpawnPoint('team2')" style="margin-bottom: 1rem;">
                                    <i class="fas fa-plus"></i> ${i18n.t('spawn.addSpawn')}
                                </button>
                                <div id="team2-spawns">
                                    ${(teamSpawns.team2 || []).map((spawn, i) => renderTeamSpawnPoint('team2', spawn, i)).join('')}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            
        case 'COMMAND':
            const cmd = spawnSettings.command || '';
            return `
                <div class="card">
                    <div class="card-header">
                        <div class="card-title">
                            <i class="fas fa-terminal"></i> ${i18n.t('spawn.commandTitle')}
                        </div>
                    </div>
                    <div class="card-body">
                        <p style="color: var(--text-secondary); margin-bottom: 1rem;">
                            ${i18n.t('spawn.commandDesc')}
                        </p>
                        <div class="form-group">
                            <label class="form-label">${i18n.t('spawn.spawnCommand')}</label>
                            <input type="text" class="form-control" value="${cmd}" 
                                   placeholder="${i18n.t('spawn.spawnCommandPlaceholder')}"
                                   onchange="currentEditingEvent['spawn-settings'].command = this.value">
                            <small style="color: var(--text-muted);">${i18n.t('spawn.spawnCommandHint')}</small>
                        </div>
                    </div>
                </div>
            `;
            
        default:
            return '<p style="color: var(--text-muted);">' + i18n.t('editor.spawnType') + '</p>';
    }
}

function renderEventSpawnPoint(spawn, index) {
    return `
        <div class="card" style="margin-bottom: 0.5rem;">
            <div class="card-body" style="padding: 0.75rem;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem;">
                    <span style="font-weight: 500;">Spawn #${index + 1}</span>
                    <button class="btn btn-danger btn-icon" onclick="removeEventSpawnPoint(${index})">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
                <div class="coords-grid">
                    <div class="coord-input">
                        <label>X</label>
                        <input type="number" class="form-control" value="${spawn.x || 0}" step="0.5"
                               onchange="updateEventSpawnPointCoord(${index}, 'x', parseFloat(this.value))">
                    </div>
                    <div class="coord-input">
                        <label>Y</label>
                        <input type="number" class="form-control" value="${spawn.y || 64}" step="0.5"
                               onchange="updateEventSpawnPointCoord(${index}, 'y', parseFloat(this.value))">
                    </div>
                    <div class="coord-input">
                        <label>Z</label>
                        <input type="number" class="form-control" value="${spawn.z || 0}" step="0.5"
                               onchange="updateEventSpawnPointCoord(${index}, 'z', parseFloat(this.value))">
                    </div>
                    <div class="coord-input">
                        <label>YAW</label>
                        <input type="number" class="form-control" value="${spawn.yaw || 0}" step="1"
                               onchange="updateEventSpawnPointCoord(${index}, 'yaw', parseFloat(this.value))">
                    </div>
                    <div class="coord-input">
                        <label>PITCH</label>
                        <input type="number" class="form-control" value="${spawn.pitch || 0}" step="1"
                               onchange="updateEventSpawnPointCoord(${index}, 'pitch', parseFloat(this.value))">
                    </div>
                </div>
            </div>
        </div>
    `;
}

function renderTeamSpawnPoint(team, spawn, index) {
    return `
        <div style="display: flex; gap: 0.5rem; align-items: center; margin-bottom: 0.5rem; padding: 0.5rem; background: var(--background); border-radius: 6px;">
            <span style="width: 20px; color: var(--text-muted);">#${index + 1}</span>
            <input type="number" class="form-control" value="${spawn.x || 0}" step="0.5" placeholder="X" style="width: 70px;"
                   onchange="updateTeamSpawnCoord('${team}', ${index}, 'x', parseFloat(this.value))">
            <input type="number" class="form-control" value="${spawn.y || 64}" step="0.5" placeholder="Y" style="width: 70px;"
                   onchange="updateTeamSpawnCoord('${team}', ${index}, 'y', parseFloat(this.value))">
            <input type="number" class="form-control" value="${spawn.z || 0}" step="0.5" placeholder="Z" style="width: 70px;"
                   onchange="updateTeamSpawnCoord('${team}', ${index}, 'z', parseFloat(this.value))">
            <input type="number" class="form-control" value="${spawn.yaw || 0}" step="1" placeholder="Yaw" style="width: 60px;"
                   onchange="updateTeamSpawnCoord('${team}', ${index}, 'yaw', parseFloat(this.value))">
            <button class="btn btn-danger btn-icon" onclick="removeTeamSpawnPoint('${team}', ${index})">
                <i class="fas fa-times"></i>
            </button>
        </div>
    `;
}

function updateEventSpawnCoord(key, coord, value) {
    currentEditingEvent['spawn-settings'] = currentEditingEvent['spawn-settings'] || {};
    currentEditingEvent['spawn-settings'][key] = currentEditingEvent['spawn-settings'][key] || {};
    currentEditingEvent['spawn-settings'][key][coord] = value;
}

function addEventSpawnPoint() {
    currentEditingEvent['spawn-settings'].spawns = currentEditingEvent['spawn-settings'].spawns || [];
    currentEditingEvent['spawn-settings'].spawns.push({ x: 0, y: 64, z: 0, yaw: 0, pitch: 0 });
    
    const container = document.getElementById('event-spawn-points');
    if (container) {
        const index = currentEditingEvent['spawn-settings'].spawns.length - 1;
        container.innerHTML = currentEditingEvent['spawn-settings'].spawns.map((spawn, i) => 
            renderEventSpawnPoint(spawn, i)
        ).join('');
    }
}

function removeEventSpawnPoint(index) {
    currentEditingEvent['spawn-settings'].spawns.splice(index, 1);
    const container = document.getElementById('event-spawn-points');
    if (container) {
        container.innerHTML = currentEditingEvent['spawn-settings'].spawns.map((spawn, i) => 
            renderEventSpawnPoint(spawn, i)
        ).join('');
    }
}

function updateEventSpawnPointCoord(index, coord, value) {
    if (currentEditingEvent['spawn-settings'].spawns && currentEditingEvent['spawn-settings'].spawns[index]) {
        currentEditingEvent['spawn-settings'].spawns[index][coord] = value;
    }
}

function addTeamSpawnPoint(team) {
    currentEditingEvent['spawn-settings']['team-spawns'] = currentEditingEvent['spawn-settings']['team-spawns'] || { team1: [], team2: [] };
    currentEditingEvent['spawn-settings']['team-spawns'][team] = currentEditingEvent['spawn-settings']['team-spawns'][team] || [];
    currentEditingEvent['spawn-settings']['team-spawns'][team].push({ x: 0, y: 64, z: 0, yaw: 0, pitch: 0 });
    
    const container = document.getElementById(`${team}-spawns`);
    if (container) {
        container.innerHTML = currentEditingEvent['spawn-settings']['team-spawns'][team].map((spawn, i) => 
            renderTeamSpawnPoint(team, spawn, i)
        ).join('');
    }
}

function removeTeamSpawnPoint(team, index) {
    currentEditingEvent['spawn-settings']['team-spawns'][team].splice(index, 1);
    const container = document.getElementById(`${team}-spawns`);
    if (container) {
        container.innerHTML = currentEditingEvent['spawn-settings']['team-spawns'][team].map((spawn, i) => 
            renderTeamSpawnPoint(team, spawn, i)
        ).join('');
    }
}

function updateTeamSpawnCoord(team, index, coord, value) {
    if (currentEditingEvent['spawn-settings']['team-spawns']?.[team]?.[index]) {
        currentEditingEvent['spawn-settings']['team-spawns'][team][index][coord] = value;
    }
}

// ============================================
// World Editor
// ============================================

/** Merkt sich die Eingaben des Multiverse-Tabs; nichts davon landet in worlds.yml. */
let currentWorldCreateSpec = null;

/** Preset-Key beim Oeffnen des Editors; null beim Neuanlegen. Erkennt Overwrites beim Speichern. */
let currentEditingWorldOriginalId = null;

function defaultWorldCreateSpec() {
    return {
        environment: 'NORMAL',
        worldType: 'NORMAL',
        seed: '',
        generator: '',
        generatorSettings: '',
        biome: '',
        generateStructures: true,
        adjustSpawn: true
    };
}

/**
 * World-ID beim Neuanlegen: Dropdown der real existierenden Server-Welten, plus die
 * Moeglichkeit, eine freie ID einzutippen (fuer Presets ohne dedizierte Welt).
 *
 * Welten, die bereits Key eines Presets sind, werden deaktiviert angeboten -- der Preset-Key
 * IST der Weltname, ein zweites Preset darauf wuerde das erste ueberschreiben.
 */
function renderWorldIdSelector(currentValue) {
    const worlds = (window.MV_STATE && MV_STATE.worlds) || [];
    const isKnownWorld = worlds.some(w => w.name === currentValue);
    const customSelected = Boolean(currentValue) && !isKnownWorld;

    let options = `<option value="" ${!currentValue ? 'selected' : ''}>${i18n.t('editor.worldIdChoose')}</option>`;

    for (const world of worlds) {
        const taken = isWorldUsedAsPreset(world.name);
        const state = world.loaded ? i18n.t('card.mvLoaded') : i18n.t('card.mvUnloaded');
        const usage = describeMvUsage(world, null);
        let label = `${world.name} — ${world.environment || '?'} · ${state}`;
        if (taken) {
            label += ` · ${i18n.t('editor.worldIdTaken')}`;
        } else if (usage) {
            label += ` · ${i18n.t('mv.usedBy')}: ${usage}`;
        }
        options += `<option value="${escapeAttr(world.name)}" ${taken ? 'disabled' : ''} ${world.name === currentValue ? 'selected' : ''}>${escapeHtml(label)}</option>`;
    }

    options += `<option value="__custom__" ${customSelected ? 'selected' : ''}>${i18n.t('editor.worldIdCustom')}</option>`;

    return `
        <select class="form-control" id="world-id-select" onchange="onWorldIdSelectChange(this.value)">
            ${options}
        </select>
        <input type="text" class="form-control" id="world-id-custom" style="margin-top: 0.5rem; ${customSelected ? '' : 'display: none;'}"
               value="${customSelected ? escapeAttr(currentValue) : ''}"
               placeholder="${i18n.t('editor.worldIdPlaceholder')}"
               oninput="onWorldIdCustomInput(this.value)">`;
}

function onWorldIdSelectChange(value) {
    const customInput = document.getElementById('world-id-custom');
    if (value === '__custom__') {
        if (customInput) {
            customInput.style.display = '';
            customInput.focus();
            currentEditingWorld.id = customInput.value.trim();
        }
    } else {
        if (customInput) customInput.style.display = 'none';
        currentEditingWorld.id = value;
    }
    refreshWorldIdDependentUi();
}

function onWorldIdCustomInput(value) {
    currentEditingWorld.id = value.trim();
    refreshWorldIdDependentUi();
}

/** Statuszeile und Multiverse-Tab neu zeichnen, wenn sich die gewaehlte World-ID aendert. */
function refreshWorldIdDependentUi() {
    const statusEl = document.getElementById('world-id-status');
    if (statusEl) statusEl.innerHTML = renderWorldIdStatus(currentEditingWorld.id);

    const mvTab = document.getElementById('world-tab-multiverse');
    if (mvTab) mvTab.innerHTML = renderWorldMultiverseTab(currentEditingWorld.id);
}

/** Einzeiler unter dem World-ID-Feld: existiert die Welt, ist sie geladen, oder Platzhalter? */
function renderWorldIdStatus(worldId) {
    if (!worldId) return '';
    if (!window.MV_STATE || !MV_STATE.loaded) return '';

    const status = getMvStatus(worldId);
    if (status.state === 'placeholder') {
        return `<span class="mv-hint mv-hint-muted"><i class="fas fa-circle-info"></i> ${i18n.t('editor.mvStatusPlaceholder')}</span>`;
    }
    if (status.state === 'loaded') {
        return `<span class="mv-hint mv-hint-ok"><i class="fas fa-circle-check"></i> ${i18n.t('editor.mvStatusLoaded')}</span>`;
    }
    return `<span class="mv-hint mv-hint-warn"><i class="fas fa-circle-pause"></i> ${i18n.t('editor.mvStatusUnloaded')}</span>`;
}

/**
 * Multiverse-Tab: Weltstatus, optionale Erstellungsparameter und die destruktiven Aktionen.
 * Alle Felder sind optional -- ein Preset darf ohne jede Serverwelt gespeichert werden.
 */
function renderWorldMultiverseTab(worldId) {
    if (!window.MV_STATE || !MV_STATE.loaded) {
        return `<p class="form-help">${i18n.t('mv.loading')}</p>`;
    }
    if (MV_STATE.stale) {
        // Kein Erstellen-Formular auf Basis veralteter Daten: die Welt koennte laengst existieren.
        return `
            <div class="alert alert-warning" style="display: flex; gap: 0.5rem;">
                <i class="fas fa-triangle-exclamation" style="margin-top: 0.2rem;"></i>
                <div>
                    <strong>${i18n.t('mv.staleTitle')}</strong><br>
                    <span style="font-size: 0.85rem; opacity: 0.9;">${i18n.t('mv.staleHint')}</span>
                </div>
            </div>
            <button class="btn btn-secondary" style="margin-top: 1rem;" onclick="refreshMvWorlds()">
                <i class="fas fa-rotate"></i> ${i18n.t('button.mvRetry')}
            </button>`;
    }
    if (!MV_STATE.available) {
        return `
            <div class="alert alert-warning" style="display: flex; gap: 0.5rem;">
                <i class="fas fa-plug-circle-xmark" style="margin-top: 0.2rem;"></i>
                <div>
                    <strong>${i18n.t('mv.backendMissing')}</strong><br>
                    <span style="font-size: 0.85rem; opacity: 0.9;">${i18n.t('mv.backendMissingHint')}</span>
                </div>
            </div>`;
    }
    if (!worldId) {
        return `<p class="form-help">${i18n.t('editor.mvChooseIdFirst')}</p>`;
    }

    currentWorldCreateSpec = currentWorldCreateSpec || defaultWorldCreateSpec();
    const spec = currentWorldCreateSpec;
    const status = getMvStatus(worldId);
    const advanced = MV_STATE.supportsAdvancedOptions;

    // Existiert die Welt bereits, ist Erstellen sinnlos -- dann nur Status + Loeschaktion.
    if (status.state !== 'placeholder') {
        return `
            <div class="alert alert-success" style="display: flex; gap: 0.5rem;">
                <i class="fas fa-circle-check" style="margin-top: 0.2rem;"></i>
                <div>
                    <strong>${i18n.t('editor.mvWorldExistsTitle', { id: worldId })}</strong><br>
                    <span style="font-size: 0.85rem; opacity: 0.9;">
                        ${status.state === 'loaded' ? i18n.t('editor.mvStatusLoaded') : i18n.t('editor.mvStatusUnloaded')}
                    </span>
                </div>
            </div>

            <div style="display: flex; gap: 0.5rem; margin-top: 1rem; flex-wrap: wrap;">
                ${status.state === 'loaded'
                    ? `<button class="btn btn-secondary" onclick="mvUnloadWorld('${escapeAttr(worldId)}')">
                           <i class="fas fa-eject"></i> ${i18n.t('button.mvUnload')}</button>`
                    : `<button class="btn btn-secondary" onclick="mvLoadWorld('${escapeAttr(worldId)}')">
                           <i class="fas fa-play"></i> ${i18n.t('button.mvLoad')}</button>`}
                <button class="btn btn-danger" onclick="openDeleteWorldModal('${escapeAttr(worldId)}', true)">
                    <i class="fas fa-trash"></i> ${i18n.t('button.mvDeleteWorldOnly')}
                </button>
            </div>
            <p class="form-help" style="margin-top: 0.5rem;">${i18n.t('editor.mvDeleteWorldOnlyHint')}</p>`;
    }

    return `
        <div class="alert alert-info" style="display: flex; gap: 0.5rem;">
            <i class="fas fa-circle-info" style="margin-top: 0.2rem;"></i>
            <div>
                <strong>${i18n.t('editor.mvStatusPlaceholder')}</strong><br>
                <span style="font-size: 0.85rem; opacity: 0.9;">${i18n.t('editor.mvCreateOptional')}</span>
            </div>
        </div>

        <div class="form-group" style="margin-top: 1rem;">
            <label class="form-label">${i18n.t('editor.mvEnvironment')}</label>
            <select class="form-control" onchange="updateWorldCreateSpec('environment', this.value)">
                <option value="NORMAL" ${spec.environment === 'NORMAL' ? 'selected' : ''}>NORMAL</option>
                <option value="NETHER" ${spec.environment === 'NETHER' ? 'selected' : ''}>NETHER</option>
                <option value="THE_END" ${spec.environment === 'THE_END' ? 'selected' : ''}>THE_END</option>
            </select>
        </div>

        <div class="form-group">
            <label class="form-label">${i18n.t('editor.mvWorldType')}</label>
            <select class="form-control" onchange="updateWorldCreateSpec('worldType', this.value)">
                <option value="NORMAL" ${spec.worldType === 'NORMAL' ? 'selected' : ''}>NORMAL</option>
                <option value="FLAT" ${spec.worldType === 'FLAT' ? 'selected' : ''}>FLAT</option>
                <option value="LARGE_BIOMES" ${spec.worldType === 'LARGE_BIOMES' ? 'selected' : ''}>LARGE_BIOMES</option>
                <option value="AMPLIFIED" ${spec.worldType === 'AMPLIFIED' ? 'selected' : ''}>AMPLIFIED</option>
            </select>
        </div>

        <div class="form-group">
            <label class="form-label">${i18n.t('editor.mvSeed')}</label>
            <input type="text" class="form-control" value="${escapeAttr(spec.seed)}"
                   placeholder="${i18n.t('editor.mvSeedPlaceholder')}"
                   oninput="updateWorldCreateSpec('seed', this.value)">
        </div>

        <div class="form-group">
            <label class="form-label">${i18n.t('editor.mvGenerator')}</label>
            <input type="text" class="form-control" value="${escapeAttr(spec.generator)}"
                   placeholder="z.B. VoidGen oder Plugin:id"
                   oninput="updateWorldCreateSpec('generator', this.value)">
        </div>

        <div class="form-group">
            <label class="form-label">${i18n.t('editor.mvBiome')}</label>
            <input type="text" class="form-control" value="${escapeAttr(spec.biome)}"
                   ${advanced ? '' : 'disabled'} placeholder="z.B. plains"
                   oninput="updateWorldCreateSpec('biome', this.value)">
            <small class="form-help">${advanced ? i18n.t('editor.mvBiomeHint') : i18n.t('editor.mvNeedsMv5')}</small>
        </div>

        <div class="form-group">
            <label class="form-label">${i18n.t('editor.mvGeneratorSettings')}</label>
            <textarea class="form-control" rows="3" ${advanced ? '' : 'disabled'}
                      placeholder='{"layers": []}'
                      oninput="updateWorldCreateSpec('generatorSettings', this.value)">${escapeHtml(spec.generatorSettings)}</textarea>
            <small class="form-help">${advanced ? i18n.t('editor.mvGeneratorSettingsHint') : i18n.t('editor.mvNeedsMv5')}</small>
        </div>

        <div class="toggle-wrapper">
            <div class="toggle-label">
                <span>${i18n.t('editor.mvGenerateStructures')}</span>
                <span>${i18n.t('editor.mvGenerateStructuresHint')}</span>
            </div>
            <label class="toggle">
                <input type="checkbox" ${spec.generateStructures ? 'checked' : ''}
                       onchange="updateWorldCreateSpec('generateStructures', this.checked)">
                <span class="toggle-slider"></span>
            </label>
        </div>

        <div class="toggle-wrapper">
            <div class="toggle-label">
                <span>${i18n.t('editor.mvAdjustSpawn')}</span>
                <span>${i18n.t('editor.mvAdjustSpawnHint')}</span>
            </div>
            <label class="toggle">
                <input type="checkbox" ${spec.adjustSpawn ? 'checked' : ''}
                       onchange="updateWorldCreateSpec('adjustSpawn', this.checked)">
                <span class="toggle-slider"></span>
            </label>
        </div>

        <button class="btn btn-primary" style="margin-top: 1rem;" id="mv-create-button"
                onclick="createMvWorldFromEditor('${escapeAttr(worldId)}')">
            <i class="fas fa-wand-magic-sparkles"></i> ${i18n.t('button.mvCreateWorldNow')}
        </button>
        <p class="form-help" style="margin-top: 0.5rem;">${i18n.t('editor.mvCreateHint')}</p>`;
}

function updateWorldCreateSpec(key, value) {
    currentWorldCreateSpec = currentWorldCreateSpec || defaultWorldCreateSpec();
    currentWorldCreateSpec[key] = typeof value === 'string' ? value.trim() : value;
}

/** Erstellt die Welt auf dem Server. Das Preset selbst wird erst beim Speichern geschrieben. */
async function createMvWorldFromEditor(worldId) {
    const button = document.getElementById('mv-create-button');
    if (button) {
        button.disabled = true;
        button.innerHTML = `<i class="fas fa-spinner fa-spin"></i> ${i18n.t('editor.mvCreating')}`;
    }

    const spec = Object.assign({ world: worldId }, currentWorldCreateSpec || defaultWorldCreateSpec());
    const ok = await runMvJob('/api/mvworlds/create', spec, i18n.t('toast.mvCreating', { id: worldId }));

    if (ok) {
        showToast(i18n.t('toast.mvCreated', { id: worldId }), 'success');
        refreshWorldIdDependentUi();
        renderWorldsList();
        renderServerWorldsPanel();
    } else if (button) {
        button.disabled = false;
        button.innerHTML = `<i class="fas fa-wand-magic-sparkles"></i> ${i18n.t('button.mvCreateWorldNow')}`;
    }
}

function createNewWorld() {
    currentEditingWorld = {
        id: '',
        'display-name': 'New World',
        'pvpwager-world-enable': true,
        'build-allowed': false,
        'regenerate-world': false,
        'clone-source-world': '',
        'pvpwager-spawn': {
            'spawn-type': 'FIXED_SPAWNS',
            spawns: {
                spectator: { x: 0, y: 80, z: 0, yaw: 0, pitch: 0 },
                player1: { x: 10, y: 64, z: 0, yaw: 90, pitch: 0 },
                player2: { x: -10, y: 64, z: 0, yaw: -90, pitch: 0 }
            }
        },
        'allowed-equipment-groups': ['all']
    };
    currentWorldCreateSpec = defaultWorldCreateSpec();
    currentEditingWorldOriginalId = null;
    currentEditingWorldOriginal = null;
    openWorldEditor(currentEditingWorld, true);
}

/**
 * @param initialTab optional: Tab, der direkt geoeffnet wird (z.B. 'multiverse', wenn der
 *                   Nutzer auf einer Platzhalter-Karte "Welt erstellen" geklickt hat)
 */
function editWorld(worldId, initialTab = null) {
    const world = CONFIG_STATE.worlds.worlds?.[worldId];
    if (!world) return;

    currentEditingWorld = JSON.parse(JSON.stringify(world));
    currentEditingWorld.id = worldId;
    currentEditingWorldOriginal = JSON.parse(JSON.stringify(world));
    currentWorldCreateSpec = defaultWorldCreateSpec();
    currentEditingWorldOriginalId = worldId;
    openWorldEditor(currentEditingWorld, false);
    if (initialTab) switchWorldTab(initialTab);
}

function openWorldEditor(worldConfig, isNew = false) {
    console.log('openWorldEditor called with:', worldConfig);
    
    // Ensure all required properties exist with defaults
    worldConfig = worldConfig || {};
    // Beim Neuanlegen bleibt die ID bewusst leer: das Dropdown startet dann auf
    // "Welt auswaehlen" statt auf einer erfundenen ID, die es auf dem Server nicht gibt.
    worldConfig.id = worldConfig.id || '';
    worldConfig['display-name'] = worldConfig['display-name'] || i18n.t('editor.newWorld');
    worldConfig['pvpwager-world-enable'] = worldConfig['pvpwager-world-enable'] || false;
    worldConfig['pvpwager-spawn'] = worldConfig['pvpwager-spawn'] || {};
    worldConfig['pvpwager-spawn']['spawn-type'] = worldConfig['pvpwager-spawn']['spawn-type'] || 'FIXED_SPAWNS';
    worldConfig['pvpwager-spawn'].spawns = worldConfig['pvpwager-spawn'].spawns || {};
    
    try {
        const spawnType = worldConfig['pvpwager-spawn']['spawn-type'];
        const spawns = worldConfig['pvpwager-spawn'].spawns;
        
        const modal = document.createElement('div');
        modal.className = 'modal-overlay active';
        modal.id = 'world-editor-modal';
    
        modal.innerHTML = `
        <div class="modal" style="max-width: 900px; max-height: 90vh;">
            <div class="modal-header">
                <h3 class="modal-title">
                    <i class="fas fa-globe"></i>
                    ${isNew ? i18n.t('editor.newWorld') : i18n.t('editor.editWorld') + ' ' + worldConfig['display-name']}
                </h3>
                <button class="modal-close" onclick="closeWorldEditor()">
                    <i class="fas fa-times"></i>
                </button>
            </div>
            <div class="modal-body" style="max-height: calc(90vh - 140px); overflow-y: auto;">
                <div class="tabs">
                    <div class="tab active" onclick="switchWorldTab('basic')">${i18n.t('editor.tabBase')}</div>
                    <div class="tab" onclick="switchWorldTab('spawns')">${i18n.t('editor.tabSpawns')}</div>
                    <div class="tab" onclick="switchWorldTab('settings')">${i18n.t('editor.tabSettings')}</div>
                    <div class="tab" onclick="switchWorldTab('multiverse')"><i class="fas fa-earth-europe"></i> ${i18n.t('editor.tabMultiverse')}</div>
                    <div class="tab" onclick="switchWorldTab('expert')"><i class="fas fa-wrench"></i> ${i18n.t('editor.tabExpert')}</div>
                </div>

                <!-- Basic Tab -->
                <div id="world-tab-basic" class="tab-content active">
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.worldId')}</label>
                        ${isNew ? renderWorldIdSelector(worldConfig.id) : `
                        <input type="text" class="form-control" id="world-id" value="${escapeHtml(worldConfig.id)}"
                               disabled>`}
                        <small class="form-help">${i18n.t('editor.worldIdHint')}</small>
                        <div id="world-id-status" class="mv-status-line">${renderWorldIdStatus(worldConfig.id)}</div>
                    </div>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.displayName')}</label>
                        <input type="text" class="form-control" id="world-name" value="${worldConfig['display-name']}"
                               onchange="currentEditingWorld['display-name'] = this.value">
                    </div>
                    
                    <div class="toggle-wrapper">
                        <div class="toggle-label">
                            <span>${i18n.t('editor.enablePvPWorld')}</span>
                            <span>${i18n.t('editor.canBeUsedInEvents')}</span>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" id="world-pvp-enable" ${worldConfig['pvpwager-world-enable'] !== false ? 'checked' : ''}
                                   onchange="currentEditingWorld['pvpwager-world-enable'] = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>

                    <div class="toggle-wrapper">
                        <div class="toggle-label">
                            <span>${i18n.t('editor.allowBuilding')}</span>
                            <span>${i18n.t('label.buildAllowed')}</span>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" id="world-build" ${worldConfig['build-allowed'] ? 'checked' : ''}
                                   onchange="currentEditingWorld['build-allowed'] = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>
                </div>

                <!-- Spawns Tab -->
                <div id="world-tab-spawns" class="tab-content">
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.spawnConfig')}</label>
                        <select class="form-control" id="world-spawn-type" 
                                onchange="updateWorldSpawnType(this.value)">
                            <option value="FIXED_SPAWNS" ${spawnType === 'FIXED_SPAWNS' ? 'selected' : ''}>${i18n.t('editor.spawnTypeFixed')}</option>
                            <option value="RANDOM_RADIUS" ${spawnType === 'RANDOM_RADIUS' ? 'selected' : ''}>${i18n.t('editor.spawnTypeRandom')}</option>
                            <option value="RANDOM_AREA" ${spawnType === 'RANDOM_AREA' ? 'selected' : ''}>${i18n.t('editor.spawnTypeRandom')}</option>
                        </select>
                    </div>

                    <div id="world-spawn-config">
                        ${renderWorldSpawnConfig(spawnType, spawns)}
                    </div>
                </div>

                <!-- Expert Tab -->
                <div id="world-tab-expert" class="tab-content">
                    <p class="form-help" style="margin-bottom: 1rem;">${i18n.t('editor.expertHint')}</p>

                    <div class="toggle-wrapper">
                        <div class="toggle-label">
                            <span>${i18n.t('editor.regenerateWorld')}</span>
                            <span>${i18n.t('label.regenEnabled')}</span>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" id="world-regen" ${worldConfig['regenerate-world'] ? 'checked' : ''}
                                   onchange="currentEditingWorld['regenerate-world'] = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>

                    <div class="form-group" style="margin-top: 1rem;">
                        <label class="form-label">${i18n.t('editor.cloneSourceWorld')}</label>
                        <select class="form-control" id="world-clone-select"
                                onchange="currentEditingWorld['clone-source-world'] = this.value; updateWorldCloneSourceUI();">
                            <option value="">${i18n.t('editor.noCloneSource')}</option>
                            ${renderConfiguredWorldOptions(worldConfig['clone-source-world'], worldConfig.id)}
                        </select>
                        <small class="form-help">${i18n.t('editor.worldMissingHint')}</small>
                    </div>

                    <!-- Warning if no clone source is configured -->
                    <div id="world-no-clone-warning" class="alert alert-warning" style="margin-top: 0.75rem; ${!worldConfig['clone-source-world'] ? 'display: flex;' : 'display: none;'}">
                        <i class="fas fa-exclamation-triangle" style="margin-right: 0.5rem; margin-top: 0.2rem; color: var(--warning, #f59e0b);"></i>
                        <div>
                            <strong>${i18n.t('editor.noCloneSourceWarningTitle')}</strong><br>
                            <span style="font-size: 0.85rem; opacity: 0.9;">${i18n.t('editor.noCloneSourceWarningText')}</span>
                        </div>
                    </div>

                    <!-- Info if clone source is configured -->
                    <div id="world-clone-active-info" class="alert alert-success" style="margin-top: 0.75rem; ${worldConfig['clone-source-world'] ? 'display: flex;' : 'display: none;'}">
                        <i class="fas fa-check-circle" style="margin-right: 0.5rem; margin-top: 0.2rem; color: var(--success, #10b981);"></i>
                        <div>
                            <strong>${i18n.t('editor.cloneSourceActiveTitle')}</strong><br>
                            <span style="font-size: 0.85rem; opacity: 0.9;">${i18n.t('editor.cloneSourceActiveText')}</span>
                        </div>
                    </div>
                </div>

                <!-- Multiverse Tab -->
                <div id="world-tab-multiverse" class="tab-content">
                    ${renderWorldMultiverseTab(worldConfig.id)}
                </div>

                <!-- Settings Tab -->
                <div id="world-tab-settings" class="tab-content">
                    <div class="form-group">
                        <label class="form-label">${i18n.t('label.allowedWorlds')}</label>
                        <div id="world-equipment-groups" class="tag-input-container">
                            ${(worldConfig['allowed-equipment-groups'] || ['all']).map(g => `
                                <span class="tag">
                                    ${g}
                                    <span class="tag-remove" onclick="removeWorldEquipmentGroup('${g}')">&times;</span>
                                </span>
                            `).join('')}
                            <input type="text" class="tag-input" placeholder="${i18n.t('button.add')}..." 
                                   onkeydown="addWorldEquipmentGroup(event)">
                        </div>
                        <small style="color: var(--text-muted);">'all' = ${i18n.t('card.allWorlds')}</small>
                    </div>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" onclick="closeWorldEditor()">${i18n.t('button.cancel')}</button>
                <button class="btn btn-primary" onclick="saveWorldEditor()">
                    <i class="fas fa-save"></i> ${i18n.t('button.save')}
                </button>
            </div>
        </div>
    `;
    
        document.body.appendChild(modal);
    } catch (error) {
        console.error('Error opening world editor:', error);
        showToast('Error opening editor: ' + error.message, 'error');
    }
}

function renderWorldSpawnConfig(spawnType, spawns) {
    if (spawnType === 'FIXED_SPAWNS') {
        const spectator = spawns.spectator || { x: 0, y: 80, z: 0, yaw: 0, pitch: 0 };
        const player1 = spawns.player1 || { x: 10, y: 64, z: 0, yaw: 90, pitch: 0 };
        const player2 = spawns.player2 || { x: -10, y: 64, z: 0, yaw: -90, pitch: 0 };
        
        return `
            <div class="card" style="margin-bottom: 1rem;">
                <div class="card-header" style="background: rgba(255, 152, 0, 0.1);">
                    <div class="card-title" style="color: var(--warning);">
                        <i class="fas fa-eye"></i> ${i18n.t('spawn.spectator')}
                    </div>
                </div>
                <div class="card-body">
                    <div class="coords-grid">
                        <div class="coord-input">
                            <label>X</label>
                            <input type="number" class="form-control" value="${spectator.x}" step="0.5"
                                   onchange="updateWorldSpawn('spectator', 'x', parseFloat(this.value))">
                        </div>
                        <div class="coord-input">
                            <label>Y</label>
                            <input type="number" class="form-control" value="${spectator.y}" step="0.5"
                                   onchange="updateWorldSpawn('spectator', 'y', parseFloat(this.value))">
                        </div>
                        <div class="coord-input">
                            <label>Z</label>
                            <input type="number" class="form-control" value="${spectator.z}" step="0.5"
                                   onchange="updateWorldSpawn('spectator', 'z', parseFloat(this.value))">
                        </div>
                        <div class="coord-input">
                            <label>YAW</label>
                            <input type="number" class="form-control" value="${spectator.yaw}" step="1"
                                   onchange="updateWorldSpawn('spectator', 'yaw', parseFloat(this.value))">
                        </div>
                        <div class="coord-input">
                            <label>PITCH</label>
                            <input type="number" class="form-control" value="${spectator.pitch}" step="1"
                                   onchange="updateWorldSpawn('spectator', 'pitch', parseFloat(this.value))">
                        </div>
                    </div>
                </div>
            </div>

            <div class="card" style="margin-bottom: 1rem;">
                <div class="card-header" style="background: rgba(33, 150, 243, 0.1);">
                    <div class="card-title" style="color: var(--info);">
                        <i class="fas fa-user"></i> ${i18n.t('spawn.player1')}
                    </div>
                </div>
                <div class="card-body">
                    <div class="coords-grid">
                        <div class="coord-input">
                            <label>X</label>
                            <input type="number" class="form-control" value="${player1.x}" step="0.5"
                                   onchange="updateWorldSpawn('player1', 'x', parseFloat(this.value))">
                        </div>
                        <div class="coord-input">
                            <label>Y</label>
                            <input type="number" class="form-control" value="${player1.y}" step="0.5"
                                   onchange="updateWorldSpawn('player1', 'y', parseFloat(this.value))">
                        </div>
                        <div class="coord-input">
                            <label>Z</label>
                            <input type="number" class="form-control" value="${player1.z}" step="0.5"
                                   onchange="updateWorldSpawn('player1', 'z', parseFloat(this.value))">
                        </div>
                        <div class="coord-input">
                            <label>YAW</label>
                            <input type="number" class="form-control" value="${player1.yaw}" step="1"
                                   onchange="updateWorldSpawn('player1', 'yaw', parseFloat(this.value))">
                        </div>
                        <div class="coord-input">
                            <label>PITCH</label>
                            <input type="number" class="form-control" value="${player1.pitch}" step="1"
                                   onchange="updateWorldSpawn('player1', 'pitch', parseFloat(this.value))">
                        </div>
                    </div>
                </div>
            </div>

            <div class="card">
                <div class="card-header" style="background: rgba(244, 67, 54, 0.1);">
                    <div class="card-title" style="color: var(--error);">
                        <i class="fas fa-user"></i> ${i18n.t('spawn.player2')}
                    </div>
                </div>
                <div class="card-body">
                    <div class="coords-grid">
                        <div class="coord-input">
                            <label>X</label>
                            <input type="number" class="form-control" value="${player2.x}" step="0.5"
                                   onchange="updateWorldSpawn('player2', 'x', parseFloat(this.value))">
                        </div>
                        <div class="coord-input">
                            <label>Y</label>
                            <input type="number" class="form-control" value="${player2.y}" step="0.5"
                                   onchange="updateWorldSpawn('player2', 'y', parseFloat(this.value))">
                        </div>
                        <div class="coord-input">
                            <label>Z</label>
                            <input type="number" class="form-control" value="${player2.z}" step="0.5"
                                   onchange="updateWorldSpawn('player2', 'z', parseFloat(this.value))">
                        </div>
                        <div class="coord-input">
                            <label>YAW</label>
                            <input type="number" class="form-control" value="${player2.yaw}" step="1"
                                   onchange="updateWorldSpawn('player2', 'yaw', parseFloat(this.value))">
                        </div>
                        <div class="coord-input">
                            <label>PITCH</label>
                            <input type="number" class="form-control" value="${player2.pitch}" step="1"
                                   onchange="updateWorldSpawn('player2', 'pitch', parseFloat(this.value))">
                        </div>
                    </div>
                </div>
            </div>
        `;
    } else if (spawnType === 'RANDOM_RADIUS') {
        const center = spawns.center || { x: 0, y: 64, z: 0 };
        const radius = spawns.radius || 20;
        return `
            <div class="card">
                <div class="card-header">
                    <div class="card-title">
                        <i class="fas fa-circle"></i> ${i18n.t('label.randomSpawnRadius')}
                    </div>
                </div>
                <div class="card-body">
                    <p style="color: var(--text-secondary); margin-bottom: 1rem;">
                        ${i18n.t('label.randomSpawnRadiusDesc')}
                    </p>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('label.center')}</label>
                        <div class="coords-grid" style="grid-template-columns: repeat(3, 1fr);">
                            <div class="coord-input">
                                <label>X</label>
                                <input type="number" class="form-control" value="${center.x}" step="0.5"
                                       onchange="updateWorldSpawn('center', 'x', parseFloat(this.value))">
                            </div>
                            <div class="coord-input">
                                <label>Y</label>
                                <input type="number" class="form-control" value="${center.y}" step="0.5"
                                       onchange="updateWorldSpawn('center', 'y', parseFloat(this.value))">
                            </div>
                            <div class="coord-input">
                                <label>Z</label>
                                <input type="number" class="form-control" value="${center.z}" step="0.5"
                                       onchange="updateWorldSpawn('center', 'z', parseFloat(this.value))">
                            </div>
                        </div>
                    </div>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('label.radius')}</label>
                        <input type="number" class="form-control" value="${radius}" min="1" max="1000"
                               onchange="updateWorldSpawnRadius(parseFloat(this.value))">
                    </div>
                </div>
            </div>
        `;
    } else if (spawnType === 'RANDOM_AREA') {
        const min = spawns.min || { x: -50, y: 64, z: -50 };
        const max = spawns.max || { x: 50, y: 64, z: 50 };
        return `
            <div class="card">
                <div class="card-header">
                    <div class="card-title">
                        <i class="fas fa-vector-square"></i> ${i18n.t('spawn.randomAreaTitle')}
                    </div>
                </div>
                <div class="card-body">
                    <p style="color: var(--text-secondary); margin-bottom: 1rem;">
                        ${i18n.t('spawn.randomAreaDesc')}
                    </p>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('spawn.minimum')}</label>
                        <div class="coords-grid" style="grid-template-columns: repeat(3, 1fr);">
                            <div class="coord-input">
                                <label>X</label>
                                <input type="number" class="form-control" value="${min.x}" step="0.5"
                                       onchange="updateWorldSpawn('min', 'x', parseFloat(this.value))">
                            </div>
                            <div class="coord-input">
                                <label>Y</label>
                                <input type="number" class="form-control" value="${min.y}" step="0.5"
                                       onchange="updateWorldSpawn('min', 'y', parseFloat(this.value))">
                            </div>
                            <div class="coord-input">
                                <label>Z</label>
                                <input type="number" class="form-control" value="${min.z}" step="0.5"
                                       onchange="updateWorldSpawn('min', 'z', parseFloat(this.value))">
                            </div>
                        </div>
                    </div>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('spawn.maximum')}</label>
                        <div class="coords-grid" style="grid-template-columns: repeat(3, 1fr);">
                            <div class="coord-input">
                                <label>X</label>
                                <input type="number" class="form-control" value="${max.x}" step="0.5"
                                       onchange="updateWorldSpawn('max', 'x', parseFloat(this.value))">
                            </div>
                            <div class="coord-input">
                                <label>Y</label>
                                <input type="number" class="form-control" value="${max.y}" step="0.5"
                                       onchange="updateWorldSpawn('max', 'y', parseFloat(this.value))">
                            </div>
                            <div class="coord-input">
                                <label>Z</label>
                                <input type="number" class="form-control" value="${max.z}" step="0.5"
                                       onchange="updateWorldSpawn('max', 'z', parseFloat(this.value))">
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }
    return `<p style="color: var(--text-muted);">${i18n.t('spawn.notConfigured')}</p>`;
}

function updateWorldSpawnType(spawnType) {
    currentEditingWorld['pvpwager-spawn'] = currentEditingWorld['pvpwager-spawn'] || {};
    currentEditingWorld['pvpwager-spawn']['spawn-type'] = spawnType;
    
    // Reset spawns based on type
    if (spawnType === 'FIXED_SPAWNS') {
        currentEditingWorld['pvpwager-spawn'].spawns = {
            spectator: { x: 0, y: 80, z: 0, yaw: 0, pitch: 0 },
            player1: { x: 10, y: 64, z: 0, yaw: 90, pitch: 0 },
            player2: { x: -10, y: 64, z: 0, yaw: -90, pitch: 0 }
        };
    } else if (spawnType === 'RANDOM_RADIUS') {
        currentEditingWorld['pvpwager-spawn'].spawns = {
            center: { x: 0, y: 64, z: 0 },
            radius: 20
        };
    } else if (spawnType === 'RANDOM_AREA') {
        currentEditingWorld['pvpwager-spawn'].spawns = {
            min: { x: -50, y: 64, z: -50 },
            max: { x: 50, y: 64, z: 50 }
        };
    }
    
    const configDiv = document.getElementById('world-spawn-config');
    configDiv.innerHTML = renderWorldSpawnConfig(spawnType, currentEditingWorld['pvpwager-spawn'].spawns);
}

function updateWorldSpawn(spawnKey, coord, value) {
    currentEditingWorld['pvpwager-spawn'] = currentEditingWorld['pvpwager-spawn'] || {};
    currentEditingWorld['pvpwager-spawn'].spawns = currentEditingWorld['pvpwager-spawn'].spawns || {};
    currentEditingWorld['pvpwager-spawn'].spawns[spawnKey] = currentEditingWorld['pvpwager-spawn'].spawns[spawnKey] || {};
    currentEditingWorld['pvpwager-spawn'].spawns[spawnKey][coord] = value;
}

function updateWorldSpawnRadius(radius) {
    currentEditingWorld['pvpwager-spawn'] = currentEditingWorld['pvpwager-spawn'] || {};
    currentEditingWorld['pvpwager-spawn'].spawns = currentEditingWorld['pvpwager-spawn'].spawns || {};
    currentEditingWorld['pvpwager-spawn'].spawns.radius = radius;
}

function switchWorldTab(tabName) {
    document.querySelectorAll('#world-editor-modal .tab-content').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('#world-editor-modal .tab').forEach(t => t.classList.remove('active'));
    
    document.getElementById(`world-tab-${tabName}`)?.classList.add('active');
    document.querySelector(`#world-editor-modal [onclick="switchWorldTab('${tabName}')"]`)?.classList.add('active');
}

function addWorldEquipmentGroup(event) {
    if (event.key === 'Enter' && event.target.value.trim()) {
        const group = event.target.value.trim();
        currentEditingWorld['allowed-equipment-groups'] = currentEditingWorld['allowed-equipment-groups'] || [];
        if (!currentEditingWorld['allowed-equipment-groups'].includes(group)) {
            currentEditingWorld['allowed-equipment-groups'].push(group);
            const container = document.getElementById('world-equipment-groups');
            const input = container.querySelector('input');
            const tag = document.createElement('span');
            tag.className = 'tag';
            tag.innerHTML = `${group}<span class="tag-remove" onclick="removeWorldEquipmentGroup('${group}')">&times;</span>`;
            container.insertBefore(tag, input);
        }
        event.target.value = '';
    }
}

function removeWorldEquipmentGroup(group) {
    currentEditingWorld['allowed-equipment-groups'] = (currentEditingWorld['allowed-equipment-groups'] || []).filter(g => g !== group);
    document.querySelectorAll('#world-equipment-groups .tag').forEach(tag => {
        if (tag.textContent.trim().replace('×', '') === group) {
            tag.remove();
        }
    });
}

function closeWorldEditor() {
    document.getElementById('world-editor-modal')?.remove();
    currentEditingWorld = null;
}

function saveWorldEditor() {
    if (!currentEditingWorld.id || currentEditingWorld.id.trim() === '') {
        showToast(i18n.t('label.worldIdEmpty'), 'error');
        return;
    }

    CONFIG_STATE.worlds.worlds = CONFIG_STATE.worlds.worlds || {};
    const worldId = currentEditingWorld.id.trim();

    // Der Preset-Key ist der Weltname. Eine frei eingetippte ID kann daher auf ein bestehendes
    // Preset zeigen -- das waere ein stiller Overwrite, also vorher nachfragen.
    const isNewPreset = !currentEditingWorldOriginalId;
    if (isNewPreset && Object.prototype.hasOwnProperty.call(CONFIG_STATE.worlds.worlds, worldId)) {
        if (!confirm(i18n.t('confirm.worldIdExists', { id: worldId }))) return;
    }

    const worldData = JSON.parse(JSON.stringify(currentEditingWorld));
    delete worldData.id; // ID ist der Key, nicht Teil der Daten

    // Prüfen, ob keine reale Änderung am Welt-Preset vorliegt
    if (currentEditingWorldOriginal && currentEditingWorldOriginalId === worldId && isDeepEqual(worldData, currentEditingWorldOriginal)) {
        closeWorldEditor();
        showToast(i18n.t('info.noChanges'), 'info');
        return;
    }

    CONFIG_STATE.worlds.worlds[worldId] = worldData;

    recordChange('worlds', `worlds.${worldId}`, CONFIG_STATE.worlds.worlds[worldId]);
    renderWorldsList();
    renderServerWorldsPanel();
    updateQuickActionsPanel();
    closeWorldEditor();
    showToast(i18n.t('worlds.saved'), 'success');
}

// ============================================
// Equipment Editor
// ============================================

// Die frueher hier stehende Tabelle MINECRAFT_ITEMS_EXTENDED (86 Items in 14 Kategorien)
// ist entfallen. Item-Liste und Kategorien kommen aus ITEM_CATALOG (items.js), gefuellt
// von /api/materials - damit deckt die Auswahl den vollstaendigen Item-Bestand des
// laufenden Servers ab statt eines handgepflegten Ausschnitts.

function createNewEquipment() {
    currentEditingEquipmentOriginal = null;
    currentEditingEquipment = {
        id: '',
        // Getrennte Schalter je System - das alte gemeinsame 'enabled' gibt es seit 1.0.9
        // nicht mehr, weil ein Set durchaus nur im PvP oder nur in Events gelten soll.
        'pvpwager-equip-enable': true,
        'event-equip-enable': true,
        'display-name': i18n.t('editor.newEquipment'),
        'allowed-pvpwager-worlds': 'all',
        armor: {
            helmet: null,
            chestplate: null,
            leggings: null,
            boots: null
        },
        offhand: null,
        inventory: [],
        // Ans Ende der Anzeigereihenfolge; verschoben wird in der Uebersicht.
        order: nextEquipmentOrder()
    };
    openEquipmentEditor(currentEditingEquipment, true);
}

function editEquipment(equipId) {
    const equip = equipmentSets()[equipId];
    if (!equip) return;
    
    currentEditingEquipment = JSON.parse(JSON.stringify(equip));
    currentEditingEquipment.id = equipId;
    currentEditingEquipmentOriginal = JSON.parse(JSON.stringify(equip));
    openEquipmentEditor(currentEditingEquipment, false);
}

function openEquipmentEditor(equipConfig, isNew = false) {
    console.log('openEquipmentEditor called with:', equipConfig);
    
    // Ensure all required properties exist with defaults
    equipConfig = equipConfig || {};
    equipConfig.id = equipConfig.id || '';
    equipConfig['display-name'] = equipConfig['display-name'] || i18n.t('editor.newEquipment');
    // Sets aus einer noch nicht migrierten Datei tragen das gemeinsame 'enabled'. Es hier
    // auf die beiden neuen Schalter abbilden, damit im Editor nichts falsch angehakt ist.
    const legacyEnabled = equipConfig.enabled !== false;
    equipConfig['pvpwager-equip-enable'] = equipConfig['pvpwager-equip-enable'] !== undefined
        ? equipConfig['pvpwager-equip-enable'] !== false : legacyEnabled;
    equipConfig['event-equip-enable'] = equipConfig['event-equip-enable'] !== undefined
        ? equipConfig['event-equip-enable'] !== false : legacyEnabled;
    delete equipConfig.enabled;
    equipConfig['allowed-pvpwager-worlds'] = equipConfig['allowed-pvpwager-worlds'] || 'all';
    equipConfig.armor = equipConfig.armor || {};
    equipConfig.inventory = equipConfig.inventory || [];
    // Alt-Format: das Icon stand bis 1.0.9 in 'gui-item.material'. Ohne diese Zeile stuende das
    // Feld bei einem bestehenden Set leer da, und das Speichern wuerde das Icon verwerfen.
    if (!equipConfig.icon && equipConfig['gui-item'] && equipConfig['gui-item'].material) {
        equipConfig.icon = equipConfig['gui-item'].material;
    }
    
    try {
        const modal = document.createElement('div');
        modal.className = 'modal-overlay active';
        modal.id = 'equipment-editor-modal';
    
        modal.innerHTML = `
        <div class="modal" style="max-width: 1000px; max-height: 90vh;">
            <div class="modal-header">
                <h3 class="modal-title">
                    <i class="fas fa-shield-alt"></i>
                    ${isNew ? i18n.t('editor.newEquipment') : i18n.t('editor.editEquipment') + ' ' + equipConfig['display-name']}
                </h3>
                <button class="modal-close" onclick="closeEquipmentEditor()">
                    <i class="fas fa-times"></i>
                </button>
            </div>
            <div class="modal-body" style="max-height: calc(90vh - 140px); overflow-y: auto;">
                <div class="tabs">
                    <div class="tab active" onclick="switchEquipmentTab('basic')">${i18n.t('editor.tabBase')}</div>
                    <div class="tab" onclick="switchEquipmentTab('armor')">${i18n.t('editor.tabArmor')}</div>
                    <div class="tab" onclick="switchEquipmentTab('inventory')">${i18n.t('editor.tabInventory')}</div>
                </div>

                <!-- Basic Tab -->
                <div id="equipment-tab-basic" class="tab-content active">
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.equipmentId')}</label>
                        <input type="text" class="form-control" id="equipment-id" value="${escapeHtml(equipConfig.id)}"
                               ${isNew ? '' : 'disabled'}
                               placeholder="diamond_pvp"
                               onchange="currentEditingEquipment.id = this.value">
                        <small style="color: var(--text-muted);">${i18n.t('editor.equipmentIdHint')}</small>
                    </div>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.displayName')}</label>
                        <input type="text" class="form-control" id="equipment-name" value="${escapeHtml(equipConfig['display-name'])}"
                               oninput="currentEditingEquipment['display-name'] = this.value; updateEquipmentTextPreview()">
                        <small style="color: var(--text-muted);">${i18n.t('item.help.colorCodes')}</small>
                    </div>

                    <!-- Icon im Ingame-Auswahlmenue. Stand bis 1.0.9 in einem eigenen Tab, zusammen
                         mit Titel und Lore - beides gab es hier aber schon. -->
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.equipmentIcon')}</label>
                        <div style="display:flex;align-items:center;gap:0.75rem;">
                            <div class="item-picker-item" style="width:44px;height:44px;flex:0 0 auto;" id="equipment-icon-preview">
                                ${equipConfig.icon ? itemIconHtml(equipConfig.icon, 36) : '<i class="fas fa-wand-magic-sparkles" style="color:var(--text-muted);"></i>'}
                            </div>
                            <input type="text" class="form-control" id="equipment-icon" value="${escapeAttr(equipConfig.icon || '')}"
                                   placeholder="${i18n.t('editor.equipmentIconAuto')}"
                                   oninput="onEquipmentIconInput(this.value)">
                        </div>
                        <small style="color: var(--text-muted);">${i18n.t('editor.equipmentIconHint')}</small>
                        <div id="equipment-icon-suggestions" style="display:flex;flex-wrap:wrap;gap:0.35rem;margin-top:0.5rem;"></div>
                    </div>

                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.equipmentDescription')}</label>
                        <textarea class="form-control" id="equipment-description" rows="3"
                                  placeholder="${i18n.t('editor.equipmentDescriptionPlaceholder')}"
                                  oninput="currentEditingEquipment.description = this.value; updateEquipmentTextPreview()">${escapeHtml(equipConfig.description || '')}</textarea>
                        <small style="color: var(--text-muted);">${i18n.t('editor.equipmentDescriptionHint')}</small>
                        <div style="margin-top:0.5rem;">
                            <div id="equipment-text-preview" class="mc-preview"></div>
                        </div>
                    </div>

                    <!-- Zwei getrennte Schalter: ein Set kann nur im PvP oder nur in Events gelten. -->
                    <div class="toggle-wrapper">
                        <div class="toggle-label">
                            <span>${i18n.t('editor.enableForPvp')}</span>
                            <span>${i18n.t('editor.enableForPvpHint')}</span>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" id="equipment-enabled-pvp" ${equipConfig['pvpwager-equip-enable'] ? 'checked' : ''}
                                   onchange="currentEditingEquipment['pvpwager-equip-enable'] = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>

                    <div class="toggle-wrapper">
                        <div class="toggle-label">
                            <span>${i18n.t('editor.enableForEvents')}</span>
                            <span>${i18n.t('editor.enableForEventsHint')}</span>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" id="equipment-enabled-event" ${equipConfig['event-equip-enable'] ? 'checked' : ''}
                                   onchange="currentEditingEquipment['event-equip-enable'] = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>

                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.allowedPvPWorlds')}</label>
                        <select class="form-control" id="equipment-worlds-mode"
                                onchange="onEquipmentWorldsModeChange(this.value)">
                            <option value="all" ${equipmentWorldsMode(equipConfig) === 'all' ? 'selected' : ''}>${i18n.t('editor.allWorlds')}</option>
                            <option value="none" ${equipmentWorldsMode(equipConfig) === 'none' ? 'selected' : ''}>${i18n.t('editor.noneEventsOnly')}</option>
                            <option value="list" ${equipmentWorldsMode(equipConfig) === 'list' ? 'selected' : ''}>${i18n.t('editor.specificWorlds')}</option>
                        </select>
                        <small style="color: var(--text-muted);">${i18n.t('editor.allowedPvPWorldsHint')}</small>
                        <div id="equipment-worlds-list" style="margin-top:0.75rem;">
                            ${renderEquipmentWorldChoices(equipConfig)}
                        </div>
                    </div>
                </div>

                <!-- Armor Tab -->
                <div id="equipment-tab-armor" class="tab-content">
                    <div class="form-hint-inline">
                        <i class="fas fa-lightbulb"></i> ${i18n.t('editor.armorClickHint')}
                    </div>
                    <div class="equipment-preview">
                        <div class="armor-slots">
                            <p style="color: var(--text-secondary); margin-bottom: 1rem; text-align: center;">${i18n.t('editor.tabArmor')}</p>
                            ${renderArmorSlot('helmet', equipConfig.armor?.helmet, i18n.t('editor.helmet'))}
                            ${renderArmorSlot('chestplate', equipConfig.armor?.chestplate, i18n.t('editor.chestplate'))}
                            ${renderArmorSlot('leggings', equipConfig.armor?.leggings, i18n.t('editor.leggings'))}
                            ${renderArmorSlot('boots', equipConfig.armor?.boots, i18n.t('editor.boots'))}
                            <div style="margin-top: 1rem; border-top: 1px solid var(--border); padding-top: 1rem;">
                                ${renderArmorSlot('offhand', equipConfig.offhand, i18n.t('editor.offhand'))}
                            </div>
                        </div>
                        <div style="flex: 1;">
                            <div class="search-box" style="margin-bottom: 1rem;">
                                <i class="fas fa-search"></i>
                                <input type="text" class="form-control" placeholder="${i18n.t('editor.searchItem')}"
                                       id="equipment-armor-search" oninput="filterArmorItems(this.value)">
                            </div>
                            <div id="armor-item-picker-inline">
                                ${renderArmorPicker('')}
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Inventory Tab -->
                <div id="equipment-tab-inventory" class="tab-content">
                    <div class="form-hint-inline">
                        <i class="fas fa-lightbulb"></i> ${i18n.t('editor.inventoryDoubleClickHint')}
                    </div>
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 2rem;">
                        <div>
                            <p style="color: var(--text-secondary); margin-bottom: 1rem;">${i18n.t('editor.tabInventory')} (36 Slots)</p>
                            <div class="inventory-grid" id="equipment-inventory-grid">
                                ${renderInventoryGrid(equipConfig.inventory || [])}
                            </div>
                            <div style="margin-top: 1rem;">
                                <button class="btn btn-secondary" onclick="clearEquipmentInventory()">
                                    <i class="fas fa-trash"></i> ${i18n.t('editor.clearInventory')}
                                </button>
                            </div>
                        </div>
                        <div>
                            <p style="color: var(--text-secondary); margin-bottom: 1rem;">${i18n.t('equipment.addItem')}</p>
                            <div class="search-box" style="margin-bottom: 1rem;">
                                <i class="fas fa-search"></i>
                                <input type="text" class="form-control" placeholder="${i18n.t('editor.searchItem')}" 
                                       id="equipment-item-search" oninput="filterEquipmentItems(this.value)">
                            </div>
                            <div id="equipment-item-categories">
                                ${renderItemCategories()}
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" onclick="closeEquipmentEditor()">${i18n.t('button.cancel')}</button>
                <button class="btn btn-primary" onclick="saveEquipmentEditor()">
                    <i class="fas fa-save"></i> ${i18n.t('button.save')}
                </button>
            </div>
        </div>
    `;

        document.body.appendChild(modal);
        updateEquipmentTextPreview();

        // Setup drag & drop event listeners after modal is in DOM
        setupInventoryDragDropListeners();
    } catch (error) {
        console.error('Error opening equipment editor:', error);
        showToast('Error opening editor: ' + error.message, 'error');
    }
}

// ============================================
// Equipment: Basis-Tab (Welten, Textvorschau)
// ============================================

/** 'all' | 'none' | 'list' - wie allowed-pvpwager-worlds gerade belegt ist. */
function equipmentWorldsMode(equipConfig) {
    const value = equipConfig['allowed-pvpwager-worlds'];
    if (Array.isArray(value)) return 'list';
    const text = String(value === undefined || value === null ? 'all' : value).trim().toLowerCase();
    if (text === 'all' || text === '') return 'all';
    if (text === 'none') return 'none';
    return 'list';  // Komma-Liste in Textform
}

/** Die aktuell erlaubten Welten als Array, egal in welcher Schreibweise sie gespeichert sind. */
function equipmentWorldsList(equipConfig) {
    const value = equipConfig['allowed-pvpwager-worlds'];
    if (Array.isArray(value)) return value.map(w => String(w).trim()).filter(Boolean);
    if (typeof value === 'string' && equipmentWorldsMode(equipConfig) === 'list') {
        return value.split(',').map(w => w.trim()).filter(Boolean);
    }
    return [];
}

/**
 * Auswahlkaesten fuer die Weltenliste.
 *
 * Das Dropdown bot bisher nur 'all' und 'none', obwohl der Server
 * (EquipmentManager.applyAllowedWorldsFromSection) Komma- und YAML-Listen laengst versteht -
 * es fehlte schlicht die Eingabemoeglichkeit. Welten, die in der Konfiguration stehen, aber
 * auf dem Server nicht existieren, werden trotzdem angezeigt, damit sie beim Speichern nicht
 * stillschweigend verschwinden.
 */
function renderEquipmentWorldChoices(equipConfig) {
    if (equipmentWorldsMode(equipConfig) !== 'list') {
        return '';
    }
    const selected = equipmentWorldsList(equipConfig);
    const available = (typeof MV_STATE !== 'undefined' && MV_STATE.worlds)
        ? MV_STATE.worlds.map(w => w.name)
        : [];
    const all = [...new Set(available.concat(selected))].sort();

    if (all.length === 0) {
        return `<p class="form-label-hint">${i18n.t('editor.noWorldsAvailable')}</p>`;
    }

    return `<div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(190px,1fr));gap:0.35rem;">
        ${all.map(world => {
            const missing = available.length > 0 && !available.includes(world);
            return `<label style="display:flex;align-items:center;gap:0.4rem;font-size:0.85rem;cursor:pointer;">
                <input type="checkbox" class="equipment-world-choice" value="${escapeAttr(world)}"
                       ${selected.includes(world) ? 'checked' : ''} onchange="saveEquipmentWorlds()">
                <span${missing ? ' style="color:var(--warning);" title="' + escapeAttr(i18n.t('editor.worldMissing')) + '"' : ''}>
                    ${escapeHtml(world)}${missing ? ' *' : ''}
                </span>
            </label>`;
        }).join('')}
    </div>`;
}

function onEquipmentWorldsModeChange(mode) {
    if (mode === 'all' || mode === 'none') {
        currentEditingEquipment['allowed-pvpwager-worlds'] = mode;
    } else if (!Array.isArray(currentEditingEquipment['allowed-pvpwager-worlds'])) {
        currentEditingEquipment['allowed-pvpwager-worlds'] = [];
    }
    const container = document.getElementById('equipment-worlds-list');
    if (container) container.innerHTML = renderEquipmentWorldChoices(currentEditingEquipment);
}

/** Uebernimmt die angehakten Welten als Liste - genau die Form, die der Server erwartet. */
function saveEquipmentWorlds() {
    const worlds = [];
    document.querySelectorAll('.equipment-world-choice').forEach(box => {
        if (box.checked) worlds.push(box.value);
    });
    // Ohne Auswahl waere die Absicht mehrdeutig; 'none' ist die eindeutige Schreibweise dafuer.
    currentEditingEquipment['allowed-pvpwager-worlds'] = worlds.length > 0 ? worlds : 'none';
}

/** Anzeigename und Beschreibung so zeigen, wie Minecraft sie faerbt. */
function updateEquipmentTextPreview() {
    const preview = document.getElementById('equipment-text-preview');
    if (!preview) return;

    const lines = [];
    const name = document.getElementById('equipment-name');
    if (name && name.value.trim()) lines.push(minecraftColorHtml(name.value));
    const description = document.getElementById('equipment-description');
    if (description) {
        description.value.split('\n').forEach(line => {
            if (line.trim()) lines.push(minecraftColorHtml(line, '#aaaaaa'));
        });
    }
    preview.innerHTML = lines.length > 0
        ? lines.map(l => `<div>${l}</div>`).join('')
        : `<div style="color:var(--text-muted);font-style:italic;">${i18n.t('item.label.previewEmpty')}</div>`;
}

// ============================================
// Equipment: Icon im Auswahlmenue
// ============================================

/**
 * Materialeingabe: Icon aktualisieren und passende Vorschlaege anbieten.
 *
 * Das Feld sass bis 1.0.9 in einem eigenen Tab "Menue-Icon", zusammen mit Titel, Lore und
 * einem 9x6-Slotraster. Titel und Lore standen im Basis-Tab bereits als 'display-name' und
 * 'description'; das Raster war die falsche Metapher, weil das Auswahlmenue die Sets
 * fortlaufend aufreiht statt sie frei zu platzieren - die Reihenfolge wird jetzt in der
 * Uebersicht per Hoch/Runter gesetzt. Geblieben ist das Icon, und das steht hier im Basis-Tab.
 */
const onEquipmentIconInput = debounce(function (value) {
    const icon = document.getElementById('equipment-icon-preview');
    const name = String(value || '').trim().toUpperCase();
    const known = itemEntry(name);

    if (icon) {
        icon.innerHTML = known
            ? itemIconHtml(name, 36)
            : '<i class="fas fa-wand-magic-sparkles" style="color:var(--text-muted);"></i>';
    }

    const suggestions = document.getElementById('equipment-icon-suggestions');
    if (suggestions) {
        // Nur solange die Eingabe kein exakter Treffer ist - sonst steht die Auswahl im Weg.
        const matches = (name && !known) ? searchItems(name, { limit: 12 }).entries : [];
        suggestions.innerHTML = matches.map(entry => `
            <div class="item-picker-item" style="width:32px;height:32px;"
                 onclick="applyEquipmentIcon('${escapeAttr(entry.name)}')"
                 title="${escapeHtml(itemDisplayName(entry.name))}">
                ${itemIconHtml(entry.name, 26, { lazy: true })}
            </div>`).join('');
    }
}, 150);

function applyEquipmentIcon(material) {
    const field = document.getElementById('equipment-icon');
    if (field) field.value = material;
    onEquipmentIconInput(material);
}

/**
 * Uebernimmt das Icon-Feld.
 *
 * Ein evtl. noch vorhandener Altblock 'gui-item' wird dabei entfernt - sonst stuenden Icon und
 * Reihenfolge in der YAML doppelt, und der Server wuerde beim naechsten Start wieder den alten
 * Wert lesen.
 */
function saveEquipmentIcon(target) {
    const material = valueOf('equipment-icon').trim().toUpperCase();
    if (material) {
        target.icon = material;
    } else {
        delete target.icon;
    }
    delete target['gui-item'];
}

function renderArmorSlot(slotType, currentItem, label) {
    const hasItem = currentItem && currentItem !== 'AIR';
    const tooltipText = hasItem ? i18n.t('tooltip.clickToEdit') : i18n.t('tooltip.clickToSelect');
    return `
        <div class="armor-slot ${hasItem ? 'filled' : ''}" 
             onclick="clickArmorSlot('${slotType}')"
             id="armor-slot-${slotType}"
             title="${label} - ${tooltipText}">
            ${hasItem ? `
                ${itemIconHtml(currentItem, 40)}
            ` : `
                <i class="fas fa-${getSlotIcon(slotType)}" style="color: var(--text-muted);"></i>
            `}
        </div>
        <small style="color: var(--text-muted); font-size: 0.7rem;">${label}</small>
    `;
}

function getSlotIcon(slotType) {
    const icons = {
        helmet: 'hard-hat',
        chestplate: 'tshirt',
        leggings: 'socks',
        boots: 'shoe-prints',
        offhand: 'hand-paper'
    };
    return icons[slotType] || 'box';
}

/**
 * Ruestungsauswahl im Armor-Tab.
 *
 * Die vier Ruestungsabschnitte kommen aus dem Server-Katalog (Feld `armorSlot`), sind also
 * automatisch vollstaendig - inklusive Ruestung, die es zur Entstehungszeit dieses Panels
 * noch gar nicht gab. Der Offhand-Abschnitt listet bewusst alle Items, weil die Nebenhand
 * in Minecraft beliebige Items aufnimmt; ohne Suchbegriff zeigt er nur die gaengigen.
 */
function renderArmorPicker(term) {
    const sections = [
        { slot: 'helmet', label: i18n.t('picker.helmets') },
        { slot: 'chestplate', label: i18n.t('picker.chestplates') },
        { slot: 'leggings', label: i18n.t('picker.leggings') },
        { slot: 'boots', label: i18n.t('picker.boots') }
    ];

    let html = sections.map(section => {
        const matches = ITEM_CATALOG.materials.filter(entry =>
            entry.armorSlot === section.slot && matchesTerm(entry.name, term));
        return renderPickerSection(section.label, matches, 'setArmorItem', term.length > 0);
    }).join('');

    // Nebenhand: ohne Suche eine kurze, sinnvolle Vorauswahl, mit Suche der ganze Katalog.
    const offhandDefaults = ['SHIELD', 'TOTEM_OF_UNDYING', 'ENDER_PEARL', 'FIREWORK_ROCKET',
        'ARROW', 'SPECTRAL_ARROW', 'TIPPED_ARROW', 'FILLED_MAP', 'TORCH', 'GOLDEN_APPLE',
        'ENCHANTED_GOLDEN_APPLE', 'EXPERIENCE_BOTTLE'];
    const offhand = term
        ? searchItems(term, { limit: 120 }).entries
        : offhandDefaults.map(itemEntry).filter(Boolean);
    html += renderPickerSection(i18n.t('picker.offhandItems'), offhand, 'setArmorItem', term.length > 0);

    return html;
}

/** Ein aufklappbarer Abschnitt der Item-Auswahl. */
function renderPickerSection(label, entries, clickHandler, expanded) {
    const body = entries.length === 0
        ? `<div style="color:var(--text-muted);font-size:0.85rem;padding:0.5rem;">${i18n.t('picker.noResults')}</div>`
        : entries.map(entry => `
            <div class="item-picker-item" style="width: 40px; height: 40px;"
                 onclick="${clickHandler}('${escapeAttr(entry.name)}')"
                 title="${escapeHtml(itemDisplayName(entry.name))} (${escapeHtml(entry.name)})">
                ${itemIconHtml(entry.name, 32, { lazy: true })}
            </div>
        `).join('');

    return `
        <div class="collapsible${expanded ? ' open' : ''}" style="margin-bottom: 0.5rem;">
            <div class="collapsible-header" onclick="toggleCollapsible(this)">
                <div class="collapsible-title">
                    <span>${label}</span>
                    <span class="nav-badge">${entries.length}</span>
                </div>
                <i class="fas fa-chevron-down collapsible-icon"></i>
            </div>
            <div class="collapsible-content">
                <div style="display: flex; flex-wrap: wrap; gap: 0.5rem;">
                    ${body}
                    <div class="item-picker-item" style="width: 40px; height: 40px; background: rgba(244, 67, 54, 0.2);"
                         onclick="${clickHandler}(null)" title="${i18n.t('tooltip.remove')}">
                        <i class="fas fa-times" style="color: var(--error);"></i>
                    </div>
                </div>
            </div>
        </div>
    `;
}

/** Sucht im Enum-Namen; Leerzeichen zaehlen wie Unterstriche ("gold helm" -> GOLDEN_HELMET). */
function matchesTerm(name, term) {
    if (!term) return true;
    return name.toLowerCase().includes(String(term).trim().toLowerCase().replace(/\s+/g, '_'));
}

/** Suche im Armor-Tab; entprellt, weil sie ueber den ganzen Katalog laeuft. */
const filterArmorItems = debounce(function (term) {
    const container = document.getElementById('armor-item-picker-inline');
    if (container) container.innerHTML = renderArmorPicker(term || '');
}, 150);

// Drag & Drop State - bereits oben definiert, hier nicht nochmal!
// let draggedItem = null;
// let dragSourceSlot = null;

function renderInventoryGrid(inventory) {
    // Minecraft inventory: 
    // Slots 0-8 = Hotbar (bottom row, highlighted)
    // Slots 9-35 = Main inventory (3 rows of 9)
    
    let html = '<div class="minecraft-inventory">';
    
    // Main Inventory (Slots 9-35, displayed first visually but higher slot numbers)
    html += '<div class="inventory-main">';
    for (let row = 0; row < 3; row++) {
        html += '<div class="inventory-row">';
        for (let col = 0; col < 9; col++) {
            const slotIndex = 9 + (row * 9) + col;
            html += renderInventorySlot(inventory, slotIndex, false);
        }
        html += '</div>';
    }
    html += '</div>';
    
    // Separator line
    html += '<div class="hotbar-separator"></div>';
    
    // Hotbar (Slots 0-8)
    html += '<div class="inventory-hotbar">';
    for (let i = 0; i < 9; i++) {
        html += renderInventorySlot(inventory, i, true);
    }
    html += '</div>';
    
    html += '</div>';
    
    // Add CSS for the inventory layout
    if (!document.getElementById('inventory-styles')) {
        const style = document.createElement('style');
        style.id = 'inventory-styles';
        style.textContent = `
            .minecraft-inventory {
                background: #2d2d2d;
                border: 2px solid #1a1a1a;
                border-radius: 8px;
                padding: 12px;
                width: fit-content;
            }
            .inventory-main {
                display: flex;
                flex-direction: column;
                gap: 2px;
            }
            .inventory-row {
                display: flex;
                gap: 2px;
            }
            .hotbar-separator {
                height: 2px;
                background: linear-gradient(90deg, transparent, var(--border), transparent);
                margin: 8px 0;
            }
            .inventory-hotbar {
                display: flex;
                gap: 2px;
            }
            .inventory-slot {
                width: 40px;
                height: 40px;
                background: #3a3a3a;
                border: 2px solid #1a1a1a;
                border-radius: 4px;
                display: flex;
                align-items: center;
                justify-content: center;
                position: relative;
                cursor: pointer;
                transition: all 0.15s ease;
            }
            .inventory-slot:hover {
                border-color: var(--primary);
                background: #454545;
            }
            .inventory-slot.hotbar-slot {
                background: linear-gradient(135deg, #3a3a3a 0%, #4a4a4a 100%);
                border-color: #555;
            }
            .inventory-slot.hotbar-slot::after {
                content: attr(data-slot);
                position: absolute;
                bottom: 1px;
                right: 3px;
                font-size: 8px;
                color: var(--text-muted);
                font-weight: bold;
            }
            .inventory-slot.filled {
                background: #404040;
            }
            .inventory-slot.enchanted {
                background: linear-gradient(135deg, #404040 0%, #4a3a5a 100%);
                border-color: #8b5cf6;
            }
            .inventory-slot.drag-over {
                border-color: var(--success) !important;
                background: rgba(76, 175, 80, 0.2) !important;
            }
            .inventory-slot.dragging {
                opacity: 0.5;
            }
            .inventory-slot img {
                width: 32px;
                height: 32px;
                image-rendering: pixelated;
                pointer-events: none;
                user-select: none;
                -webkit-user-drag: none;
            }
            .inventory-slot .amount {
                position: absolute;
                bottom: 2px;
                right: 4px;
                font-size: 11px;
                font-weight: bold;
                color: white;
                text-shadow: 1px 1px 1px black, -1px -1px 1px black, 1px -1px 1px black, -1px 1px 1px black;
                pointer-events: none;
                user-select: none;
            }
            .inventory-slot .enchant-indicator {
                position: absolute;
                top: 1px;
                left: 2px;
                font-size: 10px;
                pointer-events: none;
                user-select: none;
            }
            .item-source {
                cursor: grab;
            }
            .item-source:active {
                cursor: grabbing;
            }
            .item-source.dragging {
                opacity: 0.5;
            }
        `;
        document.head.appendChild(style);
    }
    
    return html;
}

function renderInventorySlot(inventory, slotIndex, isHotbar) {
    const item = inventory.find(inv => inv.slot === slotIndex);
    const hasItem = item && item.item;
    const hotbarClass = isHotbar ? 'hotbar-slot' : '';
    const hotbarNumber = isHotbar ? slotIndex + 1 : '';
    
    // Enchantment-Indikator
    const hasEnchants = item?.enchantments?.length > 0;
    const enchantClass = hasEnchants ? 'enchanted' : '';
    
    // Use event delegation - no inline handlers except for dblclick
    const tooltipEnchanted = i18n.t('tooltip.enchanted');
    const tooltipDoubleClick = i18n.t('tooltip.doubleClickToEdit');
    const tooltipEmpty = i18n.t('tooltip.emptySlot');
    const itemTitle = hasItem 
        ? item.item + (hasEnchants ? ` (${tooltipEnchanted}) - ${tooltipDoubleClick}` : ` - ${tooltipDoubleClick}`) 
        : tooltipEmpty;
    return `
        <div class="inventory-slot ${hasItem ? 'filled' : ''} ${hotbarClass} ${enchantClass}" 
             id="inv-slot-${slotIndex}"
             data-slot="${hotbarNumber}"
             data-slotindex="${slotIndex}"
             draggable="${hasItem ? 'true' : 'false'}"
             ondblclick="editInventorySlot(${slotIndex})"
             title="${itemTitle}">
            ${hasItem ? `
                ${itemIconHtml(item.item, 32)}
                ${item.amount > 1 ? `<span class="amount">${item.amount}</span>` : ''}
                ${hasEnchants ? `<span class="enchant-indicator">✨</span>` : ''}
            ` : ''}
        </div>
    `;
}

// Drag & Drop handlers for inventory
function onInventoryDragStart(event, slotIndex) {
    console.log('onInventoryDragStart called, slot:', slotIndex);
    const inventory = currentEditingEquipment?.inventory || [];
    const item = inventory.find(inv => inv.slot === slotIndex);
    console.log('Found item:', item);
    if (!item) {
        console.log('No item in slot, preventing drag');
        event.preventDefault();
        return;
    }
    
    draggedItem = { ...item };
    dragSourceSlot = slotIndex;
    event.target.classList.add('dragging');
    event.dataTransfer.effectAllowed = 'move';
    // Include fromInventory flag for proper handling on drop
    event.dataTransfer.setData('text/plain', JSON.stringify({ ...item, fromInventory: true }));
    console.log('Drag started successfully with fromInventory flag');
}

function onInventoryDragOver(event) {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
    event.target.closest('.inventory-slot')?.classList.add('drag-over');
}

function onInventoryDragLeave(event) {
    event.target.closest('.inventory-slot')?.classList.remove('drag-over');
}

function onInventoryDrop(event, targetSlot) {
    console.log('=== onInventoryDrop called ===');
    console.log('targetSlot:', targetSlot);
    console.log('draggedItem:', draggedItem);
    console.log('dragSourceSlot:', dragSourceSlot, '(type:', typeof dragSourceSlot, ')');
    event.preventDefault();
    event.target.closest('.inventory-slot')?.classList.remove('drag-over');
    
    // Get data from dataTransfer
    const data = event.dataTransfer.getData('text/plain');
    console.log('Drop data:', data);
    
    let itemData = null;
    try {
        itemData = JSON.parse(data);
        console.log('Parsed itemData:', itemData);
    } catch (e) {
        console.log('Could not parse drop data:', e);
    }
    
    // Case 1: Dropped from item picker
    if (itemData && itemData.fromPicker) {
        console.log('Case 1: Item from picker:', itemData.item);
        addItemToSlot(targetSlot, itemData.item, itemData.amount || 1);
        draggedItem = null;
        dragSourceSlot = null;
        return;
    }
    
    // Case 2: Dropped from inventory (internal move/swap)
    // Use typeof check to handle slot 0 correctly
    const hasValidSourceSlot = typeof dragSourceSlot === 'number';
    console.log('hasValidSourceSlot:', hasValidSourceSlot);
    
    if (itemData && itemData.fromInventory && hasValidSourceSlot) {
        console.log('Case 2: Item from inventory, source slot:', dragSourceSlot);
        
        // Don't do anything if dropping on same slot
        if (dragSourceSlot === targetSlot) {
            console.log('Same slot, ignoring');
            draggedItem = null;
            dragSourceSlot = null;
            return;
        }
        
        const inventory = currentEditingEquipment?.inventory || [];
        console.log('Current inventory length:', inventory.length);
        
        // Check if target slot has an item
        const targetItemIndex = inventory.findIndex(inv => inv.slot === targetSlot);
        const sourceItemIndex = inventory.findIndex(inv => inv.slot === dragSourceSlot);
        
        console.log('Source index:', sourceItemIndex, 'Target index:', targetItemIndex);
        
        if (targetItemIndex !== -1) {
            // SWAP: Target has item
            console.log('Swapping items');
            const targetItem = { ...inventory[targetItemIndex] };
            
            // Update target slot with dragged item
            inventory[targetItemIndex] = { 
                slot: targetSlot, 
                item: itemData.item, 
                amount: itemData.amount,
                enchantments: itemData.enchantments 
            };
            
            // Update source slot with target item
            if (sourceItemIndex !== -1) {
                inventory[sourceItemIndex] = { 
                    slot: dragSourceSlot, 
                    item: targetItem.item, 
                    amount: targetItem.amount,
                    enchantments: targetItem.enchantments 
                };
            } else {
                inventory.push({ 
                    slot: dragSourceSlot, 
                    item: targetItem.item, 
                    amount: targetItem.amount,
                    enchantments: targetItem.enchantments 
                });
            }
        } else {
            // MOVE: Target is empty
            console.log('Moving item to empty slot');
            
            // Remove from source
            if (sourceItemIndex !== -1) {
                inventory.splice(sourceItemIndex, 1);
            }
            
            // Add to target
            inventory.push({ 
                slot: targetSlot, 
                item: itemData.item, 
                amount: itemData.amount,
                enchantments: itemData.enchantments 
            });
        }
        
        currentEditingEquipment.inventory = inventory;
        refreshInventoryGrid();
        
        // No need to re-setup listeners - they use event delegation on the grid element
        
        // Clean up
        draggedItem = null;
        dragSourceSlot = null;
        console.log('=== Case 2 complete ===');
        return;
    }
    
    // Fallback for old draggedItem approach (when itemData parsing failed but we have draggedItem)
    const hasFallbackSourceSlot = typeof dragSourceSlot === 'number';
    if (draggedItem !== null && hasFallbackSourceSlot) {
        console.log('Case 3 (Fallback): Using draggedItem directly');
        if (dragSourceSlot === targetSlot) {
            draggedItem = null;
            dragSourceSlot = null;
            return;
        }
        
        const inventory = currentEditingEquipment?.inventory || [];
        const sourceIndex = inventory.findIndex(inv => inv.slot === dragSourceSlot);
        const targetIndex = inventory.findIndex(inv => inv.slot === targetSlot);
        
        if (targetIndex !== -1) {
            // Swap
            const targetItem = { ...inventory[targetIndex] };
            inventory[targetIndex] = { ...draggedItem, slot: targetSlot };
            if (sourceIndex !== -1) {
                inventory[sourceIndex] = { ...targetItem, slot: dragSourceSlot };
            } else {
                inventory.push({ ...targetItem, slot: dragSourceSlot });
            }
        } else {
            // Move
            if (sourceIndex !== -1) {
                inventory.splice(sourceIndex, 1);
            }
            inventory.push({ ...draggedItem, slot: targetSlot });
        }
        
        currentEditingEquipment.inventory = inventory;
        refreshInventoryGrid();
        // No need to re-setup listeners - they use event delegation
        
        draggedItem = null;
        dragSourceSlot = null;
        console.log('=== Case 3 complete ===');
        return;
    }
    
    // Fallback: Try plain text as item name
    if (data && !data.startsWith('{')) {
        console.log('Case 4: Plain text item name:', data);
        addItemToSlot(targetSlot, data, 1);
    } else {
        console.log('No valid drop data found');
    }
    
    draggedItem = null;
    dragSourceSlot = null;
    console.log('=== onInventoryDrop end ===');
}

// Setup drag & drop listeners programmatically (more reliable than inline handlers)
// Track if listeners are already attached to avoid duplicates
var inventoryListenersAttached = false;

function setupInventoryDragDropListeners() {
    console.log('setupInventoryDragDropListeners called, already attached:', inventoryListenersAttached);
    
    // Setup inventory grid drop zone
    const grid = document.getElementById('equipment-inventory-grid');
    if (grid && !inventoryListenersAttached) {
        console.log('Grid element found:', grid);
        console.log('Grid children:', grid.children.length);
        
        // Handler for dragover - determines where items can be dropped
        // Handler for dragover - determines where items can be dropped
        function handleGridDragOver(e) {
            e.preventDefault();
            e.stopPropagation();
            // Allow both copy (from picker) and move (within inventory)
            // Use typeof check to properly handle slot 0
            const isFromInventory = typeof dragSourceSlot === 'number';
            e.dataTransfer.dropEffect = isFromInventory ? 'move' : 'copy';
            
            // Find the slot and highlight it
            let slot = e.target.closest('.inventory-slot');
            if (!slot) {
                // Try elementsFromPoint as fallback
                const elements = document.elementsFromPoint(e.clientX, e.clientY);
                for (const el of elements) {
                    if (el.classList.contains('inventory-slot')) {
                        slot = el;
                        break;
                    }
                }
            }
            
            // Remove drag-over from all other slots first
            grid.querySelectorAll('.inventory-slot.drag-over').forEach(s => {
                if (s !== slot) s.classList.remove('drag-over');
            });
            
            if (slot) {
                slot.classList.add('drag-over');
            }
        }
        
        // Handler for dragleave
        function handleGridDragLeave(e) {
            const slot = e.target.closest('.inventory-slot');
            if (slot) slot.classList.remove('drag-over');
        }
        
        // Handler for drop
        function handleGridDrop(e) {
            e.preventDefault();
            e.stopPropagation();
            console.log('=== DROP EVENT FIRED on grid ===');
            console.log('Drop target element:', e.target);
            console.log('Drop target tagName:', e.target.tagName);
            console.log('Drop target classList:', e.target.classList?.toString());
            
            // Remove all drag-over classes
            grid.querySelectorAll('.inventory-slot.drag-over').forEach(s => s.classList.remove('drag-over'));
            
            // Find the inventory slot - check multiple levels up
            let slot = e.target.closest('.inventory-slot');
            
            // If we didn't find a slot and the target is the grid or a container, 
            // try to find it from the mouse position
            if (!slot) {
                console.log('No slot found via closest(), checking elementsFromPoint');
                const elements = document.elementsFromPoint(e.clientX, e.clientY);
                for (const el of elements) {
                    if (el.classList.contains('inventory-slot')) {
                        slot = el;
                        console.log('Found slot via elementsFromPoint:', slot);
                        break;
                    }
                }
            }
            
            console.log('Final slot:', slot);
            
            if (!slot) {
                console.log('ERROR: No slot found for drop');
                // Clean up drag state
                draggedItem = null;
                dragSourceSlot = null;
                return;
            }
            
            slot.classList.remove('drag-over');
            const targetSlot = parseInt(slot.dataset.slotindex);
            console.log('Target slot index:', targetSlot);
            
            if (isNaN(targetSlot)) {
                console.log('ERROR: Invalid slot index');
                draggedItem = null;
                dragSourceSlot = null;
                return;
            }
            
            console.log('Calling onInventoryDrop with slot:', targetSlot);
            onInventoryDrop(e, targetSlot);
        }
        
        // Handler for dragstart
        function handleGridDragStart(e) {
            console.log('=== GRID DRAGSTART EVENT ===');
            console.log('Target element:', e.target);
            console.log('Target tagName:', e.target.tagName);
            console.log('Target classList:', e.target.classList.toString());
            
            // Find the slot - could be the slot itself or a child element
            let slot = e.target.closest('.inventory-slot');
            console.log('Closest slot:', slot);
            
            if (!slot && e.target.classList.contains('inventory-slot')) {
                slot = e.target;
            }
            
            if (!slot) {
                console.log('ERROR: No slot found for dragstart');
                return;
            }
            
            const slotIndex = parseInt(slot.dataset.slotindex);
            console.log('Slot index from dataset:', slotIndex);
            
            if (isNaN(slotIndex)) {
                console.log('ERROR: Invalid slot index');
                return;
            }
            
            const inventory = currentEditingEquipment?.inventory || [];
            console.log('Current inventory:', inventory);
            
            const item = inventory.find(inv => inv.slot === slotIndex);
            console.log('Found item in slot:', item);
            
            if (!item) {
                console.log('ERROR: No item in slot, canceling drag');
                e.preventDefault();
                return;
            }
            
            console.log('SUCCESS: Starting drag of item:', item.item);
            draggedItem = { ...item };
            dragSourceSlot = slotIndex;
            slot.classList.add('dragging');
            // Use copyMove to allow both operations
            e.dataTransfer.effectAllowed = 'copyMove';
            const dataToSet = JSON.stringify({ ...item, fromInventory: true });
            console.log('Setting dataTransfer data:', dataToSet);
            e.dataTransfer.setData('text/plain', dataToSet);
            console.log('=== DRAGSTART COMPLETE ===');
        }
        
        // Handler for dragend
        function handleGridDragEnd(e) {
            const slot = e.target.closest('.inventory-slot');
            if (slot) slot.classList.remove('dragging');
            // Clean up all drag-over states
            grid.querySelectorAll('.inventory-slot.drag-over').forEach(s => s.classList.remove('drag-over'));
            grid.querySelectorAll('.inventory-slot.dragging').forEach(s => s.classList.remove('dragging'));
        }
        
        // Add event listeners to the grid for event delegation
        grid.addEventListener('dragover', handleGridDragOver);
        grid.addEventListener('dragleave', handleGridDragLeave);
        grid.addEventListener('drop', handleGridDrop);
        grid.addEventListener('dragstart', handleGridDragStart);
        grid.addEventListener('dragend', handleGridDragEnd);
        
        console.log('Inventory grid listeners attached');
    }
    
    // Setup item picker items
    const itemCategories = document.getElementById('equipment-item-categories');
    if (itemCategories && !inventoryListenersAttached) {
        itemCategories.addEventListener('dragstart', (e) => {
            const pickerItem = e.target.closest('.item-picker-item');
            if (!pickerItem) return;
            
            const itemName = pickerItem.dataset.item;
            if (!itemName) return;
            
            console.log('Item picker dragstart:', itemName);
            
            // Reset inventory drag state when dragging from picker
            draggedItem = null;
            dragSourceSlot = null;
            
            pickerItem.classList.add('dragging');
            e.dataTransfer.effectAllowed = 'copy';
            e.dataTransfer.setData('text/plain', JSON.stringify({
                fromPicker: true,
                item: itemName,
                amount: 1
            }));
        });
        
        itemCategories.addEventListener('dragend', (e) => {
            const pickerItem = e.target.closest('.item-picker-item');
            if (pickerItem) pickerItem.classList.remove('dragging');
        });
        
        console.log('Item picker listeners attached');
    }
    
    // Mark listeners as attached
    inventoryListenersAttached = true;
}

// Reset the listener tracking when modal is closed
function resetInventoryListeners() {
    inventoryListenersAttached = false;
    draggedItem = null;
    dragSourceSlot = null;
}

function addItemToSlot(slotIndex, itemName, amount) {
    if (!currentEditingEquipment.inventory) {
        currentEditingEquipment.inventory = [];
    }
    
    // Limit amount based on stackability
    const maxStack = getMaxStackSize(itemName);
    const finalAmount = Math.min(amount, maxStack);
    
    const existingIndex = currentEditingEquipment.inventory.findIndex(inv => inv.slot === slotIndex);
    if (existingIndex !== -1) {
        currentEditingEquipment.inventory[existingIndex] = {
            slot: slotIndex,
            item: itemName,
            amount: finalAmount
        };
    } else {
        currentEditingEquipment.inventory.push({
            slot: slotIndex,
            item: itemName,
            amount: finalAmount
        });
    }
    
    refreshInventoryGrid();
}

function refreshInventoryGrid() {
    const grid = document.getElementById('equipment-inventory-grid');
    if (grid) {
        grid.innerHTML = renderInventoryGrid(currentEditingEquipment?.inventory || []);
    }
}

// Update grid content only (preserves event listeners attached to grid container)
function refreshInventoryGridContent() {
    const grid = document.getElementById('equipment-inventory-grid');
    if (grid) {
        // Re-render the slots inside the minecraft-inventory div
        const minecraftInv = grid.querySelector('.minecraft-inventory');
        if (minecraftInv) {
            // Update each slot individually
            const inventory = currentEditingEquipment?.inventory || [];
            for (let i = 0; i < 36; i++) {
                const slotEl = document.getElementById(`inv-slot-${i}`);
                if (slotEl) {
                    const item = inventory.find(inv => inv.slot === i);
                    updateSlotContent(slotEl, item, i);
                }
            }
        } else {
            // Fallback: full re-render
            grid.innerHTML = renderInventoryGrid(currentEditingEquipment?.inventory || []);
        }
    }
}

// Update a single slot's content
function updateSlotContent(slotEl, item, slotIndex) {
    const hasItem = item && item.item;
    const hasEnchants = item?.enchantments?.length > 0;
    
    // Update classes
    slotEl.classList.toggle('filled', hasItem);
    slotEl.classList.toggle('enchanted', hasEnchants);
    slotEl.draggable = hasItem;
    
    // Update title
    const tooltipEnchanted = i18n.t('tooltip.enchanted');
    const tooltipDoubleClick = i18n.t('tooltip.doubleClickToEdit');
    const tooltipEmpty = i18n.t('tooltip.emptySlot');
    slotEl.title = hasItem 
        ? item.item + (hasEnchants ? ` (${tooltipEnchanted}) - ${tooltipDoubleClick}` : ` - ${tooltipDoubleClick}`) 
        : tooltipEmpty;
    
    // Update content
    if (hasItem) {
        slotEl.innerHTML = `
            ${itemIconHtml(item.item, 32)}
            ${item.amount > 1 ? `<span class="amount">${item.amount}</span>` : ''}
            ${hasEnchants ? `<span class="enchant-indicator">✨</span>` : ''}
        `;
    } else {
        slotEl.innerHTML = '';
    }
}

// Drag start handler for item picker (source items)
function onItemPickerDragStart(event, itemName) {
    console.log('onItemPickerDragStart called, item:', itemName);
    draggedItem = null; // Not from inventory
    dragSourceSlot = null;
    
    event.target.classList.add('dragging');
    event.dataTransfer.effectAllowed = 'copy';
    event.dataTransfer.setData('text/plain', JSON.stringify({
        fromPicker: true,
        item: itemName,
        amount: 1
    }));
    console.log('Item picker drag started');
    
    // Remove dragging class after drag ends
    event.target.addEventListener('dragend', () => {
        event.target.classList.remove('dragging');
    }, { once: true });
}

/** Hoechstzahl gleichzeitig gezeichneter Items je Abschnitt. */
const PICKER_SECTION_LIMIT = 150;

/**
 * Item-Auswahl im Inventar-Tab, gruppiert nach den Kategorien des Servers.
 *
 * Ohne Suchbegriff bleiben alle Abschnitte zugeklappt und je Kategorie wird nur der Anfang
 * gezeichnet - der Katalog umfasst rund 1600 Items, alles auf einmal ins DOM zu legen wuerde
 * den Editor beim Oeffnen sichtbar haengen lassen. Mit Suchbegriff bleiben nur die
 * treffenden Kategorien uebrig, und die sind aufgeklappt.
 *
 * @param {string} [term] Suchbegriff; leer = Ausgangszustand
 */
function renderItemCategories(term) {
    const search = String(term || '').trim();

    if (!ITEM_CATALOG.ready) {
        return `<div style="padding:1rem;color:var(--text-muted);">
            <i class="fas fa-circle-notch fa-spin"></i> ${i18n.t('picker.loading')}
        </div>`;
    }

    let html = '';
    let totalMatches = 0;

    for (const category of ITEM_CATALOG.categories) {
        // Ruestung hat einen eigenen Tab mit Slot-Zuordnung - hier waere sie doppelt.
        if (category === 'armor') continue;

        const all = (ITEM_CATALOG.byCategory.get(category) || [])
            .filter(entry => matchesTerm(entry.name, search));
        if (all.length === 0) continue;
        totalMatches += all.length;

        const shown = all.slice(0, PICKER_SECTION_LIMIT);
        const hidden = all.length - shown.length;

        html += `
            <div class="collapsible${search ? ' open' : ''}" style="margin-bottom: 0.5rem;">
                <div class="collapsible-header" onclick="toggleCollapsible(this)">
                    <div class="collapsible-title">
                        <span>${i18n.t('picker.category.' + category)}</span>
                        <span class="nav-badge">${all.length}</span>
                    </div>
                    <i class="fas fa-chevron-down collapsible-icon"></i>
                </div>
                <div class="collapsible-content">
                    <div style="display: flex; flex-wrap: wrap; gap: 0.5rem;">
                        ${shown.map(entry => `
                            <div class="item-picker-item item-source"
                                 style="width: 36px; height: 36px;"
                                 draggable="true"
                                 data-item="${escapeAttr(entry.name)}"
                                 onclick="addItemToInventory('${escapeAttr(entry.name)}')"
                                 title="${escapeHtml(itemDisplayName(entry.name))} (${escapeHtml(entry.name)})">
                                ${itemIconHtml(entry.name, 32, { lazy: true })}
                            </div>
                        `).join('')}
                    </div>
                    ${hidden > 0 ? `<div style="margin-top:0.5rem;color:var(--text-muted);font-size:0.8rem;">
                        ${i18n.t('picker.refineSearch', { hidden: hidden })}
                    </div>` : ''}
                </div>
            </div>
        `;
    }

    if (totalMatches === 0) {
        return `<div style="padding:2rem;text-align:center;color:var(--text-muted);">
            <i class="fas fa-search"></i> ${i18n.t('picker.noResults')}
        </div>`;
    }
    return html;
}

/**
 * Prueft, ob ein Item in einen Ruestungs- oder Nebenhandslot darf.
 *
 * Die Slot-Zuordnung kommt aus itemArmorSlot() (items.js) und damit vom Server, nicht aus
 * einem Namensvergleich. Die frueher hier stehende Pruefung liess jedes Item durch, dessen
 * Slot sie nicht erkannte ("null = universell") - praktisch also jedes Nicht-Ruestungsteil
 * in jeden Slot. Ein Schwert im Stiefel-Feld wurde gespeichert und vom Server beim Anziehen
 * stillschweigend verworfen.
 *
 * Die Nebenhand bleibt bewusst offen: Minecraft erlaubt dort tatsaechlich jedes Item.
 */
function armorSlotAccepts(material, slotType) {
    if (!material) return true;               // Leeren ist immer erlaubt
    if (slotType === 'offhand') return true;  // Nebenhand nimmt jedes Item
    return itemArmorSlot(material) === slotType;
}

/** Uebersetzter Name eines Slots, fuer Meldungen. */
function armorSlotLabel(slotType) {
    const labels = {
        helmet: i18n.t('editor.helmet'),
        chestplate: i18n.t('editor.chestplate'),
        leggings: i18n.t('editor.leggings'),
        boots: i18n.t('editor.boots'),
        offhand: i18n.t('editor.offhand')
    };
    return labels[slotType] || slotType;
}

function selectArmorSlot(slotType) {
    // Deselect previous
    document.querySelectorAll('.armor-slot').forEach(s => s.style.boxShadow = '');
    
    selectedArmorSlot = slotType;
    const slot = document.getElementById(`armor-slot-${slotType}`);
    if (slot) {
        slot.style.boxShadow = '0 0 0 3px var(--primary)';
    }
}

function setArmorItem(itemName) {
    // Ist das Item ein Ruestungsteil, springt die Auswahl auf den passenden Slot - wer ein
    // Diamanthelm anklickt, meint den Helm, egal was gerade markiert war.
    const autoSlot = itemArmorSlot(itemName);
    if (autoSlot && autoSlot !== selectedArmorSlot) {
        selectedArmorSlot = autoSlot;
        selectArmorSlot(autoSlot);
    }

    if (!selectedArmorSlot) {
        showToast(i18n.t('toast.selectArmorSlotFirst'), 'warning');
        return;
    }

    if (!armorSlotAccepts(itemName, selectedArmorSlot)) {
        // Sagen, wohin das Item stattdessen gehoert - "passt nicht" allein hilft niemandem.
        const belongsTo = itemArmorSlot(itemName);
        showToast(belongsTo
            ? i18n.t('toast.itemBelongsToSlot', {
                item: itemDisplayName(itemName),
                slot: armorSlotLabel(selectedArmorSlot),
                correct: armorSlotLabel(belongsTo)
            })
            : i18n.t('toast.itemNotArmor', {
                item: itemDisplayName(itemName),
                slot: armorSlotLabel(selectedArmorSlot)
            }), 'error');
        return;
    }

    if (selectedArmorSlot === 'offhand') {
        currentEditingEquipment.offhand = itemName;
    } else {
        currentEditingEquipment.armor = currentEditingEquipment.armor || {};
        currentEditingEquipment.armor[selectedArmorSlot] = itemName;
    }
    
    // Update UI
    const slot = document.getElementById(`armor-slot-${selectedArmorSlot}`);
    if (slot) {
        if (itemName) {
            slot.classList.add('filled');
            slot.innerHTML = `
                ${itemIconHtml(itemName, 40)}
            `;
        } else {
            slot.classList.remove('filled');
            slot.innerHTML = `<i class="fas fa-${getSlotIcon(selectedArmorSlot)}" style="color: var(--text-muted);"></i>`;
        }
    }
    
    // Die Beschriftungen standen hier fest auf Deutsch - in jeder anderen Panelsprache
    // erschien der Slotname trotzdem deutsch.
    showToast(itemName
        ? i18n.t('toast.armorSlotSet', {
            slot: armorSlotLabel(selectedArmorSlot),
            item: itemDisplayName(itemName)
        })
        : i18n.t('toast.armorSlotCleared', { slot: armorSlotLabel(selectedArmorSlot) }), 'success');
}

// getArmorSlotForItem und setArmorItemAuto standen hier: ein Alias auf die geloeschte
// Namensheuristik und ein Auto-Setzer, der beide nur weiterreichte. Keine von beiden
// hatte je einen Aufrufer; setArmorItem() macht die Slot-Erkennung inzwischen selbst.

// Click handler for armor slots - opens edit modal if filled
function clickArmorSlot(slotType) {
    selectArmorSlot(slotType);
    
    // Check if slot has item
    let itemName = null;
    if (slotType === 'offhand') {
        itemName = currentEditingEquipment?.offhand;
    } else {
        itemName = currentEditingEquipment?.armor?.[slotType];
    }
    
    if (itemName) {
        editArmorSlot(slotType);
    }
}

// Edit armor slot - opens modal for enchantments
function editArmorSlot(slotType) {
    const holder = armorFieldHolder(slotType);
    const itemName = holder[slotType];
    if (!itemName) return;

    // Zusatzfelder liegen als Geschwister mit Slot-Praefix, siehe armorFieldHolder().
    // Der alte, nie ausgewertete armorData/offhandData-Zweig wird noch gelesen, damit
    // vor 1.0.9 im Panel gemachte Eingaben beim naechsten Speichern uebernommen werden.
    const legacy = (slotType === 'offhand'
        ? currentEditingEquipment.offhandData
        : currentEditingEquipment.armorData && currentEditingEquipment.armorData[slotType]) || {};

    const currentName = holder[slotType + '-name'] || legacy.name || '';
    const currentEnchants = holder[slotType + '-enchantments'] || legacy.enchantments || [];
    const isUnbreakable = holder[slotType + '-unbreakable'] === true;
    const maxDurability = itemMaxDurability(itemName);

    const availableEnchants = getAvailableEnchantments(itemName);

    const slotLabels = {
        helmet: i18n.t('equipment.helmet'),
        chestplate: i18n.t('equipment.chestplate'),
        leggings: i18n.t('equipment.leggings'),
        boots: i18n.t('equipment.boots'),
        offhand: i18n.t('equipment.offhand')
    };
    
    const modal = document.createElement('div');
    modal.className = 'modal-overlay active';
    modal.id = 'armor-edit-modal';
    modal.style.zIndex = '2000';
    
    modal.innerHTML = `
        <div class="modal" style="max-width: 500px;">
            <div class="modal-header">
                <h3 class="modal-title">
                    ${itemIconHtml(itemName, 24, { css: 'vertical-align:middle;margin-right:8px;' })}
                    ${slotLabels[slotType]}: ${itemName}
                </h3>
                <button class="modal-close" onclick="closeArmorEditModal()">
                    <i class="fas fa-times"></i>
                </button>
            </div>
            <div class="modal-body">
                <!-- Custom Name -->
                <div class="form-group" style="margin-bottom: 1rem;">
                    <label>${i18n.t('label.customName')}</label>
                    <input type="text" id="armor-custom-name" value="${escapeHtml(currentName)}"
                           placeholder="${i18n.t('label.customNamePlaceholder')}" class="form-control">
                    <small style="color: var(--text-muted);">${i18n.t('item.help.colorCodes')}</small>
                </div>

                ${maxDurability > 0 ? `
                <div class="toggle-wrapper" style="margin-bottom: 1rem;">
                    <div class="toggle-label">
                        <span>${i18n.t('item.label.unbreakable')}</span>
                        <span>${i18n.t('item.help.unbreakable')}</span>
                    </div>
                    <label class="toggle">
                        <input type="checkbox" id="armor-unbreakable" ${isUnbreakable ? 'checked' : ''}>
                        <span class="toggle-slider"></span>
                    </label>
                </div>
                ` : ''}

                <!-- Verzauberungen -->
                ${availableEnchants.length > 0 ? `
                    <div class="form-group">
                        <label>${i18n.t('label.enchantments')}</label>
                        <div id="armor-enchantments-container" style="max-height: 250px; overflow-y: auto; background: var(--background); border-radius: 8px; padding: 0.5rem;">
                            ${renderEnchantmentsList(availableEnchants, currentEnchants)}
                        </div>
                    </div>
                ` : `
                    <div style="color: var(--text-muted); font-style: italic; padding: 1rem; text-align: center;">
                        <i class="fas fa-info-circle"></i> ${i18n.t('label.noEnchantments')}
                    </div>
                `}
            </div>
            <div class="modal-footer">
                <button class="btn btn-danger" onclick="removeArmorItem('${slotType}')">
                    <i class="fas fa-trash"></i> ${i18n.t('button.remove')}
                </button>
                <button class="btn btn-secondary" onclick="closeArmorEditModal()">${i18n.t('button.cancel')}</button>
                <button class="btn btn-primary" onclick="saveArmorEdit('${slotType}')">
                    <i class="fas fa-check"></i> ${i18n.t('button.save')}
                </button>
            </div>
        </div>
    `;
    
    document.body.appendChild(modal);
}

/**
 * Traeger der Zusatzfelder eines Ruestungs-/Nebenhandslots.
 *
 * Die Ruestung steht in der YAML als blosser Materialname; Name, Lore und Verzauberungen
 * liegen als Geschwisterfelder mit Slot-Praefix daneben ({@code helmet-enchantments},
 * {@code helmet-name}, ...). Der Offhand-Slot haengt eine Ebene hoeher, direkt am Set.
 *
 * Bis 1.0.9 schrieb das Panel diese Angaben stattdessen in eigene Objekte `armorData`
 * und `offhandData`, die kein Loader je gelesen hat - Ruestungsverzauberungen aus dem
 * Panel kamen im Spiel schlicht nicht an.
 */
function armorFieldHolder(slotType) {
    if (slotType === 'offhand') {
        return currentEditingEquipment;
    }
    currentEditingEquipment.armor = currentEditingEquipment.armor || {};
    return currentEditingEquipment.armor;
}

function saveArmorEdit(slotType) {
    const holder = armorFieldHolder(slotType);

    const nameInput = document.getElementById('armor-custom-name');
    const customName = nameInput && nameInput.value.trim();
    if (customName) {
        holder[slotType + '-name'] = customName;
    } else {
        delete holder[slotType + '-name'];
    }

    const enchantments = [];
    document.querySelectorAll('#armor-edit-modal .enchant-select').forEach(select => {
        const level = parseInt(select.value, 10);
        if (level > 0) {
            enchantments.push(`${select.dataset.enchant}:${level}`);
        }
    });
    if (enchantments.length > 0) {
        holder[slotType + '-enchantments'] = enchantments;
    } else {
        delete holder[slotType + '-enchantments'];
    }

    const unbreakable = document.getElementById('armor-unbreakable');
    if (unbreakable && unbreakable.checked) {
        holder[slotType + '-unbreakable'] = true;
    } else {
        delete holder[slotType + '-unbreakable'];
    }

    closeArmorEditModal();
    showToast(i18n.t('label.armorUpdated'), 'success');
}

function removeArmorItem(slotType) {
    const holder = armorFieldHolder(slotType);
    // Material und alle Zusatzfelder desselben Slots entfernen.
    ['', '-name', '-lore', '-enchantments', '-unbreakable'].forEach(suffix => {
        delete holder[slotType + suffix];
    });
    // Aufraeumen: die alten, nie gelesenen Hilfsobjekte aus Konfigurationen vor 1.0.9.
    if (currentEditingEquipment.armorData) delete currentEditingEquipment.armorData[slotType];
    if (slotType === 'offhand') delete currentEditingEquipment.offhandData;

    // Update UI
    const slot = document.getElementById(`armor-slot-${slotType}`);
    if (slot) {
        slot.classList.remove('filled');
        slot.innerHTML = `<i class="fas fa-${getSlotIcon(slotType)}" style="color: var(--text-muted);"></i>`;
    }
    
    closeArmorEditModal();
    showToast(i18n.t('label.armorRemoved'), 'success');
}

function closeArmorEditModal() {
    document.getElementById('armor-edit-modal')?.remove();
}

function addItemToInventory(itemName) {
    currentEditingEquipment.inventory = currentEditingEquipment.inventory || [];
    
    // Check max stack size for this item
    const maxStack = getMaxStackSize(itemName);
    
    // Find first empty slot or add to existing item (only if stackable)
    const existingItem = maxStack > 1 
        ? currentEditingEquipment.inventory.find(i => i.item === itemName && i.amount < maxStack)
        : null;
        
    if (existingItem) {
        existingItem.amount = Math.min(maxStack, (existingItem.amount || 1) + 1);
        updateInventorySlotUI(existingItem.slot);
    } else {
        // Find first empty slot
        const usedSlots = currentEditingEquipment.inventory.map(i => i.slot);
        let emptySlot = -1;
        for (let i = 0; i < 36; i++) {
            if (!usedSlots.includes(i)) {
                emptySlot = i;
                break;
            }
        }
        
        if (emptySlot === -1) {
            showToast(i18n.t('label.emptyInventory'), 'error');
            return;
        }
        
        currentEditingEquipment.inventory.push({
            slot: emptySlot,
            item: itemName,
            amount: 1
        });
        updateInventorySlotUI(emptySlot);
    }
    
    showToast(i18n.t('label.itemAdded', { item: itemName }), 'success');
}

function updateInventorySlotUI(slotIndex) {
    const item = currentEditingEquipment.inventory.find(i => i.slot === slotIndex);
    const slotEl = document.getElementById(`inv-slot-${slotIndex}`);
    
    if (slotEl && item) {
        slotEl.classList.add('filled');
        // IMPORTANT: Set draggable to true so the item can be dragged
        slotEl.setAttribute('draggable', 'true');
        slotEl.title = item.item + ' - ' + i18n.t('tooltip.doubleClickToEdit');
        
        // Check for enchantments
        const hasEnchants = item.enchantments?.length > 0;
        if (hasEnchants) {
            slotEl.classList.add('enchanted');
        } else {
            slotEl.classList.remove('enchanted');
        }
        
        slotEl.innerHTML = `
            ${itemIconHtml(item.item, 32)}
            ${item.amount > 1 ? `<span class="amount">${item.amount}</span>` : ''}
            ${hasEnchants ? `<span class="enchant-indicator">✨</span>` : ''}
        `;
    } else if (slotEl) {
        slotEl.classList.remove('filled');
        slotEl.classList.remove('enchanted');
        // Set draggable to false for empty slots
        slotEl.setAttribute('draggable', 'false');
        slotEl.title = 'Leerer Slot';
        slotEl.innerHTML = '';
    }
}

function editInventorySlot(slotIndex) {
    console.log('editInventorySlot called:', slotIndex);
    const item = currentEditingEquipment.inventory?.find(i => i.slot === slotIndex);
    console.log('Found item:', item);
    
    if (!item) return;
    
    // Öffne Item-Edit-Modal
    openItemEditModal(item, slotIndex);
}

/**
 * Item-Eigenschaften bearbeiten.
 *
 * Das Modal kann seit 1.0.9 deutlich mehr als Anzahl, Name und Verzauberungen. Damit es
 * dadurch nicht unuebersichtlich wird, ist es in Abschnitte geteilt: die drei gewohnten
 * Felder stehen offen ganz oben, alles Weitere liegt in zugeklappten Abschnitten mit je
 * einem erklaerenden Satz. Abschnitte, die zum Item nicht passen (Haltbarkeit bei einem
 * Apfel, Trankeffekte bei einem Schwert), werden gar nicht erst gezeichnet - die
 * Entscheidung darueber trifft der Server-Katalog, nicht eine Namensvermutung.
 *
 * @param {object} item      Eintrag aus currentEditingEquipment.inventory
 * @param {number} slotIndex Slot, in dem das Item liegt
 */
function openItemEditModal(item, slotIndex) {
    const material = item.item;
    const availableEnchants = getAvailableEnchantments(material);
    const currentEnchants = item.enchantments || [];
    const maxStack = getMaxStackSize(material);
    const isStackable = maxStack > 1;
    const maxDurability = itemMaxDurability(material);
    const isPotion = itemIsPotion(material);
    const amount = Math.min(item.amount || 1, maxStack);

    const modal = document.createElement('div');
    modal.className = 'modal-overlay active';
    modal.id = 'item-edit-modal';
    modal.style.zIndex = '2000';

    modal.innerHTML = `
        <div class="modal" style="max-width: 620px;">
            <div class="modal-header">
                <h3 class="modal-title">
                    ${itemIconHtml(material, 24, { css: 'vertical-align:middle;margin-right:8px;' })}
                    ${escapeHtml(itemDisplayName(material))}
                </h3>
                <button class="modal-close" onclick="closeItemEditModal()">
                    <i class="fas fa-times"></i>
                </button>
            </div>
            <div class="modal-body" style="max-height: calc(90vh - 160px); overflow-y: auto;">
                <div style="color:var(--text-muted);font-size:0.8rem;margin-bottom:1rem;">
                    <code>${escapeHtml(material)}</code>
                </div>

                <!-- Grundlagen: immer offen, das ist der haeufigste Handgriff -->
                <div class="form-group" style="margin-bottom: 1rem;">
                    <label>${isStackable ? i18n.t('label.amountMax', { max: maxStack }) : i18n.t('label.amount')}</label>
                    ${isStackable ? `
                        <div style="display: flex; align-items: center; gap: 0.5rem;">
                            <input type="range" id="item-amount" min="1" max="${maxStack}" value="${amount}"
                                   style="flex: 1;" oninput="document.getElementById('item-amount-display').textContent = this.value">
                            <span id="item-amount-display" style="min-width: 30px; text-align: center;">${amount}</span>
                        </div>
                    ` : `
                        <div style="color: var(--text-muted); font-style: italic;">
                            <i class="fas fa-info-circle"></i> ${i18n.t('label.notStackable')}
                        </div>
                        <input type="hidden" id="item-amount" value="1">
                    `}
                </div>

                <div class="form-group" style="margin-bottom: 1rem;">
                    <label>${i18n.t('label.customName')}</label>
                    <input type="text" id="item-custom-name" value="${escapeHtml(item.name || '')}"
                           placeholder="${i18n.t('label.customNamePlaceholder')}" class="form-control"
                           oninput="updateItemTextPreview()">
                    <small style="color: var(--text-muted);">${i18n.t('item.help.colorCodes')}</small>
                </div>

                ${availableEnchants.length > 0 ? `
                    <div class="form-group" style="margin-bottom: 1rem;">
                        <label>${i18n.t('label.enchantments')}</label>
                        <small style="display:block;color: var(--text-muted);margin-bottom:0.5rem;">${i18n.t('item.help.enchantments')}</small>
                        <div id="enchantments-container" style="max-height: 250px; overflow-y: auto; background: var(--background); border-radius: 8px; padding: 0.5rem;">
                            ${renderEnchantmentsList(availableEnchants, currentEnchants)}
                        </div>
                    </div>
                ` : `
                    <div style="color: var(--text-muted); font-style: italic; padding: 0.75rem; text-align: center;">
                        <i class="fas fa-info-circle"></i> ${i18n.t('label.noEnchantments')}
                    </div>
                `}

                ${itemSection('lore', 'fa-align-left', i18n.t('item.section.lore'), i18n.t('item.help.lore'), `
                    <textarea id="item-lore" class="form-control" rows="4"
                              placeholder="${i18n.t('item.placeholder.lore')}"
                              oninput="updateItemTextPreview()">${escapeHtml((item.lore || []).join('\n'))}</textarea>
                    <div style="margin-top:0.75rem;">
                        <small style="color:var(--text-muted);">${i18n.t('item.label.preview')}</small>
                        <div id="item-text-preview" class="mc-preview"></div>
                    </div>
                `)}

                ${maxDurability > 0 ? itemSection('durability', 'fa-hammer', i18n.t('item.section.durability'), i18n.t('item.help.durability'), `
                    <div class="toggle-wrapper">
                        <div class="toggle-label">
                            <span>${i18n.t('item.label.unbreakable')}</span>
                            <span>${i18n.t('item.help.unbreakable')}</span>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" id="item-unbreakable" ${item.unbreakable ? 'checked' : ''}>
                            <span class="toggle-slider"></span>
                        </label>
                    </div>
                    <div class="form-group" style="margin-top:1rem;">
                        <label>${i18n.t('item.label.damage', { max: maxDurability })}</label>
                        <div style="display:flex;align-items:center;gap:0.5rem;">
                            <input type="range" id="item-damage" min="0" max="${maxDurability}" value="${item.damage || 0}"
                                   style="flex:1;" oninput="document.getElementById('item-damage-display').textContent = this.value">
                            <span id="item-damage-display" style="min-width:40px;text-align:center;">${item.damage || 0}</span>
                        </div>
                        <small style="color:var(--text-muted);">${i18n.t('item.help.damage')}</small>
                    </div>
                `) : ''}

                ${itemSection('display', 'fa-eye', i18n.t('item.section.display'), i18n.t('item.help.display'), `
                    <div class="form-group">
                        <label>${i18n.t('item.label.customModelData')}</label>
                        <input type="number" id="item-custom-model-data" class="form-control" min="0" step="1"
                               value="${item['custom-model-data'] || ''}" placeholder="0">
                        <small style="color:var(--text-muted);">${i18n.t('item.help.customModelData')}</small>
                    </div>
                    <div class="form-group" style="margin-top:1rem;">
                        <label>${i18n.t('item.label.itemFlags')}</label>
                        <small style="display:block;color:var(--text-muted);margin-bottom:0.5rem;">${i18n.t('item.help.itemFlags')}</small>
                        <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:0.35rem;">
                            ${renderItemFlagChoices(item['item-flags'] || [])}
                        </div>
                    </div>
                `)}

                ${isPotion ? itemSection('potion', 'fa-flask', i18n.t('item.section.potion'), i18n.t('item.help.potion'),
                    renderPotionEditor(item.potion || {})) : ''}
            </div>
            <div class="modal-footer">
                <button class="btn btn-danger" onclick="removeInventoryItem(${slotIndex})">
                    <i class="fas fa-trash"></i> ${i18n.t('button.remove')}
                </button>
                <button class="btn btn-secondary" onclick="closeItemEditModal()">${i18n.t('button.cancel')}</button>
                <button class="btn btn-primary" onclick="saveItemEdit(${slotIndex})">
                    <i class="fas fa-check"></i> ${i18n.t('button.save')}
                </button>
            </div>
        </div>
    `;

    document.body.appendChild(modal);
    updateItemTextPreview();
}

/**
 * Ein zugeklappter Abschnitt mit Ueberschrift, Erklaerungssatz und Inhalt.
 * Nutzt dieselbe Collapsible-Mechanik wie der Rest des Panels.
 */
function itemSection(id, icon, title, help, body) {
    return `
        <div class="collapsible" style="margin-bottom: 0.75rem;" id="item-section-${id}">
            <div class="collapsible-header" onclick="toggleCollapsible(this)">
                <div class="collapsible-title">
                    <i class="fas ${icon}" style="margin-right:0.5rem;color:var(--text-muted);"></i>
                    <span>${title}</span>
                </div>
                <i class="fas fa-chevron-down collapsible-icon"></i>
            </div>
            <div class="collapsible-content">
                <p style="color:var(--text-muted);font-size:0.85rem;margin:0 0 0.75rem 0;">${help}</p>
                ${body}
            </div>
        </div>
    `;
}

/** Auswahlkaesten fuer die ItemFlags des Servers. */
function renderItemFlagChoices(selected) {
    const active = new Set(selected || []);
    const flags = ITEM_CATALOG.itemFlags.length > 0 ? ITEM_CATALOG.itemFlags : [
        'HIDE_ENCHANTS', 'HIDE_ATTRIBUTES', 'HIDE_UNBREAKABLE', 'HIDE_DESTROYS',
        'HIDE_PLACED_ON', 'HIDE_POTION_EFFECTS', 'HIDE_DYE'
    ];
    return flags.map(flag => `
        <label style="display:flex;align-items:center;gap:0.4rem;font-size:0.85rem;cursor:pointer;">
            <input type="checkbox" class="item-flag-choice" value="${escapeAttr(flag)}" ${active.has(flag) ? 'checked' : ''}>
            <span>${escapeHtml(itemDisplayName(flag.replace(/^HIDE_/, '')))}</span>
        </label>
    `).join('');
}

/**
 * Trank-Editor: Basisart plus beliebig viele eigene Effekte.
 *
 * Ohne diesen Abschnitt war jeder konfigurierte Trank im Spiel eine leere Glasflasche -
 * die Basisart liess sich schlicht nicht angeben.
 */
function renderPotionEditor(potion) {
    const types = ITEM_CATALOG.potionTypes;
    const options = types.map(type =>
        `<option value="${escapeAttr(type)}" ${potion.type === type ? 'selected' : ''}>${escapeHtml(itemDisplayName(type))}</option>`
    ).join('');

    return `
        <div class="form-group">
            <label>${i18n.t('item.label.potionType')}</label>
            <select id="item-potion-type" class="form-control">
                <option value="">${i18n.t('item.label.potionNone')}</option>
                ${options}
            </select>
        </div>
        <div style="display:flex;gap:1.5rem;margin-top:0.75rem;flex-wrap:wrap;">
            <label style="display:flex;align-items:center;gap:0.4rem;cursor:pointer;">
                <input type="checkbox" id="item-potion-extended" ${potion.extended ? 'checked' : ''}>
                <span>${i18n.t('item.label.potionExtended')}</span>
            </label>
            <label style="display:flex;align-items:center;gap:0.4rem;cursor:pointer;">
                <input type="checkbox" id="item-potion-upgraded" ${potion.upgraded ? 'checked' : ''}>
                <span>${i18n.t('item.label.potionUpgraded')}</span>
            </label>
        </div>
        <small style="display:block;color:var(--text-muted);margin-top:0.4rem;">${i18n.t('item.help.potionVariants')}</small>

        <div style="margin-top:1.25rem;">
            <label>${i18n.t('item.label.customEffects')}</label>
            <small style="display:block;color:var(--text-muted);margin-bottom:0.5rem;">${i18n.t('item.help.customEffects')}</small>
            <div id="item-potion-effects">
                ${(potion['custom-effects'] || []).map(renderPotionEffectRow).join('')}
            </div>
            <button type="button" class="btn btn-secondary btn-sm" style="margin-top:0.5rem;" onclick="addPotionEffectRow()">
                <i class="fas fa-plus"></i> ${i18n.t('item.button.addEffect')}
            </button>
        </div>
    `;
}

/** Eine Zeile des Effekt-Editors: Typ, Dauer in Sekunden, Stufe. */
function renderPotionEffectRow(effect) {
    const data = effect || {};
    const effects = ITEM_CATALOG.potionEffects;
    const options = effects.map(type =>
        `<option value="${escapeAttr(type)}" ${data.type === type ? 'selected' : ''}>${escapeHtml(itemDisplayName(type))}</option>`
    ).join('');
    // In der Konfiguration steht die Dauer in Ticks; im Panel sind Sekunden verstaendlicher.
    const seconds = Math.max(1, Math.round((data.duration || 600) / 20));

    return `
        <div class="potion-effect-row" style="display:grid;grid-template-columns:1fr 90px 90px 32px;gap:0.5rem;align-items:center;margin-bottom:0.4rem;">
            <select class="form-control effect-type">${options}</select>
            <input type="number" class="form-control effect-seconds" min="1" step="1" value="${seconds}"
                   title="${i18n.t('item.label.effectSeconds')}">
            <input type="number" class="form-control effect-amplifier" min="1" max="255" step="1"
                   value="${(data.amplifier || 0) + 1}" title="${i18n.t('item.label.effectLevel')}">
            <button type="button" class="btn btn-danger btn-sm" onclick="this.closest('.potion-effect-row').remove()">
                <i class="fas fa-times"></i>
            </button>
        </div>
    `;
}

function addPotionEffectRow() {
    const container = document.getElementById('item-potion-effects');
    if (!container) return;
    container.insertAdjacentHTML('beforeend', renderPotionEffectRow({}));
}

/**
 * Zeigt Name und Lore so, wie sie im Spiel aussehen - inklusive &-Farbcodes.
 * Ohne Vorschau muesste man jede Farbaenderung im Spiel nachpruefen.
 */
function updateItemTextPreview() {
    const preview = document.getElementById('item-text-preview');
    if (!preview) return;

    const name = document.getElementById('item-custom-name');
    const lore = document.getElementById('item-lore');
    const lines = [];
    if (name && name.value.trim()) {
        lines.push(minecraftColorHtml(name.value));
    }
    if (lore) {
        lore.value.split('\n').forEach(line => {
            if (line.trim()) lines.push(minecraftColorHtml(line, '#aaaaaa'));
        });
    }
    preview.innerHTML = lines.length > 0
        ? lines.map(line => `<div>${line}</div>`).join('')
        : `<div style="color:var(--text-muted);font-style:italic;">${i18n.t('item.label.previewEmpty')}</div>`;
}

/** Minecraft-Farbcodes fuer die Vorschau in HTML uebersetzen. */
const MC_COLORS = {
    '0': '#000000', '1': '#0000AA', '2': '#00AA00', '3': '#00AAAA',
    '4': '#AA0000', '5': '#AA00AA', '6': '#FFAA00', '7': '#AAAAAA',
    '8': '#555555', '9': '#5555FF', a: '#55FF55', b: '#55FFFF',
    c: '#FF5555', d: '#FF55FF', e: '#FFFF55', f: '#FFFFFF'
};

function minecraftColorHtml(text, defaultColor) {
    let color = defaultColor || '#FFFFFF';
    let bold = false;
    let italic = false;
    let underline = false;
    let out = '';
    let buffer = '';

    const flush = () => {
        if (!buffer) return;
        const style = `color:${color};` +
            (bold ? 'font-weight:bold;' : '') +
            (italic ? 'font-style:italic;' : '') +
            (underline ? 'text-decoration:underline;' : '');
        out += `<span style="${style}">${escapeHtml(buffer)}</span>`;
        buffer = '';
    };

    for (let i = 0; i < text.length; i++) {
        if ((text[i] === '&' || text[i] === '§') && i + 1 < text.length) {
            const code = text[i + 1].toLowerCase();
            if (MC_COLORS[code]) {
                flush();
                color = MC_COLORS[code];
                // Eine Farbe setzt in Minecraft alle Formatierungen zurueck.
                bold = italic = underline = false;
                i++;
                continue;
            }
            if (code === 'l' || code === 'o' || code === 'n' || code === 'r') {
                flush();
                if (code === 'l') bold = true;
                else if (code === 'o') italic = true;
                else if (code === 'n') underline = true;
                else {
                    color = defaultColor || '#FFFFFF';
                    bold = italic = underline = false;
                }
                i++;
                continue;
            }
        }
        buffer += text[i];
    }
    flush();
    return out;
}

function saveItemEdit(slotIndex) {
    const item = currentEditingEquipment.inventory?.find(i => i.slot === slotIndex);
    if (!item) return;

    const amountInput = document.getElementById('item-amount');
    if (amountInput) {
        item.amount = parseInt(amountInput.value, 10) || 1;
    }

    setOrDelete(item, 'name', valueOf('item-custom-name'));

    // Verzauberungen im Format NAME:STUFE
    const enchantments = [];
    document.querySelectorAll('.enchant-select').forEach(select => {
        const level = parseInt(select.value, 10);
        if (level > 0) {
            enchantments.push(`${select.dataset.enchant}:${level}`);
        }
    });
    setOrDelete(item, 'enchantments', enchantments.length > 0 ? enchantments : '');

    // Lore: leere Zeilen am Rand wegwerfen, Leerzeilen in der Mitte behalten
    const loreField = document.getElementById('item-lore');
    if (loreField) {
        const lines = loreField.value.split('\n');
        while (lines.length > 0 && !lines[0].trim()) lines.shift();
        while (lines.length > 0 && !lines[lines.length - 1].trim()) lines.pop();
        setOrDelete(item, 'lore', lines.length > 0 ? lines : '');
    }

    const unbreakable = document.getElementById('item-unbreakable');
    if (unbreakable) {
        if (unbreakable.checked) item.unbreakable = true; else delete item.unbreakable;
    }

    const damage = document.getElementById('item-damage');
    if (damage) {
        const value = parseInt(damage.value, 10) || 0;
        if (value > 0) item.damage = value; else delete item.damage;
    }

    const cmd = document.getElementById('item-custom-model-data');
    if (cmd) {
        const value = parseInt(cmd.value, 10) || 0;
        if (value > 0) item['custom-model-data'] = value; else delete item['custom-model-data'];
    }

    const flagBoxes = document.querySelectorAll('.item-flag-choice');
    if (flagBoxes.length > 0) {
        const flags = [];
        flagBoxes.forEach(box => { if (box.checked) flags.push(box.value); });
        setOrDelete(item, 'item-flags', flags.length > 0 ? flags : '');
    }

    savePotionEdit(item);

    updateInventorySlotUI(slotIndex);
    closeItemEditModal();
    showToast(i18n.t('label.itemUpdated'), 'success');
}

/** Liest den Trank-Abschnitt aus; entfernt ihn, wenn nichts konfiguriert wurde. */
function savePotionEdit(item) {
    const typeSelect = document.getElementById('item-potion-type');
    const effectRows = document.querySelectorAll('.potion-effect-row');
    if (!typeSelect && effectRows.length === 0) {
        return; // Abschnitt gar nicht vorhanden - vorhandene Konfiguration nicht antasten
    }

    const potion = {};
    if (typeSelect && typeSelect.value) {
        potion.type = typeSelect.value;
        const extended = document.getElementById('item-potion-extended');
        const upgraded = document.getElementById('item-potion-upgraded');
        if (extended && extended.checked) potion.extended = true;
        if (upgraded && upgraded.checked) potion.upgraded = true;
    }

    const effects = [];
    effectRows.forEach(row => {
        const type = row.querySelector('.effect-type');
        if (!type || !type.value) return;
        const seconds = parseInt(row.querySelector('.effect-seconds').value, 10) || 30;
        const level = parseInt(row.querySelector('.effect-amplifier').value, 10) || 1;
        effects.push({
            type: type.value,
            duration: Math.max(1, seconds) * 20,   // Konfiguration rechnet in Ticks
            amplifier: Math.max(0, level - 1)      // Stufe I entspricht amplifier 0
        });
    });
    if (effects.length > 0) {
        potion['custom-effects'] = effects;
    }

    if (Object.keys(potion).length > 0) {
        item.potion = potion;
    } else {
        delete item.potion;
    }
}

function valueOf(id) {
    const el = document.getElementById(id);
    return el ? el.value.trim() : '';
}

/** Setzt ein Feld oder entfernt es, damit leere Werte nicht in der YAML landen. */
function setOrDelete(target, key, value) {
    if (value === null || value === undefined || value === '' ||
        (Array.isArray(value) && value.length === 0)) {
        delete target[key];
    } else {
        target[key] = value;
    }
}

function renderEnchantmentsList(availableEnchants, currentEnchants) {
    // Parse current enchants (Format: "ENCHANT:LEVEL")
    const currentMap = {};
    currentEnchants.forEach(e => {
        const [name, level] = e.split(':');
        currentMap[name] = parseInt(level) || 1;
    });
    
    return availableEnchants.map(enchant => {
        const currentLevel = currentMap[enchant.id] || 0;
        const levelOptions = [];
        for (let i = 0; i <= enchant.maxLevel; i++) {
            levelOptions.push(`<option value="${i}" ${i === currentLevel ? 'selected' : ''}>${i === 0 ? i18n.t('label.none') : i18n.t('label.level') + ' ' + i}</option>`);
        }
        
        return `
            <div class="enchantment-row" style="display: flex; align-items: center; justify-content: space-between; padding: 0.5rem; border-bottom: 1px solid var(--border);">
                <div style="display: flex; align-items: center; gap: 0.5rem;">
                    <span style="font-size: 1.2em;">${enchant.icon}</span>
                    <span>${enchant.name}</span>
                </div>
                <select class="form-input enchant-select" data-enchant="${enchant.id}" style="width: auto; min-width: 100px;">
                    ${levelOptions.join('')}
                </select>
            </div>
        `;
    }).join('');
}


function removeInventoryItem(slotIndex) {
    currentEditingEquipment.inventory = currentEditingEquipment.inventory.filter(i => i.slot !== slotIndex);
    updateInventorySlotUI(slotIndex);
    closeItemEditModal();
    showToast(i18n.t('label.itemRemoved'), 'success');
}

function closeItemEditModal() {
    document.getElementById('item-edit-modal')?.remove();
}

function clearEquipmentInventory() {
    if (confirm(i18n.t('confirm.clearInventory'))) {
        currentEditingEquipment.inventory = [];
        const grid = document.getElementById('equipment-inventory-grid');
        if (grid) {
            grid.innerHTML = renderInventoryGrid([]);
        }
        showToast(i18n.t('label.invCleared'), 'success');
    }
}

/**
 * Suche im Inventar-Tab.
 *
 * Zeichnet die Abschnitte neu, statt wie frueher bereits gerenderte Knoten per
 * `style.display` auszublenden - bei rund 1600 Items werden gar nicht alle gerendert,
 * ein reiner DOM-Filter wuerde die meisten Treffer also nie finden.
 */
const filterEquipmentItems = debounce(function (searchTerm) {
    const categories = document.getElementById('equipment-item-categories');
    if (!categories) return;
    categories.innerHTML = renderItemCategories(searchTerm);
}, 150);

function switchEquipmentTab(tabName) {
    document.querySelectorAll('#equipment-editor-modal .tab-content').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('#equipment-editor-modal .tab').forEach(t => t.classList.remove('active'));
    
    document.getElementById(`equipment-tab-${tabName}`)?.classList.add('active');
    document.querySelector(`#equipment-editor-modal [onclick="switchEquipmentTab('${tabName}')"]`)?.classList.add('active');
}

function closeEquipmentEditor() {
    document.getElementById('equipment-editor-modal')?.remove();
    currentEditingEquipment = null;
    selectedArmorSlot = null;
    // Reset drag & drop listener tracking when modal closes
    resetInventoryListeners();
}

/**
 * Prueft die Slot-Belegung des Inventars.
 *
 * Ein Spielerinventar hat die Slots 0-35. Alles darueber verwirft der Server beim Austeilen
 * stillschweigend, und zwei Items auf demselben Slot bedeuten, dass eines davon nie im Spiel
 * ankommt. Beides war bisher ohne Rueckmeldung moeglich.
 *
 * @returns {string|null} Fehlermeldung oder null, wenn alles in Ordnung ist
 */
function validateInventorySlots(inventory) {
    const seen = new Map();
    for (const entry of inventory) {
        const slot = entry.slot;
        if (!Number.isInteger(slot) || slot < 0 || slot > 35) {
            return i18n.t('editor.slotOutOfRange', { slot: String(slot), item: entry.item });
        }
        if (seen.has(slot)) {
            return i18n.t('editor.slotDuplicate', {
                slot: slot,
                first: seen.get(slot),
                second: entry.item
            });
        }
        seen.set(slot, entry.item);
    }
    return null;
}

function saveEquipmentEditor() {
    if (!currentEditingEquipment) {
        showToast(i18n.t('error.fieldRequired'), 'error');
        return;
    }
    
    if (!currentEditingEquipment.id || currentEditingEquipment.id.trim() === '') {
        showToast(i18n.t('error.fieldRequired'), 'error');
        return;
    }

    // Inventar pruefen, bevor gespeichert wird: doppelt belegte oder ausserhalb liegende
    // Slots kamen bisher unbemerkt in die YAML und wurden vom Server still verworfen.
    const slotProblem = validateInventorySlots(currentEditingEquipment.inventory || []);
    if (slotProblem) {
        showToast(slotProblem, 'error');
        switchEquipmentTab('inventory');
        return;
    }

    saveEquipmentIcon(currentEditingEquipment);

    const equipId = currentEditingEquipment.id;
    const equipData = JSON.parse(JSON.stringify(currentEditingEquipment));
    delete equipData.id;

    // Prüfen, ob keine reale Änderung am Equipment-Set vorliegt
    if (currentEditingEquipmentOriginal && isDeepEqual(equipData, currentEditingEquipmentOriginal)) {
        closeEquipmentEditor();
        showToast(i18n.t('info.noChanges'), 'info');
        return;
    }

    equipmentSets()[equipId] = equipData;
    recordChange('equipment', equipmentSetPath(equipId), equipData);
    renderEquipmentList();
    updateQuickActionsPanel();
    closeEquipmentEditor();
    showToast(i18n.t('equipment.saved'), 'success');
}

// ============================================
// Rewards Editor
// ============================================

function renderRewardsEditor(rewards) {
    const winner = rewards.winner || { items: { enabled: false, items: [] }, commands: { enabled: false, commands: [] } };
    const participation = rewards.participation || { items: { enabled: false, items: [] }, commands: { enabled: false, commands: [] } };
    
    return `
        <!-- Winner Rewards -->
        <div class="collapsible open">
            <div class="collapsible-header" onclick="toggleCollapsible(this)" style="background: rgba(255, 215, 0, 0.1);">
                <div class="collapsible-title" style="color: #ffd700;">
                    <i class="fas fa-trophy"></i>
                    <span>${i18n.t('rewards.winnerRewards')}</span>
                </div>
                <i class="fas fa-chevron-down collapsible-icon"></i>
            </div>
            <div class="collapsible-content">
                <!-- Items -->
                <div class="card" style="margin-bottom: 1rem;">
                    <div class="card-header">
                        <div class="card-title">
                            <i class="fas fa-box"></i> ${i18n.t('rewards.itemRewards')}
                        </div>
                        <label class="toggle">
                            <input type="checkbox" ${winner.items?.enabled ? 'checked' : ''}
                                   onchange="currentEditingEvent.rewards.winner.items.enabled = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>
                    <div class="card-body">
                        <div id="winner-reward-items">
                            ${renderRewardItems('winner', winner.items?.items || [])}
                        </div>
                        <button class="btn btn-secondary" onclick="addRewardItem('winner')" style="margin-top: 0.5rem;">
                            <i class="fas fa-plus"></i> ${i18n.t('rewards.addItem')}
                        </button>
                    </div>
                </div>
                
                <!-- Commands -->
                <div class="card">
                    <div class="card-header">
                        <div class="card-title">
                            <i class="fas fa-terminal"></i> ${i18n.t('rewards.commandRewards')}
                        </div>
                        <label class="toggle">
                            <input type="checkbox" ${winner.commands?.enabled ? 'checked' : ''}
                                   onchange="currentEditingEvent.rewards.winner.commands.enabled = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>
                    <div class="card-body">
                        <div id="winner-reward-commands">
                            ${renderRewardCommands('winner', winner.commands?.commands || [])}
                        </div>
                        <button class="btn btn-secondary" onclick="addRewardCommand('winner')" style="margin-top: 0.5rem;">
                            <i class="fas fa-plus"></i> ${i18n.t('rewards.addCommand')}
                        </button>
                    </div>
                </div>
            </div>
        </div>

        <!-- Participation Rewards -->
        <div class="collapsible open">
            <div class="collapsible-header" onclick="toggleCollapsible(this)" style="background: rgba(192, 192, 192, 0.1);">
                <div class="collapsible-title" style="color: #c0c0c0;">
                    <i class="fas fa-medal"></i>
                    <span>${i18n.t('rewards.participationRewards')}</span>
                </div>
                <i class="fas fa-chevron-down collapsible-icon"></i>
            </div>
            <div class="collapsible-content">
                <!-- Items -->
                <div class="card" style="margin-bottom: 1rem;">
                    <div class="card-header">
                        <div class="card-title">
                            <i class="fas fa-box"></i> ${i18n.t('rewards.itemRewards')}
                        </div>
                        <label class="toggle">
                            <input type="checkbox" ${participation.items?.enabled ? 'checked' : ''}
                                   onchange="currentEditingEvent.rewards.participation.items.enabled = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>
                    <div class="card-body">
                        <div id="participation-reward-items">
                            ${renderRewardItems('participation', participation.items?.items || [])}
                        </div>
                        <button class="btn btn-secondary" onclick="addRewardItem('participation')" style="margin-top: 0.5rem;">
                            <i class="fas fa-plus"></i> ${i18n.t('rewards.addItem')}
                        </button>
                    </div>
                </div>
                
                <!-- Commands -->
                <div class="card">
                    <div class="card-header">
                        <div class="card-title">
                            <i class="fas fa-terminal"></i> ${i18n.t('rewards.commandRewards')}
                        </div>
                        <label class="toggle">
                            <input type="checkbox" ${participation.commands?.enabled ? 'checked' : ''}
                                   onchange="currentEditingEvent.rewards.participation.commands.enabled = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>
                    <div class="card-body">
                        <div id="participation-reward-commands">
                            ${renderRewardCommands('participation', participation.commands?.commands || [])}
                        </div>
                        <button class="btn btn-secondary" onclick="addRewardCommand('participation')" style="margin-top: 0.5rem;">
                            <i class="fas fa-plus"></i> ${i18n.t('rewards.addCommand')}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    `;
}

function renderRewardItems(type, items) {
    if (!items || items.length === 0) {
        return `<p style="color: var(--text-muted);">${i18n.t('rewards.noItems')}</p>`;
    }
    
    return items.map((item, i) => `
        <div style="display: flex; gap: 0.5rem; align-items: center; margin-bottom: 0.5rem; padding: 0.5rem; background: var(--background); border-radius: 6px;">
            <div class="item-icon" style="width: 32px; height: 32px;">
                ${itemIconHtml(item.item || 'DIAMOND', 24, { lazy: true })}
            </div>
            <input type="text" class="form-control" value="${item.item || ''}" placeholder="DIAMOND" style="flex: 1;"
                   onchange="updateRewardItem('${type}', ${i}, 'item', this.value)">
            <input type="number" class="form-control" value="${item.amount || 1}" min="1" max="64" style="width: 60px;"
                   onchange="updateRewardItem('${type}', ${i}, 'amount', parseInt(this.value))">
            <button class="btn btn-danger btn-icon" onclick="removeRewardItem('${type}', ${i})">
                <i class="fas fa-times"></i>
            </button>
        </div>
    `).join('');
}

function renderRewardCommands(type, commands) {
    if (!commands || commands.length === 0) {
        return `<p style="color: var(--text-muted);">${i18n.t('rewards.noCommands')}</p>`;
    }
    
    return commands.map((cmd, i) => `
        <div style="display: flex; gap: 0.5rem; align-items: center; margin-bottom: 0.5rem;">
            <span style="color: var(--text-muted);">/</span>
            <input type="text" class="form-control" value="${cmd}" placeholder="eco give {player} 100" style="flex: 1;"
                   onchange="updateRewardCommand('${type}', ${i}, this.value)">
            <button class="btn btn-danger btn-icon" onclick="removeRewardCommand('${type}', ${i})">
                <i class="fas fa-times"></i>
            </button>
        </div>
    `).join('');
}

function addRewardItem(type) {
    currentEditingEvent.rewards = currentEditingEvent.rewards || {};
    currentEditingEvent.rewards[type] = currentEditingEvent.rewards[type] || { items: { enabled: false, items: [] }, commands: { enabled: false, commands: [] } };
    currentEditingEvent.rewards[type].items = currentEditingEvent.rewards[type].items || { enabled: false, items: [] };
    currentEditingEvent.rewards[type].items.items = currentEditingEvent.rewards[type].items.items || [];
    currentEditingEvent.rewards[type].items.items.push({ item: 'DIAMOND', amount: 1 });
    
    const container = document.getElementById(`${type}-reward-items`);
    if (container) {
        container.innerHTML = renderRewardItems(type, currentEditingEvent.rewards[type].items.items);
    }
}

function removeRewardItem(type, index) {
    currentEditingEvent.rewards[type].items.items.splice(index, 1);
    const container = document.getElementById(`${type}-reward-items`);
    if (container) {
        container.innerHTML = renderRewardItems(type, currentEditingEvent.rewards[type].items.items);
    }
}

function updateRewardItem(type, index, field, value) {
    if (currentEditingEvent.rewards?.[type]?.items?.items?.[index]) {
        currentEditingEvent.rewards[type].items.items[index][field] = value;
    }
}

function addRewardCommand(type) {
    currentEditingEvent.rewards = currentEditingEvent.rewards || {};
    currentEditingEvent.rewards[type] = currentEditingEvent.rewards[type] || { items: { enabled: false, items: [] }, commands: { enabled: false, commands: [] } };
    currentEditingEvent.rewards[type].commands = currentEditingEvent.rewards[type].commands || { enabled: false, commands: [] };
    currentEditingEvent.rewards[type].commands.commands = currentEditingEvent.rewards[type].commands.commands || [];
    currentEditingEvent.rewards[type].commands.commands.push('');
    
    const container = document.getElementById(`${type}-reward-commands`);
    if (container) {
        container.innerHTML = renderRewardCommands(type, currentEditingEvent.rewards[type].commands.commands);
    }
}

function removeRewardCommand(type, index) {
    currentEditingEvent.rewards[type].commands.commands.splice(index, 1);
    const container = document.getElementById(`${type}-reward-commands`);
    if (container) {
        container.innerHTML = renderRewardCommands(type, currentEditingEvent.rewards[type].commands.commands);
    }
}

function updateRewardCommand(type, index, value) {
    if (currentEditingEvent.rewards?.[type]?.commands?.commands) {
        currentEditingEvent.rewards[type].commands.commands[index] = value;
    }
}

// ============================================
// Helper Functions
// ============================================

function toggleCollapsible(header) {
    const collapsible = header.closest('.collapsible');
    collapsible.classList.toggle('open');
}

function coordinateInput(label, coord = {}) {
    return `
        <div class="coord-input">
            <label>${label}</label>
            <input type="number" class="form-control" value="${coord.x || 0}" placeholder="X" step="0.5">
        </div>
    `;
}

// ============================================
// WICHTIG: Globale Window-Registrierung
// Alle Funktionen müssen am window-Objekt registriert werden,
// damit onclick-Handler sie finden können!
// ============================================

// Event Editor Funktionen
window.createNewEvent = createNewEvent;
window.editEvent = editEvent;
window.openEventEditor = openEventEditor;
window.closeEventEditor = closeEventEditor;
window.saveEventEditor = saveEventEditor;
window.switchEventTab = switchEventTab;
window.updateEventSpawnType = updateEventSpawnType;
window.updateEventSpawnCoord = updateEventSpawnCoord;
window.addEventSpawnPoint = addEventSpawnPoint;
window.removeEventSpawnPoint = removeEventSpawnPoint;
window.updateEventSpawnPointCoord = updateEventSpawnPointCoord;
window.addTeamSpawnPoint = addTeamSpawnPoint;
window.removeTeamSpawnPoint = removeTeamSpawnPoint;
window.updateTeamSpawnCoord = updateTeamSpawnCoord;
window.renderWinConditionOptions = renderWinConditionOptions;
window.updateWinConditionUI = updateWinConditionUI;
window.handleWinConditionItemChange = handleWinConditionItemChange;

// World Editor Funktionen  
window.createNewWorld = createNewWorld;
window.editWorld = editWorld;
window.openWorldEditor = openWorldEditor;
window.closeWorldEditor = closeWorldEditor;
window.saveWorldEditor = saveWorldEditor;
window.switchWorldTab = switchWorldTab;
window.updateWorldSpawnType = updateWorldSpawnType;
window.updateWorldSpawn = updateWorldSpawn;
window.addWorldEquipmentGroup = addWorldEquipmentGroup;
window.removeWorldEquipmentGroup = removeWorldEquipmentGroup;

// Multiverse-Teil des World-Editors
window.renderWorldIdSelector = renderWorldIdSelector;
window.onWorldIdSelectChange = onWorldIdSelectChange;
window.onWorldIdCustomInput = onWorldIdCustomInput;
window.refreshWorldIdDependentUi = refreshWorldIdDependentUi;
window.renderWorldIdStatus = renderWorldIdStatus;
window.renderWorldMultiverseTab = renderWorldMultiverseTab;
window.updateWorldCreateSpec = updateWorldCreateSpec;
window.createMvWorldFromEditor = createMvWorldFromEditor;

// Equipment Editor Funktionen
window.createNewEquipment = createNewEquipment;
window.editEquipment = editEquipment;
window.openEquipmentEditor = openEquipmentEditor;
window.closeEquipmentEditor = closeEquipmentEditor;
window.saveEquipmentEditor = saveEquipmentEditor;
window.switchEquipmentTab = switchEquipmentTab;
window.setupInventoryDragDropListeners = setupInventoryDragDropListeners;
window.resetInventoryListeners = resetInventoryListeners;

// Inventory Drag & Drop Funktionen
window.onInventoryDragStart = onInventoryDragStart;
window.onInventoryDragOver = onInventoryDragOver;
window.onInventoryDragLeave = onInventoryDragLeave;
window.onInventoryDrop = onInventoryDrop;
window.onItemPickerDragStart = onItemPickerDragStart;

// Inventory Edit Funktionen
window.editInventorySlot = editInventorySlot;
window.addItemToInventory = addItemToInventory;
window.addItemToSlot = addItemToSlot;
window.updateInventorySlotUI = updateInventorySlotUI;
window.clearEquipmentInventory = clearEquipmentInventory;
window.filterEquipmentItems = filterEquipmentItems;
window.refreshInventoryGrid = refreshInventoryGrid;

// Armor Slot Funktionen
window.selectArmorSlot = selectArmorSlot;
window.setArmorItem = setArmorItem;
window.clickArmorSlot = clickArmorSlot;
window.armorSlotAccepts = armorSlotAccepts;

// Enchantment & Modal Funktionen
window.editArmorSlot = editArmorSlot;
window.closeArmorEditModal = closeArmorEditModal;
window.saveArmorEdit = saveArmorEdit;
window.removeArmorItem = removeArmorItem;
window.closeItemEditModal = closeItemEditModal;
window.saveItemEdit = saveItemEdit;
window.removeInventoryItem = removeInventoryItem;
window.renderEnchantmentsList = renderEnchantmentsList;
window.getAvailableEnchantments = getAvailableEnchantments;
window.openItemEditModal = openItemEditModal;

// Reward Funktionen
window.addRewardItem = addRewardItem;
window.removeRewardItem = removeRewardItem;
window.updateRewardItem = updateRewardItem;
window.addRewardCommand = addRewardCommand;
window.removeRewardCommand = removeRewardCommand;
window.updateRewardCommand = updateRewardCommand;

// Helper Funktionen
window.toggleCollapsible = toggleCollapsible;

// Equipment-Basis-Tab
window.onEquipmentWorldsModeChange = onEquipmentWorldsModeChange;
window.saveEquipmentWorlds = saveEquipmentWorlds;
window.updateEquipmentTextPreview = updateEquipmentTextPreview;
window.onEquipmentIconInput = onEquipmentIconInput;
window.applyEquipmentIcon = applyEquipmentIcon;
window.validateInventorySlots = validateInventorySlots;

// Item-Auswahl (werden aus inline-Attributen der Editor-Modale gerufen)
window.filterArmorItems = filterArmorItems;
window.renderArmorPicker = renderArmorPicker;
window.renderItemCategories = renderItemCategories;

// Item-Eigenschaften im Bearbeiten-Modal
window.updateItemTextPreview = updateItemTextPreview;
window.addPotionEffectRow = addPotionEffectRow;
window.saveItemEdit = saveItemEdit;
window.closeItemEditModal = closeItemEditModal;

// Debug: Bestätige dass Registrierung erfolgreich war
console.log('✓ Editor-Funktionen global registriert:', {
    createNewEvent: typeof window.createNewEvent,
    editEvent: typeof window.editEvent,
    createNewWorld: typeof window.createNewWorld,
    editWorld: typeof window.editWorld,
    createNewEquipment: typeof window.createNewEquipment,
    editEquipment: typeof window.editEquipment,
    onInventoryDragStart: typeof window.onInventoryDragStart,
    onInventoryDrop: typeof window.onInventoryDrop,
    onItemPickerDragStart: typeof window.onItemPickerDragStart
});

console.log('editors.js fully loaded!');
