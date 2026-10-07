package fr.minenorth.map.client;

import fr.minenorth.map.MineNorthMap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MineNorthMap.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_MAP);
        event.register(KeyBindings.TOGGLE_MINIMAP);
        event.register(KeyBindings.ZOOM_IN);
        event.register(KeyBindings.ZOOM_OUT);
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("minimap", MinimapOverlay::render);
    }
}
