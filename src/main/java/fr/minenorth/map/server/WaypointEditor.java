package fr.minenorth.map.server;

import fr.minenorth.map.network.EditorInfoPacket;
import fr.minenorth.map.network.NetworkHandler;
import fr.minenorth.map.network.WaypointEditPacket;
import fr.minenorth.map.waypoint.Waypoint;
import fr.minenorth.map.waypoint.WaypointSavedData;
import fr.minenorth.map.waypoint.WaypointType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Édition des points depuis l'interface de la carte. Mêmes règles et même droit (OP niveau 2) que les commandes /carte point. */
public final class WaypointEditor {
    private WaypointEditor() {}

    public static boolean allowed(ServerPlayer p) { return p.hasPermissions(2); }

    public static void sendInfo(ServerPlayer p) {
        boolean ok = allowed(p);
        List<String> companies = new ArrayList<>();
        if (ok) for (Object[] c : CompanyBridge.companies(p.server)) companies.add(c[0] + "|" + c[1]);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new EditorInfoPacket(ok, companies));
    }

    private static void say(ServerPlayer p, String msg, boolean ok) {
        p.displayClientMessage(Component.literal(msg).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), true);
    }

    public static void handle(ServerPlayer p, WaypointEditPacket k) {
        if (k.action() == WaypointEditPacket.REFRESH) {
            sendInfo(p);
            return;
        }
        if (!allowed(p)) {
            say(p, "Réservé aux administrateurs.", false);
            return;
        }
        WaypointSavedData data = WaypointSavedData.get(p.server);
        String name = k.name().trim();

        switch (k.action()) {
            case WaypointEditPacket.ADD: {
                if (name.isEmpty() || name.length() > 32) { say(p, "Nom invalide (1 à 32 caractères).", false); return; }
                if (data.exists(name)) { say(p, "Un point porte déjà ce nom.", false); return; }
                WaypointType t = typeOrDefault(k.type());
                int company = company(p, k.company());
                if (company != Waypoint.NO_COMPANY && t.id().equals(Waypoint.DEFAULT_TYPE) && WaypointTypes.get("entreprise") != null) t = WaypointTypes.get("entreprise");
                data.put(new Waypoint(name, k.dim(), k.x(), k.y(), k.z(), t.color(), t.id(), company, Waypoint.STATUS_NONE));
                say(p, "Point « " + name + " » ajouté.", true);
                break;
            }
            case WaypointEditPacket.UPDATE: {
                Waypoint w = data.get(name);
                if (w == null) { say(p, "Point introuvable.", false); return; }
                String nn = k.newName().trim();
                if (nn.isEmpty() || nn.length() > 32) { say(p, "Nom invalide (1 à 32 caractères).", false); return; }
                if (!nn.equalsIgnoreCase(w.name()) && data.exists(nn)) { say(p, "Un point porte déjà ce nom.", false); return; }
                WaypointType t = typeOrDefault(k.type());
                int company = company(p, k.company());
                if (company != Waypoint.NO_COMPANY && t.id().equals(Waypoint.DEFAULT_TYPE) && WaypointTypes.get("entreprise") != null) t = WaypointTypes.get("entreprise");
                if (!nn.equals(w.name())) {
                    data.rename(w.name(), nn);
                    w = data.get(nn);
                }
                data.put(w.withType(t.id()).withCompany(company));
                say(p, "Point « " + nn + " » modifié.", true);
                break;
            }
            case WaypointEditPacket.MOVE: {
                Waypoint w = data.get(name);
                if (w == null) { say(p, "Point introuvable.", false); return; }
                data.put(w.withPos(k.dim(), k.x(), k.y(), k.z()));
                say(p, "Point « " + w.name() + " » déplacé.", true);
                break;
            }
            case WaypointEditPacket.REMOVE: {
                Waypoint w = data.remove(name);
                if (w == null) { say(p, "Point introuvable.", false); return; }
                say(p, "Point « " + w.name() + " » supprimé.", true);
                break;
            }
            default:
                return;
        }
        NetworkHandler.syncAll(p.server);
    }

    private static WaypointType typeOrDefault(String id) {
        WaypointType t = WaypointTypes.get(id);
        return t != null ? t : WaypointTypes.fallback();
    }

    /** Entreprise active existante, sinon aucune. */
    private static int company(ServerPlayer p, int id) {
        if (id < 0) return Waypoint.NO_COMPANY;
        for (Object[] c : CompanyBridge.companies(p.server)) if ((Integer) c[0] == id) return id;
        return Waypoint.NO_COMPANY;
    }
}
