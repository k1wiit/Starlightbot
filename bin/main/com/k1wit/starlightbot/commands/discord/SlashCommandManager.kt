package com.k1wit.starlightbot.commands.discord

import com.k1wit.starlightbot.StarlightBot
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData

class SlashCommandManager(private val plugin: StarlightBot, private val jda: JDA) : ListenerAdapter() {

    private val setStatusCommand = SetStatusCommand(plugin)
    private val sendScriptCommand = SendScriptCommand(plugin)
    private val lastStacktraceCommand = LastStacktraceCommand(plugin)
    private val crashReportCommand = CrashReportCommand(plugin)
    private val whitelistCommands = WhitelistCommands(plugin)
    private val setupCommand = SetupCommand(plugin)
    private val reloadConfigCommand = ReloadConfigCommand(plugin)
    private val publicCommands = PublicCommands(plugin)

    fun registerCommands() {
        val guildId = plugin.configManager.guildId.trim()
        if (guildId.isBlank() || guildId == "YOUR_GUILD_ID") {
            plugin.logger.warning("[StarlightBot] Guild ID is not configured. Slash commands will not be deployed.")
            plugin.consoleLog("<red>[StarlightBot] Guild ID is not configured. Slash commands will not be deployed.")
            return
        }

        val guild = jda.getGuildById(guildId) ?: run {
            plugin.logger.warning("[StarlightBot] Guild not found for slash command deployment. Check guild_id in config.")
            plugin.consoleLog("<red>[StarlightBot] Guild not found for slash command deployment. Check guild_id in config.")
            return
        }

        plugin.consoleLog("<gray>[StarlightBot] Deploying Discord slash commands to <white>${guild.name}<gray>...")

        val commands = listOf(
            // Public
            Commands.slash("help", "Shows all available commands"),
            Commands.slash("serverinfo", "Shows server information"),
            Commands.slash("online", "Shows the list of online players"),
            Commands.slash("whitelist", "Whitelist management")
                .addSubcommands(
                    SubcommandData("change", "Change your whitelisted Minecraft name")
                        .addOption(OptionType.STRING, "name", "Your new Minecraft username", true)
                ),

            // Admin
            Commands.slash("setstatus", "Change the bot's activity status")
                .addOption(OptionType.STRING, "type", "Activity type", true)
                .addOption(OptionType.STRING, "text", "Status text", true),

            Commands.slash("sendscript", "Fetch and post a Google Doc as a script")
                .addOption(OptionType.STRING, "url", "Google Docs URL", true),

            Commands.slash("laststacktrace", "Show the last recorded stacktrace"),
            Commands.slash("crashreport", "Send the latest crash report file"),

            Commands.slash("wl", "Whitelist admin commands")
                .addSubcommands(
                    SubcommandData("list", "List all whitelisted players"),
                    SubcommandData("remove", "Remove a player from whitelist")
                        .addOption(OptionType.STRING, "name", "Minecraft username to remove", true),
                    SubcommandData("forceadd", "Manually whitelist a player")
                        .addOption(OptionType.STRING, "discord_id", "Discord User ID", true)
                        .addOption(OptionType.STRING, "minecraft_name", "Minecraft username", true)
                ),

            Commands.slash("setup", "Setup panels")
                .addSubcommands(
                    SubcommandData("info-panel", "Create the info panel in this channel"),
                    SubcommandData("ticket-panel", "Create the ticket panel in this channel")
                ),

            Commands.slash("reloadconfig", "Reload the plugin configuration")
        )

        guild.updateCommands()
            .addCommands(commands)
            .queue(
                {
                    plugin.consoleLog("<green>[StarlightBot] Slash commands deployed successfully (${commands.size} commands).")
                    plugin.logger.info("[StarlightBot] Slash commands deployed successfully (${commands.size} commands).")
                },
                { err ->
                    plugin.logger.warning("[StarlightBot] Failed to register commands: ${err.message}")
                    plugin.consoleLog("<red>[StarlightBot] Failed to register commands: ${err.message}")
                }
            )

        // Register this as a listener for command events
        jda.addEventListener(this)
        plugin.consoleLog("<gray>[StarlightBot] Slash command listener registered.")
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        when (event.name) {
            "help"           -> publicCommands.handleHelp(event)
            "serverinfo"     -> publicCommands.handleServerInfo(event)
            "online"         -> publicCommands.handleOnline(event)
            "whitelist"      -> whitelistCommands.handlePublic(event)
            "setstatus"      -> setStatusCommand.handle(event)
            "sendscript"     -> sendScriptCommand.handle(event)
            "laststacktrace" -> lastStacktraceCommand.handle(event)
            "crashreport"    -> crashReportCommand.handle(event)
            "wl"             -> whitelistCommands.handleAdmin(event)
            "setup"          -> setupCommand.handle(event)
            "reloadconfig"   -> reloadConfigCommand.handle(event)
        }
    }
}