package com.k1wit.starlightbot.service

import com.k1wit.starlightbot.StarlightBot
import com.k1wit.starlightbot.database.tables.LevelRepository
import java.util.concurrent.ConcurrentHashMap

class LevelService(private val plugin: StarlightBot) {

    val repository = LevelRepository(plugin.sqliteManager)
    
    // Track when players join voice channels to calculate voice time
    private val voiceJoinTimes = ConcurrentHashMap<String, Long>()
    
    // Track when players join the Minecraft server to calculate playtime
    private val minecraftJoinTimes = ConcurrentHashMap<String, Long>()

    // Balanced experience rewards
    object ExpRewards {
        // Discord chat: 5 exp per message
        // This is conservative to prevent spam-leveling
        const val CHAT_MESSAGE = 5L
        
        // Voice time: 1 exp per minute in voice
        // This is rewarded periodically (every 5 minutes) to prevent high rates
        const val VOICE_PER_MINUTE = 1L
        
        // Minecraft playtime: 2 exp per minute of active play
        // This is rewarded periodically (every 5 minutes)
        const val MINECRAFT_PLAYTIME_PER_MINUTE = 2L
    }

    /**
     * Award experience for a Discord message
     * Should be called from chat listener
     */
    fun awardChatExp(discordUserId: String) {
        try {
            repository.addDiscordChatExp(discordUserId, ExpRewards.CHAT_MESSAGE)
        } catch (e: Exception) {
            plugin.logger.warning("[StarlightBot] Failed to award chat exp: ${e.message}")
        }
    }

    /**
     * Track when a player joins a voice channel
     * Should be called from VoiceChannelJoinListener
     */
    fun onVoiceChannelJoin(discordUserId: String) {
        voiceJoinTimes[discordUserId] = System.currentTimeMillis()
    }

    /**
     * Track when a player leaves a voice channel and award voice time exp
     * Should be called from VoiceChannelLeaveListener
     */
    fun onVoiceChannelLeave(discordUserId: String) {
        val joinTime = voiceJoinTimes.remove(discordUserId) ?: return
        val voiceMinutes = (System.currentTimeMillis() - joinTime) / (1000 * 60)
        
        if (voiceMinutes > 0) {
            try {
                val exp = voiceMinutes * ExpRewards.VOICE_PER_MINUTE
                repository.addVoiceTime(discordUserId, voiceMinutes)
            } catch (e: Exception) {
                plugin.logger.warning("[StarlightBot] Failed to award voice time: ${e.message}")
            }
        }
    }

    /**
     * Track when a player joins the Minecraft server
     * Should be called from PlayerJoinListener
     */
    fun onMinecraftPlayerJoin(discordUserId: String) {
        minecraftJoinTimes[discordUserId] = System.currentTimeMillis()
    }

    /**
     * Track when a player leaves the Minecraft server and award playtime exp
     * Should be called from PlayerLeaveListener
     */
    fun onMinecraftPlayerLeave(discordUserId: String) {
        val joinTime = minecraftJoinTimes.remove(discordUserId) ?: return
        val playtimeMinutes = (System.currentTimeMillis() - joinTime) / (1000 * 60)
        
        if (playtimeMinutes > 0) {
            try {
                val exp = playtimeMinutes * ExpRewards.MINECRAFT_PLAYTIME_PER_MINUTE
                repository.addMinecraftPlaytime(discordUserId, playtimeMinutes)
            } catch (e: Exception) {
                plugin.logger.warning("[StarlightBot] Failed to award minecraft playtime: ${e.message}")
            }
        }
    }

    /**
     * Get player level information
     */
    fun getPlayerLevel(discordUserId: String) = repository.findByDiscordUserId(discordUserId)

    /**
     * Get all players ranked by level and experience
     */
    fun getLeaderboard() = repository.getAllPlayers()

    /**
     * Link a Minecraft player name to their Discord account
     */
    fun linkMinecraftName(discordUserId: String, minecraftName: String) {
        try {
            repository.linkMinecraftName(discordUserId, minecraftName)
        } catch (e: Exception) {
            plugin.logger.warning("[StarlightBot] Failed to link minecraft name: ${e.message}")
        }
    }
}
