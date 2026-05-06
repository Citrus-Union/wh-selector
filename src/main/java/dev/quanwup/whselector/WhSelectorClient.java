package dev.quanwup.whselector;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhSelectorClient implements ClientModInitializer {
    public static final String MOD_ID = "wh-selector";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Selection SEL = new Selection();
    public static final Config CONFIG = Config.load();
    private static BlockPos lastAttackPos = null;
    private static long lastAttackMs = 0L;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Warehouse Selector client init, json={}", CONFIG.jsonFile);

        // Gold axe left-click => pos1.
        // Return FAIL to fully cancel: no vanilla break logic, no dig packet to server.
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClient()) return ActionResult.PASS;
            if (!isGoldAxe(player)) return ActionResult.PASS;
            if (hand != net.minecraft.util.Hand.MAIN_HAND) return ActionResult.FAIL;
            // Copy the pos (mutable on the interaction path).
            BlockPos immutable = pos.toImmutable();
            long now = System.currentTimeMillis();
            if (immutable.equals(lastAttackPos) && now - lastAttackMs < 250) {
                return ActionResult.FAIL;
            }
            lastAttackPos = immutable;
            lastAttackMs = now;
            SEL.pos1 = immutable;
            notifyClient(Text.literal("pos1 = ").formatted(Formatting.AQUA)
                .append(fmtPos(immutable)).append(SEL.describeDims()));
            return ActionResult.FAIL;
        });

        // Gold axe right-click on block => pos2.
        // Return FAIL so the PlayerInteractBlockC2S packet is NOT sent (chest won't open).
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient()) return ActionResult.PASS;
            if (!isGoldAxe(player)) return ActionResult.PASS;
            // Only handle main hand to avoid double-firing.
            if (hand != net.minecraft.util.Hand.MAIN_HAND) return ActionResult.FAIL;
            BlockPos pos = hitResult.getBlockPos().toImmutable();
            SEL.pos2 = pos;
            notifyClient(Text.literal("pos2 = ").formatted(Formatting.AQUA)
                .append(fmtPos(pos)).append(SEL.describeDims()));
            return ActionResult.FAIL;
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            WhCommand.register(dispatcher);
        });

        // Highlight registered chests in-world.
        ChestHighlightRenderer.register();
        // Best-effort initial load; also triggered after add/rm and via /wh reload.
        RegisteredChests.reloadAsync();
    }

    private static boolean isGoldAxe(net.minecraft.entity.player.PlayerEntity p) {
        return p != null && p.getMainHandStack() != null
            && p.getMainHandStack().getItem() == Items.GOLDEN_AXE;
    }

    public static Text fmtPos(BlockPos p) {
        return Text.literal("[" + p.getX() + ", " + p.getY() + ", " + p.getZ() + "]")
            .formatted(Formatting.YELLOW);
    }

    public static void notifyClient(Text msg) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("[WH] ").formatted(Formatting.GOLD).append(msg), false);
        }
    }
}
