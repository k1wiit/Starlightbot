package com.k1wit.starlightbot.listener.discord

import com.k1wit.starlightbot.StarlightBot
import com.k1wit.starlightbot.service.WhitelistResult
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class WhitelistMessageListener(private val plugin: StarlightBot) : ListenerAdapter() {

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) return

        val channelId = plugin.configManager.whitelistChannelId
        if (event.channel.id != channelId) return

        val content = event.message.contentRaw.trim()

        // 1. Delete the message immediately
        event.message.delete().queue(null) { }

        // 2. Validate: must be exactly one word (no spaces)
        if (content.contains(" ") || content.isBlank()) {
            return // Silently ignore
        }

        val user = event.author

        // 3. Process async
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val result = plugin.whitelistService.processWhitelistRequest(user.id, content)

            when (result) {
                is WhitelistResult.Success -> {
                    plugin.whitelistService.sendDmSafely(
                        user,
                        "You have been successfully whitelisted as `${result.minecraftName}`! You can now join the server."
                    )
                }
                is WhitelistResult.Error -> {
                    plugin.whitelistService.sendDmSafely(user, result.message)
                }
            }
        })
    }
}