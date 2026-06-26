package com.k1wit.starlightbot.service

import com.k1wit.starlightbot.StarlightBot
import com.k1wit.starlightbot.util.ANSIFormatter
import net.dv8tion.jda.api.utils.FileUpload
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.layout.PatternLayout
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LoggingService(private val plugin: StarlightBot) {

    private val logBuffer = mutableListOf<String>()
    private val bufferLock = Any()
    private var lastStacktrace: String? = null
    private var executor: ScheduledExecutorService? = null
    private var appender: StarLightAppender? = null

    fun start() {
        // Register Log4J appender
        try {
            val context = LogManager.getContext(false) as LoggerContext
            val config = context.configuration

            appender = StarLightAppender("StarlightBotAppender", this)
            appender!!.start()

            config.rootLogger.addAppender(appender!!, Level.ALL, null)
            context.updateLoggers()

            plugin.consoleLog("<gray>[StarlightBot] Console log appender registered.")
        } catch (e: Exception) {
            plugin.logger.warning("[StarlightBot] Could not register log appender: ${e.message}")
        }

        // Start flush executor – every 2.5 seconds
        executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "StarlightBot-LogFlusher").also { it.isDaemon = true }
        }
        executor!!.scheduleAtFixedRate({ flushBuffer() }, 3, 3, TimeUnit.SECONDS)
    }

    fun stop() {
        executor?.shutdown()
        try {
            executor?.awaitTermination(5, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {}

        // Remove appender
        try {
            val context = LogManager.getContext(false) as LoggerContext
            val cfg = context.configuration
            cfg.rootLogger.removeAppender("StarlightBotAppender")
            context.updateLoggers()
            appender?.stop()
        } catch (_: Exception) {}
    }

    fun onLogEvent(formattedLine: String, isError: Boolean, rawMessage: String) {
        synchronized(bufferLock) {
            logBuffer.add(formattedLine)
        }
        if (isError && rawMessage.contains("Exception", ignoreCase = true) ||
            rawMessage.contains("Caused by", ignoreCase = true) ||
            rawMessage.contains("\tat ", ignoreCase = false)) {
            appendStacktrace(rawMessage)
        }
    }

    private val stacktraceBuilder = StringBuilder()
    private var stacktraceTimeout = 0L

    @Synchronized
    private fun appendStacktrace(line: String) {
        val now = System.currentTimeMillis()
        if (now - stacktraceTimeout > 5000) {
            // New stacktrace
            stacktraceBuilder.clear()
        }
        stacktraceBuilder.appendLine(line)
        stacktraceTimeout = now
        // Update after a small delay - we just keep appending
        lastStacktrace = stacktraceBuilder.toString()
    }

    fun getLastStacktrace(): String? = lastStacktrace

    private fun flushBuffer() {
        val lines: List<String>
        synchronized(bufferLock) {
            if (logBuffer.isEmpty()) return
            lines = logBuffer.toList()
            logBuffer.clear()
        }

        val jda = plugin.discordBotService.jda ?: return
        val channelId = plugin.configManager.logChannelId
        if (channelId.isBlank()) return

        try {
            val channel = jda.getTextChannelById(channelId) ?: return

            // Split into chunks of max 1900 chars (leaving room for code block syntax)
            val chunks = mutableListOf<String>()
            val current = StringBuilder()

            for (line in lines) {
                val addition = if (current.isEmpty()) line else "\n$line"
                if (current.length + addition.length > 1850) {
                    chunks.add(current.toString())
                    current.clear()
                    current.append(line)
                } else {
                    current.append(addition)
                }
            }
            if (current.isNotEmpty()) chunks.add(current.toString())

            for (chunk in chunks) {
                channel.sendMessage(ANSIFormatter.wrapInCodeblock(chunk))
                    .queue(null) { err ->
                        plugin.logger.warning("[StarlightBot] Failed to send log: ${err.message}")
                    }
            }
        } catch (e: Exception) {
            // Silent - don't cause infinite loop
        }
    }

    inner class StarLightAppender(
        name: String,
        private val service: LoggingService
    ) : AbstractAppender(name, null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY) {

        override fun append(event: LogEvent) {
            val plugin = service.plugin

            // Don't log our own Discord messages (prevent loop)
            val loggerName = event.loggerName ?: ""
            if (loggerName.contains("StarlightBot") && !loggerName.contains("Minecraft")) return
            if (loggerName.contains("net.dv8tion") || loggerName.contains("okhttp3")) return

            val rawMessage = ANSIFormatter.stripMinecraftColors(event.message.formattedMessage ?: "")

            // Filter patterns from config
            val filterPatterns = try { plugin.configManager.loggingFilterPatterns } catch (_: Exception) { emptyList() }
            if (filterPatterns.any { pattern -> rawMessage.contains(pattern, ignoreCase = true) }) return

            val level = when (event.level) {
                Level.ERROR, Level.FATAL -> ANSIFormatter.LogLevel.ERROR
                Level.WARN               -> ANSIFormatter.LogLevel.WARN
                Level.INFO               -> ANSIFormatter.LogLevel.INFO
                else                     -> ANSIFormatter.LogLevel.UNKNOWN
            }

            val timestamp = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))

            val formatted = ANSIFormatter.format(timestamp, level, rawMessage)
            val isError = level == ANSIFormatter.LogLevel.ERROR

            service.onLogEvent(formatted, isError, rawMessage)
        }
    }
}