package com.cobblemon.teamanalysis.network

import com.cobblemon.teamanalysis.CobblemonTeamAnalysis
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * Payload sent from client to server to request team analysis GUI data.
 */
data class RequestAnalysisPayload(
    val presetName: String?
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<RequestAnalysisPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<RequestAnalysisPayload> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(CobblemonTeamAnalysis.MOD_ID, "request_analysis"))

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RequestAnalysisPayload> =
            StreamCodec.of(::write, ::read)

        private fun write(buf: FriendlyByteBuf, payload: RequestAnalysisPayload) {
            buf.writeUtf(payload.presetName ?: "")
        }

        private fun read(buf: FriendlyByteBuf): RequestAnalysisPayload {
            val name = buf.readUtf()
            return RequestAnalysisPayload(if (name.isEmpty()) null else name)
        }
    }
}
