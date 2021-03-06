package skinsrestorer.bungee.commands;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.connection.LoginResult;
import skinsrestorer.bungee.SkinApplier;
import skinsrestorer.bungee.SkinsRestorer;
import skinsrestorer.shared.storage.Config;
import skinsrestorer.shared.storage.Locale;
import skinsrestorer.shared.storage.SkinStorage;
import skinsrestorer.shared.utils.MojangAPI;
import skinsrestorer.shared.utils.ServiceChecker;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class SrCommand extends Command {

    public SrCommand() {
        super("skinsrestorer", null, "sr");
    }

    private void sendHelp(CommandSender sender) {
        if (!Locale.SR_LINE.isEmpty())
            sender.sendMessage(new TextComponent(Locale.SR_LINE));
        sender.sendMessage(new TextComponent(Locale.HELP_ADMIN.replace("%ver%", SkinsRestorer.getInstance().getVersion())));
        if (!Locale.SR_LINE.isEmpty())
            sender.sendMessage(new TextComponent(Locale.SR_LINE));
    }

    private ProxiedPlayer getPlayerFromNick(String nick) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(nick);

        if (player == null)
            for (ProxiedPlayer pl : ProxyServer.getInstance().getPlayers())
                if (pl.getName().startsWith(nick)) {
                    player = pl;
                    break;
                }

        return player;
    }


    public void execute(final CommandSender sender, final String[] args) {

        if (!sender.hasPermission("skinsrestorer.cmds")) {
            sender.sendMessage(new TextComponent(Locale.PLAYER_HAS_NO_PERMISSION));
            return;
        }

        if (args.length < 1) {
            this.sendHelp(sender);
            return;
        }

        String cmd = args[0].toLowerCase();

        switch (cmd) {
            case "set": {
                if (args.length < 3) {
                    this.sendHelp(sender);
                    return;
                }

                final ProxiedPlayer p = this.getPlayerFromNick(args[1]);
                final String skin = args[2];

                if (p == null) {
                    sender.sendMessage(new TextComponent(Locale.NOT_ONLINE));
                    return;
                }

                ProxyServer.getInstance().getScheduler().runAsync(SkinsRestorer.getInstance(), () -> {
                    try {
                        MojangAPI.getUUID(skin);
                        SkinStorage.setPlayerSkin(p.getName(), skin);
                        SkinApplier.applySkin(p);
                        sender.sendMessage(new TextComponent(Locale.SKIN_CHANGE_SUCCESS));
                    } catch (MojangAPI.SkinRequestException e) {
                        sender.sendMessage(new TextComponent(e.getReason()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                return;
            }

            case "relaod": {
                Locale.load();
                Config.load(SkinsRestorer.getInstance().getResourceAsStream("config.yml"));
                sender.sendMessage(new TextComponent(Locale.RELOAD));
                return;
            }

            case "config": {
                sender.sendMessage(new TextComponent("§e[§2SkinsRestorer§e] §2/sr config has been removed from SkinsRestorer. Farewell!"));
                return;
            }

            case "status": {
                sender.sendMessage(new TextComponent("Checking needed services for SR to work properly..."));

                ProxyServer.getInstance().getScheduler().runAsync(SkinsRestorer.getInstance(), () -> {
                    ServiceChecker checker = new ServiceChecker();
                    checker.checkServices();

                    ServiceChecker.ServiceCheckResponse response = checker.getResponse();
                    List<String> results = response.getResults();

                    for (String result : results) {
                        sender.sendMessage(new TextComponent(result));
                    }
                    sender.sendMessage(new TextComponent("Working UUID API count: " + response.getWorkingUUID()));
                    sender.sendMessage(new TextComponent("Working Profile API count: " + response.getWorkingProfile()));
                    if (response.getWorkingUUID() >= 1 && response.getWorkingProfile() >= 1)
                        sender.sendMessage(new TextComponent("The plugin currently is in a working state."));
                    else
                        sender.sendMessage(new TextComponent("Plugin currently can't fetch new skins. You might check out our discord at https://discordapp.com/invite/012gnzKK9EortH0v2?utm_source=Discord%20Widget&utm_medium=Connect"));
                    sender.sendMessage(new TextComponent("Finished checking services."));
                });
                return;
            }

            case "drop": {
                if (args.length < 2) {
                    this.sendHelp(sender);
                    return;
                }

                String nick = args[1];

                SkinStorage.removeSkinData(nick);
                sender.sendMessage(new TextComponent(Locale.SKIN_DATA_DROPPED.replace("%player", nick)));
                return;
            }

            case "clear": {
                if (args.length < 2) {
                    this.sendHelp(sender);
                    return;
                }

                final ProxiedPlayer p = this.getPlayerFromNick(args[1]);

                if (p == null) {
                    sender.sendMessage(new TextComponent(Locale.NOT_ONLINE));
                    return;
                }

                ProxyServer.getInstance().getScheduler().runAsync(SkinsRestorer.getInstance(), () -> {
                    String skin = SkinStorage.getDefaultSkinNameIfEnabled(p.getName(), true);
                    SkinStorage.removePlayerSkin(p.getName());
                    try {
                        SkinApplier.applySkin(p, skin, null);
                        p.sendMessage(new TextComponent(Locale.SKIN_CLEAR_SUCCESS));
                        sender.sendMessage(new TextComponent(Locale.SKIN_CLEAR_ISSUER.replace("%player", p.getName())));
                    } catch (MojangAPI.SkinRequestException e) {
                        e.printStackTrace();
                        p.sendMessage(new TextComponent(e.getReason()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                return;
            }

            case "props": {
                if (!(sender instanceof ProxiedPlayer)) {
                    sender.sendMessage(new TextComponent(Locale.NOT_PLAYER));
                    return;
                }

                if (args.length < 2) {
                    this.sendHelp(sender);
                    return;
                }

                final ProxiedPlayer p = this.getPlayerFromNick(args[1]);

                if (p == null) {
                    sender.sendMessage(new TextComponent(Locale.NOT_ONLINE));
                    return;
                }

                InitialHandler h = (InitialHandler) p.getPendingConnection();
                LoginResult.Property prop = h.getLoginProfile().getProperties()[0];

                if (prop == null) {
                    sender.sendMessage(new TextComponent(Locale.NO_SKIN_DATA));
                    return;
                }

                sender.sendMessage(new TextComponent("\n§aName: §8" + prop.getName()));
                sender.sendMessage(new TextComponent("\n§aValue : §8" + prop.getValue()));
                sender.sendMessage(new TextComponent("\n§aSignature : §8" + prop.getSignature()));

                byte[] decoded = Base64.getDecoder().decode(prop.getValue());
                sender.sendMessage(new TextComponent("\n§aValue Decoded: §e" + Arrays.toString(decoded)));

                sender.sendMessage(new TextComponent("\n§e" + Arrays.toString(decoded)));

                sender.sendMessage(new TextComponent("§cMore info in console!"));
                return;
            }

            default: {
                return;
            }
        }
    }
}
