package com.cobblemon.teamanalysis.data

/**
 * Criteria for filtering and sorting Pokemon in the browser.
 */
data class FilterCriteria(
    val searchText: String = "",
    val types: Set<String> = emptySet(),      // Empty = all types
    val showShinyOnly: Boolean = false,
    val showPartyOnly: Boolean = false,
    val showPCOnly: Boolean = false,
    val minLevel: Int = 1,
    val maxLevel: Int = 100,
    val showLegendaryOnly: Boolean = false,
    val sortBy: SortOption = SortOption.POKEDEX,
    val sortAscending: Boolean = true
) {
    enum class SortOption {
        POKEDEX,
        LEVEL,
        NAME,
        IV_TOTAL,
        EV_TOTAL,
        RECENT      // By position (party first, then PC order)
    }

    /**
     * Check if a Pokemon matches this filter criteria.
     */
    fun matches(pokemon: PokemonSummary): Boolean {
        // Missing Pokemon always show (greyed out)
        if (pokemon.isMissing) return true

        // Search text (name or species)
        if (searchText.isNotBlank()) {
            val search = searchText.lowercase()
            val nameMatch = pokemon.displayName.lowercase().contains(search)
            val speciesMatch = pokemon.speciesId.lowercase().contains(search)
            if (!nameMatch && !speciesMatch) return false
        }

        // Type filter
        if (types.isNotEmpty()) {
            val pokemonTypes = listOfNotNull(pokemon.type1, pokemon.type2).map { it.lowercase() }
            if (types.none { it.lowercase() in pokemonTypes }) return false
        }

        // Shiny filter
        if (showShinyOnly && !pokemon.isShiny) return false

        // Location filter
        if (showPartyOnly && !pokemon.isInParty) return false
        if (showPCOnly && pokemon.isInParty) return false

        // Level range
        if (pokemon.level < minLevel || pokemon.level > maxLevel) return false

        // Legendary filter
        if (showLegendaryOnly && !pokemon.isLegendary && !pokemon.isUltraBeast) return false

        return true
    }

    /**
     * Get a comparator for sorting Pokemon.
     */
    fun getComparator(): Comparator<PokemonSummary> {
        val base: Comparator<PokemonSummary> = when (sortBy) {
            SortOption.POKEDEX -> compareBy { it.speciesId }
            SortOption.LEVEL -> compareBy { it.level }
            SortOption.NAME -> compareBy { it.displayName.lowercase() }
            SortOption.IV_TOTAL -> compareBy { it.ivTotal }
            SortOption.EV_TOTAL -> compareBy { it.evTotal }
            SortOption.RECENT -> compareBy<PokemonSummary> { !it.isInParty }.thenBy { it.speciesId }
        }

        // Missing Pokemon always at the end
        val withMissing = compareBy<PokemonSummary> { it.isMissing }.then(base)

        return if (sortAscending) withMissing else withMissing.reversed()
    }
}
