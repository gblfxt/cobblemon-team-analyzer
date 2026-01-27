package com.cobblemon.teamanalysis.client.gui

import com.cobblemon.teamanalysis.analysis.TeamAnalyzer
import com.cobblemon.teamanalysis.analysis.TypeEffectiveness
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.Optional

/**
 * Main GUI screen for Cobblemon Team Analysis.
 */
class TeamAnalysisScreen : Screen(Component.literal("Cobblemon Team Analysis")) {

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
            "fairy" to 0xFFEE99AC.toInt()
        )

        private const val PANEL_PADDING = 10
    }

    private var analysis: TeamAnalyzer.TeamAnalysis? = null
    private var presetNames: List<String> = emptyList()
    private var currentTeamName: String = "Current Party"
    private var scrollOffset = 0
    private var maxScroll = 0

    fun setAnalysis(analysis: TeamAnalyzer.TeamAnalysis, teamName: String?) {
        this.analysis = analysis
        this.currentTeamName = teamName ?: "Current Party"
    }

    fun setPresetNames(names: List<String>) {
        this.presetNames = names
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Override to prevent Minecraft 1.21's default blur effect
        // Just fill with solid opaque dark color instead
        graphics.fill(0, 0, width, height, 0xFF0a0a0a.toInt())
    }

    override fun init() {
        super.init()

        // Close button
        addRenderableWidget(Button.builder(Component.literal("X")) { onClose() }
            .bounds(width - 25, 5, 20, 20)
            .build())

        // Preset buttons
        var presetY = 50
        for ((index, name) in presetNames.take(8).withIndex()) {
            addRenderableWidget(Button.builder(Component.literal(truncate(name, 10))) {
                loadPreset(name)
            }.bounds(10, presetY + (index * 22), 80, 20).build())
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Background is rendered by renderBackground() override - no blur
        renderBackground(graphics, mouseX, mouseY, partialTick)

        val panelX = 100
        val panelY = 30
        val panelWidth = width - 120
        val panelHeight = height - 60

        // Panel background
        graphics.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, 0xFF333333.toInt())
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF1a1a2e.toInt())

        // Title
        graphics.drawCenteredString(font, Component.literal("Cobblemon Team Analysis")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), width / 2, 10, 0xFFFFFF)

        // Sidebar title
        graphics.drawString(font, "Presets", 10, 35, 0xFFAAAAAA.toInt())

        val currentAnalysis = analysis
        if (currentAnalysis == null) {
            graphics.drawCenteredString(font, "Loading...", width / 2, height / 2, 0xFFFFFF)
            super.render(graphics, mouseX, mouseY, partialTick)
            return
        }

        // Enable scissor for scrolling
        graphics.enableScissor(panelX, panelY, panelX + panelWidth, panelY + panelHeight)

        var contentY = panelY + PANEL_PADDING - scrollOffset
        val contentX = panelX + PANEL_PADDING

        // Team name
        graphics.drawString(font, Component.literal("Team: $currentTeamName")
            .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), contentX, contentY, 0xFFFFFF)
        contentY += 15

        // Pokemon list
        graphics.drawString(font, Component.literal("Pokemon (${currentAnalysis.teamSize})")
            .withStyle(ChatFormatting.YELLOW), contentX, contentY, 0xFFFFFF)
        contentY += 12

        for (pokemon in currentAnalysis.pokemon) {
            graphics.drawString(font, pokemon.name, contentX + 5, contentY, 0xFFFFFF)

            var badgeX = contentX + 85
            badgeX = renderTypeBadge(graphics, pokemon.type1, badgeX, contentY - 1)
            if (pokemon.type2 != null) {
                renderTypeBadge(graphics, pokemon.type2, badgeX + 2, contentY - 1)
            }

            // Hover tooltip
            if (mouseX in contentX..(contentX + panelWidth - 20) && mouseY in (contentY - 2)..(contentY + 10)) {
                renderPokemonTooltip(graphics, pokemon, mouseX, mouseY)
            }

            contentY += 14
        }

        contentY += 8

        // Weaknesses
        if (currentAnalysis.criticalWeaknesses.isNotEmpty()) {
            graphics.drawString(font, Component.literal("⚠ WEAKNESSES")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), contentX, contentY, 0xFFFFFF)
            contentY += 12

            for (weakness in currentAnalysis.criticalWeaknesses) {
                val count = currentAnalysis.weaknessCounts[weakness] ?: 0
                val color = TYPE_COLORS[weakness.lowercase()] ?: 0xFFAAAAAA.toInt()
                graphics.fill(contentX + 5, contentY, contentX + 8, contentY + 8, color)
                graphics.drawString(font, "${count}x weak to ${TypeEffectiveness.formatType(weakness)}", contentX + 12, contentY, 0xFFFF6666.toInt())
                contentY += 11
            }
            contentY += 8
        }

        // Coverage gaps
        if (currentAnalysis.uncoveredTypes.isNotEmpty()) {
            graphics.drawString(font, Component.literal("⚔ COVERAGE GAPS")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), contentX, contentY, 0xFFFFFF)
            contentY += 12

            var badgeX = contentX + 5
            for (type in currentAnalysis.uncoveredTypes) {
                val newX = renderTypeBadge(graphics, type, badgeX, contentY)
                badgeX = newX + 4
                if (badgeX > contentX + panelWidth - 50) {
                    badgeX = contentX + 5
                    contentY += 13
                }
            }
            contentY += 20
        }

        // Defensive strengths
        val immuneTypes = mutableListOf<String>()
        val strongTypes = mutableListOf<String>()
        for (type in TypeEffectiveness.ALL_TYPES) {
            val immune = currentAnalysis.immunityCounts[type] ?: 0
            val resist = currentAnalysis.resistanceCounts[type] ?: 0
            val weak = currentAnalysis.weaknessCounts[type] ?: 0
            if (immune > 0) immuneTypes.add(type)
            else if (resist > weak && resist >= 2) strongTypes.add(type)
        }

        if (immuneTypes.isNotEmpty() || strongTypes.isNotEmpty()) {
            graphics.drawString(font, Component.literal("🛡 DEFENSIVE STRENGTHS")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), contentX, contentY, 0xFFFFFF)
            contentY += 12

            if (immuneTypes.isNotEmpty()) {
                graphics.drawString(font, "Immune:", contentX + 5, contentY, 0xFFFFD700.toInt())
                var badgeX = contentX + 50
                for (type in immuneTypes) {
                    badgeX = renderTypeBadge(graphics, type, badgeX, contentY - 1) + 2
                }
                contentY += 13
            }

            if (strongTypes.isNotEmpty()) {
                graphics.drawString(font, "Resists:", contentX + 5, contentY, 0xFF90EE90.toInt())
                var badgeX = contentX + 50
                for (type in strongTypes) {
                    badgeX = renderTypeBadge(graphics, type, badgeX, contentY - 1) + 2
                    if (badgeX > contentX + panelWidth - 50) {
                        badgeX = contentX + 50
                        contentY += 13
                    }
                }
                contentY += 13
            }
        }

        // Update max scroll
        maxScroll = maxOf(0, contentY + scrollOffset - (panelY + panelHeight) + PANEL_PADDING)

        graphics.disableScissor()

        // Scroll bar
        if (maxScroll > 0) {
            val scrollBarHeight = panelHeight - 20
            val scrollThumbHeight = maxOf(20, (scrollBarHeight * panelHeight / (panelHeight + maxScroll)))
            val scrollThumbY = panelY + 10 + ((scrollBarHeight - scrollThumbHeight) * scrollOffset / maxScroll)

            graphics.fill(panelX + panelWidth - 6, panelY + 10, panelX + panelWidth - 2, panelY + panelHeight - 10, 0xFF333333.toInt())
            graphics.fill(panelX + panelWidth - 5, scrollThumbY, panelX + panelWidth - 3, scrollThumbY + scrollThumbHeight, 0xFF666666.toInt())
        }

        super.render(graphics, mouseX, mouseY, partialTick)
    }

    private fun renderTypeBadge(graphics: GuiGraphics, type: String, x: Int, y: Int): Int {
        val color = TYPE_COLORS[type.lowercase()] ?: 0xFFAAAAAA.toInt()
        val label = type.take(3).uppercase()
        val labelWidth = font.width(label) + 4

        graphics.fill(x, y, x + labelWidth, y + 10, color)
        graphics.drawString(font, label, x + 2, y + 1, 0xFFFFFF, true)

        return x + labelWidth
    }

    private fun renderPokemonTooltip(graphics: GuiGraphics, pokemon: TeamAnalyzer.PokemonInfo, mouseX: Int, mouseY: Int) {
        val lines = mutableListOf<Component>()
        lines.add(Component.literal(pokemon.name).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
        lines.add(Component.literal("Type: ${pokemon.getTypeString()}").withStyle(ChatFormatting.GRAY))

        if (pokemon.weaknesses.isNotEmpty()) {
            lines.add(Component.literal(""))
            lines.add(Component.literal("Weak to:").withStyle(ChatFormatting.RED))
            lines.add(Component.literal("  ${pokemon.weaknesses.joinToString(", ") { TypeEffectiveness.formatType(it) }}").withStyle(ChatFormatting.GRAY))
        }

        if (pokemon.resistances.isNotEmpty()) {
            lines.add(Component.literal(""))
            lines.add(Component.literal("Resists:").withStyle(ChatFormatting.GREEN))
            lines.add(Component.literal("  ${pokemon.resistances.joinToString(", ") { TypeEffectiveness.formatType(it) }}").withStyle(ChatFormatting.GRAY))
        }

        if (pokemon.immunities.isNotEmpty()) {
            lines.add(Component.literal(""))
            lines.add(Component.literal("Immune to:").withStyle(ChatFormatting.GOLD))
            lines.add(Component.literal("  ${pokemon.immunities.joinToString(", ") { TypeEffectiveness.formatType(it) }}").withStyle(ChatFormatting.GRAY))
        }

        graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        scrollOffset = (scrollOffset - (scrollY * 10).toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun isPauseScreen(): Boolean = false

    private fun loadPreset(name: String) {
        minecraft?.player?.connection?.sendCommand("cta analyze \"$name\"")
        onClose()
    }

    private fun truncate(s: String, maxLen: Int): String {
        return if (s.length <= maxLen) s else s.take(maxLen - 2) + ".."
    }
}
