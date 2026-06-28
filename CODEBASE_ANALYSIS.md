# StarlightBot Codebase Analysis

## Overview
StarlightBot is a Kotlin-based Spigot/Paper Minecraft plugin that bridges your Minecraft server with Discord. It provides whitelisting, support ticketing, logging, anti-spam, and administrative features.

---

## 1. Logging System (LoggingService.kt)

### Architecture
The logging system uses a **custom Log4J appender** to capture all server logs and stream them to a Discord channel.

**Key Components:**
- `StarLightAppender`: Custom Log4J appender
- `logBuffer`: In-memory buffer of formatted log lines
- `executor`: ScheduledExecutorService that flushes every 3 seconds
- `lastStacktrace`: Tracks the most recent error stack trace

### How It Works

1. **Initialization** (`start()`)
   - Registers custom appender to Log4J context
   - Starts scheduled executor to flush buffer every 3 seconds

2. **Log Capture** (`onLogEvent()`)
   - Appender intercepts all log4j events
   - Formats timestamps and log levels with colors (ANSI)
   - Adds to buffer
   - Automatically detects and collects stack traces

3. **Filtering**
   - Ignores logs from StarlightBot itself (prevents loops)
   - Ignores Discord client logs (net.dv8tion, okhttp3)
   - Applies regex patterns from `config.yml` under `logging.filter_patterns`
   - Default filters: "Thread RCON Client", "RCON Client"

4. **Buffering & Flushing** (`flushBuffer()`)
   - Every 3 seconds, buffer is sent to Discord
   - Split into 1900-character chunks (Discord message limit)
   - Wrapped in Discord code blocks
   - Non-blocking queue operation

5. **Stack Trace Tracking** (`appendStacktrace()`)
   - Detects exception patterns (Exception, Caused by, \tat)
   - Groups related lines within 5-second window
   - Accessible via `/laststacktrace` command

### Integration Points
```kotlin
// In DiscordBotService
loggingService = LoggingService(this)
loggingService.start()

// In StarlightBot.onDisable()
loggingService.stop()
```

### Configuration
```yaml
logging:
  filter_patterns:
    - "Ignoring packet"
    - "moved too quickly"
    - "Can't keep up"
```

### Best Practices for Logging
- **Non-blocking**: All Discord operations are queued
- **Safe**: Catch-all try-catch prevents infinite loops
- **Configurable**: Filter patterns prevent noise
- **Historical**: Stack traces retained for debugging

---

## 2. Ticket & Transcript System

### Database Schema

**tickets table:**
```sql
id (PK)                  -- Auto-increment ID
ticket_number            -- Human-readable ticket number (0001, 0002, etc.)
discord_user_id          -- Creator's Discord ID
thread_id                -- Associated Discord thread ID
category                 -- Ticket type (application, support, player_report, other)
status                   -- 'OPEN' or 'CLOSED'
claimed_by               -- Admin ID who claimed ticket
created_at               -- Timestamp (auto)
closed_at                -- Timestamp (auto, null if open)
```

**ticket_transcripts table:**
```sql
id (PK)                  -- Auto-increment ID
ticket_id (FK)           -- References tickets(id)
author                   -- Message author name
content                  -- Message text
timestamp                -- Auto-generated timestamp
```

### TicketService Workflow

#### Creating a Ticket
```kotlin
fun createTicket(discordUserId: String, username: String, categoryValue: String)
```

**Steps:**
1. Get next ticket number from DB
2. Format thread name: `ticket-0001-username`
3. Create **private thread** in configured tickets channel
4. Post initial embed with "Claim" and "Close" buttons
5. Add ticket creator to thread
6. Auto-add all admin role members to thread
7. Auto-add configured admin user IDs to thread
8. Log ticket creation to log channel

**Key Detail:** Threads are private (only invited users can see)

#### Claiming a Ticket
```kotlin
fun claimTicket(ticketId: Int, adminUserId: String, adminName: String): Boolean
```

**Steps:**
1. Update DB: set `claimed_by` to admin ID
2. Send message to thread: "This ticket has been claimed by **AdminName**"
3. Update button state (buttons become disabled)

#### Closing a Ticket
```kotlin
fun closeTicket(ticketId: Int, closedByUserId: String, closedByName: String)
```

**Steps:**
1. Retrieve all messages from thread
2. Build transcript in memory:
   - Header with ticket number, category, creator
   - Chronological list of messages (format: `[HH:MM:SS] Author: Message`)
   - Only non-bot messages included
3. Save each message to `ticket_transcripts` table
4. Update DB: set status='CLOSED', closed_at=NOW
5. Send embed + transcript file to log channel
6. Archive thread (read-only)
7. Lock thread (no new messages)

### TicketRepository Operations

```kotlin
getNextTicketNumber()              // SELECT MAX(ticket_number) + 1
create(...)                        // INSERT ticket
findById(id)                        // SELECT by ID
findByThreadId(threadId)           // SELECT by Discord thread ID
findOpenByUserId(discordUserId)    // SELECT where status='OPEN' and user
claim(ticketId, adminUserId)       // UPDATE claimed_by
close(ticketId)                    // UPDATE status='CLOSED'
addTranscript(ticketId, ...)       // INSERT into transcripts
getTranscripts(ticketId)           // SELECT all transcripts for ticket
```

### Ticket Lifecycle Diagram
```
SelectMenu Selected (SelectMenuListener)
    ↓
TicketService.createTicket() [ASYNC]
    ↓ (Success)
Thread created → DB entry → Embed posted with buttons
    ↓
[Admin clicks "Claim"]
    ↓
ButtonInteractionListener.handleTicketClaim()
    ↓
TicketService.claimTicket() [ASYNC]
    ↓ (Success)
Button state updated, message sent to thread
    ↓
[Admin clicks "Close"]
    ↓
ButtonInteractionListener.handleTicketClose()
    ↓
TicketService.closeTicket() [ASYNC]
    ↓
Collect messages → Save transcript → Update DB → Archive thread
```

### Configuration
```yaml
channels:
  tickets: "CHANNEL_ID"  # Where ticket threads are created
  log: "CHANNEL_ID"      # Where transcripts are posted
```

---

## 3. Configuration System

### ConfigManager Overview
The configuration system wraps Bukkit's FileConfiguration with typed, lazy-loaded getters.

### Pattern
```kotlin
val propertyName: Type get() = config.getString("yaml.path", defaultValue)!!
```

All config values are lazy-loaded on first access (not cached).

### Configuration Structure

**Bot Settings:**
```yaml
bot:
  token: "YOUR_BOT_TOKEN"
  guild_id: "YOUR_GUILD_ID"
```

**Channel IDs** (used by various systems):
```yaml
channels:
  log: "CHANNEL_ID"                  # LoggingService, all audit logs
  whitelist: "CHANNEL_ID"            # WhitelistMessageListener watches
  info: "CHANNEL_ID"                 # Info panel
  tickets: "CHANNEL_ID"              # Ticket threads
  script: "CHANNEL_ID"               # Script postings
  player_count_voice: "VOICE_CHANNEL_ID"  # PlayerCountService updates title
```

**Roles & Users:**
```yaml
roles:
  admin: "ROLE_ID"  # Checked via Member.roles

admin_user_ids:     # List of Discord IDs that bypass role check
  - "ID1"
  - "ID2"
```

**Server Info** (displayed in commands):
```yaml
server:
  name: "Starlight"
  address: "play.example.com"
  version: "1.21.4"
  max_players: 30
  rules_channel_id: "CHANNEL_ID"
```

**Socials** (displayed in buttons):
```yaml
socials:
  youtube: ""
  tiktok: ""
  instagram: ""
  twitch: ""
  twitter: ""
  discord_invite: ""
  website: ""
```

**Info Embed:**
```yaml
info_embed:
  gif_url: "YOUR_GIF_URL"
  title: "Hello welcome to Starlight! What would you like to know?"
```

**Extra Info Channels** (dynamic list):
```yaml
extra_info_channels:
  - name: "Rules"
    channel_id: "CHANNEL_ID"
  - name: "Scripts"
    channel_id: "CHANNEL_ID"
  - name: "FAQ"
    channel_id: "CHANNEL_ID"
```
Accessed via:
```kotlin
data class ExtraInfoChannel(val name: String, val channelId: String)
val extraInfoChannels: List<ExtraInfoChannel>
```

**Anti-Spam:**
```yaml
anti_spam:
  max_messages: 4              # Max messages allowed
  time_window_seconds: 8       # Within this window
  timeout_minutes: 10          # Timeout duration
```

**Logging:**
```yaml
logging:
  filter_patterns:             # Patterns to ignore
    - "Ignoring packet"
    - "moved too quickly"
    - "Can't keep up"
```

**Player Count:**
```yaml
player_count:
  update_interval_minutes: 5
  format_online: "MCOnline: %count%"
  format_offline: "MCOnline: Offline"
```

### ConfigManager Methods
```kotlin
fun load()      // Save defaults, reload from disk
fun reload()    // Reload from disk (called via /reloadconfig)
```

### Admin Permission Pattern
Used throughout codebase:
```kotlin
private fun isAdmin(event: SlashCommandInteractionEvent): Boolean {
    val member = event.member ?: return false
    val config = plugin.configManager
    return member.roles.any { it.id == config.adminRoleId }
        || member.id in config.adminUserIds
}
```

---

## 4. Discord Commands Architecture

### Command Registration (SlashCommandManager.kt)

**When:**
- After JDA is ready in DiscordBotService
- Per-guild deployment (guild ID from config)

**Process:**
1. Get guild by ID
2. Define all slash commands with options/subcommands
3. Call `guild.updateCommands().addCommands(list).queue()`
4. Register SlashCommandManager as event listener
5. SlashCommandManager.onSlashCommandInteraction() routes to handler

### Example: SetStatusCommand

```kotlin
fun handle(event: SlashCommandInteractionEvent) {
    // 1. Permission check
    if (!isAdmin(event)) {
        event.reply("You don't have permission to use this command.")
            .setEphemeral(true).queue()
        return
    }

    // 2. Extract options
    val type = event.getOption("type")?.asString ?: return
    val text = event.getOption("text")?.asString ?: return

    // 3. Process
    val activity = when (type.lowercase()) {
        "playing"   -> Activity.playing(text)
        "watching"  -> Activity.watching(text)
        "listening" -> Activity.listening(text)
        "competing" -> Activity.competing(text)
        else -> {
            event.reply("Invalid type...").setEphemeral(true).queue()
            return
        }
    }

    // 4. Call service
    plugin.discordBotService.jda?.presence?.activity = activity

    // 5. Reply to user
    event.reply("Status updated to: **${type.replaceFirstChar { it.uppercase() }}** $text")
        .setEphemeral(true).queue()
}
```

**Pattern:**
1. Check admin permission
2. Extract options from event
3. Validate input
4. Call service method (often from plugin property)
5. Reply with .setEphemeral(true) for admin commands

### Example: ReloadConfigCommand

```kotlin
fun handle(event: SlashCommandInteractionEvent) {
    if (!isAdmin(event)) {
        event.reply("You don't have permission to use this command.")
            .setEphemeral(true).queue()
        return
    }

    try {
        plugin.configManager.reload()
        event.reply("Configuration reloaded successfully. Note: Token changes require a server restart.")
            .setEphemeral(true).queue()
    } catch (e: Exception) {
        event.reply("Failed to reload config: ${e.message}")
            .setEphemeral(true).queue()
    }
}
```

**Key Points:**
- Calls `plugin.configManager.reload()`
- Error handling with try-catch
- User-friendly messages

### Public Commands Pattern: PublicCommands.kt

**handleServerInfo():**
```kotlin
// Get player count on main thread (must be sync)
val future = CompletableFuture<Int>()
plugin.server.scheduler.runTask(plugin, Runnable {
    future.complete(Bukkit.getOnlinePlayers().size)
})
val onlineCount = future.get()  // Block until complete

// Build and send embed
val embed = EmbedBuilder()
    .setColor(0x1a1a2e)
    .setTitle("${config.serverName} - Server Info")
    .addField("Address", "`${config.serverAddress}`", true)
    // ... more fields
    .build()

event.replyEmbeds(embed).queue()
```

**Key Pattern:** Bukkit operations must run on main thread, use `CompletableFuture` to bridge async Discord with sync Minecraft.

---

## 5. Discord Event Listeners

### ButtonInteractionListener

**Routing:**
```kotlin
override fun onButtonInteraction(event: ButtonInteractionEvent) {
    val buttonId = event.componentId

    when {
        buttonId == "info_apply"           -> handleApply(event)
        buttonId == "info_serverinfo"      -> handleServerInfo(event)
        buttonId == "info_socials"         -> handleSocials(event)
        buttonId == "info_extra"           -> handleExtraInfo(event)
        buttonId.startsWith("ticket_claim_") -> handleTicketClaim(event, buttonId)
        buttonId.startsWith("ticket_close_") -> handleTicketClose(event, buttonId)
    }
}
```

**Info Panel Handlers:**
- All send embeds via `sendDmOrEphemeral(event, embed)`
- Attempts to open user DM, falls back to ephemeral reply if DM fails

**DM Safety Pattern:**
```kotlin
private fun sendDmOrEphemeral(event: ButtonInteractionEvent, embed: MessageEmbed) {
    event.user.openPrivateChannel().queue(
        { channel ->  // Success - send DM
            channel.sendMessageEmbeds(embed).queue(
                {
                    event.reply("Check your DMs!").setEphemeral(true).queue()
                },
                { // DM send failed
                    event.replyEmbeds(embed).setEphemeral(true).queue()
                }
            )
        },
        { // Can't open DM channel
            event.replyEmbeds(embed).setEphemeral(true).queue()
        }
    )
}
```

**Ticket Button Handlers:**

`handleTicketClaim():`
1. Extract ticket ID from button ID
2. Check if user is admin
3. Run async: Call TicketService.claimTicket()
4. Update button UI (disabled + shows claimant name)
5. Handle errors gracefully

`handleTicketClose():`
1. Extract ticket ID
2. Get ticket from DB
3. Check: user is ticket owner OR admin
4. Reply: "Closing ticket..."
5. Run async: Call TicketService.closeTicket()

### SelectMenuListener

**Pattern:**
```kotlin
override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
    if (event.componentId != "ticket_category_select") return

    val selectedValue = event.values.firstOrNull() ?: return
    val user = event.user
    val member = event.member ?: return

    // Check: User doesn't already have open ticket
    val existingTicket = plugin.ticketService.repository.findOpenByUserId(user.id)
    if (existingTicket != null) {
        event.reply("You already have an open ticket...").setEphemeral(true).queue()
        return
    }

    event.reply("Creating your ticket...").setEphemeral(true).queue()

    // Run async to avoid blocking Discord
    plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
        plugin.ticketService.createTicket(user.id, member.effectiveName, selectedValue)
    })
}
```

**Key Pattern:** Long operations run async with `runTaskAsynchronously`

### AntiSpamListener

```kotlin
override fun onMessageReceived(event: MessageReceivedEvent) {
    if (event.author.isBot) return
    if (!event.isFromGuild) return

    val member = event.member ?: return

    // Skip whitelist channel (has its own logic)
    if (event.channel.id == plugin.configManager.whitelistChannelId) return

    try {
        plugin.antiSpamService.processMessage(member, event.message)
    } catch (e: Exception) {
        plugin.logger.warning("[StarlightBot] AntiSpam error: ${e.message}")
    }
}
```

**Pattern:** Delegates to service, wraps in try-catch for safety

---

## 6. Service & Command Interaction

### Service-First Architecture

Services contain business logic, commands are thin routing layers:

```
Command receives event
    ↓
Permission check
    ↓
Extract parameters
    ↓
Call plugin.serviceInstance.method()
    ↓
Format response
    ↓
Send reply to user
```

### Key Services

**DiscordBotService:**
- Accessed via: `plugin.discordBotService.jda`
- Exposes: `jda` (JDA instance), `getGuild()`, `start()`, `shutdown()`
- Used by: All Discord operations

**WhitelistService:**
- Entry point: `plugin.whitelistService.processWhitelistRequest()`
- Returns: `WhitelistResult` (Success or Error sealed class)
- Flows: Mojang API → DB → Minecraft /whitelist command → Discord log

**TicketService:**
- Entry point: `plugin.ticketService.createTicket()`, `claimTicket()`, `closeTicket()`
- Has: `repository: TicketRepository` property
- Flows: Thread creation → DB → Admin notification → Transcript on close

**ConfigManager:**
- Accessed via: `plugin.configManager.propertyName`
- Lazy-loaded properties
- Reloadable via `reload()`

### Interaction Example: Whitelist Change Flow

**User Input:**
- Runs `/whitelist change NewName` in Discord

**Command Handler:**
```kotlin
// WhitelistCommands.handlePublic()
// Defers reply (long operation)
event.deferReply(true).queue()

// Async processing
plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
    val result = plugin.whitelistService.changeWhitelistEntry(userId, newName)
    // ... send result back
})
```

**Service Processing:**
```kotlin
// WhitelistService.changeWhitelistEntry()
1. Check existing entry
2. Mojang API lookup (network call)
3. Validate name not taken
4. Update DB
5. Update Minecraft whitelist (via Bukkit command)
6. Log to Discord
```

**Result:**
- User sees success/error message
- Log channel receives embed
- Minecraft whitelist updated
- Database consistent

---

## 7. Database Structure

### Tables

**whitelist**
```
id (INT PRIMARY KEY AUTOINCREMENT)
discord_user_id (TEXT UNIQUE) - Discord user ID
minecraft_name (TEXT UNIQUE) - Minecraft username
minecraft_uuid (TEXT) - Player UUID from Mojang
whitelisted_at (TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
```

**tickets**
```
id (INT PRIMARY KEY AUTOINCREMENT)
ticket_number (INT) - Human-readable (0001, 0002)
discord_user_id (TEXT) - Ticket creator
thread_id (TEXT) - Discord thread ID
category (TEXT) - application | support | player_report | other
status (TEXT DEFAULT 'OPEN') - OPEN | CLOSED
claimed_by (TEXT) - Admin ID or NULL
created_at (TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
closed_at (TIMESTAMP) - NULL if open
```

**ticket_transcripts**
```
id (INT PRIMARY KEY AUTOINCREMENT)
ticket_id (INT FOREIGN KEY) -> tickets(id)
author (TEXT) - Message author name
content (TEXT) - Message text
timestamp (TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
```

### Access Pattern

All database access flows through Repository classes:

```kotlin
// WhitelistRepository
fun findByDiscordId(discordUserId: String): WhitelistEntry?
fun findByMinecraftName(name: String): WhitelistEntry?
fun add(discordUserId, mcName, uuid): Boolean
fun remove(mcName): Boolean
fun updateMinecraftName(discordUserId, newName, newUuid): Boolean
fun getAll(): List<WhitelistEntry>

// TicketRepository
fun findById(id: Int): TicketEntry?
fun findByThreadId(threadId: String): TicketEntry?
fun findOpenByUserId(discordUserId: String): TicketEntry?
fun create(...): Int
fun claim(ticketId, adminUserId): Boolean
fun close(ticketId): Boolean
fun addTranscript(ticketId, author, content): Boolean
fun getTranscripts(ticketId): List<TranscriptEntry>
```

### Connection Management

```kotlin
fun getConnection(): Connection? = connection

// In repository methods:
val conn = db.getConnection() ?: return false
// ... use prepared statement
```

Connection is:
- Single long-lived connection (pooling not needed for SQLite)
- Initialized in SQLiteManager.initialize()
- Closed in SQLiteManager.close() (via plugin.onDisable)

---

## 8. Initialization & Lifecycle

### Plugin Enable Sequence

```kotlin
override fun onEnable() {
    instance = this
    printBanner()

    // 1. Load configuration
    configManager = ConfigManager(this)
    configManager.load()

    // 2. Setup database
    sqliteManager = SQLiteManager(this)
    sqliteManager.initialize()

    // 3. Initialize business services (NOT async yet)
    whitelistService = WhitelistService(this)
    ticketService = TicketService(this)
    scriptService = ScriptService(this)
    antiSpamService = AntiSpamService(this)

    // 4. Start Discord (ASYNC - doesn't block)
    discordBotService = DiscordBotService(this)
    server.scheduler.runTaskAsynchronously(this, Runnable {
        try {
            discordBotService.start()
        } catch (e: Exception) {
            logger.severe("[StarlightBot] Failed to start Discord bot: ${e.message}")
        }
    })

    // 5. Register Minecraft listeners (SYNC)
    registerMinecraftListeners()

    // 6. Start logging (captures all logs from now on)
    loggingService = LoggingService(this)
    loggingService.start()

    // 7. Player count service (started from DiscordBotService.ReadyListener)
    playerCountService = PlayerCountService(this)

    consoleLog("<green>[StarlightBot] Plugin enabled successfully.")
}
```

### Plugin Disable Sequence

```kotlin
override fun onDisable() {
    consoleLog("<yellow>[StarlightBot] Shutting down...")

    // 1. Stop player count updates
    if (::playerCountService.isInitialized) playerCountService.stop()

    // 2. Stop logging
    if (::loggingService.isInitialized) loggingService.stop()

    // 3. Shutdown Discord bot
    if (::discordBotService.isInitialized) discordBotService.shutdown()

    // 4. Close database
    if (::sqliteManager.isInitialized) sqliteManager.close()

    consoleLog("<red>[StarlightBot] Plugin disabled.")
}
```

### Discord Bot Startup (DiscordBotService.start)

```kotlin
// Create JDA with intents and cache policies
jda = JDABuilder.createDefault(token)
    .setActivity(Activity.watching("the server"))
    .enableIntents(GUILD_MESSAGES, MESSAGE_CONTENT, GUILD_MEMBERS, ...)
    .setMemberCachePolicy(MemberCachePolicy.ALL)
    .addEventListeners(
        ReadyListener(),
        WhitelistMessageListener(plugin),
        ButtonInteractionListener(plugin),
        SelectMenuListener(plugin),
        AntiSpamListener(plugin)
    )
    .build()

// Block until ready
jda!!.awaitReady()

// Register slash commands after ready
val slashCommandManager = SlashCommandManager(plugin, jda!!)
slashCommandManager.registerCommands()

// Start player count updates
plugin.playerCountService.start()

// Send server start log
sendServerStartEmbed()
```

---

## 9. Implementation Guidelines for New Features

### Adding a New Command

1. **Create command class** in `commands/discord/`
   ```kotlin
   class MyNewCommand(private val plugin: StarlightBot) {
       fun handle(event: SlashCommandInteractionEvent) {
           if (!isAdmin(event)) {
               event.reply("No permission").setEphemeral(true).queue()
               return
           }
           // ... implementation
           event.reply("Done!").setEphemeral(true).queue()
       }
       
       private fun isAdmin(event: SlashCommandInteractionEvent): Boolean {
           val member = event.member ?: return false
           val config = plugin.configManager
           return member.roles.any { it.id == config.adminRoleId }
               || member.id in config.adminUserIds
       }
   }
   ```

2. **Register in SlashCommandManager**
   ```kotlin
   // In class properties
   private val myNewCommand = MyNewCommand(plugin)

   // In registerCommands()
   Commands.slash("mycommand", "My command description")
       .addOption(OptionType.STRING, "param", "Description", true)

   // In onSlashCommandInteraction()
   "mycommand" -> myNewCommand.handle(event)
   ```

3. **Add config properties if needed**
   ```yaml
   my_feature:
     property_name: "value"
   ```
   ```kotlin
   val myFeatureProperty: String get() = config.getString("my_feature.property_name", "default")!!
   ```

### Adding a New Service

1. **Create class** in `service/`
2. **Initialize in StarlightBot.onEnable()**
   ```kotlin
   myService = MyService(this)
   ```
3. **Implement lifecycle methods if needed**
   ```kotlin
   fun start() { /* setup */ }
   fun stop() { /* cleanup */ }
   ```
4. **Stop in StarlightBot.onDisable()**
   ```kotlin
   if (::myService.isInitialized) myService.stop()
   ```

### Adding Database Table

1. **Create in SQLiteManager.createTables()**
   ```kotlin
   conn.createStatement().use { stmt ->
       stmt.executeUpdate("""
           CREATE TABLE IF NOT EXISTS my_table (
               id INTEGER PRIMARY KEY AUTOINCREMENT,
               data TEXT NOT NULL,
               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
           )
       """.trimIndent())
   }
   ```

2. **Create Repository** in `database/tables/`
3. **Access via service** → **Use in command/listener**

### Adding Discord Listener

1. **Create class** extending `ListenerAdapter`
2. **Override relevant methods** (onButtonInteraction, onMessageReceived, etc.)
3. **Register in DiscordBotService.start()**
   ```kotlin
   .addEventListeners(
       MyNewListener(plugin),
       // ...
   )
   ```

---

## 10. Key Design Patterns & Best Practices

### Async/Sync Pattern
- **Long operations**: Use `plugin.server.scheduler.runTaskAsynchronously()`
- **Bukkit API calls**: Use `plugin.server.scheduler.runTask()`
- **Discord operations**: Always async (JDA queues automatically)
- **Database**: OK to call sync from async threads

### Error Handling
```kotlin
try {
    // Operation
} catch (e: Exception) {
    plugin.logger.warning("[StarlightBot] Error: ${e.message}")
    // Send user-friendly error response
}
```

### Admin Permission Check
Pattern used consistently:
```kotlin
private fun isAdmin(event: SlashCommandInteractionEvent): Boolean {
    val member = event.member ?: return false
    val config = plugin.configManager
    return member.roles.any { it.id == config.adminRoleId }
        || member.id in config.adminUserIds
}
```

### DM Safe Pattern
```kotlin
fun sendDmSafely(user: User, message: String) {
    user.openPrivateChannel().queue(
        { channel -> channel.sendMessage(message).queue() },
        { plugin.logger.fine("[StarlightBot] Could not open DM") }
    )
}
```

### Null Safety
- Always check optional values: `event.getOption(...)?. asString ?: return`
- Use safe calls: `jda?.getTextChannelById(...)`
- Default to empty: `.getStringList(...) // returns empty list if missing`

### Logging Pattern
- Info level: Feature enabled, important events
- Warning level: Configuration issues, recoverable errors
- Severe: Plugin failures
- Use `plugin.consoleLog()` for colored console output
- Use `plugin.logger` for standard logging

### Button ID Encoding
- Include data in button ID: `ticket_claim_123` (ticket ID 123)
- Parse on interaction: `buttonId.removePrefix("ticket_claim_").toIntOrNull()`

---

## Summary

**StarlightBot is structured as:**
- **Plugin** (StarlightBot.kt) - Lifecycle manager
- **Config** (ConfigManager) - Centralized configuration
- **Database** (SQLiteManager + Repositories) - Data persistence
- **Services** (LoggingService, WhitelistService, etc.) - Business logic
- **Commands** (SlashCommandManager) - Discord command routing
- **Listeners** (ButtonInteractionListener, etc.) - Discord event handling
- **Utilities** (EmbedFactory, MojangAPI, etc.) - Reusable helpers

All components are loosely coupled through the main plugin instance, making it easy to add new features without affecting existing code.
