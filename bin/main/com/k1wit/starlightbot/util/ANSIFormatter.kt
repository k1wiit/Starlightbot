package com.k1wit.starlightbot.util

object ANSIFormatter {

    // ANSI color codes for Discord's ansi codeblock
    private const val RESET = "\u001b[0m"
    private const val GRAY = "\u001b[2;37m"
    private const val WHITE = "\u001b[0;37m"
    private const val BLUE = "\u001b[2;34m"
    private const val YELLOW = "\u001b[2;33m"
    private const val RED = "\u001b[2;31m"
    private const val DARK_GRAY = "\u001b[2;30m"

    enum class LogLevel { INFO, WARN, ERROR, UNKNOWN }

    fun format(timestamp: String, level: LogLevel, message: String): String {
        val levelStr = when (level) {
            LogLevel.INFO  -> "${BLUE}[INFO]${RESET}"
            LogLevel.WARN  -> "${YELLOW}[WARN]${RESET}"
            LogLevel.ERROR -> "${RED}[ERROR]${RESET}"
            LogLevel.UNKNOWN -> "${DARK_GRAY}[LOG]${RESET}"
        }

        val msgColor = when (level) {
            LogLevel.INFO    -> WHITE
            LogLevel.WARN    -> YELLOW
            LogLevel.ERROR   -> RED
            LogLevel.UNKNOWN -> GRAY
        }

        return "${GRAY}$timestamp${RESET} $levelStr ${msgColor}$message${RESET}"
    }

    fun wrapInCodeblock(content: String): String {
        return "```ansi\n$content\n```"
    }

    fun detectLevel(line: String): LogLevel {
        val upper = line.uppercase()
        return when {
            "[ERROR]" in upper || "ERROR" in upper && "EXCEPTION" in upper -> LogLevel.ERROR
            "[WARN]" in upper  || "[WARNING]" in upper -> LogLevel.WARN
            "[INFO]" in upper  -> LogLevel.INFO
            else               -> LogLevel.UNKNOWN
        }
    }

    fun stripMinecraftColors(text: String): String {
        // Remove Minecraft § color codes
        return text.replace(Regex("§[0-9a-fk-or]"), "")
    }
}