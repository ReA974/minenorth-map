package fr.minenorth.map.server;

import fr.minenorth.map.MineNorthMap;
import fr.minenorth.map.network.NetworkHandler;
import fr.minenorth.map.waypoint.MapIcons;
import fr.minenorth.map.waypoint.Waypoint;
import fr.minenorth.map.waypoint.WaypointSavedData;
import fr.minenorth.map.waypoint.WaypointType;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Points automatiques des boutiques de l'annuaire (application « Boutiques » du téléphone, mod minenorth_shops) : un point par boutique publique,
 * à l'emplacement de son vendeur, sur la carte de tous les joueurs. Magasins : icône « boutiques » (sac framboise de l'application) ;
 * boutiques de rachat : pastilles dorées des acheteurs (ressource devinée d'après le nom, sinon « rachat »). Jamais le marché noir, les
 * boutiques d'entreprise ni celles réservées à un métier : la liste vient du mod Boutiques (PhoneDirectory.mapShops, par réflexion).
 *
 * Règles (comme BuildingWaypoints) : un point posé à la main à moins de COVER blocs du vendeur couvre déjà la boutique (pas de doublon) ;
 * un point automatique supprimé par l'admin ne revient pas tant que la boutique existe ; modifié par l'admin, il devient un point normal.
 * Vendeur introuvable (zone non chargée) : le point reste où il était. Appelé toutes les 30 secondes.
 */
public final class ShopWaypoints {
    private ShopWaypoints() {}

    /** Préfixe des clés de ces points dans autoMap / suppressed (BuildingWaypoints ne les touche pas). */
    public static final String KEY = "shop:";
    private static final int COVER = 10;

    private static boolean tried;
    private static Method shops;

    private record Shop(int id, boolean buy, String dim, int x, int y, int z, String name) {
        boolean located() { return !dim.isEmpty(); }
    }

    @SuppressWarnings("unchecked")
    private static List<Shop> read(MinecraftServer s) {
        List<Shop> out = new ArrayList<>();
        synchronized (ShopWaypoints.class) {
            if (!tried) {
                tried = true;
                try {
                    if (ModList.get().isLoaded("minenorth_shops")) shops = Class.forName("com.minenorth_shops.PhoneDirectory").getMethod("mapShops", MinecraftServer.class);
                } catch (Throwable t) {
                    shops = null;
                }
            }
        }
        if (shops == null) return null;
        try {
            for (String line : (List<String>) shops.invoke(null, s)) {
                String[] p = line.split("[|]", 7);
                if (p.length < 7) continue;
                boolean at = !p[2].isEmpty();
                out.add(new Shop(Integer.parseInt(p[0]), "1".equals(p[1]), p[2], at ? Integer.parseInt(p[3]) : 0, at ? Integer.parseInt(p[4]) : 0,
                        at ? Integer.parseInt(p[5]) : 0, p[6]));
            }
        } catch (Throwable t) {
            MineNorthMap.LOGGER.warn("[Carte] Boutiques illisibles : {}", t.toString());
            return null;
        }
        return out;
    }

    private static String icon(Shop shop) {
        if (!shop.buy()) return "boutiques";
        String guess = MapIcons.guess("rachat " + shop.name());
        return guess.startsWith("rachat") ? guess : "rachat";
    }

    /** Remet les points automatiques des boutiques en accord avec l'annuaire. Renvoie vrai si la carte a changé. */
    public static synchronized boolean reconcile(MinecraftServer s) {
        List<Shop> all = read(s);
        if (all == null) return false;   // mod Boutiques absent ou illisible : on ne touche à rien
        WaypointSavedData data = WaypointSavedData.get(s);
        Set<String> autoNames = new HashSet<>();
        for (String n : data.autoMap().values()) autoNames.add(n.toLowerCase(Locale.ROOT));

        Map<String, Shop> wanted = new LinkedHashMap<>();
        for (Shop shop : all) wanted.put(KEY + shop.id(), shop);

        boolean changed = false;
        // une boutique disparue (supprimée, devenue illégale, d'entreprise ou réservée) : le refus de l'admin est oublié
        if (data.suppressed().removeIf(k -> k.startsWith(KEY) && !wanted.containsKey(k))) { data.setDirty(); changed = true; }

        // 1) retirer les points dont la boutique n'est plus dans l'annuaire
        for (Map.Entry<String, String> e : new ArrayList<>(data.autoMap().entrySet())) {
            if (!e.getKey().startsWith(KEY) || wanted.containsKey(e.getKey())) continue;
            data.remove(e.getValue());
            data.autoMap().remove(e.getKey());
            data.setDirty();
            changed = true;
        }

        WaypointType type = WaypointTypes.get("commerce") != null ? WaypointTypes.get("commerce") : WaypointTypes.fallback();
        // 2) créer / mettre à jour
        for (Map.Entry<String, Shop> en : wanted.entrySet()) {
            String key = en.getKey();
            Shop shop = en.getValue();
            if (data.suppressed().contains(key)) continue;
            String current = data.autoMap().get(key);
            Waypoint existing = current == null ? null : data.get(current);
            if (current != null && existing == null) { data.autoMap().remove(key); current = null; }   // point disparu : on le recrée
            if (!shop.located()) continue;   // vendeur introuvable : le point reste où il était (ou attend d'être trouvé)
            if (existing == null && coveredByHand(data, autoNames, shop)) continue;
            String base = shop.name().length() > 32 ? shop.name().substring(0, 32).trim() : shop.name();
            String name = uniqueName(data, base, current);
            String icon = icon(shop);
            if (existing == null) {
                data.put(new Waypoint(name, shop.dim(), shop.x(), shop.y(), shop.z(), type.color(), type.id(), Waypoint.NO_COMPANY, Waypoint.STATUS_NONE, icon));
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
                Waypoint upd = w.withPos(shop.dim(), shop.x(), shop.y(), shop.z()).withIcon(icon);
                if (!upd.equals(w)) {
                    data.put(upd);
                    changed = true;
                }
            }
        }
        if (changed) NetworkHandler.syncAll(s);
        return changed;
    }

    /** Un point posé à la main (pas automatique) à moins de COVER blocs du vendeur montre déjà la boutique. */
    private static boolean coveredByHand(WaypointSavedData data, Set<String> autoNames, Shop shop) {
        for (Waypoint w : data.all()) {
            if (autoNames.contains(w.name().toLowerCase(Locale.ROOT)) || !w.dimension().equals(shop.dim())) continue;
            if (Math.abs(w.x() - shop.x()) <= COVER && Math.abs(w.z() - shop.z()) <= COVER && Math.abs(w.y() - shop.y()) <= COVER) return true;
        }
        return false;
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
