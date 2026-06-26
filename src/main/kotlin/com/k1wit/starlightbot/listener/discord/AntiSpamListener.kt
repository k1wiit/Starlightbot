package com.k1wit.starlightbot.listener.discord

import com.k1wit.starlightbot.StarlightBot
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class AntiSpamListener(private val plugin: StarlightBot) : ListenerAdapter() {

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) return
        if (!event.isFromGuild) return

        val member = event.member ?: return

        // Don't spam-check the whitelist channel (handled separately)
        if (event.channel.id == plugin.configManager.whitelistChannelId) return

        try {
            plugin.antiSpamService.processMessage(member, event.message)
        } catch (e: Exception) {
            plugin.logger.warning("[StarlightBot] AntiSpam error: ${e.message}")
        }
    }
}