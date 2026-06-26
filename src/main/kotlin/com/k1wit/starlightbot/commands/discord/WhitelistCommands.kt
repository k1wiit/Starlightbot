package com.k1wit.starlightbot.commands.discord

import com.k1wit.starlightbot.StarlightBot
import com.k1wit.starlightbot.service.WhitelistResult
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

class WhitelistCommands(private val plugin: StarlightBot) {

    // Public: /whitelist change <name>
    fun handlePublic(event: SlashCommandInteractionEvent) {
        when (event.subcommandName) {
            "change" -> handleChange(event)
            else -> event.reply("Unknown subcommand.").setEphemeral(true).queue()
        }
    }

    private fun handleChange(event: SlashCommandInteractionEvent) {
        val newName = event.getOption("name")?.asString ?: return
        event.deferReply(true).queue()

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val result = plugin.whitelistService.changeWhitelistEntry(event.user.id, newName)
            when (result) {
                is WhitelistResult.Success ->
                    event.hook.sendMessage(
                        "Your Minecraft name has been updated to `${result.minecraftName}` successfully."
                    ).setEphemeral(true).queue()
                is WhitelistResult.Error ->
                    event.hook.sendMessage(result.message).setEphemeral(true).queue()
            }
        })
    }

    // Admin: /wl list|remove|forceadd
    fun handleAdmin(event: SlashCommandInteractionEvent) {
        if (!isAdmin(event)) {
            event.reply("You don't have permission to use this command.").setEphemeral(true).queue()
            return
        }

        when (event.subcommandName) {
            "list"     -> handleList(event)
            "remove"   -> handleRemove(event)
            "forceadd" -> handleForceAdd(event)
            else -> event.reply("Unknown subcommand.").setEphemeral(true).queue()
        }
    }

    private fun handleList(event: SlashCommandInteractionEvent) {
        event.deferReply(true).queue()

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val entries = plugin.whitelistService.repository.getAll()

            if (entries.isEmpty()) {
                event.hook.sendMessage("No players are currently whitelisted.").setEphemeral(true).queue()
                return@Runnable
            }

            // Split into pages of 20
            val pageSize = 20
            val chunks = entries.chunked(pageSize)
            val firstChunk = chunks.first()

            val description = firstChunk.joinToString("\n") { entry ->
                "<@${entry.discordUserId}> → `${entry.minecraftName}`"
            }

            val embed = EmbedBuilder()
                .setColor(0x2196F3)
                .setTitle("Whitelisted Players (${entries.size} total)")
                .setDescription(description)
                .setFooter("Page 1/${chunks.size}")
                .build()

            event.hook.sendMessageEmbeds(embed).setEphemeral(true).queue()
        })
    }

    private fun handleRemove(event: SlashCommandInteractionEvent) {
        val name = event.getOption("name")?.asString ?: return
        event.deferReply(true).queue()

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val success = plugin.whitelistService.removeFromWhitelist(name)
            if (success) {
                event.hook.sendMessage("Successfully removed `$name` from the whitelist.").setEphemeral(true).queue()
            } else {
                event.hook.sendMessage("Player `$name` was not found in the whitelist.").setEphemeral(true).queue()
            }
        })
    }

    private fun handleForceAdd(event: SlashCommandInteractionEvent) {
        val discordId = event.getOption("discord_id")?.asString ?: return
        val mcName = event.getOption("minecraft_name")?.asString ?: return
        event.deferReply(true).queue()

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val result = plugin.whitelistService.forceAdd(discordId, mcName)
            when (result) {
                is WhitelistResult.Success ->
                    event.hook.sendMessage(
                        "Successfully force-added `${result.minecraftName}` for <@$discordId>."
                    ).setEphemeral(true).queue()
                is WhitelistResult.Error ->
                    event.hook.sendMessage(result.message).setEphemeral(true).queue()
            }
        })
    }

    private fun isAdmin(event: SlashCommandInteractionEvent): Boolean {
        val member = event.member ?: return false
        val config = plugin.configManager
        return member.roles.any { it.id == config.adminRoleId } || member.id in config.adminUserIds
    }
}