package mchorse.bbs_mod.ui.film.controller;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.Arrays;
import java.util.TreeSet;

/**
 * The selected replay's world-space trajectory drawn into the film viewport: a
 * polyline sampled from the position channels ({@link Replay#keyframes}) with a
 * marker on every keyed tick — the same idea as Blender's motion paths, a
 * read-only overlay that shows the shape of the movement.
 *
 * <p>It draws in the world / 3D pass like {@link UIFilmController}'s orbit
 * centre marker (camera-relative, depth disabled so the whole path stays
 * visible through the model). The curve is a camera-facing ribbon (one flat
 * quad per segment, widened perpendicular to the segment and to the view ray)
 * and the markers are small axis-aligned cubes — both go straight through the
 * {@code position_color} program. A ribbon is used instead of a GL line
 * (clamped to 1px in the core profile) or a rotated box (the box's orientation
 * maths mis-aimed it).
 */
public class MotionPath
{
    private static final float LINE_HALF_WIDTH = 0.03F;
    private static final float MARKER_RADIUS = 0.06F;

    public static void render(WorldRenderContext context, Replay replay)
    {
        if (replay == null || replay.relative.get())
        {
            return;
        }

        KeyframeChannel<Double> x = replay.keyframes.x;
        KeyframeChannel<Double> y = replay.keyframes.y;
        KeyframeChannel<Double> z = replay.keyframes.z;

        if (x.isEmpty() && y.isEmpty() && z.isEmpty())
        {
            return;
        }

        float first = Float.MAX_VALUE;
        float last = -Float.MAX_VALUE;

        for (KeyframeChannel<Double> channel : Arrays.asList(x, y, z))
        {
            if (!channel.isEmpty())
            {
                first = Math.min(first, channel.get(0).getTick());
                last = Math.max(last, channel.get(channel.getKeyframes().size() - 1).getTick());
            }
        }

        Camera camera = context.camera();
        MatrixStack stack = context.matrixStack();

        /* The world is rendered camera-relative, so the points are subtracted
         * from the camera here, in double precision: feeding raw world
         * coordinates into the float matrix collapses each short per-tick
         * segment into rounding noise (the same convention as the orbit centre
         * marker and the recording overlays). */
        double cx = camera.getPos().x;
        double cy = camera.getPos().y;
        double cz = camera.getPos().z;

        Matrix4f matrix = stack.peek().getPositionMatrix();

        int color = BBSSettings.primaryColor.get();
        float r = Colors.getR(color);
        float g = Colors.getG(color);
        float b = Colors.getB(color);

        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        /* The interpolated curve: a camera-facing ribbon, one quad per tick (so
         * beziers read smooth), the exact endpoints kept regardless of a
         * fractional range. */
        Vector3d prev = sample(replay, first, cx, cy, cz, new Vector3d());

        for (float tick = first; tick < last; )
        {
            tick = Math.min(tick + 1F, last);

            Vector3d current = sample(replay, tick, cx, cy, cz, new Vector3d());

            ribbon(builder, matrix, prev, current, r, g, b);

            prev = current;
        }

        /* A marker on every keyed tick across the three channels. */
        TreeSet<Float> ticks = new TreeSet<>();

        collectTicks(ticks, x);
        collectTicks(ticks, y);
        collectTicks(ticks, z);

        for (float tick : ticks)
        {
            Vector3d point = sample(replay, tick, cx, cy, cz, prev);

            float half = MARKER_RADIUS * BBSSettings.getAxesDistanceScale((float) point.length());

            Draw.fillBox(builder, stack, (float) point.x - half, (float) point.y - half, (float) point.z - half, (float) point.x + half, (float) point.y + half, (float) point.z + half, Colors.WHITE);
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    /**
     * One flat quad from {@code a} to {@code b}, widened along the vector
     * perpendicular to both the segment and the view ray (the segment midpoint,
     * since the coordinates are camera-relative), so the ribbon always faces the
     * camera. Its width is distance-scaled to stay a constant on-screen size.
     */
    private static void ribbon(BufferBuilder builder, Matrix4f matrix, Vector3d a, Vector3d b, float red, float green, float blue)
    {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;

        double mx = (a.x + b.x) * 0.5;
        double my = (a.y + b.y) * 0.5;
        double mz = (a.z + b.z) * 0.5;

        /* side = segment x view ray */
        double sx = dy * mz - dz * my;
        double sy = dz * mx - dx * mz;
        double sz = dx * my - dy * mx;
        double length = Math.sqrt(sx * sx + sy * sy + sz * sz);

        if (length < 1.0E-6)
        {
            return;
        }

        double half = LINE_HALF_WIDTH * BBSSettings.getAxesDistanceScale((float) Math.sqrt(mx * mx + my * my + mz * mz)) / length;

        sx *= half;
        sy *= half;
        sz *= half;

        float ax1 = (float) (a.x + sx), ay1 = (float) (a.y + sy), az1 = (float) (a.z + sz);
        float ax2 = (float) (a.x - sx), ay2 = (float) (a.y - sy), az2 = (float) (a.z - sz);
        float bx1 = (float) (b.x + sx), by1 = (float) (b.y + sy), bz1 = (float) (b.z + sz);
        float bx2 = (float) (b.x - sx), by2 = (float) (b.y - sy), bz2 = (float) (b.z - sz);

        builder.vertex(matrix, ax1, ay1, az1).color(red, green, blue, 1F).next();
        builder.vertex(matrix, ax2, ay2, az2).color(red, green, blue, 1F).next();
        builder.vertex(matrix, bx2, by2, bz2).color(red, green, blue, 1F).next();

        builder.vertex(matrix, ax1, ay1, az1).color(red, green, blue, 1F).next();
        builder.vertex(matrix, bx2, by2, bz2).color(red, green, blue, 1F).next();
        builder.vertex(matrix, bx1, by1, bz1).color(red, green, blue, 1F).next();
    }

    private static void collectTicks(TreeSet<Float> ticks, KeyframeChannel<Double> channel)
    {
        for (Keyframe<Double> keyframe : channel.getKeyframes())
        {
            ticks.add(keyframe.getTick());
        }
    }

    private static Vector3d sample(Replay replay, float tick, double cx, double cy, double cz, Vector3d out)
    {
        return out.set(
            replay.keyframes.x.interpolate(tick) - cx,
            replay.keyframes.y.interpolate(tick) - cy,
            replay.keyframes.z.interpolate(tick) - cz
        );
    }
}
