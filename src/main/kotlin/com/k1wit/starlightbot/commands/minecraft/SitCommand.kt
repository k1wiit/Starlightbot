package com.k1wit.starlightbot.commands.minecraft

import com.k1wit.starlightbot.StarlightBot
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SitCommand(private val plugin: StarlightBot) : CommandExecutor, Listener {

    // Track which players are sitting and their armor stands
    private val sittingPlayers = ConcurrentHashMap<UUID, ArmorStand>()

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("This command can only be used by players").color(NamedTextColor.RED))
            return true
        }

        val player = sender
        val playerUuid = player.uniqueId

        // Check if player is already sitting
        if (sittingPlayers.containsKey(playerUuid)) {
            removeSitting(player)
            player.sendMessage(Component.text("You are no longer sitting").color(NamedTextColor.GREEN))
            return true
        }

        // Create invisible armor stand below the player
        val location = player.location.clone()
        location.y -= 1.25 // Position armor stand below player

        val armorStand = player.world.spawn(location, ArmorStand::class.java) { stand ->
            stand.isVisible = false
            stand.isInvulnerable = true
            stand.setGravity(false)
            stand.isSmall = true
            stand.customName(Component.text("Sitting"))
            stand.isCustomNameVisible = false
        }

        // Move player onto the armor stand
        player.teleport(location.clone().apply { y += 0.5 })

        // Set player as passenger of armor stand
        armorStand.addPassenger(player)

        // Track the sitting player
        sittingPlayers[playerUuid] = armorStand

        player.sendMessage(Component.text("You are now sitting. Press SHIFT to stand up").color(NamedTextColor.GREEN))

        return true
    }

    // Listen for player shift (sneak) to exit sitting
    @EventHandler
    fun onPlayerSneak(event: PlayerInteractEvent) {
        val player = event.player
        if (player.isSneaking && sittingPlayers.containsKey(player.uniqueId)) {
            event.isCancelled = true
            removeSitting(player)
            player.sendMessage(Component.text("You are no longer sitting").color(NamedTextColor.GREEN))
        }
    }

    // Clean up when player quits
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        removeSitting(player)
    }

    private fun removeSitting(player: Player) {
        val armorStand = sittingPlayers.remove(player.uniqueId) ?: return

        // Remove player as passenger
        armorStand.passengers.remove(player)

        // Remove the armor stand
        armorStand.remove()

        // Eject player if they're still on the armor stand
        if (player.vehicle == armorStand) {
            player.leaveVehicle()
        }
    }
}
