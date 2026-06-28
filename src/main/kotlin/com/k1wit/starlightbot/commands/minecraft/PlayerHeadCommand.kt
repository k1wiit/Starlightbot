package com.k1wit.starlightbot.commands.minecraft

import com.k1wit.starlightbot.StarlightBot
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.profile.PlayerProfile
import java.util.UUID
import java.util.concurrent.CompletableFuture

class PlayerHeadCommand(private val plugin: StarlightBot) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("This command can only be used by players").color(NamedTextColor.RED))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /playerhead <player>").color(NamedTextColor.RED))
            return true
        }

        val playerName = args[0]
        val player = sender

        // Try to find the player offline
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val profile = plugin.server.createPlayerProfile(playerName)
                
                // Complete the profile data from Mojang
                profile.update().thenAccept {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        givePlayerHead(player, profile)
                    })
                }.exceptionally { ex ->
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        player.sendMessage(
                            Component.text("Could not find player: $playerName")
                                .color(NamedTextColor.RED)
                        )
                    })
                    null
                }
            } catch (e: Exception) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage(
                        Component.text("Error: ${e.message}")
                            .color(NamedTextColor.RED)
                    )
                })
            }
        })

        return true
    }

    private fun givePlayerHead(player: Player, profile: PlayerProfile) {
        try {
            val head = ItemStack(Material.PLAYER_HEAD)
            val meta = head.itemMeta
            if (meta is org.bukkit.inventory.meta.SkullMeta) {
                meta.ownerProfile = profile
                head.itemMeta = meta
            }

            if (player.inventory.addItem(head).isNotEmpty()) {
                player.world.dropItem(player.location, head)
                player.sendMessage(
                    Component.text("Player head dropped on the ground (inventory full)")
                        .color(NamedTextColor.YELLOW)
                )
            } else {
                player.sendMessage(
                    Component.text("You received ${profile.name}'s head")
                        .color(NamedTextColor.GREEN)
                )
            }
        } catch (e: Exception) {
            plugin.logger.warning("[StarlightBot] Error creating player head: ${e.message}")
            player.sendMessage(
                Component.text("Error creating player head: ${e.message}")
                    .color(NamedTextColor.RED)
            )
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            val input = args[0].lowercase()
            return plugin.server.onlinePlayers
                .map { it.name }
                .filter { it.lowercase().startsWith(input) }
        }
        return emptyList()
    }
}
