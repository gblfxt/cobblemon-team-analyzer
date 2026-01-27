package com.cobblemon.teamanalysis.network

import com.cobblemon.teamanalysis.CobblemonTeamAnalysis
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * Server → Client payload containing the list of preset names.
 */
data class PresetListPayload(
    val presetNames: List<String>
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<PresetListPayload>(
            ResourceLocation.fromNamespaceAndPath(CobblemonTeamAnalysis.MOD_ID, "preset_list")
        )

        val STREAM_CODEC: StreamCodec<ByteBuf, PresetListPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PresetListPayload::presetNames,
            ::PresetListPayload
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
