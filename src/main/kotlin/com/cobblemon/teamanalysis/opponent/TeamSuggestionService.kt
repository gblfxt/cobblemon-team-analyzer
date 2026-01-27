package com.cobblemon.teamanalysis.opponent

import com.cobblemon.teamanalysis.analysis.TypeEffectiveness
import com.cobblemon.teamanalysis.data.PokemonSummary

/**
 * Service that suggests optimal Pokemon for countering specific opponents.
 */
object TeamSuggestionService {

    /**
     * Score data for a Pokemon's effectiveness against an opponent.
     */
    data class PokemonScore(
        val pokemon: PokemonSummary,
        val offensiveScore: Double,  // How well it attacks the opponent's types
        val defensiveScore: Double,  // How well it resists the opponent's types
        val totalScore: Double,
        val reasons: List<String>
    )

    /**
     * Calculate how effective a Pokemon is against a given opponent.
     */
    fun scorePokemonAgainstOpponent(pokemon: PokemonSummary, opponent: OpponentData): PokemonScore {
        var offensiveScore = 0.0
        var defensiveScore = 0.0
        val reasons = mutableListOf<String>()

        val opponentTypes = opponent.types.map { it.lowercase() }

        // Calculate offensive score - how well this Pokemon's types hit the opponent
        for (opponentType in opponentTypes) {
            // STAB attacks
            val eff1 = TypeEffectiveness.getEffectiveness(pokemon.type1, opponentType)
            if (eff1 >= 2.0) {
                offensiveScore += 2.0
                reasons.add("${formatType(pokemon.type1)} is super effective vs ${formatType(opponentType)}")
            } else if (eff1 == 0.0) {
                offensiveScore -= 0.5
            }

            if (pokemon.type2 != null) {
                val eff2 = TypeEffectiveness.getEffectiveness(pokemon.type2, opponentType)
                if (eff2 >= 2.0) {
                    offensiveScore += 2.0
                    if (!reasons.any { it.contains(pokemon.type2!!) }) {
                        reasons.add("${formatType(pokemon.type2)} is super effective vs ${formatType(opponentType)}")
                    }
                } else if (eff2 == 0.0) {
                    offensiveScore -= 0.5
                }
            }
        }

        // Calculate defensive score - how well this Pokemon resists opponent's attacks
        for (opponentType in opponentTypes) {
            val effAgainstUs = TypeEffectiveness.getEffectiveness(opponentType, pokemon.type1, pokemon.type2)

            when {
                effAgainstUs == 0.0 -> {
                    defensiveScore += 3.0
                    reasons.add("Immune to ${formatType(opponentType)}")
                }
                effAgainstUs < 1.0 -> {
                    defensiveScore += 1.5
                    reasons.add("Resists ${formatType(opponentType)}")
                }
                effAgainstUs > 1.0 -> {
                    defensiveScore -= 1.0 * effAgainstUs
                    if (effAgainstUs >= 4.0) {
                        reasons.add("4x weak to ${formatType(opponentType)}!")
                    }
                }
            }
        }

        // Bonus for high level
        val levelBonus = (pokemon.level - 50) / 100.0
        offensiveScore += levelBonus

        // Bonus for good IVs
        val ivBonus = (pokemon.ivTotal - 93) / 186.0
        offensiveScore += ivBonus * 0.5

        val totalScore = offensiveScore + defensiveScore

        return PokemonScore(
            pokemon = pokemon,
            offensiveScore = offensiveScore,
            defensiveScore = defensiveScore,
            totalScore = totalScore,
            reasons = reasons.distinct().take(4)
        )
    }

    /**
     * Get suggested Pokemon from a collection, ranked by effectiveness against opponent.
     * Returns top N Pokemon, excluding those already in team.
     */
    fun getSuggestedPokemon(
        availablePokemon: List<PokemonSummary>,
        opponent: OpponentData,
        currentTeamUuids: Set<java.util.UUID>,
        maxSuggestions: Int = 12
    ): List<PokemonScore> {
        return availablePokemon
            .filter { it.uuid !in currentTeamUuids }
            .filter { !it.isMissing }
            .map { scorePokemonAgainstOpponent(it, opponent) }
            .filter { it.totalScore > 0 }
            .sortedByDescending { it.totalScore }
            .take(maxSuggestions)
    }

    /**
     * Get the best types to use against an opponent's types.
     */
    fun getBestCounterTypes(opponentTypes: List<String>): List<String> {
        val typeScores = mutableMapOf<String, Double>()

        for (attackType in TypeEffectiveness.ALL_TYPES) {
            var score = 0.0
            for (opponentType in opponentTypes) {
                val eff = TypeEffectiveness.getEffectiveness(attackType, opponentType.lowercase())
                if (eff >= 2.0) score += 2.0
                else if (eff < 1.0 && eff > 0.0) score -= 0.5
                else if (eff == 0.0) score -= 2.0
            }
            typeScores[attackType] = score
        }

        return typeScores.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key }
    }

    /**
     * Get types that the opponent is weak to.
     */
    fun getOpponentWeaknesses(opponentTypes: List<String>): List<String> {
        return TypeEffectiveness.ALL_TYPES.filter { attackType ->
            opponentTypes.all { defType ->
                TypeEffectiveness.getEffectiveness(attackType, defType.lowercase()) >= 1.0
            } && opponentTypes.any { defType ->
                TypeEffectiveness.getEffectiveness(attackType, defType.lowercase()) >= 2.0
            }
        }
    }

    /**
     * Build an optimal team of up to 6 Pokemon against an opponent.
     * Uses a greedy algorithm to maximize type coverage while minimizing shared weaknesses.
     */
    fun buildOptimalTeam(
        availablePokemon: List<PokemonSummary>,
        opponent: OpponentData,
        teamSize: Int = 6
    ): List<PokemonSummary> {
        val candidates = availablePokemon
            .filter { !it.isMissing }
            .map { scorePokemonAgainstOpponent(it, opponent) }
            .filter { it.totalScore > 0 }
            .sortedByDescending { it.totalScore }

        if (candidates.isEmpty()) {
            return emptyList()
        }

        val team = mutableListOf<PokemonSummary>()
        val usedTypes = mutableSetOf<String>()
        val teamWeaknesses = mutableMapOf<String, Int>()

        for (candidate in candidates) {
            if (team.size >= teamSize) break

            val pokemon = candidate.pokemon

            // Check if this Pokemon adds value (new types or fills coverage)
            val newTypes = mutableListOf<String>()
            if (pokemon.type1.lowercase() !in usedTypes) {
                newTypes.add(pokemon.type1.lowercase())
            }
            if (pokemon.type2 != null && pokemon.type2.lowercase() !in usedTypes) {
                newTypes.add(pokemon.type2.lowercase())
            }

            // Calculate weakness contribution
            val weaknesses = TypeEffectiveness.getWeaknesses(pokemon.type1, pokemon.type2)
            val wouldAddTooManyWeaknesses = weaknesses.count { teamWeaknesses.getOrDefault(it, 0) >= 2 } > 0

            // Accept if it scores well and doesn't stack too many weaknesses
            if (candidate.totalScore > 1.0 || (newTypes.isNotEmpty() && !wouldAddTooManyWeaknesses)) {
                team.add(pokemon)
                usedTypes.add(pokemon.type1.lowercase())
                if (pokemon.type2 != null) {
                    usedTypes.add(pokemon.type2.lowercase())
                }
                for (weakness in weaknesses) {
                    teamWeaknesses.merge(weakness, 1, Int::plus)
                }
            }
        }

        return team
    }

    private fun formatType(type: String): String = TypeEffectiveness.formatType(type)
}
