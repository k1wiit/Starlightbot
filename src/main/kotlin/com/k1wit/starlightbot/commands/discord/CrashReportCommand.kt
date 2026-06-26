package com.k1wit.starlightbot.commands.discord

import com.k1wit.starlightbot.StarlightBot
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.utils.FileUpload
import java.io.File

class CrashReportCommand(private val plugin: StarlightBot) {

    fun handle(event: SlashCommandInteractionEvent) {
        if (!isAdmin(event)) {
            event.reply("You don't have permission to use this command.").setEphemeral(true).queue()
            return
        }

        event.deferReply(true).queue()

        val crashReportsDir = File(plugin.server.worldContainer, "crash-reports")
        if (!crashReportsDir.exists() || crashReportsDir.listFiles().isNullOrEmpty()) {
            event.hook.sendMessage("No crash reports found.").setEphemeral(true).queue()
            return
        }

        val latestReport = crashReportsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.maxByOrNull { it.lastModified() }

        if (latestReport == null) {
            event.hook.sendMessage("No crash reports found.").setEphemeral(true).queue()
            return
        }

        try {
            val fileUpload = FileUpload.fromData(latestReport, latestReport.name)
            event.hook.sendMessage("Latest crash report: `${latestReport.name}`")
                .addFiles(fileUpload)
                .setEphemeral(true)
                .queue()
        } catch (e: Exception) {
            event.hook.sendMessage("Could not read crash report: ${e.message}").setEphemeral(true).queue()
        }
    }

    private fun isAdmin(event: SlashCommandInteractionEvent): Boolean {
        val member = event.member ?: return false
        val config = plugin.configManager
        return member.roles.any { it.id == config.adminRoleId } || member.id in config.adminUserIds
    }
}