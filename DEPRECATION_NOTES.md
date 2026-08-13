# Notizen zum Deprecation-Aufraeumen

## Baseline
- **Datum:** 2026-08-13
- **Ausgangsbasis:** Version 1.1.0 auf Purpur 26.2
- **Baseline-Tests:** 152 Tests, 0 Failures, 0 Errors (`BUILD SUCCESS`)

## Entscheidungen
- `Text.ofItem(...)` wird strikt fuer alle `ItemMeta#displayName` und `ItemMeta#lore` Aufrufe verwendet (`TextDecoration.ITALIC = false`).
- `Text.of(...)` wird fuer GUI-Titel bei `Bukkit.createInventory` verwendet.
- `org.bukkit.ChatColor` bleibt gemaess Phase D6 unveraendert stehen.

## Verhaltensbeobachtungen
*(Werden bei relevanten Anpassungen erfasst)*

## BLOCKIERT
*(Keine aktuellen Blockaden)*

## Offene Punkte fuer den Menschen
*(Werden waehrend der Migration erfasst)*
