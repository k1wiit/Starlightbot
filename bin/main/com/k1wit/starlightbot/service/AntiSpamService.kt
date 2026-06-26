package com.k1wit.starlightbot.service

import com.k1wit.starlightbot.StarlightBot
import com.k1wit.starlightbot.util.EmbedFactory
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class UserSpamData(
    val messageTimes: MutableList<Long> = mutableListOf(),
    val recentMessages: MutableList<String> = mutableListOf()
)

class AntiSpamService(private val plugin: StarlightBot) {

    private val userDataMap = ConcurrentHashMap<String, UserSpamData>()
    private val timedOutUsers = ConcurrentHashMap<String, Long>()
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "StarlightBot-AntiSpam").also { it.isDaemon = true }
    }

    init {
        // Cleanup old data every 60 seconds
        executor.scheduleAtFixedRate({
            val now = System.currentTimeMillis()
            val windowMs = plugin.configManager.antiSpamTimeWindowSeconds * 1000
            userDataMap.forEach { (userId, data) ->
                synchronized(data) {
                    data.messageTimes.removeAll { it < now - windowMs }
                    if (data.messageTimes.isEmpty()) {
                        userDataMap.remove(userId)
                    }
                }
            }
            // Clean up timed out users cache
            timedOutUsers.entries.removeIf { (_, time) -> now - time > 3_600_000 }
        }, 60, 60, TimeUnit.SECONDS)
    }

    /**
     * Returns true if message should be blocked (spam detected)
     */
    fun processMessage(member: Member, message: Message): Boolean {
        // Admins are exempt
        if (isAdmin(member)) return false

        val userId = member.id
        val now = System.currentTimeMillis()
        val windowMs = plugin.configManager.antiSpamTimeWindowSeconds * 1000
        val maxMessages = plugin.configManager.antiSpamMaxMessages
        val content = message.contentRaw.trim()

        val data = userDataMap.getOrPut(userId) { UserSpamData() }

        synchronized(data) {
            // Remove old timestamps
            data.messageTimes.removeAll { it < now - windowMs }
            data.messageTimes.add(now)

            // Track recent messages for duplicate detection
            data.recentMessages.add(content)
            if (data.recentMessages.size > 10) {
                data.recentMessages.removeAt(0)
            }

            // Check rate limit
            val isRateLimited = data.messageTimes.size > maxMessages

            // Check duplicate (same message 3+ times in last 30s)
            val duplicateCount = data.recentMessages.count { it == content }
            val isDuplicate = duplicateCount >= 3

            if (isRateLimited || isDuplicate) {
                timeoutUser(member, message, if (isDuplicate) "duplicate messages" else "spam")
                return true
            }
        }

        return false
    }

    private fun timeoutUser(member: Member, triggerMessage: Message, reason: String) {
        val userId = member.id

        // Prevent double timeout
        if (timedOutUsers.containsKey(userId)) return
        timedOutUsers[userId] = System.currentTimeMillis()

        val timeoutMinutes = plugin.configManager.antiSpamTimeoutMinutes

        // Timeout the member
        member.timeoutFor(Duration.ofMinutes(timeoutMinutes)).queue(null) { err ->
            plugin.logger.warning("[StarlightBot] Could not timeout ${member.user.name}: ${err.message}")
        }

        // Delete the triggering message
        triggerMessage.delete().queue(null) { }

        // DM the user
        member.user.openPrivateChannel().queue({ channel ->
            channel.sendMessage(
                "You have been timed out for $timeoutMinutes minutes due to $reason."
            ).queue()
        }, { })

        // Log to Discord
        val jda = plugin.discordBotService.jda ?: return
        val logChannelId = plugin.configManager.logChannelId
        if (logChannelId.isBlank()) return
        try {
            val logChannel = jda.getTextChannelById(logChannelId) ?: return
            val embed = EmbedFactory.build(
                color = EmbedFactory.COLOR_WARNING,
                title = "Anti-Spam: User Timed Out",
                fields = listOf(
                    Triple("User", "${member.user.name} (<@$userId>)", true),
                    Triple("Reason", reason.replaceFirstChar { it.uppercase() }, true),
                    Triple("Duration", "$timeoutMinutes minutes", true)
                ),
                timestamp = true
            )
            logChannel.sendMessageEmbeds(embed).queue()
        } catch (_: Exception) {}

        // Clear their data
        userDataMap.remove(userId)
    }

    private fun isAdmin(member: Member): Boolean {
        val config = plugin.configManager
        return member.roles.any { it.id == config.adminRoleId }
            || member.id in config.adminUserIds
    }

    fun shutdown() {
        executor.shutdown()
    }
}