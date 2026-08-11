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
        const langSaved = await saveServerLanguage(lang);
        
        // Aktualisiere CONFIG_STATE und CONFIG_BASELINE für YAML Preview & Dirty Tracking
        if (CONFIG_STATE.config && CONFIG_STATE.config.settings) {
            CONFIG_STATE.config.settings.language = lang;
        } else if (CONFIG_STATE.config) {
            CONFIG_STATE.config.settings = { language: lang };
        }
        if (langSaved && typeof CONFIG_BASELINE !== 'undefined' && CONFIG_BASELINE.config) {
            if (CONFIG_BASELINE.config.settings) {
                CONFIG_BASELINE.config.settings.language = lang;
            } else {
                CONFIG_BASELINE.config.settings = { language: lang };
            }
            localStorage.setItem('config_backup', JSON.stringify(CONFIG_STATE.config));
        }
        
        // Re-render dynamic content
        if (typeof renderEventsList === 'function') renderEventsList();
        if (typeof renderWorldsList === 'function') renderWorldsList();
        if (typeof renderEquipmentList === 'function') renderEquipmentList();
        updateSyncStatusUI();
        showToast(i18n.t('success.saved'), 'success');
    } catch (error) {
        console.error('Failed to change language:', error);
        showToast('Failed to change language', 'error');
    }
}

// escapeHtml stand hier ein zweites Mal - eine Variante ueber div.textContent, die
// Anfuehrungszeichen nicht ersetzt. Sie war durch die spaetere Definition ohnehin
// verdeckt; entfernt, weil Aufrufer sie auch in title="..."-Attributen verwenden und
// dort ein nicht ersetztes " das Attribut sprengen wuerde.

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

// Baseline Snapshot (Pristine Server Stand nach Laden / Speichern)
const CONFIG_BASELINE = {
    config: null,
    worlds: null,
    equipment: null,
    webConfig: null
};

/**
 * Erstellt eine unabhängige Tiefenkopie eines Objekts.
 */
function deepClone(obj) {
    if (obj === null || typeof obj !== 'object') return obj;
    try {
        return structuredClone(obj);
    } catch {
        return JSON.parse(JSON.stringify(obj));
    }
}

/**
 * Prüft zwei Werte/Objekte/Arrays auf tiefgreifende strukturelle Gleichheit.
 */
function isDeepEqual(a, b) {
    if (a === b) return true;
    if (a === null || b === null || typeof a !== 'object' || typeof b !== 'object') {
        return a === b;
    }
    const isArrA = Array.isArray(a);
    const isArrB = Array.isArray(b);
    if (isArrA !== isArrB) return false;
    if (isArrA) {
        if (a.length !== b.length) return false;
        for (let i = 0; i < a.length; i++) {
            if (!isDeepEqual(a[i], b[i])) return false;
        }
        return true;
    }
    const keysA = Object.keys(a).filter(k => a[k] !== undefined);
    const keysB = Object.keys(b).filter(k => b[k] !== undefined);
    if (keysA.length !== keysB.length) return false;
    for (const k of keysA) {
        if (!Object.prototype.hasOwnProperty.call(b, k)) return false;
        if (!isDeepEqual(a[k], b[k])) return false;
    }
    return true;
}

/**
 * Ermittelt alle echten Abweichungen zwischen dem aktuellen CONFIG_STATE und CONFIG_BASELINE.
 */
function getRealUnsavedChanges() {
    const changes = {
        settings: false,
        worlds: false,
        equipment: false,
        web: false,
        count: 0,
        details: []
    };

    if (!CONFIG_BASELINE.config) {
        return changes;
    }

    // 1. settings (config.yml)
    changes.settings = !isDeepEqual(CONFIG_STATE.config, CONFIG_BASELINE.config);
    if (changes.settings) {
        const curSettings = CONFIG_STATE.config?.settings || {};
        const baseSettings = CONFIG_BASELINE.config?.settings || {};
        const allSettingKeys = new Set([...Object.keys(curSettings), ...Object.keys(baseSettings)]);
        for (const k of allSettingKeys) {
            if (!isDeepEqual(curSettings[k], baseSettings[k])) {
                changes.count++;
                changes.details.push(`settings.${k}`);
            }
        }
        const curEvents = CONFIG_STATE.config?.events || {};
        const baseEvents = CONFIG_BASELINE.config?.events || {};
        const allEventKeys = new Set([...Object.keys(curEvents), ...Object.keys(baseEvents)]);
        for (const k of allEventKeys) {
            if (!isDeepEqual(curEvents[k], baseEvents[k])) {
                changes.count++;
                changes.details.push(`events.${k}`);
            }
        }
    }

    // 2. worlds (worlds.yml)
    changes.worlds = !isDeepEqual(CONFIG_STATE.worlds, CONFIG_BASELINE.worlds);
    if (changes.worlds) {
        const curWorlds = CONFIG_STATE.worlds?.worlds || {};
        const baseWorlds = CONFIG_BASELINE.worlds?.worlds || {};
        const allWorldKeys = new Set([...Object.keys(curWorlds), ...Object.keys(baseWorlds)]);
        for (const k of allWorldKeys) {
            if (!isDeepEqual(curWorlds[k], baseWorlds[k])) {
                changes.count++;
                changes.details.push(`worlds.${k}`);
            }
        }
    }

    // 3. equipment (equipment.yml)
    changes.equipment = !isDeepEqual(CONFIG_STATE.equipment, CONFIG_BASELINE.equipment);
    if (changes.equipment) {
        const curEquip = equipmentSets(CONFIG_STATE.equipment);
        const baseEquip = equipmentSets(CONFIG_BASELINE.equipment);
        const allEquipKeys = new Set([...Object.keys(curEquip), ...Object.keys(baseEquip)]);
        for (const k of allEquipKeys) {
            if (!isDeepEqual(curEquip[k], baseEquip[k])) {
                changes.count++;
                changes.details.push(`equipment.${k}`);
            }
        }
    }

    // 4. web (web-config.yml)
    changes.web = !isDeepEqual(CONFIG_STATE.webConfig, CONFIG_BASELINE.webConfig);
    if (changes.web) {
        const curWeb = CONFIG_STATE.webConfig?.web || {};
        const baseWeb = CONFIG_BASELINE.webConfig?.web || {};
        if (!isDeepEqual(curWeb.port, baseWeb.port)) {
            changes.count++;
            changes.details.push('web.port');
        }
        if (!isDeepEqual(curWeb['public-url'], baseWeb['public-url'])) {
            changes.count++;
            changes.details.push('web.public-url');
        }
        if (!isDeepEqual(curWeb.theme, baseWeb.theme)) {
            changes.count++;
            changes.details.push('web.theme');
        }
    }

    let minCount = 0;
    if (changes.settings) minCount++;
    if (changes.worlds) minCount++;
    if (changes.equipment) minCount++;
    if (changes.web) minCount++;
    if (changes.count < minCount) {
        changes.count = minCount;
    }

    return changes;
}

// Die frueher hier stehende Liste MINECRAFT_ITEMS (137 fest einprogrammierte Namen) ist
// entfallen. Item-Liste, Stapelgroessen und Verzauberungen kommen jetzt aus items.js
// (ITEM_CATALOG), gespeist von /api/materials - also aus dem laufenden Server.

// ============================================
// Initialisierung
// ============================================

// Auth State
let isAuthenticated = false;
let authRequired = true;
let currentPlayer = null;

window.addEventListener('DOMContentLoaded', async () => {
    console.log('=== Event-PVP Web Configurator Starting ===');
    console.log('Time:', new Date().toISOString());

    // 1. Lade Sprache vom Server (config.yml settings.language)
    // Server-Wert hat IMMER Vorrang, damit Änderungen in der config.yml wirksam werden
    try {
        const serverLang = await fetchServerLanguage();
        let lang;
        if (serverLang) {
            lang = serverLang;
            localStorage.setItem('lang', serverLang);
            console.log('Using server language:', serverLang);
        } else {
            lang = localStorage.getItem('lang') || 'en';
            console.log('Server not reachable, using localStorage/default:', lang);
        }
        await i18n.init(lang);
    } catch (err) {
        console.error('Language initialization failed:', err);
        try {
            await i18n.init('en');
        } catch (e) {
            console.error('Fallback language init failed:', e);
        }
    }

    document.title = i18n.t('app.title');
    document.documentElement.lang = i18n.current;
    applyTranslations();

    // Set language selector to current language
    const langSelect = document.getElementById('settings-language');
    if (langSelect) langSelect.value = i18n.current;

    try {
        // Setup Event Listeners zuerst
        setupEventListeners();
        console.log('Event listeners setup complete');
        
        // Prüfe ob wir im Browser laufen oder von Server bedient werden
        const isServedFromServer = window.location.protocol !== 'file:';
        console.log('Protocol:', window.location.protocol, 'Is served from server:', isServedFromServer);
        
        if (isServedFromServer) {
            try {
                const authStatus = await checkAuthentication();
                if (authStatus.authenticated || !authStatus.authRequired) {
                    // Authentifiziert oder keine Auth nötig
                    isAuthenticated = true;
                    authRequired = authStatus.authRequired;
                    currentPlayer = authStatus.playerName;
                    
                    hideLoginScreen();
                    await initializeApp();
                } else {
                    // Login erforderlich
                    authRequired = true;
                    showLoginScreen();
                }
            } catch (err) {
                console.error('Auth check failed:', err);
                showLoginScreen();
            }
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
        } else if (response.status === 429) {
            showLoginError(i18n.t('auth.rateLimited'));
        } else {
            // Nicht data.error anzeigen: der Server antwortet dort auf Deutsch
            // ("Token fehlt", "Ungueltiger oder abgelaufener Token"), was im
            // sonst uebersetzten Login-Screen jeder Sprache auftauchte.
            showLoginError(i18n.t('auth.invalidToken'));
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
                description: '&7Fight to the last player standing!',
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
        equipment: {
            pvp_starter: {
                'pvpwager-equip-enable': true,
                'event-equip-enable': true,
                'display-name': '&aStarter PvP'
            }
        }
    };
    
    CONFIG_BASELINE.config = deepClone(CONFIG_STATE.config);
    CONFIG_BASELINE.worlds = deepClone(CONFIG_STATE.worlds);
    CONFIG_BASELINE.equipment = deepClone(CONFIG_STATE.equipment);
    CONFIG_BASELINE.webConfig = deepClone(CONFIG_STATE.webConfig || {});
    CONFIG_STATE.changes = [];
    CONFIG_STATE.changeIndex = -1;

    populateSettingsForm();
    renderEventsList();
    renderWorldsList();
    renderEquipmentList();
    updateSyncStatusUI();
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

        // Baseline für Deep-Dirty-Tracking & Backup für Undo/Redo speichern
        CONFIG_BASELINE.config = deepClone(CONFIG_STATE.config);
        CONFIG_BASELINE.worlds = deepClone(CONFIG_STATE.worlds);
        CONFIG_BASELINE.equipment = deepClone(CONFIG_STATE.equipment);
        CONFIG_BASELINE.webConfig = deepClone(CONFIG_STATE.webConfig);

        localStorage.setItem('config_backup', JSON.stringify(CONFIG_STATE.config));
        localStorage.setItem('worlds_backup', JSON.stringify(CONFIG_STATE.worlds));
        localStorage.setItem('equipment_backup', JSON.stringify(CONFIG_STATE.equipment));
        localStorage.setItem('webconfig_backup', JSON.stringify(CONFIG_STATE.webConfig));

        CONFIG_STATE.changes = [];
        CONFIG_STATE.changeIndex = -1;

        // Item-Katalog laden, sobald die Web-Config da ist: aus ihr stammt der optionale
        // Remote-Fallback fuer Texturen. Muss vor jedem Rendern stehen, das Icons zeichnet.
        applyTextureSettings();
        await loadItemCatalog();

        // Warnen, falls equipment.yml noch eine Alt-Sektion enthaelt - dann lief die
        // Migration mangels Serverneustart noch nicht.
        checkEquipmentSchema();

        // Serverwelten erst nach den Configs holen: der Belegungs-Index der API liest die
        // gespeicherten YAMLs, und die Weltkarten brauchen beides zum Rendern.
        await loadMvWorlds();
        await loadInventoryStatus();
        await loadInventoryGuard();

        console.log('Rendering UI components...');
        populateSettingsForm();
        renderEventsList();
        renderWorldsList();
        renderServerWorldsPanel();
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

/**
 * Uebernimmt den Textur-Block aus web-config.yml in den Icon-Helper.
 *
 * Icons liegen im Plugin-JAR; `texture-source` ist nur noch der optionale Ausweg fuer
 * Items, zu denen kein Icon mitgeliefert wurde. Ist `enable-textures` aus, zeigt das
 * Panel ausschliesslich Buchstaben-Platzhalter - sinnvoll fuer sehr schmale Verbindungen.
 */
function applyTextureSettings() {
    const items = (CONFIG_STATE.webConfig && CONFIG_STATE.webConfig.items) || {};
    ITEM_CATALOG.texturesEnabled = items['enable-textures'] !== false;
    if (!ITEM_CATALOG.texturesEnabled) {
        ITEM_TEXTURE_FALLBACK = '';
        return;
    }
    const source = items['texture-source'];
    ITEM_TEXTURE_FALLBACK = typeof source === 'string' && /^https?:\/\//.test(source) ? source : '';
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
    CONFIG_STATE.isOffline = (status !== 'active');
    updateSyncStatusUI();
}

async function saveAllConfigs() {
    try {
        // Port-Änderungswarnung: Prüfen, ob der Port geändert wurde
        if (hasConfigChanged('web')) {
            const curPort = CONFIG_STATE.webConfig?.web?.port;
            const basePort = CONFIG_BASELINE.webConfig?.web?.port;
            if (curPort !== undefined && basePort !== undefined && curPort !== basePort) {
                const warningMsg = i18n.t('web.portChangeWarning', {
                    oldPort: basePort,
                    newPort: curPort
                });
                if (!confirm(warningMsg)) {
                    return; // Speichern abgebrochen
                }
            }
        }

        CONFIG_STATE.isSaving = true;
        updateSyncStatusUI();
        showLoading(true);
        
        const promises = [];
        const savedCategories = [];
        
        console.log('[Save] Checking changes against baseline...');
        
        if (hasConfigChanged('settings')) {
            console.log('[Save] Speichere config.yml...');
            savedCategories.push('settings');
            promises.push(
                fetch('/api/config/save', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ data: CONFIG_STATE.config })
                }).then(r => { console.log('[Save] config response:', r.status); return { category: 'settings', ok: r.ok, status: r.status }; })
            );
        }

        if (hasConfigChanged('worlds')) {
            console.log('[Save] Speichere worlds.yml...');
            savedCategories.push('worlds');
            promises.push(
                fetch('/api/worlds/save', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ data: CONFIG_STATE.worlds })
                }).then(r => { console.log('[Save] worlds response:', r.status); return { category: 'worlds', ok: r.ok, status: r.status }; })
            );
        }

        if (hasConfigChanged('equipment')) {
            console.log('[Save] Speichere equipment.yml...');
            savedCategories.push('equipment');
            promises.push(
                fetch('/api/equipment/save', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ data: CONFIG_STATE.equipment })
                }).then(r => { console.log('[Save] equipment response:', r.status); return { category: 'equipment', ok: r.ok, status: r.status }; })
            );
        }

        if (hasConfigChanged('web')) {
            console.log('[Save] Speichere web-config.yml...');
            savedCategories.push('web');
            promises.push(
                fetch('/api/webconfig/save', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ data: CONFIG_STATE.webConfig })
                }).then(r => { console.log('[Save] webconfig response:', r.status); return { category: 'web', ok: r.ok, status: r.status }; })
            );
        }

        if (promises.length === 0) {
            showToast(i18n.t('info.noChanges'), 'info');
            CONFIG_STATE.isSaving = false;
            updateSyncStatusUI();
            showLoading(false);
            return;
        }
        
        console.log('[Save] Sende', promises.length, 'Save-Requests...');

        const results = await Promise.all(promises);
        const allSuccess = results.every(r => r.ok);

        if (allSuccess) {
            // Baseline und Backups für die gespeicherten Kategorien synchronisieren
            if (savedCategories.includes('settings')) {
                CONFIG_BASELINE.config = deepClone(CONFIG_STATE.config);
                localStorage.setItem('config_backup', JSON.stringify(CONFIG_STATE.config));
            }
            if (savedCategories.includes('worlds')) {
                CONFIG_BASELINE.worlds = deepClone(CONFIG_STATE.worlds);
                localStorage.setItem('worlds_backup', JSON.stringify(CONFIG_STATE.worlds));
            }
            if (savedCategories.includes('equipment')) {
                CONFIG_BASELINE.equipment = deepClone(CONFIG_STATE.equipment);
                localStorage.setItem('equipment_backup', JSON.stringify(CONFIG_STATE.equipment));
            }
            if (savedCategories.includes('web')) {
                CONFIG_BASELINE.webConfig = deepClone(CONFIG_STATE.webConfig);
                localStorage.setItem('webconfig_backup', JSON.stringify(CONFIG_STATE.webConfig));
            }
            
            CONFIG_STATE.changes = [];
            CONFIG_STATE.changeIndex = -1;
            CONFIG_STATE.lastSave = new Date();
            updateQuickActionsPanel();
            showToast(i18n.t('success.savedAll'), 'success');
        } else {
            results.forEach(res => {
                if (res.ok) {
                    if (res.category === 'settings') {
                        CONFIG_BASELINE.config = deepClone(CONFIG_STATE.config);
                        localStorage.setItem('config_backup', JSON.stringify(CONFIG_STATE.config));
                    } else if (res.category === 'worlds') {
                        CONFIG_BASELINE.worlds = deepClone(CONFIG_STATE.worlds);
                        localStorage.setItem('worlds_backup', JSON.stringify(CONFIG_STATE.worlds));
                    } else if (res.category === 'equipment') {
                        CONFIG_BASELINE.equipment = deepClone(CONFIG_STATE.equipment);
                        localStorage.setItem('equipment_backup', JSON.stringify(CONFIG_STATE.equipment));
                    } else if (res.category === 'web') {
                        CONFIG_BASELINE.webConfig = deepClone(CONFIG_STATE.webConfig);
                        localStorage.setItem('webconfig_backup', JSON.stringify(CONFIG_STATE.webConfig));
                    }
                }
            });
            showToast(i18n.t('error.savePartial'), 'error');
        }

        CONFIG_STATE.isSaving = false;
        updateSyncStatusUI();
        showLoading(false);
    } catch (error) {
        console.error('Error saving configs:', error);
        CONFIG_STATE.isSaving = false;
        updateSyncStatusUI();
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
        if (sectionName === 'inventories') {
            refreshInventorySection();
        }
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
    
    const inventory = settings['inventory-management'] || {};
    setValueSafe('inventory-restore-match-end', inventory['auto-restore-on-match-end'], true);
    setValueSafe('inventory-restore-event-end', inventory['auto-restore-on-event-end'], true);
    setValueSafe('inventory-restore-respawn', inventory['auto-restore-on-respawn'], true);
    setValueSafe('inventory-restore-rejoin', inventory['auto-restore-on-rejoin'], true);
    setValueSafe('inventory-failure-policy', inventory['on-backup-failure'], 'abort');
    setValueSafe('inventory-legacy-safety', inventory['legacy-safety-backups'], true);
    // cleanup-backups-after-match ist standardmaessig AUS; setValueSafe wuerde ein
    // fehlendes Feld als "an" lesen, deshalb hier explizit.
    const cleanupEl = document.getElementById('inventory-cleanup-after-match');
    if (cleanupEl) {
        cleanupEl.checked = inventory['cleanup-backups-after-match'] === true;
    }
    // Die Warnungen haengen an den Werten, die gerade gesetzt wurden.
    renderInventoryWarnings();

    const worldManagement = settings['world-management'] || {};
    setValueSafe('settings-world-management-events', worldManagement.events, true);
    setValueSafe('settings-world-management-arenas', worldManagement.arenas, true);
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

    // Integrations
    const integrations = settings.integrations || {};
    setValueSafe('settings-ajleaderboards-enabled', integrations.ajleaderboards?.enabled, false);
    setValueSafe('settings-decentholograms-enabled', integrations.decentholograms?.enabled, false);
    setValueSafe('settings-pvpmanager-enabled', integrations.pvpmanager?.enabled, false);
    setValueSafe('settings-refresh-interval-ticks', integrations['refresh-interval-ticks'], 20);
    
    // Show/hide auto-event settings based on enabled state
    toggleAutoEventSettings();
    updateAutoEventsSelectionList();
    populateAutoEventsDropdown();

    // Felder aus web-config.yml gehoeren zum selben Formular-Durchlauf
    populateWebConfigForm();
}

/**
 * Bereinigt die Public-URL fuer die Anzeige im Eingabefeld (entfernt :{port} und evtl. numerische Ports).
 */
function cleanPublicUrlForDisplay(url) {
    if (!url) return '';
    let cleaned = String(url).trim();
    cleaned = cleaned.replace(/:\{port\}\/?$/i, '');
    cleaned = cleaned.replace(/:\d+\/?$/, '');
    if (cleaned.endsWith('/')) {
        cleaned = cleaned.slice(0, -1);
    }
    return cleaned;
}

/**
 * Formatiert die vom Nutzer eingegebene Public-URL fuer die Speicherung in web-config.yml (haengt immer :{port} an).
 */
function formatPublicUrlForConfig(inputUrl) {
    if (!inputUrl || String(inputUrl).trim() === '') {
        return 'http://localhost:{port}';
    }
    let val = String(inputUrl).trim();
    val = val.replace(/:\{port\}\/?$/i, '');
    val = val.replace(/:\d+\/?$/, '');
    if (val.endsWith('/')) {
        val = val.slice(0, -1);
    }
    return `${val}:{port}`;
}

function updatePublicUrl(inputValue) {
    const formatted = formatPublicUrlForConfig(inputValue);
    updateWebConfig('web.public-url', formatted);
}

/**
 * Traegt die Werte aus web-config.yml in die Formularfelder ein
 * (Port, Public-URL und die Theme-Farbfelder).
 */
function populateWebConfigForm() {
    const web = CONFIG_STATE.webConfig?.web || {};

    const portEl = document.getElementById('web-port');
    if (portEl && web.port !== undefined && web.port !== null) {
        portEl.value = web.port;
    }

    const publicUrlEl = document.getElementById('web-public-url');
    if (publicUrlEl) {
        const rawUrl = web['public-url'] || 'http://localhost:{port}';
        publicUrlEl.value = cleanPublicUrlForDisplay(rawUrl);
    }

    // Theme-Farben: Color-Picker und das danebenliegende Hex-Textfeld synchron setzen
    const theme = web.theme || {};
    const colorFields = {
        'theme-primary': 'primary-color',
        'theme-secondary': 'secondary-color',
        'theme-background': 'background-color',
        'theme-surface': 'surface-color',
        'theme-card': 'card-color',
        'theme-text': 'text-color'
    };

    for (const [elementId, configKey] of Object.entries(colorFields)) {
        const value = theme[configKey];
        if (!value) continue;

        const picker = document.getElementById(elementId);
        if (!picker) continue;

        picker.value = value;

        const hexInput = picker.parentElement?.querySelector('.color-input');
        if (hexInput) {
            hexInput.value = value;
        }
    }
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
    const currentValue = getNestedValue(CONFIG_STATE.config, path);
    if (isDeepEqual(currentValue, value)) {
        return; // Wert hat sich nicht geändert -> No-Op
    }
    setNestedValue(CONFIG_STATE.config, path, value);
    recordChange('settings', path, value);
    updateQuickActionsPanel();
}

function updateWebConfig(path, value) {
    const currentValue = getNestedValue(CONFIG_STATE.webConfig, path);
    if (isDeepEqual(currentValue, value)) {
        return; // Wert hat sich nicht geändert -> No-Op
    }
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
    const mvStatus = getMvStatus(worldId);

    // Nur was der Serverzustand hergibt: laden/entladen nur bei existierender Welt und
    // ansprechbarem Multiverse, sonst der Hinweis, dass es hier nur ein Preset gibt.
    let mvActions = '';
    if (MV_STATE.loaded) {
        if (mvStatus.state === 'loaded' && MV_STATE.available) {
            mvActions = `<button class="btn btn-secondary btn-sm" onclick="mvUnloadWorld('${escapeAttr(worldId)}')">
                            <i class="fas fa-eject"></i> ${i18n.t('button.mvUnload')}</button>`;
        } else if (mvStatus.state === 'unloaded' && MV_STATE.available) {
            mvActions = `<button class="btn btn-secondary btn-sm" onclick="mvLoadWorld('${escapeAttr(worldId)}')">
                            <i class="fas fa-play"></i> ${i18n.t('button.mvLoad')}</button>`;
        } else if (mvStatus.state === 'placeholder' && MV_STATE.available) {
            mvActions = `<button class="btn btn-secondary btn-sm" onclick="editWorld('${escapeAttr(worldId)}', 'multiverse')">
                            <i class="fas fa-wand-magic-sparkles"></i> ${i18n.t('button.mvCreateWorld')}</button>`;
        }
    }

    return `
        <div class="card">
            <div class="card-header">
                <div class="card-title">
                    <i class="fas fa-globe" style="color: ${isPvPEnabled ? 'var(--error)' : 'var(--info)'};"></i>
                    <span>${config['display-name'] || worldId}</span>
                    ${isPvPEnabled ? `<span class="badge badge-error">${i18n.t('card.pvpActive')}</span>` : `<span class="badge badge-info">${i18n.t('card.eventWorld')}</span>`}
                    <span class="badge ${mvStatus.badge}" title="${i18n.t('card.mvStatusHint')}">
                        <i class="fas ${mvStatus.icon}"></i> ${mvStatus.label}
                    </span>
                </div>
                <div class="card-actions">
                    ${mvActions}
                    <button class="btn btn-secondary btn-icon" onclick="editWorld('${escapeAttr(worldId)}')" title="${i18n.t('button.edit')}">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn btn-danger btn-icon" onclick="deleteWorld('${escapeAttr(worldId)}')" title="${i18n.t('button.delete')}">
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

// ============================================
// Multiverse: Server-Welten
// ============================================

// Spiegel des tatsaechlichen Weltbestands auf dem Server. Wird beim Laden und nach jeder
// Weltoperation aufgefrischt; CONFIG_STATE bleibt davon unberuehrt (das ist die YAML-Sicht).
const MV_STATE = {
    available: false,
    backend: 'NONE',
    supportsAdvancedOptions: false,
    worlds: [],
    loaded: false,
    // true, wenn die letzte Abfrage fehlschlug: `worlds` ist dann der letzte bekannte Stand.
    stale: false
};

/**
 * Holt den Weltbestand vom Server.
 *
 * Schlaegt die Abfrage fehl, bleibt der zuletzt bekannte Stand stehen und `stale` wird gesetzt.
 * Frueher wurde hier stillschweigend auf eine leere Liste zurueckgefallen -- dann sah jede
 * konfigurierte Welt wie ein Platzhalter aus und das Panel bot "Welt erstellen" an, obwohl die
 * Welt existierte und nur gerade nicht abgefragt werden konnte.
 *
 * @returns {boolean} true, wenn frische Daten geholt wurden
 */
async function loadMvWorlds() {
    try {
        const response = await fetch('/api/mvworlds/list', { credentials: 'include' });
        const json = response.ok ? await response.json() : null;
        if (!json || json.success === false) {
            MV_STATE.stale = true;
            showToast(i18n.t('toast.mvListFailed', { message: mvErrorText(json) }), 'error');
            return false;
        }
        const data = json.data || {};
        MV_STATE.available = data.available === true;
        MV_STATE.backend = data.backend || 'NONE';
        MV_STATE.supportsAdvancedOptions = data.supportsAdvancedOptions === true;
        MV_STATE.worlds = Array.isArray(data.worlds) ? data.worlds : [];
        MV_STATE.loaded = true;
        MV_STATE.stale = false;
        return true;
    } catch (error) {
        console.warn('Multiverse world list failed:', error);
        MV_STATE.stale = true;
        showToast(i18n.t('toast.mvListFailed', { message: i18n.t('mv.error.listFailed') }), 'error');
        return false;
    }
}

/** Weltliste erneut anfordern und die Ansichten neu zeichnen. */
async function refreshMvWorlds() {
    await loadMvWorlds();
    renderWorldsList();
    renderServerWorldsPanel();
    if (typeof refreshWorldIdDependentUi === 'function' && currentEditingWorld) {
        refreshWorldIdDependentUi();
    }
}

/** Server-Zustand einer World-ID, oder null wenn es dazu keine Welt gibt (= Platzhalter). */
function getMvWorld(worldId) {
    if (!worldId) return null;
    const needle = String(worldId).toLowerCase();
    return MV_STATE.worlds.find(w => String(w.name).toLowerCase() === needle) || null;
}

/**
 * Liefert Badge-Text/Farbe fuer eine World-ID.
 * Drei Zustaende: geladen, entladen (Ordner da, aber nicht aktiv), reiner Platzhalter.
 */
function getMvStatus(worldId) {
    // Ohne verlaesslichen Serverstand darf hier nicht "Platzhalter" behauptet werden -- das
    // ist eine Aussage ueber die Welt, die wir gerade nicht treffen koennen.
    if (!MV_STATE.loaded || MV_STATE.stale) {
        return { state: 'unknown', label: i18n.t('card.mvUnknown'), badge: 'badge-secondary', icon: 'fa-circle-question' };
    }
    const world = getMvWorld(worldId);
    if (!world) {
        return { state: 'placeholder', label: i18n.t('card.mvPlaceholder'), badge: 'badge-secondary', icon: 'fa-circle' };
    }
    if (world.loaded) {
        return { state: 'loaded', label: i18n.t('card.mvLoaded'), badge: 'badge-success', icon: 'fa-circle-check' };
    }
    return { state: 'unloaded', label: i18n.t('card.mvUnloaded'), badge: 'badge-warning', icon: 'fa-circle-pause' };
}

/** Fasst usedBy-Eintraege zu einem lesbaren "belegt von ..."-Text zusammen. */
function describeMvUsage(world, excludeWorldId) {
    if (!world || !Array.isArray(world.usedBy)) return '';
    const others = world.usedBy.filter(u =>
        !(excludeWorldId && u.type === 'world' && u.field === 'world-id' &&
          String(u.id).toLowerCase() === String(excludeWorldId).toLowerCase()));
    if (others.length === 0) return '';
    return others.map(u => `${u.id} (${u.field})`).join(', ');
}

/** Ob eine Server-Welt bereits Key eines World-Presets ist -- dann waere ein neues Preset ein Overwrite. */
function isWorldUsedAsPreset(worldName) {
    const presets = CONFIG_STATE.worlds?.worlds || {};
    return Object.keys(presets).some(id => id.toLowerCase() === String(worldName).toLowerCase());
}

/**
 * Startet eine Weltoperation und wartet, bis der Server-Job fertig ist.
 * Aufloesung: true bei Erfolg. Fehler werden als Toast gemeldet.
 */
/**
 * Baut aus einer Fehlerantwort bzw. einem fehlgeschlagenen Job den anzuzeigenden Satz.
 *
 * Der Server schickt bewusst keinen fertigen Text, sondern `messageKey` (ein Eintrag aus
 * diesen Sprachdateien) und optional `detail` -- untranslatierbarer Zusatz wie der
 * Original-Fehlertext von Multiverse oder der abgelehnte Wert.
 */
// ============================================
// Inventory Management
// ============================================

const INVENTORY_STATE = {
    provider: 'auto',
    activeProvider: '',
    managed: true,
    inventoryRestoreInstalled: false,
    multiverseInventoriesInstalled: false,
    mviGuardActive: false,
    mviRecoveries: 0,
    mviGroupsUnreadable: false,
    mviConflicts: [],
    safetyBackups: false,
    openSessions: 0,
    activeTab: 'explorer'
};

function switchInventoryTab(tabId) {
    INVENTORY_STATE.activeTab = tabId;
    ['explorer', 'sessions', 'settings'].forEach(id => {
        const btn = document.getElementById(`inv-tab-btn-${id}`);
        const pane = document.getElementById(`inv-tab-pane-${id}`);
        if (btn) btn.classList.toggle('active', id === tabId);
        if (pane) pane.style.display = (id === tabId) ? '' : 'none';
    });
    if (tabId === 'sessions') {
        loadInventoryGuard();
    }
}

/**
 * Holt den Zustand der Inventar-Verwaltung und zeichnet die Auswahl neu.
 */
async function loadInventoryStatus() {
    try {
        const response = await fetch('/api/inventories/status', { credentials: 'include' });
        const json = response.ok ? await response.json() : null;
        if (!json || json.success === false) {
            return false;
        }
        applyInventoryStatus(json.data || {});
        return true;
    } catch (error) {
        console.warn('Inventory status failed:', error);
        return false;
    }
}

function applyInventoryStatus(data) {
    INVENTORY_STATE.provider = data.provider || 'auto';
    INVENTORY_STATE.activeProvider = data.activeProvider || '';
    INVENTORY_STATE.managed = data.managed !== false;
    INVENTORY_STATE.inventoryRestoreInstalled = data.inventoryRestoreInstalled === true;
    INVENTORY_STATE.multiverseInventoriesInstalled = data.multiverseInventoriesInstalled === true;
    INVENTORY_STATE.mviGuardActive = data.mviGuardActive === true;
    INVENTORY_STATE.mviRecoveries = data.mviRecoveries || 0;
    INVENTORY_STATE.mviGroupsUnreadable = data.mviGroupsUnreadable === true;
    INVENTORY_STATE.mviConflicts = Array.isArray(data.mviConflicts) ? data.mviConflicts : [];
    INVENTORY_STATE.safetyBackups = data.safetyBackups === true;
    INVENTORY_STATE.openSessions = data.openSessions || 0;

    const select = document.getElementById('inventory-provider');
    if (select) {
        // Der Altwert 'inventoryrestore' steht nicht mehr im Dropdown; er verhielt sich
        // immer wie 'auto' und wird hier auch so dargestellt.
        select.value = INVENTORY_STATE.provider === 'none' ? 'none' : 'auto';
    }
    renderInventoryMode();
    renderGlobalMviBanner();
}

/** Beschreibungstext, Warnungen, KPI-Werte und Sichtbarkeit der Detaileinstellungen aktualisieren. */
function renderInventoryMode() {
    const mode = INVENTORY_STATE.provider;
    const legacy = mode === 'none';

    // KPI Cards
    const kpiProvider = document.getElementById('inv-kpi-provider');
    if (kpiProvider) {
        kpiProvider.textContent = legacy
            ? (INVENTORY_STATE.safetyBackups ? i18n.t('inventory.activeSafetyOnly') : i18n.t('inventory.activeNothing'))
            : i18n.t('inventory.activeProvider', {
                provider: i18n.t('inventory.provider.' + (INVENTORY_STATE.activeProvider || mode || 'none'))
            });
    }
    const kpiSessions = document.getElementById('inv-kpi-sessions');
    if (kpiSessions) {
        kpiSessions.textContent = i18n.t('inventory.openSessions', {
            count: String(INVENTORY_STATE.openSessions)
        });
    }
    const kpiSafety = document.getElementById('inv-kpi-safety');
    if (kpiSafety) {
        kpiSafety.textContent = INVENTORY_STATE.safetyBackups ? i18n.t('common.enabled') : i18n.t('common.disabled');
    }

    // Sidebar & Tab Badge
    const navBadge = document.getElementById('inventories-count');
    if (navBadge) {
        navBadge.textContent = String(INVENTORY_STATE.openSessions);
        navBadge.style.display = INVENTORY_STATE.openSessions > 0 ? '' : 'none';
    }
    const tabBadge = document.getElementById('inv-tab-sessions-badge');
    if (tabBadge) {
        tabBadge.textContent = String(INVENTORY_STATE.openSessions);
        tabBadge.style.display = INVENTORY_STATE.openSessions > 0 ? '' : 'none';
    }

    const description = document.getElementById('inventory-mode-description');
    if (description) {
        // Es gibt nur noch zwei Betriebsarten. Ein alter Config-Wert 'inventoryrestore'
        // faellt hier auf die Beschreibung von 'auto' - er tat auch nie etwas anderes.
        let descText = i18n.t('inventory.descAuto');
        let key = 'inventory.descAuto';
        if (mode === 'none') {
            descText = i18n.t('inventory.descLegacy');
            key = 'inventory.descLegacy';
        }
        const span = description.querySelector('span');
        if (span) {
            span.setAttribute('data-i18n', key);
            span.textContent = descText;
        }
        description.className = legacy ? 'alert alert-warning' : 'alert alert-info';
    }

    toggleDisplay('inventory-legacy-warning', legacy);
    toggleDisplay('inventory-legacy-settings', legacy);
    toggleDisplay('inventory-coexistence-warning',
        !legacy && INVENTORY_STATE.multiverseInventoriesInstalled);
    toggleDisplay('inventory-missing-warning',
        !legacy && !INVENTORY_STATE.inventoryRestoreInstalled);
    toggleDisplay('inventory-managed-settings', !legacy);

    renderInventoryCoexistence();
    renderInventoryWarnings();
}

/**
 * Fuellt die Koexistenz-Warnung mit den konkreten Weltkollisionen.
 *
 * <p>Ein Satz ueber "Weltgruppen aufloesen" hilft niemandem, der nicht weiss, welche Welt in
 * welcher Gruppe steht. Der Server liefert genau das - inklusive des Befehls, der es loest.
 */
function renderInventoryCoexistence() {
    const container = document.getElementById('inventory-coexistence-details');
    if (!container) return;

    if (INVENTORY_STATE.mviGroupsUnreadable) {
        container.innerHTML = `<div class="form-label-hint">${escapeHtml(i18n.t('inventory.mviGroupsUnreadable'))}</div>`;
        return;
    }

    const conflicts = INVENTORY_STATE.mviConflicts || [];
    if (conflicts.length === 0) {
        container.innerHTML = `<div class="form-label-hint">${escapeHtml(i18n.t('inventory.mviNoConflicts'))}</div>`;
        return;
    }

    const rows = conflicts.map(conflict => {
        const partners = (conflict.partnerWorlds || []).join(', ');
        const line = i18n.t('inventory.mviConflictEntry', {
            world: conflict.world || '',
            group: conflict.group || '',
            partners: partners
        });
        return `<li>${escapeHtml(line)}<br><code>${escapeHtml(conflict.fixCommand || '')}</code></li>`;
    }).join('');

    const guardNote = INVENTORY_STATE.mviGuardActive
        ? i18n.t('inventory.mviGuardActive', { count: String(INVENTORY_STATE.mviRecoveries) })
        : i18n.t('inventory.mviGuardInactive');

    container.innerHTML = `<ul style="margin: 0.5rem 0 0 1.1rem;">${rows}</ul>`
        + `<div class="form-label-hint" style="margin-top: 0.4rem;">${escapeHtml(guardNote)}</div>`;
}

/**
 * Blendet die Warnung an jedem Schalter ein, der gerade im riskanten Zustand steht.
 *
 * <p>Zustandsabhaengig und nicht dauerhaft: eine Warnung, die immer dasteht, wird nach dem
 * dritten Blick nicht mehr gelesen.
 */
function renderInventoryWarnings() {
    const checked = id => {
        const element = document.getElementById(id);
        return !!element && element.checked;
    };

    toggleDisplay('inventory-warn-match-end', !checked('inventory-restore-match-end'));
    toggleDisplay('inventory-warn-event-end', !checked('inventory-restore-event-end'));
    toggleDisplay('inventory-warn-respawn', !checked('inventory-restore-respawn'));
    toggleDisplay('inventory-warn-rejoin', !checked('inventory-restore-rejoin'));
    toggleDisplay('inventory-warn-cleanup', checked('inventory-cleanup-after-match'));
    toggleDisplay('inventory-warn-legacy-safety', !checked('inventory-legacy-safety'));

    const policy = document.getElementById('inventory-failure-policy');
    toggleDisplay('inventory-warn-failure-policy', !!policy && policy.value === 'warn');
}

/**
 * Der orangene Streifen unter der Kopfzeile.
 *
 * <p>Sichtbar genau dann, wenn Multiverse-Inventories laeuft und das Plugin die Inventare
 * selbst verwaltet. Im Legacy-Betrieb ist Multiverse-Inventories gewollt - dann waere die
 * Warnung falsch.
 */
function renderGlobalMviBanner() {
    const banner = document.getElementById('global-mvi-banner');
    if (!banner) return;

    const conflicting = INVENTORY_STATE.multiverseInventoriesInstalled
        && INVENTORY_STATE.provider !== 'none';
    banner.style.display = conflicting ? '' : 'none';
    if (!conflicting) return;

    const collapsed = localStorage.getItem('mviBannerCollapsed') === '1';
    banner.classList.toggle('is-collapsed', collapsed);
    const icon = document.querySelector('#global-mvi-banner-toggle i');
    if (icon) {
        icon.className = collapsed ? 'fas fa-chevron-down' : 'fas fa-chevron-up';
    }
}

/** Klappt den Streifen auf die Ueberschrift zusammen - wegklicken laesst er sich nicht. */
function toggleGlobalMviBanner() {
    const collapsed = localStorage.getItem('mviBannerCollapsed') === '1';
    localStorage.setItem('mviBannerCollapsed', collapsed ? '0' : '1');
    renderGlobalMviBanner();
}

/** Springt aus dem Streifen in die Einstellungen, wo die Kollisionen im Detail stehen. */
function showInventoryConflictDetails() {
    showSection('inventories');
    switchInventoryTab('settings');
}

function toggleDisplay(elementId, visible) {
    const element = document.getElementById(elementId);
    if (element) {
        element.style.display = visible ? '' : 'none';
    }
}

/**
 * Schaltet die Betriebsart um.
 *
 * <p>Der Weg in den Legacy-Betrieb wird nicht kommentarlos gegangen: danach stellt das
 * Plugin nichts mehr von selbst wieder her. Die eigentliche Sperre bei offenen Sitzungen
 * sitzt im Server - hier steht nur die Rueckfrage davor.
 */
async function setInventoryProvider(mode) {
    if (mode === 'none' && !confirm(i18n.t('inventory.legacyConfirm'))) {
        const select = document.getElementById('inventory-provider');
        if (select) {
            select.value = INVENTORY_STATE.provider === 'none' ? 'none' : 'auto';
        }
        return;
    }

    try {
        const response = await fetch('/api/inventories/provider', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ provider: mode })
        });
        const json = response.ok ? await response.json() : null;
        if (!json || json.success === false) {
            showToast(inventoryErrorText(json), 'error');
            await loadInventoryStatus();
            return;
        }
        applyInventoryStatus(json.data || {});
        if (CONFIG_STATE.config && CONFIG_STATE.config.settings
                && CONFIG_STATE.config.settings['inventory-management']) {
            CONFIG_STATE.config.settings['inventory-management'].provider = mode;
        }
        if (CONFIG_BASELINE.config && CONFIG_BASELINE.config.settings
                && CONFIG_BASELINE.config.settings['inventory-management']) {
            CONFIG_BASELINE.config.settings['inventory-management'].provider = mode;
        }
        if (CONFIG_STATE.config) {
            localStorage.setItem('config_backup', JSON.stringify(CONFIG_STATE.config));
        }
        updateSyncStatusUI();
        showToast(i18n.t('inventory.switched', { mode: i18n.t('inventory.provider.' + mode) }), 'success');
    } catch (error) {
        console.error('Inventory provider switch failed:', error);
        showToast(i18n.t('inventory.error.switchFailed'), 'error');
    }
}

/** Offene Sitzungen des Guard-Journals anzeigen und Quick-Player Pills aktualisieren. */
async function loadInventoryGuard() {
    const container = document.getElementById('inventory-guard-list');
    if (!container) return;

    try {
        const response = await fetch('/api/inventories/guard', { credentials: 'include' });
        const json = response.ok ? await response.json() : null;
        if (!json || json.success === false) {
            container.innerHTML = `<p class="form-label-hint">${escapeHtml(inventoryErrorText(json))}</p>`;
            return;
        }
        const sessions = (json.data && json.data.sessions) || [];
        const returnLocations = (json.data && json.data.returnLocations) || [];
        INVENTORY_STATE.openSessions = sessions.length;
        renderInventoryMode();

        // Extract online & active players for quick pills
        const quickPlayers = new Set();
        sessions.forEach(s => { if (s.playerName) quickPlayers.add(s.playerName); });
        returnLocations.forEach(r => { if (r.playerName) quickPlayers.add(r.playerName); });
        try {
            const recents = JSON.parse(localStorage.getItem('inv_recent_players') || '[]');
            recents.forEach(p => quickPlayers.add(p));
        } catch (_) {}
        refreshOnlinePlayersList(Array.from(quickPlayers));

        let html = '';
        if (sessions.length === 0) {
            html += `<p class="form-label-hint">${escapeHtml(i18n.t('inventory.guardEmpty'))}</p>`;
        } else {
            html += sessions.map(session => {
                const phaseClass = session.phase === 'orphaned' ? 'badge-error' : 'badge-warning';
                return `<div class="form-group" style="display:flex;align-items:center;gap:0.75rem;padding:0.5rem 0;border-bottom:1px solid var(--border);">
                    <span class="badge ${phaseClass}">${escapeHtml(i18n.t('inventory.phase.' + session.phase))}</span>
                    <strong style="cursor:pointer;color:var(--primary);" onclick="selectPlayerForInventory('${escapeAttr(session.playerName || session.player)}')">${escapeHtml(session.playerName || session.player)}</strong>
                    <span class="form-label-hint">${escapeHtml(i18n.t('inventory.context.' + session.context))}
                        &middot; ${escapeHtml(session.backupId || i18n.t('inventory.noBackup'))}</span>
                </div>`;
            }).join('');
        }

        if (returnLocations.length > 0) {
            html += `<div class="form-section-title" style="margin-top:1.25rem;">
                ${escapeHtml(i18n.t('inventory.returnTitle'))}
            </div>
            <p class="form-label-hint">${escapeHtml(i18n.t('inventory.returnHint'))}</p>`;
            html += returnLocations.map(entry => `
                <div class="form-group" style="display:flex;align-items:center;gap:0.75rem;padding:0.5rem 0;border-bottom:1px solid var(--border);">
                    <span class="badge ${entry.online ? 'badge-warning' : ''}">${escapeHtml(entry.world || '?')}</span>
                    <strong style="cursor:pointer;color:var(--primary);" onclick="selectPlayerForInventory('${escapeAttr(entry.playerName || entry.player)}')">${escapeHtml(entry.playerName || entry.player)}</strong>
                    <span class="form-label-hint">
                        ${escapeHtml(formatReturnCoords(entry))}
                    </span>
                </div>
            `).join('');
        }
        container.innerHTML = html;
    } catch (error) {
        console.error('Inventory guard failed:', error);
        container.innerHTML = `<p class="form-label-hint">${escapeHtml(i18n.t('inventory.error.unavailable'))}</p>`;
    }
}

function refreshOnlinePlayersList(players) {
    const quickBar = document.getElementById('inv-online-quick-bar');
    const pillsContainer = document.getElementById('inv-online-players-list');
    if (!quickBar || !pillsContainer) return;

    const datalist = document.getElementById('inv-recent-players-list');
    if (datalist) {
        let recents = [];
        try { recents = JSON.parse(localStorage.getItem('inv_recent_players') || '[]'); } catch (_) {}
        const safePlayers = players || [];
        const allNames = new Set([...safePlayers, ...recents]);
        datalist.innerHTML = Array.from(allNames).map(name => `<option value="${escapeAttr(name)}"></option>`).join('');
    }

    if (!players || players.length === 0) {
        quickBar.style.display = 'none';
        return;
    }

    quickBar.style.display = 'flex';
    pillsContainer.innerHTML = players.slice(0, 10).map(p => `
        <div class="inv-player-pill" onclick="selectPlayerForInventory('${escapeAttr(p)}')">
            <span class="online-dot"></span>
            <span>${escapeHtml(p)}</span>
        </div>
    `).join('');
}

function selectPlayerForInventory(playerName) {
    const input = document.getElementById('inventory-browser-player');
    if (input) {
        input.value = playerName;
        loadInventoryBackups();
    }
}

// ============================================
// Inventar-Backup-Explorer
// ============================================

const INVENTORY_BROWSER = {
    player: null,
    playerInput: '',
    playerOnline: false,
    backups: [],
    selectedBackupId: null,
    selectedBackupData: null,
    activeFilter: 'ALL'
};

/** Sucht die Backups eines Spielers. */
async function loadInventoryBackups() {
    const input = document.getElementById('inventory-browser-player');
    const list = document.getElementById('inventory-browser-list');
    const preview = document.getElementById('inventory-browser-preview');
    if (!input || !list) return;

    const query = input.value.trim();
    if (!query) {
        list.innerHTML = `<div class="inv-empty-state"><i class="fas fa-boxes-packing"></i><p>${escapeHtml(i18n.t('inventory.searchHint'))}</p></div>`;
        if (preview) {
            preview.innerHTML = `<div class="inv-empty-state"><i class="fas fa-eye"></i><p>${escapeHtml(i18n.t('inventory.selectBackupHint'))}</p></div>`;
        }
        return;
    }

    list.innerHTML = `<p class="form-label-hint" style="padding:1rem;"><i class="fas fa-circle-notch fa-spin"></i> ${escapeHtml(i18n.t('inventory.browser.loading'))}</p>`;

    try {
        const response = await fetch(`/api/inventories/list?player=${encodeURIComponent(query)}`,
            { credentials: 'include' });
        const json = response.ok ? await response.json() : null;
        if (!json || json.success === false) {
            list.innerHTML = `<p class="form-label-hint" style="padding:1rem;color:var(--error);">${escapeHtml(inventoryErrorText(json))}</p>`;
            return;
        }

        INVENTORY_BROWSER.player = json.data.player;
        INVENTORY_BROWSER.playerInput = query;
        INVENTORY_BROWSER.playerOnline = !!json.data.online;
        INVENTORY_BROWSER.selectedBackupId = null;
        INVENTORY_BROWSER.selectedBackupData = null;

        // Remember in recents
        try {
            const recents = JSON.parse(localStorage.getItem('inv_recent_players') || '[]');
            const updated = [query, ...recents.filter(p => p.toLowerCase() !== query.toLowerCase())].slice(0, 8);
            localStorage.setItem('inv_recent_players', JSON.stringify(updated));
        } catch (_) {}

        INVENTORY_BROWSER.backups = (json.data.backups || [])
            .slice()
            .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));

        renderInventoryBackupList();

        // Auto-preview first backup if available
        if (INVENTORY_BROWSER.backups.length > 0) {
            previewInventoryBackup(INVENTORY_BROWSER.backups[0].id);
        } else if (preview) {
            preview.innerHTML = `<div class="inv-empty-state"><i class="fas fa-box-open"></i><p>${escapeHtml(i18n.t('inventory.browser.empty'))}</p></div>`;
        }
    } catch (error) {
        console.error('Inventory backup list failed:', error);
        list.innerHTML = `<p class="form-label-hint" style="padding:1rem;color:var(--error);">${escapeHtml(i18n.t('inventory.error.unavailable'))}</p>`;
    }
}

function filterInventoryBackups(filterType) {
    INVENTORY_BROWSER.activeFilter = filterType;
    renderInventoryBackupList();
}

function renderInventoryBackupList() {
    const list = document.getElementById('inventory-browser-list');
    const filterContainer = document.getElementById('inv-filter-container');
    const filterSelect = document.getElementById('inv-backup-filter-select');
    if (filterSelect) {
        const optionAll = filterSelect.querySelector('#inv-filter-all-option');
        if (optionAll && INVENTORY_BROWSER.backups) {
            optionAll.textContent = i18n.t('inventory.filterAll', { count: String(INVENTORY_BROWSER.backups.length) });
        }
    }
    
    if (!list) return;

    if (filterContainer) {
        filterContainer.style.display = INVENTORY_BROWSER.backups.length > 0 ? '' : 'none';
    }

    if (INVENTORY_BROWSER.backups.length === 0) {
        list.innerHTML = `<div class="inv-empty-state"><i class="fas fa-box-open"></i><p>${escapeHtml(i18n.t('inventory.browser.empty'))}</p></div>`;
        return;
    }

    const filtered = INVENTORY_BROWSER.backups.filter(backup => {
        if (INVENTORY_BROWSER.activeFilter === 'ALL') return true;
        if (INVENTORY_BROWSER.activeFilter === 'manual') {
            return backup.type === 'manual' || backup.type === 'web';
        }
        if (INVENTORY_BROWSER.activeFilter === 'pvp_match') {
            return backup.type === 'pvp-pre-match' || backup.type === 'pvp-post-match';
        }
        if (INVENTORY_BROWSER.activeFilter === 'event') {
            return backup.type === 'event-pre-join' || backup.type === 'event-post';
        }
        return backup.type === INVENTORY_BROWSER.activeFilter;
    });

    if (filtered.length === 0) {
        list.innerHTML = `<div class="inv-empty-state"><i class="fas fa-filter"></i><p>${escapeHtml(i18n.t('picker.noResults'))}</p></div>`;
        return;
    }

    const listHeader = `<div class="form-section-title" style="margin-bottom:0.5rem;font-size:0.85rem;">${escapeHtml(i18n.t('inventory.browser.found', { count: String(filtered.length) }))}</div>`;

    list.innerHTML = listHeader + filtered.map(backup => {
        const isActive = backup.id === INVENTORY_BROWSER.selectedBackupId;
        // Backup-Typen sind hyphenierte Detailstrings (z.B. "pvp-pre-match"), waehrend
        // die inventory.context.* Keys die groebere Guard-Session-Kategorie abbilden
        // (pvp_match/event/web/manual). Auf die passende Kategorie zusammenfassen, statt
        // den rohen Typ als Key zu missbrauchen.
        const typeCategory = /^pvp-/.test(backup.type || '') ? 'pvp_match'
            : /^event-/.test(backup.type || '') ? 'event'
            : (backup.type === 'web' ? 'web' : 'manual');
        const typeKey = 'inventory.context.' + typeCategory;
        const typeLabel = i18n.strings[typeKey] ? i18n.t(typeKey) : (backup.type || '?');
        const badgeClass = typeCategory === 'event' ? 'badge-primary' : (typeCategory === 'pvp_match' ? 'badge-info' : 'badge-secondary');

        return `
            <div class="inv-backup-item ${isActive ? 'active' : ''}" onclick="previewInventoryBackup('${escapeAttr(backup.id)}')">
                <div class="inv-backup-header">
                    <span class="badge ${badgeClass}">${escapeHtml(typeLabel)}</span>
                    <span class="inv-backup-date">${escapeHtml(formatBackupDate(backup.createdAt))}</span>
                </div>
                <div class="inv-backup-meta">
                    <span style="font-family:monospace;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="${escapeAttr(backup.id)}">
                        ${escapeHtml(backup.id)}
                    </span>
                </div>
                <div class="inv-backup-actions" onclick="event.stopPropagation()">
                    <button class="btn btn-primary btn-sm" onclick="openRestoreModal('${escapeAttr(backup.id)}')" title="${escapeHtml(i18n.t('inventory.browser.restore'))}">
                        <i class="fas fa-rotate-left"></i>
                    </button>
                    <button class="btn btn-danger btn-sm" onclick="deleteInventoryBackup('${escapeAttr(backup.id)}')" title="${escapeHtml(i18n.t('inventory.browser.delete'))}">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </div>
        `;
    }).join('');
}

function formatBackupDate(createdAt) {
    if (!createdAt) return i18n.t('inventory.browser.unknownDate');
    const date = new Date(Number(createdAt));
    return isNaN(date.getTime()) ? String(createdAt) : date.toLocaleString();
}

/** Laedt ein Backup und zeigt es im Minecraft-Inventargitter. */
async function previewInventoryBackup(backupId) {
    const preview = document.getElementById('inventory-browser-preview');
    if (!preview) return;

    INVENTORY_BROWSER.selectedBackupId = backupId;
    renderInventoryBackupList();

    preview.innerHTML = `<p class="form-label-hint" style="padding:2rem;text-align:center;"><i class="fas fa-circle-notch fa-spin"></i> ${escapeHtml(i18n.t('inventory.browser.loading'))}</p>`;

    try {
        const url = `/api/inventories/get?player=${encodeURIComponent(INVENTORY_BROWSER.player)}`
            + `&id=${encodeURIComponent(backupId)}`;
        const response = await fetch(url, { credentials: 'include' });
        const json = response.ok ? await response.json() : null;
        if (!json || json.success === false) {
            preview.innerHTML = `<p class="form-label-hint" style="padding:2rem;color:var(--error);text-align:center;">${escapeHtml(inventoryErrorText(json))}</p>`;
            return;
        }
        INVENTORY_BROWSER.selectedBackupData = json.data;
        renderInventoryBackupPreview(json.data);
    } catch (error) {
        console.error('Inventory backup preview failed:', error);
        preview.innerHTML = `<p class="form-label-hint" style="padding:2rem;color:var(--error);text-align:center;">${escapeHtml(i18n.t('inventory.error.loadFailed'))}</p>`;
    }
}

/**
 * Zeichnet ein Backup als authentisches Minecraft-Inventar mit XP-Leiste, Rüstung, Offhand und Stats.
 */
function renderInventoryBackupPreview(data) {
    const preview = document.getElementById('inventory-browser-preview');
    const headerActions = document.getElementById('inv-viewer-header-actions');
    if (!preview) return;
    if (headerActions) headerActions.style.display = '';

    const bySlot = new Map();
    (data.contents || []).forEach(item => bySlot.set(item.slot, item));

    // Bukkit liefert in contents 41 Slots: 0-8 Hotbar, 9-35 Storage, 36-39 Ruestung, 40 Off-Hand.
    // Ruestung und Off-Hand kommen zusaetzlich in data.armor bzw. data.offhand und werden unten
    // von dort gezaehlt - contents darf deshalb nur bis LAST_STORAGE_SLOT ausgewertet werden,
    // sonst zaehlen beide doppelt.
    const LAST_STORAGE_SLOT = 35;
    const STORAGE_SLOT_COUNT = LAST_STORAGE_SLOT + 1;
    const COUNTED_SLOTS = STORAGE_SLOT_COUNT + 1; // + Off-Hand; Ruestung hat eine eigene Karte

    // Stats calculations
    let totalItems = 0;
    let occupiedSlots = 0;
    (data.contents || []).forEach(item => {
        if (item && item.amount && item.slot <= LAST_STORAGE_SLOT) {
            totalItems += item.amount;
            occupiedSlots++;
        }
    });
    let armorPieces = 0;
    (data.armor || []).forEach(item => {
        if (item && item.material) {
            armorPieces++;
            totalItems += (item.amount || 1);
        }
    });
    if (data.offhand && data.offhand.material) {
        totalItems += (data.offhand.amount || 1);
        occupiedSlots++;
    }

    const xpPercent = Math.min(100, Math.max(0, Math.round((data.exp || 0) * 100)));
    const xpLevel = data.level || 0;
    const levelExpTitle = i18n.t('inventory.browser.levelExp', {
        level: String(xpLevel),
        exp: String(xpPercent)
    });

    const cell = (item, slotIndex) => {
        if (!item || !item.material) {
            return `<div class="inventory-slot" data-slot="${slotIndex != null ? slotIndex : ''}"></div>`;
        }
        const itemJsonAttr = escapeAttr(JSON.stringify(item));
        return `<div class="inventory-slot filled" data-slot="${slotIndex != null ? slotIndex : ''}"
                     onmouseenter="showMinecraftTooltip(event, ${itemJsonAttr})"
                     onmousemove="moveMinecraftTooltip(event)"
                     onmouseleave="hideMinecraftTooltip()">
            ${itemIconHtml(item.material, 32)}
            ${item.amount > 1 ? `<span class="amount">${item.amount}</span>` : ''}
            ${item.enchantments && Object.keys(item.enchantments).length > 0 ? '<span class="enchant-indicator">✨</span>' : ''}
        </div>`;
    };

    // Bukkit main inventory: 9-35
    const main = [];
    for (let slot = 9; slot <= 35; slot++) main.push(cell(bySlot.get(slot), slot));

    // Bukkit hotbar: 0-8
    const hotbar = [];
    for (let slot = 0; slot <= 8; slot++) hotbar.push(cell(bySlot.get(slot), slot));

    // Armor: 3=Helmet, 2=Chestplate, 1=Leggings, 0=Boots
    const armorBySlot = new Map();
    (data.armor || []).forEach(item => armorBySlot.set(item.slot, item));
    const armorOrder = [
        { slot: 3, label: i18n.t('editor.helmet'), icon: 'fa-hat-wizard' },
        { slot: 2, label: i18n.t('editor.chestplate'), icon: 'fa-vest' },
        { slot: 1, label: i18n.t('editor.leggings'), icon: 'fa-socks' },
        { slot: 0, label: i18n.t('editor.boots'), icon: 'fa-shoe-prints' }
    ];

    preview.innerHTML = `
        <div class="inv-minecraft-canvas">
            <!-- XP Bar -->
            <div class="inv-xp-container" title="${escapeAttr(levelExpTitle)}">
                <span class="inv-xp-text">${xpLevel}</span>
                <div class="inv-xp-bar-track">
                    <div class="inv-xp-bar-fill" style="width: ${xpPercent}%;"></div>
                </div>
            </div>

            <!-- Canvas Center: Armor/Offhand + Main/Hotbar Grid -->
            <div class="inv-layout-wrapper">
                <!-- Armor & Offhand Column -->
                <div class="inv-armor-offhand-col">
                    <div class="form-label-hint" style="font-size:0.75rem;text-transform:uppercase;margin-bottom:0.25rem;">
                        ${escapeHtml(i18n.t('editor.tabArmor'))}
                    </div>
                    ${armorOrder.map(entry => `
                        <div class="inv-armor-slot-row">
                            ${cell(armorBySlot.get(entry.slot), entry.slot)}
                            <span class="inv-armor-label">${escapeHtml(entry.label)}</span>
                        </div>
                    `).join('')}
                    <div class="inv-section-divider"></div>
                    <div class="inv-armor-slot-row">
                        ${cell(data.offhand, 40)}
                        <span class="inv-armor-label">${escapeHtml(i18n.t('editor.offhand'))}</span>
                    </div>
                </div>

                <!-- Main Inventory & Hotbar -->
                <div class="inv-main-storage-container">
                    <div class="form-label-hint" style="font-size:0.75rem;text-transform:uppercase;">
                        ${escapeHtml(i18n.t('editor.tabInventory'))}
                    </div>
                    <div class="inventory-grid">${main.join('')}</div>
                    <div class="inv-section-divider"></div>
                    <div class="inventory-grid">${hotbar.join('')}</div>
                </div>
            </div>

            <!-- Stats KPI Cards -->
            <div class="inv-stats-grid">
                <div class="inv-stat-card">
                    <span class="inv-stat-value">${totalItems}</span>
                    <span class="inv-stat-label" data-i18n="inventory.stats.totalItems">${escapeHtml(i18n.t('inventory.stats.totalItems'))}</span>
                </div>
                <div class="inv-stat-card">
                    <span class="inv-stat-value">${occupiedSlots} / ${COUNTED_SLOTS}</span>
                    <span class="inv-stat-label" data-i18n="inventory.stats.uniqueSlots">${escapeHtml(i18n.t('inventory.stats.uniqueSlots'))}</span>
                </div>
                <div class="inv-stat-card">
                    <span class="inv-stat-value">${armorPieces} / 4</span>
                    <span class="inv-stat-label" data-i18n="inventory.stats.armorScore">${escapeHtml(i18n.t('inventory.stats.armorScore'))}</span>
                </div>
            </div>

            <!-- Action Toolbar -->
            <div class="inv-actions-toolbar" style="display:flex;gap:0.75rem;flex-wrap:wrap;justify-content:flex-end;margin-top:0.75rem;padding-top:0.75rem;border-top:1px solid #333336;">
                <button class="btn btn-secondary btn-sm" onclick="copyCurrentBackupJson()" title="${escapeAttr(i18n.t('inventory.rawJson'))}">
                    <i class="fas fa-copy"></i>
                    <span data-i18n="inventory.copyJson">${escapeHtml(i18n.t('inventory.copyJson'))}</span>
                </button>
                <button class="btn btn-secondary btn-sm" onclick="exportCurrentBackupToEquipment()">
                    <i class="fas fa-shield-alt"></i>
                    <span data-i18n="inventory.exportEquipment">${escapeHtml(i18n.t('inventory.exportEquipment'))}</span>
                </button>
                <button class="btn btn-primary btn-sm" onclick="openRestoreModal('${escapeAttr(data.id)}')">
                    <i class="fas fa-rotate-left"></i>
                    <span data-i18n="inventory.browser.restore">${escapeHtml(i18n.t('inventory.browser.restore'))}</span>
                </button>
            </div>
        </div>
    `;
}

// ============================================
// Minecraft Floating Tooltip
// ============================================

function showMinecraftTooltip(e, item) {
    const tooltip = document.getElementById('minecraft-tooltip');
    if (!tooltip || !item) return;

    const title = item.displayName ? formatMinecraftColorCodes(item.displayName) : escapeHtml(itemDisplayName(item.material));
    let enchantsHtml = '';
    if (item.enchantments && Object.keys(item.enchantments).length > 0) {
        enchantsHtml = Object.entries(item.enchantments).map(([k, lvl]) => `
            <div class="minecraft-tooltip-enchant">
                <i class="fas fa-sparkles"></i> ${escapeHtml(itemDisplayName(k))} ${escapeHtml(String(lvl))}
            </div>
        `).join('');
    }

    let loreHtml = '';
    if (item.lore && item.lore.length > 0) {
        loreHtml = item.lore.map(l => formatMinecraftColorCodes(l)).join('<br>');
    }

    tooltip.innerHTML = `
        <div class="minecraft-tooltip-name">${title}</div>
        ${enchantsHtml}
        ${loreHtml ? `<div class="minecraft-tooltip-lore">${loreHtml}</div>` : ''}
        <div class="minecraft-tooltip-meta">
            <span>${escapeHtml(item.material)} &middot; x${item.amount || 1}</span>
        </div>
    `;

    tooltip.style.display = 'block';
    moveMinecraftTooltip(e);
}

function moveMinecraftTooltip(e) {
    const tooltip = document.getElementById('minecraft-tooltip');
    if (!tooltip || tooltip.style.display === 'none') return;

    const x = e.clientX + 16;
    const y = e.clientY + 16;
    const pad = 12;
    const maxX = window.innerWidth - tooltip.offsetWidth - pad;
    const maxY = window.innerHeight - tooltip.offsetHeight - pad;

    tooltip.style.left = `${Math.min(x, maxX)}px`;
    tooltip.style.top = `${Math.min(y, maxY)}px`;
}

function hideMinecraftTooltip() {
    const tooltip = document.getElementById('minecraft-tooltip');
    if (tooltip) tooltip.style.display = 'none';
}

function formatMinecraftColorCodes(text) {
    if (!text) return '';
    const colorMap = {
        '0': '#000000', '1': '#0000AA', '2': '#00AA00', '3': '#00AAAA',
        '4': '#AA0000', '5': '#AA00AA', '6': '#FFAA00', '7': '#AAAAAA',
        '8': '#555555', '9': '#5555FF', 'a': '#55FF55', 'b': '#55FFFF',
        'c': '#FF5555', 'd': '#FF55FF', 'e': '#FFFF55', 'f': '#FFFFFF'
    };

    let safe = escapeHtml(text);
    safe = safe.replace(/[§&]([0-9a-fA-F])/g, (match, code) => {
        const c = colorMap[code.toLowerCase()] || '#ffffff';
        return `</span><span style="color:${c};">`;
    });
    safe = safe.replace(/[§&]l/g, '<span style="font-weight:bold;">');
    safe = safe.replace(/[§&]o/g, '<span style="font-style:italic;">');
    safe = safe.replace(/[§&]r/g, '</span>');

    return `<span>${safe}</span>`;
}

// ============================================
// Restore Modal Actions
// ============================================

let CURRENT_RESTORE_BACKUP_ID = null;

function openRestoreModal(backupId) {
    CURRENT_RESTORE_BACKUP_ID = backupId;
    const modal = document.getElementById('modal-restore-inventory');
    const nameEl = document.getElementById('modal-restore-player-name');
    const uuidEl = document.getElementById('modal-restore-player-uuid');
    const statusEl = document.getElementById('modal-restore-player-status');
    const promptEl = document.getElementById('modal-restore-type-prompt');
    const inputEl = document.getElementById('modal-restore-confirm-input');

    if (!modal) return;

    const playerName = INVENTORY_BROWSER.playerInput || INVENTORY_BROWSER.player || 'Player';
    if (nameEl) nameEl.textContent = playerName;
    if (uuidEl) uuidEl.textContent = INVENTORY_BROWSER.player || '';
    const isOnline = !!INVENTORY_BROWSER.playerOnline;
    const statusText = isOnline ? i18n.t('inventory.modal.statusOnline') : i18n.t('inventory.modal.statusOffline');
    if (statusEl) {
        statusEl.innerHTML = `<span class="badge ${isOnline ? 'badge-info' : 'badge-secondary'}">${escapeHtml(statusText)}</span>`;
    }
    const confirmPromptText = i18n.t('inventory.browser.confirmRestore', { player: playerName });
    if (promptEl) {
        promptEl.textContent = i18n.t('inventory.modal.typeConfirmPrompt', { player: playerName });
        promptEl.title = confirmPromptText;
    }
    if (inputEl) {
        inputEl.value = '';
        inputEl.placeholder = playerName;
    }

    modal.classList.add('active');
    setTimeout(() => { if (inputEl) inputEl.focus(); }, 100);
}

function closeRestoreModal() {
    const modal = document.getElementById('modal-restore-inventory');
    if (modal) modal.classList.remove('active');
    CURRENT_RESTORE_BACKUP_ID = null;
}

async function executeRestoreFromModal() {
    const backupId = CURRENT_RESTORE_BACKUP_ID || INVENTORY_BROWSER.selectedBackupId;
    if (!backupId) return;

    const expected = (INVENTORY_BROWSER.playerInput || INVENTORY_BROWSER.player || '').trim().toLowerCase();
    const inputEl = document.getElementById('modal-restore-confirm-input');
    const typed = (inputEl ? inputEl.value : '').trim().toLowerCase();

    if (typed !== expected) {
        showToast(i18n.t('inventory.browser.confirmMismatch'), 'warning');
        return;
    }

    const clearBox = document.getElementById('modal-restore-clear-before');
    try {
        const response = await fetch('/api/inventories/restore', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({
                player: INVENTORY_BROWSER.player,
                backupId: backupId,
                clearBefore: clearBox ? clearBox.checked : true
            })
        });
        const json = await response.json();
        if (!json.success) {
            showToast(inventoryErrorText(json), 'error');
            return;
        }

        closeRestoreModal();
        if (json.data && json.data.queued) {
            showToast(i18n.t('inventory.browser.queued'), 'info');
        } else {
            showToast(i18n.t('inventory.browser.restored'), 'success');
        }
        loadInventoryGuard();
    } catch (error) {
        console.error('Inventory restore failed:', error);
        showToast(i18n.t('inventory.error.restoreFailed'), 'error');
    }
}

async function restoreInventoryBackup(backupId) {
    openRestoreModal(backupId);
}

async function deleteInventoryBackup(backupId) {
    if (!confirm(i18n.t('inventory.browser.confirmDelete'))) return;

    try {
        const response = await fetch('/api/inventories/delete', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ player: INVENTORY_BROWSER.player, backupId: backupId })
        });
        const json = await response.json();
        if (!json.success) {
            showToast(inventoryErrorText(json), 'error');
            return;
        }
        showToast(i18n.t('inventory.browser.deleted'), 'success');
        if (INVENTORY_BROWSER.selectedBackupId === backupId) {
            INVENTORY_BROWSER.selectedBackupId = null;
            INVENTORY_BROWSER.selectedBackupData = null;
            const preview = document.getElementById('inventory-browser-preview');
            if (preview) {
                preview.innerHTML = `<div class="inv-empty-state"><i class="fas fa-eye"></i><p>${escapeHtml(i18n.t('inventory.selectBackupHint'))}</p></div>`;
            }
        }
        INVENTORY_BROWSER.backups = INVENTORY_BROWSER.backups.filter(b => b.id !== backupId);
        renderInventoryBackupList();
    } catch (error) {
        console.error('Inventory delete failed:', error);
        showToast(i18n.t('inventory.error.deleteFailed'), 'error');
    }
}

// ============================================
// Equipment Set Export & Raw JSON Copy
// ============================================

function exportCurrentBackupToEquipment() {
    const data = INVENTORY_BROWSER.selectedBackupData;
    if (!data) return;

    const baseName = INVENTORY_BROWSER.playerInput || 'Player';
    const setId = `${baseName.toLowerCase().replace(/[^a-z0-9]/g, '_')}_backup_${Date.now().toString().slice(-4)}`;

    // Equipment-Sets kennen nur Material+Menge (keine Anzeigenamen/Lore/Verzauberungen) -
    // siehe addItemToSlot()/renderArmorSlot() in editors.js. Nicht mehr Felder vortaeuschen,
    // als der Editor tatsaechlich rendert/speichert.
    const armorSlotNames = { 3: 'helmet', 2: 'chestplate', 1: 'leggings', 0: 'boots' };
    const armor = { helmet: null, chestplate: null, leggings: null, boots: null };
    (data.armor || []).forEach(a => {
        const key = a ? armorSlotNames[a.slot] : undefined;
        if (key && a.material && a.material !== 'AIR') {
            armor[key] = a.material;
        }
    });

    const offhand = (data.offhand && data.offhand.material && data.offhand.material !== 'AIR')
        ? data.offhand.material
        : null;

    const inventory = (data.contents || [])
        .filter(c => c && c.material && c.material !== 'AIR' && Number.isInteger(c.slot) && c.slot >= 0 && c.slot <= 35)
        .map(c => ({ slot: c.slot, item: c.material, amount: c.amount || 1 }));

    const equipData = {
        'pvpwager-equip-enable': true,
        'event-equip-enable': true,
        'display-name': `${baseName} Backup (${new Date().toLocaleDateString()})`,
        'allowed-pvpwager-worlds': 'all',
        armor,
        offhand,
        inventory
    };

    equipmentSets()[setId] = equipData;
    recordChange('equipment', equipmentSetPath(setId), equipData);

    showToast(i18n.t('inventory.exportEquipmentSuccess'), 'success');
    showSection('equipment');
    if (typeof editEquipment === 'function') {
        editEquipment(setId);
    } else if (typeof renderEquipmentList === 'function') {
        renderEquipmentList();
    }
}

function copyCurrentBackupJson() {
    const data = INVENTORY_BROWSER.selectedBackupData;
    if (!data) return;

    const text = JSON.stringify(data, null, 2);
    navigator.clipboard.writeText(text).then(() => {
        showToast(i18n.t('inventory.jsonCopied'), 'success');
    }).catch(err => {
        console.error('Failed to copy JSON:', err);
        showToast('Copy failed', 'error');
    });
}

function refreshInventorySection() {
    loadInventoryStatus();
    loadInventoryGuard();
    if (INVENTORY_BROWSER.playerInput) {
        loadInventoryBackups();
    }
}

/**
 * Koordinaten einer Rueckkehrposition, ganzzahlig gerundet.
 * Weist zusaetzlich aus, wenn die Zielwelt gerade nicht geladen ist - dann laeuft die
 * Rueckkehr ins Leere und der Admin muss die Welt erst laden.
 */
function formatReturnCoords(entry) {
    const coords = `${Math.round(entry.x)} / ${Math.round(entry.y)} / ${Math.round(entry.z)}`;
    const reason = entry.reason ? ` · ${entry.reason}` : '';
    const missing = entry.worldLoaded === false ? ` · ${i18n.t('inventory.returnWorldMissing')}` : '';
    return coords + reason + missing;
}

/**
 * Loest die Server-Antwort einer fehlgeschlagenen API-Anfrage in Text auf.
 *
 * Der Server schickt bewusst keinen fertigen Satz, sondern nur `messageKey` (ein Eintrag
 * aus web/lang/*.json) und optional `detail` - das Panel uebersetzt in der Sprache des
 * Admins. Gemeinsamer Helfer fuer alle drei API-Familien (mv.error.*, inventory.error.*,
 * items.error.*), die genau dieses Schema benutzen.
 */
function apiErrorText(source, fallbackKey) {
    const key = (source && source.messageKey) || fallbackKey;
    const text = i18n.t(key);
    const detail = source && source.detail ? String(source.detail).trim() : '';
    return detail ? `${text} (${detail})` : text;
}

function inventoryErrorText(source) {
    return apiErrorText(source, 'inventory.error.unavailable');
}

function mvErrorText(source) {
    return apiErrorText(source, 'mv.error.generic');
}

async function runMvJob(url, payload, pendingMessage) {
    try {
        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify(payload)
        });
        const json = await response.json();
        if (!json.success || !json.jobId) {
            showToast(i18n.t('toast.mvFailed', { message: mvErrorText(json) }), 'error');
            return false;
        }

        if (pendingMessage) {
            showToast(pendingMessage, 'info');
        }

        const result = await pollMvJob(json.jobId);
        if (result && result.status === 'SUCCESS') {
            await loadMvWorlds();
            return true;
        }
        // Kein Ergebnis = Poll-Limit erreicht; der Job kann serverseitig noch laufen.
        showToast(i18n.t('toast.mvFailed', {
            message: result ? mvErrorText(result) : i18n.t('toast.mvTimeout')
        }), 'error');
        return false;
    } catch (error) {
        showToast(i18n.t('toast.mvFailed', { message: mvErrorText(null) }), 'error');
        console.warn('Multiverse request failed:', error);
        return false;
    }
}

/** Pollt den Job-Status im Sekundentakt. Deckelt bei 120 Versuchen (~2 Minuten). */
async function pollMvJob(jobId, maxAttempts = 120) {
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
        await new Promise(resolve => setTimeout(resolve, 1000));
        try {
            const response = await fetch(`/api/mvworlds/job?id=${encodeURIComponent(jobId)}`, { credentials: 'include' });
            if (!response.ok) continue;
            const json = await response.json();
            if (!json.success) continue;
            const job = json.data || {};
            if (job.status === 'SUCCESS' || job.status === 'FAILED') {
                return job;
            }
        } catch (error) {
            // Netzwerkhakler ueberspringen und weiter pollen.
        }
    }
    return null;
}

async function mvLoadWorld(worldName) {
    const ok = await runMvJob('/api/mvworlds/action', { action: 'load', world: worldName },
        i18n.t('toast.mvLoading', { id: worldName }));
    if (ok) showToast(i18n.t('toast.mvLoaded', { id: worldName }), 'success');
    renderWorldsList();
    renderServerWorldsPanel();
}

async function mvUnloadWorld(worldName) {
    const ok = await runMvJob('/api/mvworlds/action', { action: 'unload', world: worldName },
        i18n.t('toast.mvUnloading', { id: worldName }));
    if (ok) showToast(i18n.t('toast.mvUnloaded', { id: worldName }), 'success');
    renderWorldsList();
    renderServerWorldsPanel();
}

// ============================================
// Server-Welten-Panel (alle Welten, auch ohne Preset)
// ============================================

function renderServerWorldsPanel() {
    const container = document.getElementById('server-worlds-list');
    if (!container) return;

    const backendEl = document.getElementById('server-worlds-backend');
    if (backendEl) {
        backendEl.textContent = MV_STATE.available
            ? i18n.t('mv.backendActive', { backend: MV_STATE.backend })
            : i18n.t('mv.backendMissing');
        backendEl.className = MV_STATE.available ? 'badge badge-success' : 'badge badge-warning';
    }

    if (!MV_STATE.loaded) {
        container.innerHTML = `<div class="list-empty"><p>${i18n.t('mv.loading')}</p></div>`;
        return;
    }
    // Veraltete Daten werden gezeigt, aber als solche gekennzeichnet -- sie einfach als
    // aktuellen Stand auszugeben waere eine Falschaussage ueber den Server.
    const staleBanner = MV_STATE.stale ? `
        <div class="alert alert-warning" style="display: flex; gap: 0.5rem; margin-bottom: 0.75rem;">
            <i class="fas fa-triangle-exclamation" style="margin-top: 0.2rem;"></i>
            <div style="flex: 1;">
                <strong>${i18n.t('mv.staleTitle')}</strong><br>
                <span style="font-size: 0.85rem; opacity: 0.9;">${i18n.t('mv.staleHint')}</span>
            </div>
            <button class="btn btn-secondary btn-sm" onclick="refreshMvWorlds()">
                <i class="fas fa-rotate"></i> ${i18n.t('button.mvRetry')}
            </button>
        </div>` : '';

    if (MV_STATE.worlds.length === 0) {
        container.innerHTML = staleBanner
            + `<div class="list-empty"><p>${i18n.t(MV_STATE.stale ? 'mv.staleNoData' : 'mv.noServerWorlds')}</p></div>`;
        return;
    }

    container.innerHTML = staleBanner + MV_STATE.worlds.map(world => {
        const status = world.loaded
            ? `<span class="badge badge-success">${i18n.t('card.mvLoaded')}</span>`
            : `<span class="badge badge-warning">${i18n.t('card.mvUnloaded')}</span>`;
        const usage = describeMvUsage(world, null);
        const usageHtml = usage
            ? `<div class="mv-usage"><i class="fas fa-link"></i> ${i18n.t('mv.usedBy')}: ${escapeHtml(usage)}</div>`
            : `<div class="mv-usage mv-usage-free"><i class="fas fa-circle-notch"></i> ${i18n.t('mv.unused')}</div>`;

        const toggleButton = world.loaded
            ? `<button class="btn btn-secondary btn-sm" onclick="mvUnloadWorld('${escapeAttr(world.name)}')">
                   <i class="fas fa-eject"></i> ${i18n.t('button.mvUnload')}</button>`
            : `<button class="btn btn-secondary btn-sm" onclick="mvLoadWorld('${escapeAttr(world.name)}')">
                   <i class="fas fa-play"></i> ${i18n.t('button.mvLoad')}</button>`;

        return `
            <div class="mv-world-row">
                <div class="mv-world-info">
                    <code>${escapeHtml(world.name)}</code>
                    <span class="mv-world-env">${escapeHtml(world.environment || '')}</span>
                    ${status}
                    ${usageHtml}
                </div>
                <div class="mv-world-actions">
                    ${MV_STATE.available ? toggleButton : ''}
                    <button class="btn btn-danger btn-sm" onclick="openDeleteWorldModal('${escapeAttr(world.name)}', true)"
                            title="${i18n.t('button.mvDeleteWorldOnly')}">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </div>`;
    }).join('');
}

function escapeHtml(value) {
    return String(value == null ? '' : value)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

/**
 * Fuer Werte, die in einfachen Anfuehrungszeichen in einem onclick-Attribut landen.
 *
 * Reihenfolge ist wichtig: erst als JS-String-Literal entschaerfen, dann fuers Attribut.
 * Der Browser dekodiert Entities im Attributwert naemlich, BEVOR der JS-Parser laeuft --
 * ein blosses escapeHtml() wuerde ein &#39; also zurueck in ein ' verwandeln, das die
 * Zeichenkette schliesst. Weltnamen stammen aus Ordnernamen und koennen alles enthalten.
 */
function escapeAttr(value) {
    return escapeHtml(String(value == null ? '' : value)
        .replace(/\\/g, '\\\\')
        .replace(/'/g, "\\'"));
}

// ============================================
// Backup worlds
// ============================================

const MV_BACKUPS_STATE = { backups: [], loaded: false };

async function loadMvBackups() {
    try {
        const response = await fetch('/api/mvworlds/backups', { credentials: 'include' });
        const json = response.ok ? await response.json() : null;
        if (!json || json.success === false) {
            showToast(i18n.t('toast.mvFailed', { message: mvErrorText(json) }), 'error');
            return;
        }
        MV_BACKUPS_STATE.backups = Array.isArray(json.data?.backups) ? json.data.backups : [];
        MV_BACKUPS_STATE.loaded = true;
    } catch (error) {
        console.warn('Backup list failed:', error);
    }
    renderBackupWorldsPanel();
}

/** "20260809_141233" -> lokalisierte Datumsausgabe; unparsebar -> Rohwert. */
function formatBackupTimestamp(ts) {
    if (!ts || !/^\d{8}_\d{6}$/.test(ts)) return ts || '';
    const date = new Date(
        Number(ts.slice(0, 4)), Number(ts.slice(4, 6)) - 1, Number(ts.slice(6, 8)),
        Number(ts.slice(9, 11)), Number(ts.slice(11, 13)), Number(ts.slice(13, 15)));
    return date.toLocaleString();
}

function formatBytes(bytes) {
    if (!Number.isFinite(bytes) || bytes < 0) return '';
    if (bytes < 1024) return bytes + ' B';
    const units = ['KB', 'MB', 'GB'];
    let value = bytes;
    let unit = '';
    for (const u of units) {
        value /= 1024;
        unit = u;
        if (value < 1024) break;
    }
    return value.toFixed(1) + ' ' + unit;
}

function renderBackupWorldsPanel() {
    const container = document.getElementById('backup-worlds-list');
    if (!container) return;

    const countEl = document.getElementById('backup-worlds-count');
    if (countEl) countEl.textContent = MV_BACKUPS_STATE.loaded ? String(MV_BACKUPS_STATE.backups.length) : '';

    if (!MV_BACKUPS_STATE.loaded) {
        container.innerHTML = `<div class="list-empty"><p>${i18n.t('mv.backupsLoading')}</p></div>`;
        return;
    }
    if (MV_BACKUPS_STATE.backups.length === 0) {
        container.innerHTML = `<div class="list-empty"><p>${i18n.t('mv.noBackups')}</p></div>`;
        return;
    }

    container.innerHTML = MV_BACKUPS_STATE.backups.map(backup => {
        const worldName = backup.worldName || backup.file;
        return `
            <div class="mv-world-row">
                <div class="mv-world-info">
                    <code>${escapeHtml(worldName)}</code>
                    <span class="mv-world-env">${escapeHtml(formatBackupTimestamp(backup.timestamp))}</span>
                    <span class="mv-world-env">${escapeHtml(formatBytes(backup.sizeBytes))}</span>
                </div>
                <div class="mv-world-actions">
                    <button class="btn btn-secondary btn-sm"
                            onclick="openRestoreBackupModal('${escapeAttr(backup.file)}', '${escapeAttr(backup.worldName || '')}')">
                        <i class="fas fa-clock-rotate-left"></i> ${i18n.t('button.mvRestore')}
                    </button>
                    <button class="btn btn-danger btn-icon" onclick="deleteMvBackup('${escapeAttr(backup.file)}')"
                            title="${i18n.t('button.mvDeleteBackup')}">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </div>`;
    }).join('');
}

/**
 * Restore-Dialog: Zielname vorbelegt mit dem Original-Weltnamen, aenderbar.
 * Ein existierendes Ziel wird schon hier abgefangen (der Server prueft nochmal).
 */
function openRestoreBackupModal(file, originalName) {
    const existing = document.getElementById('restore-backup-modal');
    if (existing) existing.remove();

    const modal = document.createElement('div');
    modal.className = 'modal-overlay active';
    modal.id = 'restore-backup-modal';
    modal.innerHTML = `
        <div class="modal" style="max-width: 520px;">
            <div class="modal-header">
                <h3 class="modal-title">
                    <i class="fas fa-clock-rotate-left"></i> ${i18n.t('confirm.restoreTitle')}
                </h3>
                <button class="modal-close" onclick="closeRestoreBackupModal()"><i class="fas fa-times"></i></button>
            </div>
            <div class="modal-body">
                <p>${i18n.t('confirm.restorePrompt', { file: escapeHtml(file) })}</p>
                <div class="form-group" style="margin-top: 1rem;">
                    <label class="form-label">${i18n.t('confirm.restoreTargetLabel')}</label>
                    <input type="text" class="form-control" id="restore-target-name" autocomplete="off"
                           value="${escapeAttr(originalName)}" oninput="updateRestoreBackupModal()">
                    <small class="form-help">${i18n.t('confirm.restoreTargetHint')}</small>
                    <div id="restore-target-status" class="mv-status-line"></div>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" onclick="closeRestoreBackupModal()">${i18n.t('button.cancel')}</button>
                <button class="btn btn-primary" id="restore-backup-submit"
                        onclick="confirmRestoreBackup('${escapeAttr(file)}')">
                    <i class="fas fa-clock-rotate-left"></i> ${i18n.t('button.mvRestore')}
                </button>
            </div>
        </div>`;

    document.body.appendChild(modal);
    updateRestoreBackupModal();
}

function updateRestoreBackupModal() {
    const input = document.getElementById('restore-target-name');
    const submit = document.getElementById('restore-backup-submit');
    const status = document.getElementById('restore-target-status');
    if (!input || !submit) return;

    const name = input.value.trim();
    const validName = /^[A-Za-z0-9_-]{1,64}$/.test(name);
    const taken = validName && Boolean(getMvWorld(name));

    submit.disabled = !validName || taken;
    if (status) {
        if (!name) {
            status.innerHTML = '';
        } else if (!validName) {
            status.innerHTML = `<span class="mv-hint mv-hint-warn"><i class="fas fa-circle-exclamation"></i> ${i18n.t('mv.error.invalidName')}</span>`;
        } else if (taken) {
            status.innerHTML = `<span class="mv-hint mv-hint-warn"><i class="fas fa-circle-exclamation"></i> ${i18n.t('mv.error.restoreTargetExists')}</span>`;
        } else {
            status.innerHTML = '';
        }
    }
}

function closeRestoreBackupModal() {
    document.getElementById('restore-backup-modal')?.remove();
}

async function confirmRestoreBackup(file) {
    const input = document.getElementById('restore-target-name');
    const target = input ? input.value.trim() : '';
    closeRestoreBackupModal();

    const ok = await runMvJob('/api/mvworlds/backup-action',
        { action: 'restore', file: file, target: target },
        i18n.t('toast.mvRestoring', { id: target }));
    if (ok) {
        showToast(i18n.t('toast.mvRestored', { id: target }), 'success');
        await refreshMvWorlds();
        loadMvBackups();
    }
}

async function deleteMvBackup(file) {
    if (!confirm(i18n.t('confirm.deleteBackupPrompt', { file: file }))) return;
    try {
        const response = await fetch('/api/mvworlds/backup-action', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ action: 'delete', file: file })
        });
        const json = await response.json();
        if (json.success) {
            showToast(i18n.t('toast.mvBackupDeleted', { file: file }), 'success');
        } else {
            showToast(i18n.t('toast.mvFailed', { message: mvErrorText(json) }), 'error');
        }
    } catch (error) {
        showToast(i18n.t('toast.mvFailed', { message: mvErrorText(null) }), 'error');
    }
    loadMvBackups();
}

// ============================================
// Welt/Preset loeschen
// ============================================

/**
 * Loeschdialog fuer ein World-Preset.
 *
 * Zwei Stufen, weil die beiden Loeschungen sehr unterschiedlich schwer wiegen: das Preset ist
 * nur ein YAML-Eintrag, die Welt dagegen unwiderruflich. Die Weltloeschung ist deshalb
 * standardmaessig AUS und verlangt zusaetzlich, dass die World-ID abgetippt wird.
 *
 * @param worldId       Preset- bzw. Weltname
 * @param worldOnly     true = nur die Serverwelt loeschen, Preset unangetastet lassen
 */
function openDeleteWorldModal(worldId, worldOnly = false) {
    const existing = document.getElementById('delete-world-modal');
    if (existing) existing.remove();

    const serverWorld = getMvWorld(worldId);
    const canDeleteWorld = Boolean(serverWorld) && MV_STATE.available;

    const modal = document.createElement('div');
    modal.className = 'modal-overlay active';
    modal.id = 'delete-world-modal';
    modal.innerHTML = `
        <div class="modal" style="max-width: 560px;">
            <div class="modal-header">
                <h3 class="modal-title">
                    <i class="fas fa-triangle-exclamation" style="color: var(--error);"></i>
                    ${worldOnly ? i18n.t('confirm.deleteWorldOnlyTitle') : i18n.t('confirm.deleteWorldTitle')}
                </h3>
                <button class="modal-close" onclick="closeDeleteWorldModal()"><i class="fas fa-times"></i></button>
            </div>
            <div class="modal-body">
                <p>${worldOnly
                    ? i18n.t('confirm.deleteWorldOnlyPrompt', { id: escapeHtml(worldId) })
                    : i18n.t('confirm.deleteWorldPrompt', { id: escapeHtml(worldId) })}</p>

                ${worldOnly ? '' : `
                <div class="toggle-wrapper" style="margin-top: 1rem;">
                    <div class="toggle-label">
                        <span>${i18n.t('confirm.deleteWorldFilesLabel')}</span>
                        <span>${canDeleteWorld
                            ? i18n.t('confirm.deleteWorldFilesHint')
                            : i18n.t('confirm.deleteWorldFilesUnavailable')}</span>
                    </div>
                    <label class="toggle">
                        <input type="checkbox" id="delete-world-files" ${canDeleteWorld ? '' : 'disabled'}
                               onchange="updateDeleteWorldModal('${escapeAttr(worldId)}')">
                        <span class="toggle-slider"></span>
                    </label>
                </div>`}

                <div id="delete-world-danger" style="${worldOnly ? '' : 'display: none;'} margin-top: 1rem;">
                    <div class="alert alert-error" style="display: flex; gap: 0.5rem;">
                        <i class="fas fa-triangle-exclamation" style="margin-top: 0.2rem;"></i>
                        <div>
                            <strong>${i18n.t('confirm.deleteWorldWarningTitle')}</strong><br>
                            <span style="font-size: 0.85rem; opacity: 0.9;">${i18n.t('confirm.deleteWorldWarningText')}</span>
                        </div>
                    </div>

                    <div class="toggle-wrapper" style="margin-top: 0.75rem;">
                        <div class="toggle-label">
                            <span>${i18n.t('confirm.deleteWorldBackupLabel')}</span>
                            <span>${i18n.t('confirm.deleteWorldBackupHint')}</span>
                        </div>
                        <label class="toggle">
                            <input type="checkbox" id="delete-world-backup" checked>
                            <span class="toggle-slider"></span>
                        </label>
                    </div>

                    <div class="form-group" style="margin-top: 0.75rem;">
                        <label class="form-label">${i18n.t('confirm.deleteWorldTypeToConfirm', { id: escapeHtml(worldId) })}</label>
                        <input type="text" class="form-control" id="delete-world-confirm-input"
                               autocomplete="off" oninput="updateDeleteWorldModal('${escapeAttr(worldId)}')">
                    </div>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" onclick="closeDeleteWorldModal()">${i18n.t('button.cancel')}</button>
                <button class="btn btn-danger" id="delete-world-submit"
                        onclick="confirmDeleteWorld('${escapeAttr(worldId)}', ${worldOnly})">
                    <i class="fas fa-trash"></i> ${i18n.t('button.delete')}
                </button>
            </div>
        </div>`;

    document.body.appendChild(modal);
    updateDeleteWorldModal(worldId);
}

/** Haelt den Loeschen-Button gesperrt, solange die Tippbestaetigung fehlt. */
function updateDeleteWorldModal(worldId) {
    const filesCheckbox = document.getElementById('delete-world-files');
    const danger = document.getElementById('delete-world-danger');
    const submit = document.getElementById('delete-world-submit');
    if (!submit) return;

    // Ohne Checkbox ist der Dialog im "nur Welt loeschen"-Modus -- dann gilt der Gefahrenteil immer.
    const deletingWorld = filesCheckbox ? filesCheckbox.checked : true;
    if (danger && filesCheckbox) {
        danger.style.display = deletingWorld ? 'block' : 'none';
    }

    if (!deletingWorld) {
        submit.disabled = false;
        return;
    }
    const input = document.getElementById('delete-world-confirm-input');
    submit.disabled = !input || input.value.trim() !== String(worldId);
}

function closeDeleteWorldModal() {
    const modal = document.getElementById('delete-world-modal');
    if (modal) modal.remove();
}

async function confirmDeleteWorld(worldId, worldOnly) {
    const filesCheckbox = document.getElementById('delete-world-files');
    const deleteWorldFiles = worldOnly || (filesCheckbox ? filesCheckbox.checked : false);
    const backupCheckbox = document.getElementById('delete-world-backup');
    const backup = backupCheckbox ? backupCheckbox.checked : true;

    closeDeleteWorldModal();

    if (deleteWorldFiles) {
        const ok = await runMvJob('/api/mvworlds/action',
            { action: 'delete', world: worldId, backup: backup },
            i18n.t('toast.mvDeleting', { id: worldId }));
        if (!ok) {
            // Weltloeschung fehlgeschlagen -> das Preset bleibt bestehen, sonst zeigt die
            // Konfiguration auf eine Welt, die es noch gibt, aber nicht mehr im Panel steht.
            renderServerWorldsPanel();
            return;
        }
        showToast(i18n.t('toast.mvDeleted', { id: worldId }), 'success');
        if (backup) {
            // Das frisch geschriebene Backup soll sofort im Panel auftauchen.
            loadMvBackups();
        }
    }

    if (!worldOnly) {
        delete CONFIG_STATE.worlds.worlds[worldId];
        recordChange('worlds', `worlds.${worldId}`, undefined);
        showToast(i18n.t('toast.worldDeleted', { id: worldId }), 'success');
    }

    renderWorldsList();
    renderServerWorldsPanel();
    updateQuickActionsPanel();
}

function deleteWorld(worldId) {
    openDeleteWorldModal(worldId, false);
}

// ============================================
// Equipment Management
// ============================================

/**
 * Name der Sektion, unter der alle Equipment-Sets stehen.
 *
 * Bis 1.0.9 kannte equipment.yml drei Sektionen fuer dieselbe Sache, und das Panel war an
 * neun Stellen fest auf `equipment-sets` verdrahtet - waehrend der PvP-Loader `equipment`
 * bevorzugte. Auf einem Server mit der vereinheitlichten Sektion waren die Sets im Panel
 * damit unsichtbar, und alles hier Angelegte blieb wirkungslos. Der Server fuehrt die
 * Sektionen beim Start zusammen; das Panel kennt seitdem nur noch diese eine.
 */
const EQUIPMENT_SECTION = 'equipment';

/** Historische Sektionsnamen - nur noch, um einen veralteten Serverstand zu erkennen. */
const EQUIPMENT_LEGACY_SECTIONS = ['equipment-sets', 'equipment-groups'];

/** Alle Sets als Objekt; nie null, damit Aufrufer nicht jedes Mal absichern muessen. */
function equipmentSets(source = CONFIG_STATE.equipment) {
    if (!source) return {};
    if (source === CONFIG_STATE.equipment) {
        CONFIG_STATE.equipment = CONFIG_STATE.equipment || {};
        CONFIG_STATE.equipment[EQUIPMENT_SECTION] = CONFIG_STATE.equipment[EQUIPMENT_SECTION] || {};
        return CONFIG_STATE.equipment[EQUIPMENT_SECTION];
    }
    return source[EQUIPMENT_SECTION] || {};
}

/** Pfad eines Sets fuer recordChange(). */
function equipmentSetPath(equipId) {
    return `${EQUIPMENT_SECTION}.${equipId}`;
}

/**
 * Warnt, wenn die geladene equipment.yml noch eine Alt-Sektion enthaelt.
 *
 * Das passiert nur, wenn der Server seit dem Update nicht neu gestartet wurde - dann hat die
 * Migration noch nicht gelaufen. Ohne Hinweis wuerde das Panel eine leere Liste zeigen und
 * neue Sets in eine Sektion schreiben, die der Server danach zusammenfuehrt.
 */
function checkEquipmentSchema() {
    const legacy = EQUIPMENT_LEGACY_SECTIONS.filter(
        name => CONFIG_STATE.equipment && CONFIG_STATE.equipment[name]);
    if (legacy.length === 0) return;

    console.warn('[equipment] Alt-Sektionen gefunden:', legacy.join(', '));
    showToast(i18n.t('equipment.legacySectionWarning', { sections: legacy.join(', ') }), 'warning');
}

function renderEquipmentList() {
    console.log('=== renderEquipmentList called ===');
    const container = document.getElementById('equipment-list');
    if (!container) {
        console.error('equipment-list container NOT FOUND in DOM!');
        return;
    }
    console.log('equipment-list container found');
    
    const equipment = equipmentSets();
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

    const ordered = sortedEquipmentEntries();
    let html = '';
    ordered.forEach(([equipId, equipConfig], index) => {
        html += createEquipmentCard(equipId, equipConfig, index, ordered.length);
    });
    container.innerHTML = html;
    updateNavigationBadges();
}

/**
 * Alle Sets in der Reihenfolge, in der sie im PvP-Wager-Menue erscheinen.
 *
 * Das Feld `order` bestimmt sie; Sets ohne Angabe haengen hinten in ihrer bisherigen
 * Reihenfolge an. Der Server sortiert nach denselben Regeln (EquipmentManager.sorted), damit
 * die Uebersicht hier zeigt, was der Spieler spaeter sieht.
 */
function sortedEquipmentEntries() {
    const entries = Object.entries(equipmentSets())
        .filter(([, config]) => config && typeof config === 'object');

    return entries
        .map(([id, config], index) => ({ id, config, index }))
        .sort((a, b) => {
            const orderA = Number.isInteger(a.config.order) ? a.config.order : Number.MAX_SAFE_INTEGER;
            const orderB = Number.isInteger(b.config.order) ? b.config.order : Number.MAX_SAFE_INTEGER;
            if (orderA !== orderB) return orderA - orderB;
            return a.index - b.index;
        })
        .map(entry => [entry.id, entry.config]);
}

/**
 * Verschiebt ein Set in der Anzeigereihenfolge.
 *
 * Danach bekommen *alle* Sets ein neues, lueckenloses `order` - sonst haetten Sets aus einer
 * aelteren Datei weiterhin gar keins und wuerden trotz Verschiebung ans Ende rutschen.
 *
 * @param delta -1 = nach oben, +1 = nach unten
 */
function moveEquipment(equipId, delta) {
    const ordered = sortedEquipmentEntries();
    const from = ordered.findIndex(([id]) => id === equipId);
    const to = from + delta;
    if (from < 0 || to < 0 || to >= ordered.length) return;

    const moved = ordered.splice(from, 1)[0];
    ordered.splice(to, 0, moved);

    ordered.forEach(([id, config], index) => {
        if (config.order === index) return;
        config.order = index;
        recordChange('equipment', equipmentSetPath(id), config);
    });

    renderEquipmentList();
    updateQuickActionsPanel();
}

/** Naechste freie Position am Ende der Liste. */
function nextEquipmentOrder() {
    const orders = Object.values(equipmentSets())
        .map(config => (config && Number.isInteger(config.order)) ? config.order : -1);
    return orders.length > 0 ? Math.max(...orders) + 1 : 0;
}

function createEquipmentCard(equipId, config, position = 0, total = 1) {
    // Zwei getrennte Schalter statt des frueheren gemeinsamen 'enabled'. Sets aus einer noch
    // nicht migrierten Datei tragen nur das alte Feld - das gilt dann fuer beide Systeme.
    const legacyEnabled = config.enabled !== false;
    const pvpEnabled = config['pvpwager-equip-enable'] !== undefined
        ? config['pvpwager-equip-enable'] !== false : legacyEnabled;
    const eventEnabled = config['event-equip-enable'] !== undefined
        ? config['event-equip-enable'] !== false : legacyEnabled;
    const isEnabled = pvpEnabled || eventEnabled;

    const armor = config.armor || {};
    const inventory = config.inventory || [];
    const allowedWorlds = config['allowed-pvpwager-worlds'] || 'all';
    const worldsLabel = allowedWorlds === 'all' ? i18n.t('card.allWorlds')
        : (Array.isArray(allowedWorlds) ? allowedWorlds.join(', ') : String(allowedWorlds));

    // Erstelle Armor-Vorschau
    const armorItems = [armor.helmet, armor.chestplate, armor.leggings, armor.boots].filter(Boolean);

    // Icon wie im Ingame-Menue; ohne Angabe bleibt es beim allgemeinen Schild-Symbol. Sets aus
    // einer Datei von vor 1.0.9 tragen es noch im Altblock 'gui-item'.
    const icon = config.icon || (config['gui-item'] && config['gui-item'].material);
    const titleIcon = icon
        ? itemIconHtml(icon, 20)
        : `<i class="fas fa-shield-alt" style="color: ${isEnabled ? 'var(--success)' : 'var(--text-muted)'};"></i>`;

    return `
        <div class="card" style="${!isEnabled ? 'opacity: 0.7;' : ''}">
            <div class="card-header">
                <div class="card-title">
                    ${titleIcon}
                    <span>${escapeHtml(config['display-name'] || equipId)}</span>
                    ${pvpEnabled ? `<span class="badge badge-success">${i18n.t('card.forPvp')}</span>` : ''}
                    ${eventEnabled ? `<span class="badge badge-info">${i18n.t('card.forEvents')}</span>` : ''}
                    ${!isEnabled ? `<span class="badge badge-warning">${i18n.t('card.inactive')}</span>` : ''}
                </div>
                <div class="card-actions">
                    <button class="btn btn-secondary btn-icon" onclick="moveEquipment('${escapeAttr(equipId)}', -1)"
                            title="${i18n.t('equipment.moveUp')}" ${position === 0 ? 'disabled' : ''}>
                        <i class="fas fa-arrow-up"></i>
                    </button>
                    <button class="btn btn-secondary btn-icon" onclick="moveEquipment('${escapeAttr(equipId)}', 1)"
                            title="${i18n.t('equipment.moveDown')}" ${position >= total - 1 ? 'disabled' : ''}>
                        <i class="fas fa-arrow-down"></i>
                    </button>
                    <button class="btn btn-secondary btn-icon" onclick="editEquipment('${escapeAttr(equipId)}')" title="${i18n.t('button.edit')}">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn btn-secondary btn-icon" onclick="duplicateEquipment('${escapeAttr(equipId)}')" title="${i18n.t('button.duplicate')}">
                        <i class="fas fa-copy"></i>
                    </button>
                    <button class="btn btn-danger btn-icon" onclick="deleteEquipment('${escapeAttr(equipId)}')" title="${i18n.t('button.delete')}">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </div>
            <div class="card-body">
                <div style="margin-bottom: 0.75rem; display: flex; justify-content: space-between; align-items: center;">
                    <code style="background: var(--background); padding: 0.2rem 0.5rem; border-radius: 4px; font-size: 0.8rem;">${escapeHtml(equipId)}</code>
                    <span style="color: var(--text-muted); font-size: 0.8rem;">
                        <i class="fas fa-globe-americas"></i> ${escapeHtml(worldsLabel)}
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

/**
 * Legt eine Kopie eines Sets an.
 *
 * Ein Kit aufzubauen, das sich von einem bestehenden nur in ein paar Items unterscheidet, war
 * bisher reine Handarbeit. Die Kopie haengt sich ans Ende der Reihenfolge, statt die Position
 * des Originals zu uebernehmen - bei gleichem `order` waere die Anordnung der beiden sonst dem
 * Zufall ueberlassen.
 */
function duplicateEquipment(equipId) {
    const sets = equipmentSets();
    const source = sets[equipId];
    if (!source) return;

    let newId = equipId + '-copy';
    let counter = 2;
    while (sets[newId]) {
        newId = `${equipId}-copy${counter++}`;
    }

    const copy = JSON.parse(JSON.stringify(source));
    copy['display-name'] = (copy['display-name'] || equipId) + ' (Copy)';
    copy.order = nextEquipmentOrder();

    sets[newId] = copy;
    recordChange('equipment', equipmentSetPath(newId), copy);
    renderEquipmentList();
    updateQuickActionsPanel();
    showToast(i18n.t('toast.equipDuplicated', { source: equipId, copy: newId }), 'success');
}

function deleteEquipment(equipId) {
    if (confirm(i18n.t('confirm.deleteEquipmentPrompt', { id: equipId }))) {
        delete equipmentSets()[equipId];
        recordChange('equipment', equipmentSetPath(equipId), undefined);
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
    document.documentElement.style.setProperty(cssVar, colorValue);

    const path = `web.theme.${colorType}-color`;
    const currentValue = getNestedValue(CONFIG_STATE.webConfig, path);
    if (isDeepEqual(currentValue, colorValue)) {
        return;
    }

    setNestedValue(CONFIG_STATE.webConfig, path, colorValue);
    recordChange('web', path, colorValue);
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

    // Farbfelder auf die Defaults nachziehen, sonst zeigen sie weiter die alten Werte
    populateWebConfigForm();

    recordChange('web', 'web.theme', defaultTheme);
    updateQuickActionsPanel();
    showToast(i18n.t('theme.reset'), 'success');
}

// Der generische Item-Picker stand hier: openItemPicker/closeItemPicker/renderItemGrid/
// filterItems/selectItem/confirmItemSelection samt Modal in der index.html. Einziger
// Eingang war openItemPicker(), und die Funktion hatte nie einen Aufrufer - der
// Equipment-Editor benutzt seit jeher seine eigenen Inline-Picker (renderArmorPicker,
// renderItemCategories in editors.js). Das Modal liess sich also gar nicht oeffnen.

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
    if (!obj || !path) return;
    const keys = path.split('.');
    let current = obj;
    
    for (let i = 0; i < keys.length - 1; i++) {
        if (!(keys[i] in current) || current[keys[i]] == null || typeof current[keys[i]] !== 'object') {
            if (value === undefined) return;
            current[keys[i]] = {};
        }
        current = current[keys[i]];
    }
    
    const lastKey = keys[keys.length - 1];
    if (value === undefined) {
        delete current[lastKey];
    } else {
        current[lastKey] = value;
    }
}

function getNestedValue(obj, path) {
    return path.split('.').reduce((curr, prop) => curr?.[prop], obj);
}

function recordChange(category, path, value) {
    CONFIG_STATE.changes.splice(CONFIG_STATE.changeIndex + 1);
    CONFIG_STATE.changes.push({ category, path, value });
    CONFIG_STATE.changeIndex = CONFIG_STATE.changes.length - 1;
    updateSyncStatusUI();
}

function undoChange() {
    if (CONFIG_STATE.changeIndex >= 0) {
        CONFIG_STATE.changeIndex--;
        replayChanges();
        updateSyncStatusUI();
        showToast(i18n.t('history.undo'), 'info');
    }
}

function redoChange() {
    if (CONFIG_STATE.changeIndex < CONFIG_STATE.changes.length - 1) {
        CONFIG_STATE.changeIndex++;
        replayChanges();
        updateSyncStatusUI();
        showToast(i18n.t('history.redo'), 'info');
    }
}

function replayChanges() {
    // Basiszustand aus Baseline (oder Backup) wiederherstellen
    if (CONFIG_BASELINE.config) {
        CONFIG_STATE.config = deepClone(CONFIG_BASELINE.config);
        CONFIG_STATE.worlds = deepClone(CONFIG_BASELINE.worlds);
        CONFIG_STATE.equipment = deepClone(CONFIG_BASELINE.equipment);
        CONFIG_STATE.webConfig = deepClone(CONFIG_BASELINE.webConfig);
    } else {
        const configBackup = localStorage.getItem('config_backup');
        const worldsBackup = localStorage.getItem('worlds_backup');
        const equipmentBackup = localStorage.getItem('equipment_backup');
        const webConfigBackup = localStorage.getItem('webconfig_backup');
        
        if (!configBackup) {
            console.error('No config backup found in localStorage');
            showToast(i18n.t('error.noBackup'), 'error');
            return;
        }
        
        CONFIG_STATE.config = JSON.parse(configBackup);
        CONFIG_STATE.worlds = JSON.parse(worldsBackup || '{}');
        CONFIG_STATE.equipment = JSON.parse(equipmentBackup || '{}');
        CONFIG_STATE.webConfig = JSON.parse(webConfigBackup || '{}');
    }

    // Alle Änderungen bis zum aktuellen Index anwenden
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

    // UI aktualisieren
    populateSettingsForm();
    renderEventsList();
    renderWorldsList();
    renderEquipmentList();
    loadThemeFromConfig();
    updateSyncStatusUI();
}

function hasUnsavedChanges() {
    return getRealUnsavedChanges().count > 0;
}

function hasConfigChanged(category) {
    const diff = getRealUnsavedChanges();
    return !!diff[category];
}

function discardChanges() {
    if (confirm(i18n.t('history.discardConfirm'))) {
        CONFIG_STATE.changes = [];
        CONFIG_STATE.changeIndex = -1;
        if (CONFIG_BASELINE.config) {
            CONFIG_STATE.config = deepClone(CONFIG_BASELINE.config);
            CONFIG_STATE.worlds = deepClone(CONFIG_BASELINE.worlds);
            CONFIG_STATE.equipment = deepClone(CONFIG_BASELINE.equipment);
            CONFIG_STATE.webConfig = deepClone(CONFIG_BASELINE.webConfig);
            
            localStorage.setItem('config_backup', JSON.stringify(CONFIG_STATE.config));
            localStorage.setItem('worlds_backup', JSON.stringify(CONFIG_STATE.worlds));
            localStorage.setItem('equipment_backup', JSON.stringify(CONFIG_STATE.equipment));
            localStorage.setItem('webconfig_backup', JSON.stringify(CONFIG_STATE.webConfig));
            
            populateSettingsForm();
            renderEventsList();
            renderWorldsList();
            renderEquipmentList();
            loadThemeFromConfig();
            updateSyncStatusUI();
            showToast(i18n.t('history.discarded'), 'info');
        } else {
            loadAllConfigs();
        }
    }
}

function toggleToolsDropdown(e) {
    if (e) {
        e.stopPropagation();
        e.preventDefault();
    }
    const dd = document.getElementById('tools-dropdown');
    if (dd) {
        dd.classList.toggle('active');
    }
}

function closeToolsDropdown() {
    const dd = document.getElementById('tools-dropdown');
    if (dd) {
        dd.classList.remove('active');
    }
}

window.addEventListener('click', (e) => {
    const dd = document.getElementById('tools-dropdown');
    if (dd && !dd.contains(e.target)) {
        dd.classList.remove('active');
    }
});

function updateSyncStatusUI() {
    const badge = document.getElementById('sync-status-badge');
    const icon = document.getElementById('sync-status-icon');
    const text = document.getElementById('sync-status-text');
    const saveBadge = document.getElementById('save-counter-badge');
    const btnUndo = document.getElementById('btn-undo');
    const btnRedo = document.getElementById('btn-redo');

    const realChanges = getRealUnsavedChanges();
    const changeCount = realChanges.count;
    const historyCount = CONFIG_STATE.changes ? CONFIG_STATE.changes.length : 0;
    const changeIndex = CONFIG_STATE.changeIndex !== undefined ? CONFIG_STATE.changeIndex : -1;

    // Undo / Redo buttons update
    if (btnUndo) {
        if (changeIndex >= 0) {
            btnUndo.classList.remove('disabled');
            btnUndo.disabled = false;
        } else {
            btnUndo.classList.add('disabled');
            btnUndo.disabled = true;
        }
    }
    if (btnRedo) {
        if (changeIndex < historyCount - 1) {
            btnRedo.classList.remove('disabled');
            btnRedo.disabled = false;
        } else {
            btnRedo.classList.add('disabled');
            btnRedo.disabled = true;
        }
    }

    // Save button counter badge update (spiegelt die tatsächlichen ungespeicherten Änderungen wider)
    if (saveBadge) {
        if (changeCount > 0) {
            saveBadge.textContent = changeCount;
            saveBadge.style.display = 'inline-block';
        } else {
            saveBadge.style.display = 'none';
        }
    }

    // Live Sync Status Badge update
    if (badge && icon && text) {
        if (CONFIG_STATE.isSaving) {
            badge.className = 'sync-badge sync-saving';
            icon.className = 'fas fa-circle-notch fa-spin';
            icon.style.animation = '';
            text.textContent = i18n.t('sync.saving');
            badge.title = i18n.t('sync.saving');
        } else if (CONFIG_STATE.isOffline) {
            badge.className = 'sync-badge sync-error';
            icon.className = 'fas fa-exclamation-triangle';
            icon.style.animation = '';
            text.textContent = i18n.t('sync.error');
            badge.title = i18n.t('sync.error');
        } else if (changeCount > 0) {
            badge.className = 'sync-badge sync-unsaved';
            icon.className = 'fas fa-dot-circle';
            icon.style.animation = 'pulse 1.5s infinite';
            text.textContent = i18n.t('sync.unsaved', { count: changeCount });
            badge.title = i18n.t('sync.unsaved', { count: changeCount });
        } else {
            badge.className = 'sync-badge sync-synced';
            icon.className = 'fas fa-check-circle';
            icon.style.animation = 'none';
            text.textContent = i18n.t('sync.synced');
            badge.title = i18n.t('sync.synced');
        }
    }

    updateQuickActionsPanel();
}

function updateQuickActionsPanel() {
    const panel = document.getElementById('quick-actions');
    if (!panel) return;
    if (hasUnsavedChanges()) {
        panel.classList.remove('hidden');
    } else {
        panel.classList.add('hidden');
    }
}

function updateNavigationBadges() {
    const eventCount = Object.keys(CONFIG_STATE.config?.events || {}).length;
    const worldCount = Object.keys(CONFIG_STATE.worlds?.worlds || {}).length;
    const equipCount = Object.keys(equipmentSets()).length;

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

// toSnakeCase() stand hier - eine Funktion, deren Ersetzung /_/g -> '_' nichts tat. Sie
// diente nur dem alten Icon-URL-Bauer, den items.js abgeloest hat, und hatte danach keinen
// Aufrufer mehr.

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
    updateSyncStatusUI();
});

// ============================================
// Debug & Diagnostics
// ============================================

function debugState() {
    console.group('📊 CONFIG_STATE Debug');
    console.log('Config:', CONFIG_STATE.config);
    console.log('Events:', CONFIG_STATE.config?.events);
    console.log('Worlds:', CONFIG_STATE.worlds?.worlds);
    console.log('Equipment:', equipmentSets());
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
window.CONFIG_BASELINE = CONFIG_BASELINE;
window.isDeepEqual = isDeepEqual;
window.deepClone = deepClone;
window.getRealUnsavedChanges = getRealUnsavedChanges;

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
window.updatePublicUrl = updatePublicUrl;
window.cleanPublicUrlForDisplay = cleanPublicUrlForDisplay;
window.formatPublicUrlForConfig = formatPublicUrlForConfig;
window.incrementValue = incrementValue;
window.decrementValue = decrementValue;
window.updateThemeColor = updateThemeColor;
window.resetTheme = resetTheme;

// Multiverse-Weltverwaltung
window.MV_STATE = MV_STATE;
window.loadMvWorlds = loadMvWorlds;
window.refreshMvWorlds = refreshMvWorlds;
window.getMvWorld = getMvWorld;
window.getMvStatus = getMvStatus;
window.describeMvUsage = describeMvUsage;
window.isWorldUsedAsPreset = isWorldUsedAsPreset;
window.runMvJob = runMvJob;
window.mvErrorText = mvErrorText;
window.mvLoadWorld = mvLoadWorld;
window.mvUnloadWorld = mvUnloadWorld;
window.renderServerWorldsPanel = renderServerWorldsPanel;
window.MV_BACKUPS_STATE = MV_BACKUPS_STATE;
window.loadMvBackups = loadMvBackups;
window.renderBackupWorldsPanel = renderBackupWorldsPanel;
window.formatBackupTimestamp = formatBackupTimestamp;
window.formatBytes = formatBytes;
window.openRestoreBackupModal = openRestoreBackupModal;
window.updateRestoreBackupModal = updateRestoreBackupModal;
window.closeRestoreBackupModal = closeRestoreBackupModal;
window.confirmRestoreBackup = confirmRestoreBackup;
window.deleteMvBackup = deleteMvBackup;
window.openDeleteWorldModal = openDeleteWorldModal;
window.updateDeleteWorldModal = updateDeleteWorldModal;
window.closeDeleteWorldModal = closeDeleteWorldModal;
window.confirmDeleteWorld = confirmDeleteWorld;
window.escapeHtml = escapeHtml;
window.escapeAttr = escapeAttr;

// Equipment-Sektion und Karten-Aktionen
window.equipmentSets = equipmentSets;
window.equipmentSetPath = equipmentSetPath;
window.duplicateEquipment = duplicateEquipment;

// Inventar- & Backup-Verwaltung
window.INVENTORY_STATE = INVENTORY_STATE;
window.INVENTORY_BROWSER = INVENTORY_BROWSER;
window.switchInventoryTab = switchInventoryTab;
window.loadInventoryStatus = loadInventoryStatus;
window.loadInventoryGuard = loadInventoryGuard;
window.refreshOnlinePlayersList = refreshOnlinePlayersList;
window.selectPlayerForInventory = selectPlayerForInventory;
window.loadInventoryBackups = loadInventoryBackups;
window.filterInventoryBackups = filterInventoryBackups;
window.renderInventoryBackupList = renderInventoryBackupList;
window.previewInventoryBackup = previewInventoryBackup;
window.renderInventoryBackupPreview = renderInventoryBackupPreview;
window.restoreInventoryBackup = restoreInventoryBackup;
window.deleteInventoryBackup = deleteInventoryBackup;
window.openRestoreModal = openRestoreModal;
window.closeRestoreModal = closeRestoreModal;
window.executeRestoreFromModal = executeRestoreFromModal;
window.exportCurrentBackupToEquipment = exportCurrentBackupToEquipment;
window.copyCurrentBackupJson = copyCurrentBackupJson;
window.refreshInventorySection = refreshInventorySection;
window.renderInventoryWarnings = renderInventoryWarnings;
window.renderGlobalMviBanner = renderGlobalMviBanner;
window.toggleGlobalMviBanner = toggleGlobalMviBanner;
window.showInventoryConflictDetails = showInventoryConflictDetails;
window.showMinecraftTooltip = showMinecraftTooltip;
window.moveMinecraftTooltip = moveMinecraftTooltip;
window.hideMinecraftTooltip = hideMinecraftTooltip;
window.formatMinecraftColorCodes = formatMinecraftColorCodes;
window.loadItemCatalog = loadItemCatalog;
window.applyTextureSettings = applyTextureSettings;

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
