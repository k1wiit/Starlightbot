package com.k1wit.starlightbot.commands.discord

import com.k1wit.starlightbot.StarlightBot
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

class ReloadConfigCommand(private val plugin: StarlightBot) {

    fun handle(event: SlashCommandInteractionEvent) {
        if (!isAdmin(event)) {
            event.reply("You don't have permission to use this command.").setEphemeral(true).queue()
            return
        }

        try {
            plugin.configManager.reload()
            event.reply("Configuration reloaded successfully. Note: Token changes require a server restart.")
                .setEphemeral(true).queue()
        } catch (e: Exception) {
            event.reply("Failed to reload config: ${e.message}").setEphemeral(true).queue()
        }
    }

    private fun isAdmin(event: SlashCommandInteractionEvent): Boolean {
        val member = event.member ?: return false
        val config = plugin.configManager
        return member.roles.any { it.id == config.adminRoleId } || member.id in config.adminUserIds
    }
}