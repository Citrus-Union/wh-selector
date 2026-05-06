package dev.quanwup.whselector;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public class Selection {
    public BlockPos pos1;
    public BlockPos pos2;

    public boolean complete() {
        return pos1 != null && pos2 != null;
    }

    public BlockPos min() {
        return new BlockPos(
            Math.min(pos1.getX(), pos2.getX()),
            Math.min(pos1.getY(), pos2.getY()),
            Math.min(pos1.getZ(), pos2.getZ())
        );
    }

    public BlockPos max() {
        return new BlockPos(
            Math.max(pos1.getX(), pos2.getX()),
            Math.max(pos1.getY(), pos2.getY()),
            Math.max(pos1.getZ(), pos2.getZ())
        );
    }

    public long volume() {
        if (!complete()) return 0;
        BlockPos a = min(), b = max();
        return (long) (b.getX() - a.getX() + 1) * (b.getY() - a.getY() + 1) * (b.getZ() - a.getZ() + 1);
    }

    public Text describeDims() {
        if (!complete()) return Text.literal("");
        BlockPos a = min(), b = max();
        int dx = b.getX() - a.getX() + 1;
        int dy = b.getY() - a.getY() + 1;
        int dz = b.getZ() - a.getZ() + 1;
        return Text.literal("  (" + dx + "x" + dy + "x" + dz + " = " + volume() + ")")
            .formatted(Formatting.GRAY);
    }

    public void clear() {
        pos1 = null;
        pos2 = null;
    }
}
