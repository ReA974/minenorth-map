package fr.minenorth.map.client;

import fr.minenorth.map.waypoint.MapIcons;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Grille d'icônes cliquable (écrans d'édition). La première case = « automatique / aucune ». Dessinée et cliquée par l'écran qui la contient. */
public final class IconPicker {
    public static final int CELL = 20;
    private final int x, y, cols;
    private final String autoLabel;
    private final List<String> ids = new ArrayList<>();
    private String selected;

    public IconPicker(int x, int y, int cols, String selected, String autoLabel) {
        this.x = x;
        this.y = y;
        this.cols = cols;
        this.autoLabel = autoLabel;
        this.selected = selected == null ? "" : selected;
        ids.add("");
        ids.addAll(MapIcons.all().keySet());
    }

    public String selected() { return selected; }

    public int height() { return ((ids.size() + cols - 1) / cols) * CELL; }

    private int indexAt(double mx, double my) {
        int c = (int) Math.floor((mx - x) / CELL), r = (int) Math.floor((my - y) / CELL);
        if (mx < x || my < y || c < 0 || c >= cols || r < 0) return -1;
        int i = r * cols + c;
        return i < ids.size() ? i : -1;
    }

    public boolean click(double mx, double my) {
        int i = indexAt(mx, my);
        if (i < 0) return false;
        selected = ids.get(i);
        return true;
    }

    public void render(GuiGraphics g, Font font, int mx, int my) {
        int hov = indexAt(mx, my);
        for (int i = 0; i < ids.size(); i++) {
            int cx = x + (i % cols) * CELL, cy = y + (i / cols) * CELL;
            boolean sel = ids.get(i).equals(selected);
            g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, sel ? 0xFFFFD27A : i == hov ? 0xFF55555E : 0xFF26262C);
            g.fill(cx + 1, cy + 1, cx + CELL - 2, cy + CELL - 2, 0xFF17171C);
            if (ids.get(i).isEmpty()) g.drawCenteredString(font, "∅", cx + CELL / 2 - 1, cy + 6, sel ? 0xFFFFD27A : 0xFF999999);
            else MapIconRenderer.draw(g, ids.get(i), cx + CELL / 2 - 1, cy + CELL / 2 - 1, 16, false);
        }
    }

    /** Libellé de la case survolée (null si aucune) : à afficher en info-bulle par l'écran. */
    public String hoverLabel(double mx, double my) {
        int i = indexAt(mx, my);
        if (i < 0) return null;
        return ids.get(i).isEmpty() ? autoLabel : MapIcons.get(ids.get(i)).label();
    }
}
