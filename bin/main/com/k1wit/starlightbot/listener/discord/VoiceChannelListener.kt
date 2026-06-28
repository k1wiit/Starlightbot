package com.k1wit.starlightbot.listener.discord

import com.k1wit.starlightbot.StarlightBot
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class VoiceChannelListener(private val plugin: StarlightBot) : ListenerAdapter() {

    override fun onGuildVoiceUpdate(event: GuildVoiceUpdateEvent) {
        val userId = event.member.id
        val joinedChannel = event.channelJoined
        val leftChannel = event.channelLeft

        // User joined a voice channel
        if (joinedChannel != null && leftChannel == null) {
            try {
                plugin.levelService.onVoiceChannelJoin(userId)
            } catch (e: Exception) {
                plugin.logger.fine("[StarlightBot] Could not track voice join: ${e.message}")
            }
        }

        // User left a voice channel
        if (leftChannel != null && joinedChannel == null) {
            try {
                plugin.levelService.onVoiceChannelLeave(userId)
            } catch (e: Exception) {
                plugin.logger.fine("[StarlightBot] Could not track voice leave: ${e.message}")
            }
        }

        // User moved to a different voice channel
        if (joinedChannel != null && leftChannel != null) {
            // Award time for previous channel and start tracking new one
            try {
                plugin.levelService.onVoiceChannelLeave(userId)
                plugin.levelService.onVoiceChannelJoin(userId)
            } catch (e: Exception) {
                plugin.logger.fine("[StarlightBot] Could not track voice move: ${e.message}")
            }
        }
    }
}
