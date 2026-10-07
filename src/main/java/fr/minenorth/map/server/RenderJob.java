package fr.minenorth.map.server;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * /carte generer : dessine une zone entière. Ne traite QUE les chunks déjà générés
 * (aucun nouveau terrain n'est créé) : vérification asynchrone sur disque, puis chargement + rendu.
 */
public final class RenderJob {
    private final ServerLevel level;
    private final CommandSourceStack source;
    private final int minCx, minCz, maxCx, maxCz;
    private int curX, curZ;
    private boolean iterationDone;
    private boolean cancelled;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();
    private final ConcurrentLinkedQueue<ChunkPos> ready = new ConcurrentLinkedQueue<>();
    private int done;
    private final long total;
    private final long startMs = System.currentTimeMillis();
    private long lastReport = startMs;

    public RenderJob(ServerLevel level, CommandSourceStack source, int x1, int z1, int x2, int z2) {
        this.level = level;
        this.source = source;
        this.minCx = Math.min(x1, x2) >> 4;
        this.minCz = Math.min(z1, z2) >> 4;
        this.maxCx = Math.max(x1, x2) >> 4;
        this.maxCz = Math.max(z1, z2) >> 4;
        this.curX = minCx;
        this.curZ = minCz;
        this.total = (long) (maxCx - minCx + 1) * (maxCz - minCz + 1);
    }

    public long total() { return total; }

    public String status() {
        long processed = done + skipped.get();
        int pct = total == 0 ? 100 : (int) (processed * 100 / total);
        return pct + "% (" + done + " chunks dessinés, " + skipped.get() + " vides/non générés, sur " + total + ")";
    }

    void cancel() {
        cancelled = true;
    }

    private static boolean isFull(Optional<CompoundTag> tag) {
        if (tag.isEmpty()) return false;
        String s = tag.get().getString("Status");
        return s.equals("minecraft:full") || s.equals("full");
    }

    /** @return true quand terminé. */
    boolean tick() {
        if (cancelled) return true;
        int budget = ServerMapConfig.GENERATE_PER_TICK.get();

        // vérifications d'existence (asynchrones)
        while (!iterationDone && inFlight.get() < budget * 8) {
            final ChunkPos pos = new ChunkPos(curX, curZ);
            if (++curX > maxCx) {
                curX = minCx;
                if (++curZ > maxCz) iterationDone = true;
            }
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) != null) {
                ready.add(pos);
                continue;
            }
            inFlight.incrementAndGet();
            level.getChunkSource().chunkMap.read(pos).whenComplete((tag, err) -> {
                if (err == null && tag != null && isFull(tag)) ready.add(pos);
                else skipped.incrementAndGet();
                inFlight.decrementAndGet();
            });
        }

        // rendu
        for (int i = 0; i < budget; i++) {
            ChunkPos pos = ready.poll();
            if (pos == null) break;
            LevelChunk c = level.getChunk(pos.x, pos.z); // charge le chunk (existant) si besoin
            ServerMapManager.renderChunk(level, c);
            done++;
        }

        long now = System.currentTimeMillis();
        boolean finished = iterationDone && inFlight.get() == 0 && ready.isEmpty();
        if (finished) {
            ServerMapManager.saveAll();
            long secs = (now - startMs) / 1000;
            source.sendSuccess(() -> Component.literal("Carte : génération terminée en " + secs + " s — " + status())
                    .withStyle(ChatFormatting.GREEN), true);
            return true;
        }
        if (now - lastReport > 15_000L) {
            lastReport = now;
            source.sendSuccess(() -> Component.literal("Carte : génération " + status()).withStyle(ChatFormatting.GRAY), false);
        }
        return false;
    }
}
