package fr.minenorth.map.client;

import fr.minenorth.map.network.NetworkHandler;
import fr.minenorth.map.network.WaypointEditPacket;
import fr.minenorth.map.waypoint.Waypoint;
import fr.minenorth.map.waypoint.WaypointType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Formulaire d'ajout / modification d'un point depuis la carte (OP). Les modifications partent au serveur,
 * qui revérifie le droit et renvoie les points à tout le monde.
 */
public class WaypointEditScreen extends Screen {
    private static final int W = 250;

    private final Screen parent;
    /** Point modifié, ou null pour un nouveau point. */
    private final Waypoint editing;
    private final String dim;
    private final int px, py, pz;

    private EditBox nameBox;
    private Button typeBtn, companyBtn, deleteBtn;
    private int typeIdx;
    /** -1 = aucune ; sinon index dans ClientEditor.companies(). */
    private int companyIdx = -1;
    private boolean confirmDelete;
    private String error = "";

    /** Nouveau point à (x, y, z). */
    public WaypointEditScreen(Screen parent, String dim, int x, int y, int z) {
        super(Component.literal("Nouveau point"));
        this.parent = parent;
        this.editing = null;
        this.dim = dim;
        this.px = x;
        this.py = y;
        this.pz = z;
        this.typeIdx = defaultTypeIndex();
    }

    /** Modification d'un point existant. */
    public WaypointEditScreen(Screen parent, Waypoint w) {
        super(Component.literal("Modifier le point"));
        this.parent = parent;
        this.editing = w;
        this.dim = w.dimension();
        this.px = w.x();
        this.py = w.y();
        this.pz = w.z();
        List<WaypointType> types = ClientWaypoints.types();
        for (int i = 0; i < types.size(); i++) if (types.get(i).id().equals(w.type())) typeIdx = i;
        List<ClientEditor.Company> cs = ClientEditor.companies();
        for (int i = 0; i < cs.size(); i++) if (cs.get(i).id() == w.company()) companyIdx = i;
    }

    private static int defaultTypeIndex() {
        List<WaypointType> types = ClientWaypoints.types();
        for (int i = 0; i < types.size(); i++) if (types.get(i).id().equals(Waypoint.DEFAULT_TYPE)) return i;
        return Math.max(0, types.size() - 1);
    }

    @Override
    protected void init() {
        int x = (width - W) / 2, y = (height - 150) / 2;
        nameBox = new EditBox(font, x + 10, y + 28, W - 20, 16, Component.literal("Nom"));
        nameBox.setMaxLength(32);
        nameBox.setValue(editing != null ? editing.name() : "");
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        typeBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            List<WaypointType> t = ClientWaypoints.types();
            if (!t.isEmpty()) typeIdx = (typeIdx + 1) % t.size();
            refreshButtons();
        }).bounds(x + 10, y + 62, W - 20, 18).build());

        companyBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            int n = ClientEditor.companies().size();
            companyIdx = companyIdx + 1 >= n ? -1 : companyIdx + 1;
            // lier une entreprise : le type devient « entreprise » s'il était « autre »
            List<WaypointType> t = ClientWaypoints.types();
            if (companyIdx >= 0 && !t.isEmpty() && t.get(typeIdx).id().equals(Waypoint.DEFAULT_TYPE))
                for (int i = 0; i < t.size(); i++) if (t.get(i).id().equals("entreprise")) typeIdx = i;
            refreshButtons();
        }).bounds(x + 10, y + 96, W - 20, 18).build());

        addRenderableWidget(Button.builder(Component.literal("§aEnregistrer"), b -> save())
                .bounds(x + 10, y + 124, editing != null ? 70 : 115, 18).build());
        if (editing != null) {
            addRenderableWidget(Button.builder(Component.literal("Ma position"), b -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || mc.level == null) return;
                send(new WaypointEditPacket(WaypointEditPacket.MOVE, editing.name(), "", "", -1,
                        mc.level.dimension().location().toString(), mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()));
                onClose();
            }).bounds(x + 84, y + 124, 72, 18).build());
            deleteBtn = addRenderableWidget(Button.builder(Component.literal("§cSupprimer"), b -> {
                if (!confirmDelete) {
                    confirmDelete = true;
                    refreshButtons();
                    return;
                }
                send(new WaypointEditPacket(WaypointEditPacket.REMOVE, editing.name(), "", "", -1, "", 0, 0, 0));
                if (ClientWaypoints.isTracked(editing)) ClientWaypoints.track(null);
                onClose();
            }).bounds(x + 160, y + 124, 80, 18).build());
        }
        if (editing == null) {
            addRenderableWidget(Button.builder(Component.literal("Annuler"), b -> onClose())
                    .bounds(x + 125, y + 124, 115, 18).build());
        }
        refreshButtons();
    }

    private void refreshButtons() {
        List<WaypointType> t = ClientWaypoints.types();
        String type = t.isEmpty() ? "—" : t.get(Math.min(typeIdx, t.size() - 1)).label();
        typeBtn.setMessage(Component.literal("Type : " + type + "  (clic pour changer)"));
        List<ClientEditor.Company> cs = ClientEditor.companies();
        companyBtn.active = !cs.isEmpty();
        String comp = cs.isEmpty() ? "aucune disponible" : companyIdx < 0 ? "Aucune" : cs.get(companyIdx).name();
        companyBtn.setMessage(Component.literal("Entreprise liée : " + comp));
        if (deleteBtn != null) deleteBtn.setMessage(Component.literal(confirmDelete ? "§cCONFIRMER" : "§cSupprimer"));
    }

    private void save() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            error = "Donne un nom au point.";
            return;
        }
        List<WaypointType> t = ClientWaypoints.types();
        String type = t.isEmpty() ? Waypoint.DEFAULT_TYPE : t.get(Math.min(typeIdx, t.size() - 1)).id();
        List<ClientEditor.Company> cs = ClientEditor.companies();
        int company = companyIdx >= 0 && companyIdx < cs.size() ? cs.get(companyIdx).id() : -1;
        if (editing == null) {
            send(new WaypointEditPacket(WaypointEditPacket.ADD, name, "", type, company, dim, px, py, pz));
        } else {
            send(new WaypointEditPacket(WaypointEditPacket.UPDATE, editing.name(), name, type, company, "", 0, 0, 0));
        }
        onClose();
    }

    private static void send(WaypointEditPacket p) {
        NetworkHandler.CHANNEL.sendToServer(p);
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
        int x = (width - W) / 2, y = (height - 150) / 2;
        g.fill(x - 1, y - 1, x + W + 1, y + 151, 0xFF8B7355);
        g.fill(x, y, x + W, y + 150, 0xF0141418);
        g.drawCenteredString(font, title, width / 2, y + 8, 0xFFFFD27A);
        g.drawString(font, "Nom", x + 10, y + 18, 0xFF888888, false);
        g.drawString(font, "Type", x + 10, y + 52, 0xFF888888, false);
        g.drawString(font, "Entreprise (affiche Ouvert / Fermé sur la carte)", x + 10, y + 86, 0xFF888888, false);
        String pos = "X " + px + "  Y " + py + "  Z " + pz;
        g.drawString(font, pos, x + W - 10 - font.width(pos), y + 8, 0xFF777777, false);
        if (!error.isEmpty()) g.drawCenteredString(font, error, width / 2, y + 144 - 2, 0xFFFF5555);
        super.render(g, mx, my, pt);
    }
}
