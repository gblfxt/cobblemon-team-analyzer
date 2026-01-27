package com.cobblemon.teamanalysis.client

import com.cobblemon.mod.common.client.gui.pc.PCGUI
import com.cobblemon.teamanalysis.CobblemonTeamAnalysis
import com.cobblemon.teamanalysis.network.PresetActionPayload
import com.cobblemon.teamanalysis.network.RequestCollectionPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.network.PacketDistributor

/**
 * Integrates preset functionality into Cobblemon's PC GUI.
 */
object PCIntegration {

    private const val BUTTON_WIDTH = 60
    private const val BUTTON_HEIGHT = 16
    private const val BUTTON_SPACING = 4

    /**
     * Called when any screen finishes initializing.
     * We check if it's Cobblemon's PCGUI and add our buttons.
     */
    @SubscribeEvent
    fun onScreenInit(event: ScreenEvent.Init.Post) {
        val screen = event.screen

        // Check if this is Cobblemon's PC GUI
        if (screen !is PCGUI) return

        CobblemonTeamAnalysis.LOGGER.info("Detected PCGUI open, adding preset buttons")

        // Calculate button positions - place in bottom-left area
        val baseX = 10
        val baseY = screen.height - 60

        // Save Preset button
        val saveButton = Button.builder(Component.literal("Save Team")) { button ->
            openSaveDialog(screen)
        }
            .bounds(baseX, baseY, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build()

        // Load Preset button
        val loadButton = Button.builder(Component.literal("Load Team")) { button ->
            openLoadDialog(screen)
        }
            .bounds(baseX, baseY + BUTTON_HEIGHT + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build()

        // Analyze button
        val analyzeButton = Button.builder(Component.literal("Analyze")) { button ->
            // Request analysis of current party
            PacketDistributor.sendToServer(PresetActionPayload("analyze", ""))
        }
            .bounds(baseX + BUTTON_WIDTH + BUTTON_SPACING, baseY, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build()

        // Browse button - opens Pokemon browser
        val browseButton = Button.builder(Component.literal("Browse")) { button ->
            // Request full Pokemon collection
            PacketDistributor.sendToServer(RequestCollectionPayload(""))
        }
            .bounds(baseX + BUTTON_WIDTH + BUTTON_SPACING, baseY + BUTTON_HEIGHT + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build()

        // Add buttons to the screen
        event.addListener(saveButton)
        event.addListener(loadButton)
        event.addListener(analyzeButton)
        event.addListener(browseButton)
    }

    private fun openSaveDialog(parentScreen: Screen) {
        val minecraft = Minecraft.getInstance()
        minecraft.setScreen(PresetSaveScreen(parentScreen))
    }

    private fun openLoadDialog(parentScreen: Screen) {
        val minecraft = Minecraft.getInstance()
        minecraft.setScreen(PresetLoadScreen(parentScreen))
    }
}
