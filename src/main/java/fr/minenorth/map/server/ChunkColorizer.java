package fr.minenorth.map.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** Calcule les couleurs (façon carte vanilla) d'un chunk. Thread serveur uniquement. Aucune entité lue. */
final class ChunkColorizer {
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static int sY, sWater;
    private static MapColor sColor = MapColor.NONE;

    private ChunkColorizer() {}

    /** Dessine le chunk dans dst (tableau de la région). Retourne true si au moins un pixel a changé. */
    static boolean render(ServerLevel level, LevelChunk chunk, byte[] dst) {
        int cx = chunk.getPos().x, cz = chunk.getPos().z;
        boolean ceiling = level.dimensionType().hasCeiling();
        int minY = level.getMinBuildHeight();
        int topY = minY + level.dimensionType().logicalHeight() - 1;
        int bx = cx << 4, bz = cz << 4;

        int[] prev = null;
        LevelChunk north = level.getChunkSource().getChunkNow(cx, cz - 1);
        if (north != null) {
            prev = new int[16];
            for (int x = 0; x < 16; x++) {
                sample(level, north, bx + x, bz - 1, ceiling, topY, minY);
                prev[x] = sY;
            }
        }

        boolean changed = false;
        int[] cur = new int[16];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int wx = bx + x, wz = bz + z;
                boolean found = sample(level, chunk, wx, wz, ceiling, topY, minY);
                cur[x] = sY;
                byte packed = 0;
                if (found) {
                    MapColor.Brightness br;
                    int dither = (wx + wz) & 1;
                    if (sColor == MapColor.WATER) {
                        double d = sWater * 0.1 + dither * 0.2;
                        br = d < 0.5 ? MapColor.Brightness.HIGH : (d > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL);
                    } else {
                        int northY = prev != null ? prev[x] : sY;
                        double d = (sY - northY) * 0.8 + (dither - 0.5) * 0.4;
                        br = d > 0.6 ? MapColor.Brightness.HIGH : (d < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL);
                    }
                    packed = sColor.getPackedId(br);
                }
                int idx = ((wz & 511) << 9) | (wx & 511);
                if (dst[idx] != packed) {
                    dst[idx] = packed;
                    changed = true;
                }
            }
            if (prev == null) prev = new int[16];
            System.arraycopy(cur, 0, prev, 0, 16);
        }
        return changed;
    }

    private static boolean sample(ServerLevel level, LevelChunk chunk, int wx, int wz, boolean ceiling, int topY, int minY) {
        int y;
        sWater = 0;
        if (ceiling) {
            // Nether : on traverse le plafond puis l'air pour trouver le sol de la grotte la plus haute
            y = topY;
            POS.set(wx, y, wz);
            while (y > minY && !chunk.getBlockState(POS.setY(y)).isAir()) y--;
            while (y > minY && chunk.getBlockState(POS.setY(y)).isAir()) y--;
        } else {
            y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, wx & 15, wz & 15);
        }
        while (y >= minY) {
            POS.set(wx, y, wz);
            BlockState st = chunk.getBlockState(POS);
            MapColor mc = st.getMapColor(level, POS);
            if (mc != MapColor.NONE) {
                sY = y;
                sColor = mc;
                if (st.getFluidState().is(FluidTags.WATER)) {
                    int depth = 1, yy = y;
                    while (yy > minY && depth < 16) {
                        POS.setY(--yy);
                        if (chunk.getBlockState(POS).getFluidState().is(FluidTags.WATER)) depth++;
                        else break;
                    }
                    sWater = depth;
                }
                return true;
            }
            y--;
        }
        sY = minY;
        sColor = MapColor.NONE;
        return false;
    }
}
