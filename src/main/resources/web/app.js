// ============================================
// Event-PVP Web Konfigurator - Hauptscript
// ============================================

// ============================================
// Internationalization (i18n) System
// ============================================

const i18n = {
    current: 'en',
    strings: {},
    availableLanguages: [],
    defaultLanguage: 'en',
    
    async loadLanguageList() {
        try {
            const response = await fetch('/lang/languages.json');
            if (response.ok) {
                const data = await response.json();
                this.availableLanguages = data.available || [];
                this.defaultLanguage = data.default || 'en';
                console.log('✓ Available languages:', this.availableLanguages.map(l => l.code).join(', '));
            }
        } catch (error) {
            console.warn('Could not load languages.json, using defaults');
            this.availableLanguages = [
                { code: 'en', name: 'English', nativeName: 'English' },
                { code: 'de', name: 'German', nativeName: 'Deutsch' }
            ];
        }
    },
    
    async init(language = 'en') {
        // Load language list first if not loaded
        if (this.availableLanguages.length === 0) {
            await this.loadLanguageList();
        }
        
        try {
            const response = await fetch(`/lang/${language}.json`);
            if (!response.ok) {
                console.warn(`Language file ${language}.json not found, trying default`);
                if (language !== this.defaultLanguage) {
                    return this.init(this.defaultLanguage);
                }
                throw new Error('Default language file not found');
            }
            this.strings = await response.json();
            this.current = language;
            console.log(`✓ Language loaded: ${language}`);
            
            // Update language selector if it exists
            this.updateLanguageSelector();
        } catch (error) {
            console.error('Error loading language file:', error);
            // Fallback: use empty strings
            this.strings = {};
        }
    },
    
    updateLanguageSelector() {
        const select = document.getElementById('settings-language');
        if (!select) return;
        
        // Clear and rebuild options
        select.innerHTML = '';
        for (const lang of this.availableLanguages) {
            const option = document.createElement('option');
            option.value = lang.code;
            option.textContent = `${lang.nativeName} (${lang.name})`;
            if (lang.code === this.current) {
                option.selected = true;
            }
            select.appendChild(option);
        }
    },
    
    t(key, replacements = {}) {
        let str = this.strings[key] || key;
        for (const [k, v] of Object.entries(replacements)) {
            str = str.replace(`{${k}}`, v);
        }
        return str;
    }
};

// Apply loaded translations to static markup
function applyTranslations() {
    document.querySelectorAll('[data-i18n]').forEach(el => {
        const key = el.dataset.i18n;
        if (key) {
            el.textContent = i18n.t(key);
        }
    });

    document.querySelectorAll('[data-i18n-html]').forEach(el => {
        const key = el.dataset.i18nHtml;
        if (key) {
            el.innerHTML = i18n.t(key);
        }
    });

    document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
        const key = el.dataset.i18nPlaceholder;
        if (key) {
            el.placeholder = i18n.t(key);
        }
    });

    document.querySelectorAll('[data-i18n-title]').forEach(el => {
        const key = el.dataset.i18nTitle;
        if (key) {
            el.title = i18n.t(key);
        }
    });
}

/**
 * Holt die Sprache vom Server (config.yml settings.language)
 * Gibt null zurück wenn nicht erreichbar
 */
async function fetchServerLanguage() {
    try {
        console.log('[Language] Fetching server language...');
        const response = await fetch('/api/language/get', {
            method: 'GET',
            credentials: 'include'
        });
        console.log('[Language] Response status:', response.status);
        if (response.ok) {
            const data = await response.json();
            console.log('[Language] Server response:', JSON.stringify(data));
            if (data && data.language) {
                console.log('[Language] Using server language:', data.language);
                return data.language;
            } else {
                console.log('[Language] No language in response, data:', data);
            }
        } else {
            console.log('[Language] Response not OK:', response.status, response.statusText);
        }
    } catch (err) {
        console.error('[Language] Error fetching server language:', err);
    }
    console.log('[Language] Returning null (will use fallback)');
    return null;
}

/**
 * Speichert die Sprache auf dem Server (config.yml)
 */
async function saveServerLanguage(lang) {
    try {
        const response = await fetch('/api/language/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ language: lang })
        });
        if (response.ok) {
            console.log('Language saved to server:', lang);
            return true;
        }
    } catch (err) {
        console.log('Could not save language to server:', err.message);
    }
    return false;
}

// Change language and reload translations
async function changeLanguage(lang) {
    try {
        const response = await fetch(`/lang/${lang}.json`);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const newTranslations = await response.json();
        i18n.strings = newTranslations;
        i18n.current = lang;
        localStorage.setItem('lang', lang);
        document.documentElement.lang = lang;
        document.title = i18n.t('app.title');
        applyTranslations();
        i18n.updateLanguageSelector();
        
        // Speichere die Sprache auch auf dem Server (config.yml)
        saveServerLanguage(lang);
        
        // Aktualisiere CONFIG_STATE für YAML Preview
        if (CONFIG_STATE.config && CONFIG_STATE.config.settings) {
            CONFIG_STATE.config.settings.language = lang;
        } else if (CONFIG_STATE.config) {
            CONFIG_STATE.config.settings = { language: lang };
        }
        
        // Re-render dynamic content
        if (typeof renderEventsList === 'function') renderEventsList();
        if (typeof renderWorldsList === 'function') renderWorldsList();
        if (typeof renderEquipmentList === 'function') renderEquipmentList();
        showToast(i18n.t('success.saved'), 'success');
    } catch (error) {
        console.error('Failed to change language:', error);
        showToast('Failed to change language', 'error');
    }
}

// Helper function to escape HTML
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// State Management
const CONFIG_STATE = {
    config: {},
    worlds: {},
    equipment: {},
    webConfig: {},
    changes: [],
    changeIndex: -1,
    lastSave: null,
    selectedItem: null,
    minecraftItems: []
};

const MINECRAFT_ITEMS = [
    // Schwerter
    'WOODEN_SWORD', 'STONE_SWORD', 'IRON_SWORD', 'GOLDEN_SWORD', 'DIAMOND_SWORD', 'NETHERITE_SWORD',
    // Äxte
    'WOODEN_AXE', 'STONE_AXE', 'IRON_AXE', 'GOLDEN_AXE', 'DIAMOND_AXE', 'NETHERITE_AXE',
    // Spitzhacken
    'WOODEN_PICKAXE', 'STONE_PICKAXE', 'IRON_PICKAXE', 'GOLDEN_PICKAXE', 'DIAMOND_PICKAXE', 'NETHERITE_PICKAXE',
    // Schaufeln
    'WOODEN_SHOVEL', 'STONE_SHOVEL', 'IRON_SHOVEL', 'GOLDEN_SHOVEL', 'DIAMOND_SHOVEL', 'NETHERITE_SHOVEL',
    // Hacken
    'WOODEN_HOE', 'STONE_HOE', 'IRON_HOE', 'GOLDEN_HOE', 'DIAMOND_HOE', 'NETHERITE_HOE',
    // Helme
    'LEATHER_HELMET', 'CHAINMAIL_HELMET', 'IRON_HELMET', 'GOLDEN_HELMET', 'DIAMOND_HELMET', 'NETHERITE_HELMET', 'TURTLE_HELMET',
    // Brustpanzer
    'LEATHER_CHESTPLATE', 'CHAINMAIL_CHESTPLATE', 'IRON_CHESTPLATE', 'GOLDEN_CHESTPLATE', 'DIAMOND_CHESTPLATE', 'NETHERITE_CHESTPLATE', 'ELYTRA',
    // Beinschutz
    'LEATHER_LEGGINGS', 'CHAINMAIL_LEGGINGS', 'IRON_LEGGINGS', 'GOLDEN_LEGGINGS', 'DIAMOND_LEGGINGS', 'NETHERITE_LEGGINGS',
    // Stiefel
    'LEATHER_BOOTS', 'CHAINMAIL_BOOTS', 'IRON_BOOTS', 'GOLDEN_BOOTS', 'DIAMOND_BOOTS', 'NETHERITE_BOOTS',
    // Fernkampf
    'BOW', 'CROSSBOW', 'TRIDENT', 'SHIELD',
    // Projektile
    'ARROW', 'SPECTRAL_ARROW', 'TIPPED_ARROW', 'FIREWORK_ROCKET',
    // Nahrung
    'APPLE', 'GOLDEN_APPLE', 'ENCHANTED_GOLDEN_APPLE', 'BREAD', 'COOKED_BEEF', 'COOKED_CHICKEN', 
    'COOKED_PORKCHOP', 'COOKED_SALMON', 'COOKED_MUTTON', 'COOKED_COD', 'GOLDEN_CARROT', 'PUMPKIN_PIE',
    'BAKED_POTATO', 'MUSHROOM_STEW', 'RABBIT_STEW', 'BEETROOT_SOUP', 'SUSPICIOUS_STEW',
    // Tränke
    'POTION', 'SPLASH_POTION', 'LINGERING_POTION',
    // Nützliches
    'ENDER_PEARL', 'TOTEM_OF_UNDYING', 'CHORUS_FRUIT', 'MILK_BUCKET',
    // Werkzeuge
    'FLINT_AND_STEEL', 'FISHING_ROD', 'SHEARS', 'LEAD', 'NAME_TAG', 'SPYGLASS',
    // Eimer
    'BUCKET', 'WATER_BUCKET', 'LAVA_BUCKET', 'POWDER_SNOW_BUCKET',
    // Ressourcen
    'DIAMOND', 'EMERALD', 'IRON_INGOT', 'GOLD_INGOT', 'NETHERITE_INGOT', 'COPPER_INGOT',
    'COAL', 'REDSTONE', 'LAPIS_LAZULI', 'QUARTZ', 'AMETHYST_SHARD',
    // Blöcke
    'COBBLESTONE', 'DIRT', 'OAK_PLANKS', 'OBSIDIAN', 'TNT', 'END_CRYSTAL',
    'RESPAWN_ANCHOR', 'CHEST', 'ENDER_CHEST', 'BARREL', 'CRAFTING_TABLE', 'FURNACE',
    'ANVIL', 'ENCHANTING_TABLE', 'BREWING_STAND', 'CAULDRON', 'SCAFFOLDING',
    // Sonstiges
    'SNOWBALL', 'EGG', 'EXPERIENCE_BOTTLE', 'COMPASS', 'CLOCK', 'RECOVERY_COMPASS',
    'BONE', 'STRING', 'SLIME_BALL', 'HONEY_BOTTLE', 'GLOW_BERRIES'
];

// ============================================
// Initialisierung
// ============================================

// Auth State
let isAuthenticated = false;
let authRequired = true;
let currentPlayer = null;

window.addEventListener('DOMContentLoaded', () => {
    console.log('=== Event-PVP Web Configurator Starting ===');
    console.log('Time:', new Date().toISOString());

    // Lade Sprache vom Server (config.yml settings.language)
    // Server-Wert hat IMMER Vorrang, damit Änderungen in der config.yml wirksam werden
    fetchServerLanguage().then(serverLang => {
        // Server-Sprache hat Vorrang! Nur Fallback auf localStorage wenn Server nicht erreichbar
        let lang;
        if (serverLang) {
            lang = serverLang;
            // Überschreibe localStorage mit Server-Wert
            localStorage.setItem('lang', serverLang);
            console.log('Using server language:', serverLang);
        } else {
            lang = localStorage.getItem('lang') || 'en';
            console.log('Server not reachable, using localStorage/default:', lang);
        }
        
        return i18n.init(lang);
    }).then(() => {
        document.title = i18n.t('app.title');
        document.documentElement.lang = i18n.current;
        applyTranslations();
        // Set language selector to current language
        const langSelect = document.getElementById('settings-language');
        if (langSelect) langSelect.value = i18n.current;
    }).catch(err => {
        console.error('Language initialization failed:', err);
        // Fallback: trotzdem mit Default-Sprache starten
        i18n.init('en').then(() => applyTranslations());
    });
    
    try {
        // Setup Event Listeners zuerst
        setupEventListeners();
        console.log('Event listeners setup complete');
        
        // Prüfe ob wir im Browser laufen oder von Server bedient werden
        const isServedFromServer = window.location.protocol !== 'file:';
        
        console.log('Protocol:', window.location.protocol, 'Is served from server:', isServedFromServer);
        
        if (isServedFromServer) {
            // Auth-Check zuerst
            checkAuthentication().then(authStatus => {
                if (authStatus.authenticated || !authStatus.authRequired) {
                    // Authentifiziert oder keine Auth nötig
                    isAuthenticated = true;
                    authRequired = authStatus.authRequired;
                    currentPlayer = authStatus.playerName;
                    
                    hideLoginScreen();
                    initializeApp();
                } else {
                    // Login erforderlich
                    authRequired = true;
                    showLoginScreen();
                }
            }).catch(err => {
                console.error('Auth check failed:', err);
                // Bei Fehler: Zeige Login-Screen
                showLoginScreen();
            });
        } else {
            console.log('Running locally - loading demo data');
            hideLoginScreen();
            loadDemoData();
            updateConnectionStatus('active', i18n.t('status.demoMode'));
            showSection('settings');
        }
    } catch (error) {
        console.error('FATAL ERROR during initialization:', error);
        updateConnectionStatus('inactive', i18n.t('status.initError'));
    }
    
    loadThemeFromConfig();
});

// ============================================
// Authentication Functions
// ============================================

/**
 * Prüft den Authentifizierungsstatus beim Server
 */
async function checkAuthentication() {
    try {
        const response = await fetch('/api/auth/validate', {
            method: 'GET',
            credentials: 'include' // Cookies mitsenden
        });
        
        if (response.ok) {
            const data = await response.json();
            return {
                authenticated: data.authenticated === true,
                authRequired: data.authRequired !== false,
                playerName: data.playerName || null
            };
        } else {
            return {
                authenticated: false,
                authRequired: true,
                playerName: null
            };
        }
    } catch (error) {
        console.error('Auth check error:', error);
        return {
            authenticated: false,
            authRequired: true,
            playerName: null
        };
    }
}

/**
 * Zeigt den Login-Screen an
 */
function showLoginScreen() {
    const loginScreen = document.getElementById('login-screen');
    const mainContent = document.getElementById('main-content');
    
    if (loginScreen) {
        loginScreen.style.display = 'flex';
    }
    if (mainContent) {
        mainContent.style.display = 'none';
    }
    
    // Focus auf Token-Input
    const tokenInput = document.getElementById('login-token');
    if (tokenInput) {
        tokenInput.focus();
    }
}

/**
 * Versteckt den Login-Screen
 */
function hideLoginScreen() {
    const loginScreen = document.getElementById('login-screen');
    const mainContent = document.getElementById('main-content');
    
    if (loginScreen) {
        loginScreen.style.display = 'none';
    }
    if (mainContent) {
        mainContent.style.display = 'flex';
    }
}

/**
 * Führt den Login mit Token durch
 */
async function performLogin() {
    const tokenInput = document.getElementById('login-token');
    const loginError = document.getElementById('login-error');
    const loginButton = document.getElementById('login-button');
    
    if (!tokenInput) return;
    
    const token = tokenInput.value.trim();
    
    if (!token) {
        showLoginError(i18n.t('auth.tokenRequired'));
        return;
    }
    
    // Button deaktivieren während Login
    if (loginButton) {
        loginButton.disabled = true;
        loginButton.textContent = i18n.t('auth.authenticating');
    }
    
    try {
        const response = await fetch('/api/auth/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify({ token: token })
        });
        
        const data = await response.json();
        
        if (data.success) {
            // Login erfolgreich
            isAuthenticated = true;
            currentPlayer = data.playerName;
            
            // Token-Input leeren
            tokenInput.value = '';
            hideLoginError();
            
            // Zum Hauptinhalt wechseln
            hideLoginScreen();
            initializeApp();
            
            showToast(i18n.t('auth.welcome', { player: currentPlayer || 'Admin' }), 'success');
        } else {
            showLoginError(data.error || i18n.t('auth.invalidToken'));
        }
    } catch (error) {
        console.error('Login error:', error);
        showLoginError(i18n.t('auth.connectionError'));
    } finally {
        if (loginButton) {
            loginButton.disabled = false;
            loginButton.textContent = i18n.t('auth.login');
        }
    }
}

/**
 * Zeigt eine Login-Fehlermeldung an
 */
function showLoginError(message) {
    const loginError = document.getElementById('login-error');
    if (loginError) {
        loginError.textContent = message;
        loginError.style.display = 'block';
    }
}

/**
 * Versteckt die Login-Fehlermeldung
 */
function hideLoginError() {
    const loginError = document.getElementById('login-error');
    if (loginError) {
        loginError.style.display = 'none';
    }
}

/**
 * Führt den Logout durch
 */
async function performLogout() {
    try {
        await fetch('/api/auth/logout', {
            method: 'POST',
            credentials: 'include'
        });
    } catch (error) {
        console.error('Logout error:', error);
    }
    
    isAuthenticated = false;
    currentPlayer = null;
    showLoginScreen();
    showToast(i18n.t('auth.loggedOut'), 'info');
}

/**
 * Initialisiert die App nach erfolgreicher Authentifizierung
 */
async function initializeApp() {
    console.log('Initializing app after authentication...');
    
    // Initiale Sektion anzeigen
    showSection('settings');
    
    // Verbindungsstatus auf "Verbinde" setzen
    updateConnectionStatus('connecting', i18n.t('status.connecting'));
    
    // Anzeigen wer eingeloggt ist
    updateUserDisplay();
    
    // Configs laden mit Timeout
    const loadTimeout = setTimeout(() => {
        console.warn('Config loading timeout - might have failed');
        updateConnectionStatus('inactive', i18n.t('status.timeout'));
    }, 10000);
    
    try {
        await loadAllConfigs();
        clearTimeout(loadTimeout);
        console.log('Configs loaded successfully');
        
        // NICHT die Sprache aus der geladenen Config überschreiben!
        // Die Sprache wurde bereits via fetchServerLanguage() vom /api/language/get Endpoint geladen
        // und ist aktueller als CONFIG_STATE.config.settings.language
        // Nur i18n aktualisieren falls noch nicht initialisiert
        if (!i18n.current) {
            const language = CONFIG_STATE.config?.settings?.language || 'en';
            await i18n.init(language);
        }
        document.title = i18n.t('app.title');
        document.documentElement.lang = i18n.current;
        applyTranslations();
        
        // Update CONFIG_STATE mit der aktuellen i18n Sprache für YAML Preview Konsistenz
        if (CONFIG_STATE.config && CONFIG_STATE.config.settings) {
            CONFIG_STATE.config.settings.language = i18n.current;
        }
        
        // Sofort Server-Status prüfen nach dem Laden
        await checkServerStatus();
    } catch (err) {
        clearTimeout(loadTimeout);
        console.error('Failed to load configs:', err);
        updateConnectionStatus('inactive', i18n.t('status.error', { message: err.message || '' }));
    }
    
    // Regelmäßig Status prüfen (alle 60 Sekunden)
    setInterval(checkServerStatus, 60000);
}

/**
 * Aktualisiert die User-Anzeige im Header
 */
function updateUserDisplay() {
    const userDisplay = document.getElementById('user-display');
    if (userDisplay && currentPlayer) {
        userDisplay.innerHTML = `
            <span class="user-name">👤 ${escapeHtml(currentPlayer)}</span>
            <button class="logout-btn" onclick="performLogout()" title="${i18n.t('nav.logout')}">
                <span>🚪</span>
            </button>
        `;
        userDisplay.style.display = 'flex';
    }
}

// Token-Input: Enter-Taste
document.addEventListener('keydown', function(e) {
    if (e.key === 'Enter') {
        const loginScreen = document.getElementById('login-screen');
        if (loginScreen && loginScreen.style.display !== 'none') {
            performLogin();
        }
    }
});

// Window-Funktionen für onclick
window.performLogin = performLogin;
window.performLogout = performLogout;

// Demo-Daten für lokales Testing
function loadDemoData() {
    CONFIG_STATE.config = {
        settings: { prefix: '&6[Event]&r', 'main-world': 'world' },
        events: {
            pvparena: {
                enabled: true,
                command: 'pvparena',
                'display-name': '&c&lPvP Arena',
                description: '&7Kämpfe bis zum letzten Mann!',
                'min-players': 2,
                'max-players': 16
            }
        }
    };
    CONFIG_STATE.worlds = {
        worlds: {
            arena_world: { 'display-name': '&bBeispiel-Arena', 'pvpwager-world-enable': true }
        }
    };
    CONFIG_STATE.equipment = {
        'equipment-sets': {
            pvp_starter: { enabled: true, 'display-name': '&aStarter PvP' }
        }
    };
    
    populateSettingsForm();
    renderEventsList();
    renderWorldsList();
    renderEquipmentList();
    showToast(i18n.t('demo.loaded'), 'info');
}

window.addEventListener('beforeunload', (e) => {
    if (hasUnsavedChanges()) {
        e.preventDefault();
        e.returnValue = '';
    }
});

// ============================================
// Config Loading & Saving
// ============================================

async function loadAllConfigs() {
    console.log('=== loadAllConfigs starting ===');
    try {
        updateConnectionStatus('connecting', i18n.t('status.loadingConfigs'));
        showLoading(true);
        
        console.log('Fetching configs from API...');
        
        const [configResp, worldsResp, equipResp, webResp] = await Promise.all([
            fetch('/api/config/get').catch(e => { console.error('config fetch error:', e); return null; }),
            fetch('/api/worlds/get').catch(e => { console.error('worlds fetch error:', e); return null; }),
            fetch('/api/equipment/get').catch(e => { console.error('equipment fetch error:', e); return null; }),
            fetch('/api/webconfig/get').catch(e => { console.error('webconfig fetch error:', e); return null; })
        ]);

        console.log('API responses received:', {
            config: configResp?.status,
            worlds: worldsResp?.status,
            equipment: equipResp?.status,
            webconfig: webResp?.status
        });

        // API gibt {success: true, data: {...}} zurück - wir brauchen data
        if (configResp && configResp.ok) {
            const configJson = await configResp.json();
            CONFIG_STATE.config = configJson.data || configJson;
            console.log('Config loaded:', Object.keys(CONFIG_STATE.config));
        } else {
            console.warn('Config response not OK or null');
        }
        
        if (worldsResp && worldsResp.ok) {
            const worldsJson = await worldsResp.json();
            CONFIG_STATE.worlds = worldsJson.data || worldsJson;
            console.log('Worlds loaded:', Object.keys(CONFIG_STATE.worlds));
        } else {
            console.warn('Worlds response not OK or null');
        }
        
        if (equipResp && equipResp.ok) {
            const equipJson = await equipResp.json();
            CONFIG_STATE.equipment = equipJson.data || equipJson;
            console.log('Equipment loaded:', Object.keys(CONFIG_STATE.equipment));
        } else {
            console.warn('Equipment response not OK or null');
        }
        
        if (webResp && webResp.ok) {
            const webJson = await webResp.json();
            CONFIG_STATE.webConfig = webJson.data || webJson;
            console.log('WebConfig loaded:', Object.keys(CONFIG_STATE.webConfig));
        } else {
            console.warn('WebConfig response not OK or null');
        }

        // Backup für Undo/Redo speichern
        localStorage.setItem('config_backup', JSON.stringify(CONFIG_STATE.config));
        localStorage.setItem('worlds_backup', JSON.stringify(CONFIG_STATE.worlds));
        localStorage.setItem('equipment_backup', JSON.stringify(CONFIG_STATE.equipment));
        localStorage.setItem('webconfig_backup', JSON.stringify(CONFIG_STATE.webConfig));

        console.log('Rendering UI components...');
        populateSettingsForm();
        renderEventsList();
        renderWorldsList();
        renderEquipmentList();
        
        showLoading(false);
        updateConnectionStatus('active', i18n.t('status.connected'));
        showToast(i18n.t('success.loadedConfigs'), 'success');
        console.log('=== loadAllConfigs completed successfully ===');
    } catch (error) {
        console.error('Error loading configs:', error);
        console.error('Stack:', error.stack);
        showLoading(false);
        updateConnectionStatus('inactive', i18n.t('auth.connectionError'));
        showToast(i18n.t('error.loadFailedWithReason', { message: error.message || '' }), 'error');
    }
}

function updateConnectionStatus(status, text) {
    const dot = document.getElementById('status-dot');
    const textEl = document.getElementById('status-text');
    
    if (dot) {
        dot.className = 'status-dot ' + status;
    }
    if (textEl) {
        textEl.textContent = text;
    }
}

async function saveAllConfigs() {
    try {
        showLoading(true);
        
        const promises = [];
        
        console.log('[Save] Prüfe Änderungen...');
        console.log('[Save] CONFIG_STATE.config:', CONFIG_STATE.config);
        console.log('[Save] CONFIG_STATE.equipment:', CONFIG_STATE.equipment);
        
        if (hasConfigChanged('settings')) {
            console.log('[Save] Speichere config...');
            promises.push(
                fetch('/api/config/save', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ data: CONFIG_STATE.config })
                }).then(r => { console.log('[Save] config response:', r.status); return r; })
            );
        }

        if (hasConfigChanged('worlds')) {
            console.log('[Save] Speichere worlds...');
            promises.push(
                fetch('/api/worlds/save', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ data: CONFIG_STATE.worlds })
                }).then(r => { console.log('[Save] worlds response:', r.status); return r; })
            );
        }

        if (hasConfigChanged('equipment')) {
            console.log('[Save] Speichere equipment...');
            promises.push(
                fetch('/api/equipment/save', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ data: CONFIG_STATE.equipment })
                }).then(r => { console.log('[Save] equipment response:', r.status); return r; })
            );
        }

        if (hasConfigChanged('web')) {
            console.log('[Save] Speichere webconfig...');
            promises.push(
                fetch('/api/webconfig/save', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ data: CONFIG_STATE.webConfig })
                }).then(r => { console.log('[Save] webconfig response:', r.status); return r; })
            );
        }

        if (promises.length === 0) {
            showToast(i18n.t('info.noChanges'), 'info');
            showLoading(false);
            return;
        }
        
        console.log('[Save] Sende', promises.length, 'Save-Requests...');

        const results = await Promise.all(promises);
        const allSuccess = results.every(r => r.ok);

        if (allSuccess) {
            // Backups aktualisieren
            localStorage.setItem('config_backup', JSON.stringify(CONFIG_STATE.config));
            localStorage.setItem('worlds_backup', JSON.stringify(CONFIG_STATE.worlds));
            localStorage.setItem('equipment_backup', JSON.stringify(CONFIG_STATE.equipment));
            localStorage.setItem('webconfig_backup', JSON.stringify(CONFIG_STATE.webConfig));
            
            CONFIG_STATE.changes = [];
            CONFIG_STATE.changeIndex = -1;
            CONFIG_STATE.lastSave = new Date();
            updateQuickActionsPanel();
            showToast(i18n.t('success.savedAll'), 'success');
        } else {
            showToast(i18n.t('error.savePartial'), 'error');
        }

        showLoading(false);
    } catch (error) {
        console.error('Error saving configs:', error);
        showLoading(false);
        showToast(i18n.t('error.saveFailed'), 'error');
    }
}

// ============================================
// Navigation & UI
// ============================================

function showSection(sectionName) {
    console.log('showSection called with:', sectionName);
    
    // Get all sections and nav items
    const allSections = document.querySelectorAll('.section');
    const allNavItems = document.querySelectorAll('.nav-item[data-section]');
    
    console.log('Found sections:', allSections.length);
    
    // CRITICAL: Remove active class from ALL sections
    allSections.forEach(s => {
        s.classList.remove('active');
        // Double-ensure hidden state
        s.setAttribute('data-active', 'false');
    });
    
    // Remove active from all nav items
    allNavItems.forEach(n => n.classList.remove('active'));

    // Show selected section
    const section = document.getElementById(`section-${sectionName}`);
    if (section) {
        section.classList.add('active');
        section.setAttribute('data-active', 'true');
        console.log('Section activated:', sectionName);
    } else {
        console.error('Section not found: section-' + sectionName);
        // Fallback: show settings
        const fallback = document.getElementById('section-settings');
        if (fallback) {
            fallback.classList.add('active');
            fallback.setAttribute('data-active', 'true');
        }
    }

    // Mark nav item as active
    const navItem = document.querySelector(`.nav-item[data-section="${sectionName}"]`);
    if (navItem) {
        navItem.classList.add('active');
    }

    // Scroll content to top
    const content = document.querySelector('.content');
    if (content) {
        content.scrollTop = 0;
    }
}

function setupEventListeners() {
    // Navigation Items Click Handler
    document.querySelectorAll('.nav-item[data-section]').forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            const section = item.getAttribute('data-section');
            if (section) {
                showSection(section);
            }
        });
    });
    
    // Item search
    const itemSearch = document.getElementById('item-search');
    if (itemSearch) {
        itemSearch.addEventListener('keyup', (e) => {
            filterItems(e.target.value);
        });
    }
    
    console.log('Event listeners setup complete');
}

// ============================================
// Settings Form Management
// ============================================

function populateSettingsForm() {
    const settings = CONFIG_STATE.config?.settings || {};
    console.log('Populating settings form with:', settings);

    // Sichere Element-Zuweisung mit Fallback
    const setValueSafe = (id, value, defaultValue = '') => {
        const el = document.getElementById(id);
        if (el) {
            if (el.type === 'checkbox') {
                el.checked = value !== false && value !== undefined;
            } else {
                el.value = value !== undefined && value !== null ? value : defaultValue;
            }
        }
    };

    setValueSafe('settings-prefix', settings.prefix, '&6[Event]&r');
    setValueSafe('settings-main-world', settings['main-world'], 'world');
    setValueSafe('settings-save-location', settings['save-player-location'], true);
    setValueSafe('settings-join-phase', settings['join-phase-duration'], 30);
    setValueSafe('settings-lobby-countdown', settings['lobby-countdown'], 10);
    
    const snapshots = settings['inventory-snapshots'] || {};
    setValueSafe('settings-snapshots-enabled', snapshots.enabled, true);
    setValueSafe('settings-snapshot-group', snapshots['default-group'], 'default');
    setValueSafe('settings-retain-days', snapshots['retain-days'], 30);

    setValueSafe('settings-world-loading', settings['world-loading'], 'both');
    setValueSafe('settings-command-restriction', settings['command-restriction'], 'both');

    const spectators = settings.spectators || {};
    setValueSafe('settings-spectators-enabled', spectators.enabled, true);
    setValueSafe('settings-max-spectators', spectators['max-spectators'], 10);
    setValueSafe('settings-announce-join', spectators['announce-join'], true);

    const match = settings.match || {};
    setValueSafe('settings-match-countdown', match['countdown-time'], 10);
    setValueSafe('settings-match-duration', match['max-duration'], 600);
    setValueSafe('settings-allow-no-wager', match['allow-no-wager'], true);

    const regen = settings['arena-regeneration'] || {};
    setValueSafe('settings-arena-backups', regen.backups, true);
    setValueSafe('settings-backup-async', regen['backup-async'], true);

    const checks = settings.checks || {};
    setValueSafe('settings-check-inventory', checks['inventory-space'], true);
    setValueSafe('settings-min-bet', checks['minimum-bet-money'], 10);
    setValueSafe('settings-max-bet', checks['max-bet-money'], 100000);

    const autoEvents = settings['auto-events'] || {};
    setValueSafe('settings-auto-events-enabled', autoEvents.enabled, false);
    setValueSafe('settings-auto-events-interval-min', autoEvents['interval-min'], 1800);
    setValueSafe('settings-auto-events-interval-max', autoEvents['interval-max'], 3600);
    setValueSafe('settings-auto-events-check-players', autoEvents['check-online-players'], true);
    
    // Set dropdown mode based on random-selection value
    const modeSelect = document.getElementById('settings-auto-events-mode');
    if (modeSelect) {
        modeSelect.value = autoEvents['random-selection'] === false ? 'sequential' : 'random';
    }
    
    // Load selected events
    const selectedEvents = autoEvents['selected-events'] || [];
    if (Array.isArray(selectedEvents)) {
        CONFIG_STATE.autoEventsSelectedEvents = selectedEvents;
    } else {
        CONFIG_STATE.autoEventsSelectedEvents = [];
    }
    
    // Show/hide auto-event settings based on enabled state
    toggleAutoEventSettings();
    updateAutoEventsSelectionList();
    populateAutoEventsDropdown();
}

function toggleAutoEventSettings() {
    const enabled = document.getElementById('settings-auto-events-enabled')?.checked || false;
    const settingsPanel = document.getElementById('auto-event-settings');
    if (settingsPanel) {
        settingsPanel.style.display = enabled ? 'block' : 'none';
    }
}

function toggleEventSelectionMode() {
    const modeSelect = document.getElementById('settings-auto-events-mode');
    const isRandom = modeSelect ? modeSelect.value === 'random' : true;
    const hint = document.getElementById('event-selection-mode-hint');
    if (hint) {
        if (isRandom) {
            hint.textContent = i18n.t('settings.autoEventsSelectedEventsHintRandom');
        } else {
            hint.textContent = i18n.t('settings.autoEventsSelectedEventsHintSequential');
        }
    }
    // Only update list if called from mode change, not from within updateAutoEventsSelectionList
    if (document.activeElement && document.activeElement.id === 'settings-auto-events-mode') {
        updateAutoEventsSelectionList();
    }
}

function populateAutoEventsDropdown() {
    const select = document.getElementById('auto-events-add-select');
    if (!select) return;
    
    // Clear existing options except first
    while (select.options.length > 1) {
        select.remove(1);
    }
    
    // Add all available events
    const events = CONFIG_STATE.config?.events || {};
    const selectedIds = CONFIG_STATE.autoEventsSelectedEvents || [];
    
    for (const [eventId, eventConfig] of Object.entries(events)) {
        // Skip if already selected
        if (!selectedIds.includes(eventId)) {
            const option = document.createElement('option');
            option.value = eventId;
            option.textContent = eventConfig['display-name'] || eventId;
            select.appendChild(option);
        }
    }
}

function addAutoEventSelection(eventId) {
    if (!eventId) return;
    
    if (!CONFIG_STATE.autoEventsSelectedEvents) {
        CONFIG_STATE.autoEventsSelectedEvents = [];
    }
    
    if (!CONFIG_STATE.autoEventsSelectedEvents.includes(eventId)) {
        CONFIG_STATE.autoEventsSelectedEvents.push(eventId);
        updateConfig('settings.auto-events.selected-events', CONFIG_STATE.autoEventsSelectedEvents);
        updateAutoEventsSelectionList();
        populateAutoEventsDropdown();
    }
}

function removeAutoEventSelection(eventId) {
    if (!CONFIG_STATE.autoEventsSelectedEvents) return;
    
    const index = CONFIG_STATE.autoEventsSelectedEvents.indexOf(eventId);
    if (index > -1) {
        CONFIG_STATE.autoEventsSelectedEvents.splice(index, 1);
        updateConfig('settings.auto-events.selected-events', CONFIG_STATE.autoEventsSelectedEvents);
        updateAutoEventsSelectionList();
        populateAutoEventsDropdown();
    }
}

function moveAutoEventUp(eventId) {
    if (!CONFIG_STATE.autoEventsSelectedEvents) return;
    
    const index = CONFIG_STATE.autoEventsSelectedEvents.indexOf(eventId);
    if (index > 0) {
        const temp = CONFIG_STATE.autoEventsSelectedEvents[index];
        CONFIG_STATE.autoEventsSelectedEvents[index] = CONFIG_STATE.autoEventsSelectedEvents[index - 1];
        CONFIG_STATE.autoEventsSelectedEvents[index - 1] = temp;
        updateConfig('settings.auto-events.selected-events', CONFIG_STATE.autoEventsSelectedEvents);
        updateAutoEventsSelectionList();
    }
}

function moveAutoEventDown(eventId) {
    if (!CONFIG_STATE.autoEventsSelectedEvents) return;
    
    const index = CONFIG_STATE.autoEventsSelectedEvents.indexOf(eventId);
    if (index < CONFIG_STATE.autoEventsSelectedEvents.length - 1) {
        const temp = CONFIG_STATE.autoEventsSelectedEvents[index];
        CONFIG_STATE.autoEventsSelectedEvents[index] = CONFIG_STATE.autoEventsSelectedEvents[index + 1];
        CONFIG_STATE.autoEventsSelectedEvents[index + 1] = temp;
        updateConfig('settings.auto-events.selected-events', CONFIG_STATE.autoEventsSelectedEvents);
        updateAutoEventsSelectionList();
    }
}

function updateAutoEventsSelectionList() {
    const container = document.getElementById('auto-events-selection-list');
    if (!container) return;
    
    const selectedIds = CONFIG_STATE.autoEventsSelectedEvents || [];
    const modeSelect = document.getElementById('settings-auto-events-mode');
    const isRandom = modeSelect ? modeSelect.value === 'random' : true;
    
    if (selectedIds.length === 0) {
        container.innerHTML = `<div style="padding: 0.5rem; background: var(--surface); border-radius: 6px; color: var(--text-muted); font-size: 0.85rem;">${i18n.t('settings.autoEventsNoSelection')}</div>`;
        return;
    }
    
    const events = CONFIG_STATE.config?.events || {};
    let html = '<div style="display: flex; flex-direction: column; gap: 0.5rem;">';
    
    selectedIds.forEach((eventId, index) => {
        const eventConfig = events[eventId];
        const displayName = eventConfig?.['display-name'] || eventId;
        
        // Sequential mode: show order number and arrows
        if (!isRandom) {
            const showUpArrow = index > 0;
            const showDownArrow = index < selectedIds.length - 1;
            const nextEvent = index < selectedIds.length - 1 ? events[selectedIds[index + 1]]?.['display-name'] || selectedIds[index + 1] : null;
            
            html += `
                <div style="display: flex; flex-direction: column; gap: 0.25rem;">
                    <div style="display: flex; align-items: center; gap: 0.5rem; padding: 0.5rem; background: var(--surface); border-radius: 6px; border: 1px solid var(--border);">
                        <span style="color: var(--primary); font-weight: 700; min-width: 2rem; font-size: 1rem;">#${index + 1}</span>
                        <span style="flex: 1; font-weight: 500;">${displayName}</span>
                        ${showUpArrow ? `<button class="btn btn-icon btn-secondary" onclick="moveAutoEventUp('${eventId}')" title="${i18n.t('button.moveUp')}"><i class="fas fa-arrow-up"></i></button>` : ''}
                        ${showDownArrow ? `<button class="btn btn-icon btn-secondary" onclick="moveAutoEventDown('${eventId}')" title="${i18n.t('button.moveDown')}"><i class="fas fa-arrow-down"></i></button>` : ''}
                        <button class="btn btn-icon btn-danger" onclick="removeAutoEventSelection('${eventId}')" title="${i18n.t('button.remove')}"><i class="fas fa-times"></i></button>
                    </div>
                    ${nextEvent ? `<div style="padding-left: 1rem; color: var(--text-muted); font-size: 0.75rem; display: flex; align-items: center; gap: 0.25rem;"><i class="fas fa-arrow-down"></i> ${i18n.t('settings.autoEventsThen')}: ${nextEvent}</div>` : ''}
                </div>
            `;
        } else {
            // Random mode: simple list without order
            html += `
                <div style="display: flex; align-items: center; gap: 0.5rem; padding: 0.5rem; background: var(--surface); border-radius: 6px; border: 1px solid var(--border);">
                    <i class="fas fa-random" style="color: var(--primary); min-width: 1.5rem;"></i>
                    <span style="flex: 1;">${displayName}</span>
                    <button class="btn btn-icon btn-danger" onclick="removeAutoEventSelection('${eventId}')" title="${i18n.t('button.remove')}"><i class="fas fa-times"></i></button>
                </div>
            `;
        }
    });
    
    html += '</div>';
    container.innerHTML = html;
}


function updateConfig(path, value) {
    setNestedValue(CONFIG_STATE.config, path, value);
    recordChange('settings', path, value);
    updateQuickActionsPanel();
}

function updateWebConfig(path, value) {
    setNestedValue(CONFIG_STATE.webConfig, path, value);
    recordChange('web', path, value);
    updateQuickActionsPanel();
}

// ============================================
// Events Management
// ============================================

function renderEventsList() {
    console.log('=== renderEventsList called ===');
    const container = document.getElementById('events-list');
    if (!container) {
        console.error('events-list container NOT FOUND in DOM!');
        return;
    }
    console.log('events-list container found');
    
    // Events können direkt unter config.events sein
    let events = CONFIG_STATE.config?.events || {};
    console.log('Events data:', events);
    console.log('Events count:', Object.keys(events).length);
    
    if (Object.keys(events).length === 0) {
        container.innerHTML = `
            <div class="list-empty">
                <i class="fas fa-calendar-alt" style="font-size: 2rem; margin-bottom: 1rem;"></i>
                <p>${i18n.t('label.noEvents')}</p>
                <button class="btn btn-primary" style="margin-top: 1rem;" onclick="createNewEvent()">
                    <i class="fas fa-plus"></i> ${i18n.t('button.createEvent')}
                </button>
            </div>
        `;
        return;
    }

    let html = '';
    for (const [eventId, eventConfig] of Object.entries(events)) {
        if (eventConfig && typeof eventConfig === 'object') {
            html += createEventCard(eventId, eventConfig);
        }
    }
    container.innerHTML = html;
    updateNavigationBadges();
}

function createEventCard(eventId, config) {
    const isEnabled = config.enabled !== false;
    const minPlayers = config['min-players'] || 2;
    const maxPlayers = config['max-players'] || 20;
    const gameMode = config.mechanics?.['game-mode'] || 'SOLO';
    const countdown = config['countdown-time'] || 30;
    const command = config.command || eventId;
    
    return `
        <div class="card" style="${!isEnabled ? 'opacity: 0.7;' : ''}">
            <div class="card-header">
                <div class="card-title">
                    <i class="fas fa-calendar-alt" style="color: ${isEnabled ? 'var(--primary)' : 'var(--text-muted)'};"></i>
                    <span>${config['display-name'] || eventId}</span>
                    ${isEnabled ? `<span class="badge badge-success">${i18n.t('card.active')}</span>` : `<span class="badge badge-warning">${i18n.t('card.inactive')}</span>`}
                </div>
                <div class="card-actions">
                    <button class="btn btn-secondary btn-icon" onclick="editEvent('${eventId}')" title="${i18n.t('button.edit')}">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn btn-danger btn-icon" onclick="deleteEvent('${eventId}')" title="${i18n.t('button.delete')}">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </div>
            <div class="card-body">
                <div style="margin-bottom: 0.75rem; display: flex; justify-content: space-between; align-items: center;">
                    <code style="background: var(--background); padding: 0.2rem 0.5rem; border-radius: 4px; font-size: 0.8rem;">${eventId}</code>
                    <span style="background: var(--primary); color: white; padding: 0.25rem 0.75rem; border-radius: 4px; font-size: 0.8rem;">
                        /event ${command}
                    </span>
                </div>
                <p style="color: var(--text-secondary); margin-bottom: 1rem; font-size: 0.9rem;">${config.description || i18n.t('card.noDescription')}</p>
                <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 1rem;">
                    <div>
                        <span style="color: var(--text-muted); font-size: 0.75rem; display: block;">${i18n.t('card.players')}</span>
                        <span style="font-size: 0.9rem;">${minPlayers} - ${maxPlayers}</span>
                    </div>
                    <div>
                        <span style="color: var(--text-muted); font-size: 0.75rem; display: block;">${i18n.t('card.countdown')}</span>
                        <span style="font-size: 0.9rem;">${countdown}s</span>
                    </div>
                    <div>
                        <span style="color: var(--text-muted); font-size: 0.75rem; display: block;">${i18n.t('card.mode')}</span>
                        <span style="font-size: 0.9rem;">${formatGameMode(gameMode)}</span>
                    </div>
                    <div>
                        <span style="color: var(--text-muted); font-size: 0.75rem; display: block;">${i18n.t('card.pvp')}</span>
                        <span style="color: ${config.mechanics?.['pvp-enabled'] !== false ? 'var(--success)' : 'var(--error)'}; font-size: 0.9rem;">
                            ${config.mechanics?.['pvp-enabled'] !== false ? i18n.t('card.pvpOn') : i18n.t('card.pvpOff')}
                        </span>
                    </div>
                </div>
                ${config.worlds ? `
                <div style="margin-top: 1rem; padding-top: 0.75rem; border-top: 1px solid var(--border); display: flex; gap: 1rem;">
                    ${config.worlds['lobby-world'] ? `<span style="font-size: 0.8rem; color: var(--text-muted);"><i class="fas fa-door-open"></i> ${config.worlds['lobby-world']}</span>` : ''}
                    ${config.worlds['event-world'] ? `<span style="font-size: 0.8rem; color: var(--text-muted);"><i class="fas fa-globe"></i> ${config.worlds['event-world']}</span>` : ''}
                </div>
                ` : ''}
            </div>
        </div>
    `;
}

// Hilfsfunktion zum Formatieren des Spielmodus
function formatGameMode(mode) {
    const modes = {
        'SOLO': i18n.t('gameMode.solo'),
        'TEAM_2': i18n.t('gameMode.team2'),
        'TEAM_3': i18n.t('gameMode.team3'),
        'TEAM_4': i18n.t('gameMode.team4'),
        'FFA': i18n.t('gameMode.ffa')
    };
    return modes[mode] || mode;
}

// Die createNewEvent, editEvent, createNewWorld, editWorld, createNewEquipment, editEquipment
// Funktionen werden in editors.js definiert und überschreiben diese Fallback-Funktionen

function deleteEvent(eventId) {
    if (confirm(i18n.t('confirm.deleteEventPrompt', { id: eventId }))) {
        delete CONFIG_STATE.config.events[eventId];
        recordChange('settings', `events.${eventId}`, undefined);
        renderEventsList();
        updateQuickActionsPanel();
        showToast(i18n.t('toast.eventDeleted', { id: eventId }), 'success');
    }
}

// ============================================
// Worlds Management
// ============================================

function renderWorldsList() {
    console.log('=== renderWorldsList called ===');
    const container = document.getElementById('worlds-list');
    if (!container) {
        console.error('worlds-list container NOT FOUND in DOM!');
        return;
    }
    console.log('worlds-list container found');
    
    const worlds = CONFIG_STATE.worlds?.worlds || {};
    console.log('Worlds data:', worlds);
    console.log('Worlds count:', Object.keys(worlds).length);

    if (Object.keys(worlds).length === 0) {
        container.innerHTML = `
            <div class="list-empty">
                <i class="fas fa-globe" style="font-size: 2rem; margin-bottom: 1rem;"></i>
                <p>${i18n.t('label.noWorlds')}</p>
                <button class="btn btn-primary" style="margin-top: 1rem;" onclick="createNewWorld()">
                    <i class="fas fa-plus"></i> ${i18n.t('button.createWorld')}
                </button>
            </div>
        `;
        return;
    }

    let html = '';
    for (const [worldId, worldConfig] of Object.entries(worlds)) {
        if (worldConfig && typeof worldConfig === 'object') {
            html += createWorldCard(worldId, worldConfig);
        }
    }
    container.innerHTML = html;
    updateNavigationBadges();
}

function createWorldCard(worldId, config) {
    const isPvPEnabled = config['pvpwager-world-enable'] === true;
    const buildAllowed = config['build-allowed'] === true;
    const regenerateWorld = config['regenerate-world'] === true;
    const spawnType = config['pvpwager-spawn']?.['spawn-type'] || i18n.t('card.notDefined');
    
    return `
        <div class="card">
            <div class="card-header">
                <div class="card-title">
                    <i class="fas fa-globe" style="color: ${isPvPEnabled ? 'var(--error)' : 'var(--info)'};"></i>
                    <span>${config['display-name'] || worldId}</span>
                    ${isPvPEnabled ? `<span class="badge badge-error">${i18n.t('card.pvpActive')}</span>` : `<span class="badge badge-info">${i18n.t('card.eventWorld')}</span>`}
                </div>
                <div class="card-actions">
                    <button class="btn btn-secondary btn-icon" onclick="editWorld('${worldId}')" title="${i18n.t('button.edit')}">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn btn-danger btn-icon" onclick="deleteWorld('${worldId}')" title="${i18n.t('button.delete')}">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </div>
            <div class="card-body">
                <div style="margin-bottom: 0.5rem; color: var(--text-muted); font-size: 0.85rem;">
                    <code style="background: var(--background); padding: 0.2rem 0.5rem; border-radius: 4px;">${worldId}</code>
                </div>
                <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 1rem; margin-top: 1rem;">
                    <div>
                        <span style="color: var(--text-muted); font-size: 0.75rem; display: block;">${i18n.t('card.pvpWorld')}</span>
                        <span style="color: ${isPvPEnabled ? 'var(--success)' : 'var(--text-secondary)'};">
                            ${isPvPEnabled ? i18n.t('card.enabled') : i18n.t('card.disabled')}
                        </span>
                    </div>
                    <div>
                        <span style="color: var(--text-muted); font-size: 0.75rem; display: block;">${i18n.t('card.building')}</span>
                        <span style="color: ${buildAllowed ? 'var(--success)' : 'var(--text-secondary)'};">
                            ${buildAllowed ? i18n.t('card.allowed') : i18n.t('card.forbidden')}
                        </span>
                    </div>
                    <div>
                        <span style="color: var(--text-muted); font-size: 0.75rem; display: block;">${i18n.t('card.regeneration')}</span>
                        <span style="color: ${regenerateWorld ? 'var(--success)' : 'var(--text-secondary)'};">
                            ${regenerateWorld ? i18n.t('card.active') : i18n.t('card.inactive')}
                        </span>
                    </div>
                    <div>
                        <span style="color: var(--text-muted); font-size: 0.75rem; display: block;">${i18n.t('card.spawnType')}</span>
                        <span>${spawnType}</span>
                    </div>
                </div>
                ${config['clone-source-world'] ? `
                <div style="margin-top: 1rem; padding-top: 0.75rem; border-top: 1px solid var(--border);">
                    <span style="color: var(--text-muted); font-size: 0.75rem;">${i18n.t('card.templateWorld')}:</span>
                    <code style="margin-left: 0.5rem;">${config['clone-source-world']}</code>
                </div>
                ` : ''}
            </div>
        </div>
    `;
}

// Die createNewWorld und editWorld Funktionen werden in editors.js definiert

function deleteWorld(worldId) {
    if (confirm(i18n.t('confirm.deleteWorldPrompt', { id: worldId }))) {
        delete CONFIG_STATE.worlds.worlds[worldId];
        recordChange('worlds', `worlds.${worldId}`, undefined);
        renderWorldsList();
        updateQuickActionsPanel();
        showToast(i18n.t('toast.worldDeleted', { id: worldId }), 'success');
    }
}

// ============================================
// Equipment Management
// ============================================

function renderEquipmentList() {
    console.log('=== renderEquipmentList called ===');
    const container = document.getElementById('equipment-list');
    if (!container) {
        console.error('equipment-list container NOT FOUND in DOM!');
        return;
    }
    console.log('equipment-list container found');
    
    const equipment = CONFIG_STATE.equipment?.['equipment-sets'] || {};
    console.log('Equipment data:', equipment);
    console.log('Equipment count:', Object.keys(equipment).length);

    if (Object.keys(equipment).length === 0) {
        container.innerHTML = `
            <div class="list-empty">
                <i class="fas fa-shield-alt" style="font-size: 2rem; margin-bottom: 1rem;"></i>
                <p>${i18n.t('label.noEquipment')}</p>
                <button class="btn btn-primary" style="margin-top: 1rem;" onclick="createNewEquipment()">
                    <i class="fas fa-plus"></i> ${i18n.t('button.createEquipment')}
                </button>
            </div>
        `;
        return;
    }

    let html = '';
    for (const [equipId, equipConfig] of Object.entries(equipment)) {
        if (equipConfig && typeof equipConfig === 'object') {
            html += createEquipmentCard(equipId, equipConfig);
        }
    }
    container.innerHTML = html;
    updateNavigationBadges();
}

function createEquipmentCard(equipId, config) {
    const isEnabled = config.enabled !== false;
    const armor = config.armor || {};
    const inventory = config.inventory || [];
    const allowedWorlds = config['allowed-pvpwager-worlds'] || 'all';
    
    // Erstelle Armor-Vorschau
    const armorItems = [armor.helmet, armor.chestplate, armor.leggings, armor.boots].filter(Boolean);
    
    return `
        <div class="card" style="${!isEnabled ? 'opacity: 0.7;' : ''}">
            <div class="card-header">
                <div class="card-title">
                    <i class="fas fa-shield-alt" style="color: ${isEnabled ? 'var(--success)' : 'var(--text-muted)'};"></i>
                    <span>${config['display-name'] || equipId}</span>
                    ${isEnabled ? `<span class="badge badge-success">${i18n.t('card.active')}</span>` : `<span class="badge badge-warning">${i18n.t('card.inactive')}</span>`}
                </div>
                <div class="card-actions">
                    <button class="btn btn-secondary btn-icon" onclick="editEquipment('${equipId}')" title="${i18n.t('button.edit')}">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn btn-danger btn-icon" onclick="deleteEquipment('${equipId}')" title="${i18n.t('button.delete')}">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </div>
            <div class="card-body">
                <div style="margin-bottom: 0.75rem; display: flex; justify-content: space-between; align-items: center;">
                    <code style="background: var(--background); padding: 0.2rem 0.5rem; border-radius: 4px; font-size: 0.8rem;">${equipId}</code>
                    <span style="color: var(--text-muted); font-size: 0.8rem;">
                        <i class="fas fa-globe-americas"></i> ${allowedWorlds === 'all' ? i18n.t('card.allWorlds') : allowedWorlds}
                    </span>
                </div>
                ${config.description ? `<p style="color: var(--text-secondary); margin-bottom: 1rem; font-size: 0.9rem;">${config.description}</p>` : ''}
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; margin-top: 1rem;">
                    <div>
                        <p style="color: var(--text-muted); font-size: 0.75rem; margin-bottom: 0.5rem; text-transform: uppercase;">${i18n.t('card.armor')} (${armorItems.length}/4)</p>
                        <div style="display: flex; gap: 0.25rem; flex-wrap: wrap;">
                            ${armor.helmet ? `<span class="badge badge-info" style="font-size: 0.7rem;">${formatItemName(armor.helmet)}</span>` : ''}
                            ${armor.chestplate ? `<span class="badge badge-info" style="font-size: 0.7rem;">${formatItemName(armor.chestplate)}</span>` : ''}
                            ${armor.leggings ? `<span class="badge badge-info" style="font-size: 0.7rem;">${formatItemName(armor.leggings)}</span>` : ''}
                            ${armor.boots ? `<span class="badge badge-info" style="font-size: 0.7rem;">${formatItemName(armor.boots)}</span>` : ''}
                            ${armorItems.length === 0 ? `<span style="color: var(--text-muted); font-size: 0.8rem;">${i18n.t('card.noArmor')}</span>` : ''}
                        </div>
                    </div>
                    <div>
                        <p style="color: var(--text-muted); font-size: 0.75rem; margin-bottom: 0.5rem; text-transform: uppercase;">${i18n.t('card.inventory')} (${inventory.length} ${i18n.t('card.items')})</p>
                        ${inventory.length > 0 ? `
                            <div style="display: flex; gap: 0.25rem; flex-wrap: wrap;">
                                ${inventory.slice(0, 6).map(item => `<span class="badge badge-success" style="font-size: 0.7rem;">${formatItemName(item.item)}${item.amount > 1 ? ' x' + item.amount : ''}</span>`).join('')}
                                ${inventory.length > 6 ? `<span class="badge badge-warning" style="font-size: 0.7rem;">${i18n.t('card.more', { count: inventory.length - 6 })}</span>` : ''}
                            </div>
                        ` : `<span style="color: var(--text-muted); font-size: 0.8rem;">${i18n.t('card.emptyInventory')}</span>`}
                    </div>
                </div>
            </div>
        </div>
    `;
}

// Hilfsfunktion zum Formatieren von Item-Namen
function formatItemName(itemName) {
    if (!itemName) return '';
    return itemName.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase());
}

// Die createNewEquipment und editEquipment Funktionen werden in editors.js definiert

function deleteEquipment(equipId) {
    if (confirm(i18n.t('confirm.deleteEquipmentPrompt', { id: equipId }))) {
        delete CONFIG_STATE.equipment['equipment-sets'][equipId];
        recordChange('equipment', `equipment-sets.${equipId}`, undefined);
        renderEquipmentList();
        updateQuickActionsPanel();
        showToast(i18n.t('toast.equipDeleted', { id: equipId }), 'success');
    }
}

// ============================================
// Theme Management
// ============================================

function updateThemeColor(colorType, colorValue) {
    const cssVar = `--${colorType}`;
    if (colorType === 'primary-dark') {
        document.documentElement.style.setProperty(cssVar, colorValue);
    } else {
        document.documentElement.style.setProperty(cssVar, colorValue);
    }

    setNestedValue(CONFIG_STATE.webConfig, `web.theme.${colorType}-color`, colorValue);
    recordChange('web', `web.theme.${colorType}-color`, colorValue);
    updateQuickActionsPanel();
}

function loadThemeFromConfig() {
    const theme = CONFIG_STATE.webConfig?.web?.theme || {};
    
    const colorMap = {
        'primary-color': 'primary',
        'secondary-color': 'secondary',
        'background-color': 'background',
        'surface-color': 'surface',
        'card-color': 'card',
        'text-color': 'text',
        'text-secondary': 'text-secondary',
        'error-color': 'error',
        'warning-color': 'warning',
        'success-color': 'success',
        'info-color': 'info'
    };

    for (const [configKey, cssVar] of Object.entries(colorMap)) {
        const colorValue = theme[configKey];
        if (colorValue) {
            document.documentElement.style.setProperty(`--${cssVar}`, colorValue);
        }
    }
}

function resetTheme() {
    const defaultTheme = {
        'primary-color': '#4caf50',
        'secondary-color': '#66bb6a',
        'background-color': '#1a1a1a',
        'surface-color': '#2d2d2d',
        'card-color': '#3a3a3a',
        'text-color': '#e0e0e0',
        'text-secondary': '#b0b0b0',
        'error-color': '#f44336',
        'warning-color': '#ff9800',
        'success-color': '#4caf50',
        'info-color': '#2196f3'
    };

    CONFIG_STATE.webConfig.web = CONFIG_STATE.webConfig.web || {};
    CONFIG_STATE.webConfig.web.theme = defaultTheme;

    document.documentElement.style.setProperty('--primary', '#4caf50');
    document.documentElement.style.setProperty('--secondary', '#66bb6a');
    document.documentElement.style.setProperty('--background', '#1a1a1a');
    document.documentElement.style.setProperty('--surface', '#2d2d2d');
    document.documentElement.style.setProperty('--card', '#3a3a3a');
    document.documentElement.style.setProperty('--text', '#e0e0e0');
    document.documentElement.style.setProperty('--text-secondary', '#b0b0b0');
    document.documentElement.style.setProperty('--error', '#f44336');
    document.documentElement.style.setProperty('--warning', '#ff9800');
    document.documentElement.style.setProperty('--success', '#4caf50');
    document.documentElement.style.setProperty('--info', '#2196f3');

    recordChange('web', 'web.theme', defaultTheme);
    updateQuickActionsPanel();
    showToast(i18n.t('theme.reset'), 'success');
}

// ============================================
// Item Picker
// ============================================

let itemPickerCallback = null;

function openItemPicker(callback) {
    itemPickerCallback = callback;
    document.getElementById('item-picker-modal').classList.add('active');
    renderItemGrid(MINECRAFT_ITEMS);
}

function closeItemPicker() {
    document.getElementById('item-picker-modal').classList.remove('active');
    itemPickerCallback = null;
    document.getElementById('item-search').value = '';
}

function renderItemGrid(items) {
    const grid = document.getElementById('item-grid');
    grid.innerHTML = items.map(item => `
        <div class="item-picker-item" onclick="selectItem('${item}')" title="${item}">
            <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21/assets/minecraft/textures/item/${toSnakeCase(item)}.png" 
                 alt="${item}" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2240%22 height=%2240%22><rect fill=%22%232d2d2d%22 width=%2240%22 height=%2240%22/><text x=%2250%25%22 y=%2250%25%22 dominant-baseline=%22middle%22 text-anchor=%22middle%22 fill=%22%234caf50%22 font-size=%2210%22>${item.charAt(0)}</text></svg>'">
        </div>
    `).join('');
}

function filterItems(searchTerm) {
    const filtered = MINECRAFT_ITEMS.filter(item => 
        item.toLowerCase().includes(searchTerm.toLowerCase())
    );
    renderItemGrid(filtered);
}

function selectItem(itemName) {
    CONFIG_STATE.selectedItem = itemName;
    document.querySelectorAll('.item-picker-item').forEach(el => el.classList.remove('selected'));
    event.target.closest('.item-picker-item')?.classList.add('selected');
}

function confirmItemSelection() {
    if (CONFIG_STATE.selectedItem && itemPickerCallback) {
        itemPickerCallback(CONFIG_STATE.selectedItem);
        closeItemPicker();
    }
}

// ============================================
// YAML Preview
// ============================================

function showYamlPreview() {
    document.getElementById('yaml-modal').classList.add('active');
    showYamlTab('config');
}

function closeYamlPreview() {
    document.getElementById('yaml-modal').classList.remove('active');
}

function showYamlTab(tabName) {
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    document.querySelector(`[onclick*="showYamlTab('${tabName}')"]`)?.classList.add('active');

    let yaml = '';
    if (tabName === 'config') {
        yaml = jsonToYaml(CONFIG_STATE.config, 0);
    } else if (tabName === 'worlds') {
        yaml = jsonToYaml(CONFIG_STATE.worlds, 0);
    } else if (tabName === 'equipment') {
        yaml = jsonToYaml(CONFIG_STATE.equipment, 0);
    }

    document.getElementById('yaml-content').textContent = yaml;
}

function copyYaml() {
    const yamlContent = document.getElementById('yaml-content').textContent;
    navigator.clipboard.writeText(yamlContent).then(() => {
        showToast(i18n.t('yaml.copied'), 'success');
    });
}

// ============================================
// Utility Functions
// ============================================

function setNestedValue(obj, path, value) {
    const keys = path.split('.');
    let current = obj;
    
    for (let i = 0; i < keys.length - 1; i++) {
        if (!(keys[i] in current)) {
            current[keys[i]] = {};
        }
        current = current[keys[i]];
    }
    
    current[keys[keys.length - 1]] = value;
}

function getNestedValue(obj, path) {
    return path.split('.').reduce((curr, prop) => curr?.[prop], obj);
}

function recordChange(category, path, value) {
    CONFIG_STATE.changes.splice(CONFIG_STATE.changeIndex + 1);
    CONFIG_STATE.changes.push({ category, path, value });
    CONFIG_STATE.changeIndex = CONFIG_STATE.changes.length - 1;
    updateQuickActionsPanel();
}

function undoChange() {
    if (CONFIG_STATE.changeIndex >= 0) {
        CONFIG_STATE.changeIndex--;
        replayChanges();
        showToast(i18n.t('history.undo'), 'info');
    }
}

function redoChange() {
    if (CONFIG_STATE.changeIndex < CONFIG_STATE.changes.length - 1) {
        CONFIG_STATE.changeIndex++;
        replayChanges();
        showToast(i18n.t('history.redo'), 'info');
    }
}

function replayChanges() {
    // Load backups from localStorage
    const configBackup = localStorage.getItem('config_backup');
    const worldsBackup = localStorage.getItem('worlds_backup');
    const equipmentBackup = localStorage.getItem('equipment_backup');
    const webConfigBackup = localStorage.getItem('webconfig_backup');
    
    if (!configBackup) {
        console.error('No config backup found in localStorage');
        showToast(i18n.t('error.noBackup') || 'Keine Backups gefunden', 'error');
        return;
    }
    
    CONFIG_STATE.config = JSON.parse(configBackup);
    CONFIG_STATE.worlds = JSON.parse(worldsBackup || '{}');
    CONFIG_STATE.equipment = JSON.parse(equipmentBackup || '{}');
    CONFIG_STATE.webConfig = JSON.parse(webConfigBackup || '{}');

    // Replay all changes up to current index
    for (let i = 0; i <= CONFIG_STATE.changeIndex; i++) {
        const { category, path, value } = CONFIG_STATE.changes[i];
        if (category === 'settings') {
            setNestedValue(CONFIG_STATE.config, path, value);
        } else if (category === 'worlds') {
            setNestedValue(CONFIG_STATE.worlds, path, value);
        } else if (category === 'equipment') {
            setNestedValue(CONFIG_STATE.equipment, path, value);
        } else if (category === 'web') {
            setNestedValue(CONFIG_STATE.webConfig, path, value);
        }
    }

    // Update all UI components
    populateSettingsForm();
    renderEventsList();
    renderWorldsList();
    renderEquipmentList();
    updateQuickActionsPanel();
}

function hasUnsavedChanges() {
    return CONFIG_STATE.changes.length > 0;
}

function hasConfigChanged(category) {
    return CONFIG_STATE.changes.some(c => c.category === category);
}

function discardChanges() {
    if (confirm(i18n.t('history.discardConfirm'))) {
        CONFIG_STATE.changes = [];
        CONFIG_STATE.changeIndex = -1;
        loadAllConfigs();
        updateQuickActionsPanel();
        showToast(i18n.t('history.discarded'), 'info');
    }
}

function updateQuickActionsPanel() {
    const panel = document.getElementById('quick-actions');
    if (hasUnsavedChanges()) {
        panel.classList.remove('hidden');
    } else {
        panel.classList.add('hidden');
    }
}

function updateNavigationBadges() {
    const eventCount = Object.keys(CONFIG_STATE.config?.events || {}).length;
    const worldCount = Object.keys(CONFIG_STATE.worlds?.worlds || {}).length;
    const equipCount = Object.keys(CONFIG_STATE.equipment?.['equipment-sets'] || {}).length;

    const eventsCountEl = document.getElementById('events-count');
    const worldsCountEl = document.getElementById('worlds-count');
    const equipmentCountEl = document.getElementById('equipment-count');
    
    if (eventsCountEl) eventsCountEl.textContent = eventCount;
    if (worldsCountEl) worldsCountEl.textContent = worldCount;
    if (equipmentCountEl) equipmentCountEl.textContent = equipCount;
    
    console.log(`Navigation badges updated: Events=${eventCount}, Worlds=${worldCount}, Equipment=${equipCount}`);
}

function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = `
        <i class="fas fa-${type === 'success' ? 'check-circle' : type === 'error' ? 'exclamation-circle' : type === 'warning' ? 'exclamation-triangle' : 'info-circle'}"></i>
        <span>${message}</span>
    `;
    
    container.appendChild(toast);
    setTimeout(() => toast.remove(), 3000);
}

function showLoading(show) {
    const main = document.querySelector('.content');
    if (show && !document.getElementById('loading-spinner')) {
        const spinner = document.createElement('div');
        spinner.id = 'loading-spinner';
        spinner.className = 'loading';
        spinner.innerHTML = '<div class="spinner"></div>';
        main.appendChild(spinner);
    } else if (!show) {
        document.getElementById('loading-spinner')?.remove();
    }
}

async function reloadServer() {
    if (confirm(i18n.t('server.reloadConfirm'))) {
        try {
            console.log('[Reload] Sende Reload-Request...');
            const response = await fetch('/api/reload', { 
                method: 'POST',
                credentials: 'include'
            });
            const data = await response.json();
            console.log('[Reload] Response:', data);
            
            if (data.success) {
                showToast(i18n.t('server.reloadSuccess', { message: data.message || 'OK' }), 'success');
            } else {
                showToast(i18n.t('server.reloadError', { message: data.message || i18n.t('server.unknownError') }), 'error');
            }
        } catch (error) {
            console.error('[Reload] Error:', error);
            showToast(i18n.t('server.reloadErrorGeneric', { message: error.message || '' }), 'error');
        }
    }
}

function exportConfig() {
    const data = {
        config: CONFIG_STATE.config,
        worlds: CONFIG_STATE.worlds,
        equipment: CONFIG_STATE.equipment,
        timestamp: new Date().toISOString()
    };
    
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `event-pvp-backup-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
    
    showToast(i18n.t('export.success'), 'success');
}

function toSnakeCase(str) {
    return str.toLowerCase().replace(/_/g, '_');
}

function jsonToYaml(obj, indent = 0) {
    let yaml = '';
    const spaces = ' '.repeat(indent);

    for (const [key, value] of Object.entries(obj)) {
        if (value === null || value === undefined) continue;

        if (typeof value === 'object' && !Array.isArray(value)) {
            yaml += `${spaces}${key}:\n`;
            yaml += jsonToYaml(value, indent + 2);
        } else if (Array.isArray(value)) {
            yaml += `${spaces}${key}:\n`;
            value.forEach(item => {
                if (typeof item === 'object') {
                    yaml += `${spaces}  - ${JSON.stringify(item)}\n`;
                } else {
                    yaml += `${spaces}  - ${item}\n`;
                }
            });
        } else if (typeof value === 'boolean') {
            yaml += `${spaces}${key}: ${value ? 'true' : 'false'}\n`;
        } else if (typeof value === 'string' && (value.includes('\n') || value.includes('\r'))) {
            yaml += `${spaces}${key}: |\n`;
            value.split('\n').forEach(line => {
                yaml += `${spaces}  ${line}\n`;
            });
        } else {
            yaml += `${spaces}${key}: ${value}\n`;
        }
    }

    return yaml;
}

function incrementValue(elementId, step = 1) {
    const input = document.getElementById(elementId);
    input.value = parseInt(input.value) + step;
    input.dispatchEvent(new Event('change'));
}

function decrementValue(elementId, step = 1) {
    const input = document.getElementById(elementId);
    input.value = Math.max(parseInt(input.min || 0), parseInt(input.value) - step);
    input.dispatchEvent(new Event('change'));
}

// Initialize backups for undo/redo
window.addEventListener('load', () => {
    localStorage.setItem('config_backup', JSON.stringify(CONFIG_STATE.config));
    localStorage.setItem('worlds_backup', JSON.stringify(CONFIG_STATE.worlds));
    localStorage.setItem('equipment_backup', JSON.stringify(CONFIG_STATE.equipment));
    localStorage.setItem('webconfig_backup', JSON.stringify(CONFIG_STATE.webConfig));
});

// ============================================
// Debug & Diagnostics
// ============================================

function debugState() {
    console.group('📊 CONFIG_STATE Debug');
    console.log('Config:', CONFIG_STATE.config);
    console.log('Events:', CONFIG_STATE.config?.events);
    console.log('Worlds:', CONFIG_STATE.worlds?.worlds);
    console.log('Equipment:', CONFIG_STATE.equipment?.['equipment-sets']);
    console.log('WebConfig:', CONFIG_STATE.webConfig);
    console.log('Changes:', CONFIG_STATE.changes);
    console.groupEnd();
    
    return {
        config: CONFIG_STATE.config,
        worlds: CONFIG_STATE.worlds,
        equipment: CONFIG_STATE.equipment
    };
}

// Expose debug function globally
window.debugState = debugState;
window.CONFIG_STATE = CONFIG_STATE;

// ============================================
// Globale Funktionsregistrierung für onclick-Handler
// ============================================
window.showSection = showSection;
window.showToast = showToast;
window.deleteEvent = deleteEvent;
window.deleteWorld = deleteWorld;
window.deleteEquipment = deleteEquipment;
window.renderEventsList = renderEventsList;
window.renderWorldsList = renderWorldsList;
window.renderEquipmentList = renderEquipmentList;
window.openItemPicker = openItemPicker;
window.closeItemPicker = closeItemPicker;
window.filterItems = filterItems;
window.selectItem = selectItem;
window.showYamlPreview = showYamlPreview;
window.closeYamlPreview = closeYamlPreview;
window.showYamlTab = showYamlTab;
window.copyYaml = copyYaml;
window.exportConfig = exportConfig;
window.importConfig = importConfig;
window.reloadServer = reloadServer;
window.checkServerStatus = checkServerStatus;
window.updateConfig = updateConfig;
window.updateWebConfig = updateWebConfig;
window.incrementValue = incrementValue;
window.decrementValue = decrementValue;
window.updateThemeColor = updateThemeColor;
window.resetTheme = resetTheme;

console.log('✓ App.js Funktionen global registriert');

// ============================================
// Import Config
// ============================================

function importConfig(event) {
    const file = event.target.files[0];
    if (!file) return;
    
    const reader = new FileReader();
    reader.onload = (e) => {
        try {
            const data = JSON.parse(e.target.result);
            
            if (data.config) CONFIG_STATE.config = data.config;
            if (data.worlds) CONFIG_STATE.worlds = data.worlds;
            if (data.equipment) CONFIG_STATE.equipment = data.equipment;
            
            populateSettingsForm();
            renderEventsList();
            renderWorldsList();
            renderEquipmentList();
            
            showToast(i18n.t('import.success'), 'success');
        } catch (error) {
            showToast(i18n.t('import.error', { message: error.message || '' }), 'error');
        }
    };
    reader.readAsText(file);
}

// ============================================
// Server Status Check
// ============================================

async function checkServerStatus() {
    try {
        const response = await fetch('/api/status', {
            credentials: 'include'
        });
        if (response.ok) {
            const data = await response.json();
            const status = data.data || data;
            
            console.log('Server Status:', status);
            updateConnectionStatus('active', i18n.t('status.connectedDetail', { name: status.pluginName, version: status.pluginVersion }));
            
            // Update Server Info in Sidebar
            const onlinePlayersEl = document.getElementById('online-players');
            const tpsEl = document.getElementById('server-tps');
            
            if (onlinePlayersEl) {
                onlinePlayersEl.textContent = `${status.onlinePlayers}/${status.maxPlayers}`;
            }
            if (tpsEl) {
                const tps = status.tps || '-';
                tpsEl.textContent = typeof tps === 'number' ? tps.toFixed(1) : tps;
            }
            
            return status;
        }
    } catch (error) {
        console.warn('Server status check failed:', error);
        updateConnectionStatus('inactive', i18n.t('status.notConnected'));
    }
    return null;
}
