package fr.minenorth.map.server;

import fr.minenorth.map.MineNorthMap;
import fr.minenorth.map.network.TeleportPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/** Téléportation d'un administrateur depuis la carte (Maj + clic droit). Le droit est revérifié ici : le client n'est jamais cru. */
public final class MapTeleport {
    private MapTeleport() {}

    private static void say(ServerPlayer p, String msg, boolean ok) {
        p.displayClientMessage(Component.literal(msg).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), true);
    }

    public static void handle(ServerPlayer p, TeleportPacket k) {
        if (!WaypointEditor.allowed(p)) {
            say(p, "Réservé aux administrateurs.", false);
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(k.dim());
        ServerLevel level = id == null ? null : p.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) {
            say(p, "Dimension introuvable.", false);
            return;
        }
        if (!level.getWorldBorder().isWithinBounds(k.x(), k.z())) {
            say(p, "Cet endroit est hors de la bordure du monde.", false);
            return;
        }
        level.getChunk(k.x() >> 4, k.z() >> 4);   // charge le chunk pour connaître la surface
        int y = k.hasY() ? Mth.clamp(k.y(), level.getMinBuildHeight() + 1, level.getMaxBuildHeight() - 2)
                : level.getHeight(Heightmap.Types.MOTION_BLOCKING, k.x(), k.z());
        if (p.level() == level) p.teleportTo(k.x() + 0.5, y, k.z() + 0.5);
        else p.teleportTo(level, k.x() + 0.5, y, k.z() + 0.5, p.getYRot(), p.getXRot());
        say(p, "Téléporté en " + k.x() + ", " + y + ", " + k.z() + ".", true);
        MineNorthMap.LOGGER.info("[Carte] {} s'est téléporté en {} {} {} ({})", p.getGameProfile().getName(), k.x(), y, k.z(), k.dim());
    }
}
