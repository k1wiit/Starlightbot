package com.k1wit.starlightbot.database.tables

import com.k1wit.starlightbot.database.SQLiteManager
import java.time.Instant

data class TicketEntry(
    val id: Int,
    val ticketNumber: Int,
    val discordUserId: String,
    val threadId: String,
    val category: String,
    val status: String,
    val claimedBy: String?,
    val createdAt: String,
    val closedAt: String?
)

data class TranscriptEntry(
    val id: Int,
    val ticketId: Int,
    val author: String,
    val content: String,
    val timestamp: String
)

class TicketRepository(private val db: SQLiteManager) {

    fun getNextTicketNumber(): Int {
        val conn = db.getConnection() ?: return 1
        return conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT MAX(ticket_number) as max_num FROM tickets")
            if (rs.next()) (rs.getInt("max_num") + 1).coerceAtLeast(1) else 1
        }
    }

    fun create(ticketNumber: Int, discordUserId: String, threadId: String, category: String): Int {
        val conn = db.getConnection() ?: return -1
        return conn.prepareStatement(
            "INSERT INTO tickets (ticket_number, discord_user_id, thread_id, category) VALUES (?, ?, ?, ?)"
        ).use { stmt ->
            stmt.setInt(1, ticketNumber)
            stmt.setString(2, discordUserId)
            stmt.setString(3, threadId)
            stmt.setString(4, category)
            stmt.executeUpdate()
            val rs = conn.createStatement().executeQuery("SELECT last_insert_rowid()")
            if (rs.next()) rs.getInt(1) else -1
        }
    }

    fun findById(id: Int): TicketEntry? {
        val conn = db.getConnection() ?: return null
        return conn.prepareStatement("SELECT * FROM tickets WHERE id = ?").use { stmt ->
            stmt.setInt(1, id)
            val rs = stmt.executeQuery()
            if (rs.next()) mapRow(rs) else null
        }
    }

    fun findByThreadId(threadId: String): TicketEntry? {
        val conn = db.getConnection() ?: return null
        return conn.prepareStatement("SELECT * FROM tickets WHERE thread_id = ?").use { stmt ->
            stmt.setString(1, threadId)
            val rs = stmt.executeQuery()
            if (rs.next()) mapRow(rs) else null
        }
    }

    fun findOpenByUserId(discordUserId: String): TicketEntry? {
        val conn = db.getConnection() ?: return null
        return conn.prepareStatement(
            "SELECT * FROM tickets WHERE discord_user_id = ? AND status = 'OPEN' LIMIT 1"
        ).use { stmt ->
            stmt.setString(1, discordUserId)
            val rs = stmt.executeQuery()
            if (rs.next()) mapRow(rs) else null
        }
    }

    fun claim(ticketId: Int, adminUserId: String): Boolean {
        val conn = db.getConnection() ?: return false
        return conn.prepareStatement("UPDATE tickets SET claimed_by = ? WHERE id = ?").use { stmt ->
            stmt.setString(1, adminUserId)
            stmt.setInt(2, ticketId)
            stmt.executeUpdate() > 0
        }
    }

    fun close(ticketId: Int): Boolean {
        val conn = db.getConnection() ?: return false
        return conn.prepareStatement(
            "UPDATE tickets SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP WHERE id = ?"
        ).use { stmt ->
            stmt.setInt(1, ticketId)
            stmt.executeUpdate() > 0
        }
    }

    fun addTranscript(ticketId: Int, author: String, content: String): Boolean {
        val conn = db.getConnection() ?: return false
        return conn.prepareStatement(
            "INSERT INTO ticket_transcripts (ticket_id, author, content) VALUES (?, ?, ?)"
        ).use { stmt ->
            stmt.setInt(1, ticketId)
            stmt.setString(2, author)
            stmt.setString(3, content)
            stmt.executeUpdate() > 0
        }
    }

    fun getTranscripts(ticketId: Int): List<TranscriptEntry> {
        val conn = db.getConnection() ?: return emptyList()
        return conn.prepareStatement(
            "SELECT * FROM ticket_transcripts WHERE ticket_id = ? ORDER BY timestamp ASC"
        ).use { stmt ->
            stmt.setInt(1, ticketId)
            val rs = stmt.executeQuery()
            val list = mutableListOf<TranscriptEntry>()
            while (rs.next()) {
                list.add(
                    TranscriptEntry(
                        id = rs.getInt("id"),
                        ticketId = rs.getInt("ticket_id"),
                        author = rs.getString("author"),
                        content = rs.getString("content"),
                        timestamp = rs.getString("timestamp") ?: ""
                    )
                )
            }
            list
        }
    }

    private fun mapRow(rs: java.sql.ResultSet): TicketEntry {
        return TicketEntry(
            id = rs.getInt("id"),
            ticketNumber = rs.getInt("ticket_number"),
            discordUserId = rs.getString("discord_user_id"),
            threadId = rs.getString("thread_id"),
            category = rs.getString("category"),
            status = rs.getString("status"),
            claimedBy = rs.getString("claimed_by"),
            createdAt = rs.getString("created_at") ?: "",
            closedAt = rs.getString("closed_at")
        )
    }
}