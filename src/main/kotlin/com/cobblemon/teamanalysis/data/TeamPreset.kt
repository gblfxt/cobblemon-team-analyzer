package com.cobblemon.teamanalysis.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * Represents a saved team preset.
 */
class TeamPreset(
    var name: String,
    val slots: MutableList<TeamSlot> = mutableListOf(),
    var createdAt: Long = System.currentTimeMillis(),
    var lastUsed: Long = System.currentTimeMillis()
) {
    /**
     * A slot in the team preset.
     */
    data class TeamSlot(
        val pokemonUUID: UUID?,
        val speciesId: ResourceLocation
    ) {
        companion object {
            fun fromPokemon(uuid: UUID, speciesId: ResourceLocation): TeamSlot {
                return TeamSlot(uuid, speciesId)
            }

            fun fromSpeciesOnly(speciesId: ResourceLocation): TeamSlot {
                return TeamSlot(null, speciesId)
            }

            fun fromJson(json: JsonObject): TeamSlot {
                val uuid = if (json.has("uuid")) UUID.fromString(json.get("uuid").asString) else null
                val speciesId = ResourceLocation.parse(json.get("species").asString)
                return TeamSlot(uuid, speciesId)
            }
        }

        fun toJson(): JsonObject {
            return JsonObject().apply {
                if (pokemonUUID != null) {
                    addProperty("uuid", pokemonUUID.toString())
                }
                addProperty("species", speciesId.toString())
            }
        }
    }

    fun addSlot(slot: TeamSlot) {
        if (slots.size < 6) {
            slots.add(slot)
        }
    }

    fun markUsed() {
        lastUsed = System.currentTimeMillis()
    }

    /**
     * Convert to share code format: [Pokemon:TeamName]species1,species2,...
     */
    fun toShareCode(): String {
        val speciesList = slots.joinToString(",") { it.speciesId.toString() }
        return "[Pokemon:$name]$speciesList"
    }

    /**
     * Serialize to JSON.
     */
    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("name", name)
            addProperty("createdAt", createdAt)
            addProperty("lastUsed", lastUsed)
            add("slots", JsonArray().apply {
                slots.forEach { add(it.toJson()) }
            })
        }
    }

    companion object {
        /**
         * Parse from share code format.
         */
        fun fromShareCode(code: String): TeamPreset? {
            val regex = Regex("""\[Pokemon:([^\]]+)\](.+)""")
            val match = regex.matchEntire(code) ?: return null

            val name = match.groupValues[1]
            val speciesStr = match.groupValues[2]

            val preset = TeamPreset(name)
            for (species in speciesStr.split(",")) {
                val trimmed = species.trim()
                if (trimmed.isNotEmpty()) {
                    try {
                        val speciesId = ResourceLocation.parse(trimmed)
                        preset.addSlot(TeamSlot.fromSpeciesOnly(speciesId))
                    } catch (e: Exception) {
                        // Skip invalid species
                    }
                }
            }

            return if (preset.slots.isEmpty()) null else preset
        }

        /**
         * Deserialize from JSON.
         */
        fun fromJson(json: JsonObject): TeamPreset {
            val name = json.get("name").asString
            val createdAt = json.get("createdAt")?.asLong ?: System.currentTimeMillis()
            val lastUsed = json.get("lastUsed")?.asLong ?: System.currentTimeMillis()

            val preset = TeamPreset(name, mutableListOf(), createdAt, lastUsed)

            val slotsArray = json.getAsJsonArray("slots")
            for (slotElement in slotsArray) {
                preset.slots.add(TeamSlot.fromJson(slotElement.asJsonObject))
            }

            return preset
        }
    }
}
