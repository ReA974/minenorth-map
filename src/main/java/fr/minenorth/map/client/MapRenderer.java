package fr.minenorth.map.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Outils de dessin partagés entre la minicarte et la carte plein écran. */
public final class MapRenderer {
    private MapRenderer() {}

    /** Dessine le terrain exploré dans le rectangle écran [x0,x1]x[y0,y1], centré sur (centerX, centerZ). */
    public static void drawTerrain(GuiGraphics g, double centerX, double centerZ, float zoom,
                                   int x0, int y0, int x1, int y1) {
        float sx = (x0 + x1) / 2f;
        float sy = (y0 + y1) / 2f;
        double halfW = (x1 - x0) / 2.0 / zoom;
        double halfH = (y1 - y0) / 2.0 / zoom;
        int size = MapRegion.SIZE;
        int rxMin = Mth.floor((centerX - halfW) / size);
        int rxMax = Mth.floor((centerX + halfW) / size);
        int rzMin = Mth.floor((centerZ - halfH) / size);
        int rzMax = Mth.floor((centerZ + halfH) / size);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        PoseStack ps = g.pose();
        for (int rx = rxMin; rx <= rxMax; rx++) {
            for (int rz = rzMin; rz <= rzMax; rz++) {
                ResourceLocation tex = MapStorage.regionTexture(rx, rz);
                if (tex == null) continue;
                ps.pushPose();
                ps.translate(sx, sy, 0f);
                ps.scale(zoom, zoom, 1f);
                ps.translate((float) ((double) rx * size - centerX), (float) ((double) rz * size - centerZ), 0f);
                g.blit(tex, 0, 0, 0f, 0f, size, size, size, size);
                ps.popPose();
            }
        }
        RenderSystem.disableBlend();
    }

    /** Flèche (triangle échancré) pointant dans la direction (dirX, dirY) écran. */
    public static void drawArrow(GuiGraphics g, float x, float y, float dirX, float dirY, float size, int argb) {
        float len = Mth.sqrt(dirX * dirX + dirY * dirY);
        if (len < 1.0E-4f) { dirX = 0; dirY = -1; } else { dirX /= len; dirY /= len; }
        float px = -dirY, py = dirX;
        float tipX = x + dirX * size, tipY = y + dirY * size;
        float lX = x - dirX * size * 0.75f + px * size * 0.7f, lY = y - dirY * size * 0.75f + py * size * 0.7f;
        float rX = x - dirX * size * 0.75f - px * size * 0.7f, rY = y - dirY * size * 0.75f - py * size * 0.7f;
        float nX = x - dirX * size * 0.3f, nY = y - dirY * size * 0.3f;

        float a = (argb >>> 24 & 255) / 255f, r = (argb >> 16 & 255) / 255f, gg = (argb >> 8 & 255) / 255f, b = (argb & 255) / 255f;

        g.flush();
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        bb.vertex(m, tipX, tipY, 0).color(r, gg, b, a).endVertex();
        bb.vertex(m, lX, lY, 0).color(r, gg, b, a).endVertex();
        bb.vertex(m, nX, nY, 0).color(r, gg, b, a).endVertex();
        bb.vertex(m, tipX, tipY, 0).color(r, gg, b, a).endVertex();
        bb.vertex(m, nX, nY, 0).color(r, gg, b, a).endVertex();
        bb.vertex(m, rX, rY, 0).color(r, gg, b, a).endVertex();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Flèche blanche bordée de noir (le joueur). */
    public static void drawPlayerArrow(GuiGraphics g, float x, float y, float dirX, float dirY, float size) {
        drawArrow(g, x, y, dirX, dirY, size + 1.6f, 0xFF000000);
        drawArrow(g, x, y, dirX, dirY, size, 0xFFFFFFFF);
    }

    /** Marqueur carré d'un point de repère. */
    public static void drawMarker(GuiGraphics g, int x, int y, int half, int rgb, boolean tracked) {
        if (tracked) g.fill(x - half - 2, y - half - 2, x + half + 3, y + half + 3, 0xFFFFFFFF);
        g.fill(x - half - 1, y - half - 1, x + half + 2, y + half + 2, 0xFF000000);
        g.fill(x - half, y - half, x + half + 1, y + half + 1, 0xFF000000 | rgb);
    }

    /** Direction écran du regard du joueur (carte orientée nord en haut). */
    public static float[] facing(float yawDeg) {
        float rad = yawDeg * Mth.DEG_TO_RAD;
        return new float[]{-Mth.sin(rad), Mth.cos(rad)};
    }
}
