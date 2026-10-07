package fr.minenorth.map.client;

import fr.minenorth.map.network.NetworkHandler;
import fr.minenorth.map.network.PlayerPositionsPacket;
import fr.minenorth.map.network.TogglePlayersPacket;
import fr.minenorth.map.waypoint.Waypoint;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

/**
 * Carte plein écran : glisser pour se déplacer, molette pour zoomer,
 * liste des points de repère à gauche, clic sur un point = guidage.
 * Seuls le terrain, les points de repère et TA position sont affichés.
 */
public class MapScreen extends Screen {
    private static final int PANEL_W = 140;
    private static final int ENTRY_H = 14;
    private static final int LIST_TOP = 26;
    private static final float MIN_ZOOM = 0.125f, MAX_ZOOM = 8f;
    private static float lastZoom = 1f;
    /** Choix du staff mémorisé entre deux ouvertures de la carte. */
    private static boolean wantPlayers;
    private Button playersBtn;
    private Button pointsBtn;

    private double centerX, centerZ;
    private float zoom = lastZoom;
    private boolean dragging;
    private int listScroll;
    private List<Waypoint> list = List.of();

    public MapScreen() {
        super(Component.translatable("screen.minenorth_map.title"));
    }

    @Override
    protected void init() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && centerX == 0 && centerZ == 0) {
            centerX = p.getX();
            centerZ = p.getZ();
        }
        pointsBtn = addRenderableWidget(Button.builder(Component.literal(""), b -> {
            boolean v = !ClientConfig.HIDE_WAYPOINTS.get();
            ClientConfig.HIDE_WAYPOINTS.set(v);
            ClientConfig.HIDE_WAYPOINTS.save();
            updatePointsButton();
        }).bounds(width - 116, 17, 110, 16).build());
        updatePointsButton();
        playersBtn = addRenderableWidget(Button.builder(Component.literal(""), b -> setPlayers(!ClientPlayersView.enabled()))
                .bounds(width - 116, 35, 110, 16).build());
        if (ClientPlayersView.allowed() && wantPlayers && !ClientPlayersView.enabled()) setPlayers(true);
        updatePlayersButton();
    }

    private void setPlayers(boolean on) {
        if (on && !ClientPlayersView.allowed()) return;
        wantPlayers = on;
        ClientPlayersView.setEnabled(on);
        NetworkHandler.CHANNEL.sendToServer(new TogglePlayersPacket(on));
        updatePlayersButton();
    }

    private void updatePointsButton() {
        if (pointsBtn == null) return;
        pointsBtn.setMessage(Component.literal(ClientConfig.HIDE_WAYPOINTS.get() ? "Points : cachés" : "Points : affichés"));
    }

    private void updatePlayersButton() {
        if (playersBtn == null) return;
        playersBtn.visible = ClientPlayersView.allowed();
        boolean on = ClientPlayersView.enabled();
        int n = Math.max(0, ClientPlayersView.players().size() - 1);
        playersBtn.setMessage(Component.literal(on ? "§bJoueurs : ON §7(" + n + ")" : "Joueurs : OFF"));
    }

    @Override
    public void removed() {
        // on coupe le flux de positions quand la carte se ferme (le choix reste mémorisé)
        if (ClientPlayersView.enabled()) {
            ClientPlayersView.setEnabled(false);
            NetworkHandler.CHANNEL.sendToServer(new TogglePlayersPacket(false));
        }
        super.removed();
    }

    private String dim() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? "" : mc.level.dimension().location().toString();
    }

    private float mapCX() { return (PANEL_W + width) / 2f; }
    private float mapCY() { return height / 2f; }
    private float toScreenX(double wx) { return mapCX() + (float) ((wx - centerX) * zoom); }
    private float toScreenY(double wz) { return mapCY() + (float) ((wz - centerZ) * zoom); }
    private double toWorldX(double sx) { return centerX + (sx - mapCX()) / zoom; }
    private double toWorldZ(double sy) { return centerZ + (sy - mapCY()) / zoom; }

    private int visibleEntries() { return Math.max(1, (height - LIST_TOP - 6) / ENTRY_H); }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        String dim = dim();
        list = ClientWaypoints.inDimension(dim);
        list.sort(Comparator.comparing(w -> w.name().toLowerCase()));
        list.addAll(TempWaypoints.inDimension(dim)); // repères perso en fin de liste
        boolean hide = ClientConfig.HIDE_WAYPOINTS.get();

        // ---- zone carte
        g.fill(0, 0, width, height, 0xFF0E0E12);
        g.enableScissor(PANEL_W, 0, width, height);
        MapRenderer.drawTerrain(g, centerX, centerZ, zoom, PANEL_W, 0, width, height);

        Waypoint hovered = null;
        for (Waypoint w : list) {
            int sx = Math.round(toScreenX(w.x() + 0.5)), sy = Math.round(toScreenY(w.z() + 0.5));
            if (sx < PANEL_W - 10 || sx > width + 10 || sy < -10 || sy > height + 10) continue;
            boolean tracked = ClientWaypoints.isTracked(w);
            boolean temp = TempWaypoints.isTemp(w);
            if (hide && !temp && !tracked) continue;
            MapRenderer.drawMarker(g, sx, sy, temp ? 2 : 3, w.color(), tracked);
            int tw = font.width(w.name());
            g.fill(sx - tw / 2 - 2, sy - 17, sx + tw / 2 + 2, sy - 6, 0x90000000);
            g.drawString(font, w.name(), sx - tw / 2, sy - 15, 0xFF000000 | w.color(), true);
            if (mouseX > PANEL_W && Math.abs(mouseX - sx) <= 5 && Math.abs(mouseY - sy) <= 5) hovered = w;
        }

        // joueurs (staff uniquement, envoyé par le serveur si autorisé)
        PlayerPositionsPacket.Entry hoveredPlayer = null;
        if (ClientPlayersView.enabled()) {
            for (PlayerPositionsPacket.Entry e : ClientPlayersView.players()) {
                if (!e.dim().equals(dim) || (player != null && e.id().equals(player.getUUID()))) continue;
                float sx = toScreenX(e.x()), sy = toScreenY(e.z());
                if (sx < PANEL_W - 10 || sx > width + 10 || sy < -10 || sy > height + 10) continue;
                float[] f = MapRenderer.facing(e.yaw());
                MapRenderer.drawArrow(g, sx, sy, f[0], f[1], 5.6f, 0xFF000000);
                MapRenderer.drawArrow(g, sx, sy, f[0], f[1], 4.2f, 0xFF4FC3F7);
                int tw = font.width(e.name());
                int ix = Math.round(sx), iy = Math.round(sy);
                g.fill(ix - tw / 2 - 2, iy + 7, ix + tw / 2 + 2, iy + 18, 0x90000000);
                g.drawString(font, e.name(), ix - tw / 2, iy + 9, 0xFF4FC3F7, true);
                if (mouseX > PANEL_W && Math.abs(mouseX - sx) <= 5 && Math.abs(mouseY - sy) <= 5) hoveredPlayer = e;
            }
        }

        if (player != null) {
            float ppx = toScreenX(Mth.lerp(pt, player.xo, player.getX()));
            float ppy = toScreenY(Mth.lerp(pt, player.zo, player.getZ()));
            float[] f = MapRenderer.facing(player.getViewYRot(pt));
            MapRenderer.drawPlayerArrow(g, ppx, ppy, f[0], f[1], 6f);
        }
        g.disableScissor();

        // ---- bandeau haut : coordonnées sous le curseur
        g.fill(PANEL_W, 0, width, 14, 0x90000000);
        String info;
        if (mouseX > PANEL_W) {
            info = "X " + Mth.floor(toWorldX(mouseX)) + "   Z " + Mth.floor(toWorldZ(mouseY));
        } else {
            info = "";
        }
        g.drawString(font, info, PANEL_W + 6, 3, 0xFFFFFFFF, false);
        String z = "Zoom x" + (zoom >= 1 ? String.valueOf((int) zoom) : String.format("%.2f", zoom));
        g.drawString(font, z, width - font.width(z) - 6, 3, 0xFFBBBBBB, false);
        g.drawCenteredString(font, "N", Math.round(mapCX()), 3, 0xFFFF5555);

        // ---- aide bas
        String help = "Glisser : déplacer • Molette : zoom • Clic sur un point : guidage • Clic droit : repère perso • Espace : recentrer";
        g.fill(PANEL_W, height - 13, width, height, 0x90000000);
        g.drawCenteredString(font, help, Math.round(mapCX()), height - 11, 0xFFAAAAAA);

        // ---- panneau liste
        g.fill(0, 0, PANEL_W, height, 0xF0141418);
        g.fill(PANEL_W - 1, 0, PANEL_W, height, 0xFF8B7355);
        g.drawCenteredString(font, title, PANEL_W / 2, 6, 0xFFFFD27A);
        g.drawString(font, "Points de repère", 6, 16, 0xFF888888, false);

        int max = visibleEntries();
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, list.size() - max));
        if (list.isEmpty()) {
            g.drawString(font, "Aucun point", 8, LIST_TOP + 3, 0xFF666666, false);
        }
        for (int i = 0; i < max && i + listScroll < list.size(); i++) {
            Waypoint w = list.get(i + listScroll);
            int y = LIST_TOP + i * ENTRY_H;
            boolean hov = mouseX >= 0 && mouseX < PANEL_W - 1 && mouseY >= y && mouseY < y + ENTRY_H;
            boolean tracked = ClientWaypoints.isTracked(w);
            if (tracked) g.fill(2, y, PANEL_W - 3, y + ENTRY_H, 0x60FFD27A);
            else if (hov) g.fill(2, y, PANEL_W - 3, y + ENTRY_H, 0x40FFFFFF);
            g.fill(6, y + 4, 12, y + 10, 0xFF000000 | w.color());
            String dist = "";
            if (player != null) {
                double dx = w.x() + 0.5 - player.getX(), dz = w.z() + 0.5 - player.getZ();
                dist = (int) Math.sqrt(dx * dx + dz * dz) + "m";
            }
            int dw = font.width(dist);
            String name = font.plainSubstrByWidth(w.name(), PANEL_W - 24 - dw - 6);
            boolean dim2 = hide && !TempWaypoints.isTemp(w) && !tracked;
            g.drawString(font, name, 16, y + 3, dim2 ? 0xFF777777 : 0xFFFFFFFF, false);
            g.drawString(font, dist, PANEL_W - 6 - dw, y + 3, 0xFF888888, false);
        }

        // ---- boutons puis infobulle
        updatePlayersButton();
        super.render(g, mouseX, mouseY, pt);
        if (hoveredPlayer != null) {
            g.renderTooltip(font, Component.literal(hoveredPlayer.name() + "  (" + Mth.floor(hoveredPlayer.x()) + ", "
                    + Mth.floor(hoveredPlayer.y()) + ", " + Mth.floor(hoveredPlayer.z()) + ")"), mouseX, mouseY);
        } else if (hovered != null) {
            Component tip = Component.literal(hovered.name() + "  (" + hovered.x() + ", " + hovered.y() + ", " + hovered.z() + ")");
            g.renderTooltip(font, tip, mouseX, mouseY);
        }

    }

    private Waypoint waypointAt(double mx, double my) {
        boolean hide = ClientConfig.HIDE_WAYPOINTS.get();
        for (Waypoint w : list) {
            if (hide && !TempWaypoints.isTemp(w) && !ClientWaypoints.isTracked(w)) continue;
            float sx = toScreenX(w.x() + 0.5), sy = toScreenY(w.z() + 0.5);
            if (Math.abs(mx - sx) <= 5 && Math.abs(my - sy) <= 5) return w;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx < PANEL_W) {
            int idx = (int) ((my - LIST_TOP) / ENTRY_H);
            if (my >= LIST_TOP && idx >= 0 && idx < visibleEntries() && idx + listScroll < list.size()) {
                Waypoint w = list.get(idx + listScroll);
                if (button == 1 && TempWaypoints.isTemp(w)) {
                    TempWaypoints.remove(w);
                    return true;
                }
                centerX = w.x() + 0.5;
                centerZ = w.z() + 0.5;
                if (button == 0 || button == 1) ClientWaypoints.toggleTrack(w);
                return true;
            }
            return true;
        }
        if (playersBtn != null && playersBtn.visible && playersBtn.isMouseOver(mx, my)) {
            return super.mouseClicked(mx, my, button);
        }
        if (pointsBtn != null && pointsBtn.isMouseOver(mx, my)) {
            return super.mouseClicked(mx, my, button);
        }
        if (button == 0) {
            Waypoint w = waypointAt(mx, my);
            if (w != null) {
                ClientWaypoints.toggleTrack(w);
                return true;
            }
            dragging = true;
            return true;
        }
        if (button == 1) {
            // clic droit : poser / retirer un repère perso temporaire
            Waypoint w = waypointAt(mx, my);
            Minecraft mc = Minecraft.getInstance();
            if (w != null && TempWaypoints.isTemp(w)) {
                TempWaypoints.remove(w);
            } else if (w == null && mc.player != null) {
                int x = Mth.floor(toWorldX(mx)), z = Mth.floor(toWorldZ(my));
                Waypoint nw = TempWaypoints.add(dim(), x, mc.player.getBlockY(), z);
                ClientWaypoints.track(nw);
                mc.player.displayClientMessage(Component.literal("Repère perso posé en " + x + ", " + z
                        + " (clic droit dessus pour le retirer)").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0) {
            centerX -= dx / zoom;
            centerZ -= dy / zoom;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < PANEL_W) {
            listScroll -= (int) Math.signum(delta);
            return true;
        }
        double wx = toWorldX(mx), wz = toWorldZ(my);
        float nz = Mth.clamp(zoom * (delta > 0 ? 1.25f : 0.8f), MIN_ZOOM, MAX_ZOOM);
        if (Math.abs(nz - 1f) < 0.05f) nz = 1f; // retombe pile sur x1
        zoom = nz;
        lastZoom = nz;
        centerX = wx - (mx - mapCX()) / zoom;
        centerZ = wz - (my - mapCY()) / zoom;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (KeyBindings.OPEN_MAP.matches(key, scan)) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE) {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null) {
                centerX = p.getX();
                centerZ = p.getZ();
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
