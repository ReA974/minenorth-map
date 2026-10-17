package fr.minenorth.map.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.minenorth.map.MineNorthMap;
import fr.minenorth.map.waypoint.Waypoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Points perso du joueur : enregistrés dans un fichier local (un par serveur, à côté du cache de la carte), jamais envoyés au serveur,
 * sans expiration. Le joueur les crée, renomme, recolore, déplace et supprime depuis la carte.
 */
public final class MesPoints {
    public static final int[] PALETTE = {0xFFD27A, 0xE53935, 0xFB8C00, 0xFDD835, 0x43A047, 0x26C6DA, 0x1E88E5, 0x8E24AA, 0xF06292};
    public static final int DEFAULT_COLOR = PALETTE[0];
    public static final int MAX_NAME = 32;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Waypoint> points = new ArrayList<>();
    private static Path loadedFrom;

    private MesPoints() {}

    // ------------------------------------------------------------------ fichier

    private static Path file() {
        Path dir = MapStorage.serverFolder();
        return dir == null ? null : dir.resolve("mes_points.json");
    }

    /** Charge le fichier du serveur courant (une seule fois par connexion). */
    private static void ensureLoaded() {
        Path f = file();
        if (f == null || f.equals(loadedFrom)) return;
        loadedFrom = f;
        points.clear();
        if (!Files.isRegularFile(f)) return;
        try {
            JsonArray arr = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonArray.class);
            if (arr == null) return;
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                String name = clean(o.get("name").getAsString());
                if (name.isEmpty() || exists(name)) continue;
                points.add(new Waypoint(name, o.get("dim").getAsString(), o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(),
                        o.get("color").getAsInt() & 0xFFFFFF));
            }
        } catch (IOException | RuntimeException ex) {
            MineNorthMap.LOGGER.warn("[Carte] Points perso illisibles ({}) : {}", f, ex.toString());
        }
    }

    private static void save() {
        Path f = file();
        if (f == null) return;
        JsonArray arr = new JsonArray();
        for (Waypoint w : points) {
            JsonObject o = new JsonObject();
            o.addProperty("name", w.name());
            o.addProperty("dim", w.dimension());
            o.addProperty("x", w.x());
            o.addProperty("y", w.y());
            o.addProperty("z", w.z());
            o.addProperty("color", w.color());
            arr.add(o);
        }
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(arr), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            MineNorthMap.LOGGER.warn("[Carte] Impossible d'enregistrer les points perso : {}", ex.toString());
        }
    }

    /** Déconnexion : le prochain serveur aura son propre fichier. */
    public static void clear() {
        points.clear();
        loadedFrom = null;
    }

    // ------------------------------------------------------------------ lecture

    public static List<Waypoint> all() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(points));
    }

    public static List<Waypoint> inDimension(String dim) {
        ensureLoaded();
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : points) if (w.dimension().equals(dim)) out.add(w);
        return out;
    }

    public static Waypoint byName(String name) {
        ensureLoaded();
        for (Waypoint w : points) if (w.name().equalsIgnoreCase(name)) return w;
        return null;
    }

    public static boolean isMine(Waypoint w) {
        ensureLoaded();
        for (Waypoint p : points) if (p.name().equals(w.name()) && p.dimension().equals(w.dimension())) return true;
        return false;
    }

    public static int count() {
        ensureLoaded();
        return points.size();
    }

    public static int max() { return ClientConfig.MAX_PERSO.get(); }

    /** Nom libre pour un nouveau point : « Point 1 », « Point 2 »… */
    public static String defaultName() {
        for (int i = 1; i < 10000; i++) if (!exists("Point " + i)) return "Point " + i;
        return "Point";
    }

    /** Nom pris par un autre point perso, un point du serveur ou un repère temporaire ? (le guidage retrouve les points par leur nom) */
    private static boolean exists(String name) {
        for (Waypoint w : points) if (w.name().equalsIgnoreCase(name)) return true;
        for (Waypoint w : ClientWaypoints.all()) if (w.name().equalsIgnoreCase(name)) return true;
        return TempWaypoints.byName(name) != null;
    }

    /** Nom nettoyé : sans codes de formatage ni caractères de contrôle, 32 caractères au plus. */
    public static String clean(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) if (c >= 32 && c != '§' && c != 127) sb.append(c);
        String s = sb.toString().trim();
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME).trim() : s;
    }

    // ------------------------------------------------------------------ gestion (renvoient un message d'erreur, ou null si tout va bien)

    public static String add(String rawName, String dim, int x, int y, int z, int color) {
        ensureLoaded();
        String name = clean(rawName);
        if (name.isEmpty()) return "Donne un nom au point.";
        if (points.size() >= max()) return "Limite atteinte (" + max() + " points). Supprime-en un.";
        if (exists(name)) return "Ce nom est déjà utilisé.";
        points.add(new Waypoint(name, dim, x, y, z, color & 0xFFFFFF));
        save();
        return null;
    }

    public static String update(String oldName, String rawName, int color) {
        ensureLoaded();
        int i = indexOf(oldName);
        if (i < 0) return "Point introuvable.";
        String name = clean(rawName);
        if (name.isEmpty()) return "Donne un nom au point.";
        Waypoint w = points.get(i);
        if (!name.equalsIgnoreCase(w.name()) && exists(name)) return "Ce nom est déjà utilisé.";
        boolean wasTracked = ClientWaypoints.isTracked(w);
        Waypoint nw = new Waypoint(name, w.dimension(), w.x(), w.y(), w.z(), color & 0xFFFFFF);
        points.set(i, nw);
        if (wasTracked) ClientWaypoints.track(nw);
        save();
        return null;
    }

    public static String move(String name, String dim, int x, int y, int z) {
        ensureLoaded();
        int i = indexOf(name);
        if (i < 0) return "Point introuvable.";
        Waypoint w = points.get(i);
        points.set(i, new Waypoint(w.name(), dim, x, y, z, w.color()));
        save();
        return null;
    }

    public static void remove(String name) {
        ensureLoaded();
        int i = indexOf(name);
        if (i < 0) return;
        Waypoint w = points.remove(i);
        if (ClientWaypoints.isTracked(w)) ClientWaypoints.track(null);
        save();
    }

    private static int indexOf(String name) {
        for (int i = 0; i < points.size(); i++) if (points.get(i).name().equalsIgnoreCase(name)) return i;
        return -1;
    }
}
