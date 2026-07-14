package mchorse.bbs_mod.forms.renderers;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.StringReader;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.mixin.LimbAnimatorAccessor;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MobFormRenderer extends FormRenderer<MobForm> implements ITickable
{
    public static final GameProfile WIDE = new GameProfile(UUID.fromString("b99a2400-28a8-4288-92dc-924beafbf756"), "McHorseYT");
    public static final GameProfile SLIM = new GameProfile(UUID.fromString("5477bd28-e672-4f87-a209-c03cf75f3606"), "osmiq");

    private Entity entity;

    private String lastId = "";
    private String lastNBT = "";
    private boolean lastSlim;

    public float prevHandSwing;
    private float prevYawHead;
    private float prevPitch;
    private MatrixCache bones = new MatrixCache();
    private List<String> pickedBoneIds = List.of();
    private boolean animationInitialized;
    private boolean animationPlaying = true;

    public MobFormRenderer(MobForm form)
    {
        super(form);
    }

    @Override
    public BoneHierarchy getBoneHierarchy()
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            Object renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(this.entity);

            return VanillaRendererBones.discover(renderer).getBoneHierarchy();
        }

        return super.getBoneHierarchy();
    }

    private void bindTexture()
    {
        Link link = this.form.texture.get();

        if (link != null)
        {
            BBSModClient.getTextures().bindTexture(link);
        }
    }

    private void ensureEntity()
    {
        String id = this.form.mobID.get();
        String nbt = this.form.mobNBT.get();
        boolean slim = this.form.slim.get();

        if (!this.lastId.equals(id) || !this.lastNBT.equals(nbt) || slim != this.lastSlim)
        {
            this.lastId = id;
            this.lastNBT = nbt;
            this.lastSlim = slim;
            this.entity = null;
            this.bones.clear();
            this.pickedBoneIds = List.of();
            this.animationInitialized = false;
            this.animationPlaying = this.form.action.get();
            this.prevHandSwing = 0F;
        }

        if (this.entity != null)
        {
            return;
        }

        NbtCompound compound = new NbtCompound();

        try
        {
            compound = (new StringNbtReader(new StringReader(nbt))).parseCompound();
        }
        catch (Exception e)
        {}

        this.entity = Registries.ENTITY_TYPE.get(new Identifier(id)).create(MinecraftClient.getInstance().world);

        if (this.entity == null && this.form.isPlayer())
        {
            this.entity = new OtherClientPlayerEntity(MinecraftClient.getInstance().world, slim ? SLIM : WIDE);
            this.entity.getDataTracker().set(PlayerUtils.ProtectedAccess.getModelParts(), (byte) 0b1111111);
        }

        if (this.entity != null)
        {
            compound.putString("id", id);
            this.entity.readNbt(compound);
            this.entity.noClip = true;
        }
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.ensureEntity();
        this.animationPlaying = this.form.action.get();

        if (this.entity != null)
        {
            MatrixStack stack = context.batcher.getContext().getMatrices();

            stack.push();

            Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);
            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
            float scale = this.form.uiScale.get();
            float width = this.entity.getWidth();
            float height = this.entity.getHeight();

            scale = scale * Math.min(1.8F / Math.max(width, height), 1F);

            this.applyTransforms(uiMatrix, context.getTransition());
            MatrixStackUtils.multiply(stack, uiMatrix);
            stack.scale(scale, scale, scale);

            if (!this.form.mobID.get().equals("minecraft:ender_dragon"))
            {
                stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
            }

            stack.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
            stack.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

            BooleanHolder first = new BooleanHolder();

            CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
            {
                if (!first.bool)
                {
                    this.bindTexture();

                    first.bool = true;
                }
            });

            consumers.setUI(true);
            Object renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(this.entity);

            float transition = this.animationPlaying ? context.getTransition() : 0F;

            try (MobRenderContext ignored = MobRenderContext.push(renderer, this.form.pose.get(), this.form.poseOverlay.get(), this.getColor(0xffffffff)))
            {
                MinecraftClient.getInstance().getEntityRenderDispatcher().render(this.entity, 0D, 0D, 0D, 0F, transition, stack, consumers, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE);
            }

            consumers.draw();
            consumers.setUI(false);

            CustomVertexConsumerProvider.clearRunnables();

            stack.pop();

            RenderSystem.depthFunc(GL11.GL_ALWAYS);
        }
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        this.ensureEntity();
        this.animationPlaying = this.form.action.get();
        this.bones.clear();
        this.pickedBoneIds = List.of();

        if (this.entity != null)
        {
            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
            int light = context.light;
            BooleanHolder first = new BooleanHolder();

            if (context.isPicking())
            {
                CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
                {
                    if (!first.bool)
                    {
                        this.bindTexture();

                        first.bool = true;
                    }

                    /* The picker shader must be (re)applied for every layer, not just the
                     * first one. Entities like the piglin render held items (e.g. the golden
                     * sword) through Minecraft's own item rendering, which adds extra render
                     * layers. If those layers aren't forced onto the picker shader, they get
                     * drawn with vanilla item shaders, leaking GL/shader state that breaks the
                     * picking of any subsequent entity rendered into the stencil framebuffer. */
                    this.setupTarget(context, BBSShaders.getPickerModelsProgram());
                    RenderSystem.setShader(BBSShaders::getPickerModelsProgram);
                });

                light = 0;
            }
            else
            {
                CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
                {
                    if (!first.bool)
                    {
                        this.bindTexture();

                        first.bool = true;
                    }
                });
            }

            context.stack.push();
            if (context.world != null)
            {
                context.world.push();
            }

            Matrix4f captureBase = new Matrix4f(context.stack.peek().getPositionMatrix());

            if (this.form.mobID.get().equals("minecraft:ender_dragon"))
            {
                context.stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
                if (context.world != null)
                {
                    context.world.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
                }
            }

            if (this.entity instanceof LivingEntity entity)
            {
                int u = context.overlay & '\uffff';
                int v = context.overlay >> 16 & '\uffff';

                entity.hurtTime = v != 10 ? 100 : 0;
            }

            Object renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(this.entity);
            boolean incrementPicking = context.stencilMap != null && context.stencilMap.increment;
            MobRenderContext mobContext = MobRenderContext.push(
                renderer,
                this.form.pose.get(),
                this.form.poseOverlay.get(),
                this.getColor(context.color),
                captureBase,
                context.isPicking(),
                incrementPicking
            );

            try (mobContext)
            {
                float transition = this.animationPlaying ? context.getTransition() : 0F;

                MinecraftClient.getInstance().getEntityRenderDispatcher().render(this.entity, 0D, 0D, 0D, 0F, transition, context.stack, consumers, light);
            }

            this.bones = mobContext.getMatrices();
            this.pickedBoneIds = mobContext.getPickedBoneIds();

            consumers.draw();
            CustomVertexConsumerProvider.clearRunnables();

            context.stack.pop();

            if (context.world != null)
            {
                context.world.pop();
            }

            RenderSystem.enableDepthTest();
            RenderSystem.getModelViewMatrix().identity();
        }
    }

    private Color getColor(int contextColor)
    {
        Color color = new Color().set(contextColor, true);

        FormColorBlend.blend(color, this.form.color.get(), this.form.additiveColor.get());

        return color;
    }

    @Override
    protected void updateStencilMap(FormRenderingContext context)
    {
        StencilMap stencilMap = context.stencilMap;

        if (stencilMap == null)
        {
            return;
        }

        stencilMap.addPicking(this.form);

        if (stencilMap.increment)
        {
            for (String bone : this.pickedBoneIds)
            {
                stencilMap.addPicking(this.form, bone);
            }
        }
    }

    @Override
    public void renderBodyParts(FormRenderingContext context)
    {
        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Matrix4f matrix = this.bones.get(part.bone.get()).matrix();

            context.stack.push();
            if (context.world != null)
            {
                context.world.push();
            }

            if (matrix != null)
            {
                MatrixStackUtils.multiply(context.stack, matrix);
                if (context.world != null)
                {
                    MatrixStackUtils.multiply(context.world, matrix);
                }
            }

            this.renderBodyPart(part, context);

            context.stack.pop();
            if (context.world != null)
            {
                context.world.pop();
            }
        }
    }

    @Override
    public void collectMatrices(IEntity entity, MatrixStack stack, MatrixCache matrices, String prefix, float transition)
    {
        Matrix4f matrix = new Matrix4f();
        Matrix4f origin = new Matrix4f();

        stack.push();
        this.applyTransforms(stack, true, transition);
        origin.set(stack.peek().getPositionMatrix());
        stack.pop();

        stack.push();
        this.applyTransforms(stack, false, transition);
        matrix.set(stack.peek().getPositionMatrix());
        matrices.put(prefix, matrix, origin);

        for (Map.Entry<String, MatrixCacheEntry> entry : this.bones.entrySet())
        {
            Matrix4f boneMatrix = new Matrix4f(stack.peek().getPositionMatrix()).mul(entry.getValue().matrix());
            Matrix4f boneOrigin = new Matrix4f(stack.peek().getPositionMatrix()).mul(entry.getValue().origin());

            matrices.put(StringUtils.combinePaths(prefix, entry.getKey()), boneMatrix, boneOrigin);
        }

        int i = 0;

        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Form form = part.getForm();

            if (form != null)
            {
                Matrix4f boneMatrix = this.bones.get(part.bone.get()).matrix();

                stack.push();

                if (boneMatrix != null)
                {
                    MatrixStackUtils.multiply(stack, boneMatrix);
                }

                MatrixStackUtils.applyTransform(stack, part.transform.get());
                FormUtilsClient.getRenderer(form).collectMatrices(
                    part.useTarget.get() ? entity : part.getEntity(),
                    stack,
                    matrices,
                    StringUtils.combinePaths(prefix, String.valueOf(i)),
                    transition
                );

                stack.pop();
            }

            i += 1;
        }

        stack.pop();
    }

    @Override
    public void tick(IEntity entity)
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            float limbPos = 0F;
            float limbSpeed = 0F;

            if (this.entity instanceof LivingEntity livingEntity && livingEntity.limbAnimator instanceof LimbAnimatorAccessor accessor)
            {
                limbPos = accessor.getPos();
                limbSpeed = accessor.getSpeed();
            }

            if (this.animationPlaying)
            {
                this.entity.tick();
            }

            this.entity.prevPitch = this.prevPitch;
            this.entity.prevYaw = 0F;

            if (this.entity instanceof LivingEntity livingEntity)
            {
                livingEntity.prevHeadYaw = this.prevYawHead;
                livingEntity.prevBodyYaw = 0F;

                /* Limb swing is so ugly */
                if (livingEntity.limbAnimator instanceof LimbAnimatorAccessor a && entity.getLimbAnimator() instanceof LimbAnimatorAccessor b)
                {
                    if (!this.animationInitialized)
                    {
                        a.setPrevSpeed(b.getPrevSpeed());
                        a.setSpeed(b.getSpeed());
                        a.setPos(b.getPos());
                    }
                    else if (this.animationPlaying)
                    {
                        a.setPrevSpeed(limbSpeed);
                        a.setSpeed(b.getSpeed());
                        a.setPos(limbPos + b.getSpeed());
                    }
                }

                /* Arm swing */
                float handSwingProgress = entity.getHandSwingProgress(0F);

                if (this.animationPlaying && handSwingProgress < this.prevHandSwing)
                {
                    this.prevHandSwing = 0;
                }

                if (this.animationPlaying && handSwingProgress > 0 && this.prevHandSwing == 0)
                {
                    livingEntity.swingHand(Hand.MAIN_HAND);
                }

                this.prevHandSwing = handSwingProgress;
            }

            this.entity.setYaw(0F);
            this.entity.setHeadYaw(entity.getHeadYaw() - entity.getBodyYaw());
            this.entity.setPitch(entity.getPitch());
            this.entity.setBodyYaw(0F);

            this.entity.setPos(entity.getX(), entity.getY(), entity.getZ());
            this.entity.setOnGround(entity.isOnGround());
            this.entity.setSneaking(entity.isSneaking());
            this.entity.setSprinting(entity.isSprinting());
            this.entity.setPose(entity.isSneaking() ? EntityPose.CROUCHING : EntityPose.STANDING);
            this.entity.equipStack(EquipmentSlot.MAINHAND, entity.getEquipmentStack(EquipmentSlot.MAINHAND));
            this.entity.equipStack(EquipmentSlot.OFFHAND, entity.getEquipmentStack(EquipmentSlot.OFFHAND));
            this.entity.equipStack(EquipmentSlot.HEAD, entity.getEquipmentStack(EquipmentSlot.HEAD));
            this.entity.equipStack(EquipmentSlot.CHEST, entity.getEquipmentStack(EquipmentSlot.CHEST));
            this.entity.equipStack(EquipmentSlot.LEGS, entity.getEquipmentStack(EquipmentSlot.LEGS));
            this.entity.equipStack(EquipmentSlot.FEET, entity.getEquipmentStack(EquipmentSlot.FEET));
            if (!this.animationInitialized)
            {
                this.entity.age = entity.getAge();
                this.animationInitialized = true;
            }
            this.entity.noClip = true;

            this.prevYawHead = entity.getHeadYaw() - entity.getBodyYaw();
            this.prevPitch = entity.getPitch();
        }
    }

    private static class BooleanHolder
    {
        public boolean bool;
    }
}
