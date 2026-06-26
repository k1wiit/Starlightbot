package com.k1wit.starlightbot.service

import com.k1wit.starlightbot.StarlightBot
import com.k1wit.starlightbot.commands.discord.SlashCommandManager
import com.k1wit.starlightbot.listener.discord.*
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import java.util.concurrent.TimeUnit

class DiscordBotService(private val plugin: StarlightBot) {

    var jda: JDA? = null
        private set

    fun start() {
        val token = plugin.configManager.botToken
        if (token.isBlank() || token == "YOUR_BOT_TOKEN") {
            plugin.logger.severe("[StarlightBot] Bot token is not configured! Please set it in config.yml")
            return
        }

        try {
            jda = JDABuilder.createDefault(token)
                .setActivity(Activity.watching("the server"))
                .enableIntents(
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT,
                    GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.DIRECT_MESSAGES,
                    GatewayIntent.GUILD_MESSAGE_REACTIONS
                )
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
                .addEventListeners(
                    ReadyListener(),
                    WhitelistMessageListener(plugin),
                    ButtonInteractionListener(plugin),
                    SelectMenuListener(plugin),
                    AntiSpamListener(plugin)
                )
                .build()

            jda!!.awaitReady()

            // Register slash commands after JDA is ready
            val slashCommandManager = SlashCommandManager(plugin, jda!!)
            slashCommandManager.registerCommands()

            // Start player count service now that JDA is ready
            plugin.playerCountService.start()

            // Send server start embed to log channel
            sendServerStartEmbed()

        } catch (e: Exception) {
            plugin.logger.severe("[StarlightBot] Failed to connect to Discord: ${e.message}")
            e.printStackTrace()
        }
    }

    fun shutdown() {
        val jda = this.jda ?: return
        try {
            sendServerStopMessage()
            // Give it a moment to send the message
            Thread.sleep(1500)
            jda.shutdown()
            if (!jda.awaitShutdown(10, TimeUnit.SECONDS)) {
                jda.shutdownNow()
                jda.awaitShutdown()
            }
            plugin.consoleLog("<gray>[StarlightBot] Discord bot disconnected.")
        } catch (e: Exception) {
            plugin.logger.warning("[StarlightBot] Error during JDA shutdown: ${e.message}")
        }
    }

    private fun sendServerStartEmbed() {
        val jda = this.jda ?: return
        val channelId = plugin.configManager.logChannelId
        if (channelId.isBlank()) return

        try {
            val channel = jda.getTextChannelById(channelId) ?: return
            val embed = plugin.buildEmbed {
                setColor(0x4CAF50)
                setTitle("Server Started")
                setDescription("The Minecraft server has started and is ready to accept connections.")
                setFooter("StarlightBot v1.0.0")
                setTimestamp(java.time.Instant.now())
            }
            channel.sendMessageEmbeds(embed).queue()
        } catch (e: Exception) {
            plugin.logger.warning("[StarlightBot] Could not send start embed: ${e.message}")
        }
    }

    private fun sendServerStopMessage() {
        val jda = this.jda ?: return
        val channelId = plugin.configManager.logChannelId
        if (channelId.isBlank()) return

        try {
            val channel = jda.getTextChannelById(channelId) ?: return
            val embed = plugin.buildEmbed {
                setColor(0xF44336)
                setTitle("Server Stopping")
                setDescription("The Minecraft server is shutting down.")
                setTimestamp(java.time.Instant.now())
            }
            channel.sendMessageEmbeds(embed).complete() // complete() here to ensure it sends before shutdown
        } catch (e: Exception) {
            plugin.logger.warning("[StarlightBot] Could not send stop embed: ${e.message}")
        }
    }

    fun getGuild() = jda?.getGuildById(plugin.configManager.guildId)

    inner class ReadyListener : ListenerAdapter() {
        override fun onReady(event: ReadyEvent) {
            val guild = event.jda.getGuildById(plugin.configManager.guildId)
            plugin.server.scheduler.runTask(plugin, Runnable {
                plugin.consoleLog("<green>[StarlightBot] Discord Bot: Connected as <white>${event.jda.selfUser.name}")
                plugin.consoleLog("<green>[StarlightBot] Guild: <white>${guild?.name ?: "Unknown"} <gray>(ID: ${plugin.configManager.guildId})")
                plugin.consoleLog("<green>[StarlightBot] All systems operational.")
            })
        }
    }
}

// Extension function for building embeds cleanly
fun StarlightBot.buildEmbed(block: net.dv8tion.jda.api.EmbedBuilder.() -> Unit): net.dv8tion.jda.api.entities.MessageEmbed {
    return net.dv8tion.jda.api.EmbedBuilder().apply(block).build()
}