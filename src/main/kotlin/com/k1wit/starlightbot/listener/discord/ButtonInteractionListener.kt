package com.k1wit.starlightbot.listener.discord

import com.k1wit.starlightbot.StarlightBot
import com.k1wit.starlightbot.util.EmbedFactory
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle

class ButtonInteractionListener(private val plugin: StarlightBot) : ListenerAdapter() {

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val buttonId = event.componentId

        when {
            buttonId == "info_apply"      -> handleApply(event)
            buttonId == "info_serverinfo" -> handleServerInfo(event)
            buttonId == "info_socials"    -> handleSocials(event)
            buttonId == "info_extra"      -> handleExtraInfo(event)
            buttonId.startsWith("ticket_claim_") -> handleTicketClaim(event, buttonId)
            buttonId.startsWith("ticket_close_") -> handleTicketClose(event, buttonId)
        }
    }

    // ============================================================
    // INFO PANEL BUTTONS
    // ============================================================

    private fun handleApply(event: ButtonInteractionEvent) {
        val embed = EmbedBuilder()
            .setColor(0x1a1a2e)
            .setTitle("How to Apply")
            .setDescription(plugin.configManager.applyMessage)
            .build()

        sendDmOrEphemeral(event, embed)
    }

    private fun handleServerInfo(event: ButtonInteractionEvent) {
        val config = plugin.configManager
        val embed = EmbedBuilder()
            .setColor(0x1a1a2e)
            .setTitle("Minecraft Server Info")
            .addField("Server Name", config.serverName, true)
            .addField("Address", "`${config.serverAddress}`", true)
            .addField("Version", config.serverVersion, true)
            .addField("Max Players", config.serverMaxPlayers.toString(), true)
            .addField("Rules", "<#${config.rulesChannelId}>", true)
            .build()

        sendDmOrEphemeral(event, embed)
    }

    private fun handleSocials(event: ButtonInteractionEvent) {
        val config = plugin.configManager
        val socials = buildString {
            if (config.socialYoutube.isNotBlank())      appendLine("**YouTube:** [Link](${config.socialYoutube})")
            if (config.socialTiktok.isNotBlank())       appendLine("**TikTok:** [Link](${config.socialTiktok})")
            if (config.socialInstagram.isNotBlank())    appendLine("**Instagram:** [Link](${config.socialInstagram})")
            if (config.socialTwitch.isNotBlank())       appendLine("**Twitch:** [Link](${config.socialTwitch})")
            if (config.socialTwitter.isNotBlank())      appendLine("**Twitter/X:** [Link](${config.socialTwitter})")
            if (config.socialDiscordInvite.isNotBlank()) appendLine("**Discord:** [Invite](${config.socialDiscordInvite})")
            if (config.socialWebsite.isNotBlank())      appendLine("**Website:** [Link](${config.socialWebsite})")
        }.trim()

        val description = if (socials.isBlank()) "No socials configured yet." else socials

        val embed = EmbedBuilder()
            .setColor(0x1a1a2e)
            .setTitle("Our Socials")
            .setDescription(description)
            .build()

        sendDmOrEphemeral(event, embed)
    }

    private fun handleExtraInfo(event: ButtonInteractionEvent) {
        val channels = plugin.configManager.extraInfoChannels
        val description = if (channels.isEmpty()) {
            "No extra info channels configured."
        } else {
            channels.joinToString("\n") { "**${it.name}:** <#${it.channelId}>" }
        }

        val embed = EmbedBuilder()
            .setColor(0x1a1a2e)
            .setTitle("Extra Information")
            .setDescription(description)
            .build()

        sendDmOrEphemeral(event, embed)
    }

    private fun sendDmOrEphemeral(event: ButtonInteractionEvent, embed: MessageEmbed) {
        event.user.openPrivateChannel().queue({ channel ->
            channel.sendMessageEmbeds(embed).queue(
                {
                    event.reply("Check your DMs!").setEphemeral(true).queue()
                },
                {
                    event.replyEmbeds(embed).setEphemeral(true).queue()
                }
            )
        }, {
            // Can't open DM
            event.replyEmbeds(embed).setEphemeral(true).queue()
        })
    }

    // ============================================================
    // TICKET BUTTONS
    // ============================================================

    private fun handleTicketClaim(event: ButtonInteractionEvent, buttonId: String) {
        val ticketId = buttonId.removePrefix("ticket_claim_").toIntOrNull() ?: return
        val member = event.member ?: return

        // Admin check
        if (!isAdmin(member)) {
            event.reply("Only staff members can claim tickets.").setEphemeral(true).queue()
            return
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val success = plugin.ticketService.claimTicket(ticketId, member.id, member.effectiveName)

            if (success) {
                // Update button to show claimed
                val disabledButton = Button.of(
                    ButtonStyle.SECONDARY,
                    "ticket_claimed_$ticketId",
                    "Claimed by ${member.effectiveName}"
                ).withDisabled(true)

                val closeButton = Button.of(ButtonStyle.DANGER, "ticket_close_$ticketId", "Close Ticket")

                event.editComponents(
                    net.dv8tion.jda.api.interactions.components.ActionRow.of(disabledButton, closeButton)
                ).queue()
            } else {
                event.reply("Could not claim ticket. It may already be claimed.").setEphemeral(true).queue()
            }
        })
    }

    private fun handleTicketClose(event: ButtonInteractionEvent, buttonId: String) {
        val ticketId = buttonId.removePrefix("ticket_close_").toIntOrNull() ?: return
        val member = event.member ?: return

        // Check: admin OR ticket owner
        val ticket = plugin.ticketService.repository.findById(ticketId)
        if (ticket == null) {
            event.reply("Ticket not found.").setEphemeral(true).queue()
            return
        }

        val isOwner = ticket.discordUserId == member.id
        val isAdminUser = isAdmin(member)

        if (!isOwner && !isAdminUser) {
            event.reply("Only the ticket creator or staff can close this ticket.").setEphemeral(true).queue()
            return
        }

        event.reply("Closing ticket... A transcript will be posted to the log channel.").setEphemeral(true).queue()

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            plugin.ticketService.closeTicket(ticketId, member.id, member.effectiveName)
        })
    }

    private fun isAdmin(member: net.dv8tion.jda.api.entities.Member): Boolean {
        val config = plugin.configManager
        return member.roles.any { it.id == config.adminRoleId }
            || member.id in config.adminUserIds
    }
}