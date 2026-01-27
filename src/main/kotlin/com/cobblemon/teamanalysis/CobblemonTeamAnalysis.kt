package com.cobblemon.teamanalysis

import com.cobblemon.teamanalysis.command.TeamAnalysisCommands
import com.cobblemon.teamanalysis.data.PresetStorage
import com.cobblemon.teamanalysis.network.ModNetworking
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

@Mod(CobblemonTeamAnalysis.MOD_ID)
class CobblemonTeamAnalysis(modEventBus: IEventBus, modContainer: ModContainer) {

    companion object {
        const val MOD_ID = "cobblemonteamanalysis"
        const val MOD_NAME = "Cobblemon Team Analysis"
        val LOGGER: Logger = LoggerFactory.getLogger(MOD_NAME)

        fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
    }

    init {
        LOGGER.info("$MOD_NAME initializing...")

        // Register mod event listeners
        modEventBus.addListener(::commonSetup)
        modEventBus.addListener(ModNetworking::register)

        // Register game event listeners
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onServerStarting)
    }

    private fun commonSetup(event: FMLCommonSetupEvent) {
        LOGGER.info("$MOD_NAME common setup complete")
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        TeamAnalysisCommands.register(event.dispatcher)
        LOGGER.info("Registered $MOD_NAME commands")
    }

    private fun onServerStarting(event: ServerStartingEvent) {
        PresetStorage.init(event.server)
        LOGGER.info("$MOD_NAME ready!")
    }
}
