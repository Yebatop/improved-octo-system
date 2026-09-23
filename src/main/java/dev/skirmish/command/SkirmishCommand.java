package dev.skirmish.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.skirmish.SkirmishClient;
import dev.skirmish.gui.SkirmishScreen;
import dev.skirmish.gui.WaypointListScreen;
import dev.skirmish.waypoint.Waypoint;
import dev.skirmish.waypoint.WaypointManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client-only command (executed by Fabric before anything is sent to the server):
 * <pre>
 * /skirmish                                   open the menu
 * /skirmish wp list | here [name] | remove &lt;id&gt; | select &lt;id&gt; | unselect
 * /skirmish wp add &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;dimension&gt; &lt;name...&gt;   (dimension may be quoted: "minecraft:the_nether")
 * </pre>
 * Chat ClickEvent.RunCommand("/skirmish wp add ...") therefore creates a waypoint without any packet.
 */
public final class SkirmishCommand {
    private SkirmishCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("skirmish")
                .executes(ctx -> {
                    SkirmishClient.openScreenNextTick(() -> new SkirmishScreen(null));
                    return 1;
                })
                .then(literal("menu").executes(ctx -> {
                    SkirmishClient.openScreenNextTick(() -> new SkirmishScreen(null));
                    return 1;
                }))
                .then(literal("theme").executes(ctx -> {
                    SkirmishClient.openScreenNextTick(() -> new dev.skirmish.gui.TokensScreen(null));
                    return 1;
                }))
                .then(literal("wp")
                        .executes(ctx -> {
                            SkirmishClient.openScreenNextTick(() -> new WaypointListScreen(null));
                            return 1;
                        })
                        .then(literal("list").executes(SkirmishCommand::list))
                        .then(literal("here")
                                .executes(ctx -> here(ctx, "Waypoint"))
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> here(ctx, StringArgumentType.getString(ctx, "name")))))
                        .then(literal("add")
                                .then(argument("x", DoubleArgumentType.doubleArg())
                                        .then(argument("y", DoubleArgumentType.doubleArg())
                                                .then(argument("z", DoubleArgumentType.doubleArg())
                                                        .then(argument("dimension", StringArgumentType.string())
                                                                .executes(ctx -> add(ctx, "Waypoint"))
                                                                .then(argument("name", StringArgumentType.greedyString())
                                                                        .executes(ctx -> add(ctx, StringArgumentType.getString(ctx, "name")))))))))
                        .then(literal("remove")
                                .then(argument("id", StringArgumentType.word()).executes(SkirmishCommand::remove)))
                        .then(literal("select")
                                .then(argument("id", StringArgumentType.word()).executes(SkirmishCommand::select)))
                        .then(literal("unselect").executes(ctx -> {
                            WaypointManager.get().select(null);
                            ctx.getSource().sendFeedback(Component.translatable("skirmish.waypoint.unselected"));
                            return 1;
                        }))));
    }

    private static int here(CommandContext<FabricClientCommandSource> ctx, String name) {
        Waypoint waypoint = WaypointManager.get().addHere(name);
        if (waypoint == null) {
            return 0;
        }
        ctx.getSource().sendFeedback(Component.translatable("skirmish.waypoint.added", waypoint.name(), waypoint.coordsText()));
        return 1;
    }

    private static int add(CommandContext<FabricClientCommandSource> ctx, String name) {
        double x = DoubleArgumentType.getDouble(ctx, "x");
        double y = DoubleArgumentType.getDouble(ctx, "y");
        double z = DoubleArgumentType.getDouble(ctx, "z");
        String dimension = StringArgumentType.getString(ctx, "dimension");
        if (!dimension.contains(":")) {
            dimension = "minecraft:" + dimension;
        }
        Waypoint waypoint = WaypointManager.get().add(name, x, y, z, dimension, "command");
        ctx.getSource().sendFeedback(Component.translatable("skirmish.waypoint.added", waypoint.name(), waypoint.coordsText()));
        return 1;
    }

    private static int remove(CommandContext<FabricClientCommandSource> ctx) {
        Waypoint waypoint = WaypointManager.get().byIdPrefix(StringArgumentType.getString(ctx, "id"));
        if (waypoint == null) {
            ctx.getSource().sendError(Component.translatable("skirmish.waypoint.not_found"));
            return 0;
        }
        WaypointManager.get().remove(waypoint.id());
        ctx.getSource().sendFeedback(Component.translatable("skirmish.waypoint.removed", waypoint.name()));
        return 1;
    }

    private static int select(CommandContext<FabricClientCommandSource> ctx) {
        Waypoint waypoint = WaypointManager.get().byIdPrefix(StringArgumentType.getString(ctx, "id"));
        if (waypoint == null) {
            ctx.getSource().sendError(Component.translatable("skirmish.waypoint.not_found"));
            return 0;
        }
        WaypointManager.get().select(waypoint.id());
        ctx.getSource().sendFeedback(Component.translatable("skirmish.waypoint.selected", waypoint.name()));
        return 1;
    }

    private static int list(CommandContext<FabricClientCommandSource> ctx) {
        List<Waypoint> waypoints = WaypointManager.get().currentServer();
        if (waypoints.isEmpty()) {
            ctx.getSource().sendFeedback(Component.translatable("skirmish.waypoints.empty"));
            return 0;
        }
        for (Waypoint w : waypoints) {
            String shortId = w.id().substring(0, 8);
            MutableComponent line = Component.literal("[" + shortId + "] ").withStyle(Style.EMPTY
                    .withColor(0xAAAAAA)
                    .withClickEvent(new ClickEvent.SuggestCommand("/skirmish wp select " + shortId))
                    .withHoverEvent(new HoverEvent.ShowText(Component.translatable("skirmish.waypoint.click_select"))));
            line.append(Component.literal(w.name()).withStyle(Style.EMPTY.withColor(w.color())))
                    .append(Component.literal("  " + w.coordsText() + "  " + w.dimension().replace("minecraft:", "")).withStyle(Style.EMPTY.withColor(0xAAAAAA)));
            ctx.getSource().sendFeedback(line);
        }
        return waypoints.size();
    }
}
