package fr.minenorth.map.client;

import fr.minenorth.map.waypoint.Waypoint;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Création et gestion d'un point perso : nom, couleur, déplacement à ma position, guidage, suppression (confirmation par un second clic).
 * Tout reste sur l'ordinateur du joueur (voir {@link MesPoints}).
 */
public class PointEditScreen extends Screen {
    private static final int W = 260;

    private final Screen parent;
    /** Point géré, ou null pour un nouveau point. */
    private final Waypoint editing;
    private final String dim;
    private final int px, py, pz;

    private EditBox nameBox;
    private Button deleteBtn;
    private IconPicker picker;
    private String icon = "";
    private int color;
    private boolean confirmDelete;
    /** Nom en cours de saisie, conservé quand l'écran est reconstruit (choix d'une couleur). */
    private String draft;
    private String error = "";

    /** Nouveau point en (x, y, z). */
    public PointEditScreen(Screen parent, String dim, int x, int y, int z) {
        super(Component.literal("Nouveau point perso"));
        this.parent = parent;
        this.editing = null;
        this.dim = dim;
        this.px = x;
        this.py = y;
        this.pz = z;
        this.color = MesPoints.DEFAULT_COLOR;
    }

    /** Gestion d'un point perso existant. */
    public PointEditScreen(Screen parent, Waypoint w) {
        super(Component.literal("Mon point"));
        this.parent = parent;
        this.editing = w;
        this.dim = w.dimension();
        this.px = w.x();
        this.py = w.y();
        this.pz = w.z();
        this.color = w.color();
        this.icon = w.icon();
    }

    private int h() { return editing == null ? 176 : 206; }
    private int pickerY() { return editing == null ? 94 : 142; }

    @Override
    protected void init() {
        int x = (width - W) / 2, y = (height - h()) / 2;
        picker = new IconPicker(x + 10, y + pickerY(), 12, picker != null ? picker.selected() : icon, "Aucune (carré de la couleur choisie)");
        nameBox = new EditBox(font, x + 10, y + 28, W - 20, 16, Component.literal("Nom"));
        nameBox.setMaxLength(MesPoints.MAX_NAME);
        nameBox.setValue(draft != null ? draft : editing != null ? editing.name() : MesPoints.defaultName());
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        // palette de couleurs
        int[] pal = MesPoints.PALETTE;
        int sw = 22, gap = 3, total = pal.length * sw + (pal.length - 1) * gap, px0 = x + (W - total) / 2;
        for (int i = 0; i < pal.length; i++) {
            final int c = pal[i];
            addRenderableWidget(Button.builder(swatch(c), b -> {
                color = c;
                draft = nameBox.getValue();
                rebuildWidgets();
            }).bounds(px0 + i * (sw + gap), y + 62, sw, 16).build());
        }

        if (editing == null) {
            addRenderableWidget(Button.builder(Component.literal("§aCréer le point"), b -> create())
                    .bounds(x + 10, y + h() - 26, 120, 18).build());
            addRenderableWidget(Button.builder(Component.literal("Annuler"), b -> onClose())
                    .bounds(x + 134, y + h() - 26, 116, 18).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("§aEnregistrer"), b -> save())
                    .bounds(x + 10, y + 90, 117, 18).build());
            addRenderableWidget(Button.builder(Component.literal("Guider"), b -> guide())
                    .bounds(x + 133, y + 90, 117, 18).build());
            addRenderableWidget(Button.builder(Component.literal("Déplacer ici (ma position)"), b -> moveHere())
                    .bounds(x + 10, y + 112, W - 20, 18).build());
            deleteBtn = addRenderableWidget(Button.builder(Component.literal("§cSupprimer"), b -> {
                if (!confirmDelete) {
                    confirmDelete = true;
                    draft = nameBox.getValue();
                    deleteBtn.setMessage(Component.literal("§cCONFIRMER la suppression"));
                    return;
                }
                MesPoints.remove(editing.name());
                onClose();
            }).bounds(x + 10, y + h() - 26, 160, 18).build());
            addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
                    .bounds(x + 174, y + h() - 26, 76, 18).build());
        }
    }

    private Component swatch(int c) {
        return Component.literal(c == color ? "✔" : "■").withStyle(s -> s.withColor(c).withBold(true));
    }

    private void create() {
        String err = MesPoints.add(nameBox.getValue(), dim, px, py, pz, color, picker.selected());
        if (err != null) {
            error = err;
            return;
        }
        Waypoint w = MesPoints.byName(MesPoints.clean(nameBox.getValue()));
        if (w != null) ClientWaypoints.track(w);   // comme l'ancien repère rapide : on est guidé tout de suite
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal("Point « " + (w != null ? w.name() : "") + " » créé. Clic droit dessus pour le gérer.")
                .withStyle(ChatFormatting.GOLD), true);
        onClose();
    }

    private void save() {
        String err = MesPoints.update(editing.name(), nameBox.getValue(), color, picker.selected());
        if (err != null) {
            error = err;
            return;
        }
        onClose();
    }

    private void guide() {
        Waypoint w = MesPoints.byName(editing.name());
        if (w != null) ClientWaypoints.track(w);
        onClose();
    }

    private void moveHere() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        String err = MesPoints.move(editing.name(), mc.level.dimension().location().toString(), mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
        if (err != null) error = err;
        else onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) {   // Entrée
            if (editing == null) create(); else save();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = (width - W) / 2, y = (height - h()) / 2;
        g.fill(x - 1, y - 1, x + W + 1, y + h() + 1, 0xFF8B7355);
        g.fill(x, y, x + W, y + h(), 0xF0141418);
        g.drawCenteredString(font, title, width / 2, y + 8, 0xFFFFD27A);
        g.drawString(font, "Nom", x + 10, y + 18, 0xFF888888, false);
        g.drawString(font, "Couleur", x + 10, y + 52, 0xFF888888, false);
        String pos = "X " + px + "  Y " + py + "  Z " + pz;
        g.drawString(font, pos, x + W - 10 - font.width(pos), y + 8, 0xFF777777, false);
        g.drawString(font, "Icône", x + 10, y + pickerY() - 10, 0xFF888888, false);
        picker.render(g, font, mx, my);
        String info = MesPoints.count() + " / " + MesPoints.max() + " points";
        g.drawString(font, info, x + W - 10 - font.width(info), y + 52, 0xFF666666, false);
        if (!error.isEmpty()) g.drawCenteredString(font, error, width / 2, y + (h() - 38), 0xFFFF5555);
        super.render(g, mx, my, pt);
        String tip = picker.hoverLabel(mx, my);
        if (tip != null) g.renderTooltip(font, Component.literal(tip), mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && picker.click(mx, my)) return true;
        return super.mouseClicked(mx, my, button);
    }
}
