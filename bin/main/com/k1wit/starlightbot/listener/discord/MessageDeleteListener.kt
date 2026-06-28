package com.k1wit.starlightbot.listener.discord

import com.k1wit.starlightbot.StarlightBot
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.time.Instant

class MessageDeleteListener(private val plugin: StarlightBot) : ListenerAdapter() {

    override fun onMessageDelete(event: MessageDeleteEvent) {
        val logChannelId = plugin.configManager.deletedMessageLogChannelId
        if (logChannelId.isBlank()) return

        try {
            val jda = plugin.discordBotService.jda ?: return
            val logChannel = jda.getTextChannelById(logChannelId) ?: return

            // Build embed
            val embed = EmbedBuilder()
                .setColor(0xFF6B6B)
                .setTitle("Message Deleted")
                .addField("Channel", "<#${event.channel.id}>", true)
                .addField("Message ID", event.messageId, true)
                .setTimestamp(Instant.now())
                .build()

            logChannel.sendMessageEmbeds(embed).queue()
        } catch (e: Exception) {
            plugin.logger.fine("[StarlightBot] Failed to log deleted message: ${e.message}")
        }
    }
}
