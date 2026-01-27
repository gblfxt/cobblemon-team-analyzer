package com.cobblemon.teamanalysis.client.gui

import com.cobblemon.teamanalysis.data.FilterCriteria
import com.cobblemon.teamanalysis.data.PokemonSummary
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.Optional

/**
 * Cobblemon browser screen with search, filter, and grid display.
 */
class PokemonBrowserScreen : Screen(Component.literal("Cobblemon Browser")) {

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

        private const val GRID_COLS = 6
        private const val CELL_WIDTH = 70
        private const val CELL_HEIGHT = 50
        private const val CELL_PADDING = 4
    }

    private var allPokemon: List<PokemonSummary> = emptyList()
    private var filteredPokemon: List<PokemonSummary> = emptyList()
    private var presetName: String? = null
    private var filter = FilterCriteria()

    private var scrollOffset = 0
    private var maxScroll = 0

    private lateinit var searchBox: EditBox
    private var hoveredPokemon: PokemonSummary? = null

    private val gridX: Int get() = 20
    private val gridY: Int get() = 60
    private val gridWidth: Int get() = GRID_COLS * (CELL_WIDTH + CELL_PADDING)
    private val gridHeight: Int get() = height - 100

    fun setPokemonList(pokemon: List<PokemonSummary>, presetName: String?) {
        this.allPokemon = pokemon
        this.presetName = presetName
        applyFilter()
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
        maxScroll = maxOf(0, contentHeight - gridHeight)
    }

    override fun init() {
        super.init()

        // Search box
        searchBox = EditBox(font, gridX, 30, 200, 18, Component.literal("Search"))
        searchBox.setHint(Component.literal("Search Cobblemon...").withStyle(ChatFormatting.DARK_GRAY))
        searchBox.setResponder { text ->
            filter = filter.copy(searchText = text)
            applyFilter()
        }
        addRenderableWidget(searchBox)

        // Sort buttons
        var buttonX = gridX + 210
        addRenderableWidget(Button.builder(Component.literal("Name")) {
            filter = filter.copy(sortBy = FilterCriteria.SortOption.NAME)
            applyFilter()
        }.bounds(buttonX, 30, 45, 18).build())
        buttonX += 50

        addRenderableWidget(Button.builder(Component.literal("Lvl")) {
            filter = filter.copy(sortBy = FilterCriteria.SortOption.LEVEL, sortAscending = !filter.sortAscending)
            applyFilter()
        }.bounds(buttonX, 30, 35, 18).build())
        buttonX += 40

        addRenderableWidget(Button.builder(Component.literal("IV")) {
            filter = filter.copy(sortBy = FilterCriteria.SortOption.IV_TOTAL, sortAscending = false)
            applyFilter()
        }.bounds(buttonX, 30, 30, 18).build())

        // Filter toggles on the right
        val filterX = width - 100
        addRenderableWidget(Button.builder(Component.literal("Shiny")) {
            filter = filter.copy(showShinyOnly = !filter.showShinyOnly)
            applyFilter()
        }.bounds(filterX, 60, 50, 18).build())

        addRenderableWidget(Button.builder(Component.literal("Legend")) {
            filter = filter.copy(showLegendaryOnly = !filter.showLegendaryOnly)
            applyFilter()
        }.bounds(filterX, 82, 50, 18).build())

        addRenderableWidget(Button.builder(Component.literal("Party")) {
            filter = filter.copy(showPartyOnly = !filter.showPartyOnly, showPCOnly = false)
            applyFilter()
        }.bounds(filterX, 104, 50, 18).build())

        addRenderableWidget(Button.builder(Component.literal("PC")) {
            filter = filter.copy(showPCOnly = !filter.showPCOnly, showPartyOnly = false)
            applyFilter()
        }.bounds(filterX, 126, 50, 18).build())

        // Close button
        addRenderableWidget(Button.builder(Component.literal("X")) { onClose() }
            .bounds(width - 25, 5, 20, 20)
            .build())

        updateMaxScroll()
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)

        // Title
        val title = if (presetName != null) "Preset: $presetName" else "Cobblemon Browser"
        graphics.drawCenteredString(font, Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            width / 2, 10, 0xFFFFFF)

        // Stats
        val stats = "${filteredPokemon.size}/${allPokemon.size} Cobblemon"
        graphics.drawString(font, stats, gridX, height - 25, 0xAAAAAA)

        // Grid background
        graphics.fill(gridX - 2, gridY - 2, gridX + gridWidth + 2, gridY + gridHeight + 2, 0xFF222222.toInt())

        // Enable scissor for scrolling
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

        super.render(graphics, mouseX, mouseY, partialTick)

        // Tooltip
        if (hoveredPokemon != null) {
            renderPokemonTooltip(graphics, hoveredPokemon!!, mouseX, mouseY)
        }
    }

    private fun renderPokemonCell(graphics: GuiGraphics, pokemon: PokemonSummary, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val isHovered = mouseX in x..(x + CELL_WIDTH) && mouseY in y..(y + CELL_HEIGHT)
        if (isHovered) hoveredPokemon = pokemon

        // Push z-level for cells to render above grid background
        graphics.pose().pushPose()
        graphics.pose().translate(0.0, 0.0, 10.0)

        // Background - grey for missing, highlight for hovered
        val bgColor = when {
            pokemon.isMissing -> 0xFF3a3a3a.toInt()
            isHovered -> 0xFF4a5568.toInt()
            pokemon.isInParty -> 0xFF2d4a2d.toInt()
            else -> 0xFF2a2a3e.toInt()
        }
        graphics.fill(x, y, x + CELL_WIDTH, y + CELL_HEIGHT, bgColor)

        // Border
        val borderColor = when {
            pokemon.isMissing -> 0xFF555555.toInt()
            isHovered -> 0xFF6a6a8a.toInt()
            else -> 0xFF404050.toInt()
        }
        graphics.renderOutline(x, y, CELL_WIDTH, CELL_HEIGHT, borderColor)

        // Text color - greyed out for missing
        val textColor = if (pokemon.isMissing) 0xFF888888.toInt() else 0xFFFFFFFF.toInt()
        val nameColor = if (pokemon.isMissing) 0xFF666666.toInt() else 0xFFFFFFFF.toInt()

        // Pokemon name (truncated)
        val name = if (pokemon.displayName.length > 9) pokemon.displayName.take(8) + ".." else pokemon.displayName
        graphics.drawString(font, name, x + 3, y + 3, nameColor, false)

        // Level
        if (!pokemon.isMissing) {
            graphics.drawString(font, "Lv${pokemon.level}", x + 3, y + 14, 0xFFCCCCCC.toInt(), false)
        } else {
            graphics.drawString(font, "MISSING", x + 3, y + 14, 0xFFFF6666.toInt(), false)
        }

        // Type badges
        var badgeX = x + 3
        val badgeY = y + CELL_HEIGHT - 14

        badgeX = renderTypeBadgeMini(graphics, pokemon.type1, badgeX, badgeY, pokemon.isMissing)
        if (pokemon.type2 != null) {
            renderTypeBadgeMini(graphics, pokemon.type2, badgeX + 2, badgeY, pokemon.isMissing)
        }

        // Shiny indicator
        if (pokemon.isShiny && !pokemon.isMissing) {
            graphics.drawString(font, "✦", x + CELL_WIDTH - 10, y + 3, 0xFFFFD700.toInt(), false)
        }

        // Party indicator
        if (pokemon.isInParty && !pokemon.isMissing) {
            graphics.fill(x + CELL_WIDTH - 8, y + CELL_HEIGHT - 8, x + CELL_WIDTH - 2, y + CELL_HEIGHT - 2, 0xFF44AA44.toInt())
        }

        graphics.pose().popPose()
    }

    private fun renderTypeBadgeMini(graphics: GuiGraphics, type: String, x: Int, y: Int, greyed: Boolean): Int {
        val baseColor = TYPE_COLORS[type.lowercase()] ?: 0xFF888888.toInt()
        val color = if (greyed) {
            // Desaturate color for missing Pokemon
            val r = ((baseColor shr 16) and 0xFF) / 2 + 64
            val g = ((baseColor shr 8) and 0xFF) / 2 + 64
            val b = (baseColor and 0xFF) / 2 + 64
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        } else baseColor

        val label = type.take(3).uppercase()
        val labelWidth = font.width(label) + 2

        graphics.fill(x, y, x + labelWidth, y + 10, color)
        graphics.drawString(font, label, x + 1, y + 1, if (greyed) 0xFFAAAAAA.toInt() else 0xFFFFFFFF.toInt(), false)

        return x + labelWidth
    }

    private fun renderPokemonTooltip(graphics: GuiGraphics, pokemon: PokemonSummary, mouseX: Int, mouseY: Int) {
        val lines = mutableListOf<Component>()

        if (pokemon.isMissing) {
            lines.add(Component.literal(pokemon.displayName).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
            lines.add(Component.literal("This Pokemon is missing!").withStyle(ChatFormatting.GRAY))
            lines.add(Component.literal("It may have been released or traded.").withStyle(ChatFormatting.DARK_GRAY))
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
            lines.add(Component.literal("EVs: ${pokemon.evTotal}/510").withStyle(ChatFormatting.GREEN))

            if (pokemon.isShiny) {
                lines.add(Component.literal(""))
                lines.add(Component.literal("✦ Shiny!").withStyle(ChatFormatting.GOLD))
            }

            if (pokemon.isLegendary) {
                lines.add(Component.literal("★ Legendary").withStyle(ChatFormatting.LIGHT_PURPLE))
            }

            lines.add(Component.literal(""))
            val location = if (pokemon.isInParty) "In Party" else "In PC"
            lines.add(Component.literal(location).withStyle(ChatFormatting.DARK_GRAY))
        }

        graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        scrollOffset = (scrollOffset - (scrollY * 20).toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Could add click handling for selecting Pokemon
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (searchBox.isFocused) {
            return super.keyPressed(keyCode, scanCode, modifiers)
        }
        if (keyCode == 256) { // Escape
            onClose()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, 0xFF0a0a0a.toInt())
    }

    override fun isPauseScreen(): Boolean = false
}
