package com.cobblemon.teamanalysis.analysis

import com.cobblemon.teamanalysis.util.CobblemonHelper
import net.minecraft.resources.ResourceLocation

/**
 * Analyzes team composition for weaknesses, resistances, and coverage.
 */
object TeamAnalyzer {

    /**
     * Info about a single Pokemon in the analysis.
     */
    data class PokemonInfo(
        val name: String,
        val type1: String,
        val type2: String?,
        val weaknesses: List<String> = TypeEffectiveness.getWeaknesses(type1, type2),
        val resistances: List<String> = TypeEffectiveness.getResistances(type1, type2),
        val immunities: List<String> = TypeEffectiveness.getImmunities(type1, type2)
    ) {
        fun getTypeString(): String {
            return if (type2.isNullOrEmpty()) {
                TypeEffectiveness.formatType(type1)
            } else {
                "${TypeEffectiveness.formatType(type1)}/${TypeEffectiveness.formatType(type2)}"
            }
        }
    }

    /**
     * Result of analyzing a team.
     */
    data class TeamAnalysis(
        val pokemon: List<PokemonInfo>,
        val weaknessCounts: Map<String, Int>,
        val resistanceCounts: Map<String, Int>,
        val immunityCounts: Map<String, Int>,
        val criticalWeaknesses: List<String>,
        val uncoveredTypes: List<String>,
        val suggestions: List<String>
    ) {
        val teamSize: Int get() = pokemon.size
    }

    /**
     * Analyze a team given a list of species IDs.
     */
    fun analyze(speciesIds: List<ResourceLocation>): TeamAnalysis {
        val pokemonList = mutableListOf<PokemonInfo>()

        // Gather Pokemon info
        for (speciesId in speciesIds) {
            val name = CobblemonHelper.getSpeciesDisplayName(speciesId)
            val type1 = CobblemonHelper.getPrimaryType(speciesId)
            val type2 = CobblemonHelper.getSecondaryType(speciesId)

            if (type1 != null) {
                pokemonList.add(PokemonInfo(name, type1, type2))
            }
        }

        if (pokemonList.isEmpty()) {
            return TeamAnalysis(
                pokemon = pokemonList,
                weaknessCounts = emptyMap(),
                resistanceCounts = emptyMap(),
                immunityCounts = emptyMap(),
                criticalWeaknesses = emptyList(),
                uncoveredTypes = TypeEffectiveness.ALL_TYPES,
                suggestions = listOf("No Pokemon to analyze!")
            )
        }

        // Count weaknesses, resistances, immunities across team
        val weaknessCounts = mutableMapOf<String, Int>()
        val resistanceCounts = mutableMapOf<String, Int>()
        val immunityCounts = mutableMapOf<String, Int>()

        for (type in TypeEffectiveness.ALL_TYPES) {
            weaknessCounts[type] = 0
            resistanceCounts[type] = 0
            immunityCounts[type] = 0
        }

        for (pokemon in pokemonList) {
            for (weakness in pokemon.weaknesses) {
                weaknessCounts.merge(weakness, 1, Int::plus)
            }
            for (resistance in pokemon.resistances) {
                resistanceCounts.merge(resistance, 1, Int::plus)
            }
            for (immunity in pokemon.immunities) {
                immunityCounts.merge(immunity, 1, Int::plus)
            }
        }

        // Find critical weaknesses (3+ Pokemon weak to same type)
        val criticalWeaknesses = weaknessCounts.entries
            .filter { it.value >= 3 }
            .sortedByDescending { it.value }
            .map { it.key }

        // Find offensive coverage gaps
        val coveredTypes = mutableSetOf<String>()
        for (pokemon in pokemonList) {
            for (defenderType in TypeEffectiveness.ALL_TYPES) {
                val eff1 = TypeEffectiveness.getEffectiveness(pokemon.type1, defenderType)
                if (eff1 >= 2.0) {
                    coveredTypes.add(defenderType)
                }
                if (pokemon.type2 != null) {
                    val eff2 = TypeEffectiveness.getEffectiveness(pokemon.type2, defenderType)
                    if (eff2 >= 2.0) {
                        coveredTypes.add(defenderType)
                    }
                }
            }
        }

        val uncoveredTypes = TypeEffectiveness.ALL_TYPES.filter { it !in coveredTypes }

        // Generate suggestions
        val suggestions = generateSuggestions(
            pokemonList, criticalWeaknesses, uncoveredTypes, weaknessCounts, resistanceCounts, immunityCounts
        )

        return TeamAnalysis(
            pokemon = pokemonList,
            weaknessCounts = weaknessCounts,
            resistanceCounts = resistanceCounts,
            immunityCounts = immunityCounts,
            criticalWeaknesses = criticalWeaknesses,
            uncoveredTypes = uncoveredTypes,
            suggestions = suggestions
        )
    }

    private fun generateSuggestions(
        pokemon: List<PokemonInfo>,
        criticalWeaknesses: List<String>,
        uncoveredTypes: List<String>,
        weaknessCounts: Map<String, Int>,
        resistanceCounts: Map<String, Int>,
        immunityCounts: Map<String, Int>
    ): List<String> {
        val suggestions = mutableListOf<String>()

        // Critical weakness warnings
        for (weakness in criticalWeaknesses) {
            val count = weaknessCounts[weakness] ?: 0
            val formatted = TypeEffectiveness.formatType(weakness)
            val counterSuggestion = getCounterSuggestion(weakness)
            suggestions.add("$count Pokemon weak to $formatted! $counterSuggestion")
        }

        // Coverage gaps
        if (uncoveredTypes.isNotEmpty() && uncoveredTypes.size <= 5) {
            val uncovered = uncoveredTypes.take(5).joinToString(", ") { TypeEffectiveness.formatType(it) }
            suggestions.add("No super-effective coverage against: $uncovered")
        } else if (uncoveredTypes.size > 5) {
            suggestions.add("Missing coverage against ${uncoveredTypes.size} types - consider more type diversity")
        }

        // Check for type redundancy
        val typeCounts = mutableMapOf<String, Int>()
        for (p in pokemon) {
            typeCounts.merge(p.type1, 1, Int::plus)
            if (p.type2 != null) {
                typeCounts.merge(p.type2, 1, Int::plus)
            }
        }

        for ((type, count) in typeCounts) {
            if (count >= 3) {
                suggestions.add("Heavy ${TypeEffectiveness.formatType(type)} concentration ($count Pokemon) - shared weaknesses!")
            }
        }

        // Positive feedback if team is balanced
        if (criticalWeaknesses.isEmpty() && uncoveredTypes.size <= 3) {
            suggestions.add("Good type balance!")
        }

        return suggestions
    }

    private fun getCounterSuggestion(weakness: String): String {
        val counters = mapOf(
            "fire" to listOf("Water", "Rock"),
            "water" to listOf("Grass", "Electric"),
            "electric" to listOf("Ground"),
            "grass" to listOf("Fire", "Flying", "Bug"),
            "ice" to listOf("Fire", "Fighting", "Steel"),
            "fighting" to listOf("Flying", "Psychic", "Fairy"),
            "poison" to listOf("Ground", "Psychic"),
            "ground" to listOf("Water", "Grass", "Ice"),
            "flying" to listOf("Electric", "Ice", "Rock"),
            "psychic" to listOf("Bug", "Ghost", "Dark"),
            "bug" to listOf("Fire", "Flying", "Rock"),
            "rock" to listOf("Water", "Grass", "Fighting"),
            "ghost" to listOf("Ghost", "Dark"),
            "dragon" to listOf("Ice", "Dragon", "Fairy"),
            "dark" to listOf("Fighting", "Bug", "Fairy"),
            "steel" to listOf("Fire", "Fighting", "Ground"),
            "fairy" to listOf("Poison", "Steel"),
            "normal" to listOf("Fighting")
        )

        val suggestions = counters[weakness.lowercase()] ?: return ""
        return "Consider adding: ${suggestions.take(2).joinToString("/")}"
    }
}
