package fr.minenorth.map.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.minenorth.map.network.NetworkHandler;
import fr.minenorth.map.server.RenderJob;
import fr.minenorth.map.server.ServerMapManager;
import fr.minenorth.map.waypoint.Waypoint;
import fr.minenorth.map.waypoint.WaypointSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /carte point ajouter "<nom>" [couleur]           -> à ta position
 * /carte point placer "<nom>" <x> <y> <z> [couleur]
 * /carte point deplacer "<nom>"                    -> déplace à ta position
 * /carte point supprimer "<nom>"
 * /carte point renommer "<ancien>" "<nouveau>"
 * /carte point couleur "<nom>" <couleur>
 * /carte point liste
 * /carte point tp "<nom>"
 * /carte generer rayon <blocs>                     -> dessine la carte commune autour de toi
 * /carte generer zone <x1> <z1> <x2> <z2>
 * /carte generer statut | stop
 * Les noms avec espaces doivent être entre guillemets. Couleur : nom (rouge, bleu...) ou hex RRGGBB.
 */
public final class MapCommand {
    private MapCommand() {}

    public static final Map<String, Integer> COLORS = new LinkedHashMap<>();
    static {
        COLORS.put("rouge", 0xE53935);
        COLORS.put("orange", 0xFB8C00);
        COLORS.put("jaune", 0xFDD835);
        COLORS.put("vert", 0x43A047);
        COLORS.put("cyan", 0x00ACC1);
        COLORS.put("bleu", 0x1E88E5);
        COLORS.put("violet", 0x8E24AA);
        COLORS.put("rose", 0xEC407A);
        COLORS.put("blanc", 0xFFFFFF);
        COLORS.put("gris", 0x9E9E9E);
        COLORS.put("noir", 0x212121);
        COLORS.put("marron", 0x795548);
    }
    private static final int DEFAULT_COLOR = 0xFDD835;

    private static final SimpleCommandExceptionType BAD_COLOR = new SimpleCommandExceptionType(
            Component.literal("Couleur inconnue. Utilise : " + String.join(", ", COLORS.keySet()) + " ou un code hex RRGGBB"));
    private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(
            Component.literal("Ce point de repère n'existe pas."));
    private static final SimpleCommandExceptionType ALREADY = new SimpleCommandExceptionType(
            Component.literal("Un point de repère porte déjà ce nom."));
    private static final SimpleCommandExceptionType BAD_NAME = new SimpleCommandExceptionType(
            Component.literal("Nom invalide (1 à 32 caractères)."));

    private static final SuggestionProvider<CommandSourceStack> NAMES = (ctx, b) ->
            SharedSuggestionProvider.suggest(
                    WaypointSavedData.get(ctx.getSource().getServer()).all().stream()
                            .map(w -> StringArgumentType.escapeIfRequired(w.name())), b);

    private static final SuggestionProvider<CommandSourceStack> COLOR_SUGGEST = (ctx, b) ->
            SharedSuggestionProvider.suggest(COLORS.keySet(), b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("carte")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("generer")
                        .then(Commands.literal("rayon")
                                .then(Commands.argument("blocs", IntegerArgumentType.integer(16, 10000))
                                        .executes(c -> {
                                            int r = IntegerArgumentType.getInteger(c, "blocs");
                                            BlockPos p = BlockPos.containing(c.getSource().getPosition());
                                            return generate(c, p.getX() - r, p.getZ() - r, p.getX() + r, p.getZ() + r);
                                        })))
                        .then(Commands.literal("zone")
                                .then(Commands.argument("x1", IntegerArgumentType.integer())
                                        .then(Commands.argument("z1", IntegerArgumentType.integer())
                                                .then(Commands.argument("x2", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z2", IntegerArgumentType.integer())
                                                                .executes(c -> generate(c,
                                                                        IntegerArgumentType.getInteger(c, "x1"),
                                                                        IntegerArgumentType.getInteger(c, "z1"),
                                                                        IntegerArgumentType.getInteger(c, "x2"),
                                                                        IntegerArgumentType.getInteger(c, "z2"))))))))
                        .then(Commands.literal("statut").executes(c -> {
                            RenderJob j = ServerMapManager.currentJob();
                            c.getSource().sendSystemMessage(Component.literal(j == null ? "Aucune génération en cours."
                                    : "Génération : " + j.status()).withStyle(ChatFormatting.GRAY));
                            return 1;
                        }))
                        .then(Commands.literal("stop").executes(c -> {
                            boolean had = ServerMapManager.currentJob() != null;
                            ServerMapManager.stopJob();
                            ServerMapManager.saveAll();
                            c.getSource().sendSystemMessage(Component.literal(had ? "Génération arrêtée (le travail déjà fait est conservé)."
                                    : "Aucune génération en cours.").withStyle(ChatFormatting.YELLOW));
                            return 1;
                        })))
                .then(Commands.literal("point")
                        .then(Commands.literal("ajouter")
                                .then(Commands.argument("nom", StringArgumentType.string())
                                        .executes(c -> add(c, BlockPos.containing(c.getSource().getPosition()), DEFAULT_COLOR))
                                        .then(Commands.argument("couleur", StringArgumentType.word()).suggests(COLOR_SUGGEST)
                                                .executes(c -> add(c, BlockPos.containing(c.getSource().getPosition()), color(c))))))
                        .then(Commands.literal("placer")
                                .then(Commands.argument("nom", StringArgumentType.string())
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(c -> add(c, BlockPosArgument.getBlockPos(c, "pos"), DEFAULT_COLOR))
                                                .then(Commands.argument("couleur", StringArgumentType.word()).suggests(COLOR_SUGGEST)
                                                        .executes(c -> add(c, BlockPosArgument.getBlockPos(c, "pos"), color(c)))))))
                        .then(Commands.literal("deplacer")
                                .then(Commands.argument("nom", StringArgumentType.string()).suggests(NAMES)
                                        .executes(MapCommand::move)))
                        .then(Commands.literal("supprimer")
                                .then(Commands.argument("nom", StringArgumentType.string()).suggests(NAMES)
                                        .executes(MapCommand::remove)))
                        .then(Commands.literal("renommer")
                                .then(Commands.argument("nom", StringArgumentType.string()).suggests(NAMES)
                                        .then(Commands.argument("nouveau", StringArgumentType.string())
                                                .executes(MapCommand::rename))))
                        .then(Commands.literal("couleur")
                                .then(Commands.argument("nom", StringArgumentType.string()).suggests(NAMES)
                                        .then(Commands.argument("couleur", StringArgumentType.word()).suggests(COLOR_SUGGEST)
                                                .executes(MapCommand::recolor))))
                        .then(Commands.literal("liste").executes(MapCommand::list))
                        .then(Commands.literal("tp")
                                .then(Commands.argument("nom", StringArgumentType.string()).suggests(NAMES)
                                        .executes(MapCommand::tp)))));
    }

    private static int generate(CommandContext<CommandSourceStack> c, int x1, int z1, int x2, int z2) {
        CommandSourceStack src = c.getSource();
        RenderJob job = new RenderJob(src.getLevel(), src, x1, z1, x2, z2);
        if (job.total() > 2_000_000L) {
            src.sendFailure(Component.literal("Zone trop grande (" + job.total() + " chunks, max 2 000 000)."));
            return 0;
        }
        ServerMapManager.startJob(job);
        src.sendSystemMessage(Component.literal("Carte : génération lancée sur " + job.total()
                + " chunks dans " + src.getLevel().dimension().location()
                + ". Seuls les chunks déjà générés sont dessinés. /carte generer statut pour suivre.")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int color(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String s = StringArgumentType.getString(c, "couleur").toLowerCase(Locale.ROOT);
        Integer named = COLORS.get(s);
        if (named != null) return named;
        if (s.startsWith("#")) s = s.substring(1);
        if (s.matches("[0-9a-f]{6}")) return Integer.parseInt(s, 16);
        throw BAD_COLOR.create();
    }

    private static String name(CommandContext<CommandSourceStack> c, String arg) throws CommandSyntaxException {
        String n = StringArgumentType.getString(c, arg).trim();
        if (n.isEmpty() || n.length() > 32) throw BAD_NAME.create();
        return n;
    }

    private static Waypoint existing(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Waypoint w = WaypointSavedData.get(c.getSource().getServer()).get(StringArgumentType.getString(c, "nom"));
        if (w == null) throw UNKNOWN.create();
        return w;
    }

    private static MutableComponent label(Waypoint w) {
        return Component.literal(w.name()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(w.color())).withBold(true));
    }

    private static void changed(CommandSourceStack src) {
        NetworkHandler.syncAll(src.getServer());
    }

    private static int add(CommandContext<CommandSourceStack> c, BlockPos pos, int color) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        String n = name(c, "nom");
        WaypointSavedData data = WaypointSavedData.get(src.getServer());
        if (data.exists(n)) throw ALREADY.create();
        String dim = src.getLevel().dimension().location().toString();
        Waypoint w = new Waypoint(n, dim, pos.getX(), pos.getY(), pos.getZ(), color);
        data.put(w);
        changed(src);
        src.sendSystemMessage(Component.literal("Point de repère ").withStyle(ChatFormatting.GREEN)
                .append(label(w))
                .append(Component.literal(" ajouté en " + pos.getX() + " " + pos.getY() + " " + pos.getZ()).withStyle(ChatFormatting.GREEN)));
        return 1;
    }

    private static int move(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        Waypoint w = existing(c);
        BlockPos pos = BlockPos.containing(src.getPosition());
        Waypoint nw = w.withPos(src.getLevel().dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
        WaypointSavedData.get(src.getServer()).put(nw);
        changed(src);
        src.sendSystemMessage(Component.literal("Point ").withStyle(ChatFormatting.GREEN).append(label(nw))
                .append(Component.literal(" déplacé en " + pos.getX() + " " + pos.getY() + " " + pos.getZ()).withStyle(ChatFormatting.GREEN)));
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        Waypoint w = existing(c);
        WaypointSavedData.get(src.getServer()).remove(w.name());
        changed(src);
        src.sendSystemMessage(Component.literal("Point de repère supprimé : ").withStyle(ChatFormatting.YELLOW).append(label(w)));
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        Waypoint w = existing(c);
        String nn = name(c, "nouveau");
        WaypointSavedData data = WaypointSavedData.get(src.getServer());
        if (!nn.equalsIgnoreCase(w.name()) && data.exists(nn)) throw ALREADY.create();
        data.rename(w.name(), nn);
        changed(src);
        src.sendSystemMessage(Component.literal("Point renommé : " + w.name() + " → " + nn).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int recolor(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        Waypoint w = existing(c).withColor(color(c));
        WaypointSavedData.get(src.getServer()).put(w);
        changed(src);
        src.sendSystemMessage(Component.literal("Nouvelle couleur pour ").withStyle(ChatFormatting.GREEN).append(label(w)));
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        List<Waypoint> all = WaypointSavedData.get(src.getServer()).snapshot();
        if (all.isEmpty()) {
            src.sendSystemMessage(Component.literal("Aucun point de repère. /carte point ajouter \"<nom>\" [couleur]").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        src.sendSystemMessage(Component.literal("=== Points de repère (" + all.size() + ") ===").withStyle(ChatFormatting.GOLD));
        for (Waypoint w : all) {
            String tpCmd = "/carte point tp " + StringArgumentType.escapeIfRequired(w.name());
            MutableComponent line = Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(label(w))
                    .append(Component.literal("  " + w.x() + " " + w.y() + " " + w.z() + "  (" + w.dimension() + ")").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("  [TP]").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCmd))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Se téléporter")))));
            src.sendSystemMessage(line);
        }
        return all.size();
    }

    private static int tp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        ServerPlayer player = src.getPlayerOrException();
        Waypoint w = existing(c);
        ResourceLocation dimId = ResourceLocation.tryParse(w.dimension());
        ServerLevel level = dimId == null ? null
                : src.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        if (level == null) level = src.getServer().getLevel(Level.OVERWORLD);
        player.teleportTo(level, w.x() + 0.5, w.y(), w.z() + 0.5, player.getYRot(), player.getXRot());
        src.sendSystemMessage(Component.literal("Téléporté à ").withStyle(ChatFormatting.GREEN).append(label(w)));
        return 1;
    }
}
