package com.k1wit.starlightbot.commands.discord

import com.k1wit.starlightbot.StarlightBot
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.utils.FileUpload

class LastStacktraceCommand(private val plugin: StarlightBot) {

    fun handle(event: SlashCommandInteractionEvent) {
        if (!isAdmin(event)) {
            event.reply("You don't have permission to use this command.").setEphemeral(true).queue()
            return
        }

        val stacktrace = plugin.loggingService.getLastStacktrace()

        if (stacktrace == null) {
            event.reply("No stacktrace recorded since the last server start.")
                .setEphemeral(true).queue()
            return
        }

        if (stacktrace.length <= 3900) {
            val embed = EmbedBuilder()
                .setColor(0xF44336)
                .setTitle("Last Stacktrace")
                .setDescription("```java\n${stacktrace.take(3900)}\n```")
                .setTimestamp(java.time.Instant.now())
                .build()

            event.replyEmbeds(embed).setEphemeral(true).queue()
        } else {
            val embed = EmbedBuilder()
                .setColor(0xF44336)
                .setTitle("Last Stacktrace")
                .setDescription("Stacktrace too long. See attached file.")
                .setTimestamp(java.time.Instant.now())
                .build()

            event.replyEmbeds(embed)
                .addFiles(FileUpload.fromData(stacktrace.toByteArray(), "stacktrace.txt"))
                .setEphemeral(true)
                .queue()
        }
    }

    private fun isAdmin(event: SlashCommandInteractionEvent): Boolean {
        val member = event.member ?: return false
        val config = plugin.configManager
        return member.roles.any { it.id == config.adminRoleId } || member.id in config.adminUserIds
    }
}