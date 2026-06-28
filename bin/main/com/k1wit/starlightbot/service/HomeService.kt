package com.k1wit.starlightbot.service

import com.k1wit.starlightbot.StarlightBot
import com.k1wit.starlightbot.database.tables.HomeRepository
import org.bukkit.entity.Player

class HomeService(private val plugin: StarlightBot) {

    val repository = HomeRepository(plugin.sqliteManager)

    companion object {
        const val MAX_HOMES = 14
    }

    fun setHome(player: Player, homeName: String): Boolean {
        val playerUuid = player.uniqueId.toString()
        val count = repository.countPlayerHomes(playerUuid)
        
        // Check if home already exists
        val existingHome = repository.findByName(playerUuid, homeName)
        
        if (existingHome == null && count >= MAX_HOMES) {
            return false // Max homes reached
        }

        val location = player.location
        return repository.create(
            playerUuid = playerUuid,
            playerName = player.name,
            homeName = homeName,
            x = location.x,
            y = location.y,
            z = location.z,
            yaw = location.yaw,
            pitch = location.pitch,
            worldName = location.world.name,
            isPublic = false
        )
    }

    fun setPublicHome(player: Player, homeName: String): Boolean {
        val playerUuid = player.uniqueId.toString()
        val count = repository.countPlayerHomes(playerUuid)
        
        // Check if home already exists
        val existingHome = repository.findByName(playerUuid, homeName)
        
        if (existingHome == null && count >= MAX_HOMES) {
            return false // Max homes reached
        }

        val location = player.location
        return repository.create(
            playerUuid = playerUuid,
            playerName = player.name,
            homeName = homeName,
            x = location.x,
            y = location.y,
            z = location.z,
            yaw = location.yaw,
            pitch = location.pitch,
            worldName = location.world.name,
            isPublic = true
        )
    }

    fun deleteHome(player: Player, homeName: String): Boolean {
        return repository.delete(player.uniqueId.toString(), homeName)
    }

    fun deletePublicHome(homeName: String): Boolean {
        return repository.deletePublic(homeName)
    }

    fun getPlayerHomes(player: Player): List<com.k1wit.starlightbot.database.tables.Home> {
        return repository.findByPlayerUuid(player.uniqueId.toString())
    }

    fun getPublicHomes(): List<com.k1wit.starlightbot.database.tables.Home> {
        return repository.findPublicHomes()
    }

    fun getHome(player: Player, homeName: String): com.k1wit.starlightbot.database.tables.Home? {
        return repository.findByName(player.uniqueId.toString(), homeName)
    }

    fun teleportToHome(player: Player, home: com.k1wit.starlightbot.database.tables.Home): Boolean {
        return try {
            val world = plugin.server.getWorld(home.worldName) ?: return false
            val location = org.bukkit.Location(world, home.x, home.y, home.z, home.yaw, home.pitch)
            player.teleport(location)
            true
        } catch (e: Exception) {
            false
        }
    }
}
