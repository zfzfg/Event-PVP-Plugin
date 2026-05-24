# Spawn-Methoden – Beispiele und Einfügeorte

Dieser Leitfaden zeigt alle unterstützten Spawn-Varianten und wo sie in den YAML-Dateien konfiguriert werden.

## Übersicht der Spawn-Typen
- SINGLE_POINT
- RANDOM_RADIUS
- RANDOM_AREA
- RANDOM_CUBE
- MULTIPLE_SPAWNS
- TEAM_SPAWNS
- COMMAND

## Events (config.yml)
Einfügeort: `events.<id>.spawn-settings`

### SINGLE_POINT
```yml
events:
  <id>:
    spawn-settings:
      spawn-type: SINGLE_POINT
      single-spawn: { x: 0.5, y: 65, z: 0.5, yaw: 0, pitch: 0 }
```

### RANDOM_RADIUS
```yml
events:
  <id>:
    spawn-settings:
      spawn-type: RANDOM_RADIUS
      random-radius:
        center-x: 0
        center-z: 0
        radius: 20
        min-distance: 8
```

### RANDOM_AREA
```yml
events:
  <id>:
    spawn-settings:
      spawn-type: RANDOM_AREA
      random-area:
        point1: { x: -20, z: -20 }
        point2: { x: 20,  z: 20 }
        min-distance: 10
```

### RANDOM_CUBE
```yml
events:
  <id>:
    spawn-settings:
      spawn-type: RANDOM_CUBE
      random-cube:
        point1: { x: -20, y: 64, z: -20 }
        point2: { x: 20,  y: 70, z: 20 }
        min-distance: 10
```

### MULTIPLE_SPAWNS
```yml
events:
  <id>:
    spawn-settings:
      spawn-type: MULTIPLE_SPAWNS
      multiple-spawns:
        spawns:
          a: { x: 10, y: 64, z: 0, yaw: 90,  pitch: 0 }
          b: { x: -10, y: 64, z: 0, yaw: -90, pitch: 0 }
```

### TEAM_SPAWNS
```yml
events:
  <id>:
    spawn-settings:
      spawn-type: TEAM_SPAWNS
      team-spawns:
        team1:
          a: { x: 10,  y: 64, z: 0, yaw: 90,  pitch: 0 }
          b: { x: 12,  y: 64, z: 2, yaw: 90,  pitch: 0 }
        team2:
          a: { x: -10, y: 64, z: 0, yaw: -90, pitch: 0 }
          b: { x: -12, y: 64, z: -2, yaw: -90, pitch: 0 }
```

### COMMAND
```yml
events:
  <id>:
    spawn-settings:
      spawn-type: COMMAND
      spawn-command: "tp {player} 0 64 0"
```

## PvP-Arenen (worlds.yml)
Einfügeort: `worlds.<welt>.pvpwager-spawn`

### FIXED_SPAWNS (Spieler-Spawns + Spectator)
```yml
worlds:
  <welt>:
    pvpwager-spawn:
      spawn-type: FIXED_SPAWNS
      spawns:
        spectator: { x: 0,  y: 80, z: 0,  yaw: 0,  pitch: 0 }
        player1:   { x: 10, y: 64, z: 0,  yaw: 90, pitch: 0 }
        player2:   { x: -10, y: 64, z: 0, yaw: -90, pitch: 0 }
```

### RANDOM_RADIUS
```yml
worlds:
  <welt>:
    pvpwager-spawn:
      spawn-type: RANDOM_RADIUS
      random-radius:
        center-x: 0
        center-z: 0
        radius: 20
        min-distance: 8
```

### RANDOM_AREA
```yml
worlds:
  <welt>:
    pvpwager-spawn:
      spawn-type: RANDOM_AREA
      random-area:
        point1: { x: -20, z: -20 }
        point2: { x: 20,  z: 20 }
        min-distance: 10
```

### RANDOM_CUBE
```yml
worlds:
  <welt>:
    pvpwager-spawn:
      spawn-type: RANDOM_CUBE
      random-cube:
        point1: { x: -20, y: 64, z: -20 }
        point2: { x: 20,  y: 70, z: 20 }
        min-distance: 10
```

### MULTIPLE_SPAWNS
```yml
worlds:
  <welt>:
    pvpwager-spawn:
      spawn-type: MULTIPLE_SPAWNS
      spawns:
        a: { x: 10,  y: 64, z: 0, yaw: 90,  pitch: 0 }
        b: { x: -10, y: 64, z: 0, yaw: -90, pitch: 0 }
```

### COMMAND (mit Platzhaltern)
```yml
worlds:
  <welt>:
    pvpwager-spawn:
      spawn-type: COMMAND
      command:
        command: "tp {player} 0 64 0"
        placeholders:
          player: "{player}"
```

## Hinweise
- Spectator-Spawn für PvP-Arenen: `worlds.<welt>.pvpwager-spawn.spawns.spectator`.
- Events nutzen keinen Spectator-Spawn; Zuschauer-Logik gehört zum PvP-Modul.