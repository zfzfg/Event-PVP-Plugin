const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.join(__dirname, '../src/main/resources/web');
const app = fs.readFileSync(path.join(root, 'app.js'), 'utf8');
const editors = fs.readFileSync(path.join(root, 'editors.js'), 'utf8');
test('dynamic actions carry opaque identifiers and preserve hostile values as data', () => {
    const listeners = {};
    const sandbox = { document: { documentElement: {}, addEventListener: (type, callback) => listeners[type] = callback }, MutationObserver: class { observe() {} } };
    vm.createContext(sandbox);
    vm.runInContext(app.slice(0, app.indexOf('const CONFIG_REVISIONS')), sandbox);
    sandbox.payload = `');globalThis.compromised=true;//<img src=x onerror=alert(1)>`;
    vm.runInContext('globalThis.attrs = eventAttrs("click", function () { globalThis.received = payload; });', sandbox);
    assert.match(sandbox.attrs, /^data-panel-click="[0-9]+"$/);
    assert.equal(sandbox.compromised, undefined);
    const id = sandbox.attrs.match(/[0-9]+/)[0];
    listeners.click({ target: { closest: () => ({ getAttribute: () => id }) } });
    assert.equal(sandbox.received, sandbox.payload); assert.equal(sandbox.compromised, undefined);
});
test('templates do not interpolate configuration into executable handler attributes', () => {
    for (const source of [app, editors]) assert.doesNotMatch(source, /on(?:click|change|input|keydown)="[^"\n]*\$\{/);
    assert.match(editors, /escapeHtml\(group\)/);
    assert.match(editors, /escapeHtml\(g\)/);
});

test('picker callbacks are callable references and selectors do not allocate event bindings', () => {
    assert.doesNotMatch(editors, /querySelector[^\n]*eventAttrs/);
    assert.doesNotMatch(editors, /renderPickerSection\([^\n]*'setArmorItem'/);
    const listeners = {};
    const sandbox = { document: { documentElement: {}, addEventListener: (type, callback) => listeners[type] = callback }, MutationObserver: class { observe() {} }, i18n: { t: key => key }, escapeHtml: value => value, escapeAttr: value => value, itemDisplayName: value => value, itemIconHtml: () => '' };
    vm.createContext(sandbox);
    vm.runInContext(app.slice(0, app.indexOf('const CONFIG_REVISIONS')), sandbox);
    const start = editors.indexOf('function renderPickerSection(');
    const end = editors.indexOf('/** Sucht im Enum-Namen', start);
    vm.runInContext(editors.slice(start, end), sandbox);
    vm.runInContext('globalThis.html = renderPickerSection("title", [{name:"DIAMOND"}], value => globalThis.selected = value, false);', sandbox);
    const id = sandbox.html.match(/data-panel-click="([0-9]+)"/)[1];
    listeners.click({target:{closest:() => ({getAttribute:() => id})}});
    assert.equal(sandbox.selected, 'DIAMOND');
});
