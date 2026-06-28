package com.k1wit.starlightbot.listener.discord

import com.k1wit.starlightbot.StarlightBot
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class VoiceChannelListener(private val plugin: StarlightBot) : ListenerAdapter() {

    private val tempChannels = mutableSetOf<Long>()

    override fun onGuildVoiceUpdate(event: GuildVoiceUpdateEvent) {
        val userId = event.member.id
        val joinedChannel = event.channelJoined
        val leftChannel = event.channelLeft

        // 1. Join-to-Create Logic
        if (joinedChannel != null && joinedChannel.id == plugin.configManager.joinToCreateVoiceChannelId) {
            val guild = event.guild
            val member = event.member
            val category = joinedChannel.parentCategory

            guild.createVoiceChannel("${member.effectiveName}'s Channel")
                .apply {
                    if (category != null) setParent(category)
                }
                .queue { newChannel ->
                    tempChannels.add(newChannel.idLong)
                    guild.moveVoiceMember(member, newChannel).queue()
                }
        }

        // 2. Auto-Delete Logic
        if (leftChannel != null && tempChannels.contains(leftChannel.idLong)) {
            if (leftChannel.members.isEmpty()) {
                tempChannels.remove(leftChannel.idLong)
                leftChannel.delete().queue()
            }
        }

        // 3. Leveling Logic
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
