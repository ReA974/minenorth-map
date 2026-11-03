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
    /** Taille écran des icônes de la minicarte : constante, quel que soit le zoom. */
    private static final int ICON_SIZE = 11;

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
        MapRenderer.drawRoute(g, (wx, wz) -> new float[]{cx + (float) ((wx - px) * zoom), cy + (float) ((wz - pz) * zoom)}, 3f);
        boolean hide = ClientConfig.HIDE_WAYPOINTS.get();
        java.util.List<Waypoint> shown = new java.util.ArrayList<>();
        for (Waypoint w : ClientWaypoints.inDimension(dim)) if (ClientWaypoints.isTracked(w) || (!hide && ClientWaypoints.passesFilter(w))) shown.add(w);
        shown.addAll(TempWaypoints.inDimension(dim));
        shown.addAll(MesPoints.inDimension(dim));
        java.util.List<Waypoint> iconPts = new java.util.ArrayList<>();
        java.util.List<int[]> iconPos = new java.util.ArrayList<>();
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
            if (!w.icon().isEmpty()) {
                // les icônes sont dessinées après, regroupées si elles se chevauchent (le point guidé passe en premier)
                int at = tracked ? 0 : iconPts.size();
                iconPts.add(at, w);
                iconPos.add(at, new int[]{Math.round(wx), Math.round(wy)});
                continue;
            }
            MapRenderer.drawMarker(g, Math.round(wx), Math.round(wy), 2, w.color(), tracked);
            MapRenderer.drawStatusDot(g, Math.round(wx), Math.round(wy), 2, w.status());
        }
        for (MapIconRenderer.Placed p : MapIconRenderer.cluster(iconPts, iconPos, ICON_SIZE - 1)) {
            MapIconRenderer.draw(g, p.rep().icon(), p.x(), p.y(), ICON_SIZE, ClientWaypoints.isTracked(p.rep()));
            MapRenderer.drawStatusDot(g, p.x(), p.y(), ICON_SIZE / 2 - 1, p.rep().status());
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
            // itinéraire GPS : flèche vers la suite du trajet et distance par la route ; sinon ligne droite
            boolean gps = GpsRoute.active();
            double dx = (gps ? GpsRoute.aheadX() : t.x() + 0.5) - px, dz = (gps ? GpsRoute.aheadZ() : t.z() + 0.5) - pz;
            double dist = gps ? GpsRoute.remaining() : Math.sqrt(dx * dx + dz * dz);
            float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float rel = Mth.wrapDegrees(targetYaw - yaw) * Mth.DEG_TO_RAD;
            dir = new float[]{Mth.sin(rel), -Mth.cos(rel)};
            text = t.name() + " • " + (dist >= 1000 ? String.format(java.util.Locale.FRANCE, "%.1f km", dist / 1000) : Math.round(dist) + " m")
                    + (gps ? " par la route" : "");
        }
        int tw = font.width(text);
        int boxW = tw + (dir != null ? 22 : 10);
        int bx = sw / 2 - boxW / 2, by = 4;
        g.fill(bx, by, bx + boxW, by + 16, 0x90000000);
        g.fill(bx, by + 15, bx + boxW, by + 16, 0xFF000000 | (GpsRoute.active() ? GpsRoute.COLOR : t.color()));
        int textX = bx + 5;
        if (dir != null) {
            MapRenderer.drawArrow(g, bx + 9, by + 8, dir[0], dir[1], 5.5f, 0xFF000000);
            MapRenderer.drawArrow(g, bx + 9, by + 8, dir[0], dir[1], 4.5f, 0xFF000000 | t.color());
            textX = bx + 17;
        }
        g.drawString(font, text, textX, by + 4, 0xFFFFFFFF, true);
    }
}
