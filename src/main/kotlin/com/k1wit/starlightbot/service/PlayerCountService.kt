package com.k1wit.starlightbot.service

import com.k1wit.starlightbot.StarlightBot
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.RestAction
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask

class PlayerCountService(private val plugin: StarlightBot) {

    private var task: BukkitTask? = null
    private var lastRenameAttempt = 0L
    private val minRenameInterval = 600_000L // 10 minutes to stay safe with Discord rate limits

    fun start() {
        val intervalMinutes = plugin.configManager.playerCountUpdateIntervalMinutes
        val intervalTicks = intervalMinutes * 60 * 20L

        task = plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            updateVoiceChannel()
        }, intervalTicks, intervalTicks)

        plugin.consoleLog("<gray>[StarlightBot] Player count service started (interval: ${intervalMinutes}m).")
    }

    fun stop() {
        task?.cancel()
        setOffline()
    }

    private fun updateVoiceChannel() {
        val now = System.currentTimeMillis()
        if (now - lastRenameAttempt < minRenameInterval) {
            return // Rate limit protection
        }

        val jda = plugin.discordBotService.jda ?: return
        val voiceChannelId = plugin.configManager.playerCountVoiceChannelId
        if (voiceChannelId.isBlank()) return

        val voiceChannel = jda.getVoiceChannelById(voiceChannelId) ?: return

        // Get online player count on main thread
        val onlineCount = Bukkit.getOnlinePlayers().size

        val newName = plugin.configManager.playerCountFormatOnline
            .replace("%count%", onlineCount.toString())

        lastRenameAttempt = now

        voiceChannel.manager.setName(newName).queue(null) { error ->
            if (error.message?.contains("rate limit", ignoreCase = true) == true) {
                plugin.logger.fine("[StarlightBot] Voice channel rename rate limited, will retry next interval.")
                lastRenameAttempt = now + minRenameInterval // Back off
            } else {
                plugin.logger.warning("[StarlightBot] Failed to update voice channel: ${error.message}")
            }
        }
    }

    fun setOffline() {
        val jda = plugin.discordBotService.jda ?: return
        val voiceChannelId = plugin.configManager.playerCountVoiceChannelId
        if (voiceChannelId.isBlank()) return

        try {
            val voiceChannel = jda.getVoiceChannelById(voiceChannelId) ?: return
            val offlineName = plugin.configManager.playerCountFormatOffline
            voiceChannel.manager.setName(offlineName).complete()
        } catch (e: Exception) {
            plugin.logger.warning("[StarlightBot] Could not set voice channel to offline: ${e.message}")
        }
    }
}