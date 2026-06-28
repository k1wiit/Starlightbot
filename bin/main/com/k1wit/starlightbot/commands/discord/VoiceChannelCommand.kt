package com.k1wit.starlightbot.commands.discord

import com.k1wit.starlightbot.StarlightBot
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.OptionType

class VoiceChannelCommand(private val plugin: StarlightBot) {

    fun getCommandData() = Commands.slash("voicecreate", "Create a temporary voice channel")
        .addOption(OptionType.STRING, "name", "Name of the voice channel", true)
        .addOption(OptionType.INTEGER, "limit", "User limit (0 = unlimited)", false, true)

    fun handle(event: SlashCommandInteractionEvent) {
        val channelName = event.getOption("name")?.asString ?: return
        val userLimit = event.getOption("limit")?.asInt ?: 0

        if (channelName.isBlank()) {
            event.reply("Please provide a valid channel name").setEphemeral(true).queue()
            return
        }

        if (channelName.length > 100) {
            event.reply("Channel name must be 100 characters or less").setEphemeral(true).queue()
            return
        }

        if (userLimit < 0 || userLimit > 99) {
            event.reply("User limit must be between 0 and 99").setEphemeral(true).queue()
            return
        }

        try {
            val guild = event.guild ?: return
            val category = guild.categories.firstOrNull { it.name.equals("Voice Channels", ignoreCase = true) }
                ?: guild.categories.firstOrNull()

            guild.createVoiceChannel(channelName)
                .apply {
                    if (category != null) setParent(category)
                    if (userLimit > 0) setUserLimit(userLimit)
                }
                .queue({ voiceChannel ->
                    // Set permissions so the creator can manage it
                    voiceChannel.manager.putMemberPermissionOverride(
                        event.member!!.idLong,
                        listOf(Permission.MANAGE_CHANNEL, Permission.VOICE_CONNECT),
                        emptyList()
                    ).queue()

                    event.reply("✅ Voice channel **$channelName** created!")
                        .setEphemeral(true)
                        .queue()
                }, { error ->
                    plugin.logger.warning("[StarlightBot] Failed to create voice channel: ${error.message}")
                    event.reply("❌ Failed to create voice channel: ${error.message}")
                        .setEphemeral(true)
                        .queue()
                })
        } catch (e: Exception) {
            plugin.logger.warning("[StarlightBot] Voice channel error: ${e.message}")
            event.reply("❌ Error: ${e.message}").setEphemeral(true).queue()
        }
    }
}
