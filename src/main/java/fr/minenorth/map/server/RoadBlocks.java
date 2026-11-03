package fr.minenorth.map.server;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Blocs considérés comme route pour le GPS (liste de la config serveur). Thread serveur uniquement. */
public final class RoadBlocks {
    private static final Map<Block, Boolean> CACHE = new IdentityHashMap<>();
    private static List<? extends String> cachedFor;

    private RoadBlocks() {}

    static boolean isRoad(BlockState st) {
        List<? extends String> list;
        try {
            list = ServerMapConfig.ROAD_BLOCKS.get();
        } catch (IllegalStateException e) {   // config pas encore chargée
            list = ServerMapConfig.DEFAULT_ROADS;
        }
        if (list != cachedFor) {
            CACHE.clear();
            cachedFor = list;
        }
        return CACHE.computeIfAbsent(st.getBlock(), b -> matches(b, cachedFor));
    }

    private static boolean matches(Block b, List<? extends String> list) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
        if (id == null) return false;
        String full = id.toString(), path = id.getPath();
        for (String e : list) {
            String s = e.trim().toLowerCase(Locale.ROOT);
            if (s.isEmpty()) continue;
            if (s.contains(":") ? full.equals(s) : path.contains(s)) return true;
        }
        return false;
    }
}
