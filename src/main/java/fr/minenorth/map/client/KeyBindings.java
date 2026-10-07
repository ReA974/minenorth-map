package fr.minenorth.map.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {
    public static final String CATEGORY = "key.categories.minenorth_map";

    public static final KeyMapping OPEN_MAP = new KeyMapping("key.minenorth_map.open_map",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);
    public static final KeyMapping TOGGLE_MINIMAP = new KeyMapping("key.minenorth_map.toggle_minimap",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);
    public static final KeyMapping ZOOM_IN = new KeyMapping("key.minenorth_map.zoom_in",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_ADD, CATEGORY);
    public static final KeyMapping ZOOM_OUT = new KeyMapping("key.minenorth_map.zoom_out",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_SUBTRACT, CATEGORY);

    private KeyBindings() {}
}
