package com.k1wit.starlightbot.service

import com.k1wit.starlightbot.StarlightBot
import com.k1wit.starlightbot.database.tables.WhitelistRepository
import com.k1wit.starlightbot.util.EmbedFactory
import com.k1wit.starlightbot.util.MojangAPI
import net.dv8tion.jda.api.entities.User
import org.bukkit.Bukkit

sealed class WhitelistResult {
    data class Success(val minecraftName: String, val uuid: String) : WhitelistResult()
    data class Error(val message: String) : WhitelistResult()
}

class WhitelistService(private val plugin: StarlightBot) {

    val repository = WhitelistRepository(plugin.sqliteManager)

    /**
     * Full whitelist flow: validate → mojang check → dupe check → add
     * Must be called from an async thread!
     */
    fun processWhitelistRequest(discordUserId: String, inputName: String): WhitelistResult {
        // 1. Mojang check
        val profile = try {
            MojangAPI.getProfile(inputName)
        } catch (e: Exception) {
            plugin.logger.warning("[StarlightBot] Mojang API error: ${e.message}")
            return WhitelistResult.Error("Could not verify Minecraft username. Please try again later.")
        }

        if (profile == null) {
            return WhitelistResult.Error(
                "The Minecraft username `$inputName` does not exist. Please check for typos."
            )
        }

        // 2. Check if this Discord user already has a whitelist entry
        val existingByDiscord = repository.findByDiscordId(discordUserId)
        if (existingByDiscord != null) {
            return WhitelistResult.Error(
                "You already have `${existingByDiscord.minecraftName}` whitelisted. " +
                "Use `/whitelist change <new_name>` to update."
            )
        }

        // 3. Check if MC name is already taken by someone else
        val existingByName = repository.findByMinecraftName(profile.name)
        if (existingByName != null) {
            return WhitelistResult.Error(
                "The username `${profile.name}` is already whitelisted by another user."
            )
        }

        // 4. Add to DB
        val added = repository.add(discordUserId, profile.name, profile.uuid)
        if (!added) {
            return WhitelistResult.Error("Database error. Please try again or contact an admin.")
        }

        // 5. Add to Minecraft whitelist (must be on main thread)
        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist add ${profile.name}")
        })

        // 6. Log to Discord
        sendWhitelistLog(discordUserId, profile.name, "Added")

        return WhitelistResult.Success(profile.name, profile.uuid)
    }

    fun changeWhitelistEntry(discordUserId: String, newName: String): WhitelistResult {
        // 1. Check user has existing entry
        val existing = repository.findByDiscordId(discordUserId)
            ?: return WhitelistResult.Error("You don't have a whitelisted account. Write your Minecraft name in the whitelist channel.")

        // 2. Mojang check
        val profile = try {
            MojangAPI.getProfile(newName)
        } catch (e: Exception) {
            return WhitelistResult.Error("Could not verify Minecraft username. Please try again later.")
        }

        if (profile == null) {
            return WhitelistResult.Error("The Minecraft username `$newName` does not exist.")
        }

        // 3. Check name not taken by someone else
        val existingByName = repository.findByMinecraftName(profile.name)
        if (existingByName != null && existingByName.discordUserId != discordUserId) {
            return WhitelistResult.Error("The username `${profile.name}` is already whitelisted by another user.")
        }

        // 4. Update DB
        repository.updateMinecraftName(discordUserId, profile.name, profile.uuid)

        // 5. Update Minecraft whitelist
        val oldName = existing.minecraftName
        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist remove $oldName")
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist add ${profile.name}")
        })

        sendWhitelistLog(discordUserId, profile.name, "Changed (was: $oldName)")

        return WhitelistResult.Success(profile.name, profile.uuid)
    }

    fun forceAdd(discordUserId: String, minecraftName: String): WhitelistResult {
        val profile = try {
            MojangAPI.getProfile(minecraftName)
        } catch (e: Exception) {
            return WhitelistResult.Error("Could not verify Minecraft username: ${e.message}")
        }

        if (profile == null) {
            return WhitelistResult.Error("The Minecraft username `$minecraftName` does not exist.")
        }

        // Remove existing entry for this Discord user if any
        repository.removeByDiscordId(discordUserId)

        val added = repository.add(discordUserId, profile.name, profile.uuid)
        if (!added) return WhitelistResult.Error("Database error.")

        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist add ${profile.name}")
        })

        sendWhitelistLog(discordUserId, profile.name, "Force Added")
        return WhitelistResult.Success(profile.name, profile.uuid)
    }

    fun removeFromWhitelist(minecraftName: String): Boolean {
        val entry = repository.findByMinecraftName(minecraftName) ?: return false
        repository.remove(minecraftName)
        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist remove $minecraftName")
        })
        sendWhitelistLog(entry.discordUserId, minecraftName, "Removed")
        return true
    }

    private fun sendWhitelistLog(discordUserId: String, mcName: String, action: String) {
        val jda = plugin.discordBotService.jda ?: return
        val channelId = plugin.configManager.logChannelId
        if (channelId.isBlank()) return
        try {
            val channel = jda.getTextChannelById(channelId) ?: return
            channel.sendMessageEmbeds(EmbedFactory.whitelistChange(discordUserId, mcName, action)).queue()
        } catch (_: Exception) {}
    }

    fun sendDmSafely(user: User, message: String) {
        try {
            user.openPrivateChannel().queue({ channel ->
                channel.sendMessage(message).queue()
            }, {
                plugin.logger.fine("[StarlightBot] Could not open DM with ${user.name}")
            })
        } catch (_: Exception) {}
    }
}