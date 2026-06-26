package com.k1wit.starlightbot.service

import com.k1wit.starlightbot.StarlightBot
import com.k1wit.starlightbot.util.GoogleDocsFetcher
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.utils.FileUpload

class ScriptService(private val plugin: StarlightBot) {

    fun postScript(url: String, hook: InteractionHook) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val docId = GoogleDocsFetcher.extractDocId(url)
                if (docId == null) {
                    hook.sendMessage("Invalid Google Docs URL. Please provide a valid Google Docs link.")
                        .setEphemeral(true).queue()
                    return@Runnable
                }

                val rawText = try {
                    GoogleDocsFetcher.fetchAsText(docId)
                } catch (e: Exception) {
                    hook.sendMessage(
                        "Could not access the document. Make sure it's set to 'Anyone with the link can view'."
                    ).setEphemeral(true).queue()
                    return@Runnable
                }

                if (rawText.isBlank()) {
                    hook.sendMessage("The document appears to be empty.").setEphemeral(true).queue()
                    return@Runnable
                }

                val jda = plugin.discordBotService.jda ?: return@Runnable
                val scriptChannel = jda.getTextChannelById(plugin.configManager.scriptChannelId)
                if (scriptChannel == null) {
                    hook.sendMessage("Script channel not configured or not found.").setEphemeral(true).queue()
                    return@Runnable
                }

                if (rawText.length > 8000) {
                    scriptChannel.sendMessage("New script posted:")
                        .addFiles(FileUpload.fromData(rawText.toByteArray(), "script.md"))
                        .queue()
                } else {
                    val chunks = GoogleDocsFetcher.splitIntoChunks(rawText, 1850)
                    chunks.forEachIndexed { index, chunk ->
                        val message = if (chunks.size > 1) {
                            "**Script (Part ${index + 1}/${chunks.size}):**\n```md\n$chunk\n```"
                        } else {
                            "```md\n$chunk\n```"
                        }
                        scriptChannel.sendMessage(message).queue()
                    }
                }

                hook.sendMessage("Script has been posted to <#${plugin.configManager.scriptChannelId}>")
                    .setEphemeral(true).queue()

            } catch (e: Exception) {
                plugin.logger.warning("[StarlightBot] Script error: ${e.message}")
                try {
                    hook.sendMessage("An unexpected error occurred: ${e.message}")
                        .setEphemeral(true).queue()
                } catch (_: Exception) {}
            }
        })
    }
}