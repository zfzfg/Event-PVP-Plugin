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
// Minecraft Enchantments Datenbank
// ============================================
const MINECRAFT_ENCHANTMENTS = {
    // Rüstung allgemein
    armor: [
        { id: 'PROTECTION', name: 'Protection', maxLevel: 4, icon: '🛡️' },
        { id: 'FIRE_PROTECTION', name: 'Fire Protection', maxLevel: 4, icon: '🔥' },
        { id: 'BLAST_PROTECTION', name: 'Blast Protection', maxLevel: 4, icon: '💥' },
        { id: 'PROJECTILE_PROTECTION', name: 'Projectile Protection', maxLevel: 4, icon: '🏹' },
        { id: 'THORNS', name: 'Thorns', maxLevel: 3, icon: '🌵' },
        { id: 'UNBREAKING', name: 'Unbreaking', maxLevel: 3, icon: '💎' },
        { id: 'MENDING', name: 'Mending', maxLevel: 1, icon: '✨' },
        { id: 'CURSE_OF_VANISHING', name: 'Curse of Vanishing', maxLevel: 1, icon: '👻' },
        { id: 'CURSE_OF_BINDING', name: 'Curse of Binding', maxLevel: 1, icon: '🔒' }
    ],
    // Nur Helm
    helmet: [
        { id: 'RESPIRATION', name: 'Respiration', maxLevel: 3, icon: '💨' },
        { id: 'AQUA_AFFINITY', name: 'Aqua Affinity', maxLevel: 1, icon: '🌊' }
    ],
    // Nur Stiefel
    boots: [
        { id: 'FEATHER_FALLING', name: 'Feather Falling', maxLevel: 4, icon: '🪶' },
        { id: 'DEPTH_STRIDER', name: 'Depth Strider', maxLevel: 3, icon: '🏊' },
        { id: 'FROST_WALKER', name: 'Frost Walker', maxLevel: 2, icon: '❄️' },
        { id: 'SOUL_SPEED', name: 'Soul Speed', maxLevel: 3, icon: '👻' },
        { id: 'SWIFT_SNEAK', name: 'Swift Sneak', maxLevel: 3, icon: '🦶' }
    ],
    // Schwerter
    sword: [
        { id: 'SHARPNESS', name: 'Sharpness', maxLevel: 5, icon: '⚔️' },
        { id: 'SMITE', name: 'Smite', maxLevel: 5, icon: '💀' },
        { id: 'BANE_OF_ARTHROPODS', name: 'Bane of Arthropods', maxLevel: 5, icon: '🕷️' },
        { id: 'KNOCKBACK', name: 'Knockback', maxLevel: 2, icon: '💨' },
        { id: 'FIRE_ASPECT', name: 'Fire Aspect', maxLevel: 2, icon: '🔥' },
        { id: 'LOOTING', name: 'Looting', maxLevel: 3, icon: '💰' },
        { id: 'SWEEPING_EDGE', name: 'Sweeping Edge', maxLevel: 3, icon: '🌀' },
        { id: 'UNBREAKING', name: 'Unbreaking', maxLevel: 3, icon: '💎' },
        { id: 'MENDING', name: 'Mending', maxLevel: 1, icon: '✨' }
    ],
    // Äxte
    axe: [
        { id: 'SHARPNESS', name: 'Sharpness', maxLevel: 5, icon: '⚔️' },
        { id: 'SMITE', name: 'Smite', maxLevel: 5, icon: '💀' },
        { id: 'BANE_OF_ARTHROPODS', name: 'Bane of Arthropods', maxLevel: 5, icon: '🕷️' },
        { id: 'EFFICIENCY', name: 'Efficiency', maxLevel: 5, icon: '⚡' },
        { id: 'SILK_TOUCH', name: 'Silk Touch', maxLevel: 1, icon: '🧵' },
        { id: 'FORTUNE', name: 'Fortune', maxLevel: 3, icon: '🍀' },
        { id: 'UNBREAKING', name: 'Unbreaking', maxLevel: 3, icon: '💎' },
        { id: 'MENDING', name: 'Mending', maxLevel: 1, icon: '✨' }
    ],
    // Bögen
    bow: [
        { id: 'POWER', name: 'Power', maxLevel: 5, icon: '💪' },
        { id: 'PUNCH', name: 'Punch', maxLevel: 2, icon: '👊' },
        { id: 'FLAME', name: 'Flame', maxLevel: 1, icon: '🔥' },
        { id: 'INFINITY', name: 'Infinity', maxLevel: 1, icon: '♾️' },
        { id: 'UNBREAKING', name: 'Unbreaking', maxLevel: 3, icon: '💎' },
        { id: 'MENDING', name: 'Mending', maxLevel: 1, icon: '✨' }
    ],
    // Armbrüste
    crossbow: [
        { id: 'MULTISHOT', name: 'Multishot', maxLevel: 1, icon: '🎯' },
        { id: 'PIERCING', name: 'Piercing', maxLevel: 4, icon: '📍' },
        { id: 'QUICK_CHARGE', name: 'Quick Charge', maxLevel: 3, icon: '⚡' },
        { id: 'UNBREAKING', name: 'Unbreaking', maxLevel: 3, icon: '💎' },
        { id: 'MENDING', name: 'Mending', maxLevel: 1, icon: '✨' }
    ],
    // Dreizack
    trident: [
        { id: 'IMPALING', name: 'Impaling', maxLevel: 5, icon: '🔱' },
        { id: 'RIPTIDE', name: 'Riptide', maxLevel: 3, icon: '🌊' },
        { id: 'LOYALTY', name: 'Loyalty', maxLevel: 3, icon: '❤️' },
        { id: 'CHANNELING', name: 'Channeling', maxLevel: 1, icon: '⚡' },
        { id: 'UNBREAKING', name: 'Unbreaking', maxLevel: 3, icon: '💎' },
        { id: 'MENDING', name: 'Mending', maxLevel: 1, icon: '✨' }
    ],
    // Werkzeuge (Spitzhacke, Schaufel, Hacke)
    tool: [
        { id: 'EFFICIENCY', name: 'Efficiency', maxLevel: 5, icon: '⚡' },
        { id: 'SILK_TOUCH', name: 'Silk Touch', maxLevel: 1, icon: '🧵' },
        { id: 'FORTUNE', name: 'Fortune', maxLevel: 3, icon: '🍀' },
        { id: 'UNBREAKING', name: 'Unbreaking', maxLevel: 3, icon: '💎' },
        { id: 'MENDING', name: 'Mending', maxLevel: 1, icon: '✨' }
    ],
    // Angelrute
    fishingrod: [
        { id: 'LUCK_OF_THE_SEA', name: 'Luck of the Sea', maxLevel: 3, icon: '🌊' },
        { id: 'LURE', name: 'Lure', maxLevel: 3, icon: '🎣' },
        { id: 'UNBREAKING', name: 'Unbreaking', maxLevel: 3, icon: '💎' },
        { id: 'MENDING', name: 'Mending', maxLevel: 1, icon: '✨' }
    ]
};

// Bestimmt den Enchantment-Typ basierend auf Item-Namen
function getEnchantmentCategory(itemName) {
    if (!itemName) return null;
    const name = itemName.toUpperCase();
    
    if (name.includes('HELMET') || name.includes('CAP')) return 'helmet';
    if (name.includes('CHESTPLATE') || name.includes('TUNIC')) return 'armor';
    if (name.includes('LEGGINGS') || name.includes('PANTS')) return 'armor';
    if (name.includes('BOOTS')) return 'boots';
    if (name.includes('SWORD')) return 'sword';
    if (name.includes('AXE')) return 'axe';
    if (name.includes('BOW') && !name.includes('CROSSBOW')) return 'bow';
    if (name.includes('CROSSBOW')) return 'crossbow';
    if (name.includes('TRIDENT')) return 'trident';
    if (name.includes('PICKAXE') || name.includes('SHOVEL') || name.includes('HOE')) return 'tool';
    if (name.includes('FISHING_ROD')) return 'fishingrod';
    
    return null;
}

// Gibt verfügbare Enchantments für ein Item zurück
function getAvailableEnchantments(itemName) {
    const category = getEnchantmentCategory(itemName);
    if (!category) return [];
    
    let enchants = [];
    
    // Basis-Enchantments für die Kategorie
    if (MINECRAFT_ENCHANTMENTS[category]) {
        enchants = [...MINECRAFT_ENCHANTMENTS[category]];
    }
    
    // Bei Rüstung: Auch allgemeine Armor-Enchantments hinzufügen
    if (['helmet', 'boots'].includes(category)) {
        enchants = [...MINECRAFT_ENCHANTMENTS.armor, ...enchants];
    }
    
    return enchants;
}

// Items die nicht stackbar sind (max 1)
const UNSTACKABLE_ITEMS = [
    // Waffen
    'WOODEN_SWORD', 'STONE_SWORD', 'IRON_SWORD', 'GOLDEN_SWORD', 'DIAMOND_SWORD', 'NETHERITE_SWORD',
    'WOODEN_AXE', 'STONE_AXE', 'IRON_AXE', 'GOLDEN_AXE', 'DIAMOND_AXE', 'NETHERITE_AXE',
    'BOW', 'CROSSBOW', 'TRIDENT',
    // Werkzeuge
    'WOODEN_PICKAXE', 'STONE_PICKAXE', 'IRON_PICKAXE', 'GOLDEN_PICKAXE', 'DIAMOND_PICKAXE', 'NETHERITE_PICKAXE',
    'WOODEN_SHOVEL', 'STONE_SHOVEL', 'IRON_SHOVEL', 'GOLDEN_SHOVEL', 'DIAMOND_SHOVEL', 'NETHERITE_SHOVEL',
    'WOODEN_HOE', 'STONE_HOE', 'IRON_HOE', 'GOLDEN_HOE', 'DIAMOND_HOE', 'NETHERITE_HOE',
    'FISHING_ROD', 'FLINT_AND_STEEL', 'SHEARS',
    // Rüstung
    'LEATHER_HELMET', 'CHAINMAIL_HELMET', 'IRON_HELMET', 'GOLDEN_HELMET', 'DIAMOND_HELMET', 'NETHERITE_HELMET', 'TURTLE_HELMET',
    'LEATHER_CHESTPLATE', 'CHAINMAIL_CHESTPLATE', 'IRON_CHESTPLATE', 'GOLDEN_CHESTPLATE', 'DIAMOND_CHESTPLATE', 'NETHERITE_CHESTPLATE', 'ELYTRA',
    'LEATHER_LEGGINGS', 'CHAINMAIL_LEGGINGS', 'IRON_LEGGINGS', 'GOLDEN_LEGGINGS', 'DIAMOND_LEGGINGS', 'NETHERITE_LEGGINGS',
    'LEATHER_BOOTS', 'CHAINMAIL_BOOTS', 'IRON_BOOTS', 'GOLDEN_BOOTS', 'DIAMOND_BOOTS', 'NETHERITE_BOOTS',
    // Sonstige
    'SHIELD', 'TOTEM_OF_UNDYING', 'ELYTRA', 'CARROT_ON_A_STICK', 'WARPED_FUNGUS_ON_A_STICK'
];

// Prüft ob Item stackbar ist
function isItemStackable(itemName) {
    return !UNSTACKABLE_ITEMS.includes(itemName?.toUpperCase());
}

// Gibt max Stack-Größe zurück
function getMaxStackSize(itemName) {
    if (!isItemStackable(itemName)) return 1;
    // Ender Pearls, Eier, etc. stacken nur bis 16
    const STACK_16 = ['ENDER_PEARL', 'SNOWBALL', 'EGG', 'HONEY_BOTTLE', 'ENDER_EYE'];
    if (STACK_16.includes(itemName?.toUpperCase())) return 16;
    return 64;
}

// Globale Editing-Variablen
var currentEditingEvent = null;
var currentEditingWorld = null;
var currentEditingEquipment = null;
var currentEditingSlot = null;
var selectedArmorSlot = null;

// Drag & Drop Variablen
var draggedItem = null;
var dragSourceSlot = null;

function createNewEvent() {
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
            'lobby-world': 'EventLobby',
            'event-world': 'EventWorld',
            'build-allowed': false,
            'regenerate-event-world': true,
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
            start: '&e&lEvent startet!',
            winner: '&6&l{player} gewinnt!',
            eliminated: '&7{player} wurde eliminiert!',
            objective: '&7Ziel: Event gewinnen'
        }
    };
    openEventEditor(currentEditingEvent);
}

function editEvent(eventId) {
    const event = CONFIG_STATE.config?.events?.[eventId];
    if (!event) {
        showToast('Event nicht gefunden', 'error');
        return;
    }
    
    currentEditingEvent = JSON.parse(JSON.stringify(event));
    currentEditingEvent.id = eventId;
    openEventEditor(currentEditingEvent);
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
    eventConfig.worlds['lobby-world'] = eventConfig.worlds['lobby-world'] || 'EventLobby';
    eventConfig.worlds['event-world'] = eventConfig.worlds['event-world'] || 'EventWorld';
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
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.lobbyWorld')}</label>
                        <input type="text" class="form-control" value="${eventConfig.worlds['lobby-world']}"
                               onchange="currentEditingEvent.worlds['lobby-world'] = this.value">
                    </div>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.eventWorld')}</label>
                        <input type="text" class="form-control" value="${eventConfig.worlds['event-world']}"
                               onchange="currentEditingEvent.worlds['event-world'] = this.value">
                    </div>
                    <div class="toggle-wrapper">
                        <div class="toggle-label">
                            <span>${i18n.t('editor.buildAllowed')}</span>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" ${eventConfig.worlds['build-allowed'] !== false ? 'checked' : ''}
                                   onchange="currentEditingEvent.worlds['build-allowed'] = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>
                    <div class="toggle-wrapper">
                        <div class="toggle-label">
                            <span>${i18n.t('editor.regenerateEventWorld')}</span>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" ${eventConfig.worlds['regenerate-event-world'] !== false ? 'checked' : ''}
                                   onchange="currentEditingEvent.worlds['regenerate-event-world'] = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.cloneSource')}</label>
                        <input type="text" class="form-control" value="${eventConfig.worlds['clone-source-event-world'] || ''}"
                               onchange="currentEditingEvent.worlds['clone-source-event-world'] = this.value"
                               placeholder="${i18n.t('editor.cloneSourcePlaceholder')}">
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
                               placeholder="&e&lEvent startet!">
                        <small class="form-help">${i18n.t('editor.messageStartHelp')}</small>
                    </div>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.messageWinner')}</label>
                        <input type="text" class="form-control" value="${eventConfig.messages?.winner || ''}"
                               onchange="if (!currentEditingEvent.messages) currentEditingEvent.messages = {}; currentEditingEvent.messages.winner = this.value"
                               placeholder="&6&l{player} gewinnt!">
                        <small class="form-help">${i18n.t('editor.messageWinnerHelp')}</small>
                    </div>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.messageEliminated')}</label>
                        <input type="text" class="form-control" value="${eventConfig.messages?.eliminated || ''}"
                               onchange="if (!currentEditingEvent.messages) currentEditingEvent.messages = {}; currentEditingEvent.messages.eliminated = this.value"
                               placeholder="&7{player} wurde eliminiert!">
                        <small class="form-help">${i18n.t('editor.messageEliminatedHelp')}</small>
                    </div>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.messageObjective')}</label>
                        <input type="text" class="form-control" value="${eventConfig.messages?.objective || ''}"
                               onchange="if (!currentEditingEvent.messages) currentEditingEvent.messages = {}; currentEditingEvent.messages.objective = this.value"
                               placeholder="&7Ziel: Event gewinnen">
                        <small class="form-help">${i18n.t('editor.messageObjectiveHelp')}</small>
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

    CONFIG_STATE.config.events = CONFIG_STATE.config.events || {};
    const eventId = currentEditingEvent.id;
    CONFIG_STATE.config.events[eventId] = JSON.parse(JSON.stringify(currentEditingEvent));
    
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

function createNewWorld() {
    currentEditingWorld = {
        id: '',
        'display-name': 'Neue Welt',
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
    openWorldEditor(currentEditingWorld, true);
}

function editWorld(worldId) {
    const world = CONFIG_STATE.worlds.worlds?.[worldId];
    if (!world) return;
    
    currentEditingWorld = JSON.parse(JSON.stringify(world));
    currentEditingWorld.id = worldId;
    openWorldEditor(currentEditingWorld, false);
}

function openWorldEditor(worldConfig, isNew = false) {
    console.log('openWorldEditor called with:', worldConfig);
    
    // Ensure all required properties exist with defaults
    worldConfig = worldConfig || {};
    worldConfig.id = worldConfig.id || `world_${Date.now()}`;
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
                </div>

                <!-- Basic Tab -->
                <div id="world-tab-basic" class="tab-content active">
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.worldId')}</label>
                        <input type="text" class="form-control" id="world-id" value="${worldConfig.id}"
                               ${isNew ? '' : 'disabled'}
                               placeholder="z.B. pvp_arena_1"
                               onchange="currentEditingWorld.id = this.value">
                        <small style="color: var(--text-muted);">${i18n.t('editor.worldId')}</small>
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

                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.cloneSourceWorld')}</label>
                        <input type="text" class="form-control" id="world-clone" value="${worldConfig['clone-source-world'] || ''}"
                               placeholder=""
                               onchange="currentEditingWorld['clone-source-world'] = this.value">
                        <small style="color: var(--text-muted);">${i18n.t('label.templateWorld')}</small>
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
    const worldId = currentEditingWorld.id;
    const worldData = JSON.parse(JSON.stringify(currentEditingWorld));
    delete worldData.id; // ID ist der Key, nicht Teil der Daten
    
    CONFIG_STATE.worlds.worlds[worldId] = worldData;
    
    recordChange('worlds', `worlds.${worldId}`, CONFIG_STATE.worlds.worlds[worldId]);
    renderWorldsList();
    updateQuickActionsPanel();
    closeWorldEditor();
    showToast(i18n.t('worlds.saved'), 'success');
}

// ============================================
// Equipment Editor
// ============================================

// Erweiterte Item-Liste mit Kategorien
const MINECRAFT_ITEMS_EXTENDED = {
    swords: ['WOODEN_SWORD', 'STONE_SWORD', 'IRON_SWORD', 'GOLDEN_SWORD', 'DIAMOND_SWORD', 'NETHERITE_SWORD'],
    axes: ['WOODEN_AXE', 'STONE_AXE', 'IRON_AXE', 'GOLDEN_AXE', 'DIAMOND_AXE', 'NETHERITE_AXE'],
    pickaxes: ['WOODEN_PICKAXE', 'STONE_PICKAXE', 'IRON_PICKAXE', 'GOLDEN_PICKAXE', 'DIAMOND_PICKAXE', 'NETHERITE_PICKAXE'],
    helmets: ['LEATHER_HELMET', 'CHAINMAIL_HELMET', 'IRON_HELMET', 'GOLDEN_HELMET', 'DIAMOND_HELMET', 'NETHERITE_HELMET', 'TURTLE_HELMET'],
    chestplates: ['LEATHER_CHESTPLATE', 'CHAINMAIL_CHESTPLATE', 'IRON_CHESTPLATE', 'GOLDEN_CHESTPLATE', 'DIAMOND_CHESTPLATE', 'NETHERITE_CHESTPLATE', 'ELYTRA'],
    leggings: ['LEATHER_LEGGINGS', 'CHAINMAIL_LEGGINGS', 'IRON_LEGGINGS', 'GOLDEN_LEGGINGS', 'DIAMOND_LEGGINGS', 'NETHERITE_LEGGINGS'],
    boots: ['LEATHER_BOOTS', 'CHAINMAIL_BOOTS', 'IRON_BOOTS', 'GOLDEN_BOOTS', 'DIAMOND_BOOTS', 'NETHERITE_BOOTS'],
    bows: ['BOW', 'CROSSBOW', 'TRIDENT'],
    food: ['APPLE', 'GOLDEN_APPLE', 'ENCHANTED_GOLDEN_APPLE', 'BREAD', 'COOKED_BEEF', 'COOKED_PORKCHOP', 'COOKED_CHICKEN', 'COOKED_SALMON', 'COOKED_MUTTON', 'COOKED_COD', 'GOLDEN_CARROT', 'PUMPKIN_PIE'],
    potions: ['POTION', 'SPLASH_POTION', 'LINGERING_POTION'],
    utility: ['SHIELD', 'TOTEM_OF_UNDYING', 'ENDER_PEARL', 'CHORUS_FRUIT', 'FIREWORK_ROCKET'],
    projectiles: ['ARROW', 'SPECTRAL_ARROW', 'TIPPED_ARROW', 'SNOWBALL', 'EGG'],
    blocks: ['COBBLESTONE', 'DIRT', 'OAK_PLANKS', 'OBSIDIAN', 'WATER_BUCKET', 'LAVA_BUCKET', 'TNT', 'END_CRYSTAL'],
    misc: ['FISHING_ROD', 'FLINT_AND_STEEL', 'COMPASS', 'CLOCK', 'NAME_TAG', 'LEAD']
};

function createNewEquipment() {
    currentEditingEquipment = {
        id: '',
        enabled: true,
        'display-name': 'Neues Equipment-Set',
        'allowed-pvpwager-worlds': 'all',
        armor: {
            helmet: null,
            chestplate: null,
            leggings: null,
            boots: null
        },
        offhand: null,
        inventory: []
    };
    openEquipmentEditor(currentEditingEquipment, true);
}

function editEquipment(equipId) {
    const equip = CONFIG_STATE.equipment['equipment-sets']?.[equipId];
    if (!equip) return;
    
    currentEditingEquipment = JSON.parse(JSON.stringify(equip));
    currentEditingEquipment.id = equipId;
    openEquipmentEditor(currentEditingEquipment, false);
}

function openEquipmentEditor(equipConfig, isNew = false) {
    console.log('openEquipmentEditor called with:', equipConfig);
    
    // Ensure all required properties exist with defaults
    equipConfig = equipConfig || {};
    equipConfig.id = equipConfig.id || '';
    equipConfig['display-name'] = equipConfig['display-name'] || i18n.t('editor.newEquipment');
    equipConfig.enabled = equipConfig.enabled !== false;
    equipConfig['allowed-pvpwager-worlds'] = equipConfig['allowed-pvpwager-worlds'] || 'all';
    equipConfig.armor = equipConfig.armor || {};
    equipConfig.inventory = equipConfig.inventory || [];
    
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
                        <input type="text" class="form-control" id="equipment-id" value="${equipConfig.id}"
                               ${isNew ? '' : 'disabled'}
                               placeholder="z.B. diamond_pvp"
                               onchange="currentEditingEquipment.id = this.value">
                        <small style="color: var(--text-muted);">${i18n.t('editor.equipmentId')}</small>
                    </div>
                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.displayName')}</label>
                        <input type="text" class="form-control" id="equipment-name" value="${equipConfig['display-name']}"
                               onchange="currentEditingEquipment['display-name'] = this.value">
                    </div>
                    
                    <div class="toggle-wrapper">
                        <div class="toggle-label">
                            <span>${i18n.t('editor.enableEquipment')}</span>
                            <span>${i18n.t('editor.canBeUsedInEvents')}</span>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" id="equipment-enabled" ${equipConfig.enabled !== false ? 'checked' : ''}
                                   onchange="currentEditingEquipment.enabled = this.checked">
                            <span class="toggle-slider"></span>
                        </label>
                    </div>

                    <div class="form-group">
                        <label class="form-label">${i18n.t('editor.allowedPvPWorlds')}</label>
                        <select class="form-control" id="equipment-worlds"
                                onchange="currentEditingEquipment['allowed-pvpwager-worlds'] = this.value">
                            <option value="all" ${equipConfig['allowed-pvpwager-worlds'] === 'all' ? 'selected' : ''}>${i18n.t('editor.allWorlds')}</option>
                            <option value="none" ${equipConfig['allowed-pvpwager-worlds'] === 'none' ? 'selected' : ''}>${i18n.t('editor.noneEventsOnly')}</option>
                        </select>
                    </div>
                </div>

                <!-- Armor Tab -->
                <div id="equipment-tab-armor" class="tab-content">
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
                            <p style="color: var(--text-secondary); margin-bottom: 1rem;">${i18n.t('editor.searchItem')}</p>
                            <div id="armor-item-picker-inline">
                                ${renderInlineItemPicker('helmets', i18n.t('picker.helmets'))}
                                ${renderInlineItemPicker('chestplates', i18n.t('picker.chestplates'))}
                                ${renderInlineItemPicker('leggings', i18n.t('picker.leggings'))}
                                ${renderInlineItemPicker('boots', i18n.t('picker.boots'))}
                                ${renderInlineItemPicker('utility', i18n.t('picker.offhandItems'))}
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Inventory Tab -->
                <div id="equipment-tab-inventory" class="tab-content">
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
        
        // Setup drag & drop event listeners after modal is in DOM
        setupInventoryDragDropListeners();
    } catch (error) {
        console.error('Error opening equipment editor:', error);
        showToast('Error opening editor: ' + error.message, 'error');
    }
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
                <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21/assets/minecraft/textures/item/${currentItem.toLowerCase()}.png" 
                     alt="${currentItem}" 
                     onerror="this.parentElement.innerHTML='<i class=\\'fas fa-question\\'></i>'"
                     style="width: 40px; height: 40px; image-rendering: pixelated;">
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

function renderInlineItemPicker(category, label) {
    const items = MINECRAFT_ITEMS_EXTENDED[category] || [];
    return `
        <div class="collapsible" style="margin-bottom: 0.5rem;">
            <div class="collapsible-header" onclick="toggleCollapsible(this)">
                <div class="collapsible-title">
                    <span>${label}</span>
                    <span class="nav-badge">${items.length}</span>
                </div>
                <i class="fas fa-chevron-down collapsible-icon"></i>
            </div>
            <div class="collapsible-content">
                <div style="display: flex; flex-wrap: wrap; gap: 0.5rem;">
                    ${items.map(item => `
                        <div class="item-picker-item" style="width: 40px; height: 40px;" 
                             onclick="setArmorItem('${item}')" title="${item}">
                            <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21/assets/minecraft/textures/item/${item.toLowerCase()}.png" 
                                 alt="${item}" 
                                 onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2232%22 height=%2232%22><rect fill=%22%232d2d2d%22 width=%2232%22 height=%2232%22/><text x=%2250%25%22 y=%2250%25%22 dominant-baseline=%22middle%22 text-anchor=%22middle%22 fill=%22%234caf50%22 font-size=%228%22>${item.charAt(0)}</text></svg>'">
                        </div>
                    `).join('')}
                    <div class="item-picker-item" style="width: 40px; height: 40px; background: rgba(244, 67, 54, 0.2);" 
                         onclick="setArmorItem(null)" title="${i18n.t('tooltip.remove')}">
                        <i class="fas fa-times" style="color: var(--error);"></i>
                    </div>
                </div>
            </div>
        </div>
    `;
}

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
                <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21/assets/minecraft/textures/item/${item.item.toLowerCase()}.png" 
                     alt="${item.item}" 
                     draggable="false"
                     onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2232%22 height=%2232%22><rect fill=%22%23404040%22 width=%2232%22 height=%2232%22/><text x=%2250%25%22 y=%2255%25%22 dominant-baseline=%22middle%22 text-anchor=%22middle%22 fill=%22%234caf50%22 font-size=%2210%22>${item.item.charAt(0)}</text></svg>'">
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
            <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21/assets/minecraft/textures/item/${item.item.toLowerCase()}.png" 
                 alt="${item.item}" 
                 draggable="false"
                 onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2232%22 height=%2232%22><rect fill=%22%23404040%22 width=%2232%22 height=%2232%22/><text x=%2250%25%22 y=%2255%25%22 dominant-baseline=%22middle%22 text-anchor=%22middle%22 fill=%22%234caf50%22 font-size=%2210%22>${item.item.charAt(0)}</text></svg>'">
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

function renderItemCategories() {
    let html = '';
    const categoryLabels = {
        swords: i18n.t('picker.swords'),
        axes: i18n.t('picker.axes'),
        pickaxes: i18n.t('picker.pickaxes'),
        bows: i18n.t('picker.ranged'),
        food: i18n.t('picker.food'),
        potions: i18n.t('picker.potions'),
        utility: i18n.t('picker.utility'),
        projectiles: i18n.t('picker.projectiles'),
        blocks: i18n.t('picker.blocks'),
        misc: i18n.t('picker.misc')
    };
    
    for (const [category, items] of Object.entries(MINECRAFT_ITEMS_EXTENDED)) {
        if (['helmets', 'chestplates', 'leggings', 'boots'].includes(category)) continue;
        const label = categoryLabels[category] || category;
        html += `
            <div class="collapsible" style="margin-bottom: 0.5rem;">
                <div class="collapsible-header" onclick="toggleCollapsible(this)">
                    <div class="collapsible-title">
                        <span>${label}</span>
                        <span class="nav-badge">${items.length}</span>
                    </div>
                    <i class="fas fa-chevron-down collapsible-icon"></i>
                </div>
                <div class="collapsible-content">
                    <div style="display: flex; flex-wrap: wrap; gap: 0.5rem;">
                        ${items.map(item => `
                            <div class="item-picker-item item-source" 
                                 style="width: 36px; height: 36px;" 
                                 draggable="true"
                                 data-item="${item}"
                                 onclick="addItemToInventory('${item}')" 
                                 title="${item} - Klicken oder ins Inventar ziehen">
                                <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21/assets/minecraft/textures/item/${item.toLowerCase()}.png" 
                                     alt="${item}" 
                                     draggable="false"
                                     onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2228%22 height=%2228%22><rect fill=%22%232d2d2d%22 width=%2228%22 height=%2228%22/><text x=%2250%25%22 y=%2250%25%22 dominant-baseline=%22middle%22 text-anchor=%22middle%22 fill=%22%234caf50%22 font-size=%226%22>${item.charAt(0)}</text></svg>'">
                            </div>
                        `).join('')}
                    </div>
                </div>
            </div>
        `;
    }
    return html;
}

// Mapping von Item zu Slot-Typ
function getArmorSlotType(itemName) {
    if (!itemName) return null;
    const name = itemName.toUpperCase();
    if (name.includes('HELMET') || name.includes('CAP') || name.includes('HEAD')) return 'helmet';
    if (name.includes('CHESTPLATE') || name.includes('TUNIC') || name.includes('ELYTRA')) return 'chestplate';
    if (name.includes('LEGGINGS') || name.includes('PANTS')) return 'leggings';
    if (name.includes('BOOTS')) return 'boots';
    // Shield und andere Items für Offhand
    if (name.includes('SHIELD') || name.includes('TOTEM') || name.includes('MAP') || name.includes('ARROW')) return 'offhand';
    return null;
}

// Prüft ob Item zum Slot passt
function isItemValidForSlot(itemName, slotType) {
    const itemSlotType = getArmorSlotType(itemName);
    return itemSlotType === slotType || itemSlotType === null; // null = universell oder nicht-Armor
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
    // Auto-detect slot type if item is armor
    const autoSlot = getArmorSlotType(itemName);
    
    if (autoSlot && autoSlot !== selectedArmorSlot) {
        // Auto-select the correct slot
        selectedArmorSlot = autoSlot;
        selectArmorSlot(autoSlot);
    }
    
    if (!selectedArmorSlot) {
        showToast('Wähle zuerst einen Rüstungsslot aus', 'warning');
        return;
    }
    
    // Validiere ob Item zum Slot passt
    if (itemName && !isItemValidForSlot(itemName, selectedArmorSlot)) {
        const slotLabels = {
            helmet: 'Helm-Slot',
            chestplate: 'Brustpanzer-Slot',
            leggings: 'Hosen-Slot',
            boots: 'Stiefel-Slot',
            offhand: 'Offhand-Slot'
        };
        showToast(`${itemName} passt nicht in den ${slotLabels[selectedArmorSlot]}!`, 'error');
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
                <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21/assets/minecraft/textures/item/${itemName.toLowerCase()}.png" 
                     alt="${itemName}" 
                     onerror="this.parentElement.innerHTML='<i class=\\'fas fa-question\\'></i>'"
                     style="width: 40px; height: 40px; image-rendering: pixelated;">
            `;
        } else {
            slot.classList.remove('filled');
            slot.innerHTML = `<i class="fas fa-${getSlotIcon(selectedArmorSlot)}" style="color: var(--text-muted);"></i>`;
        }
    }
    
    const slotLabels = {
        helmet: 'Helm',
        chestplate: 'Brustpanzer',
        leggings: 'Hosen',
        boots: 'Stiefel',
        offhand: 'Offhand'
    };
    showToast(`${slotLabels[selectedArmorSlot]}: ${itemName || 'entfernt'}`, 'success');
}

// Alias für Kompatibilität
function getArmorSlotForItem(itemName) {
    return getArmorSlotType(itemName);
}

// Auto-set armor item to correct slot based on item type
function setArmorItemAuto(itemName) {
    const slot = getArmorSlotType(itemName);
    if (slot) {
        selectedArmorSlot = slot;
        selectArmorSlot(slot);
        setArmorItem(itemName);
    } else {
        // For non-armor items, use offhand or show warning
        if (selectedArmorSlot) {
            setArmorItem(itemName);
        } else {
            showToast('Wähle zuerst einen Slot aus', 'warning');
        }
    }
}

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
    let item = null;
    let itemName = null;
    
    if (slotType === 'offhand') {
        itemName = currentEditingEquipment?.offhand;
        item = currentEditingEquipment?.offhandData || { item: itemName, enchantments: [], name: '' };
    } else {
        itemName = currentEditingEquipment?.armor?.[slotType];
        currentEditingEquipment.armorData = currentEditingEquipment.armorData || {};
        item = currentEditingEquipment.armorData[slotType] || { item: itemName, enchantments: [], name: '' };
    }
    
    if (!itemName) return;
    
    item.item = itemName;
    
    const availableEnchants = getAvailableEnchantments(itemName);
    const currentEnchants = item.enchantments || [];
    
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
                    <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21/assets/minecraft/textures/item/${itemName.toLowerCase()}.png" 
                         style="width: 24px; height: 24px; vertical-align: middle; margin-right: 8px;"
                         onerror="this.style.display='none'">
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
                    <input type="text" id="armor-custom-name" value="${item.name || ''}" 
                           placeholder="${i18n.t('label.customNamePlaceholder')}" class="form-input">
                </div>
                
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

function saveArmorEdit(slotType) {
    // Custom Name
    const nameInput = document.getElementById('armor-custom-name');
    const customName = nameInput?.value?.trim() || null;
    
    // Verzauberungen sammeln
    const enchantSelects = document.querySelectorAll('#armor-edit-modal .enchant-select');
    const enchantments = [];
    enchantSelects.forEach(select => {
        const level = parseInt(select.value);
        if (level > 0) {
            enchantments.push(`${select.dataset.enchant}:${level}`);
        }
    });
    
    // Speichern
    if (slotType === 'offhand') {
        currentEditingEquipment.offhandData = {
            item: currentEditingEquipment.offhand,
            name: customName,
            enchantments: enchantments.length > 0 ? enchantments : undefined
        };
    } else {
        currentEditingEquipment.armorData = currentEditingEquipment.armorData || {};
        currentEditingEquipment.armorData[slotType] = {
            item: currentEditingEquipment.armor[slotType],
            name: customName,
            enchantments: enchantments.length > 0 ? enchantments : undefined
        };
    }
    
    closeArmorEditModal();
    showToast(i18n.t('label.armorUpdated'), 'success');
}

function removeArmorItem(slotType) {
    if (slotType === 'offhand') {
        delete currentEditingEquipment.offhand;
        delete currentEditingEquipment.offhandData;
    } else {
        if (currentEditingEquipment.armor) {
            delete currentEditingEquipment.armor[slotType];
        }
        if (currentEditingEquipment.armorData) {
            delete currentEditingEquipment.armorData[slotType];
        }
    }
    
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
            <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21/assets/minecraft/textures/item/${item.item.toLowerCase()}.png" 
                 alt="${item.item}" 
                 draggable="false"
                 onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2232%22 height=%2232%22><rect fill=%22%23404040%22 width=%2232%22 height=%2232%22/><text x=%2250%25%22 y=%2255%25%22 dominant-baseline=%22middle%22 text-anchor=%22middle%22 fill=%22%234caf50%22 font-size=%2210%22>${item.item.charAt(0)}</text></svg>'">
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

function openItemEditModal(item, slotIndex) {
    console.log('openItemEditModal called:', { item, slotIndex });
    const availableEnchants = getAvailableEnchantments(item.item);
    console.log('Available enchants:', availableEnchants);
    const currentEnchants = item.enchantments || [];
    
    // Check if item is stackable
    const maxStack = getMaxStackSize(item.item);
    const isStackable = maxStack > 1;
    
    const modal = document.createElement('div');
    modal.className = 'modal-overlay active';
    modal.id = 'item-edit-modal';
    modal.style.zIndex = '2000';
    
    modal.innerHTML = `
        <div class="modal" style="max-width: 500px;">
            <div class="modal-header">
                <h3 class="modal-title">
                    <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21/assets/minecraft/textures/item/${item.item.toLowerCase()}.png" 
                         style="width: 24px; height: 24px; vertical-align: middle; margin-right: 8px;"
                         onerror="this.style.display='none'">
                    ${i18n.t('label.editItem', { item: item.item })}
                </h3>
                <button class="modal-close" onclick="closeItemEditModal()">
                    <i class="fas fa-times"></i>
                </button>
            </div>
            <div class="modal-body">
                <!-- Anzahl -->
                ${isStackable ? `
                <div class="form-group" style="margin-bottom: 1rem;">
                    <label>${i18n.t('label.amountMax', { max: maxStack })}</label>
                    <div style="display: flex; align-items: center; gap: 0.5rem;">
                        <input type="range" id="item-amount" min="1" max="${maxStack}" value="${Math.min(item.amount || 1, maxStack)}" 
                               style="flex: 1;" onchange="document.getElementById('item-amount-display').textContent = this.value">
                        <span id="item-amount-display" style="min-width: 30px; text-align: center;">${Math.min(item.amount || 1, maxStack)}</span>
                    </div>
                </div>
                ` : `
                <div class="form-group" style="margin-bottom: 1rem;">
                    <label>${i18n.t('label.amount')}</label>
                    <div style="color: var(--text-muted); font-style: italic;">
                        <i class="fas fa-info-circle"></i> ${i18n.t('label.notStackable')}
                    </div>
                    <input type="hidden" id="item-amount" value="1">
                </div>
                `}
                
                <!-- Custom Name -->
                <div class="form-group" style="margin-bottom: 1rem;">
                    <label>${i18n.t('label.customName')}</label>
                    <input type="text" id="item-custom-name" value="${item.name || ''}" 
                           placeholder="${i18n.t('label.customNamePlaceholder')}" class="form-input">
                </div>
                
                <!-- Verzauberungen -->
                ${availableEnchants.length > 0 ? `
                    <div class="form-group">
                        <label>${i18n.t('label.enchantments')}</label>
                        <div id="enchantments-container" style="max-height: 250px; overflow-y: auto; background: var(--background); border-radius: 8px; padding: 0.5rem;">
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

function saveItemEdit(slotIndex) {
    const item = currentEditingEquipment.inventory?.find(i => i.slot === slotIndex);
    if (!item) return;
    
    // Anzahl
    const amountInput = document.getElementById('item-amount');
    if (amountInput) {
        item.amount = parseInt(amountInput.value) || 1;
    }
    
    // Custom Name
    const nameInput = document.getElementById('item-custom-name');
    if (nameInput && nameInput.value.trim()) {
        item.name = nameInput.value.trim();
    } else {
        delete item.name;
    }
    
    // Verzauberungen sammeln
    const enchantSelects = document.querySelectorAll('.enchant-select');
    const enchantments = [];
    enchantSelects.forEach(select => {
        const level = parseInt(select.value);
        if (level > 0) {
            enchantments.push(`${select.dataset.enchant}:${level}`);
        }
    });
    
    if (enchantments.length > 0) {
        item.enchantments = enchantments;
    } else {
        delete item.enchantments;
    }
    
    updateInventorySlotUI(slotIndex);
    closeItemEditModal();
    showToast(i18n.t('label.itemUpdated'), 'success');
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

function filterEquipmentItems(searchTerm) {
    const categories = document.getElementById('equipment-item-categories');
    if (!categories) return;
    
    const term = searchTerm.toLowerCase();
    categories.querySelectorAll('.item-picker-item').forEach(item => {
        const itemName = item.getAttribute('title')?.toLowerCase() || '';
        item.style.display = itemName.includes(term) ? 'flex' : 'none';
    });
}

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

function saveEquipmentEditor() {
    if (!currentEditingEquipment) {
        showToast(i18n.t('error.fieldRequired'), 'error');
        return;
    }
    
    if (!currentEditingEquipment.id || currentEditingEquipment.id.trim() === '') {
        showToast(i18n.t('error.fieldRequired'), 'error');
        return;
    }

    CONFIG_STATE.equipment['equipment-sets'] = CONFIG_STATE.equipment['equipment-sets'] || {};
    const equipId = currentEditingEquipment.id;
    const displayName = currentEditingEquipment['display-name'] || equipId;
    const equipData = JSON.parse(JSON.stringify(currentEditingEquipment));
    delete equipData.id;
    
    CONFIG_STATE.equipment['equipment-sets'][equipId] = equipData;
    
    recordChange('equipment', `equipment-sets.${equipId}`, CONFIG_STATE.equipment['equipment-sets'][equipId]);
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
                <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21/assets/minecraft/textures/item/${(item.item || 'diamond').toLowerCase()}.png" 
                     alt="${item.item}" style="width: 24px; height: 24px; image-rendering: pixelated;"
                     onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2224%22 height=%2224%22><rect fill=%22%232d2d2d%22 width=%2224%22 height=%2224%22/></svg>'">
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
window.setArmorItemAuto = setArmorItemAuto;
window.clickArmorSlot = clickArmorSlot;
window.getArmorSlotForItem = getArmorSlotForItem;

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
