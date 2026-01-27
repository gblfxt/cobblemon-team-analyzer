package com.cobblemon.teamanalysis.network

import com.cobblemon.teamanalysis.CobblemonTeamAnalysis
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * Client → Server payload for preset actions (save, load, delete, analyze).
 */
data class PresetActionPayload(
    val action: String,  // "save", "load", "delete", "analyze"
    val presetName: String
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<PresetActionPayload>(
            ResourceLocation.fromNamespaceAndPath(CobblemonTeamAnalysis.MOD_ID, "preset_action")
        )

        val STREAM_CODEC: StreamCodec<ByteBuf, PresetActionPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PresetActionPayload::action,
            ByteBufCodecs.STRING_UTF8, PresetActionPayload::presetName,
            ::PresetActionPayload
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
