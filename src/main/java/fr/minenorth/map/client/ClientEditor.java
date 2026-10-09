package fr.minenorth.map.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Droit d'édition des points depuis la carte + entreprises liables, envoyés par le serveur. Aucune classe client-only ici. */
public final class ClientEditor {
    public record Company(int id, String name) {}

    private static volatile boolean canEdit;
    private static volatile List<Company> companies = Collections.emptyList();

    private ClientEditor() {}

    public static boolean canEdit() { return canEdit; }
    public static List<Company> companies() { return companies; }

    public static void set(boolean can, List<String> raw) {
        canEdit = can;
        List<Company> l = new ArrayList<>();
        for (String s : raw) {
            int i = s.indexOf('|');
            if (i <= 0) continue;
            try {
                l.add(new Company(Integer.parseInt(s.substring(0, i)), s.substring(i + 1)));
            } catch (NumberFormatException ignored) {}
        }
        l.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        companies = Collections.unmodifiableList(l);
    }

    public static void reset() {
        canEdit = false;
        companies = Collections.emptyList();
    }
}
