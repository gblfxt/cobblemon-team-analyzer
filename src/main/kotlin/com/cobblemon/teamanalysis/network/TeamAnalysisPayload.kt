package com.cobblemon.teamanalysis.network

import com.cobblemon.teamanalysis.CobblemonTeamAnalysis
import com.cobblemon.teamanalysis.analysis.TeamAnalyzer
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * Network payload to send team analysis data from server to client.
 */
data class TeamAnalysisPayload(
    val teamName: String,
    val pokemon: List<PokemonData>,
    val weaknessCounts: Map<String, Int>,
    val resistanceCounts: Map<String, Int>,
    val immunityCounts: Map<String, Int>,
    val criticalWeaknesses: List<String>,
    val uncoveredTypes: List<String>,
    val suggestions: List<String>,
    val presetNames: List<String>
) : CustomPacketPayload {

    data class PokemonData(
        val name: String,
        val type1: String,
        val type2: String?,
        val weaknesses: List<String>,
        val resistances: List<String>,
        val immunities: List<String>
    )

    override fun type(): CustomPacketPayload.Type<TeamAnalysisPayload> = TYPE

    fun toAnalysis(): TeamAnalyzer.TeamAnalysis {
        val pokemonInfos = pokemon.map { data ->
            TeamAnalyzer.PokemonInfo(data.name, data.type1, data.type2)
        }

        return TeamAnalyzer.TeamAnalysis(
            pokemon = pokemonInfos,
            weaknessCounts = weaknessCounts,
            resistanceCounts = resistanceCounts,
            immunityCounts = immunityCounts,
            criticalWeaknesses = criticalWeaknesses,
            uncoveredTypes = uncoveredTypes,
            suggestions = suggestions
        )
    }

    companion object {
        val TYPE: CustomPacketPayload.Type<TeamAnalysisPayload> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(CobblemonTeamAnalysis.MOD_ID, "team_analysis"))

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, TeamAnalysisPayload> =
            StreamCodec.of(::write, ::read)

        private fun write(buf: FriendlyByteBuf, payload: TeamAnalysisPayload) {
            buf.writeUtf(payload.teamName)

            // Pokemon list
            buf.writeVarInt(payload.pokemon.size)
            for (p in payload.pokemon) {
                buf.writeUtf(p.name)
                buf.writeUtf(p.type1)
                buf.writeUtf(p.type2 ?: "")
                buf.writeCollection(p.weaknesses) { b, s -> b.writeUtf(s) }
                buf.writeCollection(p.resistances) { b, s -> b.writeUtf(s) }
                buf.writeCollection(p.immunities) { b, s -> b.writeUtf(s) }
            }

            // Maps
            writeStringIntMap(buf, payload.weaknessCounts)
            writeStringIntMap(buf, payload.resistanceCounts)
            writeStringIntMap(buf, payload.immunityCounts)

            // Lists
            buf.writeCollection(payload.criticalWeaknesses) { b, s -> b.writeUtf(s) }
            buf.writeCollection(payload.uncoveredTypes) { b, s -> b.writeUtf(s) }
            buf.writeCollection(payload.suggestions) { b, s -> b.writeUtf(s) }
            buf.writeCollection(payload.presetNames) { b, s -> b.writeUtf(s) }
        }

        private fun read(buf: FriendlyByteBuf): TeamAnalysisPayload {
            val teamName = buf.readUtf()

            // Pokemon list
            val pokemonCount = buf.readVarInt()
            val pokemon = mutableListOf<PokemonData>()
            repeat(pokemonCount) {
                val name = buf.readUtf()
                val type1 = buf.readUtf()
                val type2Raw = buf.readUtf()
                val type2 = if (type2Raw.isEmpty()) null else type2Raw
                val weaknesses = buf.readList { it.readUtf() }
                val resistances = buf.readList { it.readUtf() }
                val immunities = buf.readList { it.readUtf() }
                pokemon.add(PokemonData(name, type1, type2, weaknesses, resistances, immunities))
            }

            // Maps
            val weaknessCounts = readStringIntMap(buf)
            val resistanceCounts = readStringIntMap(buf)
            val immunityCounts = readStringIntMap(buf)

            // Lists
            val criticalWeaknesses = buf.readList { it.readUtf() }
            val uncoveredTypes = buf.readList { it.readUtf() }
            val suggestions = buf.readList { it.readUtf() }
            val presetNames = buf.readList { it.readUtf() }

            return TeamAnalysisPayload(
                teamName, pokemon, weaknessCounts, resistanceCounts, immunityCounts,
                criticalWeaknesses, uncoveredTypes, suggestions, presetNames
            )
        }

        private fun writeStringIntMap(buf: FriendlyByteBuf, map: Map<String, Int>) {
            buf.writeVarInt(map.size)
            for ((key, value) in map) {
                buf.writeUtf(key)
                buf.writeVarInt(value)
            }
        }

        private fun readStringIntMap(buf: FriendlyByteBuf): Map<String, Int> {
            val size = buf.readVarInt()
            val map = mutableMapOf<String, Int>()
            repeat(size) {
                map[buf.readUtf()] = buf.readVarInt()
            }
            return map
        }

        fun fromAnalysis(analysis: TeamAnalyzer.TeamAnalysis, teamName: String, presetNames: List<String>): TeamAnalysisPayload {
            val pokemonData = analysis.pokemon.map { info ->
                PokemonData(
                    name = info.name,
                    type1 = info.type1,
                    type2 = info.type2,
                    weaknesses = info.weaknesses,
                    resistances = info.resistances,
                    immunities = info.immunities
                )
            }

            return TeamAnalysisPayload(
                teamName = teamName,
                pokemon = pokemonData,
                weaknessCounts = analysis.weaknessCounts,
                resistanceCounts = analysis.resistanceCounts,
                immunityCounts = analysis.immunityCounts,
                criticalWeaknesses = analysis.criticalWeaknesses,
                uncoveredTypes = analysis.uncoveredTypes,
                suggestions = analysis.suggestions,
                presetNames = presetNames
            )
        }
    }
}
