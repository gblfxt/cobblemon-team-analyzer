package com.cobblemon.teamanalysis.client.gui

import com.cobblemon.teamanalysis.analysis.TeamAnalyzer
import com.cobblemon.teamanalysis.analysis.TypeEffectiveness
import com.cobblemon.teamanalysis.client.PresetLoadScreen
import com.cobblemon.teamanalysis.client.PresetSaveScreen
import com.cobblemon.teamanalysis.data.FilterCriteria
import com.cobblemon.teamanalysis.data.PokemonSummary
import com.cobblemon.teamanalysis.data.TeamPreset
import com.cobblemon.teamanalysis.network.PresetActionPayload
import com.cobblemon.teamanalysis.network.RequestPresetsPayload
import com.cobblemon.teamanalysis.opponent.OpponentData
import com.cobblemon.teamanalysis.opponent.OpponentService
import com.cobblemon.teamanalysis.opponent.TeamSuggestionService
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor
import java.util.Optional
import java.util.UUID

/**
 * Team Builder screen with drag-and-drop team composition.
 * Left panel: 6 team slots
 * Right panel: Scrollable Pokemon collection grid
 */
class TeamBuilderScreen : Screen(Component.literal("Team Builder")) {

    companion object {
        // Type colors (ARGB)
        private val TYPE_COLORS = mapOf(
            "normal" to 0xFFA8A878.toInt(),
            "fire" to 0xFFF08030.toInt(),
            "water" to 0xFF6890F0.toInt(),
            "electric" to 0xFFF8D030.toInt(),
            "grass" to 0xFF78C850.toInt(),
            "ice" to 0xFF98D8D8.toInt(),
            "fighting" to 0xFFC03028.toInt(),
            "poison" to 0xFFA040A0.toInt(),
            "ground" to 0xFFE0C068.toInt(),
            "flying" to 0xFFA890F0.toInt(),
            "psychic" to 0xFFF85888.toInt(),
            "bug" to 0xFFA8B820.toInt(),
            "rock" to 0xFFB8A038.toInt(),
            "ghost" to 0xFF705898.toInt(),
            "dragon" to 0xFF7038F8.toInt(),
            "dark" to 0xFF705848.toInt(),
            "steel" to 0xFFB8B8D0.toInt(),
            "fairy" to 0xFFEE99AC.toInt(),
            "unknown" to 0xFF666666.toInt()
        )

        // Layout constants
        private const val TEAM_PANEL_WIDTH = 110
        private const val TEAM_SLOT_WIDTH = 95
        private const val TEAM_SLOT_HEIGHT = 38
        private const val TEAM_SLOT_SPACING = 5

        private const val GRID_COLS = 6
        private const val CELL_WIDTH = 65
        private const val CELL_HEIGHT = 50
        private const val CELL_PADDING = 4

        // Cobblemon-style colors
        private const val COBBLEMON_GOLD = 0xFFB5C42F.toInt()
        private const val COBBLEMON_DARK_BG = 0xFF0a0a0a.toInt()
        private const val PANEL_BG = 0xFF1a1a2e.toInt()
        private const val PANEL_BORDER = 0xFF333333.toInt()
        private const val PANEL_HEADER = 0xFF252538.toInt()
        private const val SLOT_EMPTY = 0xFF2a2a3e.toInt()
        private const val SLOT_FILLED = 0xFF2e3e2e.toInt()
        private const val SLOT_HOVER = 0xFF3a3a5e.toInt()
        private const val CELL_BG = 0xFF2a2a3e.toInt()
        private const val CELL_HOVER = 0xFF3a3a5e.toInt()
        private const val CELL_IN_TEAM = 0xFF2e3e2e.toInt()
    }

    // Drag-and-drop state
    private enum class DragSource { NONE, TEAM_SLOT, COLLECTION }
    private var draggedPokemon: PokemonSummary? = null
    private var dragSourceType: DragSource = DragSource.NONE
    private var dragSourceIndex: Int = -1
    private var dragX: Int = 0
    private var dragY: Int = 0

    // Team state
    private val teamSlots: Array<PokemonSummary?> = arrayOfNulls(6)

    // Collection state
    private var allPokemon: List<PokemonSummary> = emptyList()
    private var filteredPokemon: List<PokemonSummary> = emptyList()
    private var filter = FilterCriteria()
    private var scrollOffset = 0
    private var maxScroll = 0

    // UI components
    private lateinit var searchBox: EditBox
    private var hoveredPokemon: PokemonSummary? = null
    private var hoveredSlotIndex: Int = -1

    // Layout positions (calculated in init)
    private var teamPanelX = 0
    private var teamPanelY = 0
    private var gridX = 0
    private var gridY = 0
    private var gridWidth = 0
    private var gridHeight = 0

    // Preset management
    private var presetNames: List<String> = emptyList()

    // Opponent targeting
    private var selectedOpponent: OpponentData? = null
    private var suggestedPokemon: List<TeamSuggestionService.PokemonScore> = emptyList()

    /**
     * Set the Pokemon collection to display.
     */
    fun setPokemonList(pokemon: List<PokemonSummary>) {
        this.allPokemon = pokemon
        applyFilter()
    }

    /**
     * Set available preset names.
     */
    fun setPresetNames(names: List<String>) {
        this.presetNames = names
    }

    /**
     * Check if a Pokemon is currently in the team.
     */
    fun isInTeam(pokemon: PokemonSummary): Boolean =
        teamSlots.any { it?.uuid == pokemon.uuid }

    /**
     * Create a TeamPreset from the current team composition.
     */
    fun createPresetFromTeam(name: String): TeamPreset {
        val preset = TeamPreset(name)
        teamSlots.filterNotNull().forEach { pokemon ->
            preset.addSlot(TeamPreset.TeamSlot.fromPokemon(
                pokemon.uuid,
                ResourceLocation.parse(pokemon.speciesId)
            ))
        }
        return preset
    }

    private fun applyFilter() {
        filteredPokemon = allPokemon
            .filter { filter.matches(it) }
            .sortedWith(filter.getComparator())
        scrollOffset = 0
        updateMaxScroll()
    }

    private fun updateMaxScroll() {
        val rows = (filteredPokemon.size + GRID_COLS - 1) / GRID_COLS
        val contentHeight = rows * (CELL_HEIGHT + CELL_PADDING)
        maxScroll = maxOf(0, contentHeight - gridHeight + 10)
    }

    override fun init() {
        super.init()

        // Calculate layout
        teamPanelX = 15
        teamPanelY = 40
        gridX = TEAM_PANEL_WIDTH + 35
        gridY = 90  // More space below search/sort buttons
        gridWidth = width - gridX - 20
        gridHeight = height - gridY - 40

        // Search box
        searchBox = EditBox(font, gridX, 32, 180, 18, Component.literal("Search"))
        searchBox.setHint(Component.literal("Search Cobblemon...").withStyle(ChatFormatting.DARK_GRAY))
        searchBox.setResponder { text ->
            filter = filter.copy(searchText = text)
            applyFilter()
        }
        addRenderableWidget(searchBox)

        // Sort buttons below search
        var buttonX = gridX
        val sortY = 54
        addRenderableWidget(Button.builder(Component.literal("Name")) {
            filter = filter.copy(sortBy = FilterCriteria.SortOption.NAME)
            applyFilter()
        }.bounds(buttonX, sortY, 40, 16).tooltip(Tooltip.create(Component.literal("Sort by name"))).build())
        buttonX += 44

        addRenderableWidget(Button.builder(Component.literal("Lvl")) {
            filter = filter.copy(sortBy = FilterCriteria.SortOption.LEVEL, sortAscending = !filter.sortAscending)
            applyFilter()
        }.bounds(buttonX, sortY, 30, 16).tooltip(Tooltip.create(Component.literal("Sort by level"))).build())
        buttonX += 34

        addRenderableWidget(Button.builder(Component.literal("IV")) {
            filter = filter.copy(sortBy = FilterCriteria.SortOption.IV_TOTAL, sortAscending = false)
            applyFilter()
        }.bounds(buttonX, sortY, 25, 16).tooltip(Tooltip.create(Component.literal("Sort by IV total"))).build())

        // Filter buttons (below grid)
        val filterY = gridY + gridHeight + 8
        var filterX = gridX

        addRenderableWidget(Button.builder(Component.literal("Shiny")) {
            filter = filter.copy(showShinyOnly = !filter.showShinyOnly)
            applyFilter()
        }.bounds(filterX, filterY, 45, 16).tooltip(Tooltip.create(Component.literal("Show shiny only"))).build())
        filterX += 50

        addRenderableWidget(Button.builder(Component.literal("Legend")) {
            filter = filter.copy(showLegendaryOnly = !filter.showLegendaryOnly)
            applyFilter()
        }.bounds(filterX, filterY, 50, 16).tooltip(Tooltip.create(Component.literal("Show legendaries only"))).build())
        filterX += 55

        addRenderableWidget(Button.builder(Component.literal("Party")) {
            filter = filter.copy(showPartyOnly = !filter.showPartyOnly, showPCOnly = false)
            applyFilter()
        }.bounds(filterX, filterY, 40, 16).tooltip(Tooltip.create(Component.literal("Show party only"))).build())
        filterX += 45

        addRenderableWidget(Button.builder(Component.literal("PC")) {
            filter = filter.copy(showPCOnly = !filter.showPCOnly, showPartyOnly = false)
            applyFilter()
        }.bounds(filterX, filterY, 30, 16).tooltip(Tooltip.create(Component.literal("Show PC only"))).build())

        // Action buttons (below team panel)
        val actionY = teamPanelY + (6 * (TEAM_SLOT_HEIGHT + TEAM_SLOT_SPACING)) + 15
        val actionButtonWidth = 42

        addRenderableWidget(Button.builder(Component.literal("Analyze")) {
            analyzeTeam()
        }.bounds(teamPanelX, actionY, actionButtonWidth + 10, 18).tooltip(Tooltip.create(Component.literal("Analyze team type coverage"))).build())

        addRenderableWidget(Button.builder(Component.literal("Save")) {
            saveTeam()
        }.bounds(teamPanelX, actionY + 22, actionButtonWidth, 18).tooltip(Tooltip.create(Component.literal("Save team as preset"))).build())

        addRenderableWidget(Button.builder(Component.literal("Load")) {
            loadTeam()
        }.bounds(teamPanelX + actionButtonWidth + 4, actionY + 22, actionButtonWidth, 18).tooltip(Tooltip.create(Component.literal("Load team from preset"))).build())

        addRenderableWidget(Button.builder(Component.literal("Clear")) {
            clearTeam()
        }.bounds(teamPanelX, actionY + 44, TEAM_SLOT_WIDTH, 18).tooltip(Tooltip.create(Component.literal("Clear all team slots"))).build())

        // Opponent selector button (below clear)
        addRenderableWidget(Button.builder(Component.literal("⚔ Target")) {
            openOpponentSelector()
        }.bounds(teamPanelX, actionY + 66, TEAM_SLOT_WIDTH, 18).tooltip(Tooltip.create(Component.literal("Select opponent to counter"))).build())

        // Auto-build button (appears when opponent selected)
        addRenderableWidget(Button.builder(Component.literal("Auto-Build")) {
            autoBuildTeam()
        }.bounds(teamPanelX, actionY + 88, TEAM_SLOT_WIDTH, 18).tooltip(Tooltip.create(Component.literal("Auto-build optimal team against selected opponent"))).build())

        // Close button
        addRenderableWidget(Button.builder(Component.literal("X")) { onClose() }
            .bounds(width - 22, 5, 18, 18)
            .tooltip(Tooltip.create(Component.literal("Close")))
            .build())

        updateMaxScroll()
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)

        // Title bar background
        graphics.fill(0, 0, width, 26, PANEL_HEADER)
        graphics.fill(0, 26, width, 28, PANEL_BORDER)

        // Title
        graphics.drawCenteredString(font, Component.literal("⚔ Team Builder ⚔")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), width / 2, 8, 0xFFFFFF)

        // Team panel with border
        val teamPanelBottom = teamPanelY + 6 * (TEAM_SLOT_HEIGHT + TEAM_SLOT_SPACING)
        graphics.fill(teamPanelX - 8, teamPanelY - 22, teamPanelX + TEAM_SLOT_WIDTH + 8, teamPanelBottom + 8, PANEL_BORDER)
        graphics.fill(teamPanelX - 6, teamPanelY - 20, teamPanelX + TEAM_SLOT_WIDTH + 6, teamPanelBottom + 6, PANEL_BG)

        // Team panel header bar
        graphics.fill(teamPanelX - 6, teamPanelY - 20, teamPanelX + TEAM_SLOT_WIDTH + 6, teamPanelY - 6, PANEL_HEADER)
        graphics.drawCenteredString(font, Component.literal("YOUR TEAM").withStyle(ChatFormatting.GOLD),
            teamPanelX + TEAM_SLOT_WIDTH / 2, teamPanelY - 16, 0xFFFFFF)

        // Render team slots
        hoveredSlotIndex = -1
        for (i in 0 until 6) {
            renderTeamSlot(graphics, i, mouseX, mouseY)
        }

        // Collection panel with border
        graphics.fill(gridX - 6, gridY - 22, gridX + gridWidth + 6, gridY + gridHeight + 6, PANEL_BORDER)
        graphics.fill(gridX - 4, gridY - 20, gridX + gridWidth + 4, gridY + gridHeight + 4, PANEL_BG)

        // Collection header bar
        graphics.fill(gridX - 4, gridY - 20, gridX + gridWidth + 4, gridY - 4, PANEL_HEADER)
        graphics.drawString(font, Component.literal("COLLECTION").withStyle(ChatFormatting.GOLD),
            gridX, gridY - 16, 0xFFFFFF)

        // Collection stats
        val inTeamCount = teamSlots.count { it != null }
        val stats = "$inTeamCount/6 | ${filteredPokemon.size}/${allPokemon.size}"
        graphics.drawString(font, stats, gridX + gridWidth - font.width(stats) - 2, gridY - 16, 0xFFAAAAAA.toInt())

        // Render selected opponent indicator (below team panel)
        val opponentY = teamPanelY + 6 * (TEAM_SLOT_HEIGHT + TEAM_SLOT_SPACING) + 140
        if (selectedOpponent != null) {
            renderOpponentIndicator(graphics, teamPanelX - 6, opponentY, selectedOpponent!!)
        }

        // Grid inner background
        graphics.fill(gridX, gridY, gridX + gridWidth, gridY + gridHeight, 0xFF151520.toInt())

        // Enable scissor for grid scrolling
        graphics.enableScissor(gridX, gridY, gridX + gridWidth, gridY + gridHeight)

        hoveredPokemon = null

        // Render Pokemon grid
        for ((index, pokemon) in filteredPokemon.withIndex()) {
            val col = index % GRID_COLS
            val row = index / GRID_COLS

            val cellX = gridX + col * (CELL_WIDTH + CELL_PADDING)
            val cellY = gridY + row * (CELL_HEIGHT + CELL_PADDING) - scrollOffset

            // Skip if not visible
            if (cellY + CELL_HEIGHT < gridY || cellY > gridY + gridHeight) continue

            renderPokemonCell(graphics, pokemon, cellX, cellY, mouseX, mouseY)
        }

        graphics.disableScissor()

        // Scrollbar
        if (maxScroll > 0) {
            val scrollBarX = gridX + gridWidth + 4
            val scrollBarHeight = gridHeight - 20
            val thumbHeight = maxOf(20, scrollBarHeight * gridHeight / (gridHeight + maxScroll))
            val thumbY = gridY + 10 + ((scrollBarHeight - thumbHeight) * scrollOffset / maxScroll)

            graphics.fill(scrollBarX, gridY + 10, scrollBarX + 6, gridY + gridHeight - 10, 0xFF333333.toInt())
            graphics.fill(scrollBarX + 1, thumbY, scrollBarX + 5, thumbY + thumbHeight, 0xFF666666.toInt())
        }

        // Render widgets (buttons, search box)
        super.render(graphics, mouseX, mouseY, partialTick)

        // Render dragged Pokemon (on top of everything)
        if (draggedPokemon != null) {
            renderDraggedPokemon(graphics, mouseX, mouseY)
        }

        // Tooltip (only when not dragging)
        if (draggedPokemon == null && hoveredPokemon != null) {
            renderPokemonTooltip(graphics, hoveredPokemon!!, mouseX, mouseY)
        }
    }

    private fun renderTeamSlot(graphics: GuiGraphics, index: Int, mouseX: Int, mouseY: Int) {
        val slotX = teamPanelX
        val slotY = teamPanelY + index * (TEAM_SLOT_HEIGHT + TEAM_SLOT_SPACING)

        val pokemon = teamSlots[index]
        val isHovered = mouseX in slotX..(slotX + TEAM_SLOT_WIDTH) && mouseY in slotY..(slotY + TEAM_SLOT_HEIGHT)
        val isDragTarget = draggedPokemon != null && isHovered

        if (isHovered) hoveredSlotIndex = index

        // Push z-level for team slots
        graphics.pose().pushPose()
        graphics.pose().translate(0.0, 0.0, 10.0)

        // Slot background with subtle gradient effect
        val bgColor = when {
            isDragTarget -> COBBLEMON_GOLD
            isHovered -> SLOT_HOVER
            pokemon != null -> SLOT_FILLED
            else -> SLOT_EMPTY
        }
        graphics.fill(slotX, slotY, slotX + TEAM_SLOT_WIDTH, slotY + TEAM_SLOT_HEIGHT, bgColor)

        // Border - gold for drag target, highlight for hover
        val borderColor = when {
            isDragTarget -> COBBLEMON_GOLD
            isHovered -> 0xFFBBBBBB.toInt()
            pokemon != null -> 0xFF4a6a4a.toInt()
            else -> 0xFF404050.toInt()
        }
        graphics.renderOutline(slotX, slotY, TEAM_SLOT_WIDTH, TEAM_SLOT_HEIGHT, borderColor)

        // Slot number badge
        val slotNumBg = if (pokemon != null) 0xFF3a5a3a.toInt() else 0xFF3a3a4a.toInt()
        graphics.fill(slotX + 2, slotY + 2, slotX + 12, slotY + 12, slotNumBg)
        graphics.drawString(font, "${index + 1}", slotX + 4, slotY + 3, 0xFFCCCCCC.toInt(), false)

        if (pokemon != null && !(dragSourceType == DragSource.TEAM_SLOT && dragSourceIndex == index)) {
            // Pokemon name
            val name = if (pokemon.displayName.length > 9) pokemon.displayName.take(8) + ".." else pokemon.displayName
            graphics.drawString(font, name, slotX + 14, slotY + 3, 0xFFFFFFFF.toInt(), false)

            // Level with colored background
            val lvlText = "Lv${pokemon.level}"
            graphics.drawString(font, lvlText, slotX + 14, slotY + 14, 0xFFBBBBBB.toInt(), false)

            // Type badges
            var badgeX = slotX + 3
            val badgeY = slotY + TEAM_SLOT_HEIGHT - 13
            badgeX = renderTypeBadgeMini(graphics, pokemon.type1, badgeX, badgeY)
            if (pokemon.type2 != null) {
                renderTypeBadgeMini(graphics, pokemon.type2, badgeX + 2, badgeY)
            }

            // Shiny star indicator
            if (pokemon.isShiny) {
                graphics.drawString(font, "★", slotX + TEAM_SLOT_WIDTH - 12, slotY + 3, 0xFFFFD700.toInt(), false)
            }
        } else if (pokemon == null) {
            // Empty slot indicator with dashed look
            graphics.drawCenteredString(font, Component.literal("Empty").withStyle(ChatFormatting.DARK_GRAY),
                slotX + TEAM_SLOT_WIDTH / 2, slotY + TEAM_SLOT_HEIGHT / 2 - 4, 0xFFFFFF)
        }

        graphics.pose().popPose()
    }

    private fun renderPokemonCell(graphics: GuiGraphics, pokemon: PokemonSummary, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val isHovered = mouseX in x..(x + CELL_WIDTH) && mouseY in y..(y + CELL_HEIGHT)
        val isBeingDragged = draggedPokemon?.uuid == pokemon.uuid
        val inTeam = isInTeam(pokemon)
        val isSuggested = selectedOpponent != null && isSuggested(pokemon)
        val suggestionScore = if (isSuggested) getSuggestionScore(pokemon) else 0.0

        if (isHovered && !isBeingDragged) hoveredPokemon = pokemon

        // Push z-level for cells to render above grid background
        graphics.pose().pushPose()
        graphics.pose().translate(0.0, 0.0, 10.0)

        // Skip rendering if being dragged
        if (isBeingDragged) {
            // Show placeholder with dashed border effect
            graphics.fill(x, y, x + CELL_WIDTH, y + CELL_HEIGHT, 0xFF1a1a1a.toInt())
            graphics.renderOutline(x, y, CELL_WIDTH, CELL_HEIGHT, 0xFF444444.toInt())
            graphics.pose().popPose()
            return
        }

        // Background with Cobblemon-style colors - highlight suggested Pokemon
        val bgColor = when {
            pokemon.isMissing -> 0xFF3a3a3a.toInt()
            inTeam -> CELL_IN_TEAM
            isSuggested && suggestionScore >= 4.0 -> 0xFF3a5a3a.toInt()  // Strong suggestion - green
            isSuggested && suggestionScore >= 2.0 -> 0xFF4a5a3a.toInt()  // Good suggestion - yellow-green
            isSuggested -> 0xFF4a4a3a.toInt()  // Decent suggestion - yellow tint
            isHovered -> CELL_HOVER
            else -> CELL_BG
        }
        graphics.fill(x, y, x + CELL_WIDTH, y + CELL_HEIGHT, bgColor)

        // Border - subtle but visible
        val borderColor = when {
            inTeam -> 0xFF5a8a5a.toInt()
            pokemon.isMissing -> 0xFF555555.toInt()
            isHovered -> 0xFF6a6a8a.toInt()
            else -> 0xFF404050.toInt()
        }
        graphics.renderOutline(x, y, CELL_WIDTH, CELL_HEIGHT, borderColor)

        // Text color
        val textColor = if (pokemon.isMissing) 0xFF777777.toInt() else 0xFFFFFFFF.toInt()

        // Pokemon name
        val name = if (pokemon.displayName.length > 8) pokemon.displayName.take(7) + ".." else pokemon.displayName
        graphics.drawString(font, name, x + 3, y + 3, textColor, false)

        // Level badge
        if (!pokemon.isMissing) {
            val lvlText = "Lv${pokemon.level}"
            graphics.drawString(font, lvlText, x + 3, y + 14, 0xFF999999.toInt(), false)
        }

        // Type badges at bottom
        var badgeX = x + 3
        val badgeY = y + CELL_HEIGHT - 12
        badgeX = renderTypeBadgeMini(graphics, pokemon.type1, badgeX, badgeY, pokemon.isMissing)
        if (pokemon.type2 != null) {
            renderTypeBadgeMini(graphics, pokemon.type2, badgeX + 2, badgeY, pokemon.isMissing)
        }

        // Shiny star indicator (top right)
        if (pokemon.isShiny && !pokemon.isMissing) {
            graphics.drawString(font, "★", x + CELL_WIDTH - 10, y + 3, 0xFFFFD700.toInt(), false)
        }

        // In-team checkmark indicator
        if (inTeam) {
            graphics.fill(x + CELL_WIDTH - 11, y + CELL_HEIGHT - 11, x + CELL_WIDTH - 2, y + CELL_HEIGHT - 2, 0xFF3a6a3a.toInt())
            graphics.drawString(font, "✓", x + CELL_WIDTH - 10, y + CELL_HEIGHT - 11, 0xFF88FF88.toInt(), false)
        }

        // Suggestion indicator (when opponent selected and not in team)
        if (isSuggested && !inTeam) {
            val indicatorColor = when {
                suggestionScore >= 4.0 -> 0xFF44FF44.toInt()  // Strong - bright green
                suggestionScore >= 2.0 -> 0xFFAAFF44.toInt()  // Good - yellow-green
                else -> 0xFFFFFF44.toInt()  // Decent - yellow
            }
            // Small corner triangle indicator
            graphics.fill(x + CELL_WIDTH - 8, y, x + CELL_WIDTH, y, indicatorColor)
            graphics.fill(x + CELL_WIDTH - 7, y, x + CELL_WIDTH, y + 1, indicatorColor)
            graphics.fill(x + CELL_WIDTH - 6, y + 1, x + CELL_WIDTH, y + 2, indicatorColor)
            graphics.fill(x + CELL_WIDTH - 5, y + 2, x + CELL_WIDTH, y + 3, indicatorColor)
            graphics.fill(x + CELL_WIDTH - 4, y + 3, x + CELL_WIDTH, y + 4, indicatorColor)
            graphics.fill(x + CELL_WIDTH - 3, y + 4, x + CELL_WIDTH, y + 5, indicatorColor)
            graphics.fill(x + CELL_WIDTH - 2, y + 5, x + CELL_WIDTH, y + 6, indicatorColor)
            graphics.fill(x + CELL_WIDTH - 1, y + 6, x + CELL_WIDTH, y + 7, indicatorColor)
        }

        graphics.pose().popPose()
    }

    private fun renderTypeBadgeMini(graphics: GuiGraphics, type: String, x: Int, y: Int, greyed: Boolean = false): Int {
        val baseColor = TYPE_COLORS[type.lowercase()] ?: 0xFF888888.toInt()
        val color = if (greyed) {
            val r = ((baseColor shr 16) and 0xFF) / 2 + 64
            val g = ((baseColor shr 8) and 0xFF) / 2 + 64
            val b = (baseColor and 0xFF) / 2 + 64
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        } else baseColor

        val label = type.take(3).uppercase()
        val labelWidth = font.width(label) + 2

        graphics.fill(x, y, x + labelWidth, y + 9, color)
        graphics.drawString(font, label, x + 1, y + 1, if (greyed) 0xFFAAAAAA.toInt() else 0xFFFFFFFF.toInt(), false)

        return x + labelWidth
    }

    private fun renderDraggedPokemon(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val pokemon = draggedPokemon ?: return

        // Push to front
        graphics.pose().pushPose()
        graphics.pose().translate(0.0, 0.0, 200.0)

        val dragWidth = 60
        val dragHeight = 40
        val x = mouseX - dragWidth / 2
        val y = mouseY - dragHeight / 2

        // Shadow
        graphics.fill(x + 3, y + 3, x + dragWidth + 3, y + dragHeight + 3, 0x88000000.toInt())

        // Background with gold border
        graphics.fill(x - 1, y - 1, x + dragWidth + 1, y + dragHeight + 1, COBBLEMON_GOLD)
        graphics.fill(x, y, x + dragWidth, y + dragHeight, PANEL_BG)

        // Pokemon name
        val name = if (pokemon.displayName.length > 8) pokemon.displayName.take(7) + ".." else pokemon.displayName
        graphics.drawCenteredString(font, name, x + dragWidth / 2, y + 4, 0xFFFFFFFF.toInt())

        // Level
        graphics.drawCenteredString(font, "Lv${pokemon.level}", x + dragWidth / 2, y + 15, 0xFFBBBBBB.toInt())

        // Type badges centered
        val type1Width = font.width(pokemon.type1.take(3).uppercase()) + 2
        val type2Width = if (pokemon.type2 != null) font.width(pokemon.type2.take(3).uppercase()) + 2 else 0
        val totalWidth = type1Width + (if (pokemon.type2 != null) type2Width + 2 else 0)
        var badgeX = x + (dragWidth - totalWidth) / 2
        badgeX = renderTypeBadgeMini(graphics, pokemon.type1, badgeX, y + dragHeight - 12)
        if (pokemon.type2 != null) {
            renderTypeBadgeMini(graphics, pokemon.type2, badgeX + 2, y + dragHeight - 12)
        }

        graphics.pose().popPose()
    }

    private fun renderPokemonTooltip(graphics: GuiGraphics, pokemon: PokemonSummary, mouseX: Int, mouseY: Int) {
        val lines = mutableListOf<Component>()

        if (pokemon.isMissing) {
            lines.add(Component.literal(pokemon.displayName).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
            lines.add(Component.literal("Pokemon is missing!").withStyle(ChatFormatting.GRAY))
        } else {
            lines.add(Component.literal(pokemon.displayName).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
            lines.add(Component.literal("Level ${pokemon.level}").withStyle(ChatFormatting.YELLOW))

            val typeStr = if (pokemon.type2 != null) "${pokemon.type1}/${pokemon.type2}" else pokemon.type1
            lines.add(Component.literal("Type: $typeStr").withStyle(ChatFormatting.GRAY))

            lines.add(Component.literal(""))
            lines.add(Component.literal("Nature: ${pokemon.nature}").withStyle(ChatFormatting.GRAY))
            lines.add(Component.literal("Ability: ${pokemon.ability}").withStyle(ChatFormatting.GRAY))

            lines.add(Component.literal(""))
            lines.add(Component.literal("IVs: ${pokemon.ivTotal}/186").withStyle(ChatFormatting.AQUA))

            if (pokemon.isShiny) {
                lines.add(Component.literal(""))
                lines.add(Component.literal("* Shiny!").withStyle(ChatFormatting.GOLD))
            }

            if (pokemon.isLegendary) {
                lines.add(Component.literal("* Legendary").withStyle(ChatFormatting.LIGHT_PURPLE))
            }

            if (isInTeam(pokemon)) {
                lines.add(Component.literal(""))
                lines.add(Component.literal("[In Team]").withStyle(ChatFormatting.GREEN))
            }

            lines.add(Component.literal(""))
            lines.add(Component.literal("Click to add to team").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
        }

        graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)

        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        // Check if clicked on team slot
        for (i in 0 until 6) {
            val slotX = teamPanelX
            val slotY = teamPanelY + i * (TEAM_SLOT_HEIGHT + TEAM_SLOT_SPACING)

            if (mx in slotX..(slotX + TEAM_SLOT_WIDTH) && my in slotY..(slotY + TEAM_SLOT_HEIGHT)) {
                val pokemon = teamSlots[i]
                if (pokemon != null) {
                    // Start dragging from team slot
                    draggedPokemon = pokemon
                    dragSourceType = DragSource.TEAM_SLOT
                    dragSourceIndex = i
                    return true
                }
            }
        }

        // Check if clicked on collection grid
        if (mx in gridX..(gridX + gridWidth) && my in gridY..(gridY + gridHeight)) {
            val pokemon = getPokemonAtPosition(mx, my)
            if (pokemon != null && !pokemon.isMissing && !isInTeam(pokemon)) {
                // Start dragging from collection
                draggedPokemon = pokemon
                dragSourceType = DragSource.COLLECTION
                dragSourceIndex = filteredPokemon.indexOf(pokemon)
                return true
            } else if (pokemon != null && !pokemon.isMissing && isInTeam(pokemon)) {
                // Clicked on Pokemon already in team - remove it
                for (i in 0 until 6) {
                    if (teamSlots[i]?.uuid == pokemon.uuid) {
                        teamSlots[i] = null
                        break
                    }
                }
                return true
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0 || draggedPokemon == null) return super.mouseReleased(mouseX, mouseY, button)

        val mx = mouseX.toInt()
        val my = mouseY.toInt()
        val pokemon = draggedPokemon!!

        // Check if released on team slot
        var droppedOnSlot = false
        for (i in 0 until 6) {
            val slotX = teamPanelX
            val slotY = teamPanelY + i * (TEAM_SLOT_HEIGHT + TEAM_SLOT_SPACING)

            if (mx in slotX..(slotX + TEAM_SLOT_WIDTH) && my in slotY..(slotY + TEAM_SLOT_HEIGHT)) {
                // Drop on this slot
                if (dragSourceType == DragSource.TEAM_SLOT) {
                    // Swap slots
                    val existing = teamSlots[i]
                    teamSlots[i] = pokemon
                    teamSlots[dragSourceIndex] = existing
                } else {
                    // Add from collection
                    if (teamSlots[i] != null) {
                        // Slot occupied - swap or find empty slot
                        val emptySlot = teamSlots.indexOfFirst { it == null }
                        if (emptySlot >= 0) {
                            teamSlots[emptySlot] = teamSlots[i]
                        }
                    }
                    teamSlots[i] = pokemon
                }
                droppedOnSlot = true
                break
            }
        }

        // If dragged from team and dropped outside team slots - remove from team
        if (!droppedOnSlot && dragSourceType == DragSource.TEAM_SLOT) {
            teamSlots[dragSourceIndex] = null
        }

        // If dragged from collection and dropped outside team slots - find first empty slot
        if (!droppedOnSlot && dragSourceType == DragSource.COLLECTION) {
            val emptySlot = teamSlots.indexOfFirst { it == null }
            if (emptySlot >= 0) {
                teamSlots[emptySlot] = pokemon
            }
        }

        // Reset drag state
        draggedPokemon = null
        dragSourceType = DragSource.NONE
        dragSourceIndex = -1

        return true
    }

    private fun getPokemonAtPosition(mouseX: Int, mouseY: Int): PokemonSummary? {
        for ((index, pokemon) in filteredPokemon.withIndex()) {
            val col = index % GRID_COLS
            val row = index / GRID_COLS

            val cellX = gridX + col * (CELL_WIDTH + CELL_PADDING)
            val cellY = gridY + row * (CELL_HEIGHT + CELL_PADDING) - scrollOffset

            if (mouseX in cellX..(cellX + CELL_WIDTH) && mouseY in cellY..(cellY + CELL_HEIGHT)) {
                return pokemon
            }
        }
        return null
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (mouseX.toInt() in gridX..(gridX + gridWidth + 10)) {
            scrollOffset = (scrollOffset - (scrollY * 20).toInt()).coerceIn(0, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (searchBox.isFocused) {
            return super.keyPressed(keyCode, scanCode, modifiers)
        }
        if (keyCode == 256) { // Escape
            if (draggedPokemon != null) {
                // Cancel drag
                draggedPokemon = null
                dragSourceType = DragSource.NONE
                dragSourceIndex = -1
                return true
            }
            onClose()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, COBBLEMON_DARK_BG)
    }

    override fun isPauseScreen(): Boolean = false

    // Action handlers

    private fun analyzeTeam() {
        val filledSlots = teamSlots.filterNotNull()
        if (filledSlots.isEmpty()) {
            minecraft?.player?.sendSystemMessage(Component.literal("No Pokemon in team to analyze!")
                .withStyle(ChatFormatting.RED))
            return
        }

        // Perform local analysis
        val speciesIds = filledSlots.map { ResourceLocation.parse(it.speciesId) }
        val analysis = TeamAnalyzer.analyze(speciesIds)

        // Open analysis screen
        val analysisScreen = TeamAnalysisScreen()
        analysisScreen.setAnalysis(analysis, "Builder Team")
        analysisScreen.setPresetNames(presetNames)
        minecraft?.setScreen(analysisScreen)
    }

    private fun saveTeam() {
        val filledSlots = teamSlots.filterNotNull()
        if (filledSlots.isEmpty()) {
            minecraft?.player?.sendSystemMessage(Component.literal("No Pokemon in team to save!")
                .withStyle(ChatFormatting.RED))
            return
        }

        // Open save dialog
        minecraft?.setScreen(TeamBuilderSaveScreen(this))
    }

    private fun loadTeam() {
        // Request preset list and open load dialog
        PacketDistributor.sendToServer(RequestPresetsPayload)
        minecraft?.setScreen(TeamBuilderLoadScreen(this))
    }

    private fun clearTeam() {
        for (i in 0 until 6) {
            teamSlots[i] = null
        }
    }

    /**
     * Load a preset into the team slots.
     */
    fun loadPresetToSlots(presetName: String) {
        // Find matching Pokemon from collection by UUID
        PacketDistributor.sendToServer(PresetActionPayload("load_to_builder", presetName))
    }

    /**
     * Set team slots from Pokemon UUIDs (called when loading preset).
     */
    fun setTeamFromUUIDs(uuids: List<UUID>) {
        clearTeam()
        for ((index, uuid) in uuids.take(6).withIndex()) {
            val pokemon = allPokemon.find { it.uuid == uuid }
            if (pokemon != null) {
                teamSlots[index] = pokemon
            }
        }
    }

    /**
     * Get current team as list of PokemonSummary.
     */
    fun getTeam(): List<PokemonSummary> = teamSlots.filterNotNull()

    /**
     * Load preset Pokemon into team slots.
     * Called when server sends preset data.
     */
    fun loadPresetPokemon(pokemon: List<PokemonSummary>) {
        clearTeam()
        for ((index, poke) in pokemon.take(6).withIndex()) {
            // Only add if not missing
            if (!poke.isMissing) {
                teamSlots[index] = poke
            }
        }
    }

    // Opponent Selection

    private fun openOpponentSelector() {
        minecraft?.setScreen(OpponentSelectorScreen(this))
    }

    /**
     * Set the selected opponent and update suggestions.
     */
    fun setSelectedOpponent(opponent: OpponentData?) {
        this.selectedOpponent = opponent
        updateSuggestions()
    }

    /**
     * Get the currently selected opponent.
     */
    fun getSelectedOpponent(): OpponentData? = selectedOpponent

    private fun updateSuggestions() {
        if (selectedOpponent == null || allPokemon.isEmpty()) {
            suggestedPokemon = emptyList()
            return
        }

        val currentTeamUuids = teamSlots.filterNotNull().map { it.uuid }.toSet()
        suggestedPokemon = TeamSuggestionService.getSuggestedPokemon(
            allPokemon,
            selectedOpponent!!,
            currentTeamUuids,
            12
        )
    }

    private fun autoBuildTeam() {
        if (selectedOpponent == null) {
            minecraft?.player?.sendSystemMessage(Component.literal("Select an opponent first!")
                .withStyle(ChatFormatting.YELLOW))
            return
        }

        if (allPokemon.isEmpty()) {
            minecraft?.player?.sendSystemMessage(Component.literal("No Pokemon available!")
                .withStyle(ChatFormatting.RED))
            return
        }

        // Build optimal team
        val optimalTeam = TeamSuggestionService.buildOptimalTeam(
            allPokemon,
            selectedOpponent!!,
            6
        )

        if (optimalTeam.isEmpty()) {
            minecraft?.player?.sendSystemMessage(Component.literal("Could not find suitable Pokemon!")
                .withStyle(ChatFormatting.RED))
            return
        }

        // Fill team slots
        clearTeam()
        for ((index, pokemon) in optimalTeam.withIndex()) {
            teamSlots[index] = pokemon
        }

        minecraft?.player?.sendSystemMessage(Component.literal("Built team of ${optimalTeam.size} Pokemon against ${selectedOpponent!!.displayName}")
            .withStyle(ChatFormatting.GREEN))
    }

    private fun renderOpponentIndicator(graphics: GuiGraphics, x: Int, y: Int, opponent: OpponentData) {
        val indicatorWidth = TEAM_SLOT_WIDTH + 12
        val indicatorHeight = 50

        // Background
        graphics.fill(x, y, x + indicatorWidth, y + indicatorHeight, PANEL_BORDER)
        graphics.fill(x + 1, y + 1, x + indicatorWidth - 1, y + indicatorHeight - 1, PANEL_BG)

        // Header
        graphics.fill(x + 1, y + 1, x + indicatorWidth - 1, y + 14, PANEL_HEADER)
        graphics.drawString(font, "TARGET", x + 4, y + 3, COBBLEMON_GOLD, false)

        // Clear button
        val clearX = x + indicatorWidth - 12
        graphics.fill(clearX, y + 2, clearX + 10, y + 12, 0xFF553333.toInt())
        graphics.drawString(font, "x", clearX + 2, y + 2, 0xFFFF6666.toInt(), false)

        // Opponent name (truncated)
        val name = if (opponent.displayName.length > 12) opponent.displayName.take(11) + ".." else opponent.displayName
        graphics.drawString(font, name, x + 4, y + 17, 0xFFFFFFFF.toInt(), false)

        // Type badges
        var badgeX = x + 4
        val badgeY = y + 30
        for (type in opponent.types.take(2)) {
            badgeX = renderTypeBadgeMini(graphics, type, badgeX, badgeY) + 2
        }

        // Counter types hint
        val counterTypes = TeamSuggestionService.getBestCounterTypes(opponent.types)
        if (counterTypes.isNotEmpty()) {
            val counterText = "Use: ${counterTypes.take(3).joinToString("/") { TypeEffectiveness.formatType(it).take(3) }}"
            graphics.drawString(font, counterText, x + 4, y + 42, 0xFF88FF88.toInt(), false)
        }
    }

    /**
     * Check if a Pokemon is suggested for the current opponent.
     */
    fun isSuggested(pokemon: PokemonSummary): Boolean {
        return suggestedPokemon.any { it.pokemon.uuid == pokemon.uuid }
    }

    /**
     * Get suggestion score for a Pokemon (for highlighting).
     */
    fun getSuggestionScore(pokemon: PokemonSummary): Double {
        return suggestedPokemon.find { it.pokemon.uuid == pokemon.uuid }?.totalScore ?: 0.0
    }
}

/**
 * Save dialog for Team Builder.
 */
class TeamBuilderSaveScreen(private val parentScreen: TeamBuilderScreen) : Screen(Component.literal("Save Team")) {

    private lateinit var nameBox: EditBox
    private lateinit var saveButton: Button

    override fun init() {
        super.init()

        val dialogWidth = 200
        val dialogHeight = 80
        val dialogX = (width - dialogWidth) / 2
        val dialogY = (height - dialogHeight) / 2

        nameBox = EditBox(font, dialogX + 10, dialogY + 25, dialogWidth - 20, 18, Component.literal("Team Name"))
        nameBox.setMaxLength(32)
        nameBox.setHint(Component.literal("Enter team name...").withStyle(ChatFormatting.DARK_GRAY))
        nameBox.setResponder { updateSaveButton() }
        addRenderableWidget(nameBox)
        setInitialFocus(nameBox)

        saveButton = Button.builder(Component.literal("Save")) {
            val name = nameBox.value.trim()
            if (name.isNotEmpty()) {
                val preset = parentScreen.createPresetFromTeam(name)
                PacketDistributor.sendToServer(PresetActionPayload("save", name))
                minecraft?.setScreen(parentScreen)
            }
        }.bounds(dialogX + 10, dialogY + 50, 85, 20).build()
        addRenderableWidget(saveButton)

        addRenderableWidget(Button.builder(Component.literal("Cancel")) {
            minecraft?.setScreen(parentScreen)
        }.bounds(dialogX + dialogWidth - 95, dialogY + 50, 85, 20).build())

        updateSaveButton()
    }

    private fun updateSaveButton() {
        saveButton.active = nameBox.value.trim().isNotEmpty()
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)

        val dialogWidth = 200
        val dialogHeight = 80
        val dialogX = (width - dialogWidth) / 2
        val dialogY = (height - dialogHeight) / 2

        // Dialog background
        graphics.fill(dialogX - 2, dialogY - 2, dialogX + dialogWidth + 2, dialogY + dialogHeight + 2, 0xFF333333.toInt())
        graphics.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, 0xFF1a1a2e.toInt())

        // Title
        graphics.drawCenteredString(font, Component.literal("Save Team").withStyle(ChatFormatting.GOLD),
            width / 2, dialogY + 8, 0xFFFFFF)

        super.render(graphics, mouseX, mouseY, partialTick)
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, 0xAA000000.toInt())
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == 256) { // Escape
            minecraft?.setScreen(parentScreen)
            return true
        }
        if (keyCode == 257 && saveButton.active) { // Enter
            saveButton.onPress()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun isPauseScreen(): Boolean = false
}

/**
 * Load dialog for Team Builder.
 */
class TeamBuilderLoadScreen(private val parentScreen: TeamBuilderScreen) : Screen(Component.literal("Load Team")) {

    private var presetNames: List<String> = emptyList()
    private var selectedIndex: Int = -1
    private var scrollOffset: Int = 0
    private val visibleItems = 6
    private lateinit var loadButton: Button

    fun setPresetNames(names: List<String>) {
        this.presetNames = names
        selectedIndex = if (names.isNotEmpty()) 0 else -1
    }

    override fun init() {
        super.init()

        val dialogWidth = 220
        val dialogHeight = 180
        val dialogX = (width - dialogWidth) / 2
        val dialogY = (height - dialogHeight) / 2

        loadButton = Button.builder(Component.literal("Load")) {
            if (selectedIndex in presetNames.indices) {
                val presetName = presetNames[selectedIndex]
                parentScreen.loadPresetToSlots(presetName)
                minecraft?.setScreen(parentScreen)
            }
        }.bounds(dialogX + 10, dialogY + dialogHeight - 28, 60, 20).build()
        loadButton.active = false
        addRenderableWidget(loadButton)

        addRenderableWidget(Button.builder(Component.literal("Cancel")) {
            minecraft?.setScreen(parentScreen)
        }.bounds(dialogX + dialogWidth - 70, dialogY + dialogHeight - 28, 60, 20).build())

        // Request preset list from server
        PacketDistributor.sendToServer(RequestPresetsPayload)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)

        val dialogWidth = 220
        val dialogHeight = 180
        val dialogX = (width - dialogWidth) / 2
        val dialogY = (height - dialogHeight) / 2

        // Dialog background
        graphics.fill(dialogX - 2, dialogY - 2, dialogX + dialogWidth + 2, dialogY + dialogHeight + 2, 0xFF333333.toInt())
        graphics.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, 0xFF1a1a2e.toInt())

        // Title
        graphics.drawCenteredString(font, Component.literal("Load Team").withStyle(ChatFormatting.GOLD),
            width / 2, dialogY + 8, 0xFFFFFF)

        // Preset list
        val listX = dialogX + 10
        val listY = dialogY + 25
        val listWidth = dialogWidth - 20
        val itemHeight = 20

        graphics.fill(listX, listY, listX + listWidth, listY + visibleItems * itemHeight, 0xFF222222.toInt())

        if (presetNames.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("No saved teams").withStyle(ChatFormatting.GRAY),
                dialogX + dialogWidth / 2, listY + 50, 0xFFFFFF)
        } else {
            for (i in 0 until visibleItems) {
                val index = scrollOffset + i
                if (index >= presetNames.size) break

                val itemY = listY + i * itemHeight
                val isSelected = index == selectedIndex
                val isHovered = mouseX in listX..(listX + listWidth) && mouseY in itemY..(itemY + itemHeight)

                // Background
                val bgColor = when {
                    isSelected -> 0xFF4a5568.toInt()
                    isHovered -> 0xFF3a3a4e.toInt()
                    else -> 0xFF2a2a3e.toInt()
                }
                graphics.fill(listX, itemY, listX + listWidth, itemY + itemHeight, bgColor)

                // Name
                graphics.drawString(font, presetNames[index], listX + 5, itemY + 6, 0xFFFFFFFF.toInt())
            }

            // Scrollbar if needed
            if (presetNames.size > visibleItems) {
                val scrollBarX = listX + listWidth - 6
                val scrollBarHeight = visibleItems * itemHeight - 10
                val thumbHeight = maxOf(20, scrollBarHeight * visibleItems / presetNames.size)
                val maxScrollOffset = presetNames.size - visibleItems
                val thumbY = listY + 5 + ((scrollBarHeight - thumbHeight) * scrollOffset / maxScrollOffset)

                graphics.fill(scrollBarX, listY + 5, scrollBarX + 4, listY + visibleItems * itemHeight - 5, 0xFF333333.toInt())
                graphics.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight, 0xFF666666.toInt())
            }
        }

        loadButton.active = selectedIndex in presetNames.indices

        super.render(graphics, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val dialogWidth = 220
        val dialogHeight = 180
        val dialogX = (width - dialogWidth) / 2
        val dialogY = (height - dialogHeight) / 2

        val listX = dialogX + 10
        val listY = dialogY + 25
        val listWidth = dialogWidth - 20
        val itemHeight = 20

        if (mouseX.toInt() in listX..(listX + listWidth) && mouseY.toInt() in listY..(listY + visibleItems * itemHeight)) {
            val clickedIndex = scrollOffset + ((mouseY.toInt() - listY) / itemHeight)
            if (clickedIndex in presetNames.indices) {
                if (selectedIndex == clickedIndex) {
                    // Double click - load
                    loadButton.onPress()
                } else {
                    selectedIndex = clickedIndex
                }
                return true
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val maxScrollOffset = maxOf(0, presetNames.size - visibleItems)
        scrollOffset = (scrollOffset - scrollY.toInt()).coerceIn(0, maxScrollOffset)
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        when (keyCode) {
            256 -> { // Escape
                minecraft?.setScreen(parentScreen)
                return true
            }
            257 -> { // Enter
                if (loadButton.active) {
                    loadButton.onPress()
                    return true
                }
            }
            265 -> { // Up arrow
                if (selectedIndex > 0) {
                    selectedIndex--
                    if (selectedIndex < scrollOffset) scrollOffset = selectedIndex
                }
                return true
            }
            264 -> { // Down arrow
                if (selectedIndex < presetNames.size - 1) {
                    selectedIndex++
                    if (selectedIndex >= scrollOffset + visibleItems) scrollOffset = selectedIndex - visibleItems + 1
                }
                return true
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, 0xAA000000.toInt())
    }

    override fun isPauseScreen(): Boolean = false
}

/**
 * Opponent selector dialog for Team Builder.
 * Shows type gyms, dual-type elites, and RCT trainers (if available).
 */
class OpponentSelectorScreen(private val parentScreen: TeamBuilderScreen) : Screen(Component.literal("Select Opponent")) {

    companion object {
        private val TYPE_COLORS = mapOf(
            "normal" to 0xFFA8A878.toInt(),
            "fire" to 0xFFF08030.toInt(),
            "water" to 0xFF6890F0.toInt(),
            "electric" to 0xFFF8D030.toInt(),
            "grass" to 0xFF78C850.toInt(),
            "ice" to 0xFF98D8D8.toInt(),
            "fighting" to 0xFFC03028.toInt(),
            "poison" to 0xFFA040A0.toInt(),
            "ground" to 0xFFE0C068.toInt(),
            "flying" to 0xFFA890F0.toInt(),
            "psychic" to 0xFFF85888.toInt(),
            "bug" to 0xFFA8B820.toInt(),
            "rock" to 0xFFB8A038.toInt(),
            "ghost" to 0xFF705898.toInt(),
            "dragon" to 0xFF7038F8.toInt(),
            "dark" to 0xFF705848.toInt(),
            "steel" to 0xFFB8B8D0.toInt(),
            "fairy" to 0xFFEE99AC.toInt()
        )
    }

    private enum class Tab { TYPE_GYMS, DUAL_TYPES, RCT_TRAINERS }

    private var currentTab: Tab = Tab.TYPE_GYMS
    private var opponents: List<OpponentData> = emptyList()
    private var filteredOpponents: List<OpponentData> = emptyList()
    private var selectedIndex: Int = -1
    private var scrollOffset: Int = 0
    private val visibleItems = 8

    private lateinit var searchBox: EditBox
    private lateinit var selectButton: Button

    override fun init() {
        super.init()

        val dialogWidth = 300
        val dialogHeight = 260
        val dialogX = (width - dialogWidth) / 2
        val dialogY = (height - dialogHeight) / 2

        // Tab buttons
        val tabY = dialogY + 22
        addRenderableWidget(Button.builder(Component.literal("Type Gyms")) {
            currentTab = Tab.TYPE_GYMS
            loadOpponents()
        }.bounds(dialogX + 10, tabY, 75, 16).build())

        addRenderableWidget(Button.builder(Component.literal("Dual Types")) {
            currentTab = Tab.DUAL_TYPES
            loadOpponents()
        }.bounds(dialogX + 90, tabY, 75, 16).build())

        val rctAvailable = OpponentService.hasRCTTrainers()
        val rctButton = Button.builder(Component.literal("RCT Trainers")) {
            if (rctAvailable) {
                currentTab = Tab.RCT_TRAINERS
                loadOpponents()
            }
        }.bounds(dialogX + 170, tabY, 80, 16).build()
        rctButton.active = rctAvailable
        addRenderableWidget(rctButton)

        // Search box
        searchBox = EditBox(font, dialogX + 10, tabY + 22, dialogWidth - 20, 16, Component.literal("Search"))
        searchBox.setHint(Component.literal("Search...").withStyle(ChatFormatting.DARK_GRAY))
        searchBox.setResponder { filterOpponents() }
        addRenderableWidget(searchBox)

        // Select button
        selectButton = Button.builder(Component.literal("Select")) {
            if (selectedIndex in filteredOpponents.indices) {
                parentScreen.setSelectedOpponent(filteredOpponents[selectedIndex])
                minecraft?.setScreen(parentScreen)
            }
        }.bounds(dialogX + 10, dialogY + dialogHeight - 28, 80, 20).build()
        selectButton.active = false
        addRenderableWidget(selectButton)

        // Clear button
        addRenderableWidget(Button.builder(Component.literal("Clear")) {
            parentScreen.setSelectedOpponent(null)
            minecraft?.setScreen(parentScreen)
        }.bounds(dialogX + 100, dialogY + dialogHeight - 28, 60, 20).build())

        // Cancel button
        addRenderableWidget(Button.builder(Component.literal("Cancel")) {
            minecraft?.setScreen(parentScreen)
        }.bounds(dialogX + dialogWidth - 70, dialogY + dialogHeight - 28, 60, 20).build())

        // Pre-select current opponent if any
        val currentOpponent = parentScreen.getSelectedOpponent()
        if (currentOpponent != null) {
            currentTab = when (currentOpponent.source) {
                OpponentData.OpponentSource.RCT_TRAINER -> Tab.RCT_TRAINERS
                OpponentData.OpponentSource.TYPE_CHALLENGE ->
                    if (currentOpponent.types.size > 1) Tab.DUAL_TYPES else Tab.TYPE_GYMS
            }
        }

        loadOpponents()

        // Try to select current opponent
        if (currentOpponent != null) {
            val index = filteredOpponents.indexOfFirst { it.id == currentOpponent.id }
            if (index >= 0) {
                selectedIndex = index
                if (selectedIndex >= visibleItems) {
                    scrollOffset = selectedIndex - visibleItems + 1
                }
            }
        }
    }

    private fun loadOpponents() {
        opponents = when (currentTab) {
            Tab.TYPE_GYMS -> OpponentService.getTypeGyms()
            Tab.DUAL_TYPES -> OpponentService.getDualTypeElites()
            Tab.RCT_TRAINERS -> OpponentService.getRCTTrainers()
        }
        filterOpponents()
    }

    private fun filterOpponents() {
        val query = searchBox.value.lowercase().trim()
        filteredOpponents = if (query.isEmpty()) {
            opponents
        } else {
            opponents.filter {
                it.displayName.lowercase().contains(query) ||
                it.types.any { type -> type.contains(query) }
            }
        }
        selectedIndex = if (filteredOpponents.isNotEmpty()) 0 else -1
        scrollOffset = 0
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)

        val dialogWidth = 300
        val dialogHeight = 260
        val dialogX = (width - dialogWidth) / 2
        val dialogY = (height - dialogHeight) / 2

        // Dialog background
        graphics.fill(dialogX - 2, dialogY - 2, dialogX + dialogWidth + 2, dialogY + dialogHeight + 2, 0xFF333333.toInt())
        graphics.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, 0xFF1a1a2e.toInt())

        // Title bar
        graphics.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + 18, 0xFF252538.toInt())
        graphics.drawCenteredString(font, Component.literal("⚔ Select Opponent ⚔").withStyle(ChatFormatting.GOLD),
            width / 2, dialogY + 5, 0xFFFFFF)

        // Opponent list
        val listX = dialogX + 10
        val listY = dialogY + 65
        val listWidth = dialogWidth - 20
        val itemHeight = 22

        graphics.fill(listX, listY, listX + listWidth, listY + visibleItems * itemHeight, 0xFF222222.toInt())

        if (filteredOpponents.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("No opponents found").withStyle(ChatFormatting.GRAY),
                dialogX + dialogWidth / 2, listY + 60, 0xFFFFFF)
        } else {
            for (i in 0 until visibleItems) {
                val index = scrollOffset + i
                if (index >= filteredOpponents.size) break

                val opponent = filteredOpponents[index]
                val itemY = listY + i * itemHeight
                val isSelected = index == selectedIndex
                val isHovered = mouseX in listX..(listX + listWidth) && mouseY in itemY..(itemY + itemHeight)

                // Background
                val bgColor = when {
                    isSelected -> 0xFF4a5568.toInt()
                    isHovered -> 0xFF3a3a4e.toInt()
                    else -> 0xFF2a2a3e.toInt()
                }
                graphics.fill(listX, itemY, listX + listWidth, itemY + itemHeight, bgColor)

                // Type color bar on left
                val typeColor = TYPE_COLORS[opponent.types.firstOrNull()?.lowercase()] ?: 0xFF888888.toInt()
                graphics.fill(listX, itemY, listX + 4, itemY + itemHeight, typeColor)

                // Name
                val name = if (opponent.displayName.length > 20) opponent.displayName.take(19) + ".." else opponent.displayName
                graphics.drawString(font, name, listX + 8, itemY + 3, 0xFFFFFFFF.toInt())

                // Type badges
                var badgeX = listX + 8
                for (type in opponent.types.take(2)) {
                    val color = TYPE_COLORS[type.lowercase()] ?: 0xFF888888.toInt()
                    val label = type.take(3).uppercase()
                    val labelWidth = font.width(label) + 2
                    graphics.fill(badgeX, itemY + 12, badgeX + labelWidth, itemY + 20, color)
                    graphics.drawString(font, label, badgeX + 1, itemY + 12, 0xFFFFFFFF.toInt(), false)
                    badgeX += labelWidth + 2
                }

                // Source indicator
                if (opponent.source == OpponentData.OpponentSource.RCT_TRAINER) {
                    graphics.drawString(font, "[RCT]", listX + listWidth - 30, itemY + 7, 0xFF888888.toInt(), false)
                }
            }

            // Scrollbar
            if (filteredOpponents.size > visibleItems) {
                val scrollBarX = listX + listWidth - 6
                val scrollBarHeight = visibleItems * itemHeight - 10
                val thumbHeight = maxOf(20, scrollBarHeight * visibleItems / filteredOpponents.size)
                val maxScrollOffset = filteredOpponents.size - visibleItems
                val thumbY = listY + 5 + ((scrollBarHeight - thumbHeight) * scrollOffset / maxOf(1, maxScrollOffset))

                graphics.fill(scrollBarX, listY + 5, scrollBarX + 4, listY + visibleItems * itemHeight - 5, 0xFF333333.toInt())
                graphics.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight, 0xFF666666.toInt())
            }
        }

        selectButton.active = selectedIndex in filteredOpponents.indices

        // RCT hint if not available
        if (!OpponentService.hasRCTTrainers()) {
            graphics.drawString(font, "Install RCT mod for trainer data",
                dialogX + 10, dialogY + dialogHeight - 44, 0xFF666666.toInt(), false)
        }

        super.render(graphics, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val dialogWidth = 300
        val dialogHeight = 260
        val dialogX = (width - dialogWidth) / 2
        val dialogY = (height - dialogHeight) / 2

        val listX = dialogX + 10
        val listY = dialogY + 65
        val listWidth = dialogWidth - 20
        val itemHeight = 22

        if (mouseX.toInt() in listX..(listX + listWidth) && mouseY.toInt() in listY..(listY + visibleItems * itemHeight)) {
            val clickedIndex = scrollOffset + ((mouseY.toInt() - listY) / itemHeight)
            if (clickedIndex in filteredOpponents.indices) {
                if (selectedIndex == clickedIndex) {
                    // Double click - select
                    selectButton.onPress()
                } else {
                    selectedIndex = clickedIndex
                }
                return true
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val maxScrollOffset = maxOf(0, filteredOpponents.size - visibleItems)
        scrollOffset = (scrollOffset - scrollY.toInt()).coerceIn(0, maxScrollOffset)
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (searchBox.isFocused) {
            return super.keyPressed(keyCode, scanCode, modifiers)
        }
        when (keyCode) {
            256 -> { // Escape
                minecraft?.setScreen(parentScreen)
                return true
            }
            257 -> { // Enter
                if (selectButton.active) {
                    selectButton.onPress()
                    return true
                }
            }
            265 -> { // Up arrow
                if (selectedIndex > 0) {
                    selectedIndex--
                    if (selectedIndex < scrollOffset) scrollOffset = selectedIndex
                }
                return true
            }
            264 -> { // Down arrow
                if (selectedIndex < filteredOpponents.size - 1) {
                    selectedIndex++
                    if (selectedIndex >= scrollOffset + visibleItems) scrollOffset = selectedIndex - visibleItems + 1
                }
                return true
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, 0xAA000000.toInt())
    }

    override fun isPauseScreen(): Boolean = false
}
