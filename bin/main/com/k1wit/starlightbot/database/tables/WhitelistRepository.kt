package com.k1wit.starlightbot.database.tables

import com.k1wit.starlightbot.database.SQLiteManager

data class WhitelistEntry(
    val id: Int,
    val discordUserId: String,
    val minecraftName: String,
    val minecraftUuid: String,
    val whitelistedAt: String
)

class WhitelistRepository(private val db: SQLiteManager) {

    fun add(discordUserId: String, minecraftName: String, minecraftUuid: String): Boolean {
        return try {
            val conn = db.getConnection() ?: return false
            val sql = "INSERT INTO whitelist (discord_user_id, minecraft_name, minecraft_uuid) VALUES (?, ?, ?)"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, discordUserId)
                stmt.setString(2, minecraftName)
                stmt.setString(3, minecraftUuid)
                stmt.executeUpdate() > 0
            }
        } catch (e: Exception) {
            false
        }
    }

    fun remove(minecraftName: String): Boolean {
        val conn = db.getConnection() ?: return false
        return conn.prepareStatement("DELETE FROM whitelist WHERE LOWER(minecraft_name) = LOWER(?)").use { stmt ->
            stmt.setString(1, minecraftName)
            stmt.executeUpdate() > 0
        }
    }

    fun removeByDiscordId(discordUserId: String): Boolean {
        val conn = db.getConnection() ?: return false
        return conn.prepareStatement("DELETE FROM whitelist WHERE discord_user_id = ?").use { stmt ->
            stmt.setString(1, discordUserId)
            stmt.executeUpdate() > 0
        }
    }

    fun findByDiscordId(discordUserId: String): WhitelistEntry? {
        val conn = db.getConnection() ?: return null
        return conn.prepareStatement("SELECT * FROM whitelist WHERE discord_user_id = ?").use { stmt ->
            stmt.setString(1, discordUserId)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                WhitelistEntry(
                    id = rs.getInt("id"),
                    discordUserId = rs.getString("discord_user_id"),
                    minecraftName = rs.getString("minecraft_name"),
                    minecraftUuid = rs.getString("minecraft_uuid"),
                    whitelistedAt = rs.getString("whitelisted_at") ?: ""
                )
            } else null
        }
    }

    fun findByMinecraftName(name: String): WhitelistEntry? {
        val conn = db.getConnection() ?: return null
        return conn.prepareStatement("SELECT * FROM whitelist WHERE LOWER(minecraft_name) = LOWER(?)").use { stmt ->
            stmt.setString(1, name)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                WhitelistEntry(
                    id = rs.getInt("id"),
                    discordUserId = rs.getString("discord_user_id"),
                    minecraftName = rs.getString("minecraft_name"),
                    minecraftUuid = rs.getString("minecraft_uuid"),
                    whitelistedAt = rs.getString("whitelisted_at") ?: ""
                )
            } else null
        }
    }

    fun getAll(): List<WhitelistEntry> {
        val conn = db.getConnection() ?: return emptyList()
        return conn.prepareStatement("SELECT * FROM whitelist ORDER BY whitelisted_at DESC").use { stmt ->
            val rs = stmt.executeQuery()
            val list = mutableListOf<WhitelistEntry>()
            while (rs.next()) {
                list.add(
                    WhitelistEntry(
                        id = rs.getInt("id"),
                        discordUserId = rs.getString("discord_user_id"),
                        minecraftName = rs.getString("minecraft_name"),
                        minecraftUuid = rs.getString("minecraft_uuid"),
                        whitelistedAt = rs.getString("whitelisted_at") ?: ""
                    )
                )
            }
            list
        }
    }

    fun updateMinecraftName(discordUserId: String, newName: String, newUuid: String): Boolean {
        val conn = db.getConnection() ?: return false
        return conn.prepareStatement(
            "UPDATE whitelist SET minecraft_name = ?, minecraft_uuid = ? WHERE discord_user_id = ?"
        ).use { stmt ->
            stmt.setString(1, newName)
            stmt.setString(2, newUuid)
            stmt.setString(3, discordUserId)
            stmt.executeUpdate() > 0
        }
    }
}