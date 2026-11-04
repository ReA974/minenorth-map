package fr.minenorth.map.server;

import fr.minenorth.map.MineNorthMap;
import fr.minenorth.map.network.NetworkHandler;
import fr.minenorth.map.waypoint.Waypoint;
import fr.minenorth.map.waypoint.WaypointSavedData;
import fr.minenorth.map.waypoint.WaypointType;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Points automatiques des terrains à bâtir disponibles (mod Portes, PortesApi.lands par réflexion) : « Terrain à vendre : nom » (ou à louer)
 * au centre du terrain, icône maison, sur la carte de tous les joueurs. Le point disparaît dès que le terrain est acheté ou loué et revient
 * quand il est remis sur le marché (après la remise à nu). Mêmes règles que les locaux d'entreprise : supprimé par l'admin, il ne revient
 * pas tant que le terrain reste disponible. Appelé toutes les 30 secondes et juste après chaque achat ou revente.
 */
public final class LandWaypoints {
    private LandWaypoints() {}

    /** Préfixe des clés de ces points dans autoMap / suppressed (BuildingWaypoints ne les touche pas). */
    public static final String KEY = "terrain:";

    private static boolean tried;
    private static Method lands;

    private record Land(String dim, int x, int y, int z, String name, boolean rent) {}

    @SuppressWarnings("unchecked")
    private static List<Land> read(MinecraftServer s) {
        synchronized (LandWaypoints.class) {
            if (!tried) {
                tried = true;
                try {
                    if (ModList.get().isLoaded("minenorthportes")) lands = Class.forName("fr.minenorth.portes.api.PortesApi").getMethod("lands", MinecraftServer.class);
                } catch (Throwable t) {
                    lands = null;
                }
            }
        }
        if (lands == null) return null;
        List<Land> out = new ArrayList<>();
        try {
            for (String line : (List<String>) lands.invoke(null, s)) {
                String[] p = line.split("[|]", 8);
                if (p.length < 6) continue;
                out.add(new Land(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4], "RENTAL".equals(p[5])));
            }
        } catch (Throwable t) {
            MineNorthMap.LOGGER.warn("[Carte] Terrains illisibles : {}", t.toString());
            return null;
        }
        return out;
    }

    public static synchronized boolean reconcile(MinecraftServer s) {
        List<Land> all = read(s);
        if (all == null) return false;
        WaypointSavedData data = WaypointSavedData.get(s);
        Map<String, Land> wanted = new LinkedHashMap<>();
        for (Land l : all) wanted.put(KEY + l.dim() + "|" + l.name().toLowerCase(Locale.ROOT), l);
        boolean changed = false;
        if (data.suppressed().removeIf(k -> k.startsWith(KEY) && !wanted.containsKey(k))) { data.setDirty(); changed = true; }
        for (Map.Entry<String, String> e : new ArrayList<>(data.autoMap().entrySet())) {
            if (!e.getKey().startsWith(KEY) || wanted.containsKey(e.getKey())) continue;
            data.remove(e.getValue());
            data.autoMap().remove(e.getKey());
            data.setDirty();
            changed = true;
        }
        WaypointType type = WaypointTypes.get("maison") != null ? WaypointTypes.get("maison") : WaypointTypes.fallback();
        for (Map.Entry<String, Land> en : wanted.entrySet()) {
            String key = en.getKey();
            Land l = en.getValue();
            if (data.suppressed().contains(key)) continue;
            String current = data.autoMap().get(key);
            Waypoint existing = current == null ? null : data.get(current);
            if (current != null && existing == null) { data.autoMap().remove(key); current = null; }
            String base = (l.rent() ? "Terrain à louer : " : "Terrain à vendre : ") + l.name();
            if (base.length() > 32) base = base.substring(0, 32).trim();
            String name = uniqueName(data, base, current);
            if (existing == null) {
                data.put(new Waypoint(name, l.dim(), l.x(), l.y(), l.z(), type.color(), type.id(), Waypoint.NO_COMPANY, Waypoint.STATUS_NONE, "maison"));
                data.autoMap().put(key, name);
                data.setDirty();
                changed = true;
            } else {
                Waypoint w = existing;
                if (!current.equals(name)) {
                    data.rename(current, name);
                    w = data.get(name);
                    data.autoMap().put(key, name);
                    data.setDirty();
                    changed = true;
                }
                Waypoint upd = w.withPos(l.dim(), l.x(), l.y(), l.z()).withIcon("maison");
                if (!upd.equals(w)) { data.put(upd); changed = true; }
            }
        }
        if (changed) NetworkHandler.syncAll(s);
        return changed;
    }

    private static String uniqueName(WaypointSavedData data, String base, String own) {
        String n = base;
        for (int i = 2; data.exists(n) && !(own != null && n.equalsIgnoreCase(own)); i++) {
            String suffix = " (" + i + ")";
            n = (base.length() + suffix.length() > 32 ? base.substring(0, 32 - suffix.length()).trim() : base) + suffix;
        }
        return n;
    }
}
