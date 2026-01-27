package com.cobblemon.teamanalysis.util

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore
import com.cobblemon.mod.common.api.storage.pc.PCStore
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.teamanalysis.data.PokemonSummary
import com.cobblemon.teamanalysis.data.TeamPreset
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Helper methods to interface with Cobblemon's API.
 */
object CobblemonHelper {

    /**
     * Get player's party store.
     */
    fun getParty(player: ServerPlayer): PlayerPartyStore {
        return Cobblemon.storage.getParty(player)
    }

    /**
     * Get player's PC store.
     */
    fun getPC(player: ServerPlayer): PCStore {
        return Cobblemon.storage.getPC(player)
    }

    /**
     * Create a preset from player's current party.
     */
    fun createPresetFromParty(player: ServerPlayer, name: String): TeamPreset? {
        val party = getParty(player)
        val preset = TeamPreset(name)

        // Use slots() or similar method to get pokemon list
        for (i in 0 until 6) {
            val pokemon = party.get(i)
            if (pokemon != null) {
                val speciesId = pokemon.species.resourceIdentifier
                val pokemonUUID = pokemon.uuid
                preset.addSlot(TeamPreset.TeamSlot.fromPokemon(pokemonUUID, speciesId))
            }
        }

        return if (preset.slots.isEmpty()) null else preset
    }

    /**
     * Load a preset to player's party (swapping from PC).
     * Returns true if at least one Pokemon was loaded.
     */
    fun loadPresetToParty(player: ServerPlayer, preset: TeamPreset): Boolean {
        val party = getParty(player)
        val pc = getPC(player)

        // Step 1: Find all Pokemon from the preset
        val presetPokemon = mutableListOf<Pokemon>()
        val missingSlots = mutableListOf<Int>()

        for ((index, slot) in preset.slots.withIndex()) {
            val uuid = slot.pokemonUUID
            if (uuid == null) {
                missingSlots.add(index)
                continue
            }

            val pokemon = findPokemon(player, uuid)
            if (pokemon != null) {
                presetPokemon.add(pokemon)
            } else {
                missingSlots.add(index)
            }
        }

        if (presetPokemon.isEmpty()) {
            return false
        }

        // Step 2: Move current party Pokemon to PC (except those staying)
        val stayingUuids = presetPokemon.map { it.uuid }.toSet()
        val partyToMove = mutableListOf<Pokemon>()

        for (i in 0 until 6) {
            val pokemon = party.get(i)
            if (pokemon != null && pokemon.uuid !in stayingUuids) {
                partyToMove.add(pokemon)
            }
        }

        // Move party Pokemon to PC
        for (pokemon in partyToMove) {
            party.remove(pokemon)
            pc.add(pokemon)
        }

        // Step 3: Move preset Pokemon from PC to party
        for (pokemon in presetPokemon) {
            // Check if already in party
            var inParty = false
            for (i in 0 until 6) {
                if (party.get(i)?.uuid == pokemon.uuid) {
                    inParty = true
                    break
                }
            }

            if (!inParty) {
                // Remove from PC and add to party
                for (boxNum in 0 until pc.boxes.size) {
                    val box = pc.boxes[boxNum]
                    for (slotNum in 0 until 30) {
                        val pcPokemon = box.get(slotNum)
                        if (pcPokemon?.uuid == pokemon.uuid) {
                            box.set(slotNum, null)
                            party.add(pokemon)
                            break
                        }
                    }
                }
            }
        }

        return true
    }

    /**
     * Get species display name from ID.
     */
    fun getSpeciesDisplayName(speciesId: ResourceLocation): String {
        val species = PokemonSpecies.getByIdentifier(speciesId)
        return species?.name ?: speciesId.path.replaceFirstChar { it.uppercase() }
    }

    /**
     * Get primary type of a species.
     */
    fun getPrimaryType(speciesId: ResourceLocation): String? {
        val species = PokemonSpecies.getByIdentifier(speciesId)
        return species?.primaryType?.name?.lowercase()
    }

    /**
     * Get secondary type of a species (null if single type).
     */
    fun getSecondaryType(speciesId: ResourceLocation): String? {
        val species = PokemonSpecies.getByIdentifier(speciesId)
        return species?.secondaryType?.name?.lowercase()
    }

    /**
     * Find a Pokemon by UUID in party or PC.
     */
    fun findPokemon(player: ServerPlayer, uuid: UUID): Pokemon? {
        val party = getParty(player)
        val pc = getPC(player)

        // Check party first
        for (i in 0 until 6) {
            val pokemon = party.get(i)
            if (pokemon?.uuid == uuid) return pokemon
        }

        // Check PC
        for (boxNum in 0 until pc.boxes.size) {
            val box = pc.boxes[boxNum]
            for (slotNum in 0 until 30) {
                val pokemon = box.get(slotNum)
                if (pokemon?.uuid == uuid) return pokemon
            }
        }

        return null
    }

    /**
     * Convert a Pokemon to a lightweight summary for the browser.
     */
    fun toSummary(pokemon: Pokemon, isInParty: Boolean): PokemonSummary {
        val species = pokemon.species
        val form = pokemon.form

        // Calculate IV and EV totals
        val ivTotal = Stats.PERMANENT.sumOf { pokemon.ivs[it] ?: 0 }
        val evTotal = Stats.PERMANENT.sumOf { pokemon.evs[it] ?: 0 }

        // Check if can evolve (has any evolution)
        val canEvolve = species.evolutions.isNotEmpty()

        // Get gender string
        val gender = when {
            pokemon.gender.name.equals("MALE", ignoreCase = true) -> "male"
            pokemon.gender.name.equals("FEMALE", ignoreCase = true) -> "female"
            else -> "genderless"
        }

        return PokemonSummary(
            uuid = pokemon.uuid,
            speciesId = species.resourceIdentifier.toString(),
            displayName = pokemon.getDisplayName().string,
            level = pokemon.level,
            type1 = form.primaryType.name.lowercase(),
            type2 = form.secondaryType?.name?.lowercase(),
            isShiny = pokemon.shiny,
            isInParty = isInParty,
            gender = gender,
            nature = pokemon.nature.name.path,
            ability = pokemon.ability.name,
            ivTotal = ivTotal,
            evTotal = evTotal,
            ballType = pokemon.caughtBall.name.path,
            isLegendary = species.labels.any { it.equals("legendary", ignoreCase = true) || it.equals("mythical", ignoreCase = true) },
            isUltraBeast = species.labels.any { it.equals("ultra_beast", ignoreCase = true) },
            canEvolve = canEvolve
        )
    }

    /**
     * Get all Pokemon from player's party and PC as summaries.
     */
    fun getAllPokemonSummaries(player: ServerPlayer): List<PokemonSummary> {
        val party = getParty(player)
        val pc = getPC(player)
        val summaries = mutableListOf<PokemonSummary>()

        // Party Pokemon first
        for (i in 0 until 6) {
            val pokemon = party.get(i)
            if (pokemon != null) {
                summaries.add(toSummary(pokemon, isInParty = true))
            }
        }

        // Then PC Pokemon
        for (boxNum in 0 until pc.boxes.size) {
            val box = pc.boxes[boxNum]
            for (slotNum in 0 until 30) {
                val pokemon = box.get(slotNum)
                if (pokemon != null) {
                    summaries.add(toSummary(pokemon, isInParty = false))
                }
            }
        }

        return summaries
    }

    /**
     * Get Pokemon summaries for a preset, marking missing ones.
     */
    fun getPresetPokemonSummaries(player: ServerPlayer, preset: TeamPreset): List<PokemonSummary> {
        val summaries = mutableListOf<PokemonSummary>()

        for (slot in preset.slots) {
            val uuid = slot.pokemonUUID
            if (uuid != null) {
                val pokemon = findPokemon(player, uuid)
                if (pokemon != null) {
                    // Found - check if in party
                    val party = getParty(player)
                    var inParty = false
                    for (i in 0 until 6) {
                        if (party.get(i)?.uuid == uuid) {
                            inParty = true
                            break
                        }
                    }
                    summaries.add(toSummary(pokemon, inParty))
                } else {
                    // Missing - create placeholder
                    val displayName = getSpeciesDisplayName(slot.speciesId)
                    summaries.add(PokemonSummary.missing(uuid, slot.speciesId.toString(), displayName))
                }
            } else {
                // Species-only slot (from import) - mark as missing
                val displayName = getSpeciesDisplayName(slot.speciesId)
                summaries.add(PokemonSummary.missing(UUID.randomUUID(), slot.speciesId.toString(), displayName))
            }
        }

        return summaries
    }
}
