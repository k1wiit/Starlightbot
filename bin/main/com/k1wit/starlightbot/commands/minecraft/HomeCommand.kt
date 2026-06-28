package com.k1wit.starlightbot.commands.minecraft

import com.k1wit.starlightbot.StarlightBot
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent

class HomeCommand(private val plugin: StarlightBot) : CommandExecutor, TabCompleter, Listener {

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("This command can only be used by players").color(NamedTextColor.RED))
            return true
        }

        return when (label.lowercase()) {
            "home" -> handleHome(sender, args)
            "sethome" -> handleSetHome(sender, args)
            "delhome" -> handleDelHome(sender, args)
            "setpublichome" -> handleSetPublicHome(sender, args)
            "delpublichome" -> handleDelPublicHome(sender, args)
            else -> false
        }
    }

    private fun handleHome(player: Player, args: Array<out String>): Boolean {
        return if (args.isNotEmpty()) {
            // Teleport to specific home
            val homeName = args.joinToString(" ")
            val home = plugin.homeService.getHome(player, homeName)
            
            if (home != null) {
                if (plugin.homeService.teleportToHome(player, home)) {
                    player.sendMessage(Component.text("Teleported to home: $homeName").color(NamedTextColor.GREEN))
                    true
                } else {
                    player.sendMessage(Component.text("Failed to teleport to home (world not found)").color(NamedTextColor.RED))
                    true
                }
            } else {
                player.sendMessage(Component.text("Home not found: $homeName").color(NamedTextColor.RED))
                true
            }
        } else {
            // Open GUI
            openHomeGui(player)
            true
        }
    }

    private fun handleSetHome(player: Player, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            player.sendMessage(Component.text("Usage: /sethome <name>").color(NamedTextColor.RED))
            return true
        }

        val homeName = args.joinToString(" ")
        
        if (homeName.length > 32) {
            player.sendMessage(Component.text("Home name must be 32 characters or less").color(NamedTextColor.RED))
            return true
        }

        if (plugin.homeService.setHome(player, homeName)) {
            player.sendMessage(Component.text("Home set: $homeName").color(NamedTextColor.GREEN))
        } else {
            player.sendMessage(Component.text("Failed to set home (max homes reached: ${HomeService.MAX_HOMES})").color(NamedTextColor.RED))
        }
        return true
    }

    private fun handleDelHome(player: Player, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            player.sendMessage(Component.text("Usage: /delhome <name>").color(NamedTextColor.RED))
            return true
        }

        val homeName = args.joinToString(" ")
        if (plugin.homeService.deleteHome(player, homeName)) {
            player.sendMessage(Component.text("Home deleted: $homeName").color(NamedTextColor.GREEN))
        } else {
            player.sendMessage(Component.text("Home not found: $homeName").color(NamedTextColor.RED))
        }
        return true
    }

    private fun handleSetPublicHome(player: Player, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            player.sendMessage(Component.text("Usage: /setpublichome <name>").color(NamedTextColor.RED))
            return true
        }

        val homeName = args.joinToString(" ")
        
        if (homeName.length > 32) {
            player.sendMessage(Component.text("Home name must be 32 characters or less").color(NamedTextColor.RED))
            return true
        }

        if (plugin.homeService.setPublicHome(player, homeName)) {
            player.sendMessage(Component.text("Public home set: $homeName").color(NamedTextColor.GREEN))
        } else {
            player.sendMessage(Component.text("Failed to set home (max homes reached: ${HomeService.MAX_HOMES})").color(NamedTextColor.RED))
        }
        return true
    }

    private fun handleDelPublicHome(player: Player, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            player.sendMessage(Component.text("Usage: /delpublichome <name>").color(NamedTextColor.RED))
            return true
        }

        val homeName = args.joinToString(" ")
        if (plugin.homeService.deletePublicHome(homeName)) {
            player.sendMessage(Component.text("Public home deleted: $homeName").color(NamedTextColor.GREEN))
        } else {
            player.sendMessage(Component.text("Public home not found: $homeName").color(NamedTextColor.RED))
        }
        return true
    }

    private fun openHomeGui(player: Player) {
        val inventory = plugin.server.createInventory(null, 27, Component.text("Homes"))

        val privateHomes = plugin.homeService.getPlayerHomes(player).filter { !it.isPublic }
        val publicHomes = plugin.homeService.getPublicHomes()

        // Fill slots with homes
        var slot = 0

        // Public homes (top row + middle row start)
        for (home in publicHomes.take(9)) {
            inventory.setItem(slot++, createHomeItem(home, true))
        }

        // Private homes (middle row start + bottom row)
        for (home in privateHomes.take(9)) {
            inventory.setItem(slot++, createHomeItem(home, false))
        }

        // Fill remaining slots with barriers for empty homes
        while (slot < 18) {
            inventory.setItem(slot++, createEmptyHomeItem())
        }

        // Add credit item (bottom left)
        inventory.setItem(25, createCreditItem())

        player.openInventory(inventory)
    }

    private fun createHomeItem(home: com.k1wit.starlightbot.database.tables.Home, isPublic: Boolean): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta ?: return item
        
        meta.displayName(Component.text(home.homeName).color(NamedTextColor.GREEN))
        meta.lore(listOf(
            Component.text("Owner: ${home.playerName}").color(NamedTextColor.GRAY),
            Component.text("Location: ${home.worldName}").color(NamedTextColor.GRAY),
            Component.text("").color(NamedTextColor.GRAY),
            Component.text("Left Click: Teleport").color(NamedTextColor.YELLOW),
            Component.text("Right Click: Delete").color(NamedTextColor.RED)
        ))
        
        item.itemMeta = meta
        return item
    }

    private fun createEmptyHomeItem(): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta = item.itemMeta ?: return item
        
        meta.displayName(Component.text("NO HOME PROVIDED").color(NamedTextColor.RED)
            .decorate(TextDecoration.BOLD))
        meta.lore(listOf(
            Component.text("Use /sethome to set your home").color(NamedTextColor.GRAY)
        ))
        
        item.itemMeta = meta
        return item
    }

    private fun createCreditItem(): ItemStack {
        val item = ItemStack(Material.GLASS_PANE)
        val meta = item.itemMeta ?: return item
        
        meta.displayName(Component.text("Made by K1wit").color(NamedTextColor.AQUA)
            .decorate(TextDecoration.BOLD))
        
        item.itemMeta = meta
        return item
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title != Component.text("Homes")) return

        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return

        if (clickedItem.type == Material.BARRIER || clickedItem.type == Material.GLASS_PANE) return

        val homeName = clickedItem.itemMeta?.displayName?.let {
            // Extract home name from display name
            (it as? net.kyori.adventure.text.TextComponent)?.content()
        } ?: return

        val home = plugin.homeService.getHome(player, homeName) ?: run {
            // Try to find public home
            plugin.homeService.getPublicHomes().find { it.homeName == homeName }
        } ?: return

        when (event.click) {
            org.bukkit.event.inventory.ClickType.LEFT -> {
                // Teleport
                if (plugin.homeService.teleportToHome(player, home)) {
                    player.sendMessage(Component.text("Teleported to: $homeName").color(NamedTextColor.GREEN))
                    player.closeInventory()
                } else {
                    player.sendMessage(Component.text("Failed to teleport (world not found)").color(NamedTextColor.RED))
                }
            }
            org.bukkit.event.inventory.ClickType.RIGHT -> {
                // Delete
                if (plugin.homeService.deleteHome(player, homeName)) {
                    player.sendMessage(Component.text("Home deleted: $homeName").color(NamedTextColor.GREEN))
                    openHomeGui(player) // Refresh GUI
                } else {
                    player.sendMessage(Component.text("Failed to delete home").color(NamedTextColor.RED))
                }
            }
            else -> {}
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String> {
        if (sender !is Player || args.isEmpty()) return emptyList()

        val input = args.last().lowercase()
        return plugin.homeService.getPlayerHomes(sender)
            .map { it.homeName }
            .filter { it.lowercase().startsWith(input) }
    }
}
