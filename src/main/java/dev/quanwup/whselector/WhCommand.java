package dev.quanwup.whselector;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public class WhCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("wh")
            .then(ClientCommandManager.literal("sel").executes(WhCommand::showSel))
            .then(ClientCommandManager.literal("clear").executes(WhCommand::clearSel))
            .then(ClientCommandManager.literal("add").executes(WhCommand::add))
            .then(ClientCommandManager.literal("rm").executes(WhCommand::remove))
            .then(ClientCommandManager.literal("file")
                .executes(WhCommand::showFile)
                .then(ClientCommandManager.argument("path", StringArgumentType.greedyString())
                    .executes(WhCommand::setFile)))
            .then(ClientCommandManager.literal("count").executes(WhCommand::count))
            .then(ClientCommandManager.literal("show").executes(WhCommand::show))
            .then(ClientCommandManager.literal("hide").executes(WhCommand::hide))
            .then(ClientCommandManager.literal("reload").executes(WhCommand::reload))
            .then(ClientCommandManager.literal("range")
                .then(ClientCommandManager.argument("blocks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(8, 512))
                    .executes(WhCommand::setRange)))
            .then(ClientCommandManager.literal("debug").executes(WhCommand::debug))
            .then(ClientCommandManager.literal("help").executes(WhCommand::help))
            .executes(WhCommand::help));
    }

    private static int help(CommandContext<FabricClientCommandSource> ctx) {
        send(ctx, Text.literal("Warehouse Selector 命令:").formatted(Formatting.GOLD));
        send(ctx, Text.literal("  金斧头左键 = 设置 pos1").formatted(Formatting.GRAY));
        send(ctx, Text.literal("  金斧头右键 = 设置 pos2").formatted(Formatting.GRAY));
        send(ctx, Text.literal("  /wh sel         查看当前选区").formatted(Formatting.AQUA));
        send(ctx, Text.literal("  /wh clear       清空选区").formatted(Formatting.AQUA));
        send(ctx, Text.literal("  /wh file <path> 选择要读写的容器 JSON 文件").formatted(Formatting.AQUA));
        send(ctx, Text.literal("  /wh file        查看当前 JSON 文件").formatted(Formatting.AQUA));
        send(ctx, Text.literal("  /wh add         将选区内容器加入 JSON").formatted(Formatting.AQUA));
        send(ctx, Text.literal("  /wh rm          将选区内已记录容器移除").formatted(Formatting.AQUA));
        send(ctx, Text.literal("  /wh count       显示已记录容器数量").formatted(Formatting.AQUA));
        send(ctx, Text.literal("  /wh show | hide 开关已记录容器高亮").formatted(Formatting.AQUA));
        send(ctx, Text.literal("  /wh reload      重新从 JSON 文件读取容器列表").formatted(Formatting.AQUA));
        send(ctx, Text.literal("  /wh range <N>   高亮渲染距离（默认 96）").formatted(Formatting.AQUA));
        return 1;
    }

    private static int show(CommandContext<FabricClientCommandSource> ctx) {
        ChestHighlightRenderer.enabled = true;
        RegisteredChests.reloadAsync();
        send(ctx, Text.literal("已开启高亮（" + RegisteredChests.total() + " 个容器，范围 "
            + (int) ChestHighlightRenderer.renderDistance + "m）").formatted(Formatting.GREEN));
        return 1;
    }

    private static int hide(CommandContext<FabricClientCommandSource> ctx) {
        ChestHighlightRenderer.enabled = false;
        send(ctx, Text.literal("已关闭高亮").formatted(Formatting.YELLOW));
        return 1;
    }

    private static int reload(CommandContext<FabricClientCommandSource> ctx) {
        new Thread(() -> {
            try {
                RegisteredChests.reload();
                MinecraftClient.getInstance().execute(() -> send(ctx, Text.literal("[" + RegisteredChests.mode()
                    + "] 已加载 " + RegisteredChests.total() + " 个容器").formatted(Formatting.GREEN)));
            } catch (Exception e) {
                MinecraftClient.getInstance().execute(() ->
                    send(ctx, Text.literal("reload error: " + e.getMessage()).formatted(Formatting.RED)));
            }
        }, "wh-reload").start();
        return 1;
    }

    private static int setRange(CommandContext<FabricClientCommandSource> ctx) {
        int n = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "blocks");
        ChestHighlightRenderer.renderDistance = n;
        send(ctx, Text.literal("高亮渲染距离 = " + n + "m").formatted(Formatting.GREEN));
        return 1;
    }

    private static int debug(CommandContext<FabricClientCommandSource> ctx) {
        Config cfg = WhSelectorClient.CONFIG;
        send(ctx, Text.literal("jsonFile=" + (cfg.hasJsonFile() ? cfg.jsonFile : "(未设置)")
            + "  source=" + RegisteredChests.mode()
            + "  total=" + RegisteredChests.total()
            + "  age=" + RegisteredChests.ageSeconds() + "s").formatted(Formatting.GOLD));
        String dim = currentDim();
        send(ctx, Text.literal("currentDim=" + dim
            + "  entriesForDim=" + RegisteredChests.forDim(dim).size()).formatted(Formatting.GRAY));
        for (var e : RegisteredChests.getAll().entrySet()) {
            send(ctx, Text.literal("  dim[" + e.getKey() + "] = " + e.getValue().size()).formatted(Formatting.GRAY));
        }
        send(ctx, Text.literal("renderer: enabled=" + ChestHighlightRenderer.enabled
            + "  range=" + (int) ChestHighlightRenderer.renderDistance
            + "  lastDrawn=" + ChestHighlightRenderer.lastDrawn
            + "  lastRenderAgoMs=" + (ChestHighlightRenderer.lastRenderTick == 0 ? -1 : (System.currentTimeMillis() - ChestHighlightRenderer.lastRenderTick))
            ).formatted(Formatting.AQUA));
        return 1;
    }

    private static int showSel(CommandContext<FabricClientCommandSource> ctx) {
        Selection s = WhSelectorClient.SEL;
        if (s.pos1 == null && s.pos2 == null) {
            send(ctx, Text.literal("未选点。手持金斧头左/右键方块选点。").formatted(Formatting.YELLOW));
            return 0;
        }
        send(ctx, Text.literal("pos1: ").append(s.pos1 == null ? Text.literal("(未设)").formatted(Formatting.DARK_GRAY) : WhSelectorClient.fmtPos(s.pos1)));
        send(ctx, Text.literal("pos2: ").append(s.pos2 == null ? Text.literal("(未设)").formatted(Formatting.DARK_GRAY) : WhSelectorClient.fmtPos(s.pos2)));
        if (s.complete()) {
            send(ctx, Text.literal("范围: ").append(WhSelectorClient.fmtPos(s.min())).append("  ").append(WhSelectorClient.fmtPos(s.max())).append(s.describeDims()));
        }
        return 1;
    }

    private static int clearSel(CommandContext<FabricClientCommandSource> ctx) {
        WhSelectorClient.SEL.clear();
        send(ctx, Text.literal("已清空选区").formatted(Formatting.YELLOW));
        return 1;
    }

    private static int add(CommandContext<FabricClientCommandSource> ctx) {
        Selection s = WhSelectorClient.SEL;
        if (!s.complete()) {
            send(ctx, Text.literal("请先用金斧选好 pos1 和 pos2").formatted(Formatting.RED));
            return 0;
        }
        Config cfg = WhSelectorClient.CONFIG;
        if (!cfg.hasJsonFile()) {
            send(ctx, Text.literal("请先使用 /wh file <path> 选择 JSON 文件").formatted(Formatting.RED));
            return 0;
        }
        String dim = currentDim();
        BlockPos a = s.min(), b = s.max();
        send(ctx, Text.literal("[file] 添加选区容器 " + a.toShortString() + "  " + b.toShortString()).formatted(Formatting.GRAY));
        try {
            FileStore store = new FileStore(cfg.jsonFile);
            FileStore.AddResult r = store.addFromClient(a, b, java.util.Set.of(), dim);
            send(ctx, Text.literal(String.format("[file] 新增 %d 条，扫描到 %d 个容器", r.added(), r.scanned())).formatted(Formatting.GREEN));
            RegisteredChests.reloadAsync();
        } catch (Exception e) {
            send(ctx, Text.literal("[file] error: " + e.getMessage()).formatted(Formatting.RED));
        }
        return 1;
    }

    private static int remove(CommandContext<FabricClientCommandSource> ctx) {
        Selection s = WhSelectorClient.SEL;
        if (!s.complete()) {
            send(ctx, Text.literal("请先用金斧选好 pos1 和 pos2").formatted(Formatting.RED));
            return 0;
        }
        Config cfg = WhSelectorClient.CONFIG;
        if (!cfg.hasJsonFile()) {
            send(ctx, Text.literal("请先使用 /wh file <path> 选择 JSON 文件").formatted(Formatting.RED));
            return 0;
        }
        String dim = currentDim();
        BlockPos a = s.min(), b = s.max();
        send(ctx, Text.literal("[file] 从 JSON 移除选区容器 " + a.toShortString() + "  " + b.toShortString()).formatted(Formatting.GRAY));
        try {
            int removed = new FileStore(cfg.jsonFile).removeInAabb(a, b, dim);
            send(ctx, Text.literal("[file] 移除 " + removed + " 条").formatted(Formatting.GREEN));
            RegisteredChests.reloadAsync();
        } catch (Exception e) {
            send(ctx, Text.literal("[file] error: " + e.getMessage()).formatted(Formatting.RED));
        }
        return 1;
    }

    private static int showFile(CommandContext<FabricClientCommandSource> ctx) {
        Config cfg = WhSelectorClient.CONFIG;
        if (cfg.hasJsonFile()) {
            send(ctx, Text.literal("当前 JSON 文件 = " + cfg.jsonFile).formatted(Formatting.AQUA));
        } else {
            send(ctx, Text.literal("未设置 JSON 文件，请使用 /wh file <path>").formatted(Formatting.YELLOW));
        }
        return 1;
    }

    private static int setFile(CommandContext<FabricClientCommandSource> ctx) {
        String path = StringArgumentType.getString(ctx, "path").trim();
        if (path.equals("-") || path.equalsIgnoreCase("clear") || path.equalsIgnoreCase("none")) path = "";
        WhSelectorClient.CONFIG.jsonFile = path;
        WhSelectorClient.CONFIG.save();
        if (path.isEmpty()) {
            send(ctx, Text.literal("已清除 JSON 文件路径").formatted(Formatting.GREEN));
        } else {
            send(ctx, Text.literal("JSON 文件已设为 " + path).formatted(Formatting.GREEN));
            RegisteredChests.reloadAsync();
        }
        return 1;
    }

    private static int count(CommandContext<FabricClientCommandSource> ctx) {
        Config cfg = WhSelectorClient.CONFIG;
        if (!cfg.hasJsonFile()) {
            send(ctx, Text.literal("请先使用 /wh file <path> 选择 JSON 文件").formatted(Formatting.RED));
            return 0;
        }
        try {
            int n = new FileStore(cfg.jsonFile).count();
            send(ctx, Text.literal("[file] 已记录容器 " + n + " 个").formatted(Formatting.AQUA));
        } catch (Exception e) {
            send(ctx, Text.literal("[file] error: " + e.getMessage()).formatted(Formatting.RED));
        }
        return 1;
    }

    private static String currentDim() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return "minecraft:overworld";
        return mc.world.getRegistryKey().getValue().toString();
    }

    private static void send(CommandContext<FabricClientCommandSource> ctx, Text msg) {
        ctx.getSource().sendFeedback(Text.literal("[WH] ").formatted(Formatting.GOLD).append(msg));
    }
}
