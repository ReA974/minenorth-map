package fr.minenorth.map.client;

import fr.minenorth.map.network.NetworkHandler;
import fr.minenorth.map.network.PlayerPositionsPacket;
import fr.minenorth.map.network.TeleportPacket;
import fr.minenorth.map.network.TogglePlayersPacket;
import fr.minenorth.map.waypoint.Waypoint;
import fr.minenorth.map.waypoint.WaypointType;
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
    private Button filterBtn;
    private Button editBtn;
    /** Mode édition (OP) : clic sur un point = le modifier, clic droit = nouveau point. */
    private static boolean editMode;
    private boolean showFilters;
    private static final int FILTER_ROW_H = 12;

    private double centerX, centerZ;
    private float zoom = lastZoom;
    private boolean dragging;
    private int listScroll;
    private List<Waypoint> list = List.of();
    /** Points réellement dessinés au dernier affichage (icônes regroupées comprises) : sert au survol et au clic. */
    private final List<MapIconRenderer.Placed> placed = new java.util.ArrayList<>();
    private static final int ICON_SIZE = 16;

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
        filterBtn = addRenderableWidget(Button.builder(Component.literal("Filtres ▾"), b -> {
            showFilters = !showFilters;
            updateFilterButton();
        }).bounds(width - 116, 53, 110, 16).build());
        updateFilterButton();
        editBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            editMode = !editMode;
            updateEditButton();
        }).bounds(width - 116, 71, 110, 16).build());
        updateEditButton();
        NetworkHandler.CHANNEL.sendToServer(new fr.minenorth.map.network.WaypointEditPacket(
                fr.minenorth.map.network.WaypointEditPacket.REFRESH, "", "", "", -1, "", 0, 0, 0, ""));
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

    private void updateEditButton() {
        if (editBtn == null) return;
        editBtn.visible = ClientEditor.canEdit();
        if (!ClientEditor.canEdit()) editMode = false;
        editBtn.setMessage(Component.literal(editMode ? "§6Édition : ON" : "Édition : OFF"));
    }

    private boolean editing() { return editMode && ClientEditor.canEdit(); }

    private void openEditor(Waypoint w, double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        if (w != null) {
            mc.setScreen(new WaypointEditScreen(this, w));
        } else if (mc.player != null) {
            mc.setScreen(new WaypointEditScreen(this, dim(), Mth.floor(toWorldX(mx)), mc.player.getBlockY(), Mth.floor(toWorldZ(my))));
        }
    }

    private void updateFilterButton() {
        if (filterBtn == null) return;
        int hidden = 0;
        for (WaypointType t : ClientWaypoints.types()) if (ClientWaypoints.isTypeHidden(t.id())) hidden++;
        for (String id : fr.minenorth.map.waypoint.MapIcons.all().keySet()) if (ClientWaypoints.isIconHidden(id)) hidden++;
        boolean active = hidden > 0 || ClientConfig.ONLY_OPEN.get();
        filterBtn.setMessage(Component.literal((active ? "§eFiltres" : "Filtres") + (showFilters ? " ▴" : " ▾")));
    }

    /** Case du panneau de filtres. kind : TYPE / ICON (id), ONLY_OPEN, TYPES_ALL / TYPES_NONE, ICONS_ALL / ICONS_NONE, HEADER. */
    private record Cell(int x, int y, int w, String kind, String id, String label, int color, boolean on) {}

    private static final int TYPE_COL_W = 150, ICON_COL_W = 112, ICON_ROWS = 12;

    private int filterW() { return TYPE_COL_W + 2 * ICON_COL_W; }

    private List<Cell> filterCells() {
        List<Cell> out = new java.util.ArrayList<>();
        int x0 = filterX(), y0 = filterY() + 3;
        int r = 0;
        out.add(new Cell(x0, y0, TYPE_COL_W, "HEADER", "", "§6Types", 0, true));
        r++;
        for (WaypointType t : ClientWaypoints.types()) {
            out.add(new Cell(x0, y0 + r * FILTER_ROW_H, TYPE_COL_W, "TYPE", t.id(), t.label(), t.color(), !ClientWaypoints.isTypeHidden(t.id())));
            r++;
        }
        out.add(new Cell(x0, y0 + r * FILTER_ROW_H, TYPE_COL_W, "ONLY_OPEN", "", "Seulement ouvertes", 0x43D854, ClientConfig.ONLY_OPEN.get()));
        r++;
        out.add(new Cell(x0, y0 + r * FILTER_ROW_H, TYPE_COL_W / 2, "TYPES_ALL", "", "§nTout", 0, true));
        out.add(new Cell(x0 + TYPE_COL_W / 2, y0 + r * FILTER_ROW_H, TYPE_COL_W / 2, "TYPES_NONE", "", "§nAucun", 0, true));
        // catégories d'icônes : deux colonnes
        int ix = x0 + TYPE_COL_W;
        out.add(new Cell(ix, y0, ICON_COL_W * 2, "HEADER", "", "§6Catégories d'icônes", 0, true));
        int n = 0;
        for (fr.minenorth.map.waypoint.MapIcons.Icon ic : fr.minenorth.map.waypoint.MapIcons.all().values()) {
            int col = n / ICON_ROWS, row = n % ICON_ROWS;
            out.add(new Cell(ix + col * ICON_COL_W, y0 + (row + 1) * FILTER_ROW_H, ICON_COL_W, "ICON", ic.id(), ic.label(), ic.color(), !ClientWaypoints.isIconHidden(ic.id())));
            n++;
        }
        int lastRow = ICON_ROWS + 1;
        out.add(new Cell(ix + ICON_COL_W, y0 + lastRow * FILTER_ROW_H, ICON_COL_W / 2, "ICONS_ALL", "", "§nTout", 0, true));
        out.add(new Cell(ix + ICON_COL_W + ICON_COL_W / 2, y0 + lastRow * FILTER_ROW_H, ICON_COL_W / 2, "ICONS_NONE", "", "§nAucun", 0, true));
        return out;
    }

    private int filterRows() {
        return Math.max(ClientWaypoints.types().size() + 3, ICON_ROWS + 2);
    }

    private int filterX() { return Math.max(PANEL_W + 2, width - filterW() - 6); }
    private int filterY() { return Math.max(2, Math.min(90, height - filterH() - 16)); }
    private int filterH() { return filterRows() * FILTER_ROW_H + 6; }

    private boolean inFilterPanel(double mx, double my) {
        return showFilters && mx >= filterX() && mx <= filterX() + filterW() && my >= filterY() && my <= filterY() + filterH();
    }

    private void drawFilterPanel(GuiGraphics g, int mouseX, int mouseY) {
        int x = filterX(), y = filterY();
        g.fill(x - 1, y - 1, x + filterW() + 1, y + filterH() + 1, 0xFF8B7355);
        g.fill(x, y, x + filterW(), y + filterH(), 0xF0141418);
        g.fill(x + TYPE_COL_W - 2, y + 4, x + TYPE_COL_W - 1, y + filterH() - 4, 0xFF3A3A44);
        for (Cell c : filterCells()) {
            boolean hov = mouseX >= c.x && mouseX < c.x + c.w && mouseY >= c.y && mouseY < c.y + FILTER_ROW_H;
            if (c.kind.equals("HEADER")) {
                g.drawString(font, c.label, c.x + 5, c.y + 2, 0xFFFFFFFF, false);
                continue;
            }
            if (hov) g.fill(c.x, c.y, c.x + c.w - 1, c.y + FILTER_ROW_H, 0x40FFFFFF);
            switch (c.kind) {
                case "TYPE", "ONLY_OPEN" -> {
                    g.fill(c.x + 5, c.y + 2, c.x + 13, c.y + 10, 0xFF000000);
                    if (c.on) g.fill(c.x + 6, c.y + 3, c.x + 12, c.y + 9, 0xFF000000 | c.color);
                    g.drawString(font, c.label, c.x + 19, c.y + 2, c.on ? 0xFFFFFFFF : c.kind.equals("TYPE") ? 0xFF777777 : 0xFFAAAAAA, false);
                }
                case "ICON" -> {
                    MapIconRenderer.draw(g, c.id, c.x + 9, c.y + 6, 10, false);
                    if (!c.on) g.fill(c.x + 4, c.y + 1, c.x + 14, c.y + 11, 0xB0141418);
                    g.drawString(font, font.plainSubstrByWidth(c.label, c.w - 20), c.x + 16, c.y + 2, c.on ? 0xFFFFFFFF : 0xFF777777, false);
                }
                default -> g.drawString(font, c.label, c.x + 5, c.y + 2, 0xFFAAAAAA, false);
            }
        }
    }

    /** Clic dans le panneau de filtres ; renvoie true si le clic y a été traité. */
    private boolean clickFilterPanel(double mx, double my) {
        if (!inFilterPanel(mx, my)) return false;
        for (Cell c : filterCells()) {
            if (mx < c.x || mx >= c.x + c.w || my < c.y || my >= c.y + FILTER_ROW_H) continue;
            switch (c.kind) {
                case "TYPE" -> ClientWaypoints.setTypeHidden(c.id, c.on);
                case "ICON" -> ClientWaypoints.setIconHidden(c.id, c.on);
                case "ONLY_OPEN" -> ClientWaypoints.setOnlyOpen(!c.on);
                case "TYPES_ALL", "TYPES_NONE" -> {
                    for (WaypointType t : ClientWaypoints.types()) ClientWaypoints.setTypeHidden(t.id(), c.kind.equals("TYPES_NONE"));
                }
                case "ICONS_ALL", "ICONS_NONE" -> {
                    for (String id : fr.minenorth.map.waypoint.MapIcons.all().keySet()) ClientWaypoints.setIconHidden(id, c.kind.equals("ICONS_NONE"));
                }
                default -> { }
            }
            break;
        }
        updateFilterButton();
        return true;
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
        list.removeIf(w -> !ClientWaypoints.passesFilter(w) && !ClientWaypoints.isTracked(w));
        list.sort(Comparator.comparing(w -> w.name().toLowerCase()));
        list.addAll(TempWaypoints.inDimension(dim)); // repères temporaires
        List<Waypoint> mine = new java.util.ArrayList<>(MesPoints.inDimension(dim));
        mine.sort(Comparator.comparing(w -> w.name().toLowerCase()));
        int mineStart = list.size();
        list.addAll(mine); // mes points perso, en fin de liste
        boolean hide = ClientConfig.HIDE_WAYPOINTS.get();

        // ---- zone carte
        g.fill(0, 0, width, height, 0xFF0E0E12);
        g.enableScissor(PANEL_W, 0, width, height);
        MapRenderer.drawTerrain(g, centerX, centerZ, zoom, PANEL_W, 0, width, height);

        Waypoint hovered = null;
        MapIconRenderer.Placed hoveredCluster = null;
        placed.clear();
        List<Waypoint> iconPts = new java.util.ArrayList<>();
        List<int[]> iconPos = new java.util.ArrayList<>();
        for (Waypoint w : list) {
            int sx = Math.round(toScreenX(w.x() + 0.5)), sy = Math.round(toScreenY(w.z() + 0.5));
            if (sx < PANEL_W - 10 || sx > width + 10 || sy < -10 || sy > height + 10) continue;
            boolean tracked = ClientWaypoints.isTracked(w);
            boolean mineP = MesPoints.isMine(w);
            boolean temp = TempWaypoints.isTemp(w) && !mineP;
            if (hide && !temp && !mineP && !tracked) continue;
            if (!w.icon().isEmpty()) {
                // dessinées après les carrés, regroupées si elles se chevauchent (le point guidé passe en premier)
                int at = tracked ? 0 : iconPts.size();
                iconPts.add(at, w);
                iconPos.add(at, new int[]{sx, sy});
                continue;
            }
            MapRenderer.drawMarker(g, sx, sy, temp ? 2 : 3, w.color(), tracked);
            MapRenderer.drawStatusDot(g, sx, sy, temp ? 2 : 3, w.status());
            String label = w.name() + (w.status() == Waypoint.STATUS_OPEN ? " §a(Ouvert)" : w.status() == Waypoint.STATUS_CLOSED ? " §c(Fermé)" : "");
            int tw = font.width(label);
            g.fill(sx - tw / 2 - 2, sy - 17, sx + tw / 2 + 2, sy - 6, 0x90000000);
            g.drawString(font, label, sx - tw / 2, sy - 15, 0xFF000000 | w.color(), true);
            List<Waypoint> one = new java.util.ArrayList<>();
            one.add(w);
            MapIconRenderer.Placed pl = new MapIconRenderer.Placed(w, sx, sy, one);
            placed.add(pl);
            if (mouseX > PANEL_W && Math.abs(mouseX - sx) <= 5 && Math.abs(mouseY - sy) <= 5) { hovered = w; hoveredCluster = pl; }
        }
        // icônes de catégorie : taille constante, regroupement quand elles se touchent, nom au survol
        List<MapIconRenderer.Placed> clusters = MapIconRenderer.cluster(iconPts, iconPos, ICON_SIZE - 2);
        MapIconRenderer.Placed hoverIcon = null;
        for (MapIconRenderer.Placed p : clusters) {
            if (mouseX > PANEL_W && Math.abs(mouseX - p.x()) <= ICON_SIZE / 2 + 1 && Math.abs(mouseY - p.y()) <= ICON_SIZE / 2 + 1) hoverIcon = p;
        }
        for (MapIconRenderer.Placed p : clusters) {
            Waypoint w = p.rep();
            boolean tracked = ClientWaypoints.isTracked(w);
            MapIconRenderer.draw(g, w.icon(), p.x(), p.y(), ICON_SIZE, tracked);
            MapRenderer.drawStatusDot(g, p.x(), p.y(), ICON_SIZE / 2 - 1, w.status());
            if (p.count() > 1) {
                String n = "+" + (p.count() - 1);
                int nw = font.width(n);
                g.fill(p.x() + ICON_SIZE / 2 - nw + 1, p.y() + ICON_SIZE / 2 - 8, p.x() + ICON_SIZE / 2 + 3, p.y() + ICON_SIZE / 2 + 1, 0xE0000000);
                g.drawString(font, n, p.x() + ICON_SIZE / 2 - nw + 2, p.y() + ICON_SIZE / 2 - 7, 0xFFFFD27A, false);
            }
            // nom sous l'icône : point guidé, point survolé, ou quand on est assez près
            if (p == hoverIcon || tracked || (zoom >= 2f && p.count() == 1)) {
                int tw = font.width(w.name());
                g.fill(p.x() - tw / 2 - 2, p.y() + ICON_SIZE / 2 + 1, p.x() + tw / 2 + 2, p.y() + ICON_SIZE / 2 + 12, 0xB0000000);
                g.drawString(font, w.name(), p.x() - tw / 2, p.y() + ICON_SIZE / 2 + 3, 0xFFFFFFFF, true);
            }
            placed.add(p);
        }
        if (hoverIcon != null) { hovered = hoverIcon.rep(); hoveredCluster = hoverIcon; }

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
        String help = editing() ? "§6MODE ÉDITION§7 • Clic sur un point : le modifier • Clic droit sur la carte : nouveau point • Glisser : déplacer • Molette : zoom"
                : "Clic : guidage • Clic droit : point perso (créer / gérer) • Ctrl + clic droit : repère temporaire"
                + (ClientEditor.canEdit() ? " • Maj + clic droit : TP" : "");
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
            if (w.icon().isEmpty()) g.fill(6, y + 4, 12, y + 10, 0xFF000000 | w.color());
            else MapIconRenderer.draw(g, w.icon(), 9, y + 7, 10, false);
            boolean isMine = i + listScroll >= mineStart;
            if (isMine && i + listScroll == mineStart && i > 0) g.fill(4, y, PANEL_W - 5, y + 1, 0xFF8B7355);
            String dist = "";
            if (player != null) {
                double dx = w.x() + 0.5 - player.getX(), dz = w.z() + 0.5 - player.getZ();
                dist = (int) Math.sqrt(dx * dx + dz * dz) + "m";
            }
            int dw = font.width(dist);
            if (w.status() != Waypoint.STATUS_NONE) g.fill(PANEL_W - 10 - dw - 8, y + 5, PANEL_W - 10 - dw - 3, y + 10,
                    w.status() == Waypoint.STATUS_OPEN ? 0xFF43D854 : 0xFFE53935);
            String name = font.plainSubstrByWidth(w.name(), PANEL_W - 24 - dw - (w.status() != Waypoint.STATUS_NONE ? 16 : 6));
            boolean dim2 = hide && !TempWaypoints.isTemp(w) && !isMine && !tracked;
            g.drawString(font, (isMine ? "§6★ §r" : "") + name, 16, y + 3, dim2 ? 0xFF777777 : 0xFFFFFFFF, false);
            g.drawString(font, dist, PANEL_W - 6 - dw, y + 3, 0xFF888888, false);
        }

        // ---- boutons puis infobulle
        updatePlayersButton();
        updateFilterButton();
        updateEditButton();
        super.render(g, mouseX, mouseY, pt);
        if (showFilters) drawFilterPanel(g, mouseX, mouseY);
        if (hoveredPlayer != null) {
            g.renderTooltip(font, Component.literal(hoveredPlayer.name() + "  (" + Mth.floor(hoveredPlayer.x()) + ", "
                    + Mth.floor(hoveredPlayer.y()) + ", " + Mth.floor(hoveredPlayer.z()) + ")"), mouseX, mouseY);
        } else if (hovered != null && !inFilterPanel(mouseX, mouseY)) {
            List<Component> tip = new java.util.ArrayList<>();
            tip.add(Component.literal(hovered.name() + "  (" + hovered.x() + ", " + hovered.y() + ", " + hovered.z() + ")"));
            WaypointType type = ClientWaypoints.typeOf(hovered);
            if (type != null) tip.add(Component.literal("§7Type : §f" + type.label()));
            if (!hovered.icon().isEmpty()) {
                fr.minenorth.map.waypoint.MapIcons.Icon ic = fr.minenorth.map.waypoint.MapIcons.get(hovered.icon());
                if (ic != null) tip.add(Component.literal("§7Catégorie : §f" + ic.label()));
            }
            if (hovered.status() == Waypoint.STATUS_OPEN) tip.add(Component.literal("§aOuvert"));
            else if (hovered.status() == Waypoint.STATUS_CLOSED) tip.add(Component.literal("§cFermé"));
            if (hoveredCluster != null && hoveredCluster.count() > 1) {
                tip.add(Component.literal("§eAussi ici (zoome pour séparer) :"));
                for (int i = 1; i < hoveredCluster.members().size() && i <= 6; i++) tip.add(Component.literal("§7 • " + hoveredCluster.members().get(i).name()));
                if (hoveredCluster.count() > 7) tip.add(Component.literal("§7 … et " + (hoveredCluster.count() - 7) + " autres"));
            }
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }

    }

    /** Admin : TP au point (le serveur revérifie le droit). */
    private void teleportTo(Waypoint w) {
        teleportTo(w.dimension(), w.x(), w.y(), w.z(), true);
    }

    private void teleportTo(String dim, int x, int y, int z, boolean hasY) {
        NetworkHandler.CHANNEL.sendToServer(new TeleportPacket(dim, x, y, z, hasY));
        onClose();
    }

    private Waypoint waypointAt(double mx, double my) {
        if (inFilterPanel(mx, my)) return null;
        for (int i = placed.size() - 1; i >= 0; i--) {   // le dernier dessiné est au-dessus
            MapIconRenderer.Placed p = placed.get(i);
            int r = p.rep().icon().isEmpty() ? 5 : ICON_SIZE / 2 + 1;
            if (Math.abs(mx - p.x()) <= r && Math.abs(my - p.y()) <= r) return p.rep();
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (filterBtn != null && filterBtn.isMouseOver(mx, my)) return super.mouseClicked(mx, my, button);
        if (editBtn != null && editBtn.visible && editBtn.isMouseOver(mx, my)) return super.mouseClicked(mx, my, button);
        if (clickFilterPanel(mx, my)) return true;
        if (showFilters && button == 0 && !inFilterPanel(mx, my) && mx >= PANEL_W) showFilters = false;
        if (mx < PANEL_W) {
            int idx = (int) ((my - LIST_TOP) / ENTRY_H);
            if (my >= LIST_TOP && idx >= 0 && idx < visibleEntries() && idx + listScroll < list.size()) {
                Waypoint w = list.get(idx + listScroll);
                if (button == 1 && hasShiftDown() && ClientEditor.canEdit()) {
                    teleportTo(w);
                    return true;
                }
                if (button == 1 && MesPoints.isMine(w)) {
                    Minecraft.getInstance().setScreen(new PointEditScreen(this, w));
                    return true;
                }
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
                if (editing() && !TempWaypoints.isTemp(w) && !MesPoints.isMine(w)) openEditor(w, mx, my);
                else ClientWaypoints.toggleTrack(w);
                return true;
            }
            dragging = true;
            return true;
        }
        if (button == 1) {
            Waypoint w = waypointAt(mx, my);
            Minecraft mc = Minecraft.getInstance();
            if (hasShiftDown() && ClientEditor.canEdit()) {
                // Maj + clic droit (admin) : téléportation à l'endroit cliqué ou au point ; rien n'est posé
                if (w != null) teleportTo(w);
                else teleportTo(dim(), Mth.floor(toWorldX(mx)), 0, Mth.floor(toWorldZ(my)), false);
                return true;
            }
            if (w != null && MesPoints.isMine(w)) {
                mc.setScreen(new PointEditScreen(this, w));   // gérer mon point : renommer, couleur, déplacer, supprimer
                return true;
            }
            if (editing() && (w == null || !TempWaypoints.isTemp(w))) {
                openEditor(w, mx, my);
            } else if (w != null && TempWaypoints.isTemp(w)) {
                TempWaypoints.remove(w);
            } else if (w == null && mc.player != null) {
                int x = Mth.floor(toWorldX(mx)), z = Mth.floor(toWorldZ(my));
                if (hasControlDown()) {
                    // Ctrl + clic droit : repère temporaire rapide (comme avant)
                    Waypoint nw = TempWaypoints.add(dim(), x, mc.player.getBlockY(), z);
                    ClientWaypoints.track(nw);
                    mc.player.displayClientMessage(Component.literal("Repère temporaire posé en " + x + ", " + z
                            + " (clic droit dessus pour le retirer)").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
                } else if (MesPoints.count() >= MesPoints.max()) {
                    mc.player.displayClientMessage(Component.literal("Limite de " + MesPoints.max() + " points perso atteinte : supprime-en un (clic droit dessus).")
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
                } else {
                    mc.setScreen(new PointEditScreen(this, dim(), x, mc.player.getBlockY(), z));
                }
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
