package skinsrestorer.bungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.connection.LoginResult;
import net.md_5.bungee.connection.LoginResult.Property;
import skinsrestorer.shared.storage.SkinStorage;
import skinsrestorer.shared.utils.MojangAPI;
import skinsrestorer.shared.utils.ReflectionUtil;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class SkinApplier {

    private static Class<?> LoginResult;

    public static void applySkin(final ProxiedPlayer p, final String nick, InitialHandler handler) throws Exception {
        String uuid = null;
        if (p == null && handler == null)
            return;

        if (p != null) {
            uuid = p.getUniqueId().toString();
            handler = (InitialHandler) p.getPendingConnection();
        }
        Property textures = (Property) SkinStorage.getOrCreateSkinForPlayer(nick);

        if (handler.isOnlineMode()) {
            if (p != null) {
                sendUpdateRequest(p, textures);
                return;
            }
            // Online mode => get real IP from API
            uuid = MojangAPI.getUUID(nick);

            // Offline mode use offline uuid
        } else {
            uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + nick).getBytes(StandardCharsets.UTF_8)).toString();
        }

        LoginResult profile = handler.getLoginProfile();

        if (profile == null) {
            profile = new LoginResult(null, null, new Property[]{textures});
            // System.out.println("Created new LoginResult:");
            // System.out.println(profile.getProperties()[0]);
        }

        Property[] newprops = new Property[]{textures};

        profile.setProperties(newprops);
        ReflectionUtil.setObject(InitialHandler.class, handler, "loginProfile", profile);

        if (SkinsRestorer.getInstance().isMultiBungee()) {
            if (p != null)
                sendUpdateRequest(p, textures);
        } else {
            if (p != null)
                sendUpdateRequest(p, null);
        }

    }

    public static void applySkin(final String pname) throws Exception {
        ProxiedPlayer p = ProxyServer.getInstance().getPlayer(pname);
        applySkin(p, pname, null);
    }

    public static void applySkin(final ProxiedPlayer p) throws Exception {
        applySkin(p, p.getName(), null);
    }

    public static void init() {
        try {
            LoginResult = ReflectionUtil.getBungeeClass("connection", "LoginResult");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendUpdateRequest(ProxiedPlayer p, Property textures) {
        if (p == null)
            return;

        if (p.getServer() == null)
            return;

        System.out.println("[SkinsRestorer] Sending skin update request for " + p.getName());

        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);
        try {
            out.writeUTF("SkinUpdate");

            if (textures != null) {
                out.writeUTF(textures.getName());
                out.writeUTF(textures.getValue());
                out.writeUTF(textures.getSignature());
            }

            p.getServer().sendData("sr:skinchange", b.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}