package com.k1wit.starlightbot.config

import com.k1wit.starlightbot.StarlightBot
import org.bukkit.configuration.file.FileConfiguration

class ConfigManager(private val plugin: StarlightBot) {

    private var config: FileConfiguration = plugin.config

    fun load() {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        config = plugin.config
        plugin.consoleLog("<gray>[StarlightBot] Config loaded.")
    }

    fun reload() {
        plugin.reloadConfig()
        config = plugin.config
        plugin.consoleLog("<gray>[StarlightBot] Config reloaded.")
    }

    // Bot
    val botToken: String get() = config.getString("bot.token", "")!!
    val guildId: String get() = config.getString("bot.guild_id", "")!!

    // Channels
    val logChannelId: String get() = config.getString("channels.log", "")!!
    val ticketLogChannelId: String get() = config.getString("channels.ticket_log", "")!!
    val deletedMessageLogChannelId: String get() = config.getString("channels.deleted_message_log", "")!!
    val whitelistChannelId: String get() = config.getString("channels.whitelist", "")!!
    val infoChannelId: String get() = config.getString("channels.info", "")!!
    val ticketsChannelId: String get() = config.getString("channels.tickets", "")!!
    val scriptChannelId: String get() = config.getString("channels.script", "")!!
    val playerCountVoiceChannelId: String get() = config.getString("channels.player_count_voice", "")!!

    // Roles
    val adminRoleId: String get() = config.getString("roles.admin", "")!!

    // Admin Users
    val adminUserIds: List<String> get() = config.getStringList("admin_user_ids")

    // Server Info
    val serverName: String get() = config.getString("server.name", "Starlight")!!
    val serverAddress: String get() = config.getString("server.address", "play.example.com")!!
    val serverVersion: String get() = config.getString("server.version", "1.21.4")!!
    val serverMaxPlayers: Int get() = config.getInt("server.max_players", 30)
    val rulesChannelId: String get() = config.getString("server.rules_channel_id", "")!!

    // Socials
    val socialYoutube: String get() = config.getString("socials.youtube", "")!!
    val socialTiktok: String get() = config.getString("socials.tiktok", "")!!
    val socialInstagram: String get() = config.getString("socials.instagram", "")!!
    val socialTwitch: String get() = config.getString("socials.twitch", "")!!
    val socialTwitter: String get() = config.getString("socials.twitter", "")!!
    val socialDiscordInvite: String get() = config.getString("socials.discord_invite", "")!!
    val socialWebsite: String get() = config.getString("socials.website", "")!!

    // Info Embed
    val infoEmbedGifUrl: String get() = config.getString("info_embed.gif_url", "")!!
    val infoEmbedTitle: String get() = config.getString("info_embed.title", "Welcome!")!!

    // Extra Info Channels
    data class ExtraInfoChannel(val name: String, val channelId: String)
    val extraInfoChannels: List<ExtraInfoChannel>
        get() {
            val list = config.getMapList("extra_info_channels")
            return list.map { map ->
                ExtraInfoChannel(
                    name = map["name"]?.toString() ?: "",
                    channelId = map["channel_id"]?.toString() ?: ""
                )
            }
        }

    // Apply message
    val applyMessage: String get() = config.getString("apply_message", "Please whitelist yourself first.")!!

    // Anti Spam
    val antiSpamMaxMessages: Int get() = config.getInt("anti_spam.max_messages", 4)
    val antiSpamTimeWindowSeconds: Long get() = config.getLong("anti_spam.time_window_seconds", 8)
    val antiSpamTimeoutMinutes: Long get() = config.getLong("anti_spam.timeout_minutes", 10)

    // Logging
    val loggingFilterPatterns: List<String> get() = config.getStringList("logging.filter_patterns")

    // Player Count
    val playerCountUpdateIntervalMinutes: Long get() = config.getLong("player_count.update_interval_minutes", 5)
    val playerCountFormatOnline: String get() = config.getString("player_count.format_online", "MCOnline: %count%")!!
    val playerCountFormatOffline: String get() = config.getString("player_count.format_offline", "MCOnline: Offline")!!
}