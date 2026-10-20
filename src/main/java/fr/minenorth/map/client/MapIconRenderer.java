package fr.minenorth.map.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.minenorth.map.MineNorthMap;
import fr.minenorth.map.waypoint.MapIcons;
import fr.minenorth.map.waypoint.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Dessin des icônes de catégorie (textures 32x32, taille écran constante quel que soit le zoom) et regroupement des icônes trop proches. */
public final class MapIconRenderer {
    private MapIconRenderer() {}

    private static final Map<String, ResourceLocation> LOCATIONS = new HashMap<>();
    /** Texture déjà passée en filtrage lisse : si elle est rechargée (changement de pack), on recommence. */
    private static final Map<String, AbstractTexture> SMOOTHED = new HashMap<>();

    public static ResourceLocation texture(String id) {
        return LOCATIONS.computeIfAbsent(id, k -> new ResourceLocation(MineNorthMap.MODID, "textures/map_icons/" + k + ".png"));
    }

    /** Dessine l'icône centrée en (cx, cy), carrée de côté size. tracked = cadre blanc (point guidé). */
    public static void draw(GuiGraphics g, String icon, int cx, int cy, int size, boolean tracked) {
        if (!MapIcons.valid(icon)) return;
        ResourceLocation rl = texture(icon);
        smooth(icon, rl);
        int x = cx - size / 2, y = cy - size / 2;
        if (tracked) {
            g.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xFFFFFFFF);
            g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF000000);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(rl, x, y, size, size, 0f, 0f, 32, 32, 32, 32);
        RenderSystem.disableBlend();
    }

    private static void smooth(String id, ResourceLocation rl) {
        try {
            AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(rl);
            if (SMOOTHED.get(id) != t) {
                t.bind();
                t.setFilter(true, false);
                SMOOTHED.put(id, t);
            }
        } catch (RuntimeException ignored) {
            // l'icône s'affichera simplement avec le filtrage par défaut
        }
    }

    /** Icône groupée : rep = point affiché, members = tous les points regroupés (rep compris). */
    public record Placed(Waypoint rep, int x, int y, List<Waypoint> members) {
        public int count() { return members.size(); }
    }

    /** Fusionne les points dont les icônes se chevauchent (distance < minDist px). L'ordre d'entrée donne la priorité (le premier reste visible). */
    public static List<Placed> cluster(List<Waypoint> pts, List<int[]> screen, int minDist) {
        List<Placed> out = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            int x = screen.get(i)[0], y = screen.get(i)[1];
            Placed hit = null;
            for (Placed p : out) {
                if (Math.abs(p.x - x) < minDist && Math.abs(p.y - y) < minDist) { hit = p; break; }
            }
            if (hit != null) hit.members.add(pts.get(i));
            else {
                List<Waypoint> m = new ArrayList<>();
                m.add(pts.get(i));
                out.add(new Placed(pts.get(i), x, y, m));
            }
        }
        return out;
    }
}
