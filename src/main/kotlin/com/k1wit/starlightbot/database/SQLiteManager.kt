package com.k1wit.starlightbot.database

import com.k1wit.starlightbot.StarlightBot
import java.sql.Connection
import java.sql.DriverManager

class SQLiteManager(private val plugin: StarlightBot) {

    private var connection: Connection? = null

    fun initialize() {
        try {
            Class.forName("org.sqlite.JDBC")
            val dbFile = plugin.dataFolder.resolve("starlightbot.db")
            plugin.dataFolder.mkdirs()
            connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
            createTables()
            plugin.consoleLog("<gray>[StarlightBot] SQLite initialized.")
        } catch (e: Exception) {
            plugin.logger.severe("[StarlightBot] Failed to initialize SQLite: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun createTables() {
        val conn = connection ?: return

        conn.createStatement().use { stmt ->
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS whitelist (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    discord_user_id TEXT UNIQUE NOT NULL,
                    minecraft_name TEXT UNIQUE NOT NULL,
                    minecraft_uuid TEXT NOT NULL,
                    whitelisted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tickets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ticket_number INTEGER NOT NULL,
                    discord_user_id TEXT NOT NULL,
                    thread_id TEXT NOT NULL,
                    category TEXT NOT NULL,
                    status TEXT DEFAULT 'OPEN',
                    claimed_by TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    closed_at TIMESTAMP
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ticket_transcripts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ticket_id INTEGER NOT NULL,
                    author TEXT NOT NULL,
                    content TEXT NOT NULL,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (ticket_id) REFERENCES tickets(id)
                )
            """.trimIndent())
        }
    }

    fun getConnection(): Connection? = connection

    fun close() {
        try {
            connection?.close()
            plugin.consoleLog("<gray>[StarlightBot] SQLite connection closed.")
        } catch (e: Exception) {
            plugin.logger.warning("[StarlightBot] Error closing SQLite: ${e.message}")
        }
    }
}