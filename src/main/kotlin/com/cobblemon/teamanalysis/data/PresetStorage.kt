package com.cobblemon.teamanalysis.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.cobblemon.teamanalysis.CobblemonTeamAnalysis
import net.minecraft.server.MinecraftServer
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages storage and retrieval of team presets per player.
 */
class PresetStorage private constructor(private val server: MinecraftServer) {

    private val playerPresets = ConcurrentHashMap<UUID, MutableList<TeamPreset>>()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    companion object {
        private var instance: PresetStorage? = null
        private const val MAX_PRESETS_PER_PLAYER = 20

        fun init(server: MinecraftServer) {
            instance = PresetStorage(server)
            instance?.loadAll()
        }

        fun get(): PresetStorage {
            return instance ?: throw IllegalStateException("PresetStorage not initialized!")
        }
    }

    private fun getStorageDir(): File {
        val worldDir = server.serverDirectory.resolve("world").toFile()
        val modDir = File(worldDir, "cobblemonteamanalysis")
        if (!modDir.exists()) {
            modDir.mkdirs()
        }
        return modDir
    }

    private fun getPlayerFile(playerUUID: UUID): File {
        return File(getStorageDir(), "$playerUUID.json")
    }

    private fun loadAll() {
        val dir = getStorageDir()
        if (!dir.exists()) return

        for (file in dir.listFiles() ?: emptyArray()) {
            if (file.extension == "json") {
                try {
                    val uuid = UUID.fromString(file.nameWithoutExtension)
                    loadPlayer(uuid)
                } catch (e: Exception) {
                    CobblemonTeamAnalysis.LOGGER.error("Failed to load presets from ${file.name}", e)
                }
            }
        }
    }

    private fun loadPlayer(playerUUID: UUID) {
        val file = getPlayerFile(playerUUID)
        if (!file.exists()) return

        try {
            val json = gson.fromJson(file.readText(), JsonObject::class.java)
            val presetsArray = json.getAsJsonArray("presets")

            val presets = mutableListOf<TeamPreset>()
            for (presetElement in presetsArray) {
                presets.add(TeamPreset.fromJson(presetElement.asJsonObject))
            }

            playerPresets[playerUUID] = presets
        } catch (e: Exception) {
            CobblemonTeamAnalysis.LOGGER.error("Failed to load presets for $playerUUID", e)
        }
    }

    private fun savePlayer(playerUUID: UUID) {
        val presets = playerPresets[playerUUID] ?: return

        val json = JsonObject().apply {
            add("presets", JsonArray().apply {
                presets.forEach { add(it.toJson()) }
            })
        }

        try {
            val file = getPlayerFile(playerUUID)
            file.writeText(gson.toJson(json))
        } catch (e: Exception) {
            CobblemonTeamAnalysis.LOGGER.error("Failed to save presets for $playerUUID", e)
        }
    }

    fun getPresets(playerUUID: UUID): List<TeamPreset> {
        return playerPresets[playerUUID]?.toList() ?: emptyList()
    }

    fun getPreset(playerUUID: UUID, name: String): TeamPreset? {
        return playerPresets[playerUUID]?.find { it.name.equals(name, ignoreCase = true) }
    }

    fun savePreset(playerUUID: UUID, preset: TeamPreset): Boolean {
        val presets = playerPresets.getOrPut(playerUUID) { mutableListOf() }

        // Check max limit
        if (presets.size >= MAX_PRESETS_PER_PLAYER) {
            return false
        }

        // Check for duplicate name
        if (presets.any { it.name.equals(preset.name, ignoreCase = true) }) {
            return false
        }

        presets.add(preset)
        savePlayer(playerUUID)
        return true
    }

    fun updatePreset(playerUUID: UUID, preset: TeamPreset) {
        val presets = playerPresets[playerUUID] ?: return
        val index = presets.indexOfFirst { it.name.equals(preset.name, ignoreCase = true) }
        if (index >= 0) {
            presets[index] = preset
            savePlayer(playerUUID)
        }
    }

    fun deletePreset(playerUUID: UUID, name: String): Boolean {
        val presets = playerPresets[playerUUID] ?: return false
        val removed = presets.removeIf { it.name.equals(name, ignoreCase = true) }
        if (removed) {
            savePlayer(playerUUID)
        }
        return removed
    }

    fun renamePreset(playerUUID: UUID, oldName: String, newName: String): Boolean {
        val presets = playerPresets[playerUUID] ?: return false

        // Check if new name already exists
        if (presets.any { it.name.equals(newName, ignoreCase = true) }) {
            return false
        }

        val preset = presets.find { it.name.equals(oldName, ignoreCase = true) } ?: return false
        preset.name = newName
        savePlayer(playerUUID)
        return true
    }
}
