# Spawn Methods – Examples and Configuration Locations

This guide shows all supported spawn types and where they are configured in the YAML files.

## Overview of Spawn Types
- **SINGLE_POINT** - All players spawn at one location
- **RANDOM_RADIUS** - Random spawn within a circular radius
- **RANDOM_AREA** - Random spawn in a 2D rectangular area (X/Z)
- **RANDOM_CUBE** - Random spawn in a 3D box volume
- **MULTIPLE_SPAWNS** - Random selection from predefined spawn points
- **TEAM_SPAWNS** - Team-specific spawn points
- **COMMAND** - Execute a command to spawn players

## Events (config.yml)
Configuration location: `events.<id>.spawn-settings`

### SINGLE_POINT
All players spawn at exactly the same location.

```yml
events:
  <id>:
    spawn-settings:
      spawn-type: SINGLE_POINT
      single-spawn: { x: 0.5, y: 65, z: 0.5, yaw: 0, pitch: 0 }
```

**Use case**: Hub events, parkour start, small arenas

---

### RANDOM_RADIUS
Players spawn randomly within a circular radius around a center point.

```yml
events:
  <id>:
    spawn-settings:
      spawn-type: RANDOM_RADIUS
      random-radius:
        center-x: 0        # Center X coordinate
        center-z: 0        # Center Z coordinate
        radius: 20         # Radius in blocks
        min-distance: 8    # Minimum distance between players
```

**Use case**: Battle royale, free-for-all events, scattered spawning

**Notes**:
- Y coordinate is automatically detected (highest block at X/Z)
- `min-distance` prevents players from spawning too close together
- Players spawn in a circle, not a sphere (2D)

---

### RANDOM_AREA
Players spawn randomly within a rectangular area (2D).

```yml
events:
  <id>:
    spawn-settings:
      spawn-type: RANDOM_AREA
      random-area:
        point1: { x: -20, z: -20 }   # First corner
        point2: { x: 20,  z: 20 }    # Opposite corner
        min-distance: 10              # Minimum distance between players
```

**Use case**: Large rectangular arenas, defined boundaries

**Notes**:
- Only X and Z coordinates are used
- Y coordinate is automatically detected
- Creates a rectangular spawn zone

---

### RANDOM_CUBE
Players spawn randomly within a 3D box volume.

```yml
events:
  <id>:
    spawn-settings:
      spawn-type: RANDOM_CUBE
      random-cube:
        point1: { x: -20, y: 64, z: -20 }   # First corner (3D)
        point2: { x: 20,  y: 70, z: 20 }    # Opposite corner (3D)
        min-distance: 10                     # Minimum distance between players
```

**Use case**: Vertical arenas, sky islands, multi-level spawning

**Notes**:
- All three coordinates (X, Y, Z) are used
- Players can spawn at different heights
- Useful for 3D maps and floating islands

---

### MULTIPLE_SPAWNS
Players spawn at randomly selected predefined positions.

```yml
events:
  <id>:
    spawn-settings:
      spawn-type: MULTIPLE_SPAWNS
      multiple-spawns:
        spawns:
          spawn1: { x: 10,  y: 64, z: 0,  yaw: 90,  pitch: 0 }
          spawn2: { x: -10, y: 64, z: 0,  yaw: -90, pitch: 0 }
          spawn3: { x: 0,   y: 64, z: 10, yaw: 180, pitch: 0 }
          spawn4: { x: 0,   y: 64, z: -10, yaw: 0,  pitch: 0 }
```

**Use case**: Balanced spawn points, specific locations with good views

**Notes**:
- Each spawn can have a unique facing direction (yaw/pitch)
- Spawns are randomly assigned to players
- Ideal for carefully designed spawn locations

---

### TEAM_SPAWNS
Team-specific spawn points with multiple positions per team.

```yml
events:
  <id>:
    spawn-settings:
      spawn-type: TEAM_SPAWNS
      team-spawns:
        RED:
          spawn1: { x: 10,  y: 64, z: 0,  yaw: 90,  pitch: 0 }
          spawn2: { x: 12,  y: 64, z: 2,  yaw: 90,  pitch: 0 }
          spawn3: { x: 14,  y: 64, z: 4,  yaw: 90,  pitch: 0 }
        BLUE:
          spawn1: { x: -10, y: 64, z: 0,  yaw: -90, pitch: 0 }
          spawn2: { x: -12, y: 64, z: -2, yaw: -90, pitch: 0 }
          spawn3: { x: -14, y: 64, z: -4, yaw: -90, pitch: 0 }
```

**Use case**: Team vs team events, capture the flag, team deathmatch

**Notes**:
- Each team has its own set of spawn points
- Team members are randomly assigned to their team's spawns
- Teams are automatically assigned based on `game-mode` (TEAM_2, TEAM_3)

**Team Names**:
- For `TEAM_2`: Use `RED` and `BLUE`
- For `TEAM_3`: Use `RED`, `BLUE`, and `GREEN`

---

### COMMAND
Execute a console command to spawn players (advanced).

```yml
events:
  <id>:
    spawn-settings:
      spawn-type: COMMAND
      spawn-command: "tp {player} 0 64 0"
```

**Use case**: Custom spawn logic, integration with other plugins

**Available placeholders**:
- `{player}` - Player name
- Any custom placeholders from other plugins

**Examples**:
```yml
spawn-command: "tp {player} 0 64 0"                    # Simple teleport
spawn-command: "warp {player} spawn"                   # Warps plugin
spawn-command: "mvtp {player} WorldName:0,64,0"       # Multiverse teleport
```

---

## PvP Arenas (worlds.yml)
Configuration location: `worlds.<world>.pvpwager-spawn`

### FIXED_SPAWNS (Player Spawns + Spectator)
Dedicated spawn points for each role (recommended for PvP).

```yml
worlds:
  <world>:
    pvpwager-spawn:
      spawn-type: FIXED_SPAWNS
      spawns:
        spectator: { x: 0,   y: 80, z: 0,   yaw: 0,   pitch: 0 }
        player1:   { x: 10,  y: 64, z: 0,   yaw: 90,  pitch: 0 }
        player2:   { x: -10, y: 64, z: 0,   yaw: -90, pitch: 0 }
```

**Use case**: 1v1 PvP matches, balanced arenas

**Notes**:
- `player1` and `player2` are facing each other
- `spectator` spawn is typically elevated with a good view
- Recommended for fair, balanced PvP matches

---

### RANDOM_RADIUS (PvP Arena)
```yml
worlds:
  <world>:
    pvpwager-spawn:
      spawn-type: RANDOM_RADIUS
      random-radius:
        center-x: 0
        center-z: 0
        radius: 20
        min-distance: 8
```

**Use case**: Varied PvP encounters, exploration-based combat

---

### RANDOM_AREA (PvP Arena)
```yml
worlds:
  <world>:
    pvpwager-spawn:
      spawn-type: RANDOM_AREA
      random-area:
        point1: { x: -20, z: -20 }
        point2: { x: 20,  z: 20 }
        min-distance: 10
```

**Use case**: Large rectangular PvP arenas

---

### RANDOM_CUBE (PvP Arena)
```yml
worlds:
  <world>:
    pvpwager-spawn:
      spawn-type: RANDOM_CUBE
      random-cube:
        point1: { x: -20, y: 64, z: -20 }
        point2: { x: 20,  y: 70, z: 20 }
        min-distance: 10
```

**Use case**: Multi-level PvP arenas, vertical combat

---

### MULTIPLE_SPAWNS (PvP Arena)
```yml
worlds:
  <world>:
    pvpwager-spawn:
      spawn-type: MULTIPLE_SPAWNS
      spawns:
        spawn1: { x: 10,  y: 64, z: 0,  yaw: 90,  pitch: 0 }
        spawn2: { x: -10, y: 64, z: 0,  yaw: -90, pitch: 0 }
        spawn3: { x: 0,   y: 64, z: 10, yaw: 180, pitch: 0 }
```

**Use case**: Multiple balanced spawn options

---

### COMMAND (PvP Arena with Placeholders)
```yml
worlds:
  <world>:
    pvpwager-spawn:
      spawn-type: COMMAND
      command:
        command: "tp {player} {x} {y} {z}"
        placeholders:
          player: "{player}"
          x: "0"
          y: "64"
          z: "0"
```

**Use case**: Advanced spawn logic, plugin integration

---

## Best Practices

### Choosing the Right Spawn Type

**For PvP Arenas (1v1)**:
- ✅ Use `FIXED_SPAWNS` for fair, balanced matches
- ❌ Avoid `SINGLE_POINT` (players spawn on top of each other)

**For Team Events**:
- ✅ Use `TEAM_SPAWNS` for separate team spawn areas
- ✅ Consider `RANDOM_AREA` for each team's side

**For Free-for-All Events**:
- ✅ Use `RANDOM_RADIUS` or `RANDOM_AREA` for spread out spawning
- ✅ Use `MULTIPLE_SPAWNS` for carefully designed spawn points

**For Parkour/Linear Events**:
- ✅ Use `SINGLE_POINT` for starting line
- ✅ Use `MULTIPLE_SPAWNS` for multiple starting positions

**For Sky Islands/Vertical Maps**:
- ✅ Use `RANDOM_CUBE` for 3D spawning
- ✅ Use `MULTIPLE_SPAWNS` with different Y values

### min-distance Parameter
- Prevents players from spawning too close together
- Recommended values:
  - Small arenas: 5-8 blocks
  - Medium arenas: 10-15 blocks
  - Large arenas: 15-20 blocks
- If spawn attempts fail (too many players, not enough space), players will spawn at the fallback location

### Spectator Spawns
- Only applicable for PvP arenas with `FIXED_SPAWNS`
- Should have a good overview of the arena
- Typically elevated (y: 70-100)
- Not used in event spawn systems (events use their own spectator logic)

### Facing Direction (yaw/pitch)
- **yaw**: Horizontal rotation
  - 0 = South
  - 90 = West
  - 180 = North
  - -90 or 270 = East
- **pitch**: Vertical rotation
  - 0 = Straight ahead
  - -90 = Looking up
  - 90 = Looking down

### Testing Spawns
After configuring spawns:
1. Run `/eventpvp reload`
2. Test the event or arena
3. Check console for spawn-related errors
4. Verify players spawn in correct locations
5. Check `min-distance` prevents spawns in walls/lava

---

## Notes
- Spectator spawns for PvP arenas: `worlds.<world>.pvpwager-spawn.spawns.spectator`
- Events don't use spectator spawns; spectator logic belongs to the PvP module
- After making changes, always run `/eventpvp reload`
- For COMMAND spawn types, ensure the command exists and is valid
- Random spawn types automatically detect safe Y coordinates (highest block)
