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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Points automatiques des locaux d'entreprise : quand une entreprise achète (ou loue) un bâtiment à l'agence immobilière (mod Portes), un point
 * de type « Entreprise » apparaît sur la carte de tous les joueurs, avec l'état ouvert / fermé de l'entreprise. Le point est recalculé à
 * partir de la liste des locaux (source de vérité : le mod Portes), donc il suit tout seul : renommage de l'entreprise, revente, fin de
 * location, fermeture de l'entreprise. Un seul point par local ; nom unique.
 *
 * Règles : une entreprise dont l'admin a déjà posé un point lié à la main n'en reçoit pas d'automatique ; un point automatique supprimé par
 * l'admin ne revient pas tant que le local reste à l'entreprise ; un point automatique modifié par l'admin devient un point normal.
 * Appelé toutes les 30 secondes et juste après chaque achat ou revente (par réflexion depuis le mod Portes).
 */
public final class BuildingWaypoints {
    private BuildingWaypoints() {}

    private static boolean tried;
    private static Method buildings;

    private record Building(int company, String dim, int x, int y, int z, String house) {
        String key() { return dim + "|" + house.toLowerCase(Locale.ROOT); }
    }

    @SuppressWarnings("unchecked")
    private static List<Building> read(MinecraftServer s) {
        List<Building> out = new ArrayList<>();
        synchronized (BuildingWaypoints.class) {
            if (!tried) {
                tried = true;
                try {
                    if (ModList.get().isLoaded("minenorthportes")) buildings = Class.forName("fr.minenorth.portes.api.PortesApi").getMethod("companyBuildings", MinecraftServer.class);
                } catch (Throwable t) {
                    buildings = null;
                }
            }
        }
        if (buildings == null) return out;
        try {
            for (String line : (List<String>) buildings.invoke(null, s)) {
                String[] p = line.split("[|]", 6);
                if (p.length < 6) continue;
                out.add(new Building(Integer.parseInt(p[0]), p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]), p[5]));
            }
        } catch (Throwable t) {
            MineNorthMap.LOGGER.warn("[Carte] Locaux d'entreprise illisibles : {}", t.toString());
        }
        return out;
    }

    /** Remet les points automatiques en accord avec les locaux d'entreprise. Renvoie vrai si la carte a changé. */
    public static synchronized boolean reconcile(MinecraftServer s) {
        if (!CompanyBridge.available()) return false;
        WaypointSavedData data = WaypointSavedData.get(s);
        Map<Integer, String> companyNames = new HashMap<>();
        for (Object[] c : CompanyBridge.companies(s)) companyNames.put((Integer) c[0], (String) c[1]);

        // locaux voulus : entreprise active, pas déjà couverte par un point posé à la main
        Set<String> autoNames = new HashSet<>();
        for (String n : data.autoMap().values()) autoNames.add(n.toLowerCase(Locale.ROOT));
        Set<Integer> manualCompanies = new HashSet<>();
        for (Waypoint w : data.all()) if (w.company() != Waypoint.NO_COMPANY && !autoNames.contains(w.name().toLowerCase(Locale.ROOT))) manualCompanies.add(w.company());

        List<Building> all = read(s);
        Map<Integer, Integer> perCompany = new HashMap<>();
        for (Building b : all) perCompany.merge(b.company(), 1, Integer::sum);
        Map<String, Building> wanted = new LinkedHashMap<>();
        for (Building b : all) {
            if (!companyNames.containsKey(b.company()) || manualCompanies.contains(b.company())) continue;
            wanted.put(b.key(), b);
        }

        boolean changed = false;
        // un local qui n'est plus à une entreprise : le refus de l'admin est oublié
        if (data.suppressed().removeIf(k -> !wanted.containsKey(k))) { data.setDirty(); changed = true; }

        // 1) retirer les points dont le local n'est plus voulu
        for (Map.Entry<String, String> e : new ArrayList<>(data.autoMap().entrySet())) {
            if (wanted.containsKey(e.getKey())) continue;
            data.remove(e.getValue());
            data.autoMap().remove(e.getKey());
            data.setDirty();
            changed = true;
        }

        WaypointType type = WaypointTypes.get("entreprise") != null ? WaypointTypes.get("entreprise") : WaypointTypes.fallback();
        // 2) créer / mettre à jour
        for (Map.Entry<String, Building> en : wanted.entrySet()) {
            String key = en.getKey();
            Building b = en.getValue();
            if (data.suppressed().contains(key)) continue;
            String base = companyNames.get(b.company());
            if (perCompany.getOrDefault(b.company(), 1) > 1) base = base + " - " + b.house();
            if (base.length() > 32) base = base.substring(0, 32).trim();
            String current = data.autoMap().get(key);
            Waypoint existing = current == null ? null : data.get(current);
            if (current != null && existing == null) { data.autoMap().remove(key); current = null; }   // point disparu : on le recrée
            String name = uniqueName(data, base, current);
            if (existing == null) {
                data.put(new Waypoint(name, b.dim(), b.x(), b.y(), b.z(), type.color(), type.id(), b.company(), Waypoint.STATUS_NONE));
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
                Waypoint upd = w.withPos(b.dim(), b.x(), b.y(), b.z()).withType(type.id()).withCompany(b.company());
                if (!upd.equals(w)) {
                    data.put(upd);
                    changed = true;
                }
            }
        }
        if (changed) NetworkHandler.syncAll(s);
        return changed;
    }

    /** Nom libre : celui voulu, sinon avec un numéro. Le point automatique lui-même ne compte pas comme un doublon. */
    private static String uniqueName(WaypointSavedData data, String base, String own) {
        String n = base;
        for (int i = 2; data.exists(n) && !(own != null && n.equalsIgnoreCase(own)); i++) {
            String suffix = " (" + i + ")";
            n = (base.length() + suffix.length() > 32 ? base.substring(0, 32 - suffix.length()).trim() : base) + suffix;
        }
        return n;
    }
}
