package dev.nandi0813.practice;

import com.github.retrooper.packetevents.PacketEvents;
import dev.faststats.ErrorTracker;
import dev.faststats.bukkit.BukkitContext;
import dev.nandi0813.practice.command.arena.ArenaCommand;
import dev.nandi0813.practice.command.event.EventCommand;
import dev.nandi0813.practice.command.ffa.FFACommand;
import dev.nandi0813.practice.command.ladder.LadderCommand;
import dev.nandi0813.practice.command.party.PartyCommand;
import dev.nandi0813.practice.command.practice.PracticeCommand;
import dev.nandi0813.practice.command.privatemessage.MessageCommand;
import dev.nandi0813.practice.command.privatemessage.ReplyCommand;
import dev.nandi0813.practice.command.singlecommands.*;
import dev.nandi0813.practice.command.staff.StaffCommand;
import dev.nandi0813.practice.listener.*;
import dev.nandi0813.practice.manager.arena.ArenaManager;
import dev.nandi0813.practice.manager.arena.listener.ArenaCopyUtilListener;
import dev.nandi0813.practice.manager.arena.listener.ArenaListener;
import dev.nandi0813.practice.manager.arena.setup.SpawnMarkerManager;
import dev.nandi0813.practice.manager.arena.util.ArenaWorldUtil;
import dev.nandi0813.practice.manager.backend.*;
import dev.nandi0813.practice.manager.backend.database.Database;
import dev.nandi0813.practice.manager.division.DivisionManager;
import dev.nandi0813.practice.manager.fight.event.EventManager;
import dev.nandi0813.practice.manager.fight.ffa.FFAListener;
import dev.nandi0813.practice.manager.fight.ffa.FFAManager;
import dev.nandi0813.practice.manager.fight.listener.BuildListener;
import dev.nandi0813.practice.manager.fight.listener.CreeperListener;
import dev.nandi0813.practice.manager.fight.listener.FireworkRocketCooldownListener;
import dev.nandi0813.practice.manager.fight.listener.ProjectileCooldownListener;
import dev.nandi0813.practice.manager.fight.match.MatchManager;
import dev.nandi0813.practice.manager.fight.match.util.EloMode;
import dev.nandi0813.practice.manager.fight.util.EntityHider;
import dev.nandi0813.practice.manager.fight.util.EntityHiderListener;
import dev.nandi0813.practice.manager.gui.setup.arena.ArenaGUISetupManager;
import dev.nandi0813.practice.manager.inventory.InventoryManager;
import dev.nandi0813.practice.manager.ladder.LadderManager;
import dev.nandi0813.practice.manager.ladder.abstraction.Ladder;
import dev.nandi0813.practice.manager.leaderboard.LeaderboardManager;
import dev.nandi0813.practice.manager.leaderboard.hologram.HologramManager;
import dev.nandi0813.practice.manager.matchhistory.MatchHistoryManager;
import dev.nandi0813.practice.manager.nametag.NametagManager;
import dev.nandi0813.practice.manager.playerkit.PlayerKitManager;
import dev.nandi0813.practice.manager.profile.ProfileManager;
import dev.nandi0813.practice.manager.profile.cosmetics.CosmeticsPermissionManager;
import dev.nandi0813.practice.manager.server.ServerManager;
import dev.nandi0813.practice.manager.sidebar.SidebarManager;
import dev.nandi0813.practice.util.*;
import dev.nandi0813.practice.util.placeholderapi.PlayerExpansion;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;

public final class ZonePractice extends JavaPlugin {

    @Getter
    private final Map<StartUpTypes, Boolean> startUpProgress = new EnumMap<>(StartUpTypes.class);

    @Getter
    private static ZonePractice instance;
    @Getter
    private static MiniMessage miniMessage = MiniMessageTagResolver.createMiniMessage();
    @Getter
    private static EntityHider entityHider;
    @Getter
    private static ArenaCopyUtilListener arenaCopyUtilListener;
    /**
     * The Mariadb backend, or {@code null} when it is disabled or unreachable. Every
     * caller must treat it as optional; the plugin stores profiles on disk regardless.
     */
    @Getter
    private static Database database;

    @Getter
    private static volatile boolean fullyLoaded = false;

    // BStats
    private Metrics metrics;

    public static final ErrorTracker ERROR_TRACKER = ErrorTracker.contextAware();
    private final BukkitContext faststats_metrics = new BukkitContext.Factory(this, "a515f033e0639d1407b9bd6a926c2efb")
        .errorTrackerService(ERROR_TRACKER)
        .metrics(factory -> factory.create())
        .create();

    @Override
    public void onLoad() {
        instance = this;

        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        instance = this;

        miniMessage = MiniMessageTagResolver.createMiniMessage();
        entityHider = new EntityHider(this, EntityHider.Policy.BLACKLIST);
        arenaCopyUtilListener = new ArenaCopyUtilListener();

        PacketEvents.getAPI().init();
        metrics = new Metrics(this, 34393);
        faststats_metrics.ready();

        if (VersionChecker.getBukkitVersion() == null) {
            Common.sendConsoleMMMessage("<red>Unsupported server version! Please use 1.21.x - 26.3");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        new SaveResource().saveResources(this);

        ConfigManager.createFile();
        EloMode.validateConfig();
        BackendManager.createFile(this);
        LanguageManager.createFile(this);
        GUIFile.createFile(this);
        initializeDatabase();
        MatchHistoryManager.getInstance(); // eagerly initialise singleton
        DivisionManager.getInstance().getData();
        ArenaWorldUtil.createArenaWorld();
        CosmeticsPermissionManager.registerAllPermissions();

        ZonePracticeApiImpl.setup();
        StartUpUtil.loadStartUpProgressMap();

        this.registerCommands(Bukkit.getServer());
        this.registerListeners(Bukkit.getPluginManager());

        ServerManager.getInstance().loadLobby();
        InventoryManager.getInstance().loadInventories();
        PlayerKitManager.getInstance().load();
        NametagManager.getInstance().initialize(); // Initialize after all plugins loaded to detect TAB conflicts

        LadderManager.getInstance().loadLadders(() ->
        {
            LadderManager.getInstance().getLadders().sort(Comparator.comparing(Ladder::getName));
            startUpProgress.replace(StartUpTypes.LADDER_LOADING, true);

            ArenaManager.getInstance().loadArenas(() ->
            {
                ArenaGUISetupManager.getInstance().loadGUIs();
                startUpProgress.replace(StartUpTypes.ARENA_LOADING, true);

                // Clean up any orphaned marker armor stands from previous server sessions
                org.bukkit.World arenasWorld = ArenaWorldUtil.getArenasWorld();
                if (arenasWorld != null) {
                    SpawnMarkerManager.getInstance().cleanupOrphanedMarkers(arenasWorld);
                }
            });

            ProfileManager.getInstance().loadProfiles(() ->
            {
                ProfileManager.getInstance().loadAllProfileInformations();
                startUpProgress.replace(StartUpTypes.PROFILE_LOADING, true);

                LeaderboardManager.getInstance().createAllLB(() ->
                {
                    startUpProgress.replace(StartUpTypes.LEADERBOARD_LOADING, true);
                    LadderManager.getInstance().loadGUIs();

                    HologramManager.getInstance().loadHolograms();
                    dev.nandi0813.practice.manager.leaderboard.hologram.HologramProtectionListener.register();
                    startUpProgress.replace(StartUpTypes.HOLOGRAM_LOADING, true);

                    SidebarManager.getInstance().load();
                    startUpProgress.replace(StartUpTypes.SIDEBAR_LOADING, true);

                    this.loadPlaceholderAPI();

                    // Mark plugin as fully loaded
                    fullyLoaded = true;

                    // Check for updates asynchronously and log to console
                    UpdateChecker.checkAsync(ZonePractice.this);
                });
            });
        });

        EventManager.getInstance().loadEventData(() ->
        {
            EventManager.getInstance().loadGUIs();
            startUpProgress.replace(StartUpTypes.EVENT_LOADING, true);
        });

        FFAManager.getInstance();
        EntityHiderListener.getInstance();
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();

        // Shutdown nametag manager and unregister packet blocker
        NametagManager.getInstance().shutdown();

        // Clear all spawn markers to prevent them persisting after server restart
        SpawnMarkerManager.getInstance().clearAllMarkers();

        // Clear all event spawn markers
        dev.nandi0813.practice.manager.fight.event.setup.EventSpawnMarkerManager.getInstance().clearAllMarkers();

        MatchManager.getInstance().endMatches();
        FFAManager.getInstance().endFFAs();
        HologramManager.getInstance().saveAndDespawnHolograms(); // Use saveAndDespawn for shutdown to clean up armor stands
        EventManager.getInstance().endEvents();
        EventManager.getInstance().saveEventData();
        ArenaManager.getInstance().saveArenas();
        ProfileManager.getInstance().saveProfiles();
        // The plugin is going down, so wait for the final database flush instead of
        // handing it to an executor that is about to be shut down.
        ProfileManager.getInstance().saveProfilesToDatabase().join();
        LadderManager.getInstance().saveLadders();
        SidebarManager.getInstance().close();
        InventoryManager.getInstance().setData();
        if (metrics != null) metrics.shutdown();
        faststats_metrics.shutdown();
        if (database != null) database.close();
        BackendManager.save();
    }

    /**
     * Opens the configured database.
     * <p>
     * A database that cannot be reached is not fatal: the plugin keeps running on
     * its file storage, so the failure is reported and the plugin starts without one.
     */
    private static void initializeDatabase() {
        if (!ConfigManager.getBoolean("MARIADB-DATABASE.ENABLED")) return;

        try {
            database = Database.forMariaDB(
                    ConfigManager.getString("MARIADB-DATABASE.CONNECTION.HOST"),
                    ConfigManager.getInt("MARIADB-DATABASE.CONNECTION.PORT"),
                    ConfigManager.getString("MARIADB-DATABASE.CONNECTION.DATABASE"),
                    ConfigManager.getString("MARIADB-DATABASE.CONNECTION.USER"),
                    ConfigManager.getString("MARIADB-DATABASE.CONNECTION.PASSWORD"),
                    Math.max(2, ConfigManager.getInt("MARIADB-DATABASE.CONNECTION.POOL-SIZE"))
            );
        } catch (Exception e) {
            Common.sendConsoleMMMessage("<red>Error during database initialization, continuing without it: "
                    + (e.getCause() != null ? e.getCause() : e).getMessage());
        }
    }

    /**
     * It registers all the commands that the plugin uses
     */
    private void registerCommands(Server server) {
        registerCommand(server, "accept", new AcceptCommand());
        registerCommand(server, "arena", new ArenaCommand());
        registerCommand(server, "duel", new DuelCommand());
        registerCommand(server, "event", new EventCommand());
        registerCommand(server, "ladder", new LadderCommand());
        registerCommand(server, "matchinv", new MatchStatsCommand());
        registerCommand(server, "matchhistory", new MatchHistoryCommand());
        registerCommand(server, "party", new PartyCommand());
        registerCommand(server, "practice", new PracticeCommand());
        registerCommand(server, "preview", new PreviewCommand());
        registerCommand(server, "divisions", new DivisionsCommand());
        registerCommand(server, "settings", new SettingsCommand());
        registerCommand(server, "setup", new SetupCommand());
        registerCommand(server, "spectate", new SpectateCommand());
        registerCommand(server, "staff", new StaffCommand());
        registerCommand(server, "statistics", new StatisticsCommand());
        registerCommand(server, "unranked", new UnrankedCommand());
        registerCommand(server, "ranked", new RankedCommand());
        registerCommand(server, "editor", new EditorCommand());
        registerCommand(server, "copykit", new CopyKitCommand());
        registerCommand(server, "ffa", new FFACommand());
        registerCommand(server, "ignorequeue", new IgnoreQueueCommand());
        registerCommand(server, "cosmetics", new CosmeticsCommand());
        registerCommand(server, "customqueue", new CustomQueueCommand());
        registerCommand(server, "nick", new NickCommand());

        if (ConfigManager.getBoolean("CHAT.PRIVATE-CHAT-ENABLED")) {
            new MessageCommand();
            new ReplyCommand();
        }

        if (ConfigManager.getBoolean("MATCH-SETTINGS.LEAVE-COMMAND.ENABLED")) {
            new LeaveCommand();
        }
    }

    private void registerCommand(Server server, String name, CommandExecutor executor) {
        PluginCommand command = server.getPluginCommand(name);
        if (command == null) return;

        command.setExecutor(executor);
        if (executor instanceof TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }

    private void loadPlaceholderAPI() {
        if (SoftDependUtil.isPAPI_ENABLED) {
            new PlayerExpansion("zppro").register();
            new PlayerExpansion("zpp").register();
        }
    }

    /**
     * It registers all the events that are used in the plugin
     */
    private void registerListeners(PluginManager pm) {

        pm.registerEvents(new PlayerPreLogin(), this);
        pm.registerEvents(new PlayerJoin(), this);
        pm.registerEvents(new PlayerQuit(), this);
        pm.registerEvents(new PlayerInteract(), this);
        pm.registerEvents(new WeatherChange(), this);
        pm.registerEvents(new ItemConsume(), this);
        pm.registerEvents(new ProjectileLaunch(), this);
        pm.registerEvents(new PlayerCommandPreprocess(), this);
        pm.registerEvents(new EntityDamage(), this);
        pm.registerEvents(new ArenaListener(), this);
        pm.registerEvents(new StatisticListener(), this);
        pm.registerEvents(arenaCopyUtilListener, this);
        pm.registerEvents(new BuildListener(), this);
        pm.registerEvents(new FFAListener(), this);
        pm.registerEvents(new ProjectileCooldownListener(), this);
        pm.registerEvents(new FireworkRocketCooldownListener(), this);
        pm.registerEvents(new PlayerChatListener(), this);
        pm.registerEvents(new CreeperListener(), this);
    }

}
