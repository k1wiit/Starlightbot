package com.k1wit.starlightbot.listener.discord

import com.k1wit.starlightbot.StarlightBot
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class SelectMenuListener(private val plugin: StarlightBot) : ListenerAdapter() {

    override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
        if (event.componentId != "ticket_category_select") return

        val selectedValue = event.values.firstOrNull() ?: return
        val user = event.user
        val member = event.member ?: return

        // Check if user already has an open ticket
        val existingTicket = plugin.ticketService.repository.findOpenByUserId(user.id)
        if (existingTicket != null) {
            event.reply(
                "You already have an open ticket (Ticket #${existingTicket.ticketNumber.toString().padStart(4, '0')}). " +
                "Please close it before opening a new one."
            ).setEphemeral(true).queue()
            return
        }

        event.reply("Creating your ticket...").setEphemeral(true).queue()

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            plugin.ticketService.createTicket(user.id, member.effectiveName, selectedValue)
        })
    }
}