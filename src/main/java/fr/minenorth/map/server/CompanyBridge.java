package fr.minenorth.map.server;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pont par réflexion vers EntrepriseApi (mod entreprises). Sans lui, tous les appels sont sans effet :
 * aucune dépendance de compilation entre les deux mods.
 */
public final class CompanyBridge {
    private CompanyBridge() {}

    private static boolean tried;
    private static Method isOpen, list, name;

    private static synchronized boolean ready() {
        if (!tried) {
            tried = true;
            try {
                if (ModList.get().isLoaded("minenorthentreprises")) {
                    Class<?> api = Class.forName("fr.minenorth.entreprises.api.EntrepriseApi");
                    isOpen = api.getMethod("isOpen", MinecraftServer.class, int.class);
                    list = api.getMethod("listCompanies", MinecraftServer.class);
                    name = api.getMethod("companyName", MinecraftServer.class, int.class);
                }
            } catch (Throwable t) {
                isOpen = list = name = null;
            }
        }
        return isOpen != null;
    }

    public static boolean available() { return ready(); }

    public static boolean isOpen(MinecraftServer s, int id) {
        if (!ready()) return false;
        try { return (Boolean) isOpen.invoke(null, s, id); } catch (Throwable t) { return false; }
    }

    /** Nom de l'entreprise, "" si elle n'existe plus ou si le mod est absent. */
    public static String companyName(MinecraftServer s, int id) {
        if (!ready()) return "";
        try { return (String) name.invoke(null, s, id); } catch (Throwable t) { return ""; }
    }

    /** Entreprises actives : {id, nom}. */
    @SuppressWarnings("unchecked")
    public static List<Object[]> companies(MinecraftServer s) {
        List<Object[]> out = new ArrayList<>();
        if (!ready()) return out;
        try {
            for (String line : (List<String>) list.invoke(null, s)) {
                int i = line.indexOf('|');
                if (i > 0) out.add(new Object[]{Integer.parseInt(line.substring(0, i)), line.substring(i + 1)});
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** Id de l'entreprise active portant ce nom (insensible à la casse), ou -1. */
    public static int findByName(MinecraftServer s, String n) {
        for (Object[] c : companies(s)) if (((String) c[1]).toLowerCase(Locale.ROOT).equals(n.toLowerCase(Locale.ROOT))) return (Integer) c[0];
        return -1;
    }
}
