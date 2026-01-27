package com.cobblemon.teamanalysis.network

import com.cobblemon.teamanalysis.CobblemonTeamAnalysis
import com.cobblemon.teamanalysis.analysis.TeamAnalyzer
import com.cobblemon.teamanalysis.data.PresetStorage
import com.cobblemon.teamanalysis.data.TeamPreset
import com.cobblemon.teamanalysis.util.CobblemonHelper
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Handles network packet registration and processing.
 */
object ModNetworking {

    // Client handler is set by ClientNetworking on client init
    var clientHandler: IClientPacketHandler? = null

    interface IClientPacketHandler {
        fun openAnalysisScreen(payload: TeamAnalysisPayload)
        fun handlePresetList(payload: PresetListPayload)
        fun handlePokemonCollection(payload: PokemonCollectionPayload)
        fun handlePresetPokemon(payload: PresetPokemonPayload)
    }

    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(CobblemonTeamAnalysis.MOD_ID).versioned("1.0")

        // Server -> Client payloads - handler delegates to client handler if available
        registrar.playToClient(
            TeamAnalysisPayload.TYPE,
            TeamAnalysisPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                clientHandler?.openAnalysisScreen(payload)
            }
        }

        registrar.playToClient(
            PresetListPayload.TYPE,
            PresetListPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                clientHandler?.handlePresetList(payload)
            }
        }

        registrar.playToClient(
            PokemonCollectionPayload.TYPE,
            PokemonCollectionPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                clientHandler?.handlePokemonCollection(payload)
            }
        }

        registrar.playToClient(
            PresetPokemonPayload.TYPE,
            PresetPokemonPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                clientHandler?.handlePresetPokemon(payload)
            }
        }

        // Client -> Server: Request analysis
        registrar.playToServer(
            RequestAnalysisPayload.TYPE,
            RequestAnalysisPayload.STREAM_CODEC,
            ::handleRequestAnalysis
        )

        // Client -> Server: Request presets list
        registrar.playToServer(
            RequestPresetsPayload.TYPE,
            RequestPresetsPayload.STREAM_CODEC,
            ::handleRequestPresets
        )

        // Client -> Server: Preset action (save/load/delete/analyze)
        registrar.playToServer(
            PresetActionPayload.TYPE,
            PresetActionPayload.STREAM_CODEC,
            ::handlePresetAction
        )

        // Client -> Server: Request Pokemon collection
        registrar.playToServer(
            RequestCollectionPayload.TYPE,
            RequestCollectionPayload.STREAM_CODEC,
            ::handleRequestCollection
        )

        CobblemonTeamAnalysis.LOGGER.info("Registered network payloads")
    }

    private fun handleRequestCollection(payload: RequestCollectionPayload, context: IPayloadContext) {
        context.enqueueWork {
            val player = context.player()
            if (player is ServerPlayer) {
                sendCollectionToPlayer(player, payload.presetName.takeIf { it.isNotEmpty() }, payload.forBuilder)
            }
        }
    }

    /**
     * Send Pokemon collection to a player.
     * @param forBuilder if true, opens TeamBuilderScreen instead of PokemonBrowserScreen
     */
    fun sendCollectionToPlayer(player: ServerPlayer, presetName: String?, forBuilder: Boolean = false) {
        val pokemon = if (!presetName.isNullOrEmpty()) {
            // Get preset Pokemon with missing markers
            val preset = PresetStorage.get().getPreset(player.uuid, presetName) ?: return
            CobblemonHelper.getPresetPokemonSummaries(player, preset)
        } else {
            // Get full collection
            CobblemonHelper.getAllPokemonSummaries(player)
        }

        PacketDistributor.sendToPlayer(player, PokemonCollectionPayload(pokemon, presetName, forBuilder))
    }

    private fun handleRequestAnalysis(payload: RequestAnalysisPayload, context: IPayloadContext) {
        context.enqueueWork {
            val player = context.player()
            if (player is ServerPlayer) {
                sendAnalysisToPlayer(player, payload.presetName)
            }
        }
    }

    private fun handleRequestPresets(payload: RequestPresetsPayload, context: IPayloadContext) {
        context.enqueueWork {
            val player = context.player()
            if (player is ServerPlayer) {
                val presetNames = PresetStorage.get().getPresets(player.uuid).map { it.name }
                PacketDistributor.sendToPlayer(player, PresetListPayload(presetNames))
            }
        }
    }

    private fun handlePresetAction(payload: PresetActionPayload, context: IPayloadContext) {
        context.enqueueWork {
            val player = context.player()
            if (player !is ServerPlayer) return@enqueueWork

            when (payload.action) {
                "save" -> {
                    val preset = CobblemonHelper.createPresetFromParty(player, payload.presetName)
                    if (preset == null || preset.slots.isEmpty()) {
                        player.sendSystemMessage(Component.literal("Your party is empty!")
                            .withStyle(ChatFormatting.RED))
                        return@enqueueWork
                    }

                    val saved = PresetStorage.get().savePreset(player.uuid, preset)
                    if (saved) {
                        player.sendSystemMessage(Component.literal("Saved team: ${payload.presetName}")
                            .withStyle(ChatFormatting.GREEN))
                    } else {
                        player.sendSystemMessage(Component.literal("Failed to save. Name exists or max presets (20) reached.")
                            .withStyle(ChatFormatting.RED))
                    }
                }

                "load" -> {
                    val preset = PresetStorage.get().getPreset(player.uuid, payload.presetName)
                    if (preset == null) {
                        player.sendSystemMessage(Component.literal("Preset not found: ${payload.presetName}")
                            .withStyle(ChatFormatting.RED))
                        return@enqueueWork
                    }

                    val success = CobblemonHelper.loadPresetToParty(player, preset)
                    if (success) {
                        preset.markUsed()
                        PresetStorage.get().updatePreset(player.uuid, preset)
                        player.sendSystemMessage(Component.literal("Loaded team: ${payload.presetName}")
                            .withStyle(ChatFormatting.GREEN))
                    } else {
                        player.sendSystemMessage(Component.literal("Some Pokemon in this preset are missing.")
                            .withStyle(ChatFormatting.YELLOW))
                    }
                }

                "delete" -> {
                    val deleted = PresetStorage.get().deletePreset(player.uuid, payload.presetName)
                    if (deleted) {
                        player.sendSystemMessage(Component.literal("Deleted preset: ${payload.presetName}")
                            .withStyle(ChatFormatting.RED))
                    } else {
                        player.sendSystemMessage(Component.literal("Preset not found: ${payload.presetName}")
                            .withStyle(ChatFormatting.RED))
                    }
                }

                "analyze" -> {
                    sendAnalysisToPlayer(player, null)
                }

                "load_to_builder" -> {
                    val preset = PresetStorage.get().getPreset(player.uuid, payload.presetName)
                    if (preset == null) {
                        player.sendSystemMessage(Component.literal("Preset not found: ${payload.presetName}")
                            .withStyle(ChatFormatting.RED))
                        return@enqueueWork
                    }

                    // Send preset Pokemon summaries to populate builder slots
                    val pokemon = CobblemonHelper.getPresetPokemonSummaries(player, preset)
                    PacketDistributor.sendToPlayer(player, PresetPokemonPayload(pokemon))
                }
            }
        }
    }

    /**
     * Send analysis data to a player (called from server).
     */
    fun sendAnalysisToPlayer(player: ServerPlayer, presetName: String?) {
        val analysis: TeamAnalyzer.TeamAnalysis
        val teamName: String

        if (!presetName.isNullOrEmpty()) {
            // Analyze a saved preset
            val preset = PresetStorage.get().getPreset(player.uuid, presetName) ?: return

            val speciesIds = preset.slots
                .filter { it.speciesId != null }
                .map { it.speciesId }

            analysis = TeamAnalyzer.analyze(speciesIds)
            teamName = preset.name
        } else {
            // Analyze current party
            val currentParty = CobblemonHelper.createPresetFromParty(player, "current") ?: return

            val speciesIds = currentParty.slots
                .filter { it.speciesId != null }
                .map { it.speciesId }

            analysis = TeamAnalyzer.analyze(speciesIds)
            teamName = "Current Party"
        }

        // Get preset names for the sidebar
        val presetNames = PresetStorage.get().getPresets(player.uuid).map { it.name }

        // Send to client
        val responsePayload = TeamAnalysisPayload.fromAnalysis(analysis, teamName, presetNames)
        PacketDistributor.sendToPlayer(player, responsePayload)
    }
}
