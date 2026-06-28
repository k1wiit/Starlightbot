# StarlightBot Quick Reference Guide

## File Organization
```
src/main/kotlin/com/k1wit/starlightbot/
├── StarlightBot.kt              ← Main plugin class, lifecycle
├── commands/discord/            ← Slash command handlers
│   ├── SlashCommandManager.kt    ← Command registration & routing
│   ├── SetStatusCommand.kt       ← Example admin command
│   ├── ReloadConfigCommand.kt    ← Example admin command
│   ├── PublicCommands.kt         ← Example public commands
│   └── ...
├── listener/discord/            ← Discord event handlers
│   ├── ButtonInteractionListener.kt    ← Button clicks
│   ├── SelectMenuListener.kt           ← Dropdown selections
│   ├── AntiSpamListener.kt             ← Message spam detection
│   └── WhitelistMessageListener.kt     ← Whitelist channel monitoring
├── listener/minecraft/          ← Minecraft server events
│   ├── PlayerJoinLeaveListener.kt
│   ├── ChatListener.kt
│   ├── DeathListener.kt
│   └── AdvancementListener.kt
├── service/                     ← Business logic layer
│   ├── DiscordBotService.kt     ← JDA bot lifecycle
│   ├── LoggingService.kt        ← Log buffering & forwarding
│   ├── WhitelistService.kt      ← Whitelist management
│   ├── TicketService.kt         ← Ticket & transcript system
│   ├── PlayerCountService.kt    ← Voice channel updates
│   ├── AntiSpamService.kt       ← Spam detection
│   └── ScriptService.kt         ← Google Docs integration
├── config/
│   └── ConfigManager.kt         ← YAML config wrapper
├── database/
│   ├── SQLiteManager.kt         ← Database initialization
│   └── tables/
│       ├── WhitelistRepository.kt
│       └── TicketRepository.kt
└── util/
    ├── EmbedBuilder.kt          ← Pre-built embeds
    ├── ANSIFormatter.kt         ← Color formatting
    ├── MojangAPI.kt             ← Username lookup
    └── GoogleDocsFetcher.kt     ← Google Docs fetch

src/main/resources/
├── config.yml                   ← Plugin configuration
└── paper-plugin.yml             ← Paper plugin metadata
```

## Core Services & Access

| Service | Access | Main Methods |
|---------|--------|--------------|
| **DiscordBotService** | `plugin.discordBotService` | `start()`, `shutdown()`, `.jda` (JDA instance) |
| **LoggingService** | `plugin.loggingService` | `start()`, `stop()` |
| **WhitelistService** | `plugin.whitelistService` | `processWhitelistRequest()`, `changeWhitelistEntry()` |
| **TicketService** | `plugin.ticketService` | `createTicket()`, `claimTicket()`, `closeTicket()` |
| **PlayerCountService** | `plugin.playerCountService` | `start()`, `stop()` |
| **ConfigManager** | `plugin.configManager` | `load()`, `reload()`, properties |
| **SQLiteManager** | `plugin.sqliteManager` | `getConnection()`, `close()` |

## Configuration Quick Reference

```yaml
# Required
bot:
  token: "discord-bot-token"
  guild_id: "guild-id"

# Channel IDs
channels:
  log: "channel-id"              # Audit logs
  whitelist: "channel-id"        # Whitelist input
  info: "channel-id"             # Info panel
  tickets: "channel-id"          # Ticket threads
  script: "channel-id"           # Script posts
  player_count_voice: "vc-id"    # Voice channel

# Permissions
roles:
  admin: "role-id"
admin_user_ids:
  - "discord-id"
  - "discord-id"

# Server Display
server:
  name: "Server Name"
  address: "play.example.com"
  version: "1.21.4"
  max_players: 30
  rules_channel_id: "channel-id"

# Visible in Info Panel
socials:
  youtube: "url"
  tiktok: "url"
  # ... etc

# Anti-Spam Thresholds
anti_spam:
  max_messages: 4                # messages allowed
  time_window_seconds: 8         # within this period
  timeout_minutes: 10            # timeout duration

# Logging Filters (patterns to ignore)
logging:
  filter_patterns:
    - "Thread RCON Client"
    - "moved too quickly"
```

## Common Code Patterns

### Permission Check (in commands)
```kotlin
private fun isAdmin(event: SlashCommandInteractionEvent): Boolean {
    val member = event.member ?: return false
    val config = plugin.configManager
    return member.roles.any { it.id == config.adminRoleId }
        || member.id in config.adminUserIds
}
```

### Reply with Ephemeral (hidden from others)
```kotlin
event.reply("Only you see this")
    .setEphemeral(true)
    .queue()
```

### Async Task from Discord Command
```kotlin
plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
    // Long operation (DB, API calls, etc)
    val result = plugin.whitelistService.someMethod()
    
    // Reply back (JDA queue operations automatically)
    channel.sendMessage(result).queue()
})
```

### Get Main Thread Result from Async
```kotlin
val future = CompletableFuture<Int>()
plugin.server.scheduler.runTask(plugin, Runnable {
    future.complete(Bukkit.getOnlinePlayers().size)
})
val count = future.get()  // Blocks until complete
```

### Send Embed to Channel
```kotlin
val embed = EmbedFactory.playerJoin("PlayerName", 5, 30)
jda.getTextChannelById("channel-id")?.sendMessageEmbeds(embed)?.queue()
```

### Defer Long Operation (Let Discord know you're working)
```kotlin
event.deferReply(true).queue()  // true = ephemeral

plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
    // ... do work ...
    val result = "Done!"
    event.hook.sendMessage(result).setEphemeral(true).queue()
})
```

### Database Query
```kotlin
val conn = plugin.sqliteManager.getConnection() ?: return
conn.prepareStatement("SELECT * FROM whitelist WHERE id = ?").use { stmt ->
    stmt.setString(1, value)
    val rs = stmt.executeQuery()
    if (rs.next()) {
        // Process row
        val data = rs.getString("column_name")
    }
}
```

### Try-Catch with Error Reply
```kotlin
try {
    // Operation
    val result = plugin.whitelistService.processWhitelistRequest(userId, name)
    when (result) {
        is WhitelistResult.Success -> {
            event.reply("Success: ${result.minecraftName}").setEphemeral(true).queue()
        }
        is WhitelistResult.Error -> {
            event.reply("Error: ${result.message}").setEphemeral(true).queue()
        }
    }
} catch (e: Exception) {
    event.reply("Fatal error: ${e.message}").setEphemeral(true).queue()
}
```

## Database Schema Summary

### whitelist
```
id [PK]
discord_user_id [UNIQUE]
minecraft_name [UNIQUE]
minecraft_uuid
whitelisted_at [TIMESTAMP]
```

### tickets
```
id [PK]
ticket_number [display number: 0001, 0002]
discord_user_id
thread_id [Discord thread]
category [application|support|player_report|other]
status [OPEN|CLOSED]
claimed_by [admin ID or null]
created_at [TIMESTAMP]
closed_at [TIMESTAMP, null if open]
```

### ticket_transcripts
```
id [PK]
ticket_id [FK -> tickets]
author [message author name]
content [message text]
timestamp [TIMESTAMP]
```

## Event Flow Diagrams

### Ticket Creation
```
User selects category → SelectMenuListener.onStringSelectInteraction()
  ↓
Check: User doesn't have open ticket already
  ↓
"Creating your ticket..." ephemeral reply
  ↓
Async: TicketService.createTicket()
  ├─ Create Discord thread
  ├─ Insert into tickets table
  ├─ Post embed with Claim/Close buttons
  ├─ Add creator to thread
  ├─ Add all admins to thread
  └─ Log to log channel
```

### Button Click → Permission Check → Action
```
Admin clicks button → ButtonInteractionListener.onButtonInteraction()
  ↓
Extract ticket ID from button ID (e.g., "ticket_claim_123")
  ↓
Check: Is admin? (role OR user ID list)
  ↓
No → Ephemeral error reply
  ↓
Yes → Async operation
  ├─ Update database
  ├─ Update Discord thread
  ├─ Send confirmation
  └─ Update button UI
```

### Logging Pipeline
```
Server generates log → Log4J event
  ↓
StarLightAppender.append() captures
  ↓
Format + filter
  ↓
Add to logBuffer (guarded by lock)
  ↓
Every 3 seconds: flushBuffer()
  ├─ Get buffer contents
  ├─ Split into 1900-char chunks
  └─ Send to log channel (queued)
```

## Common Config Access Patterns

```kotlin
// Channels
val logChannel = plugin.discordBotService.jda?.getTextChannelById(
    plugin.configManager.logChannelId
)

// Permissions
val adminRoleId = plugin.configManager.adminRoleId
val isAdmin = member.roles.any { it.id == adminRoleId } 
    || member.id in plugin.configManager.adminUserIds

// Server info
val serverName = plugin.configManager.serverName
val address = plugin.configManager.serverAddress
val maxPlayers = plugin.configManager.serverMaxPlayers

// Anti-spam thresholds
val maxMsg = plugin.configManager.antiSpamMaxMessages
val window = plugin.configManager.antiSpamTimeWindowSeconds
val timeout = plugin.configManager.antiSpamTimeoutMinutes

// Logging filters
val filters = plugin.configManager.loggingFilterPatterns

// Dynamic list
val extraChannels = plugin.configManager.extraInfoChannels
```

## Initialization Checklist

For new features, ensure:
- [ ] Config properties added to `config.yml` & `ConfigManager.kt`
- [ ] Service created (if complex logic)
- [ ] Service initialized in `StarlightBot.onEnable()`
- [ ] Service stopped in `StarlightBot.onDisable()` (if needed)
- [ ] Command/listener registered
- [ ] Database tables created (if needed)
- [ ] Repository created (if DB needed)
- [ ] Error handling with try-catch
- [ ] Async operations for long tasks
- [ ] Admin permission checks (if admin-only)
- [ ] Embeds use EmbedFactory
- [ ] Logging via plugin.logger or plugin.consoleLog()

## Useful JDA/Bukkit Methods

### JDA
```kotlin
jda?.getTextChannelById(id)                 // Get channel
jda?.getGuildById(id)                       // Get guild
jda?.getThreadChannelById(id)               // Get thread
jda?.retrieveUserById(id)                   // Get user (async)
guild?.getRoleById(id)                      // Get role
guild?.getMembersWithRoles(role)            // Get members with role
thread.addThreadMember(user)                // Add to thread
thread.iterableHistory                      // Get messages
```

### Bukkit
```kotlin
Bukkit.getOnlinePlayers()                   // Get online players
Bukkit.dispatchCommand(sender, cmd)         // Run console command
plugin.server.scheduler.runTask(...)        // Main thread task
plugin.server.scheduler.runTaskAsynchronously(...)  // Async task
```

## Debugging Tips

1. **Check config first** - Most errors are missing channel/role IDs
2. **Look at logs** - LoggingService captures everything
3. **Database queries** - Use SQLite browser to inspect database
4. **JDA ready state** - Many operations fail if JDA not ready
5. **Thread safety** - Discord operations must use `.queue()` not `.complete()`
6. **Permissions** - Verify bot has permissions in guild (roles, channels)
7. **Rate limits** - Discord has API rate limits, queue operations gracefully
