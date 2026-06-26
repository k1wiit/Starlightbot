package com.k1wit.starlightbot.commands.discord

import com.k1wit.starlightbot.StarlightBot
import com.k1wit.starlightbot.manager.PanelManager
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

class SetupCommand(private val plugin: StarlightBot) {

    private val panelManager = PanelManager(plugin)

    fun handle(event: SlashCommandInteractionEvent) {
        if (!isAdmin(event)) {
            event.reply("You don't have permission to use this command.").setEphemeral(true).queue()
            return
        }

        val textChannel = event.channel.asTextChannel()

        when (event.subcommandName) {
            "info-panel" -> {
                event.deferReply(true).queue()
                panelManager.createInfoPanel(textChannel) {
                    event.hook.sendMessage("Info panel created successfully!").setEphemeral(true).queue()
                }
            }
            "ticket-panel" -> {
                event.deferReply(true).queue()
                panelManager.createTicketPanel(textChannel) {
                    event.hook.sendMessage("Ticket panel created successfully!").setEphemeral(true).queue()
                }
            }
            else -> event.reply("Unknown subcommand.").setEphemeral(true).queue()
        }
    }

    private fun isAdmin(event: SlashCommandInteractionEvent): Boolean {
        val member = event.member ?: return false
        val config = plugin.configManager
        return member.roles.any { it.id == config.adminRoleId } || member.id in config.adminUserIds
    }
}