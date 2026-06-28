package com.k1wit.starlightbot.listener.minecraft

import com.k1wit.starlightbot.StarlightBot
import com.k1wit.starlightbot.util.EmbedFactory
import net.dv8tion.jda.api.utils.FileUpload
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerJoinLeaveListener(private val plugin: StarlightBot) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val playerName = event.player.name
        val onlineCount = plugin.server.onlinePlayers.size
        val maxPlayers = plugin.server.maxPlayers

        // Track Minecraft playtime for level system
        val whitelistEntry = plugin.whitelistService.repository.findByMinecraftName(playerName)
        if (whitelistEntry != null) {
            plugin.levelService.onMinecraftPlayerJoin(whitelistEntry.discordUserId)
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            sendLogEmbed(EmbedFactory.playerJoin(playerName, onlineCount, maxPlayers))
        })
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val playerName = event.player.name
        // Count after they leave
        val onlineCount = plugin.server.onlinePlayers.size - 1
        val maxPlayers = plugin.server.maxPlayers

        // Award Minecraft playtime experience
        val whitelistEntry = plugin.whitelistService.repository.findByMinecraftName(playerName)
        if (whitelistEntry != null) {
            plugin.levelService.onMinecraftPlayerLeave(whitelistEntry.discordUserId)
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            sendLogEmbed(EmbedFactory.playerLeave(playerName, onlineCount.coerceAtLeast(0), maxPlayers))
        })
    }

    private fun sendLogEmbed(embed: net.dv8tion.jda.api.entities.MessageEmbed) {
        try {
            val jda = plugin.discordBotService.jda ?: return
            val channelId = plugin.configManager.logChannelId
            if (channelId.isBlank()) return
            jda.getTextChannelById(channelId)?.sendMessageEmbeds(embed)?.queue()
        } catch (e: Exception) {
            plugin.logger.fine("[StarlightBot] Could not send join/leave embed: ${e.message}")
        }
    }
}