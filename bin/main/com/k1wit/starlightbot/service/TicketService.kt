package com.k1wit.starlightbot.service

import com.k1wit.starlightbot.StarlightBot
import com.k1wit.starlightbot.database.tables.TicketRepository
import com.k1wit.starlightbot.util.EmbedFactory
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.utils.FileUpload
import java.time.Instant

class TicketService(private val plugin: StarlightBot) {

    val repository = TicketRepository(plugin.sqliteManager)

    fun createTicket(
        discordUserId: String,
        username: String,
        categoryValue: String
    ) {
        val jda = plugin.discordBotService.jda ?: return
        val ticketsChannelId = plugin.configManager.ticketsChannelId
        if (ticketsChannelId.isBlank()) return

        val channel = jda.getTextChannelById(ticketsChannelId) ?: run {
            plugin.logger.warning("[StarlightBot] Ticket channel not found: $ticketsChannelId")
            return
        }

        val ticketNumber = repository.getNextTicketNumber()
        val threadName = "ticket-${ticketNumber.toString().padStart(4, '0')}-$username"
            .take(100) // Discord thread name limit

        val categoryDisplay = when (categoryValue) {
            "application"   -> "Application"
            "support"       -> "Support / Bug Report"
            "player_report" -> "Player Report"
            "other"         -> "Other"
            else            -> categoryValue.replaceFirstChar { it.uppercase() }
        }

        // Create private thread
        channel.createThreadChannel(threadName, true).queue({ thread ->
            // Save to DB
            val ticketId = repository.create(ticketNumber, discordUserId, thread.id, categoryValue)

            // Build initial embed
            val embed = EmbedBuilder()
                .setColor(0x1a1a2e)
                .setTitle("Ticket #${ticketNumber.toString().padStart(4, '0')} - $categoryDisplay")
                .setDescription(
                    "Welcome to your ticket! Please describe your request and a staff member will assist you shortly."
                )
                .setFooter("Ticket created by $username")
                .setTimestamp(Instant.now())
                .build()

            val claimButton = Button.of(ButtonStyle.PRIMARY, "ticket_claim_$ticketId", "Claim Ticket")
            val closeButton = Button.of(ButtonStyle.DANGER, "ticket_close_$ticketId", "Close Ticket")

            // Post initial message
            thread.sendMessageEmbeds(embed)
                .setComponents(ActionRow.of(claimButton, closeButton))
                .queue()

            // Add the user to thread
            thread.addThreadMember(jda.retrieveUserById(discordUserId).complete()).queue()

            // Add admins to thread
            val guild = jda.getGuildById(plugin.configManager.guildId)
            guild?.getMembersWithRoles(
                guild.getRoleById(plugin.configManager.adminRoleId) ?: return@queue
            )?.forEach { member ->
                thread.addThreadMember(member).queue(null) { /* ignore errors */ }
            }

            // Add admin user IDs
            plugin.configManager.adminUserIds.forEach { adminId ->
                try {
                    thread.addThreadMember(jda.retrieveUserById(adminId).complete()).queue(null) { }
                } catch (_: Exception) {}
            }

            // Log ticket creation
            sendTicketLog(EmbedFactory.ticketCreated(ticketNumber, categoryDisplay, discordUserId))

        }, { error ->
            plugin.logger.warning("[StarlightBot] Failed to create ticket thread: ${error.message}")
        })
    }

    fun claimTicket(ticketId: Int, adminUserId: String, adminName: String): Boolean {
        return repository.claim(ticketId, adminUserId).also { success ->
            if (success) {
                val ticket = repository.findById(ticketId) ?: return@also
                val jda = plugin.discordBotService.jda ?: return@also
                val thread = jda.getThreadChannelById(ticket.threadId) ?: return@also
                thread.sendMessage("This ticket has been claimed by **$adminName**.").queue()
            }
        }
    }

    fun closeTicket(ticketId: Int, closedByUserId: String, closedByName: String) {
        val ticket = repository.findById(ticketId) ?: return
        val jda = plugin.discordBotService.jda ?: return
        val thread = jda.getThreadChannelById(ticket.threadId) ?: return

        // Collect messages for transcript
        thread.iterableHistory.cache(false).queue({ messages ->
            val sb = StringBuilder()
            sb.appendLine("=== Ticket Transcript ===")
            sb.appendLine("Ticket #${ticket.ticketNumber.toString().padStart(4, '0')}")
            sb.appendLine("Category: ${ticket.category}")
            sb.appendLine("Opened by: <@${ticket.discordUserId}>")
            sb.appendLine("Closed by: $closedByName")
            sb.appendLine("========================")
            sb.appendLine()

            messages.reversed().forEach { msg ->
                if (!msg.author.isBot) {
                    val time = msg.timeCreated.format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    )
                    sb.appendLine("[$time] ${msg.author.name}: ${msg.contentDisplay}")
                    // Save transcript to DB
                    repository.addTranscript(ticketId, msg.author.name, msg.contentDisplay)
                }
            }

            // Update DB
            repository.close(ticketId)

            // Send transcript to ticket log channel
            val ticketLogChannelId = plugin.configManager.ticketLogChannelId
            val ticketLogChannel = if (ticketLogChannelId.isNotBlank()) {
                jda.getTextChannelById(ticketLogChannelId)
            } else {
                null
            }

            val embed = EmbedFactory.ticketClosed(ticket.ticketNumber, ticket.category, closedByUserId)
            if (ticketLogChannel != null) {
                ticketLogChannel.sendMessageEmbeds(embed)
                    .addFiles(FileUpload.fromData(transcriptBytes, "ticket-${ticket.ticketNumber}-transcript.txt"))
                    .queue()
            }

            // Also send to log channel as backup
            val logChannelId = plugin.configManager.logChannelId
            val logChannel = if (logChannelId.isNotBlank()) {
                jda.getTextChannelById(logChannelId)
            } else {
                null
            }

            logChannel?.sendMessageEmbeds(embed)
                ?.addFiles(FileUpload.fromData(transcriptBytes, "ticket-${ticket.ticketNumber}-transcript.txt"))
                ?.queue()

            // Archive & lock thread
            thread.manager
                .setArchived(true)
                .setLocked(true)
                .queue()

        }, { error ->
            plugin.logger.warning("[StarlightBot] Failed to collect transcript: ${error.message}")
            // Still close the ticket
            repository.close(ticketId)
            thread.manager.setArchived(true).setLocked(true).queue()
        })
    }

    private fun sendTicketLog(embed: net.dv8tion.jda.api.entities.MessageEmbed) {
        val jda = plugin.discordBotService.jda ?: return
        
        // Send to ticket log channel if configured
        val ticketLogChannelId = plugin.configManager.ticketLogChannelId
        if (ticketLogChannelId.isNotBlank()) {
            try {
                jda.getTextChannelById(ticketLogChannelId)?.sendMessageEmbeds(embed)?.queue()
            } catch (_: Exception) {}
        } else {
            // Fall back to log channel
            val logChannelId = plugin.configManager.logChannelId
            if (logChannelId.isNotBlank()) {
                try {
                    jda.getTextChannelById(logChannelId)?.sendMessageEmbeds(embed)?.queue()
                } catch (_: Exception) {}
            }
        }
    }
}