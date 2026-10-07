package fr.minenorth.map.client;

import fr.minenorth.map.MineNorthMap;
import fr.minenorth.map.waypoint.Waypoint;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MineNorthMap.MODID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {}

    private static final double[] ZOOMS = {0.25, 0.5, 1.0, 2.0, 4.0};

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        MapStorage.tick(mc);

        while (KeyBindings.OPEN_MAP.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new MapScreen());
        }
        while (KeyBindings.TOGGLE_MINIMAP.consumeClick()) {
            boolean v = !ClientConfig.MINIMAP_ENABLED.get();
            ClientConfig.MINIMAP_ENABLED.set(v);
            ClientConfig.MINIMAP_ENABLED.save();
        }
        while (KeyBindings.ZOOM_IN.consumeClick()) stepZoom(1);
        while (KeyBindings.ZOOM_OUT.consumeClick()) stepZoom(-1);

        TempWaypoints.tick();

        // arrivée au point suivi -> fin du guidage (et suppression si c'est un repère perso)
        Waypoint t = ClientWaypoints.getTracked();
        if (t != null && t.dimension().equals(mc.level.dimension().location().toString())) {
            double dx = t.x() + 0.5 - player.getX(), dz = t.z() + 0.5 - player.getZ();
            if (dx * dx + dz * dz < 16) {
                ClientWaypoints.track(null);
                if (TempWaypoints.isTemp(t)) TempWaypoints.remove(t);
                player.displayClientMessage(Component.literal("Vous êtes arrivé : " + t.name())
                        .withStyle(ChatFormatting.GREEN), true);
            }
        }
    }

    private static void stepZoom(int dir) {
        double cur = ClientConfig.MINIMAP_ZOOM.get();
        int idx = 0;
        for (int i = 0; i < ZOOMS.length; i++) if (Math.abs(ZOOMS[i] - cur) < Math.abs(ZOOMS[idx] - cur)) idx = i;
        idx = Math.max(0, Math.min(ZOOMS.length - 1, idx + dir));
        ClientConfig.MINIMAP_ZOOM.set(ZOOMS[idx]);
        ClientConfig.MINIMAP_ZOOM.save();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        MapStorage.onLogout();
        ClientWaypoints.clear();
        ClientPlayersView.reset();
        TempWaypoints.clear();
    }
}
