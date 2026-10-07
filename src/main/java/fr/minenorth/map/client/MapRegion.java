package fr.minenorth.map.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;

/** Région de 512x512 blocs (32x32 chunks) = une image 512x512, 1 pixel par bloc. */
final class MapRegion {
    static final int SIZE = 512;

    final int rx, rz;
    final NativeImage image;
    private DynamicTexture texture;
    private ResourceLocation location;
    private boolean textureDirty = true;
    boolean diskDirty;
    long lastAccess = System.currentTimeMillis();

    MapRegion(int rx, int rz, NativeImage image) {
        this.rx = rx;
        this.rz = rz;
        this.image = image;
    }

    static MapRegion blank(int rx, int rz) {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, true); // transparent
        return new MapRegion(rx, rz, img);
    }

    private static int[] palette;

    /** Remplit l'image depuis les couleurs packées envoyées par le serveur (1 octet par bloc). */
    void applyPacked(byte[] colors) {
        if (palette == null) {
            int[] p = new int[256];
            for (int i = 4; i < 256; i++) p[i] = MapColor.getColorFromPackedId(i);
            palette = p;
        }
        for (int z = 0; z < SIZE; z++) {
            int row = z << 9;
            for (int x = 0; x < SIZE; x++) image.setPixelRGBA(x, z, palette[colors[row | x] & 255]);
        }
        textureDirty = true;
    }

    /** color au format ABGR (celui de NativeImage / MapColor.calculateRGBColor). */
    void setPixel(int px, int pz, int abgr) {
        if (image.getPixelRGBA(px, pz) != abgr) {
            image.setPixelRGBA(px, pz, abgr);
            textureDirty = true;
            diskDirty = true;
        }
    }

    /** À appeler sur le thread de rendu. */
    ResourceLocation texture() {
        lastAccess = System.currentTimeMillis();
        if (texture == null) {
            texture = new DynamicTexture(image);
            location = Minecraft.getInstance().getTextureManager()
                    .register("minenorth_map_" + rx + "_" + rz, texture);
            textureDirty = false;
        } else if (textureDirty) {
            texture.upload();
            textureDirty = false;
        }
        return location;
    }

    void uploadIfDirty() {
        if (texture != null && textureDirty) {
            texture.upload();
            textureDirty = false;
        }
    }

    void close() {
        if (location != null) {
            Minecraft.getInstance().getTextureManager().release(location);
            texture.close(); // ferme aussi l'image
            location = null;
            texture = null;
        } else {
            image.close();
        }
    }
}
