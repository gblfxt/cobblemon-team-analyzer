package com.cobblemon.teamanalysis.network

import com.cobblemon.teamanalysis.CobblemonTeamAnalysis
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * Client → Server payload requesting the list of preset names.
 * Uses object singleton to work with StreamCodec.unit()
 */
object RequestPresetsPayload : CustomPacketPayload {

    val TYPE = CustomPacketPayload.Type<RequestPresetsPayload>(
        ResourceLocation.fromNamespaceAndPath(CobblemonTeamAnalysis.MOD_ID, "request_presets")
    )

    val STREAM_CODEC: StreamCodec<ByteBuf, RequestPresetsPayload> = StreamCodec.unit(RequestPresetsPayload)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
