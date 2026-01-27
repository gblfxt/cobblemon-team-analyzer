package com.cobblemon.teamanalysis.network

import com.cobblemon.teamanalysis.client.PresetLoadScreen
import com.cobblemon.teamanalysis.client.gui.PokemonBrowserScreen
import com.cobblemon.teamanalysis.client.gui.TeamAnalysisScreen
import com.cobblemon.teamanalysis.client.gui.TeamBuilderLoadScreen
import com.cobblemon.teamanalysis.client.gui.TeamBuilderScreen
import net.minecraft.client.Minecraft

/**
 * Client-side packet handlers.
 * Implements IClientPacketHandler interface for dist-safe networking.
 */
object ClientPacketHandler : ModNetworking.IClientPacketHandler {

    /**
     * Register this handler with ModNetworking.
     * Called during client setup.
     */
    fun register() {
        ModNetworking.clientHandler = this
    }

    /**
     * Open the analysis screen with the received data.
     */
    override fun openAnalysisScreen(payload: TeamAnalysisPayload) {
        val mc = Minecraft.getInstance()
        if (mc.level == null) return

        val screen = TeamAnalysisScreen()
        screen.setAnalysis(payload.toAnalysis(), payload.teamName)
        screen.setPresetNames(payload.presetNames)

        mc.setScreen(screen)
    }

    /**
     * Handle preset list response - update the PresetLoadScreen or TeamBuilderLoadScreen if open.
     */
    override fun handlePresetList(payload: PresetListPayload) {
        val mc = Minecraft.getInstance()
        val currentScreen = mc.screen

        when (currentScreen) {
            is PresetLoadScreen -> currentScreen.setPresetNames(payload.presetNames)
            is TeamBuilderLoadScreen -> currentScreen.setPresetNames(payload.presetNames)
        }
    }

    /**
     * Handle Pokemon collection - open or update the browser/builder screen.
     */
    override fun handlePokemonCollection(payload: PokemonCollectionPayload) {
        val mc = Minecraft.getInstance()
        if (mc.level == null) return

        // Handle forBuilder flag
        if (payload.forBuilder) {
            openTeamBuilderScreen(payload)
            return
        }

        val currentScreen = mc.screen

        when (currentScreen) {
            is TeamBuilderScreen -> {
                // Update existing builder screen
                currentScreen.setPokemonList(payload.pokemon)
            }
            is PokemonBrowserScreen -> {
                // Update existing browser screen
                currentScreen.setPokemonList(payload.pokemon, payload.presetName)
            }
            else -> {
                // Open new browser screen (default behavior)
                val screen = PokemonBrowserScreen()
                screen.setPokemonList(payload.pokemon, payload.presetName)
                mc.setScreen(screen)
            }
        }
    }

    /**
     * Open the Team Builder screen with Pokemon collection.
     */
    fun openTeamBuilderScreen(payload: PokemonCollectionPayload) {
        val mc = Minecraft.getInstance()
        if (mc.level == null) return

        val currentScreen = mc.screen

        // If builder is already open, just update it
        if (currentScreen is TeamBuilderScreen) {
            currentScreen.setPokemonList(payload.pokemon)
        } else {
            // Open new builder screen
            val screen = TeamBuilderScreen()
            screen.setPokemonList(payload.pokemon)
            mc.setScreen(screen)
        }
    }

    /**
     * Handle preset Pokemon for loading into Team Builder.
     */
    override fun handlePresetPokemon(payload: PresetPokemonPayload) {
        val mc = Minecraft.getInstance()
        val currentScreen = mc.screen

        if (currentScreen is TeamBuilderScreen) {
            currentScreen.loadPresetPokemon(payload.pokemon)
        }
    }
}
