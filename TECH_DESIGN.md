# Cobblemon Team Builder - Technical Design Document

## Overview

A NeoForge 1.21.1 addon mod for Cobblemon that provides advanced team management with search/filter/sort capabilities and team composition analysis to help players build balanced teams.

## Target Platform

- Minecraft: 1.21.1
- Loader: NeoForge 21.4.x
- Cobblemon: 1.7.x
- Java: 21
- Kotlin for Forge: Required (Cobblemon dependency)
- Side: Both (GUI client-side, data server-side)

## Design Philosophy

- **Keep it simple** - Focus on core features, avoid over-engineering
- **Social** - Easy team sharing between players
- **Educational** - Help new players understand type matchups

## Core Features

### 1. Pokemon Browser with Search/Filter/Sort

#### Search
- Species name (fuzzy match)
- Nickname
- Held item name

#### Filters
| Filter | Type | Values |
|--------|------|--------|
| Type | Multi-select | All 18 Pokemon types |
| Generation | Multi-select | Gen 1-9 |
| Shiny | Toggle | Yes/No/Both |
| Level | Range slider | 1-100 |
| Ability | Dropdown | Player's Pokemon abilities |
| Nature | Dropdown | All 25 natures |
| Has Move | Text search | Move name |
| IV Total | Range slider | 0-186 |
| EV Total | Range slider | 0-510 |
| Ball Type | Multi-select | All Poke Ball types |
| Legendary | Toggle | Yes/No/Both |
| Can Evolve | Toggle | Yes/No/Both |

#### Sort Options
- Pokedex number (asc/desc)
- Level (asc/desc)
- Species name (A-Z, Z-A)
- Total IVs (asc/desc)
- Total EVs (asc/desc)
- Date caught (newest/oldest)
- CP/Battle rating (calculated)

### 2. Team Preset System

#### Data Model
```java
public record TeamPreset(
    UUID id,
    String name,
    String description,
    List<UUID> pokemonUUIDs,  // References to Pokemon in PC/Party
    long createdAt,
    long lastUsedAt,
    @Nullable String iconSpecies  // Species for display icon
) {}
```

#### Operations
- Save current party as preset
- Load preset → current party moves to PC, preset Pokemon move to party
- Rename/delete/reorder presets
- Duplicate preset
- Maximum presets per player: configurable (default 20)

#### Edge Cases
- Pokemon in preset was released → show as "missing" slot
- Pokemon in preset is currently in party → no-op for that slot
- Pokemon in preset is in another player's trade → block load with message

### 3. Team Composition Analyzer

#### Type Effectiveness Engine
```java
public class TypeEffectiveness {
    // 18x18 matrix of type matchups
    // Values: 0 (immune), 0.5 (resist), 1 (neutral), 2 (super effective)

    public double getMultiplier(PokemonType attack, PokemonType defend);
    public double getMultiplier(PokemonType attack, PokemonType defend1, PokemonType defend2);
}
```

#### Team Analysis Output
```java
public record TeamAnalysis(
    Map<PokemonType, DefenseRating> defensiveProfile,  // How team handles each attacking type
    Map<PokemonType, CoverageRating> offensiveProfile, // What types team can hit effectively
    List<PokemonType> criticalWeaknesses,              // 3+ Pokemon weak to this type
    List<PokemonType> missingCoverage,                 // No super-effective moves against
    List<String> suggestions,                          // Human-readable tips
    double overallScore                                // 0-100 team balance score
) {}

public enum DefenseRating {
    IMMUNE,      // At least one Pokemon immune
    STRONG,      // Most Pokemon resist
    NEUTRAL,     // Mixed
    WEAK,        // Most Pokemon weak
    CRITICAL     // 3+ Pokemon take super-effective
}
```

#### Suggestion Engine
Generates contextual advice:
- "Your team has 3 Pokemon weak to Ground. Consider adding a Flying type."
- "No Pokemon can hit Steel types super-effectively. Teach a Fire/Fighting/Ground move."
- "All your Pokemon are physical attackers. Consider a special attacker for defensive walls."
- "Good type diversity! Your team covers 15/18 types offensively."

### 4. Counter Lookup Tool

- Input: Pokemon species or type
- Output:
  - Types that are super-effective against it
  - Pokemon in your collection that counter it
  - Suggested moves for coverage

### 5. Team Sharing (Chat-based)

Simple text-based team sharing between players.

#### Export Format
```
[Pokemon:Team Name]species1,species2,species3,species4,species5,species6
```

Example:
```
[Pokemon:PvP Main]charizard,pikachu,bulbasaur,squirtle,eevee,snorlax
```

#### Export Flow
1. Player clicks "Share" button on a team preset
2. Code is generated and either:
   - Copied to clipboard (with toast notification)
   - Posted directly to chat (configurable)

#### Import Flow
1. Player sees code in chat
2. Either:
   - Click the code (if clickable chat is supported)
   - Use `/teambuilder import <code>` command
3. Team template is added to their presets
4. Pokemon they own are auto-matched; missing ones show as empty slots with the species name

#### Edge Cases
- Species name doesn't exist → Skip with warning
- Player has multiple of same species → Pick highest level or prompt
- Code is malformed → Show error, don't crash

#### Namespaced Species IDs (Fakemon Support)
Cobblemon addons (Gravelmon, Alatia, GG&T, etc.) register custom Pokemon with namespaced IDs.

```
cobblemon:charizard      - Base Cobblemon
alatia:somefakemon       - Alatia's Fakemon Pack
gravelmon:custommon      - Gravelmon addon
```

Share codes must preserve namespaces:
```
[Pokemon:Mixed Team]cobblemon:charizard,alatia:firefakemon,cobblemon:pikachu
```

For brevity, `cobblemon:` prefix can be omitted (assumed default):
```
[Pokemon:Mixed Team]charizard,alatia:firefakemon,pikachu
```

Import parsing:
1. If species has `:`, treat as full namespace
2. If no `:`, prepend `cobblemon:`
3. Query Cobblemon's species registry to validate

## Architecture

### Module Structure
```
cobblemon-team-builder/
├── src/main/java/com/example/teambuilder/
│   ├── TeamBuilderMod.java              # Main mod entry point
│   │
│   ├── api/                             # Public API for other mods
│   │   └── TeamBuilderAPI.java
│   │
│   ├── data/
│   │   ├── TeamPreset.java              # Preset data record
│   │   ├── TeamAnalysis.java            # Analysis result record
│   │   ├── FilterCriteria.java          # Search/filter parameters
│   │   └── SortOption.java              # Sort configuration
│   │
│   ├── analysis/
│   │   ├── TypeEffectiveness.java       # Type chart engine
│   │   ├── TeamAnalyzer.java            # Analyzes team composition
│   │   ├── SuggestionEngine.java        # Generates improvement tips
│   │   └── CounterLookup.java           # Find counters for species/type
│   │
│   ├── storage/
│   │   ├── PresetStorage.java           # Per-player preset persistence
│   │   ├── PresetCodec.java             # JSON serialization
│   │   └── PlayerPresetCapability.java  # Forge capability attachment
│   │
│   ├── search/
│   │   ├── PokemonSearchEngine.java     # Search/filter/sort logic
│   │   ├── FilterPredicates.java        # Individual filter implementations
│   │   └── SearchIndex.java             # Cached index for fast search
│   │
│   ├── network/
│   │   ├── ModPackets.java              # Packet registration
│   │   ├── SyncPresetsPacket.java       # Server→Client preset sync
│   │   ├── SavePresetPacket.java        # Client→Server save request
│   │   ├── LoadPresetPacket.java        # Client→Server load request
│   │   └── RequestAnalysisPacket.java   # Request team analysis
│   │
│   ├── client/
│   │   ├── gui/
│   │   │   ├── TeamBuilderScreen.java   # Main GUI screen
│   │   │   ├── PokemonBrowserWidget.java # Scrollable Pokemon grid
│   │   │   ├── FilterPanelWidget.java   # Filter controls
│   │   │   ├── TeamSlotWidget.java      # Drag-drop team slot
│   │   │   ├── PresetListWidget.java    # Saved presets sidebar
│   │   │   ├── AnalysisPanelWidget.java # Type coverage display
│   │   │   └── CounterLookupWidget.java # Counter search tool
│   │   │
│   │   ├── render/
│   │   │   ├── PokemonSlotRenderer.java # Pokemon icon + info rendering
│   │   │   └── TypeBadgeRenderer.java   # Type icons
│   │   │
│   │   └── ClientEvents.java            # Keybind registration
│   │
│   └── command/
│       └── TeamBuilderCommands.java     # Admin/debug commands
│
├── src/main/resources/
│   ├── META-INF/
│   │   ├── mods.toml                    # NeoForge mod metadata
│   │   └── accesstransformer.cfg        # If needed
│   │
│   ├── assets/teambuilder/
│   │   ├── textures/
│   │   │   └── gui/
│   │   │       ├── team_builder_bg.png  # Main GUI background
│   │   │       ├── slot.png             # Pokemon slot
│   │   │       ├── types/               # Type icons (18 types)
│   │   │       └── widgets/             # Button sprites
│   │   │
│   │   └── lang/
│   │       └── en_us.json               # Translations
│   │
│   └── data/teambuilder/
│       └── type_chart.json              # Type effectiveness data
│
└── build.gradle                         # Build configuration
```

### Cobblemon Integration Points

```java
// Getting player's Pokemon storage
CobblemonStorage storage = Cobblemon.storage;
PlayerPartyStore party = storage.getParty(player);
PCStore pc = storage.getPC(player);

// Iterating Pokemon
for (Pokemon pokemon : pc.getAll()) {
    Species species = pokemon.getSpecies();
    PokemonType type1 = pokemon.getPrimaryType();
    PokemonType type2 = pokemon.getSecondaryType(); // nullable

    int level = pokemon.getLevel();
    Nature nature = pokemon.getNature();
    Ability ability = pokemon.getAbility();

    IVs ivs = pokemon.getIvs();
    EVs evs = pokemon.getEvs();

    MoveSet moves = pokemon.getMoveSet();
    boolean shiny = pokemon.getShiny();
    UUID uuid = pokemon.getUUID();
}

// Moving Pokemon between party and PC
party.remove(pokemon);
pc.add(pokemon);

pc.remove(pokemon);
party.add(pokemon);
```

### Network Protocol

#### Client → Server
| Packet | Payload | Response |
|--------|---------|----------|
| `SavePresetPacket` | name, description, pokemon UUIDs | `SyncPresetsPacket` |
| `LoadPresetPacket` | preset UUID | Success/Error + `SyncPresetsPacket` |
| `DeletePresetPacket` | preset UUID | `SyncPresetsPacket` |
| `RenamePresetPacket` | preset UUID, new name | `SyncPresetsPacket` |
| `RequestAnalysisPacket` | list of Pokemon UUIDs | `AnalysisResultPacket` |

#### Server → Client
| Packet | Payload | Trigger |
|--------|---------|---------|
| `SyncPresetsPacket` | All player's presets | Login, any preset change |
| `AnalysisResultPacket` | TeamAnalysis record | On request |

### Data Persistence

Presets stored in world save under:
```
world/data/teambuilder/
└── presets/
    └── <player-uuid>.json
```

Format:
```json
{
  "version": 1,
  "presets": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "PvP Main",
      "description": "Balanced competitive team",
      "pokemon": [
        "pokemon-uuid-1",
        "pokemon-uuid-2",
        "pokemon-uuid-3",
        "pokemon-uuid-4",
        "pokemon-uuid-5",
        "pokemon-uuid-6"
      ],
      "createdAt": 1705849200000,
      "lastUsedAt": 1705935600000,
      "iconSpecies": "charizard"
    }
  ]
}
```

## GUI Design

### Main Screen Layout (480x320)
```
┌─────────────────────────────────────────────────────────────────────────────┐
│ TEAM BUILDER                                              [?] [Settings] [X]│
├───────────────────────────────────────────────┬─────────────────────────────┤
│ Search: [_______________] [🔍]                │  TEAM ANALYSIS              │
│                                               │                             │
│ Filters: [Type ▼] [Level ▼] [More ▼]  [Sort ▼]│  Defense:                   │
├───────────────────────────────────────────────│  Normal   ████████░░  OK    │
│                                               │  Fire     ██████░░░░  Weak  │
│    Pokemon Grid (scrollable)                  │  Water    ██████████  Good  │
│   ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ │  ...                        │
│   │ Pokemon │ Pokemon │ Pokemon │ Pokemon │ Pokemon │  │                             │
│   └────┘ └────┘ └────┘ └────┘ └────┘ └────┘ │  ⚠️ Weak to: Ground, Ice    │
│   ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ │                             │
│   │ Pokemon │ Pokemon │ Pokemon │ Pokemon │ Pokemon │  │  Coverage:                  │
│   └────┘ └────┘ └────┘ └────┘ └────┘ └────┘ │  ✓ Hits 14/18 types SE      │
│   ... (more rows)                            │  ✗ No counter: Steel        │
│                                               │                             │
├───────────────────────────────────────────────┤  💡 Suggestions:            │
│ CURRENT TEAM     [Save] [Clear]              │  "Add Fighting type for     │
│ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐  │   Steel coverage"           │
│ │ 1  │ │ 2  │ │ 3  │ │ 4  │ │ 5  │ │ 6  │  │                             │
│ └────┘ └────┘ └────┘ └────┘ └────┘ └────┘  │  [Counter Lookup...]        │
├───────────────────────────────────────────────┼─────────────────────────────┤
│ SAVED TEAMS                                   │                             │
│ [PvP Main ⚡] [Shiny Squad ✨] [Gym Rush] [+] │  [Load Selected] [Delete]   │
└───────────────────────────────────────────────┴─────────────────────────────┘
```

### Interactions
- **Drag & Drop**: Drag Pokemon from grid to team slots
- **Right-click Pokemon**: Quick actions menu (add to team, view stats, find counters)
- **Click team slot**: Remove Pokemon (returns to grid)
- **Click saved team**: Select for load/delete
- **Double-click saved team**: Quick load

### Keybinds
- `B` (configurable): Open Team Builder
- `Escape`: Close screen
- `Ctrl+S`: Quick save current team
- `Ctrl+F`: Focus search box

## Configuration

`config/teambuilder.toml`:
```toml
[general]
# Maximum saved presets per player
maxPresets = 20

# Show team analysis panel by default
showAnalysis = true

# Default sort option
defaultSort = "pokedex"

[keybinds]
# Key to open team builder (GLFW key code)
openKey = 66  # B

[analysis]
# Threshold for "critical weakness" warning
criticalWeaknessCount = 3

# Show move coverage analysis (more CPU intensive)
analyzeMoves = true

[ui]
# Pokemon grid columns
gridColumns = 6

# Show Pokemon levels in grid
showLevels = true

# Show shiny sparkle effect
showShinyEffect = true
```

## Dependencies

```groovy
// build.gradle
dependencies {
    // NeoForge
    implementation "net.neoforged:neoforge:${neoforge_version}"

    // Cobblemon
    compileOnly "com.cobblemon:mod:${cobblemon_version}"

    // Optional: JEI integration for Pokemon lookup
    compileOnly "mezz.jei:jei-${mc_version}-common:${jei_version}"
    compileOnly "mezz.jei:jei-${mc_version}-neoforge:${jei_version}"
}
```

## Future Enhancements (v2.0+)

1. **Battle History Integration**
   - Track win/loss with each preset
   - Suggest improvements based on losses

2. **Breeding Planner**
   - Integration with Cobbreeding
   - Show optimal breeding paths for desired Pokemon

3. **Tournament Mode**
   - Lock team before tournament
   - Team validation against rulesets (level caps, banned Pokemon)

## Commands

```
/teambuilder                    - Open team builder GUI (alias: /tb)
/teambuilder save <name>        - Save current party as preset
/teambuilder load <name>        - Load a preset
/teambuilder list               - List saved presets
/teambuilder delete <name>      - Delete a preset
/teambuilder share <name>       - Post team code to chat
/teambuilder import <code>      - Import team from code
```

## Development Milestones

### Phase 1: Core Infrastructure - COMPLETE (2026-01-21)
- [x] Project setup with NeoForge + Cobblemon 1.7
- [x] Data models (TeamPreset, FilterCriteria, PokemonSummary)
- [x] Preset storage and persistence (JSON per-player)
- [x] Basic network packets (8 payload types)
- [x] Commands (/cta save/load/list/delete/analyze/browse)
- [x] PC Integration - buttons added to Cobblemon's PCGUI

### Phase 2: Team Sharing - COMPLETE (2026-01-21)
- [x] Export format implementation (`[Pokemon:Name]species1,species2,...`)
- [x] Import parsing and validation with namespace support
- [x] /cta share and import commands
- [x] Clickable chat codes with hover tooltips
- [ ] Clipboard integration (deferred)

### Phase 3: Search & Filter - COMPLETE (2026-01-23)
- [x] PokemonBrowserScreen with grid display
- [x] Core filter predicates (type, level, shiny, legendary, party/PC)
- [x] Sort options (name, level, IV total)
- [x] Search by Pokemon name
- [x] Missing Pokemon shown greyed out with "MISSING" label
- [x] Scrollable grid with tooltips

### Phase 4: Team Analysis - COMPLETE (2026-01-21)
- [x] TypeEffectiveness engine (18x18 matrix)
- [x] TeamAnalyzer implementation
- [x] SuggestionEngine with contextual tips
- [x] Defense/offense profile visualization
- [ ] Counter lookup (deferred to v2)

### Phase 5: GUI - PARTIAL
- [x] TeamAnalysisScreen (type coverage visualization)
- [x] PokemonBrowserScreen (grid with search/filter/sort)
- [x] PresetSaveScreen / PresetLoadScreen (dialogs)
- [ ] Main TeamBuilderScreen with drag-drop team building
- [ ] Team slot drag-drop
- [ ] Full preset management UI

### Phase 6: Polish
- [ ] Keybind configuration
- [ ] Config file
- [ ] Localization (en_us)
- [ ] Performance optimization

## Open Questions

1. **Cobblemon 1.7 API**: Verify storage/party APIs haven't changed from 1.6
2. **PC iteration performance**: Large PCs (1000+ Pokemon) may need pagination/virtualization
3. **Fakemon testing**: Test with Alatia's Fakemon Pack to verify:
   - Species registry returns correct type data for namespaced IDs
   - Type analysis works correctly for custom Pokemon
   - Share codes with mixed namespaces import properly

## Decisions Made

- Presets are per-world (stored in world save data)
- Team sharing uses simple text format: `[Pokemon:Name]species1,species2,...`
- Keep it simple - no optional mod integrations for v1
- English only for v1, structure for i18n later

---

## Implementation Notes (2026-01-23)

### Architecture Implemented
- **Language:** Kotlin (leverages Cobblemon's Kotlin dependency)
- **Mod ID:** `cobblemonteamanalysis`
- **Commands:** `/cta` (alias) and `/teamanalysis`
- **Network:** 8 custom payload types using NeoForge's StreamCodec

### Key Files
| File | Purpose |
|------|---------|
| `CobblemonTeamAnalysis.kt` | Main mod entry point |
| `CobblemonHelper.kt` | Cobblemon API wrapper (party/PC access, summaries) |
| `TeamAnalyzer.kt` | Type effectiveness calculations |
| `PresetStorage.kt` | JSON persistence for presets |
| `PCIntegration.kt` | Hooks into Cobblemon PCGUI via ScreenEvent |
| `PokemonBrowserScreen.kt` | Grid browser with search/filter |
| `TeamAnalysisScreen.kt` | Type coverage visualization |

### Design Decisions
- Team swapping only works when PC is open (prevents cheating)
- Missing Pokemon displayed greyed out with clear "MISSING" label
- PC Integration uses NeoForge events (not mixins) for clean hooking
- Singleton pattern for empty payloads (StreamCodec.unit() compatibility)

---

*Document Version: 1.1*
*Last Updated: 2026-01-23*
*Author: Claude Code + Human Design*
