package fr.minenorth.map.client;

import fr.minenorth.map.waypoint.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Minicarte HUD + bandeau de guidage vers le point suivi. Aucun joueur n'est affiché. */
public final class MinimapOverlay {
    private MinimapOverlay() {}

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) return;
        if (mc.screen instanceof MapScreen) return;

        double px = Mth.lerp(pt, player.xo, player.getX());
        double pz = Mth.lerp(pt, player.zo, player.getZ());
        float yaw = player.getViewYRot(pt);
        String dim = mc.level.dimension().location().toString();

        if (ClientConfig.SHOW_GUIDE.get()) renderGuide(g, mc.font, sw, px, pz, yaw, dim);

        if (!ClientConfig.MINIMAP_ENABLED.get() || mc.options.renderDebug) return;

        int size = ClientConfig.MINIMAP_SIZE.get();
        float zoom = ClientConfig.MINIMAP_ZOOM.get().floatValue();
        boolean coords = ClientConfig.SHOW_COORDS.get();
        int m = 6;
        ClientConfig.Position pos = ClientConfig.MINIMAP_POSITION.get();
        boolean right = pos == ClientConfig.Position.HAUT_DROITE || pos == ClientConfig.Position.BAS_DROITE;
        boolean top = pos == ClientConfig.Position.HAUT_DROITE || pos == ClientConfig.Position.HAUT_GAUCHE;
        int x0 = right ? sw - size - m : m;
        int y0 = top ? m : sh - size - m - (coords ? 12 : 0);
        int x1 = x0 + size, y1 = y0 + size;
        float cx = (x0 + x1) / 2f, cy = (y0 + y1) / 2f;

        // cadre
        g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0xFF000000);
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF8B7355);
        g.fill(x0, y0, x1, y1, 0xFF1B1B1F);

        g.enableScissor(x0, y0, x1, y1);
        MapRenderer.drawTerrain(g, px, pz, zoom, x0, y0, x1, y1);
        boolean hide = ClientConfig.HIDE_WAYPOINTS.get();
        java.util.List<Waypoint> shown = new java.util.ArrayList<>();
        for (Waypoint w : ClientWaypoints.inDimension(dim)) if (ClientWaypoints.isTracked(w) || (!hide && ClientWaypoints.passesFilter(w))) shown.add(w);
        shown.addAll(TempWaypoints.inDimension(dim));
        for (Waypoint w : shown) {
            boolean tracked = ClientWaypoints.isTracked(w);
            float wx = cx + (float) ((w.x() + 0.5 - px) * zoom);
            float wy = cy + (float) ((w.z() + 0.5 - pz) * zoom);
            boolean inside = wx >= x0 + 2 && wx <= x1 - 3 && wy >= y0 + 2 && wy <= y1 - 3;
            if (!inside) {
                if (!tracked) continue;
                wx = Mth.clamp(wx, x0 + 4, x1 - 5);
                wy = Mth.clamp(wy, y0 + 4, y1 - 5);
            }
            MapRenderer.drawMarker(g, Math.round(wx), Math.round(wy), 2, w.color(), tracked);
            MapRenderer.drawStatusDot(g, Math.round(wx), Math.round(wy), 2, w.status());
        }
        g.disableScissor();

        float[] f = MapRenderer.facing(yaw);
        MapRenderer.drawPlayerArrow(g, cx, cy, f[0], f[1], 4.5f);

        g.drawCenteredString(mc.font, "N", Math.round(cx), y0 + 2, 0xFFFF5555);
        if (coords) {
            String s = player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ();
            g.drawCenteredString(mc.font, s, Math.round(cx), y1 + 4, 0xFFFFFFFF);
        }
    }

    private static void renderGuide(GuiGraphics g, Font font, int sw, double px, double pz, float yaw, String dim) {
        Waypoint t = ClientWaypoints.getTracked();
        if (t == null) return;
        String text;
        float[] dir = null;
        if (!t.dimension().equals(dim)) {
            text = t.name() + " • autre dimension";
        } else {
            double dx = t.x() + 0.5 - px, dz = t.z() + 0.5 - pz;
            int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float rel = Mth.wrapDegrees(targetYaw - yaw) * Mth.DEG_TO_RAD;
            dir = new float[]{Mth.sin(rel), -Mth.cos(rel)};
            text = t.name() + " • " + dist + " m";
        }
        int tw = font.width(text);
        int boxW = tw + (dir != null ? 22 : 10);
        int bx = sw / 2 - boxW / 2, by = 4;
        g.fill(bx, by, bx + boxW, by + 16, 0x90000000);
        g.fill(bx, by + 15, bx + boxW, by + 16, 0xFF000000 | t.color());
        int textX = bx + 5;
        if (dir != null) {
            MapRenderer.drawArrow(g, bx + 9, by + 8, dir[0], dir[1], 5.5f, 0xFF000000);
            MapRenderer.drawArrow(g, bx + 9, by + 8, dir[0], dir[1], 4.5f, 0xFF000000 | t.color());
            textX = bx + 17;
        }
        g.drawString(font, text, textX, by + 4, 0xFFFFFFFF, true);
    }
}
