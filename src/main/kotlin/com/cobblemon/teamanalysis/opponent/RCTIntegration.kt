package com.cobblemon.teamanalysis.opponent

import com.cobblemon.teamanalysis.CobblemonTeamAnalysis
import net.neoforged.fml.ModList

/**
 * Integration with Radical Cobblemon Trainers (RCT) mod.
 * Provides trainer data when RCT is installed, gracefully degrades when not.
 */
object RCTIntegration {

    private const val RCT_MOD_ID = "rctmod"
    private const val RCT_API_MOD_ID = "rctapi"

    private var cachedTrainers: List<OpponentData>? = null
    private var rctAvailable: Boolean? = null

    /**
     * Check if RCT mod is available.
     */
    fun isRCTAvailable(): Boolean {
        if (rctAvailable == null) {
            rctAvailable = try {
                ModList.get().isLoaded(RCT_MOD_ID) || ModList.get().isLoaded(RCT_API_MOD_ID)
            } catch (e: Exception) {
                false
            }
            CobblemonTeamAnalysis.LOGGER.info("RCT integration available: $rctAvailable")
        }
        return rctAvailable!!
    }

    /**
     * Get trainers from RCT if available.
     * Returns empty list if RCT is not installed or if loading fails.
     */
    fun getRCTTrainers(): List<OpponentData> {
        if (!isRCTAvailable()) {
            return emptyList()
        }

        if (cachedTrainers != null) {
            return cachedTrainers!!
        }

        cachedTrainers = try {
            loadTrainersFromRCT()
        } catch (e: Exception) {
            CobblemonTeamAnalysis.LOGGER.warn("Failed to load RCT trainers: ${e.message}")
            emptyList()
        }

        return cachedTrainers!!
    }

    /**
     * Attempt to load trainer data from RCT API.
     * Uses reflection to avoid hard dependency on RCT classes.
     */
    private fun loadTrainersFromRCT(): List<OpponentData> {
        val trainers = mutableListOf<OpponentData>()

        try {
            // Get RCTApi class
            val rctApiClass = Class.forName("com.gitlab.srcmc.rctapi.api.RCTApi")

            // Get the DEFAULT_INSTANCE field
            val instanceField = rctApiClass.getDeclaredField("DEFAULT_INSTANCE")
            instanceField.isAccessible = true
            val rctApi = instanceField.get(null)

            if (rctApi == null) {
                CobblemonTeamAnalysis.LOGGER.warn("RCT API instance is null")
                return emptyList()
            }

            // Get TrainerRegistry
            val getRegistryMethod = rctApiClass.getMethod("getTrainerRegistry")
            val registry = getRegistryMethod.invoke(rctApi) ?: return emptyList()

            // Get all trainer IDs
            val registryClass = registry.javaClass
            val getIdsMethod = registryClass.getMethod("getIds")
            @Suppress("UNCHECKED_CAST")
            val trainerIds = getIdsMethod.invoke(registry) as? Collection<String> ?: return emptyList()

            CobblemonTeamAnalysis.LOGGER.info("Found ${trainerIds.size} RCT trainers")

            // Get trainer details for each ID
            val getByIdMethod = registryClass.getMethod("getById", String::class.java)

            for (trainerId in trainerIds) {
                try {
                    val trainer = getByIdMethod.invoke(registry, trainerId) ?: continue
                    val trainerData = extractTrainerData(trainerId, trainer)
                    if (trainerData != null) {
                        trainers.add(trainerData)
                    }
                } catch (e: Exception) {
                    CobblemonTeamAnalysis.LOGGER.debug("Failed to load trainer $trainerId: ${e.message}")
                }
            }

        } catch (e: ClassNotFoundException) {
            CobblemonTeamAnalysis.LOGGER.info("RCT API classes not found - RCT may not be properly installed")
        } catch (e: Exception) {
            CobblemonTeamAnalysis.LOGGER.warn("Error loading RCT trainers: ${e.message}")
        }

        return trainers
    }

    /**
     * Extract trainer data from RCT Trainer object using reflection.
     */
    private fun extractTrainerData(trainerId: String, trainer: Any): OpponentData? {
        try {
            val trainerClass = trainer.javaClass

            // Get trainer name
            val getNameMethod = trainerClass.getMethod("getName")
            val nameObj = getNameMethod.invoke(trainer)
            val displayName = extractTextContent(nameObj) ?: trainerId

            // Get trainer's team to determine types
            val getTeamMethod = trainerClass.getMethod("getTeam")
            val team = getTeamMethod.invoke(trainer) as? Array<*>

            val types = mutableSetOf<String>()
            val speciesList = mutableListOf<String>()

            if (team != null) {
                for (pokemon in team) {
                    if (pokemon == null) continue
                    try {
                        val pokemonTypes = extractPokemonTypes(pokemon)
                        types.addAll(pokemonTypes)

                        val speciesId = extractPokemonSpecies(pokemon)
                        if (speciesId != null) {
                            speciesList.add(speciesId)
                        }
                    } catch (e: Exception) {
                        // Skip this Pokemon if extraction fails
                    }
                }
            }

            if (types.isEmpty()) {
                return null
            }

            return OpponentData(
                id = "rct_$trainerId",
                displayName = displayName,
                types = types.toList(),
                source = OpponentData.OpponentSource.RCT_TRAINER,
                pokemonSpecies = speciesList
            )

        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Extract text content from RCT Text object.
     */
    private fun extractTextContent(textObj: Any?): String? {
        if (textObj == null) return null
        try {
            // Try toString first
            val result = textObj.toString()
            if (result.isNotEmpty() && result != textObj.javaClass.name) {
                return result
            }

            // Try to get raw text
            val textClass = textObj.javaClass
            val getTextMethod = textClass.methods.find { it.name == "getText" || it.name == "getRaw" }
            if (getTextMethod != null) {
                return getTextMethod.invoke(textObj)?.toString()
            }
        } catch (e: Exception) {
            // Fall through
        }
        return null
    }

    /**
     * Extract types from a Cobblemon Pokemon object.
     */
    private fun extractPokemonTypes(pokemon: Any): List<String> {
        val types = mutableListOf<String>()
        try {
            val pokemonClass = pokemon.javaClass

            // Try to get primary and secondary type
            val primaryTypeMethod = pokemonClass.methods.find {
                it.name == "getPrimaryType" || it.name == "getType1"
            }
            val secondaryTypeMethod = pokemonClass.methods.find {
                it.name == "getSecondaryType" || it.name == "getType2"
            }

            // Try Species approach
            val getSpeciesMethod = pokemonClass.methods.find { it.name == "getSpecies" }
            if (getSpeciesMethod != null) {
                val species = getSpeciesMethod.invoke(pokemon)
                if (species != null) {
                    val speciesClass = species.javaClass

                    // Get types from species
                    val getTypesMethod = speciesClass.methods.find {
                        it.name == "getTypes" || it.name == "getPrimaryType"
                    }
                    if (getTypesMethod != null) {
                        val typesResult = getTypesMethod.invoke(species)
                        if (typesResult is Iterable<*>) {
                            for (type in typesResult) {
                                val typeName = type?.toString()?.lowercase()
                                    ?.replace("elementaltype[", "")
                                    ?.replace("]", "")
                                    ?.trim()
                                if (!typeName.isNullOrEmpty()) {
                                    types.add(typeName)
                                }
                            }
                        }
                    }
                }
            }

            if (primaryTypeMethod != null && types.isEmpty()) {
                val primaryType = primaryTypeMethod.invoke(pokemon)
                val typeName = primaryType?.toString()?.lowercase()
                if (!typeName.isNullOrEmpty()) {
                    types.add(typeName)
                }
            }

            if (secondaryTypeMethod != null) {
                val secondaryType = secondaryTypeMethod.invoke(pokemon)
                val typeName = secondaryType?.toString()?.lowercase()
                if (!typeName.isNullOrEmpty() && typeName != "null") {
                    types.add(typeName)
                }
            }

        } catch (e: Exception) {
            // Return empty list on failure
        }
        return types
    }

    /**
     * Extract species ID from a Cobblemon Pokemon object.
     */
    private fun extractPokemonSpecies(pokemon: Any): String? {
        try {
            val pokemonClass = pokemon.javaClass
            val getSpeciesMethod = pokemonClass.methods.find { it.name == "getSpecies" }
            if (getSpeciesMethod != null) {
                val species = getSpeciesMethod.invoke(pokemon)
                if (species != null) {
                    val resourceIdMethod = species.javaClass.methods.find {
                        it.name == "getResourceIdentifier" || it.name == "resourceIdentifier"
                    }
                    if (resourceIdMethod != null) {
                        return resourceIdMethod.invoke(species)?.toString()
                    }
                    // Fallback to name
                    val getNameMethod = species.javaClass.methods.find { it.name == "getName" }
                    if (getNameMethod != null) {
                        return getNameMethod.invoke(species)?.toString()
                    }
                }
            }
        } catch (e: Exception) {
            // Return null on failure
        }
        return null
    }

    /**
     * Clear cached trainer data (useful for refreshing).
     */
    fun clearCache() {
        cachedTrainers = null
    }
}
