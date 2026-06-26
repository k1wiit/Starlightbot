package com.k1wit.starlightbot.listener.minecraft

import com.k1wit.starlightbot.StarlightBot
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class ChatListener(private val plugin: StarlightBot) : Listener {

    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        val playerName = event.player.name
        val message = PlainTextComponentSerializer.plainText().serialize(event.message())

        // This event fires async, we can directly send to Discord
        try {
            val jda = plugin.discordBotService.jda ?: return
            val channelId = plugin.configManager.logChannelId
            if (channelId.isBlank()) return

            val channel = jda.getTextChannelById(channelId) ?: return
            // Send as plain message (not embed) to keep chat log clean
            channel.sendMessage("**$playerName**: $message").queue()
        } catch (e: Exception) {
            plugin.logger.fine("[StarlightBot] Chat log error: ${e.message}")
        }
    }
}