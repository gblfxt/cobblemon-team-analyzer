package com.cobblemon.teamanalysis.opponent

/**
 * Service for managing opponents that can be countered in team building.
 * Combines RCT trainers (when available) with type-based challenges.
 */
object OpponentService {

    /**
     * Get all available opponents.
     * Includes RCT trainers if the mod is installed, plus type challenges.
     */
    fun getAvailableOpponents(): List<OpponentData> {
        val opponents = mutableListOf<OpponentData>()

        // Add RCT trainers if available
        val rctTrainers = RCTIntegration.getRCTTrainers()
        if (rctTrainers.isNotEmpty()) {
            opponents.addAll(rctTrainers)
        }

        // Always add type challenges as fallback/additional options
        opponents.addAll(TypeChallenges.getAll())

        return opponents
    }

    /**
     * Get only RCT trainers (for dedicated trainer selector).
     */
    fun getRCTTrainers(): List<OpponentData> = RCTIntegration.getRCTTrainers()

    /**
     * Get only type-based challenges.
     */
    fun getTypeChallenges(): List<OpponentData> = TypeChallenges.getAll()

    /**
     * Get type gyms only.
     */
    fun getTypeGyms(): List<OpponentData> = TypeChallenges.TYPE_GYMS

    /**
     * Get dual-type elite challenges.
     */
    fun getDualTypeElites(): List<OpponentData> = TypeChallenges.DUAL_TYPE_ELITES

    /**
     * Search opponents by name.
     */
    fun searchOpponents(query: String): List<OpponentData> {
        val lowerQuery = query.lowercase()
        return getAvailableOpponents().filter {
            it.displayName.lowercase().contains(lowerQuery) ||
            it.types.any { type -> type.contains(lowerQuery) }
        }
    }

    /**
     * Get opponent by ID.
     */
    fun getOpponentById(id: String): OpponentData? {
        return getAvailableOpponents().find { it.id == id }
    }

    /**
     * Check if RCT trainers are available.
     */
    fun hasRCTTrainers(): Boolean = RCTIntegration.isRCTAvailable() && RCTIntegration.getRCTTrainers().isNotEmpty()
}
