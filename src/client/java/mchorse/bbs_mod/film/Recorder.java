package mchorse.bbs_mod.film;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.Inventory;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public class Recorder extends WorldFilmController
{
    public ReplayKeyframes keyframes = new ReplayKeyframes("keyframes");
    public FormProperties properties = new FormProperties("properties");
    public Inventory inventory = new Inventory("inventory");

    /**
     * Mobs captured within {@link Film#mobRecordingRadius} when recording started.
     * Their base attributes are recorded each tick and turned into replays on stop.
     */
    public final List<RecordedMob> mobs = new ArrayList<>();

    public float hp;
    public float hunger;
    public int xpLevel;
    public float xpProgress;

    private static Matrix4f perspective = new Matrix4f();

    public Form lastForm;
    public Vector3d lastPosition;
    public Vector4f lastRotation;

    public int countdown;
    public final int initialTick;

    public static void renderCameraPreview(Position position, Camera camera, MatrixStack stack)
    {
        if (!BBSSettings.recordingOverlays.get())
        {
            return;
        }

        Vector4f vector = Vectors.TEMP_4F;
        Matrix4f matrix = Matrices.TEMP_4F;
        float x = (float) (position.point.x - camera.getPos().x);
        float y = (float) (position.point.y - camera.getPos().y);
        float z = (float) (position.point.z - camera.getPos().z);
        float fov = MathUtils.toRad(position.angle.fov);
        float aspect = BBSRendering.getVideoWidth() / (float) BBSRendering.getVideoHeight();
        float thickness = 0.025F;

        perspective.identity().perspective(fov, aspect, 0.001F, 100F).invert();

        matrix.identity()
            .rotateY(MathUtils.toRad(position.angle.yaw + 180))
            .rotateX(MathUtils.toRad(-position.angle.pitch));

        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        transformFrustum(vector, matrix, 1F, 1F);
        Draw.fillBoxTo(builder, stack, x, y, z, x + vector.x, y + vector.y, z + vector.z, thickness, 1F, 1F, 1F, 1F);

        transformFrustum(vector, matrix, -1F, 1F);
        Draw.fillBoxTo(builder, stack, x, y, z, x + vector.x, y + vector.y, z + vector.z, thickness, 1F, 1F, 1F, 1F);

        transformFrustum(vector, matrix, 1F, -1F);
        Draw.fillBoxTo(builder, stack, x, y, z, x + vector.x, y + vector.y, z + vector.z, thickness, 1F, 1F, 1F, 1F);

        transformFrustum(vector, matrix, -1F, -1F);
        Draw.fillBoxTo(builder, stack, x, y, z, x + vector.x, y + vector.y, z + vector.z, thickness, 1F, 1F, 1F, 1F);

        transformFrustum(vector, matrix, 0F, 0F);
        Draw.fillBoxTo(builder, stack, x, y, z, x + vector.x, y + vector.y, z + vector.z, thickness, 0F, 0.5F, 1F, 1F);

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.disableDepthTest();
    }

    private static void transformFrustum(Vector4f vector, Matrix4f matrix, float x, float y)
    {
        vector.set(x, y, 0F, 1F);
        vector.mul(perspective);
        vector.w = 1F;
        vector.normalize().mul(100F);
        vector.w = 1F;
        vector.mul(matrix);
    }

    public Recorder(Film film, Form form, int replayId, int tick)
    {
        super(film);

        this.lastForm = FormUtils.copy(form);
        this.exception = replayId;
        this.tick = tick;
        this.countdown = TimeUtils.toTick(BBSSettings.recordingCountdown.get());
        this.initialTick = tick;
    }

    public boolean hasNotStarted()
    {
        return this.countdown > 0;
    }

    public void update()
    {
        if (this.hasNotStarted())
        {
            this.countdown -= 1;

            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (this.lastPosition == null)
        {
            this.lastPosition = new Vector3d(player.getX(), player.getY(), player.getZ());
            this.lastRotation = new Vector4f(player.getYaw(), player.getPitch(), player.getHeadYaw(), player.getBodyYaw());
            this.inventory.fromPlayer(player);

            this.hp = player.getHealth();
            this.hunger = player.getHungerManager().getFoodLevel();
            this.xpLevel = player.experienceLevel;
            this.xpProgress = player.experienceProgress;

            this.captureMobs(player);
        }

        if (this.tick >= 0)
        {
            Morph morph = Morph.getMorph(player);

            this.keyframes.record(this.tick, morph.entity, null);
            this.recordMobs();
        }

        super.update();
    }

    /**
     * Snapshot every living entity (except the recording player) within
     * {@link Film#mobRecordingRadius} into {@link #mobs}. A radius of {@code 0} disables it.
     */
    private void captureMobs(ClientPlayerEntity player)
    {
        float radius = this.film.mobRecordingRadius.get();

        if (radius <= 0)
        {
            return;
        }

        Box box = player.getBoundingBox().expand(radius);
        double radiusSq = radius * radius;

        for (LivingEntity entity : player.getWorld().getEntitiesByClass(LivingEntity.class, box, (e) -> e != player && e.isAlive() && e.squaredDistanceTo(player) <= radiusSq))
        {
            MobForm form = Morph.createMobForm(entity);

            if (form != null)
            {
                this.mobs.add(new RecordedMob(form, entity));
            }
        }
    }

    private void recordMobs()
    {
        for (RecordedMob mob : this.mobs)
        {
            if (mob.entity.getMcEntity().isAlive())
            {
                mob.keyframes.record(this.tick, mob.entity, null);
            }
        }
    }

    /**
     * A mob captured at recording start: its snapshotted {@link MobForm} plus the
     * keyframes recorded each tick from the live entity it wraps.
     */
    public static class RecordedMob
    {
        public final MobForm form;
        public final MCEntity entity;
        public final ReplayKeyframes keyframes = new ReplayKeyframes("keyframes");

        public RecordedMob(MobForm form, Entity mcEntity)
        {
            this.form = form;
            this.entity = new MCEntity(mcEntity);
        }
    }

    public void render(WorldRenderContext context)
    {
        super.render(context);

        renderCameraPreview(this.position, context.camera(), context.matrixStack());
    }

    @Override
    public void shutdown()
    {
        Vector3d pos = this.lastPosition;

        if (pos != null)
        {
            Vector4f rot = this.lastRotation;

            PlayerUtils.teleport(pos.x, pos.y, pos.z, rot.z, rot.y);
            ClientNetwork.sendPlayerForm(this.lastForm);
        }

        super.shutdown();
    }
}
