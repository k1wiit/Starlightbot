package com.k1wit.starlightbot.util

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import java.time.Instant

object EmbedFactory {

    // Color constants
    const val COLOR_JOIN = 0x4CAF50
    const val COLOR_LEAVE = 0xF44336
    const val COLOR_DEATH = 0x9E9E9E
    const val COLOR_ADVANCEMENT = 0xFFD700
    const val COLOR_WHITELIST = 0x2196F3
    const val COLOR_TICKET = 0x9C27B0
    const val COLOR_SERVER_START = 0x4CAF50
    const val COLOR_SERVER_STOP = 0xF44336
    const val COLOR_WARNING = 0xFF9800
    const val COLOR_ERROR = 0xF44336
    const val COLOR_COMMAND = 0x607D8B
    const val COLOR_DARK = 0x1a1a2e
    const val COLOR_INFO = 0x2196F3

    fun build(
        color: Int,
        title: String? = null,
        description: String? = null,
        fields: List<Triple<String, String, Boolean>> = emptyList(),
        footer: String? = null,
        timestamp: Boolean = false,
        imageUrl: String? = null,
        thumbnailUrl: String? = null
    ): MessageEmbed {
        return EmbedBuilder().apply {
            setColor(color)
            title?.let { setTitle(it) }
            description?.let { setDescription(it) }
            fields.forEach { (name, value, inline) -> addField(name, value, inline) }
            footer?.let { setFooter(it) }
            if (timestamp) setTimestamp(Instant.now())
            imageUrl?.let { setImage(it) }
            thumbnailUrl?.let { setThumbnail(it) }
        }.build()
    }

    fun playerJoin(playerName: String, onlineCount: Int, maxPlayers: Int): MessageEmbed {
        return build(
            color = COLOR_JOIN,
            title = "Player Joined",
            description = "**$playerName** has joined the server",
            footer = "Players Online: $onlineCount/$maxPlayers",
            timestamp = true
        )
    }

    fun playerLeave(playerName: String, onlineCount: Int, maxPlayers: Int): MessageEmbed {
        return build(
            color = COLOR_LEAVE,
            title = "Player Left",
            description = "**$playerName** has left the server",
            footer = "Players Online: $onlineCount/$maxPlayers",
            timestamp = true
        )
    }

    fun playerDeath(deathMessage: String): MessageEmbed {
        return build(
            color = COLOR_DEATH,
            title = "Player Death",
            description = deathMessage,
            timestamp = true
        )
    }

    fun advancement(playerName: String, advancementTitle: String, advancementDesc: String): MessageEmbed {
        return build(
            color = COLOR_ADVANCEMENT,
            title = "Advancement Unlocked",
            description = "**$playerName** has made the advancement **[$advancementTitle]**",
            footer = advancementDesc,
            timestamp = true
        )
    }

    fun whitelistChange(discordUserId: String, minecraftName: String, action: String): MessageEmbed {
        return build(
            color = COLOR_WHITELIST,
            title = "Whitelist $action",
            fields = listOf(
                Triple("Discord User", "<@$discordUserId>", true),
                Triple("Minecraft Name", "`$minecraftName`", true)
            ),
            timestamp = true
        )
    }

    fun ticketCreated(ticketNumber: Int, category: String, discordUserId: String): MessageEmbed {
        return build(
            color = COLOR_TICKET,
            title = "Ticket Created",
            fields = listOf(
                Triple("Ticket", "#$ticketNumber", true),
                Triple("Category", category.replaceFirstChar { it.uppercase() }, true),
                Triple("User", "<@$discordUserId>", true)
            ),
            timestamp = true
        )
    }

    fun ticketClosed(ticketNumber: Int, category: String, closedBy: String): MessageEmbed {
        return build(
            color = COLOR_TICKET,
            title = "Ticket Closed",
            fields = listOf(
                Triple("Ticket", "#$ticketNumber", true),
                Triple("Category", category.replaceFirstChar { it.uppercase() }, true),
                Triple("Closed by", "<@$closedBy>", true)
            ),
            timestamp = true
        )
    }

    fun error(title: String, description: String): MessageEmbed {
        return build(
            color = COLOR_ERROR,
            title = title,
            description = description,
            timestamp = true
        )
    }
}