package com.k1wit.starlightbot.commands.discord

import com.k1wit.starlightbot.StarlightBot
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

class SetStatusCommand(private val plugin: StarlightBot) {

    fun handle(event: SlashCommandInteractionEvent) {
        if (!isAdmin(event)) {
            event.reply("You don't have permission to use this command.").setEphemeral(true).queue()
            return
        }

        val type = event.getOption("type")?.asString ?: return
        val text = event.getOption("text")?.asString ?: return

        val activity = when (type.lowercase()) {
            "playing"   -> Activity.playing(text)
            "watching"  -> Activity.watching(text)
            "listening" -> Activity.listening(text)
            "competing" -> Activity.competing(text)
            else -> {
                event.reply("Invalid type. Use: `playing`, `watching`, `listening`, or `competing`.")
                    .setEphemeral(true).queue()
                return
            }
        }

        plugin.discordBotService.jda?.presence?.activity = activity

        event.reply("Status updated to: **${type.replaceFirstChar { it.uppercase() }}** $text")
            .setEphemeral(true).queue()
    }

    private fun isAdmin(event: SlashCommandInteractionEvent): Boolean {
        val member = event.member ?: return false
        val config = plugin.configManager
        return member.roles.any { it.id == config.adminRoleId }
            || member.id in config.adminUserIds
    }
}