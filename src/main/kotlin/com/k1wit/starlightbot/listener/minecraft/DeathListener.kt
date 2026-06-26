package com.k1wit.starlightbot.listener.minecraft

import com.k1wit.starlightbot.StarlightBot
import com.k1wit.starlightbot.util.EmbedFactory
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

class DeathListener(private val plugin: StarlightBot) : Listener {

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val deathMessage = event.deathMessage()?.let {
            PlainTextComponentSerializer.plainText().serialize(it)
        } ?: "${event.entity.name} died"

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val jda = plugin.discordBotService.jda ?: return@Runnable
                val channelId = plugin.configManager.logChannelId
                if (channelId.isBlank()) return@Runnable
                jda.getTextChannelById(channelId)
                    ?.sendMessageEmbeds(EmbedFactory.playerDeath(deathMessage))
                    ?.queue()
            } catch (e: Exception) {
                plugin.logger.fine("[StarlightBot] Death log error: ${e.message}")
            }
        })
    }
}