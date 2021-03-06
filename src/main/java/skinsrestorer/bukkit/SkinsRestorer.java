package skinsrestorer.bukkit;

import org.bstats.bukkit.MetricsLite;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.update.spiget.UpdateCallback;
import skinsrestorer.bukkit.commands.GUICommand;
import skinsrestorer.bukkit.commands.SkinCommand;
import skinsrestorer.bukkit.commands.SrCommand;
import skinsrestorer.bukkit.listener.PlayerJoin;
import skinsrestorer.bukkit.skinfactory.SkinFactory;
import skinsrestorer.bukkit.skinfactory.UniversalSkinFactory;
import skinsrestorer.shared.storage.Config;
import skinsrestorer.shared.storage.CooldownStorage;
import skinsrestorer.shared.storage.Locale;
import skinsrestorer.shared.storage.SkinStorage;
import skinsrestorer.shared.utils.MojangAPI;
import skinsrestorer.shared.utils.MojangAPI.SkinRequestException;
import skinsrestorer.shared.utils.MySQL;
import skinsrestorer.shared.utils.ReflectionUtil;
import skinsrestorer.shared.utils.UpdateChecker;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

public class SkinsRestorer extends JavaPlugin {

    private static SkinsRestorer instance;
    private SkinFactory factory;
    private MySQL mysql;
    private boolean bungeeEnabled;
    private UpdateChecker updateChecker;
    private UpdateDownloader updateDownloader;
    private CommandSender console;

    public static SkinsRestorer getInstance() {
        return instance;
    }

    public SkinFactory getFactory() {
        return factory;
    }

    public MySQL getMySQL() {
        return mysql;
    }

    public String getVersion() {
        return getDescription().getVersion();
    }

    public UpdateChecker getUpdateChecker() {
        return this.updateChecker;
    }

    public void onEnable() {
        console = getServer().getConsoleSender();

        @SuppressWarnings("unused")
        MetricsLite metrics = new MetricsLite(this);

        updateChecker = new UpdateChecker(2124, this.getDescription().getVersion(), this.getLogger(), "SkinsRestorerUpdater/Bukkit");
        updateDownloader = new UpdateDownloader(this);

        instance = this;
        factory = new UniversalSkinFactory();
        Config.load(getResource("config.yml"));
        Locale.load();

        console.sendMessage("§e[§2SkinsRestorer§e] §aDetected Minecraft §e" + ReflectionUtil.serverVersion + "§a, using §e" + factory.getClass().getSimpleName() + "§a.");

        // Detect ChangeSkin
        if (getServer().getPluginManager().getPlugin("ChangeSkin") != null) {
            console.sendMessage("§e[§2SkinsRestorer§e] §cWe have detected ChangeSkin on your server, disabling SkinsRestorer.");
            Bukkit.getPluginManager().disablePlugin(this);
        }

        // Check if we are running in bungee mode
        this.checkBungeeMode();

        // Check for updates
        this.checkUpdate(bungeeEnabled);

        if (bungeeEnabled) {
            Bukkit.getMessenger().registerOutgoingPluginChannel(this, "sr:skinchange");
            Bukkit.getMessenger().registerIncomingPluginChannel(this, "sr:skinchange", (channel, player, message) -> {
                if (!channel.equals("sr:skinchange"))
                    return;

                Bukkit.getScheduler().runTaskAsynchronously(getInstance(), () -> {
                    DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));

                    try {
                        String subchannel = in.readUTF();

                        if (subchannel.equalsIgnoreCase("SkinUpdate")) {
                            try {
                                factory.applySkin(player,
                                        SkinStorage.createProperty(in.readUTF(), in.readUTF(), in.readUTF()));
                            } catch (IOException ignored) {
                            }
                            factory.updateSkin(player);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            });
            return;
        }

        // Initialise MySQL
        if (Config.USE_MYSQL)
            SkinStorage.init(mysql = new MySQL(
                    Config.MYSQL_HOST,
                    Config.MYSQL_PORT,
                    Config.MYSQL_DATABASE,
                    Config.MYSQL_USERNAME,
                    Config.MYSQL_PASSWORD
            ));
        else
            SkinStorage.init(getDataFolder());

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new CooldownStorage(), 0, 20);

        // Commands
        getCommand("skinsrestorer").setExecutor(new SrCommand());
        getCommand("skin").setExecutor(new SkinCommand());
        getCommand("skins").setExecutor(new GUICommand());

        // Events
        Bukkit.getPluginManager().registerEvents(new SkinsGUI(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoin(), this);

        // Preload default skins
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            if (Config.DEFAULT_SKINS_ENABLED)
                for (String skin : Config.DEFAULT_SKINS)
                    try {
                        SkinStorage.setSkinData(skin, MojangAPI.getSkinProperty(MojangAPI.getUUID(skin)));
                    } catch (SkinRequestException e) {
                        if (SkinStorage.getSkinData(skin) == null)
                            console.sendMessage("§e[§2SkinsRestorer§e] §cDefault Skin '" + skin + "' request error: " + e.getReason());
                    }
        });

    }

    private void checkBungeeMode() {
        try {
            bungeeEnabled = getServer().spigot().getConfig().getBoolean("settings.bungeecord");

            // sometimes it does not get the right "bungeecord: true" setting
            // we will try it again with the old function from SR 13.3
            // https://github.com/DoNotSpamPls/SkinsRestorerX/blob/cbddd95ac36acb5b1afff2b9f48d0fc5b5541cb0/src/main/java/skinsrestorer/bukkit/SkinsRestorer.java#L109
            if (!bungeeEnabled) {
                bungeeEnabled = YamlConfiguration.loadConfiguration(new File("spigot.yml")).getBoolean("settings.bungeecord");
            }
        } catch (Throwable e) {
            bungeeEnabled = false;
        }
    }

    private void checkUpdate(boolean bungeeMode) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            if (Config.UPDATER_ENABLED) {
                updateChecker.checkForUpdate(new UpdateCallback() {
                    @Override
                    public void updateAvailable(String newVersion, String downloadUrl, boolean hasDirectDownload) {
                        console.sendMessage("§e[§2SkinsRestorer§e] §a----------------------------------------------");
                        console.sendMessage("§e[§2SkinsRestorer§e] §a    +===============+");
                        console.sendMessage("§e[§2SkinsRestorer§e] §a    | SkinsRestorer |");
                        if (bungeeMode) {
                            console.sendMessage("§e[§2SkinsRestorer§e] §a    |---------------|");
                            console.sendMessage("§e[§2SkinsRestorer§e] §a    |  §eBungee Mode§a  |");
                        }
                        console.sendMessage("§e[§2SkinsRestorer§e] §a    +===============+");
                        console.sendMessage("§e[§2SkinsRestorer§e] §a----------------------------------------------");
                        console.sendMessage("§e[§2SkinsRestorer§e] §b    Current version: §c" + getVersion());
                        if (hasDirectDownload) {
                            console.sendMessage("§e[§2SkinsRestorer§e]     A new version is available! Downloading it now...");
                            if (updateDownloader.downloadUpdate()) {
                                console.sendMessage("§e[§2SkinsRestorer§e] Update downloaded successfully, it will be applied on the next restart.");
                            } else {
                                // Update failed
                                console.sendMessage("§e[§2SkinsRestorer§e] §cCould not download the update, reason: " + updateDownloader.getFailReason());
                            }
                        } else {
                            console.sendMessage("§e[§2SkinsRestorer§e] §e    A new version is available! Download it at:");
                            console.sendMessage("§e[§2SkinsRestorer§e] §e    " + downloadUrl);
                        }
                        console.sendMessage("§e[§2SkinsRestorer§e] §a----------------------------------------------");
                    }

                    @Override
                    public void upToDate() {
                        console.sendMessage("§e[§2SkinsRestorer§e] §a----------------------------------------------");
                        console.sendMessage("§e[§2SkinsRestorer§e] §a    +===============+");
                        console.sendMessage("§e[§2SkinsRestorer§e] §a    | SkinsRestorer |");
                        if (bungeeMode) {
                            console.sendMessage("§e[§2SkinsRestorer§e] §a    |---------------|");
                            console.sendMessage("§e[§2SkinsRestorer§e] §a    |  §eBungee Mode§a  |");
                        }
                        console.sendMessage("§e[§2SkinsRestorer§e] §a    +===============+");
                        console.sendMessage("§e[§2SkinsRestorer§e] §a----------------------------------------------");
                        console.sendMessage("§e[§2SkinsRestorer§e] §b    Current version: §a" + getVersion());
                        console.sendMessage("§e[§2SkinsRestorer§e] §a    This is the latest version!");
                        console.sendMessage("§e[§2SkinsRestorer§e] §a----------------------------------------------");
                    }
                });
            }
        });
    }
}
