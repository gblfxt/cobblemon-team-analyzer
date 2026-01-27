package com.cobblemon.teamanalysis.data

import java.util.UUID

/**
 * Lightweight summary of a Pokemon for the browser.
 * Sent from server to client for display.
 */
data class PokemonSummary(
    val uuid: UUID,
    val speciesId: String,      // e.g. "cobblemon:pikachu"
    val displayName: String,    // Species name or nickname
    val level: Int,
    val type1: String,
    val type2: String?,         // null if single type
    val isShiny: Boolean,
    val isInParty: Boolean,     // true if in party, false if in PC
    val gender: String,         // "male", "female", "genderless"
    val nature: String,
    val ability: String,
    val ivTotal: Int,           // Sum of all IVs (0-186)
    val evTotal: Int,           // Sum of all EVs (0-510)
    val ballType: String,       // e.g. "poke_ball"
    val isLegendary: Boolean,
    val isUltraBeast: Boolean,
    val canEvolve: Boolean
) {
    companion object {
        /**
         * Create a "missing" placeholder for Pokemon that no longer exist.
         */
        fun missing(uuid: UUID, speciesId: String, displayName: String): PokemonSummary {
            return PokemonSummary(
                uuid = uuid,
                speciesId = speciesId,
                displayName = displayName,
                level = 0,
                type1 = "unknown",
                type2 = null,
                isShiny = false,
                isInParty = false,
                gender = "unknown",
                nature = "unknown",
                ability = "unknown",
                ivTotal = 0,
                evTotal = 0,
                ballType = "unknown",
                isLegendary = false,
                isUltraBeast = false,
                canEvolve = false
            )
        }
    }

    val isMissing: Boolean get() = level == 0 && type1 == "unknown"
}
