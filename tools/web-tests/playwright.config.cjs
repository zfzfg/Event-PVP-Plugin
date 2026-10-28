const { defineConfig } = require('@playwright/test');
module.exports = defineConfig({ testDir: '.', testMatch: '*.spec.cjs', workers: 1, use: { headless: true }, reporter: 'list' });
