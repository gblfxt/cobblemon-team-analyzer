package com.cobblemon.teamanalysis.client

import com.mojang.blaze3d.platform.InputConstants
import com.cobblemon.teamanalysis.CobblemonTeamAnalysis
import com.cobblemon.teamanalysis.network.ClientPacketHandler
import com.cobblemon.teamanalysis.network.RequestAnalysisPayload
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.settings.KeyConflictContext
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

/**
 * Client-side setup and event handling.
 */
@EventBusSubscriber(modid = CobblemonTeamAnalysis.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object ClientSetup {

    val OPEN_GUI_KEY: KeyMapping = KeyMapping(
        "key.cobblemonteamanalysis.open_gui",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_J,
        "key.categories.cobblemonteamanalysis"
    )

    @SubscribeEvent
    @JvmStatic
    fun onClientSetup(event: FMLClientSetupEvent) {
        CobblemonTeamAnalysis.LOGGER.info("Cobblemon Team Analysis client setup")

        // Register client packet handler
        ClientPacketHandler.register()

        NeoForge.EVENT_BUS.addListener(::onClientTick)
        // Register PC GUI integration for preset buttons
        NeoForge.EVENT_BUS.register(PCIntegration)
        CobblemonTeamAnalysis.LOGGER.info("Registered PC GUI integration")
    }

    @SubscribeEvent
    @JvmStatic
    fun onRegisterKeyMappings(event: RegisterKeyMappingsEvent) {
        event.register(OPEN_GUI_KEY)
        CobblemonTeamAnalysis.LOGGER.info("Registered Team Analysis keybind (default: J)")
    }

    private fun onClientTick(event: ClientTickEvent.Post) {
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) return

        while (OPEN_GUI_KEY.consumeClick()) {
            PacketDistributor.sendToServer(RequestAnalysisPayload(null))
        }
    }
}
