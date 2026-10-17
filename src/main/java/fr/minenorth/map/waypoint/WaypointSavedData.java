package fr.minenorth.map.waypoint;

import fr.minenorth.map.server.CompanyBridge;
import fr.minenorth.map.server.WaypointTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Stockage des points de repère dans le monde (world/data/minenorth_map_waypoints.dat). */
public class WaypointSavedData extends SavedData {
    private static final String ID = "minenorth_map_waypoints";
    private final Map<String, Waypoint> waypoints = new LinkedHashMap<>();
    /** Points posés automatiquement pour les locaux d'entreprise : clé du local (« dimension|nom du bien ») -> nom du point. */
    private final Map<String, String> auto = new LinkedHashMap<>();
    /** Locaux dont l'admin a supprimé le point : ils ne reviennent pas tant que le local reste à l'entreprise. */
    private final java.util.Set<String> suppressed = new java.util.LinkedHashSet<>();

    public static WaypointSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(WaypointSavedData::load, WaypointSavedData::new, ID);
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public static WaypointSavedData load(CompoundTag tag) {
        WaypointSavedData d = new WaypointSavedData();
        ListTag list = tag.getList("waypoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Waypoint w = Waypoint.load(list.getCompound(i));
            d.waypoints.put(key(w.name()), w);
        }
        ListTag au = tag.getList("auto", Tag.TAG_COMPOUND);
        for (int i = 0; i < au.size(); i++) d.auto.put(au.getCompound(i).getString("k"), au.getCompound(i).getString("n"));
        ListTag su = tag.getList("suppressed", Tag.TAG_STRING);
        for (int i = 0; i < su.size(); i++) d.suppressed.add(su.getString(i));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Waypoint w : waypoints.values()) list.add(w.save());
        tag.put("waypoints", list);
        ListTag au = new ListTag();
        for (Map.Entry<String, String> e : auto.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("k", e.getKey());
            c.putString("n", e.getValue());
            au.add(c);
        }
        tag.put("auto", au);
        ListTag su = new ListTag();
        for (String k : suppressed) su.add(net.minecraft.nbt.StringTag.valueOf(k));
        tag.put("suppressed", su);
        return tag;
    }

    public Waypoint get(String name) { return waypoints.get(key(name)); }

    public boolean exists(String name) { return waypoints.containsKey(key(name)); }

    public void put(Waypoint w) {
        waypoints.put(key(w.name()), w);
        setDirty();
    }

    public Waypoint remove(String name) {
        Waypoint w = waypoints.remove(key(name));
        if (w != null) setDirty();
        return w;
    }

    /** Renomme en conservant l'ordre. */
    public void rename(String oldName, String newName) {
        Map<String, Waypoint> copy = new LinkedHashMap<>(waypoints);
        waypoints.clear();
        for (Map.Entry<String, Waypoint> e : copy.entrySet()) {
            if (e.getKey().equals(key(oldName))) {
                Waypoint w = e.getValue().withName(newName);
                waypoints.put(key(newName), w);
            } else {
                waypoints.put(e.getKey(), e.getValue());
            }
        }
        setDirty();
    }

    public Collection<Waypoint> all() { return waypoints.values(); }

    // ------------------------------------------------------------------ points automatiques (locaux d'entreprise)

    public Map<String, String> autoMap() { return auto; }

    public java.util.Set<String> suppressed() { return suppressed; }

    /** Le point n'est plus géré automatiquement (l'admin l'a modifié : il devient un point comme les autres). */
    public void adopt(String waypointName) {
        if (auto.values().removeIf(n -> n.equalsIgnoreCase(waypointName))) setDirty();
    }

    /** L'admin supprime un point automatique : le local est noté comme refusé. */
    public void suppressAuto(String waypointName) {
        for (Map.Entry<String, String> e : new ArrayList<>(auto.entrySet())) {
            if (e.getValue().equalsIgnoreCase(waypointName)) {
                suppressed.add(e.getKey());
                auto.remove(e.getKey());
                setDirty();
            }
        }
    }

    public List<Waypoint> snapshot() { return new ArrayList<>(waypoints.values()); }

    /** Copie envoyée aux clients : couleur déduite du type, état ouvert / fermé des entreprises liées. */
    public List<Waypoint> forClients(MinecraftServer server) {
        List<Waypoint> out = new ArrayList<>(waypoints.size());
        for (Waypoint w : waypoints.values()) {
            WaypointType t = WaypointTypes.get(w.type());
            if (t == null) t = WaypointTypes.fallback();
            int color = t != null ? t.color() : w.color();
            String type = t != null ? t.id() : w.type();
            int status = Waypoint.STATUS_NONE;
            if (w.company() != Waypoint.NO_COMPANY && CompanyBridge.available()) {
                status = CompanyBridge.isOpen(server, w.company()) ? Waypoint.STATUS_OPEN : Waypoint.STATUS_CLOSED;
            }
            out.add(w.withType(type).resolved(color, status));
        }
        return out;
    }
}
