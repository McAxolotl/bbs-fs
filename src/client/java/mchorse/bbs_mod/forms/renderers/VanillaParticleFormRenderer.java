package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.graphics.InverseView;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.StringReader;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.forms.forms.utils.ParticleSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.command.argument.ParticleEffectArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class VanillaParticleFormRenderer extends FormRenderer<VanillaParticleForm> implements ITickable
{
    public static final Link PARTICLE_PREVIEW = new Link("minecraft", "textures/particle/flame.png");

    private Vector3d pos = new Vector3d();
    private Vector3f vel = new Vector3f();
    private Matrix3f rot = new Matrix3f();
    private int tick;

    public VanillaParticleFormRenderer(VanillaParticleForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        Texture texture = context.render.getTextures().getTexture(PARTICLE_PREVIEW);

        float min = Math.min(texture.width, texture.height);
        int ow = (x2 - x1) - 4;
        int oh = (y2 - y1) - 4;

        int w = (int) ((texture.width / min) * ow);
        int h = (int) ((texture.height / min) * ow);

        int x = x1 + (ow - w) / 2 + 2;
        int y = y1 + (oh - h) / 2 + 2;

        context.batcher.fullTexturedBox(texture, x, y, w, h);
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        super.render3D(context);

        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Matrix4f matrix = new Matrix4f(InverseView.get());

        /* Since 1.21.1 the world render keeps the camera view in RenderSystem's global
         * model-view and gives the stack an identity base, so stack.peek() no longer
         * carries the view rotation that InverseView is meant to cancel. Fold the global
         * model-view back in (identity, hence a no-op, in the form editor where the camera
         * lives in the stack) so the emitter's world position and direction come out right. */
        matrix.mul(RenderSystem.getModelViewMatrix());
        matrix.mul(context.stack.peek().getPositionMatrix());

        Vector3d translation = new Vector3d(matrix.getTranslation(Vectors.TEMP_3F));

        translation.add(camera.getPos().x, camera.getPos().y, camera.getPos().z);
        context.stack.push();
        context.stack.loadIdentity();
        context.stack.multiplyPositionMatrix(new Matrix4f(InverseView.get()).invert());

        this.pos.set(translation);
        this.vel.set(0F, 0F, 1F);
        this.rot.set(matrix).transform(this.vel);

        context.stack.pop();
    }

    @Override
    public void tick(IEntity entity)
    {
        World world = entity.getWorld();
        boolean paused = this.form.paused.get();
        Vector3f temp3f = new Vector3f();

        if (world != null && !paused)
        {
            float velocity = this.form.velocity.get();
            int count = this.form.count.get();
            int frequency = this.form.frequency.get();

            if (this.tick <= 0)
            {
                Matrix3f m = Matrices.TEMP_3F;
                Vector3f v = Vectors.TEMP_3F;
                ParticleSettings settings = this.form.settings.get();
                ParticleEffect effect = createEffect(settings, world);

                for (int i = 0; i < count; i++)
                {
                    float velocityX = this.vel.x * velocity;
                    float velocityY = this.vel.y * velocity;
                    float velocityZ = this.vel.z * velocity;
                    float sh = MathUtils.toRad(this.form.scatteringYaw.get()) * (float) (Math.random() - 0.5D);
                    float sv = MathUtils.toRad(this.form.scatteringPitch.get()) * (float) (Math.random() - 0.5D);

                    m.identity()
                        .rotateY(sh)
                        .rotateX(sv)
                        .transform(v.set(velocityX, velocityY, velocityZ));

                    temp3f.set(
                        (Math.random() * 2F - 1F) * this.form.offsetX.get(),
                        (Math.random() * 2F - 1F) * this.form.offsetY.get(),
                        (Math.random() * 2F - 1F) * this.form.offsetZ.get()
                    );

                    if (this.form.local.get())
                    {
                        this.rot.transform(temp3f);
                    }

                    double x = this.pos.x + temp3f.x;
                    double y = this.pos.y + temp3f.y;
                    double z = this.pos.z + temp3f.z;

                    world.addParticle(effect, true, x, y, z, v.x, v.y, v.z);
                }

                this.tick = frequency;
            }

            this.tick -= 1;
        }
    }

    /**
     * Builds the particle effect from the form's settings. For the two common parametric
     * particles the arguments string is just a bare id for convenience — {@code dirt} for
     * {@code minecraft:block}, {@code apple} for {@code minecraft:item} — and the rest of the
     * structure is filled in here. Otherwise (or when a {@code {...}} compound is given) it is
     * parsed exactly like the vanilla {@code /particle} command, so every particle type still
     * works. Falls back to flame when nothing parses.
     */
    private static ParticleEffect createEffect(ParticleSettings settings, World world)
    {
        String args = settings.arguments == null ? "" : settings.arguments.trim();

        try
        {
            ParticleType<?> type = Registries.PARTICLE_TYPE.get(settings.particle);
            boolean bareId = !args.isEmpty() && args.charAt(0) != '{';

            if (bareId && type == ParticleTypes.BLOCK)
            {
                return new BlockStateParticleEffect(ParticleTypes.BLOCK, Registries.BLOCK.get(Identifier.of(args)).getDefaultState());
            }

            if (bareId && type == ParticleTypes.ITEM)
            {
                return new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Registries.ITEM.get(Identifier.of(args))));
            }

            StringReader reader = new StringReader(settings.particle.toString() + args);

            return ParticleEffectArgumentType.readParameters(reader, world.getRegistryManager());
        }
        catch (Exception e)
        {
            return ParticleTypes.FLAME;
        }
    }
}