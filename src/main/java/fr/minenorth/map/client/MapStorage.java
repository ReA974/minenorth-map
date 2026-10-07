package fr.minenorth.map.client;

import fr.minenorth.map.MineNorthMap;
import fr.minenorth.map.network.NetworkHandler;
import fr.minenorth.map.network.RegionDataPacket;
import fr.minenorth.map.network.RequestRegionPacket;
import fr.minenorth.map.server.RegionCodec;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Côté client : la carte est COMMUNE et vient du serveur.
 * Les régions affichées sont demandées au serveur (et redemandées toutes les 30 s pour suivre les changements),
 * puis gardées en cache local : .minecraft/minenorth_map/<serveur>/<dimension>/r.X.Z.mnm
 */
public final class MapStorage {
    private static final long REFRESH_MS = 30_000L;
    private static final long TIMEOUT_MS = 15_000L;
    private static final int MAX_IN_FLIGHT = 8;

    private static final class ClientRegion {
        final int rx, rz;
        MapRegion tile;
        long version;
        long lastRequest;
        boolean inFlight;
        long lastAccess = System.currentTimeMillis();
        ClientRegion(int rx, int rz) { this.rx = rx; this.rz = rz; }
    }

    private static final Map<Long, ClientRegion> regions = new HashMap<>();
    private static String dimension;
    private static String serverKey;
    private static Path folder;
    private static int inFlight;
    private static int ticks;
    private static int diskLoadBudget;

    private MapStorage() {}

    // ------------------------------------------------------------------ cycle de vie

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (serverKey == null) serverKey = computeServerKey(mc);
        String dim = mc.level.dimension().location().toString();
        if (!dim.equals(dimension)) switchDimension(mc, dim);

        ticks++;
        diskLoadBudget = 3;
        long now = System.currentTimeMillis();
        for (ClientRegion r : regions.values()) {
            if (r.inFlight && now - r.lastRequest > TIMEOUT_MS) {
                r.inFlight = false;
                inFlight = Math.max(0, inFlight - 1);
            }
            if (r.tile != null) r.tile.uploadIfDirty();
        }
        if (ticks % 1200 == 0) unloadIdle(now);
    }

    public static void onLogout() {
        closeAll();
        dimension = null;
        serverKey = null;
        folder = null;
    }

    private static void switchDimension(Minecraft mc, String dim) {
        closeAll();
        dimension = dim;
        ResourceLocation id = ResourceLocation.tryParse(dim);
        String sub = id == null ? dim : id.getNamespace() + "_" + id.getPath();
        folder = mc.gameDirectory.toPath().resolve("minenorth_map").resolve(serverKey).resolve(sanitize(sub));
    }

    private static String computeServerKey(Minecraft mc) {
        String name;
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            name = "solo_" + mc.getSingleplayerServer().getWorldData().getLevelName();
        } else {
            ServerData sd = mc.getCurrentServer();
            name = sd != null ? "mp_" + sd.ip : "inconnu";
        }
        return sanitize(name);
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static void closeAll() {
        for (ClientRegion r : regions.values()) if (r.tile != null) r.tile.close();
        regions.clear();
        inFlight = 0;
    }

    private static void unloadIdle(long now) {
        Iterator<ClientRegion> it = regions.values().iterator();
        while (it.hasNext()) {
            ClientRegion r = it.next();
            if (!r.inFlight && now - r.lastAccess > 120_000L) {
                if (r.tile != null) r.tile.close();
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ affichage

    /** Texture d'une région pour l'affichage, ou null si pas (encore) de données. Thread de rendu. */
    static ResourceLocation regionTexture(int rx, int rz) {
        if (folder == null) return null;
        long k = ChunkPos.asLong(rx, rz);
        ClientRegion r = regions.get(k);
        if (r == null) {
            if (diskLoadBudget <= 0) return null; // on étale les lectures disque sur plusieurs ticks
            diskLoadBudget--;
            r = new ClientRegion(rx, rz);
            loadCache(r);
            regions.put(k, r);
        }
        long now = System.currentTimeMillis();
        r.lastAccess = now;
        if (!r.inFlight && inFlight < MAX_IN_FLIGHT && now - r.lastRequest > REFRESH_MS) {
            r.inFlight = true;
            r.lastRequest = now;
            inFlight++;
            NetworkHandler.CHANNEL.sendToServer(new RequestRegionPacket(dimension, rx, rz, r.version));
        }
        return r.tile != null ? r.tile.texture() : null;
    }

    // ------------------------------------------------------------------ réseau

    public static void onRegionData(RegionDataPacket p) {
        if (dimension == null || !dimension.equals(p.dim())) return;
        long k = ChunkPos.asLong(p.rx(), p.rz());
        ClientRegion r = regions.get(k);
        if (r == null) {
            r = new ClientRegion(p.rx(), p.rz());
            r.lastRequest = System.currentTimeMillis();
            regions.put(k, r);
        }
        if (r.inFlight) {
            r.inFlight = false;
            inFlight = Math.max(0, inFlight - 1);
        }
        if (p.data() == null) return; // inexistante ou inchangée
        byte[] colors = RegionCodec.decompress(p.data());
        if (colors == null) return;
        if (r.tile == null) r.tile = MapRegion.blank(p.rx(), p.rz());
        r.tile.applyPacked(colors);
        r.version = p.version();
        saveCache(r.rx, r.rz, p.version(), p.data());
    }

    // ------------------------------------------------------------------ cache disque

    private static Path cacheFile(int rx, int rz) {
        return folder.resolve("r." + rx + "." + rz + ".mnm");
    }

    private static void loadCache(ClientRegion r) {
        Path f = cacheFile(r.rx, r.rz);
        if (!Files.isRegularFile(f)) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(f)))) {
            if (in.readInt() != RegionCodec.MAGIC) return;
            long version = in.readLong();
            int len = in.readInt();
            if (len <= 0 || len > 4 * RegionCodec.BYTES) return;
            byte[] data = new byte[len];
            in.readFully(data);
            byte[] colors = RegionCodec.decompress(data);
            if (colors == null) return;
            r.tile = MapRegion.blank(r.rx, r.rz);
            r.tile.applyPacked(colors);
            r.version = version;
        } catch (IOException e) {
            MineNorthMap.LOGGER.warn("Carte : cache illisible {}", f, e);
        }
    }

    private static void saveCache(int rx, int rz, long version, byte[] data) {
        Path f = cacheFile(rx, rz);
        Util.ioPool().execute(() -> {
            Path tmp = f.resolveSibling(f.getFileName() + "." + System.nanoTime() + ".tmp");
            try {
                Files.createDirectories(f.getParent());
                try (OutputStream os = Files.newOutputStream(tmp); DataOutputStream out = new DataOutputStream(os)) {
                    out.writeInt(RegionCodec.MAGIC);
                    out.writeLong(version);
                    out.writeInt(data.length);
                    out.write(data);
                }
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                MineNorthMap.LOGGER.warn("Carte : écriture du cache impossible {}", f, e);
            }
        });
    }
}
