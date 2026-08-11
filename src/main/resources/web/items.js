// ============================================
// Item-Katalog und Icon-Aufloesung
// ============================================
//
// Einzige Stelle im Panel, die weiss, wie ein Item-Icon entsteht und welche Items es
// ueberhaupt gibt. Vorher lagen beide Informationen verstreut: elf fest einprogrammierte
// GitHub-URLs fuer die Icons und zwei ueberlappende JS-Arrays (137 + 86 Eintraege) als
// Item-Liste. Ergebnis war ein Panel, das ohne Internet nur Buchstaben-Platzhalter zeigte
// und nicht einmal jedes zehnte Item des Spiels anbieten konnte.
//
// Reihenfolge der Quellen (jede spaetere ist nur Rueckfall der vorherigen):
//   1. /api/materials       - Items, Verzauberungen, Stapelgroessen des LAUFENDEN Servers
//   2. /item-assets/_index.json - Manifest der mitgelieferten Icons (auch ohne Login lesbar)
//   3. web-config.yml items.texture-source - optionaler Remote-Fallback
//
// Icons liegen als 64x64-PNG im Plugin-JAR unter /item-assets/<MATERIAL>.png und werden
// vom StaticFileHandler ausgeliefert - kein externer Abruf, kein Internetzwang.

const ITEM_CATALOG = {
    /** Alle Items des Servers: [{name, category, maxStack, block, edible, maxDurability, armorSlot, potion, enchantments}] */
    materials: [],
    /** name -> Eintrag aus materials, fuer O(1)-Zugriff beim Rendern. */
    byName: new Map(),
    /** category -> Eintraege, in der vom Server vorgegebenen Reihenfolge. */
    byCategory: new Map(),
    /** Reihenfolge der Kategorien, wie sie das Panel anzeigt. */
    categories: [],
    /** Material-Namen, fuer die ein mitgeliefertes Icon existiert. */
    assets: new Set(),
    /** Material-Namen mit einer Textur aus dem Server-Resourcepack (schlaegt Assets). */
    overrides: new Set(),
    /** key -> {key, maxLevel, startLevel, treasure, curse} */
    enchantments: new Map(),
    /** Verfuegbare ItemFlags, PotionTypes und PotionEffectTypes des Servers. */
    itemFlags: [],
    potionTypes: [],
    potionEffects: [],
    /** false schaltet auf reine Buchstaben-Platzhalter um (web-config: items.enable-textures). */
    texturesEnabled: true,
    /** true, sobald mindestens eine Quelle geladen wurde. */
    ready: false,
    /** true, wenn /api/materials nicht erreichbar war und nur das Manifest greift. */
    degraded: false
};

/** Remote-Fallback aus web-config.yml; leer = kein externer Abruf. */
let ITEM_TEXTURE_FALLBACK = '';

// ============================================
// Laden
// ============================================

/**
 * Laedt den Item-Katalog. Wird einmal beim Panel-Start aufgerufen.
 *
 * Faellt der Server-Endpunkt aus, arbeitet das Panel mit dem Asset-Manifest allein weiter
 * (`degraded`): die Auswahl bleibt vollstaendig, nur Stapelgroessen und Verzauberungslisten
 * sind dann Schaetzungen. Das ist deutlich besser als ein Panel ohne jede Item-Liste.
 */
async function loadItemCatalog() {
    await Promise.all([
        loadMaterialsFromServer(),
        loadAssetManifest(),
        loadTextureOverrides()
    ]);

    // Ohne Server-Antwort das Manifest als Item-Liste verwenden.
    if (ITEM_CATALOG.materials.length === 0 && ITEM_CATALOG.assets.size > 0) {
        ITEM_CATALOG.degraded = true;
        ITEM_CATALOG.materials = Array.from(ITEM_CATALOG.assets)
            .sort()
            .map(name => ({ name: name, category: guessCategory(name), maxStack: 64 }));
        indexMaterials();
    }

    ITEM_CATALOG.ready = ITEM_CATALOG.materials.length > 0;
    CONFIG_STATE.minecraftItems = ITEM_CATALOG.materials.map(m => m.name);
    console.log(`[items] Catalog loaded: ${ITEM_CATALOG.materials.length} items, ` +
        `${ITEM_CATALOG.assets.size} icons, ${ITEM_CATALOG.overrides.size} resource pack textures` +
        (ITEM_CATALOG.degraded ? ' (without the server catalog)' : ''));
    return ITEM_CATALOG.ready;
}

async function loadMaterialsFromServer() {
    try {
        const response = await fetch('/api/materials', { credentials: 'include' });
        if (!response.ok) return;
        const json = await response.json();
        if (!json.success || !json.data) {
            // Der Server antwortet, meldet aber einen echten Fehler beim Katalogaufbau
            // (messageKey: 'items.error.catalogFailed') - das ist etwas anderes als ein
            // schlicht unerreichbarer Endpunkt und gehoert ins Log, nicht nur stumm in
            // den Notbetrieb ueber das Asset-Manifest. apiErrorText() steht in app.js,
            // ist zum Aufrufzeitpunkt aber laengst geladen - loadMaterialsFromServer()
            // laeuft erst async ueber loadItemCatalog(), nach dem vollstaendigen Seitenaufbau.
            console.warn('[items] /api/materials reported an error:', apiErrorText(json, 'items.error.catalogFailed'));
            return;
        }

        ITEM_CATALOG.materials = json.data.materials || [];
        ITEM_CATALOG.categories = json.data.categories || [];
        ITEM_CATALOG.itemFlags = json.data.itemFlags || [];
        ITEM_CATALOG.potionTypes = json.data.potionTypes || [];
        ITEM_CATALOG.potionEffects = json.data.potionEffects || [];
        (json.data.enchantments || []).forEach(e => ITEM_CATALOG.enchantments.set(e.key, e));
        indexMaterials();
    } catch (error) {
        console.warn('[items] /api/materials unreachable, falling back to the asset manifest', error);
    }
}

async function loadAssetManifest() {
    try {
        const response = await fetch('/item-assets/_index.json', { credentials: 'include' });
        if (!response.ok) return;
        const json = await response.json();
        (json.items || []).forEach(name => ITEM_CATALOG.assets.add(name));
    } catch (error) {
        console.warn('[items] Asset manifest not readable', error);
    }
}

/**
 * Texturen aus dem Server-Resourcepack. Der Endpunkt existiert nur, wenn die Funktion in
 * web-config.yml eingeschaltet ist - ein 404 ist deshalb der Normalfall und kein Fehler.
 */
async function loadTextureOverrides() {
    try {
        const response = await fetch('/api/textures/overrides', { credentials: 'include' });
        if (!response.ok) return;
        const json = await response.json();
        if (!json.success || !json.data) return;
        (json.data.materials || []).forEach(name => ITEM_CATALOG.overrides.add(name));
    } catch (error) {
        // Feature aus oder Pack noch nicht entpackt - stiller Rueckfall auf die Assets.
    }
}

function indexMaterials() {
    ITEM_CATALOG.byName.clear();
    ITEM_CATALOG.byCategory.clear();
    ITEM_CATALOG.materials.forEach(entry => {
        ITEM_CATALOG.byName.set(entry.name, entry);
        const category = entry.category || 'misc';
        if (!ITEM_CATALOG.byCategory.has(category)) {
            ITEM_CATALOG.byCategory.set(category, []);
        }
        ITEM_CATALOG.byCategory.get(category).push(entry);
    });
    if (ITEM_CATALOG.categories.length === 0) {
        ITEM_CATALOG.categories = Array.from(ITEM_CATALOG.byCategory.keys()).sort();
    }
}

/** Grobe Kategorie fuer den Notbetrieb ohne Server-Katalog. */
function guessCategory(name) {
    if (/_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)$/.test(name) || name === 'ELYTRA') return 'armor';
    if (/_(SWORD|AXE)$/.test(name) && !name.endsWith('_PICKAXE')) return 'weapons';
    if (/^(BOW|CROSSBOW|TRIDENT|MACE|SHIELD)$/.test(name)) return 'weapons';
    if (/_(PICKAXE|SHOVEL|HOE)$/.test(name)) return 'tools';
    if (name.endsWith('_SPAWN_EGG')) return 'spawnEggs';
    if (name.endsWith('POTION')) return 'potions';
    return 'misc';
}

// ============================================
// Icons
// ============================================

/**
 * Bild-URL fuer ein Material.
 *
 * Reihenfolge: Resourcepack-Textur -> mitgeliefertes Asset -> Remote-Fallback.
 * Kennt das Panel das Material gar nicht, wird trotzdem der Asset-Pfad geliefert - das
 * onerror-Attribut aus itemIconHtml() faengt den 404 mit dem Platzhalter ab.
 */
function itemIconUrl(material) {
    if (!material) return itemPlaceholderUrl('?');
    const name = String(material).toUpperCase();

    if (!ITEM_CATALOG.texturesEnabled) {
        return itemPlaceholderUrl(name.charAt(0));
    }
    if (ITEM_CATALOG.overrides.has(name)) {
        return `/item-assets/override/${encodeURIComponent(name)}.png`;
    }
    if (ITEM_CATALOG.assets.size === 0 || ITEM_CATALOG.assets.has(name)) {
        return `/item-assets/${encodeURIComponent(name)}.png`;
    }
    if (ITEM_TEXTURE_FALLBACK) {
        return ITEM_TEXTURE_FALLBACK + name.toLowerCase() + '.png';
    }
    return itemPlaceholderUrl(name.charAt(0));
}

/** Inline-SVG mit dem Anfangsbuchstaben - letzte Stufe, wenn kein Bild existiert. */
function itemPlaceholderUrl(letter) {
    const safe = String(letter || '?').charAt(0).toUpperCase().replace(/[<>&"']/g, '?');
    return 'data:image/svg+xml,' + encodeURIComponent(
        `<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40">` +
        `<rect fill="#2d2d2d" width="40" height="40" rx="4"/>` +
        `<text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" ` +
        `fill="#4caf50" font-family="sans-serif" font-size="16">${safe}</text></svg>`
    );
}

/**
 * Fertiges <img>-Element fuer ein Material.
 *
 * @param {string} material Material-Name (Bukkit-Enum)
 * @param {number} [size]   Kantenlaenge in Pixeln; ohne Angabe fuellt das Bild seinen Slot
 * @param {object} [opts]   {lazy: true} fuer Listen mit vielen Icons, {css: '...'} fuer Extra-Stil
 */
function itemIconHtml(material, size, opts) {
    const options = opts || {};
    const name = String(material || '').toUpperCase();
    const dimension = size ? `width:${size}px;height:${size}px;` : 'width:100%;height:100%;';
    const style = dimension + 'object-fit:contain;image-rendering:pixelated;' + (options.css || '');
    const lazy = options.lazy ? ' loading="lazy" decoding="async"' : '';
    // image-rendering:pixelated ist Pflicht: die Icons sind Pixelart, ohne das Attribut
    // glaettet der Browser sie beim Hochskalieren zu Matsch.
    return `<img src="${itemIconUrl(name)}" alt="${escapeHtml(name)}" draggable="false"${lazy}` +
        ` style="${style}" onerror="onItemIconError(this)">`;
}

/**
 * Fehlender Icon-Treffer: einmal auf den Remote-Fallback ausweichen, danach Platzhalter.
 * Das Flag am Element verhindert eine Endlosschleife, falls auch die Remote-URL 404 liefert.
 */
function onItemIconError(img) {
    const name = (img.getAttribute('alt') || '?').toUpperCase();
    if (!img.dataset.fallbackTried && ITEM_TEXTURE_FALLBACK) {
        img.dataset.fallbackTried = '1';
        img.src = ITEM_TEXTURE_FALLBACK + name.toLowerCase() + '.png';
        return;
    }
    img.onerror = null;
    img.src = itemPlaceholderUrl(name.charAt(0));
}

// ============================================
// Abfragen ueber den Katalog
// ============================================

function itemEntry(material) {
    return ITEM_CATALOG.byName.get(String(material || '').toUpperCase()) || null;
}

/** "ACACIA_BOAT" -> "Acacia Boat" - Minecraft-Materialnamen sind nicht uebersetzt. */
function itemDisplayName(material) {
    return String(material || '')
        .toLowerCase()
        .split('_')
        .filter(part => part.length > 0)
        .map(part => part.charAt(0).toUpperCase() + part.slice(1))
        .join(' ');
}

/** Maximale Stapelgroesse laut Server; 64, solange der Katalog fehlt. */
function itemMaxStack(material) {
    const entry = itemEntry(material);
    return entry && entry.maxStack ? entry.maxStack : 64;
}

/** Ruestungsslot ('helmet' | 'chestplate' | 'leggings' | 'boots') oder null. */
function itemArmorSlot(material) {
    const entry = itemEntry(material);
    if (entry) return entry.armorSlot || null;
    // Notbetrieb ohne Katalog: aus dem Namen ableiten.
    const name = String(material || '').toUpperCase();
    if (name.endsWith('_HELMET') || name === 'TURTLE_HELMET' || name === 'CARVED_PUMPKIN') return 'helmet';
    if (name.endsWith('_CHESTPLATE') || name === 'ELYTRA') return 'chestplate';
    if (name.endsWith('_LEGGINGS')) return 'leggings';
    if (name.endsWith('_BOOTS')) return 'boots';
    return null;
}

/** true, wenn das Item einen Haltbarkeitswert traegt. */
function itemMaxDurability(material) {
    const entry = itemEntry(material);
    return entry && entry.maxDurability ? entry.maxDurability : 0;
}

/** true, wenn das Panel fuer dieses Item den Trank-Editor anbieten soll. */
function itemIsPotion(material) {
    const entry = itemEntry(material);
    if (entry) return entry.potion === true;
    return /^(POTION|SPLASH_POTION|LINGERING_POTION|TIPPED_ARROW)$/.test(String(material || '').toUpperCase());
}

/**
 * Verzauberungen, die auf dieses Item passen - vom Server beantwortet, nicht geraten.
 * Rueckgabe: [{key, maxLevel, startLevel, treasure, curse, label}]
 */
function itemEnchantments(material) {
    const entry = itemEntry(material);
    if (!entry || !entry.enchantments) return [];
    return entry.enchantments
        .map(key => {
            const meta = ITEM_CATALOG.enchantments.get(key) || { key: key, maxLevel: 1, startLevel: 1 };
            return Object.assign({ label: itemDisplayName(key) }, meta);
        })
        .sort((a, b) => a.label.localeCompare(b.label));
}

// ============================================
// Suche
// ============================================

/**
 * Sucht Items nach Enum-Namen UND lesbarem Namen, damit "diamond sw" DIAMOND_SWORD findet.
 *
 * Praefix-Treffer stehen vorn: wer "gold" tippt, will GOLD_INGOT sehen und nicht
 * BLOCK_OF_RAW_GOLD. Das Limit deckelt die DOM-Last - bei ~1600 Items wuerde ein
 * ungefiltertes Rendern das Panel spuerbar bremsen.
 *
 * @returns {{entries: Array, total: number}} total = Treffer vor dem Limit
 */
function searchItems(term, options) {
    const opts = options || {};
    const limit = opts.limit || 200;
    const pool = opts.category
        ? (ITEM_CATALOG.byCategory.get(opts.category) || [])
        : ITEM_CATALOG.materials;

    const query = String(term || '').trim().toLowerCase().replace(/\s+/g, '_');
    if (!query) {
        return { entries: pool.slice(0, limit), total: pool.length };
    }

    const prefix = [];
    const contains = [];
    for (const entry of pool) {
        const name = entry.name.toLowerCase();
        const index = name.indexOf(query);
        if (index === 0) {
            prefix.push(entry);
        } else if (index > 0) {
            contains.push(entry);
        }
    }
    const all = prefix.concat(contains);
    return { entries: all.slice(0, limit), total: all.length };
}

/**
 * Entprellt eine Suchfunktion. Ohne das feuert jeder Tastendruck ein Rendern
 * ueber bis zu 200 Icons.
 */
function debounce(fn, delay) {
    let timer = null;
    return function (...args) {
        clearTimeout(timer);
        timer = setTimeout(() => fn.apply(this, args), delay === undefined ? 150 : delay);
    };
}
