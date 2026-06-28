package com.k1wit.starlightbot.database.tables

import com.k1wit.starlightbot.database.SQLiteManager

data class PlayerLevel(
    val id: Int,
    val discordUserId: String,
    val minecraftName: String?,
    val level: Int,
    val experience: Long,
    val discordChatExp: Long,
    val voiceTimeMinutes: Long,
    val minecraftPlaytimeMinutes: Long,
    val lastUpdated: Long
)

class LevelRepository(private val db: SQLiteManager) {

    init {
        createTable()
    }

    private fun createTable() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS player_levels (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                discord_user_id TEXT UNIQUE NOT NULL,
                minecraft_name TEXT,
                level INTEGER DEFAULT 1,
                experience LONG DEFAULT 0,
                discord_chat_exp LONG DEFAULT 0,
                voice_time_minutes LONG DEFAULT 0,
                minecraft_playtime_minutes LONG DEFAULT 0,
                last_updated LONG DEFAULT 0
            )
        """.trimIndent())
    }

    fun getOrCreate(discordUserId: String): PlayerLevel {
        val existing = findByDiscordUserId(discordUserId)
        if (existing != null) return existing

        db.execute("""
            INSERT INTO player_levels (discord_user_id, last_updated)
            VALUES (?, ?)
        """.trimIndent(), discordUserId, System.currentTimeMillis())

        return findByDiscordUserId(discordUserId) ?: throw Exception("Failed to create player level")
    }

    fun findByDiscordUserId(discordUserId: String): PlayerLevel? {
        return db.query("""
            SELECT * FROM player_levels WHERE discord_user_id = ?
        """.trimIndent(), discordUserId) { rs ->
            if (rs.next()) {
                PlayerLevel(
                    id = rs.getInt("id"),
                    discordUserId = rs.getString("discord_user_id"),
                    minecraftName = rs.getString("minecraft_name"),
                    level = rs.getInt("level"),
                    experience = rs.getLong("experience"),
                    discordChatExp = rs.getLong("discord_chat_exp"),
                    voiceTimeMinutes = rs.getLong("voice_time_minutes"),
                    minecraftPlaytimeMinutes = rs.getLong("minecraft_playtime_minutes"),
                    lastUpdated = rs.getLong("last_updated")
                )
            } else {
                null
            }
        }
    }

    fun findByMinecraftName(minecraftName: String): PlayerLevel? {
        return db.query("""
            SELECT * FROM player_levels WHERE minecraft_name = ?
        """.trimIndent(), minecraftName) { rs ->
            if (rs.next()) {
                PlayerLevel(
                    id = rs.getInt("id"),
                    discordUserId = rs.getString("discord_user_id"),
                    minecraftName = rs.getString("minecraft_name"),
                    level = rs.getInt("level"),
                    experience = rs.getLong("experience"),
                    discordChatExp = rs.getLong("discord_chat_exp"),
                    voiceTimeMinutes = rs.getLong("voice_time_minutes"),
                    minecraftPlaytimeMinutes = rs.getLong("minecraft_playtime_minutes"),
                    lastUpdated = rs.getLong("last_updated")
                )
            } else {
                null
            }
        }
    }

    fun getAllPlayers(): List<PlayerLevel> {
        return db.query("""
            SELECT * FROM player_levels ORDER BY level DESC, experience DESC
        """.trimIndent()) { rs ->
            val list = mutableListOf<PlayerLevel>()
            while (rs.next()) {
                list.add(PlayerLevel(
                    id = rs.getInt("id"),
                    discordUserId = rs.getString("discord_user_id"),
                    minecraftName = rs.getString("minecraft_name"),
                    level = rs.getInt("level"),
                    experience = rs.getLong("experience"),
                    discordChatExp = rs.getLong("discord_chat_exp"),
                    voiceTimeMinutes = rs.getLong("voice_time_minutes"),
                    minecraftPlaytimeMinutes = rs.getLong("minecraft_playtime_minutes"),
                    lastUpdated = rs.getLong("last_updated")
                ))
            }
            list
        } ?: emptyList()
    }

    fun addDiscordChatExp(discordUserId: String, exp: Long) {
        val player = getOrCreate(discordUserId)
        val newExp = player.discordChatExp + exp
        updateExp(player.id, player.experience + exp, newExp, player.voiceTimeMinutes, player.minecraftPlaytimeMinutes)
    }

    fun addVoiceTime(discordUserId: String, minutes: Long) {
        val player = getOrCreate(discordUserId)
        val newVoiceTime = player.voiceTimeMinutes + minutes
        updateExp(player.id, player.experience, player.discordChatExp, newVoiceTime, player.minecraftPlaytimeMinutes)
    }

    fun addMinecraftPlaytime(discordUserId: String, minutes: Long) {
        val player = getOrCreate(discordUserId)
        val newPlaytime = player.minecraftPlaytimeMinutes + minutes
        updateExp(player.id, player.experience, player.discordChatExp, player.voiceTimeMinutes, newPlaytime)
    }

    fun linkMinecraftName(discordUserId: String, minecraftName: String) {
        db.execute("""
            UPDATE player_levels
            SET minecraft_name = ?, last_updated = ?
            WHERE discord_user_id = ?
        """.trimIndent(), minecraftName, System.currentTimeMillis(), discordUserId)
    }

    private fun updateExp(playerId: Int, totalExp: Long, discordChatExp: Long, voiceTime: Long, minecraftPlaytime: Long) {
        val newLevel = calculateLevel(totalExp)
        
        db.execute("""
            UPDATE player_levels
            SET experience = ?, level = ?, discord_chat_exp = ?, voice_time_minutes = ?, 
                minecraft_playtime_minutes = ?, last_updated = ?
            WHERE id = ?
        """.trimIndent(), totalExp, newLevel, discordChatExp, voiceTime, minecraftPlaytime, System.currentTimeMillis(), playerId)
    }

    private fun calculateLevel(experience: Long): Int {
        // Level progression: each level requires progressively more exp
        // Level 1: 0 exp
        // Level 2: 100 exp
        // Level 3: 300 exp (cumulative)
        // Level 4: 600 exp (cumulative)
        // Formula: exp_for_level = 100 * level
        
        var level = 1
        var requiredExp = 100L
        var totalRequired = 0L

        while (totalRequired + requiredExp <= experience) {
            totalRequired += requiredExp
            level++
            requiredExp = 100L * level
        }

        return level
    }
}
