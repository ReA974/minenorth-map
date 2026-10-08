package fr.minenorth.map.network;

import fr.minenorth.map.MineNorthMap;
import fr.minenorth.map.waypoint.WaypointSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class NetworkHandler {
    private static final String PROTOCOL = "4";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MineNorthMap.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private NetworkHandler() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SyncWaypointsPacket.class,
                SyncWaypointsPacket::encode, SyncWaypointsPacket::decode, SyncWaypointsPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, RequestRegionPacket.class,
                RequestRegionPacket::encode, RequestRegionPacket::decode, RequestRegionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, RegionDataPacket.class,
                RegionDataPacket::encode, RegionDataPacket::decode, RegionDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, MapPermPacket.class,
                MapPermPacket::encode, MapPermPacket::decode, MapPermPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, TogglePlayersPacket.class,
                TogglePlayersPacket::encode, TogglePlayersPacket::decode, TogglePlayersPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, PlayerPositionsPacket.class,
                PlayerPositionsPacket::encode, PlayerPositionsPacket::decode, PlayerPositionsPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, MarkerPacket.class,
                MarkerPacket::encode, MarkerPacket::decode, MarkerPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void syncTo(ServerPlayer player) {
        SyncWaypointsPacket pkt = new SyncWaypointsPacket(WaypointSavedData.get(player.server).snapshot());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
    }

    public static void syncAll(MinecraftServer server) {
        SyncWaypointsPacket pkt = new SyncWaypointsPacket(WaypointSavedData.get(server).snapshot());
        CHANNEL.send(PacketDistributor.ALL.noArg(), pkt);
    }
}
