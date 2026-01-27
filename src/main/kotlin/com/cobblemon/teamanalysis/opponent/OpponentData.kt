package com.cobblemon.teamanalysis.opponent

/**
 * Represents an opponent (trainer or type-based challenge) that can be countered.
 */
data class OpponentData(
    val id: String,
    val displayName: String,
    val types: List<String>,
    val source: OpponentSource,
    val pokemonSpecies: List<String> = emptyList() // Species IDs if known (from RCT)
) {
    enum class OpponentSource {
        RCT_TRAINER,    // From RCT mod trainer data
        TYPE_CHALLENGE  // Type-based challenge (e.g., "Water Gym")
    }
}

/**
 * Predefined type challenges for when RCT is not available or as additional options.
 */
object TypeChallenges {

    val TYPE_GYMS: List<OpponentData> = listOf(
        OpponentData("gym_normal", "Normal Type Gym", listOf("normal"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("gym_fire", "Fire Type Gym", listOf("fire"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("gym_water", "Water Type Gym", listOf("water"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("gym_electric", "Electric Type Gym", listOf("electric"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("gym_grass", "Grass Type Gym", listOf("grass"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("gym_ice", "Ice Type Gym", listOf("ice"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("gym_fighting", "Fighting Type Gym", listOf("fighting"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("gym_poison", "Poison Type Gym", listOf("poison"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("gym_ground", "Ground Type Gym", listOf("ground"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("gym_flying", "Flying Type Gym", listOf("flying"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("gym_psychic", "Psychic Type Gym", listOf("psychic"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("gym_bug", "Bug Type Gym", listOf("bug"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("gym_rock", "Rock Type Gym", listOf("rock"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("gym_ghost", "Ghost Type Gym", listOf("ghost"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("gym_dragon", "Dragon Type Gym", listOf("dragon"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("gym_dark", "Dark Type Gym", listOf("dark"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("gym_steel", "Steel Type Gym", listOf("steel"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("gym_fairy", "Fairy Type Gym", listOf("fairy"), OpponentData.OpponentSource.TYPE_CHALLENGE)
    )

    val DUAL_TYPE_ELITES: List<OpponentData> = listOf(
        OpponentData("elite_dragon_flying", "Dragon/Flying Elite", listOf("dragon", "flying"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("elite_psychic_fairy", "Psychic/Fairy Elite", listOf("psychic", "fairy"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("elite_dark_ghost", "Dark/Ghost Elite", listOf("dark", "ghost"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("elite_steel_fighting", "Steel/Fighting Elite", listOf("steel", "fighting"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("elite_fire_fighting", "Fire/Fighting Elite", listOf("fire", "fighting"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("elite_water_ice", "Water/Ice Elite", listOf("water", "ice"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("elite_grass_poison", "Grass/Poison Elite", listOf("grass", "poison"), OpponentData.OpponentSource.TYPE_CHALLENGE),
        OpponentData("elite_ground_rock", "Ground/Rock Elite", listOf("ground", "rock"), OpponentData.OpponentSource.TYPE_CHALLENGE)
    )

    fun getAll(): List<OpponentData> = TYPE_GYMS + DUAL_TYPE_ELITES
}
