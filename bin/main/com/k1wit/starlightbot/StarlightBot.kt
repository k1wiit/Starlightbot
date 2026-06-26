package com.k1wit.starlightbot

import com.k1wit.starlightbot.commands.discord.SlashCommandManager
import com.k1wit.starlightbot.config.ConfigManager
import com.k1wit.starlightbot.database.SQLiteManager
import com.k1wit.starlightbot.listener.minecraft.*
import com.k1wit.starlightbot.service.*
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.java.JavaPlugin

class StarlightBot : JavaPlugin() {

    companion object {
        lateinit var instance: StarlightBot
            private set
    }

    lateinit var configManager: ConfigManager
        private set
    lateinit var sqliteManager: SQLiteManager
        private set
    lateinit var discordBotService: DiscordBotService
        private set
    lateinit var loggingService: LoggingService
        private set
    lateinit var whitelistService: WhitelistService
        private set
    lateinit var ticketService: TicketService
        private set
    lateinit var playerCountService: PlayerCountService
        private set
    lateinit var antiSpamService: AntiSpamService
        private set
    lateinit var scriptService: ScriptService
        private set

    private val mm = MiniMessage.miniMessage()

    override fun onEnable() {
        instance = this

        printBanner()

        // 1. Load config
        configManager = ConfigManager(this)
        configManager.load()

        // 2. Initialize SQLite
        sqliteManager = SQLiteManager(this)
        sqliteManager.initialize()

        // 3. Initialize services
        whitelistService = WhitelistService(this)
        ticketService = TicketService(this)
        scriptService = ScriptService(this)
        antiSpamService = AntiSpamService(this)

        // 4. Start Discord bot ASYNCHRONOUSLY
        discordBotService = DiscordBotService(this)
        server.scheduler.runTaskAsynchronously(this, Runnable {
            try {
                discordBotService.start()
            } catch (e: Exception) {
                logger.severe("[StarlightBot] Failed to start Discord bot: ${e.message}")
                e.printStackTrace()
            }
        })

        // 5. Register Minecraft listeners
        registerMinecraftListeners()

        // 6. Start logging service (console appender)
        loggingService = LoggingService(this)
        loggingService.start()

        // 7. Player count service starts after JDA is ready (handled in DiscordBotService)
        playerCountService = PlayerCountService(this)

        consoleLog("<green>[StarlightBot] Plugin enabled successfully.")
    }

    override fun onDisable() {
        consoleLog("<yellow>[StarlightBot] Shutting down...")

        // Stop player count service
        if (::playerCountService.isInitialized) {
            playerCountService.stop()
        }

        // Stop logging service
        if (::loggingService.isInitialized) {
            loggingService.stop()
        }

        // Shutdown Discord bot
        if (::discordBotService.isInitialized) {
            discordBotService.shutdown()
        }

        // Close SQLite
        if (::sqliteManager.isInitialized) {
            sqliteManager.close()
        }

        consoleLog("<red>[StarlightBot] Plugin disabled.")
    }

    private fun registerMinecraftListeners() {
        val pm = server.pluginManager
        pm.registerEvents(PlayerJoinLeaveListener(this), this)
        pm.registerEvents(ChatListener(this), this)
        pm.registerEvents(DeathListener(this), this)
        pm.registerEvents(AdvancementListener(this), this)
    }

    fun consoleLog(message: String) {
        val component = mm.deserialize(message)
        server.consoleSender.sendMessage(component)
    }

    private fun printBanner() {
        val banner = """
            
<dark_gray> ____  _             _ _       _     _   ____        _   
<dark_gray>/ ___|| |_ __ _ _ __| (_) __ _| |__ | |_| __ )  ___ | |_ 
<dark_gray>\___ \| __/ _` | '__| | |/ _` | '_ \| __|  _ \ / _ \| __|
<dark_gray> ___) | || (_| | |  | | | (_| | | | | |_| |_) | (_) | |_ 
<dark_gray>|____/ \__\__,_|_|  |_|_|\__, |_| |_|\__|____/ \___/ \__|
<dark_gray>                          |___/                            
<gray>StarlightBot <white>v1.0.0 <gray>| by <aqua>K1wit
<yellow>Discord Bot: <white>Connecting...

        """.trimIndent()

        banner.lines().forEach { consoleLog(it) }
    }
}