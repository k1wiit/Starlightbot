package com.k1wit.starlightbot.commands.discord

import com.k1wit.starlightbot.StarlightBot
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.bukkit.Bukkit
import java.util.concurrent.CompletableFuture

class PublicCommands(private val plugin: StarlightBot) {

    fun handleHelp(event: SlashCommandInteractionEvent) {
        val embed = EmbedBuilder()
            .setColor(0x1a1a2e)
            .setTitle("StarlightBot Commands")
            .addField(
                "Public Commands",
                """
                `/help` - Show this menu
                `/serverinfo` - Server information
                `/online` - Online players
                `/whitelist change <name>` - Change your MC name
                """.trimIndent(),
                false
            )
            .addField(
                "Staff Commands",
                """
                `/setstatus <type> <text>` - Change bot status
                `/sendscript <url>` - Post Google Doc as script
                `/laststacktrace` - Last error stacktrace
                `/crashreport` - Latest crash report
                `/wl list` - List whitelisted players
                `/wl remove <name>` - Remove from whitelist
                `/wl forceadd <id> <name>` - Force whitelist
                `/setup info-panel` - Create info panel
                `/setup ticket-panel` - Create ticket panel
                `/reloadconfig` - Reload config
                """.trimIndent(),
                false
            )
            .build()

        event.replyEmbeds(embed).setEphemeral(true).queue()
    }

    fun handleServerInfo(event: SlashCommandInteractionEvent) {
        val config = plugin.configManager

        // Get player count on main thread
        val future = CompletableFuture<Int>()
        plugin.server.scheduler.runTask(plugin, Runnable {
            future.complete(Bukkit.getOnlinePlayers().size)
        })

        val onlineCount = future.get()

        val embed = EmbedBuilder()
            .setColor(0x1a1a2e)
            .setTitle("${config.serverName} - Server Info")
            .addField("Address", "`${config.serverAddress}`", true)
            .addField("Version", config.serverVersion, true)
            .addField("Players", "$onlineCount/${config.serverMaxPlayers}", true)
            .setTimestamp(java.time.Instant.now())
            .build()

        event.replyEmbeds(embed).queue()
    }

    fun handleOnline(event: SlashCommandInteractionEvent) {
        val future = CompletableFuture<List<String>>()
        plugin.server.scheduler.runTask(plugin, Runnable {
            future.complete(Bukkit.getOnlinePlayers().map { it.name })
        })

        val players = future.get()

        val embed = EmbedBuilder()
            .setColor(0x1a1a2e)
            .setTitle("Online Players (${players.size}/${plugin.configManager.serverMaxPlayers})")
            .setDescription(
                if (players.isEmpty()) "No players online."
                else players.joinToString("\n") { "- $it" }
            )
            .setTimestamp(java.time.Instant.now())
            .build()

        event.replyEmbeds(embed).queue()
    }
}