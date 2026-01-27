# Cobblemon Team Analyzer

A NeoForge mod for Minecraft 1.21.1 that provides team building and analysis tools for [Cobblemon](https://cobblemon.com/).

## Features

### Team Analysis
- Analyze your party or saved teams for type coverage
- View team weaknesses, resistances, and immunities
- Get suggestions for improving team composition
- Command: `/cta analyze` or press `J` (default keybind)

### Team Builder
- Drag-and-drop team composition interface
- Browse your entire Cobblemon collection (Party + PC)
- Search, filter, and sort Pokemon
- Save and load team presets
- Command: `/cta builder`

### Opponent Counter System
- Select opponents to counter (Type Gyms, Dual-Type Elites)
- Optional RCT (Radical Cobblemon Trainers) integration for trainer data
- Auto-build optimal teams against selected opponents
- Visual highlighting for suggested Pokemon

### Pokemon Browser
- View all your Pokemon in a grid layout
- Filter by shiny, legendary, party/PC
- Sort by name, level, or IVs
- Command: `/cta browse`

### Team Presets
- Save up to 20 team presets per player
- Quick-swap teams from the PC GUI
- Preset buttons integrated into Cobblemon's PC interface

## Commands

| Command | Description |
|---------|-------------|
| `/cta analyze [preset]` | Analyze current party or a saved preset |
| `/cta builder` | Open the Team Builder |
| `/cta browse [preset]` | Browse Pokemon collection |
| `/cta presets` | List saved presets |
| `/cta save <name>` | Save current party as preset |
| `/cta load <name>` | Load preset to party |
| `/cta delete <name>` | Delete a preset |

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1+
- Cobblemon 1.7+
- Kotlin for Forge

## Installation

1. Install NeoForge for Minecraft 1.21.1
2. Install Cobblemon and Kotlin for Forge
3. Download the latest release and place in your `mods` folder

## Optional Integrations

- **RCT (Radical Cobblemon Trainers)**: When installed, the Team Builder can show RCT trainer teams as opponents to counter

## Building from Source

```bash
./gradlew build
```

The built jar will be in `build/libs/`.

## License

MIT License - see [LICENSE](LICENSE)
