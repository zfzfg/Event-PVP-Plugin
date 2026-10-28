const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../../src/main/resources/web');
const configs = { config: { settings: { language: 'de' }, events: {} }, worlds: { worlds: {} }, equipment: { equipment: {} }, webconfig: { web: { port: 8080 }, items: {} } };
const backup = (id = 'one') => ({ id, level: 4, exp: .73, contents: [
    { slot: 0, material: 'DIAMOND_SWORD', amount: 1, enchantments: { SHARPNESS: 3 } },
    { slot: 9, material: 'STONE', amount: 32 }, { slot: 35, material: 'BREAD', amount: 43 },
    { slot: 36, material: 'DIAMOND_BOOTS', amount: 1 }
], armor: [{ slot: 0, material: 'DIAMOND_BOOTS', amount: 1 }], offhand: null });
async function setup(page, intercept) {
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
        const url = new URL(route.request().url());
        const json = body => route.fulfill({ json: body });
        if (url.origin !== 'http://panel.test') return route.abort();
        if (intercept && await intercept(route, url)) return;
        const api = url.pathname;
        if (api === '/api/language/get') return json({ language: 'de' });
        if (api === '/api/auth/validate') return json({ authenticated: true, playerName: 'Admin' });
        if (api === '/api/status') return json({ data: { pluginName: 'Event-PVP', pluginVersion: '1.1.3', onlinePlayers: 1, maxPlayers: 20 } });
        const config = api.match(/^\/api\/(config|worlds|equipment|webconfig)\/get$/);
        if (config) return json({ success: true, data: configs[config[1]], revision: 'r1' });
        if (api === '/api/inventories/list') return json({ success: true, data: { player: url.searchParams.get('player'), backups: [{ id: 'one', type: 'manual', createdAt: 1 }], online: true } });
        if (api === '/api/inventories/get') return json({ success: true, data: backup(url.searchParams.get('id')) });
        if (api === '/api/materials') return json({ success: true, data: { materials: [], enchantments: [] } });
        if (api.startsWith('/api/')) return json({ success: true, data: { worlds: [], sessions: [], backups: [] } });
        if (api === '/item-assets/_index.json') return json([]);
        const file = path.join(root, api === '/' ? 'index.html' : api);
        if (fs.existsSync(file) && fs.statSync(file).isFile()) return route.fulfill({ path: file });
        return route.fulfill({ status: 404, body: '' });
    });
    await page.goto('http://panel.test');
    await page.waitForFunction(() => typeof CONFIG_LOAD_STATE !== 'undefined' && Object.values(CONFIG_LOAD_STATE).length === 4 && !Object.values(CONFIG_LOAD_STATE).includes('loading'));
    await page.waitForFunction(() => !document.getElementById('loading-spinner'));
    return errors;
}
async function showBackup(page) {
    await page.evaluate(() => { showSection('inventories'); document.getElementById('inventory-browser-player').value = 'Alex'; });
    await page.evaluate(() => loadInventoryBackups());
    await expect(page.locator('.inv-minecraft-canvas')).toBeVisible();
}

test('inventory geometry is stable across editor visits and viewport sizes', async ({ page }) => {
    const errors = await setup(page);
    await showBackup(page);
    const geometry = () => page.locator('.inv-minecraft-canvas .inventory-slot').evaluateAll(slots => slots.map(s => ({ width: s.getBoundingClientRect().width, height: s.getBoundingClientRect().height })));
    await page.locator('#toast-container').evaluate(e => e.replaceChildren());
    const before = await geometry();
    expect(before).toHaveLength(41);
    expect(before.every(s => s.width === 42 && s.height === 42)).toBeTruthy();
    await page.evaluate(() => { equipmentSets().test = { 'display-name': 'Test', armor: {}, inventory: [] }; editEquipment('test'); });
    await expect(page.locator('#equipment-editor-modal')).toBeVisible();
    await page.evaluate(() => closeEquipmentEditor());
    expect(await geometry()).toEqual(before);
    await expect(page.locator('#inventory-styles')).toHaveCount(0);
    await expect(page.locator('.inv-minecraft-canvas .inv-stat-value')).toHaveText(['77', '3 / 37', '1 / 4']);
    for (const width of [360, 768, 1024, 1440]) {
        await page.setViewportSize({ width, height: 950 });
        await page.screenshot({ path: `../../target/panel-${width}.png`, fullPage: true });
        const overflow = await page.evaluate(() => [...document.querySelectorAll('body *')].filter(e => e.getBoundingClientRect().right > innerWidth + 1 && e.getBoundingClientRect().width && getComputedStyle(e).position !== 'absolute').map(e => [e.tagName, e.className, e.getBoundingClientRect().right]).slice(0,25));
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), JSON.stringify(overflow)).toBeTruthy();
        const sizes = await geometry();
        expect(sizes.every(s => s.width === 42 && s.height === 42)).toBeTruthy();
        await page.screenshot({ path: `../../target/panel-${width}.png`, fullPage: true });
    }
    await page.evaluate(() => document.documentElement.style.zoom = '2');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
    expect(errors).toEqual([]);
});

test('normalization excludes invalid and duplicate slots and preserves empty slots', async ({ page }) => {
    await setup(page); await showBackup(page);
    const stats = await page.evaluate(() => {
        const data = normalizedBackup({ contents: [
            {slot: 0, material: 'STONE', amount: 5}, {slot: 0, material: 'STONE', amount: 7},
            {slot: -1, material: 'STONE', amount: 99}, {slot: 1, material: 'AIR', amount: 9},
            {slot: 2, material: 'STONE', amount: NaN}
        ], armor: [], offhand: {material: 'AIR', amount: 1} });
        renderInventoryBackupPreview(data); return data;
    });
    expect(stats.contents).toHaveLength(1);
    await expect(page.locator('.inv-minecraft-canvas .inv-stat-value')).toHaveText(['7', '1 / 37', '0 / 4']);
    await page.evaluate(() => renderInventoryBackupPreview({}));
    await expect(page.locator('.inv-minecraft-canvas .inventory-slot')).toHaveCount(41);
    await expect(page.locator('.inv-minecraft-canvas .filled')).toHaveCount(0);
});

test('stale backup responses cannot replace the current selection', async ({ page }) => {
    let release;
    const pending = new Promise(resolve => release = resolve);
    await setup(page, async (route, url) => {
        if (url.pathname === '/api/inventories/get' && url.searchParams.get('id') === 'slow') {
            await pending; await route.fulfill({ json: { success: true, data: backup('slow') } }).catch(() => {}); return true;
        }
    });
    await showBackup(page);
    await page.evaluate(() => { previewInventoryBackup('slow'); });
    expect(await page.evaluate(() => currentInventoryBackup())).toBeNull();
    await page.evaluate(() => previewInventoryBackup('fast'));
    release();
    await expect.poll(() => page.evaluate(() => currentInventoryBackup()?.id)).toBe('fast');
});

test('save uses submitted snapshots and records partial success', async ({ page }) => {
    let release;
    const pending = new Promise(resolve => release = resolve);
    let saves = 0;
    await setup(page, async (route, url) => {
        if (url.pathname.endsWith('/save') && !url.pathname.includes('language')) {
            saves++;
            await pending;
            if (url.pathname === '/api/worlds/save') await route.fulfill({ status: 500, json: {success:false} });
            else await route.fulfill({ json: {success:true, revision:'r2'} });
            return true;
        }
    });
    await page.evaluate(() => {
        CONFIG_STATE.config.settings.prefix = 'submitted';
        CONFIG_STATE.worlds.worlds = { arena: {} };
        window.saving = saveAllConfigs();
        saveAllConfigs();
    });
    await expect.poll(() => saves).toBe(2);
    await page.evaluate(() => CONFIG_STATE.config.settings.prefix = 'edited while saving');
    release();
    await page.evaluate(() => window.saving);
    expect(await page.evaluate(() => ({ baseline: CONFIG_BASELINE.config.settings.prefix,
        changed: hasConfigChanged('settings'), worlds: hasConfigChanged('worlds'), revision: CONFIG_REVISIONS.settings })))
        .toEqual({ baseline:'submitted', changed:true, worlds:true, revision:'r2' });
});

test('failed categories can be retried without discarding other edits', async ({ page }) => {
    let failing = true;
    await setup(page, async (route,url) => {
        if (failing && url.pathname === '/api/worlds/get') { await route.fulfill({ status:503, json:{success:false} }); return true; }
    });
    await expect(page.locator('#panel-load-errors')).toBeVisible();
    await page.evaluate(() => CONFIG_STATE.config.settings.prefix = 'keep me');
    failing = false;
    await page.locator('#panel-load-errors button').click();
    await expect(page.locator('#panel-load-errors')).toBeHidden();
    expect(await page.evaluate(() => CONFIG_STATE.config.settings.prefix)).toBe('keep me');
});

test('restore binds its target and blocks duplicate submissions; dialog focus and tooltip work', async ({ page }) => {
    let release;
    const pending = new Promise(resolve => release = resolve);
    const calls = [];
    await setup(page, async (route,url) => {
        if (url.pathname === '/api/inventories/restore') { calls.push(route.request().postDataJSON()); await pending; await route.fulfill({json:{success:true}}); return true; }
    });
    await showBackup(page);
    const item = page.locator('.inv-minecraft-canvas .filled').first();
    await item.focus();
    await expect(page.locator('#minecraft-tooltip')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.locator('#minecraft-tooltip')).toBeHidden();
    const restore = page.locator('.inv-actions-toolbar .btn-primary');
    await restore.focus(); await restore.click();
    await page.locator('#modal-restore-confirm-input').fill('Alex');
    await page.evaluate(() => { INVENTORY_BROWSER.player = 'Other'; INVENTORY_BROWSER.playerInput = 'Other'; window.restoring = executeRestoreFromModal(); executeRestoreFromModal(); });
    await expect.poll(() => calls.length).toBe(1);
    expect(calls[0]).toMatchObject({player:'Alex',backupId:'one'});
    release(); await page.evaluate(() => window.restoring);
    await expect(restore).toBeFocused();
});

test('status timer is unique and cleared on logout', async ({ page }) => {
    await setup(page);
    expect(await page.evaluate(() => {
        const timers = new Set(); const oldSet = window.setInterval, oldClear = window.clearInterval;
        window.setInterval = (...args) => { const id = oldSet(...args); timers.add(id); return id; };
        window.clearInterval = id => { timers.delete(id); oldClear(id); };
        startPanelStatus(); startPanelStatus(); const count = timers.size;
        stopPanelSession(); return {count,remaining:timers.size};
    })).toEqual({count:1,remaining:0});
});

test('all panel sections stay within narrow viewports and open editors retain input on language change', async ({ page }) => {
    const errors = await setup(page);
    await page.setViewportSize({width:360,height:800});
    const sections = await page.locator('.section').evaluateAll(elements => elements.map(e => e.id.replace('section-', '')));
    for (const section of sections) {
        await page.evaluate(section => showSection(section), section);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), section).toBeTruthy();
    }
    await page.evaluate(() => {
        equipmentSets().test = { 'display-name': 'Test', armor: {}, inventory: [], 'allowed-pvpwager-worlds': 'all' };
        editEquipment('test');
    });
    await expect(page.locator('#equipment-editor-modal')).toBeVisible();
    await page.locator('#equipment-name').fill('Unsaved name');
    await page.evaluate(() => changeLanguage('en'));
    await expect(page.locator('#equipment-name')).toHaveValue('Unsaved name');
    await page.keyboard.press('Escape');
    await expect(page.locator('#equipment-editor-modal')).toHaveCount(0);
    expect(errors).toEqual([]);
});

test('HTTP errors and invalid JSON never expose old backup actions; expiry ends session', async ({ page }) => {
    let mode = 'ok';
    await setup(page, async (route,url) => {
        if (mode !== 'ok' && url.pathname === '/api/inventories/get') {
            await route.fulfill(mode === 'invalid' ? { status:200, body:'not JSON' } : {status:401,json:{success:false}});
            return true;
        }
    });
    await showBackup(page); mode = 'invalid';
    await page.evaluate(() => previewInventoryBackup('bad'));
    await expect(page.locator('.inv-actions-toolbar')).toHaveCount(0);
    expect(await page.evaluate(() => currentInventoryBackup())).toBeNull();
    mode = 'expired';
    await page.evaluate(() => previewInventoryBackup('expired'));
    await expect(page.locator('#login-screen')).toBeVisible();
    expect(await page.evaluate(() => ({ authenticated:isAuthenticated,timer:panelStatusTimer }))).toEqual({authenticated:false,timer:null});
});

test('conflict confirmation happens once after every save settles and cancellation keeps edits', async ({ page }) => {
    let completed = 0, prompts = 0;
    await setup(page, async (route,url) => {
        if (url.pathname.endsWith('/save')) {
            completed++;
            await route.fulfill({status:409,json:{success:false,revision:'server'}}); return true;
        }
    });
    page.on('dialog', async dialog => { prompts++; expect(completed).toBe(2); await dialog.dismiss(); });
    await page.evaluate(async () => {
        CONFIG_STATE.config.settings.prefix = 'keep'; CONFIG_STATE.worlds.worlds = {changed:{}};
        await saveAllConfigs();
    });
    expect(prompts).toBe(1);
    expect(await page.evaluate(() => [CONFIG_STATE.config.settings.prefix,hasConfigChanged('worlds'),CONFIG_REVISIONS.settings])).toEqual(['keep',true,'r1']);
});

test('full inventory, missing icons, long tooltip, tap and focus trap', async ({ page }) => {
    await setup(page); await showBackup(page);
    await page.setViewportSize({width:360,height:700});
    await page.evaluate(() => renderInventoryBackupPreview({id:'full',contents:Array.from({length:36},(_,slot)=>({slot,material:'MISSING_MATERIAL',amount:64,displayName:'Very long name '.repeat(30),lore:['lore '.repeat(300)]})),armor:[],offhand:null}));
    await expect(page.locator('.inv-stat-value')).toHaveText(['2304','36 / 37','0 / 4']);
    await page.locator('.inv-minecraft-canvas .filled').first().click();
    await expect(page.locator('#minecraft-tooltip')).toBeVisible();
    expect(await page.locator('#minecraft-tooltip').evaluate(e => { const r=e.getBoundingClientRect();return r.left>=0 && r.top>=0 && r.right<=innerWidth && r.bottom<=innerHeight; })).toBeTruthy();
    await page.evaluate(() => openRestoreModal('one'));
    const modal = page.locator('#modal-restore-inventory');
    await modal.locator('.modal-close').focus(); await page.keyboard.press('Shift+Tab');
    expect(await modal.evaluate(e => e.contains(document.activeElement))).toBeTruthy();
    await page.keyboard.press('Escape'); await expect(modal).toBeHidden();
});

test('all seven translations provide the shared panel messages', async () => {
    const keys = Object.keys(JSON.parse(fs.readFileSync(path.join(root,'lang/en.json'),'utf8'))).filter(key=>key.startsWith('panel.'));
    for (const language of ['de','en','fr','es','pl','ru','ja']) {
        const strings=JSON.parse(fs.readFileSync(path.join(root,`lang/${language}.json`),'utf8'));
        for(const key of keys) expect(strings[key],`${language}: ${key}`).toBeTruthy();
    }
});

test('changing player cancels pending lists and backup-list buttons remain usable', async ({ page }) => {
    let release;
    const pending = new Promise(resolve => release = resolve);
    await setup(page, async (route,url) => {
        if (url.pathname === '/api/inventories/list' && url.searchParams.get('player') === 'Slow') {
            await pending; await route.fulfill({json:{success:true,data:{player:'Slow',backups:[]}}}).catch(()=>{}); return true;
        }
    });
    await showBackup(page);
    await page.evaluate(() => { document.getElementById('inventory-browser-player').value='Slow';loadInventoryBackups(); });
    await page.evaluate(async () => { document.getElementById('inventory-browser-player').value='New';await loadInventoryBackups(); });
    release();
    await expect.poll(() => page.evaluate(() => INVENTORY_BROWSER.player)).toBe('New');
    await page.locator('.inv-backup-actions .btn-primary').click();
    await expect(page.locator('#modal-restore-player-name')).toHaveText('New');
    await page.keyboard.press('Escape');
});

test('delete prevents duplicate requests and does not alter a newly selected player', async ({ page }) => {
    let release; let calls=0;
    const pending = new Promise(resolve => release=resolve);
    await setup(page,async(route,url)=>{
        if(url.pathname==='/api/inventories/delete'){calls++;await pending;await route.fulfill({json:{success:true}});return true;}
    });
    page.on('dialog',dialog=>dialog.accept());
    await showBackup(page);
    await page.evaluate(()=>{window.deleting=deleteInventoryBackup('one');deleteInventoryBackup('one');});
    await expect.poll(()=>calls).toBe(1);
    await page.evaluate(async()=>{document.getElementById('inventory-browser-player').value='New';await loadInventoryBackups();});
    release();await page.evaluate(()=>window.deleting);
    expect(await page.evaluate(()=>INVENTORY_BROWSER.backups.length)).toBe(1);
});

test('an old write cannot expire a new login session', async ({ page }) => {
    let release;
    const pending = new Promise(resolve => release=resolve);
    await setup(page,async(route,url)=>{
        if(url.pathname==='/api/config/save'){await pending;await route.fulfill({status:401,json:{success:false}});return true;}
    });
    await page.evaluate(()=>{CONFIG_STATE.config.settings.prefix='old session';window.oldSave=saveAllConfigs();});
    await page.evaluate(()=>{stopPanelSession();isAuthenticated=true;currentPlayer='New login';hideLoginScreen();});
    release();await page.evaluate(()=>window.oldSave);
    expect(await page.evaluate(()=>[isAuthenticated,currentPlayer,CONFIG_STATE.isSaving])).toEqual([true,'New login',false]);
    await expect(page.locator('#login-screen')).toBeHidden();
});

test('local SVGs, keyboard navigation, translated action names and theme roundtrip', async ({ page }) => {
    const saved = {};
    const errors = await setup(page, async (route, url) => {
        if (url.pathname === '/api/webconfig/get') {
            await route.fulfill({json:{success:true,data:{web:{theme:saved.theme || {'background-color':'#182028','text-secondary':'#ccddee'}}},revision:'r1'}}); return true;
        }
        if (url.pathname === '/api/webconfig/save') {
            const body = route.request().postDataJSON();
            saved.theme = body.data?.web?.theme || body.web?.theme;
            await route.fulfill({json:{success:true,revision:'r2'}}); return true;
        }
    });
    await expect(page.locator('.nav-item')).toHaveCount(7);
    for (const section of ['settings','events','worlds','equipment','inventories','expert','theme']) {
        const nav = page.locator(`.nav-item[data-section="${section}"]`);
        await nav.focus(); await page.keyboard.press('Enter');
        await expect(nav).toHaveAttribute('aria-current','page');
        await expect(nav.locator('svg')).toHaveCount(1);
    }
    expect(await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--background').trim())).toBe('#182028');
    await page.evaluate(() => updateThemeColor('text-secondary','#abcdef'));
    expect(await page.evaluate(() => CONFIG_STATE.webConfig.web.theme['text-secondary'])).toBe('#abcdef');
    expect(await page.evaluate(() => CONFIG_STATE.webConfig.web.theme['text-secondary-color'])).toBeUndefined();
    await page.evaluate(() => updateThemeColor('background','#202830'));
    await expect(page.locator('#theme-background')).toHaveValue('#202830');
    await page.evaluate(() => saveAllConfigs());
    expect(saved.theme['background-color']).toBe('#202830');
    await page.reload();
    await expect.poll(() => page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--background').trim())).toBe('#202830');
    await page.evaluate(() => { showSection('theme'); resetTheme(); });
    await expect(page.locator('#theme-background')).toHaveValue('#141414');
    await expect(page.locator('#theme-background').locator('..').locator('.color-input')).toHaveValue('#141414');
    await showBackup(page);
    await expect(page.locator('.enchant-indicator svg')).toHaveCount(1);
    expect(await page.locator('body').innerText()).not.toContain('${panelIcon');
    expect(await page.locator('link[href*="cdnjs"]').count()).toBe(0);
    expect(await page.locator('button').evaluateAll(buttons => buttons.filter(b => b.getClientRects().length && !b.textContent.trim() && !b.getAttribute('aria-label')).map(b => b.outerHTML))).toEqual([]);
    expect(errors).toEqual([]);
});

test('design screenshots cover all areas, dialogs, narrow widths and reduced motion', async ({ page }) => {
    const errors = await setup(page);
    for (const width of [320,360,768,1024,1440]) {
        await page.setViewportSize({width,height:950});
        for (const section of ['settings','events','worlds','equipment','inventories','expert','theme']) {
            await page.evaluate(section => showSection(section),section);
            expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${section} at ${width}`).toBeTruthy();
            await page.screenshot({path:`../../target/design-${section}-${width}.png`,fullPage:true});
        }
        await showBackup(page);
        await page.screenshot({path:`../../target/design-inventory-${width}.png`,fullPage:true});
        await page.evaluate(() => { equipmentSets().design = {'display-name':'Design',armor:{},inventory:[]}; editEquipment('design'); });
        await page.screenshot({path:`../../target/design-equipment-dialog-${width}.png`,fullPage:true});
        await page.keyboard.press('Escape');
        await page.locator('.inv-actions-toolbar .btn-primary').click();
        await page.screenshot({path:`../../target/design-restore-dialog-${width}.png`,fullPage:false});
        await page.keyboard.press('Escape');
    }
    await page.evaluate(() => document.documentElement.style.zoom = '2');
    for (const section of ['settings','events','worlds','equipment','inventories','expert','theme']) {
        await page.evaluate(section => showSection(section),section);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),`zoom ${section}`).toBeTruthy();
        await page.screenshot({path:`../../target/design-zoom-${section}.png`,fullPage:true});
    }
    await page.emulateMedia({reducedMotion:'reduce'});
    await page.evaluate(() => { CONFIG_STATE.isSaving = true; updateSyncStatusUI(); });
    expect(await page.locator('#sync-status-icon svg').evaluate(e => getComputedStyle(e).animationName)).toBe('none');
    await page.evaluate(() => { document.documentElement.style.zoom = ''; showLoginScreen(); });
    await page.setViewportSize({width:320,height:800});
    await expect(page.locator('#login-screen')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
    await page.screenshot({path:'../../target/design-login-320.png',fullPage:true});
    expect(errors).toEqual([]);
});

test('primary button contrast, theme validation and keyboard inventory editing', async ({page}) => {
    await setup(page);
    const contrast = () => page.locator('#btn-save-all').evaluate(button => {
        const color = value => {
            const c = value.match(/[\d.]+/g).slice(0,3).map(Number).map(v => v/255).map(v => v <= .04045 ? v/12.92 : ((v+.055)/1.055)**2.4);
            return c[0]*.2126+c[1]*.7152+c[2]*.0722;
        };
        const style = getComputedStyle(button), a = color(style.color), b = color(style.backgroundColor);
        return (Math.max(a,b)+.05)/(Math.min(a,b)+.05);
    });
    for (const accent of ['#4caf50','#112233','#ffffff']) {
        await page.evaluate(accent => updateThemeColor('primary',accent),accent);
        expect(await contrast()).toBeGreaterThanOrEqual(4.5);
        await page.locator('#btn-save-all').hover();
        expect(await contrast()).toBeGreaterThanOrEqual(4.5);
        await page.mouse.down(); expect(await contrast()).toBeGreaterThanOrEqual(4.5); await page.mouse.up();
    }
    await page.evaluate(() => updateThemeColor('primary','invalid'));
    expect(await page.evaluate(() => CONFIG_STATE.webConfig.web.theme['primary-color'])).toBe('#ffffff');
    await page.evaluate(() => { equipmentSets().keyboard = {'display-name':'Keyboard',armor:{},inventory:[{slot:0,item:'STONE',amount:1}]}; editEquipment('keyboard'); });
    // Native keyboard activation also works for the existing custom editor tabs.
    const inventoryTab = page.locator('#equipment-editor-modal .tab').nth(2);
    await inventoryTab.focus(); await page.keyboard.press('Enter');
    const slot = page.locator('#inv-slot-0');
    await expect(slot).toBeVisible();
    await slot.focus(); await page.keyboard.press('Enter');
    await expect(page.locator('#item-edit-modal')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(slot).toBeFocused();
});
