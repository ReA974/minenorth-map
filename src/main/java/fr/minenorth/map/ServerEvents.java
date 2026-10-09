package fr.minenorth.map;

import fr.minenorth.map.command.MapCommand;
import fr.minenorth.map.network.NetworkHandler;
import fr.minenorth.map.server.PlayerTracker;
import fr.minenorth.map.server.ServerMapManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = MineNorthMap.MODID)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        MapCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            NetworkHandler.syncTo(sp);
            fr.minenorth.map.server.WaypointEditor.sendInfo(sp);
            PlayerTracker.onLogin(sp);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerMapManager.onLogout(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerMapManager.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ServerMapManager.onServerStopping();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ServerMapManager.tick();
        statusTick(event.getServer() != null ? event.getServer() : null);
    }

    private static int statusCountdown;
    private static int lastStatuses = Integer.MIN_VALUE;

    /** Toutes les 5 s : si l'état ouvert / fermé d'une entreprise liée a changé, les points sont renvoyés à tout le monde. */
    private static void statusTick(net.minecraft.server.MinecraftServer server) {
        if (server == null || ++statusCountdown < 100) return;
        statusCountdown = 0;
        if (!fr.minenorth.map.server.CompanyBridge.available()) return;
        int h = 1;
        for (fr.minenorth.map.waypoint.Waypoint w : fr.minenorth.map.waypoint.WaypointSavedData.get(server).forClients(server)) h = 31 * h + w.status();
        if (h != lastStatuses) {
            lastStatuses = h;
            NetworkHandler.syncAll(server);
        }
    }

    @SubscribeEvent
    public static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel sl && sl.dimension() == Level.OVERWORLD) {
            ServerMapManager.saveAll();
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel sl && event.getChunk() instanceof LevelChunk c) {
            ServerMapManager.onChunkLoaded(sl.dimension(), c.getPos().x, c.getPos().z);
        }
    }

    private static void dirty(LevelAccessor level, BlockPos pos) {
        if (level instanceof Level l && !l.isClientSide) {
            ServerMapManager.markDirty(l, pos.getX() >> 4, pos.getZ() >> 4);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        dirty(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        dirty(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Set<Long> seen = new HashSet<>();
        for (BlockPos p : event.getAffectedBlocks()) {
            long k = ((long) (p.getX() >> 4) << 32) ^ ((p.getZ() >> 4) & 0xFFFFFFFFL);
            if (seen.add(k)) dirty(event.getLevel(), p);
        }
    }
}
