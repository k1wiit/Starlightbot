package com.k1wit.starlightbot.listener.minecraft

import com.k1wit.starlightbot.StarlightBot
import com.k1wit.starlightbot.util.EmbedFactory
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAdvancementDoneEvent

class AdvancementListener(private val plugin: StarlightBot) : Listener {

    @EventHandler
    fun onAdvancement(event: PlayerAdvancementDoneEvent) {
        val advancement = event.advancement

        // Filter out recipe advancements (they spam a lot)
        val key = advancement.key.key
        if (key.startsWith("recipes/")) return

        val display = advancement.display ?: return // null for recipe/root advancements
        if (!display.doesAnnounceToChat()) return   // only announced ones

        val playerName = event.player.name
        val title = display.title().let {
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it)
        }
        val description = display.description().let {
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it)
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val jda = plugin.discordBotService.jda ?: return@Runnable
                val channelId = plugin.configManager.logChannelId
                if (channelId.isBlank()) return@Runnable
                jda.getTextChannelById(channelId)
                    ?.sendMessageEmbeds(EmbedFactory.advancement(playerName, title, description))
                    ?.queue()
            } catch (e: Exception) {
                plugin.logger.fine("[StarlightBot] Advancement log error: ${e.message}")
            }
        })
    }
}