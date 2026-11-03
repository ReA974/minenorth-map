package fr.minenorth.map.client;

import fr.minenorth.map.MineNorthMap;
import fr.minenorth.map.waypoint.Waypoint;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
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
        GpsRoute.tick(mc);
    }

    /** Flèches violettes au sol le long de l'itinéraire GPS, devant le joueur (une vague lumineuse les parcourt vers l'avant). */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        float[] gr = GpsRoute.ground();
        if (gr.length == 0 || !GpsRoute.active()) return;
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f m = event.getPoseStack().last().pose();
        int count = gr.length / 5;
        float r = (GpsRoute.COLOR >> 16 & 255) / 255f, gg = (GpsRoute.COLOR >> 8 & 255) / 255f, b = (GpsRoute.COLOR & 255) / 255f;
        double phase = (System.currentTimeMillis() % 1500L) / 1500.0 * Math.PI * 2;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < count; i++) {
            float x = (float) (gr[i * 5] - cam.x), y = (float) (gr[i * 5 + 1] - cam.y), z = (float) (gr[i * 5 + 2] - cam.z);
            float dx = gr[i * 5 + 3], dz = gr[i * 5 + 4], px = -dz, pz = dx;
            float wave = (float) (0.5 + 0.5 * Math.sin(i * 0.6 - phase));
            float a = (0.45f + 0.4f * wave) * (1f - 0.75f * i / count);
            float k = 0.75f + 0.25f * wave;
            float tipX = x + dx * 0.45f, tipZ = z + dz * 0.45f;
            float t = 0.3f;
            for (int side = -1; side <= 1; side += 2) {
                float wX = x - dx * 0.25f + px * 0.5f * side, wZ = z - dz * 0.25f + pz * 0.5f * side;
                bb.vertex(m, tipX, y, tipZ).color(r * k, gg * k, b * k, a).endVertex();
                bb.vertex(m, wX, y, wZ).color(r * k, gg * k, b * k, a).endVertex();
                bb.vertex(m, wX - dx * t, y, wZ - dz * t).color(r * k, gg * k, b * k, a).endVertex();
                bb.vertex(m, tipX - dx * t, y, tipZ - dz * t).color(r * k, gg * k, b * k, a).endVertex();
            }
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
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
        ClientEditor.reset();
        ClientPlayersView.reset();
        TempWaypoints.clear();
        MesPoints.clear();
        GpsRoute.clear();
    }
}
