package com.cobblemon.teamanalysis.network

import com.cobblemon.teamanalysis.CobblemonTeamAnalysis
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * Client → Server payload requesting Pokemon collection.
 * If presetName is set, returns just the preset's Pokemon (with missing marked).
 * If empty, returns the full collection.
 * If forBuilder is true, opens TeamBuilderScreen instead of PokemonBrowserScreen.
 */
data class RequestCollectionPayload(
    val presetName: String,  // Empty string for full collection
    val forBuilder: Boolean = false  // True to open TeamBuilderScreen
) : CustomPacketPayload {

    constructor(presetName: String) : this(presetName, false)

    companion object {
        val TYPE = CustomPacketPayload.Type<RequestCollectionPayload>(
            ResourceLocation.fromNamespaceAndPath(CobblemonTeamAnalysis.MOD_ID, "request_collection")
        )

        val STREAM_CODEC: StreamCodec<ByteBuf, RequestCollectionPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestCollectionPayload::presetName,
            ByteBufCodecs.BOOL, RequestCollectionPayload::forBuilder,
            ::RequestCollectionPayload
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
