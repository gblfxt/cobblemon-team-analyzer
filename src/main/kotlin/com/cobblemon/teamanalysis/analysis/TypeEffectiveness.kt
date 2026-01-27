package com.cobblemon.teamanalysis.analysis

/**
 * Pokemon type effectiveness chart and calculations.
 */
object TypeEffectiveness {

    val ALL_TYPES = listOf(
        "normal", "fire", "water", "electric", "grass", "ice",
        "fighting", "poison", "ground", "flying", "psychic", "bug",
        "rock", "ghost", "dragon", "dark", "steel", "fairy"
    )

    // Type chart: attacker -> defender -> effectiveness
    // 2.0 = super effective, 0.5 = not very effective, 0.0 = immune
    private val TYPE_CHART: Map<String, Map<String, Double>> = mapOf(
        "normal" to mapOf("rock" to 0.5, "ghost" to 0.0, "steel" to 0.5),
        "fire" to mapOf("fire" to 0.5, "water" to 0.5, "grass" to 2.0, "ice" to 2.0, "bug" to 2.0, "rock" to 0.5, "dragon" to 0.5, "steel" to 2.0),
        "water" to mapOf("fire" to 2.0, "water" to 0.5, "grass" to 0.5, "ground" to 2.0, "rock" to 2.0, "dragon" to 0.5),
        "electric" to mapOf("water" to 2.0, "electric" to 0.5, "grass" to 0.5, "ground" to 0.0, "flying" to 2.0, "dragon" to 0.5),
        "grass" to mapOf("fire" to 0.5, "water" to 2.0, "grass" to 0.5, "poison" to 0.5, "ground" to 2.0, "flying" to 0.5, "bug" to 0.5, "rock" to 2.0, "dragon" to 0.5, "steel" to 0.5),
        "ice" to mapOf("fire" to 0.5, "water" to 0.5, "grass" to 2.0, "ice" to 0.5, "ground" to 2.0, "flying" to 2.0, "dragon" to 2.0, "steel" to 0.5),
        "fighting" to mapOf("normal" to 2.0, "ice" to 2.0, "poison" to 0.5, "flying" to 0.5, "psychic" to 0.5, "bug" to 0.5, "rock" to 2.0, "ghost" to 0.0, "dark" to 2.0, "steel" to 2.0, "fairy" to 0.5),
        "poison" to mapOf("grass" to 2.0, "poison" to 0.5, "ground" to 0.5, "rock" to 0.5, "ghost" to 0.5, "steel" to 0.0, "fairy" to 2.0),
        "ground" to mapOf("fire" to 2.0, "electric" to 2.0, "grass" to 0.5, "poison" to 2.0, "flying" to 0.0, "bug" to 0.5, "rock" to 2.0, "steel" to 2.0),
        "flying" to mapOf("electric" to 0.5, "grass" to 2.0, "fighting" to 2.0, "bug" to 2.0, "rock" to 0.5, "steel" to 0.5),
        "psychic" to mapOf("fighting" to 2.0, "poison" to 2.0, "psychic" to 0.5, "dark" to 0.0, "steel" to 0.5),
        "bug" to mapOf("fire" to 0.5, "grass" to 2.0, "fighting" to 0.5, "poison" to 0.5, "flying" to 0.5, "psychic" to 2.0, "ghost" to 0.5, "dark" to 2.0, "steel" to 0.5, "fairy" to 0.5),
        "rock" to mapOf("fire" to 2.0, "ice" to 2.0, "fighting" to 0.5, "ground" to 0.5, "flying" to 2.0, "bug" to 2.0, "steel" to 0.5),
        "ghost" to mapOf("normal" to 0.0, "psychic" to 2.0, "ghost" to 2.0, "dark" to 0.5),
        "dragon" to mapOf("dragon" to 2.0, "steel" to 0.5, "fairy" to 0.0),
        "dark" to mapOf("fighting" to 0.5, "psychic" to 2.0, "ghost" to 2.0, "dark" to 0.5, "fairy" to 0.5),
        "steel" to mapOf("fire" to 0.5, "water" to 0.5, "electric" to 0.5, "ice" to 2.0, "rock" to 2.0, "steel" to 0.5, "fairy" to 2.0),
        "fairy" to mapOf("fire" to 0.5, "fighting" to 2.0, "poison" to 0.5, "dragon" to 2.0, "dark" to 2.0, "steel" to 0.5)
    )

    /**
     * Get effectiveness of attack type vs single defense type.
     */
    fun getEffectiveness(attackType: String, defenseType: String): Double {
        val attackLower = attackType.lowercase()
        val defenseLower = defenseType.lowercase()
        return TYPE_CHART[attackLower]?.get(defenseLower) ?: 1.0
    }

    /**
     * Get effectiveness of attack type vs dual type defender.
     */
    fun getEffectiveness(attackType: String, defenseType1: String, defenseType2: String?): Double {
        val mult1 = getEffectiveness(attackType, defenseType1)
        if (defenseType2.isNullOrEmpty()) return mult1
        val mult2 = getEffectiveness(attackType, defenseType2)
        return mult1 * mult2
    }

    /**
     * Get all types that a Pokemon with given types is weak to (>1x damage).
     */
    fun getWeaknesses(type1: String, type2: String?): List<String> {
        return ALL_TYPES.filter { attackType ->
            getEffectiveness(attackType, type1, type2) > 1.0
        }
    }

    /**
     * Get all types that a Pokemon with given types resists (<1x damage, not immune).
     */
    fun getResistances(type1: String, type2: String?): List<String> {
        return ALL_TYPES.filter { attackType ->
            val eff = getEffectiveness(attackType, type1, type2)
            eff < 1.0 && eff > 0.0
        }
    }

    /**
     * Get all types that a Pokemon with given types is immune to (0x damage).
     */
    fun getImmunities(type1: String, type2: String?): List<String> {
        return ALL_TYPES.filter { attackType ->
            getEffectiveness(attackType, type1, type2) == 0.0
        }
    }

    /**
     * Format type name with proper capitalization.
     */
    fun formatType(type: String): String {
        return type.replaceFirstChar { it.uppercase() }
    }
}
