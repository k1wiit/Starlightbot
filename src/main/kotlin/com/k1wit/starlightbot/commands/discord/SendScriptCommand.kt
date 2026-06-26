package com.k1wit.starlightbot.commands.discord

import com.k1wit.starlightbot.StarlightBot
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

class SendScriptCommand(private val plugin: StarlightBot) {

    fun handle(event: SlashCommandInteractionEvent) {
        if (!isAdmin(event)) {
            event.reply("You don't have permission to use this command.").setEphemeral(true).queue()
            return
        }

        val url = event.getOption("url")?.asString ?: return

        // Defer reply since fetching takes time
        event.deferReply(true).queue()

        plugin.scriptService.postScript(url, event.hook)
    }

    private fun isAdmin(event: SlashCommandInteractionEvent): Boolean {
        val member = event.member ?: return false
        val config = plugin.configManager
        return member.roles.any { it.id == config.adminRoleId } || member.id in config.adminUserIds
    }
}