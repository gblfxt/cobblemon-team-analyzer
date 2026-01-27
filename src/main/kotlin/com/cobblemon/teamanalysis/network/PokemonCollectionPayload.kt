package com.cobblemon.teamanalysis.network

import com.cobblemon.teamanalysis.CobblemonTeamAnalysis
import com.cobblemon.teamanalysis.data.PokemonSummary
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * Server → Client payload containing the player's Pokemon collection.
 */
data class PokemonCollectionPayload(
    val pokemon: List<PokemonSummary>,
    val presetName: String?,  // If viewing a specific preset, null for full collection
    val forBuilder: Boolean = false  // True to open TeamBuilderScreen instead of browser
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<PokemonCollectionPayload>(
            ResourceLocation.fromNamespaceAndPath(CobblemonTeamAnalysis.MOD_ID, "pokemon_collection")
        )

        // Codec for PokemonSummary
        private val SUMMARY_CODEC: StreamCodec<ByteBuf, PokemonSummary> = object : StreamCodec<ByteBuf, PokemonSummary> {
            override fun decode(buf: ByteBuf): PokemonSummary {
                return PokemonSummary(
                    uuid = UUID(buf.readLong(), buf.readLong()),
                    speciesId = ByteBufCodecs.STRING_UTF8.decode(buf),
                    displayName = ByteBufCodecs.STRING_UTF8.decode(buf),
                    level = buf.readInt(),
                    type1 = ByteBufCodecs.STRING_UTF8.decode(buf),
                    type2 = ByteBufCodecs.STRING_UTF8.decode(buf).takeIf { it.isNotEmpty() },
                    isShiny = buf.readBoolean(),
                    isInParty = buf.readBoolean(),
                    gender = ByteBufCodecs.STRING_UTF8.decode(buf),
                    nature = ByteBufCodecs.STRING_UTF8.decode(buf),
                    ability = ByteBufCodecs.STRING_UTF8.decode(buf),
                    ivTotal = buf.readInt(),
                    evTotal = buf.readInt(),
                    ballType = ByteBufCodecs.STRING_UTF8.decode(buf),
                    isLegendary = buf.readBoolean(),
                    isUltraBeast = buf.readBoolean(),
                    canEvolve = buf.readBoolean()
                )
            }

            override fun encode(buf: ByteBuf, value: PokemonSummary) {
                buf.writeLong(value.uuid.mostSignificantBits)
                buf.writeLong(value.uuid.leastSignificantBits)
                ByteBufCodecs.STRING_UTF8.encode(buf, value.speciesId)
                ByteBufCodecs.STRING_UTF8.encode(buf, value.displayName)
                buf.writeInt(value.level)
                ByteBufCodecs.STRING_UTF8.encode(buf, value.type1)
                ByteBufCodecs.STRING_UTF8.encode(buf, value.type2 ?: "")
                buf.writeBoolean(value.isShiny)
                buf.writeBoolean(value.isInParty)
                ByteBufCodecs.STRING_UTF8.encode(buf, value.gender)
                ByteBufCodecs.STRING_UTF8.encode(buf, value.nature)
                ByteBufCodecs.STRING_UTF8.encode(buf, value.ability)
                buf.writeInt(value.ivTotal)
                buf.writeInt(value.evTotal)
                ByteBufCodecs.STRING_UTF8.encode(buf, value.ballType)
                buf.writeBoolean(value.isLegendary)
                buf.writeBoolean(value.isUltraBeast)
                buf.writeBoolean(value.canEvolve)
            }
        }

        val STREAM_CODEC: StreamCodec<ByteBuf, PokemonCollectionPayload> = object : StreamCodec<ByteBuf, PokemonCollectionPayload> {
            private val listCodec = SUMMARY_CODEC.apply(ByteBufCodecs.list())

            override fun decode(buf: ByteBuf): PokemonCollectionPayload {
                val pokemon = listCodec.decode(buf)
                val presetName = ByteBufCodecs.STRING_UTF8.decode(buf).takeIf { it.isNotEmpty() }
                val forBuilder = buf.readBoolean()
                return PokemonCollectionPayload(pokemon, presetName, forBuilder)
            }

            override fun encode(buf: ByteBuf, value: PokemonCollectionPayload) {
                listCodec.encode(buf, value.pokemon)
                ByteBufCodecs.STRING_UTF8.encode(buf, value.presetName ?: "")
                buf.writeBoolean(value.forBuilder)
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
