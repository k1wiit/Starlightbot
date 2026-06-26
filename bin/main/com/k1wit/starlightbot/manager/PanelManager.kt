package com.k1wit.starlightbot.manager

import com.k1wit.starlightbot.StarlightBot
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu

class PanelManager(private val plugin: StarlightBot) {

    fun createInfoPanel(channel: TextChannel, onSuccess: () -> Unit) {
        val config = plugin.configManager

        val embed = EmbedBuilder()
            .setColor(0x1a1a2e)
            .setTitle(config.infoEmbedTitle)
            .apply {
                val gif = config.infoEmbedGifUrl
                if (gif.isNotBlank() && gif != "YOUR_GIF_URL") {
                    setImage(gif)
                }
            }
            .build()

        val buttons = ActionRow.of(
            Button.of(ButtonStyle.SECONDARY, "info_apply",      "How to apply"),
            Button.of(ButtonStyle.SECONDARY, "info_serverinfo", "MC server info"),
            Button.of(ButtonStyle.SECONDARY, "info_socials",    "Socials"),
            Button.of(ButtonStyle.SECONDARY, "info_extra",      "Extra info")
        )

        channel.sendMessageEmbeds(embed)
            .setComponents(buttons)
            .queue(
                { onSuccess() },
                { err ->
                    plugin.logger.warning("[StarlightBot] Failed to create info panel: ${err.message}")
                }
            )
    }

    fun createTicketPanel(channel: TextChannel, onSuccess: () -> Unit) {
        val embed = EmbedBuilder()
            .setColor(0x1a1a2e)
            .setTitle("Support Tickets")
            .setDescription("Need help? Select a category below to open a ticket.")
            .build()

        val selectMenu = StringSelectMenu.create("ticket_category_select")
            .setPlaceholder("Select a ticket category...")
            .addOption(
                "Application",
                "application",
                "Apply to join the server"
            )
            .addOption(
                "Support / Bug Report",
                "support",
                "Get help or report a bug"
            )
            .addOption(
                "Player Report",
                "player_report",
                "Report a player"
            )
            .addOption(
                "Other",
                "other",
                "Anything else"
            )
            .build()

        channel.sendMessageEmbeds(embed)
            .setComponents(ActionRow.of(selectMenu))
            .queue(
                { onSuccess() },
                { err ->
                    plugin.logger.warning("[StarlightBot] Failed to create ticket panel: ${err.message}")
                }
            )
    }
}