package com.k1wit.starlightbot.database.tables

import com.k1wit.starlightbot.database.SQLiteManager

data class Home(
    val id: Int,
    val playerUuid: String,
    val playerName: String,
    val homeName: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val worldName: String,
    val isPublic: Boolean,
    val createdAt: Long
)

class HomeRepository(private val db: SQLiteManager) {

    init {
        createTable()
    }

    private fun createTable() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS homes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                player_name TEXT NOT NULL,
                home_name TEXT NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                yaw FLOAT NOT NULL,
                pitch FLOAT NOT NULL,
                world_name TEXT NOT NULL,
                is_public INTEGER DEFAULT 0,
                created_at LONG DEFAULT 0,
                UNIQUE(player_uuid, home_name)
            )
        """.trimIndent())
    }

    fun create(
        playerUuid: String,
        playerName: String,
        homeName: String,
        x: Double,
        y: Double,
        z: Double,
        yaw: Float,
        pitch: Float,
        worldName: String,
        isPublic: Boolean = false
    ): Boolean {
        return try {
            db.execute("""
                INSERT INTO homes (player_uuid, player_name, home_name, x, y, z, yaw, pitch, world_name, is_public, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
                playerUuid, playerName, homeName, x, y, z, yaw, pitch, worldName,
                if (isPublic) 1 else 0, System.currentTimeMillis()
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    fun delete(playerUuid: String, homeName: String): Boolean {
        return try {
            db.execute("""
                DELETE FROM homes WHERE player_uuid = ? AND home_name = ?
            """.trimIndent(), playerUuid, homeName) > 0
        } catch (e: Exception) {
            false
        }
    }

    fun deletePublic(homeName: String): Boolean {
        return try {
            db.execute("""
                DELETE FROM homes WHERE home_name = ? AND is_public = 1
            """.trimIndent(), homeName) > 0
        } catch (e: Exception) {
            false
        }
    }

    fun findByPlayerUuid(playerUuid: String): List<Home> {
        return db.query("""
            SELECT * FROM homes WHERE player_uuid = ? ORDER BY created_at DESC
        """.trimIndent(), playerUuid) { rs ->
            val list = mutableListOf<Home>()
            while (rs.next()) {
                list.add(Home(
                    id = rs.getInt("id"),
                    playerUuid = rs.getString("player_uuid"),
                    playerName = rs.getString("player_name"),
                    homeName = rs.getString("home_name"),
                    x = rs.getDouble("x"),
                    y = rs.getDouble("y"),
                    z = rs.getDouble("z"),
                    yaw = rs.getFloat("yaw"),
                    pitch = rs.getFloat("pitch"),
                    worldName = rs.getString("world_name"),
                    isPublic = rs.getInt("is_public") == 1,
                    createdAt = rs.getLong("created_at")
                ))
            }
            list
        } ?: emptyList()
    }

    fun findPublicHomes(): List<Home> {
        return db.query("""
            SELECT * FROM homes WHERE is_public = 1 ORDER BY created_at DESC
        """.trimIndent()) { rs ->
            val list = mutableListOf<Home>()
            while (rs.next()) {
                list.add(Home(
                    id = rs.getInt("id"),
                    playerUuid = rs.getString("player_uuid"),
                    playerName = rs.getString("player_name"),
                    homeName = rs.getString("home_name"),
                    x = rs.getDouble("x"),
                    y = rs.getDouble("y"),
                    z = rs.getDouble("z"),
                    yaw = rs.getFloat("yaw"),
                    pitch = rs.getFloat("pitch"),
                    worldName = rs.getString("world_name"),
                    isPublic = true,
                    createdAt = rs.getLong("created_at")
                ))
            }
            list
        } ?: emptyList()
    }

    fun findByName(playerUuid: String, homeName: String): Home? {
        return db.query("""
            SELECT * FROM homes WHERE player_uuid = ? AND home_name = ?
        """.trimIndent(), playerUuid, homeName) { rs ->
            if (rs.next()) {
                Home(
                    id = rs.getInt("id"),
                    playerUuid = rs.getString("player_uuid"),
                    playerName = rs.getString("player_name"),
                    homeName = rs.getString("home_name"),
                    x = rs.getDouble("x"),
                    y = rs.getDouble("y"),
                    z = rs.getDouble("z"),
                    yaw = rs.getFloat("yaw"),
                    pitch = rs.getFloat("pitch"),
                    worldName = rs.getString("world_name"),
                    isPublic = rs.getInt("is_public") == 1,
                    createdAt = rs.getLong("created_at")
                )
            } else null
        }
    }

    fun updateVisibility(playerUuid: String, homeName: String, isPublic: Boolean): Boolean {
        return try {
            db.execute("""
                UPDATE homes SET is_public = ? WHERE player_uuid = ? AND home_name = ?
            """.trimIndent(), if (isPublic) 1 else 0, playerUuid, homeName) > 0
        } catch (e: Exception) {
            false
        }
    }

    fun countPlayerHomes(playerUuid: String): Int {
        return db.query("""
            SELECT COUNT(*) as count FROM homes WHERE player_uuid = ?
        """.trimIndent(), playerUuid) { rs ->
            if (rs.next()) rs.getInt("count") else 0
        } ?: 0
    }
}
