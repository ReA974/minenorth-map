package fr.minenorth.map.server;

import fr.minenorth.map.MineNorthMap;
import fr.minenorth.map.network.NetworkHandler;
import fr.minenorth.map.network.RegionDataPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.network.PacketDistributor;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Carte COMMUNE dessinée par le serveur (même carte pour tous les joueurs).
 * Stockage : <monde>/minenorth_map/<dimension>/r.X.Z.mnm
 * Sources : chunks chargés par les joueurs, blocs posés/cassés, et /carte generer.
 */
public final class ServerMapManager {
    private static final Map<ResourceKey<Level>, DimData> DIMS = new HashMap<>();
    private static final ConcurrentLinkedQueue<PendingChunk> LOADED = new ConcurrentLinkedQueue<>();
    private static final ArrayDeque<Request> REQUESTS = new ArrayDeque<>();
    private static final Map<UUID, Integer> REQUESTS_PER_PLAYER = new HashMap<>();
    private static MinecraftServer server;
    private static RenderJob job;
    private static int ticks;

    private record PendingChunk(ResourceKey<Level> dim, int cx, int cz) {}
    private record Request(UUID player, String dim, int rx, int rz, long knownVersion) {}

    private static final class DimData {
        final Path folder;
        final Map<Long, ServerRegion> regions = new HashMap<>();
        final Set<Long> absent = new HashSet<>();
        final LinkedHashSet<Long> dirtyChunks = new LinkedHashSet<>();
        DimData(Path folder) { this.folder = folder; }
    }

    private ServerMapManager() {}

    // ------------------------------------------------------------------ cycle de vie

    public static void onServerStarted(MinecraftServer s) {
        server = s;
        DIMS.clear();
        LOADED.clear();
        REQUESTS.clear();
        REQUESTS_PER_PLAYER.clear();
        job = null;
        PlayerTracker.reset();
    }

    public static void onServerStopping() {
        if (server == null) return;
        if (job != null) job.cancel();
        job = null;
        saveAll();
        DIMS.clear();
        server = null;
    }

    public static void tick() {
        if (server == null) return;
        ticks++;

        // 1) chunks chargés par les joueurs jamais dessinés
        if (ServerMapConfig.AUTO_MAP_LOADED.get()) {
            int budget = ServerMapConfig.LOADED_PER_TICK.get();
            PendingChunk p;
            while (budget > 0 && (p = LOADED.poll()) != null) {
                ServerLevel level = server.getLevel(p.dim());
                if (level == null) continue;
                if (isRendered(level, p.cx(), p.cz())) continue;
                LevelChunk c = level.getChunkSource().getChunkNow(p.cx(), p.cz());
                if (c == null) continue;
                renderChunk(level, c);
                budget--;
            }
        } else {
            LOADED.clear();
        }

        // 2) chunks modifiés
        int upd = ServerMapConfig.UPDATES_PER_TICK.get();
        for (Map.Entry<ResourceKey<Level>, DimData> e : DIMS.entrySet()) {
            if (upd <= 0) break;
            ServerLevel level = server.getLevel(e.getKey());
            Iterator<Long> it = e.getValue().dirtyChunks.iterator();
            while (upd > 0 && it.hasNext()) {
                long k = it.next();
                it.remove();
                if (level == null) continue;
                LevelChunk c = level.getChunkSource().getChunkNow(ChunkPos.getX(k), ChunkPos.getZ(k));
                if (c != null) {
                    renderChunk(level, c);
                    upd--;
                }
            }
        }

        // 3) toutes les 30 s : on rafraîchit les chunks autour des joueurs (pistons, explosions, etc.)
        if (ticks % 600 == 0) {
            for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
                int pcx = pl.getBlockX() >> 4, pcz = pl.getBlockZ() >> 4;
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++)
                        markDirty(pl.serverLevel(), pcx + dx, pcz + dz);
            }
        }

        // 4) génération lancée par un OP
        if (job != null && job.tick()) job = null;

        // 4b) positions des joueurs pour le staff autorisé
        PlayerTracker.tick(server);

        // 5) envois aux joueurs
        int sends = ServerMapConfig.REQUESTS_PER_TICK.get();
        Request r;
        while (sends-- > 0 && (r = REQUESTS.poll()) != null) {
            REQUESTS_PER_PLAYER.merge(r.player(), -1, Integer::sum);
            ServerPlayer pl = server.getPlayerList().getPlayer(r.player());
            if (pl == null) continue;
            answer(pl, r);
        }

        // 6) toutes les 5 min : sauvegarde + déchargement mémoire
        if (ticks % 6000 == 0) {
            saveAll();
            unloadIdle();
        }
    }

    // ------------------------------------------------------------------ événements

    public static void onChunkLoaded(ResourceKey<Level> dim, int cx, int cz) {
        if (server != null && LOADED.size() < 20000) LOADED.add(new PendingChunk(dim, cx, cz));
    }

    public static void markDirty(Level level, int cx, int cz) {
        if (server == null || !(level instanceof ServerLevel sl)) return;
        DimData d = dim(sl);
        if (d.dirtyChunks.size() < 20000) d.dirtyChunks.add(ChunkPos.asLong(cx, cz));
    }

    public static void onRequest(ServerPlayer player, String dim, int rx, int rz, long known) {
        if (server == null) return;
        int pending = REQUESTS_PER_PLAYER.getOrDefault(player.getUUID(), 0);
        if (pending >= 48) return; // le client redemandera plus tard
        REQUESTS_PER_PLAYER.put(player.getUUID(), pending + 1);
        REQUESTS.add(new Request(player.getUUID(), dim, rx, rz, known));
    }

    public static void onLogout(UUID id) {
        PlayerTracker.onLogout(id);
        REQUESTS_PER_PLAYER.remove(id);
        REQUESTS.removeIf(r -> r.player().equals(id));
    }

    // ------------------------------------------------------------------ génération

    public static RenderJob currentJob() { return job; }

    public static void startJob(RenderJob j) {
        if (job != null) job.cancel();
        job = j;
    }

    public static void stopJob() {
        if (job != null) job.cancel();
        job = null;
    }

    // ------------------------------------------------------------------ rendu

    static boolean isRendered(ServerLevel level, int cx, int cz) {
        ServerRegion r = region(level, cx >> 5, cz >> 5, false);
        return r != null && r.rendered.get(ServerRegion.chunkIndex(cx, cz));
    }

    static void renderChunk(ServerLevel level, LevelChunk chunk) {
        int cx = chunk.getPos().x, cz = chunk.getPos().z;
        ServerRegion r = region(level, cx >> 5, cz >> 5, true);
        boolean changed = ChunkColorizer.render(level, chunk, r.colors);
        int idx = ServerRegion.chunkIndex(cx, cz);
        if (changed) {
            r.markChanged();
            r.rendered.set(idx);
        } else if (!r.rendered.get(idx)) {
            r.rendered.set(idx);
            r.dirty = true;
        }
        r.lastAccess = System.currentTimeMillis();
    }

    private static DimData dim(ServerLevel level) {
        return DIMS.computeIfAbsent(level.dimension(), k -> {
            ResourceLocation id = k.location();
            Path f = server.getWorldPath(LevelResource.ROOT).resolve("minenorth_map")
                    .resolve((id.getNamespace() + "_" + id.getPath()).replaceAll("[^a-zA-Z0-9._-]", "_"));
            return new DimData(f);
        });
    }

    private static ServerRegion region(ServerLevel level, int rx, int rz, boolean create) {
        DimData d = dim(level);
        long k = ChunkPos.asLong(rx, rz);
        ServerRegion r = d.regions.get(k);
        if (r == null && !d.absent.contains(k)) {
            r = load(d, rx, rz);
            if (r != null) d.regions.put(k, r);
            else d.absent.add(k);
        }
        if (r == null && create) {
            r = ServerRegion.blank(rx, rz);
            d.regions.put(k, r);
            d.absent.remove(k);
        }
        if (r != null) r.lastAccess = System.currentTimeMillis();
        return r;
    }

    private static void answer(ServerPlayer pl, Request req) {
        ResourceLocation id = ResourceLocation.tryParse(req.dim());
        ServerLevel level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        RegionDataPacket pkt;
        if (level == null) {
            pkt = new RegionDataPacket(req.dim(), req.rx(), req.rz(), 0L, null);
        } else {
            ServerRegion r = region(level, req.rx(), req.rz(), false);
            if (r == null) pkt = new RegionDataPacket(req.dim(), req.rx(), req.rz(), 0L, null);
            else if (r.version <= req.knownVersion()) pkt = new RegionDataPacket(req.dim(), req.rx(), req.rz(), r.version, null);
            else pkt = new RegionDataPacket(req.dim(), req.rx(), req.rz(), r.version, r.compressed());
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pl), pkt);
    }

    // ------------------------------------------------------------------ disque

    private static Path file(DimData d, int rx, int rz) {
        return d.folder.resolve("r." + rx + "." + rz + ".mnm");
    }

    private static ServerRegion load(DimData d, int rx, int rz) {
        Path f = file(d, rx, rz);
        if (!Files.isRegularFile(f)) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(f)))) {
            if (in.readInt() != RegionCodec.MAGIC) return null;
            long version = in.readLong();
            byte[] bits = new byte[128];
            in.readFully(bits);
            int len = in.readInt();
            if (len <= 0 || len > 4 * RegionCodec.BYTES) return null;
            byte[] comp = new byte[len];
            in.readFully(comp);
            byte[] colors = RegionCodec.decompress(comp);
            if (colors == null) return null;
            return new ServerRegion(rx, rz, colors, BitSet.valueOf(bits), version);
        } catch (IOException e) {
            MineNorthMap.LOGGER.warn("Carte : lecture impossible {}", f, e);
            return null;
        }
    }

    public static void saveAll() {
        for (DimData d : DIMS.values()) {
            for (ServerRegion r : d.regions.values()) {
                if (!r.dirty) continue;
                Path f = file(d, r.rx, r.rz);
                Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
                try {
                    Files.createDirectories(d.folder);
                    byte[] comp = r.compressed();
                    byte[] bits = new byte[128];
                    byte[] src = r.rendered.toByteArray();
                    System.arraycopy(src, 0, bits, 0, Math.min(src.length, 128));
                    try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                        out.writeInt(RegionCodec.MAGIC);
                        out.writeLong(r.version);
                        out.write(bits);
                        out.writeInt(comp.length);
                        out.write(comp);
                    }
                    Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
                    r.dirty = false;
                } catch (IOException e) {
                    MineNorthMap.LOGGER.warn("Carte : écriture impossible {}", f, e);
                }
            }
        }
    }

    private static void unloadIdle() {
        long now = System.currentTimeMillis();
        for (DimData d : DIMS.values()) {
            d.regions.values().removeIf(r -> !r.dirty && now - r.lastAccess > 300_000L);
            d.absent.clear();
        }
    }
}
