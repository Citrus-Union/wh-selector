package dev.quanwup.whselector;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws wireframe boxes around all containers registered in warehouse-agent's data,
 * for the current dimension, within a configurable range around the camera.
 */
public class ChestHighlightRenderer {
    public static volatile boolean enabled = true;
    /** Max distance from camera (blocks) at which to draw. Beyond this, boxes are culled. */
    public static volatile double renderDistance = 96.0;

    // Normal containers — cyan-ish, fully opaque.
    private static final float R = 0.20f, G = 0.95f, B = 1.00f, A = 1.00f;
    private static final RenderPipeline X_RAY_LINES_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation("pipeline/wh_selector_xray_lines")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build()
    );
    private static final RenderLayer X_RAY_LINES = RenderLayer.of(
        "wh_selector_xray_lines",
        1536,
        false,
        false,
        X_RAY_LINES_PIPELINE,
        RenderLayer.MultiPhaseParameters.builder()
            .lineWidth(new RenderPhase.LineWidth(java.util.OptionalDouble.empty()))
            .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
            .target(RenderPhase.ITEM_ENTITY_TARGET)
            .build(false)
    );

    public static void register() {
        // AFTER_ENTITIES: terrain + entities are already drawn. We draw lines on an Immediate
        // buffer we control and explicitly flush, because the default entity consumers
        // provider may not have a buffer for RenderLayer.getLines() and never flushes it.
        WorldRenderEvents.AFTER_ENTITIES.register(ChestHighlightRenderer::onRender);
    }

    /** Incremented each frame a render happens; inspect with /wh debug. */
    public static volatile long lastRenderTick = 0;
    public static volatile int lastDrawn = 0;

    private static void onRender(WorldRenderContext ctx) {
        if (!enabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        String dim = mc.world.getRegistryKey().getValue().toString();
        List<RegisteredChests.Entry> entries = RegisteredChests.forDim(dim);
        lastRenderTick = System.currentTimeMillis();
        lastDrawn = 0;
        if (entries.isEmpty()) return;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cam = camera.getPos();
        double maxSq = renderDistance * renderDistance;

        MatrixStack ms = ctx.matrices();
        if (ms == null) return;

        // Get the entity consumers (Immediate) so we can explicitly flush the LINES layer.
        BufferBuilderStorage storage = mc.getBufferBuilders();
        VertexConsumerProvider.Immediate immediate = storage.getEntityVertexConsumers();
        VertexConsumer buf = immediate.getBuffer(X_RAY_LINES);

        ms.push();
        // Translate world-space positions into camera-relative space.
        ms.translate(-cam.x, -cam.y, -cam.z);

        MatrixStack.Entry entry = ms.peek();
        Matrix4f mat = entry.getPositionMatrix();

        int drawn = 0;
        for (RegisteredChests.Entry e : entries) {
            BlockPos p = e.pos();
            double dx = p.getX() + 0.5 - cam.x;
            double dy = p.getY() + 0.5 - cam.y;
            double dz = p.getZ() + 0.5 - cam.z;
            if (dx * dx + dy * dy + dz * dz > maxSq) continue;

            // Slight outward inflation to avoid z-fighting with block faces.
            float x0 = p.getX() - 0.002f;
            float y0 = p.getY() - 0.002f;
            float z0 = p.getZ() - 0.002f;
            float x1 = p.getX() + 1f + 0.002f;
            float y1 = p.getY() + 1f + 0.002f;
            float z1 = p.getZ() + 1f + 0.002f;

            drawBox(buf, entry, mat, x0, y0, z0, x1, y1, z1, R, G, B, A);
            drawn++;
        }

        ms.pop();

        // Explicitly flush the LINES layer so the vertices actually get drawn.
        immediate.draw(X_RAY_LINES);
        lastDrawn = drawn;
    }

    /** Emit 12 line segments for a box in LINES layer. */
    private static void drawBox(VertexConsumer buf, MatrixStack.Entry entry, Matrix4f mat,
                                float x0, float y0, float z0, float x1, float y1, float z1,
                                float r, float g, float b, float a) {
        // Bottom rectangle (y0)
        line(buf, entry, mat, x0, y0, z0, x1, y0, z0, 1, 0, 0, r, g, b, a);
        line(buf, entry, mat, x1, y0, z0, x1, y0, z1, 0, 0, 1, r, g, b, a);
        line(buf, entry, mat, x1, y0, z1, x0, y0, z1, -1, 0, 0, r, g, b, a);
        line(buf, entry, mat, x0, y0, z1, x0, y0, z0, 0, 0, -1, r, g, b, a);
        // Top rectangle (y1)
        line(buf, entry, mat, x0, y1, z0, x1, y1, z0, 1, 0, 0, r, g, b, a);
        line(buf, entry, mat, x1, y1, z0, x1, y1, z1, 0, 0, 1, r, g, b, a);
        line(buf, entry, mat, x1, y1, z1, x0, y1, z1, -1, 0, 0, r, g, b, a);
        line(buf, entry, mat, x0, y1, z1, x0, y1, z0, 0, 0, -1, r, g, b, a);
        // Vertical edges
        line(buf, entry, mat, x0, y0, z0, x0, y1, z0, 0, 1, 0, r, g, b, a);
        line(buf, entry, mat, x1, y0, z0, x1, y1, z0, 0, 1, 0, r, g, b, a);
        line(buf, entry, mat, x1, y0, z1, x1, y1, z1, 0, 1, 0, r, g, b, a);
        line(buf, entry, mat, x0, y0, z1, x0, y1, z1, 0, 1, 0, r, g, b, a);
    }

    private static void line(VertexConsumer buf, MatrixStack.Entry entry, Matrix4f mat,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float nx, float ny, float nz,
                             float r, float g, float b, float a) {
        buf.vertex(mat, x1, y1, z1).color(r, g, b, a).normal(entry, nx, ny, nz);
        buf.vertex(mat, x2, y2, z2).color(r, g, b, a).normal(entry, nx, ny, nz);
    }
}
