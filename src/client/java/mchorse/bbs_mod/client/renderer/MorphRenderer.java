package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.selectors.ISelectorOwnerProvider;
import mchorse.bbs_mod.selectors.SelectorOwner;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Morph rendering for the 1.21.11 (render-state) pipeline.
 *
 * <p>1.21.2+ made entity {@code render()} a build phase that only enqueues into the
 * OrderedRenderCommandQueue, so a morph form cannot be drawn there (the BBS immediate form pipeline
 * needs the camera model-view active, which only holds once the entity queue is flushed). So
 * {@code LivingEntityRendererMorphMixin} COLLECTS each morph (snapshotting the camera-relative pose
 * vanilla computed) and cancels the vanilla render, then {@link #renderQueued()} draws the collected
 * forms from WorldRenderEvents.AFTER_ENTITIES (via {@code BBSRendering.renderCoolStuff}) — the proven
 * draw context shared with replay/film forms.
 */
public class MorphRenderer
{
    public static boolean hidePlayer = false;

    private static final List<Queued> QUEUE = new ArrayList<>();

    /**
     * Collect a player morph for deferred rendering. Returns true to suppress the vanilla render.
     */
    public static boolean collectPlayer(AbstractClientPlayerEntity player, MatrixStack matrices, int light, int overlay, float bodyYaw, float tickDelta)
    {
        if (hidePlayer)
        {
            if (FormUtilsClient.getCurrentForm() instanceof MobForm form && !form.isPlayer())
            {
                return true;
            }
        }

        Morph morph = Morph.getMorph(player);

        if (morph != null && morph.getForm() != null)
        {
            if (canRender())
            {
                queue(morph.getForm(), morph.entity, matrices, light, overlay, bodyYaw, tickDelta);
            }

            return true;
        }

        return false;
    }

    /**
     * Collect a selector-owner (mob) morph for deferred rendering. Returns true to suppress the
     * vanilla render.
     */
    public static boolean collectLivingEntity(LivingEntity livingEntity, MatrixStack matrices, int light, int overlay, float bodyYaw, float tickDelta)
    {
        if (!(livingEntity instanceof ISelectorOwnerProvider))
        {
            return false;
        }

        SelectorOwner owner = ((ISelectorOwnerProvider) livingEntity).getOwner();

        owner.check();

        Form form = owner.getForm();

        if (form != null)
        {
            queue(form, owner.entity, matrices, light, overlay, bodyYaw, tickDelta);

            return true;
        }

        return false;
    }

    private static void queue(Form form, IEntity entity, MatrixStack matrices, int light, int overlay, float bodyYaw, float tickDelta)
    {
        Queued queued = new Queued();

        queued.form = form;
        queued.entity = entity;
        queued.pose = new Matrix4f(matrices.peek().getPositionMatrix());
        queued.normal = new Matrix3f(matrices.peek().getNormalMatrix());
        queued.light = light;
        queued.overlay = overlay;
        queued.bodyYaw = bodyYaw;
        queued.tickDelta = tickDelta;

        QUEUE.add(queued);
    }

    /**
     * Draw all collected morph forms. Called from WorldRenderEvents.AFTER_ENTITIES, where the entity
     * command queue has already flushed and the camera model-view is still active — the only world
     * context where the BBS immediate form pipeline lands at the correct position.
     */
    public static void renderQueued()
    {
        if (QUEUE.isEmpty())
        {
            return;
        }

        for (Queued queued : QUEUE)
        {
            MatrixStack stack = new MatrixStack();

            stack.peek().getPositionMatrix().set(queued.pose);
            stack.peek().getNormalMatrix().set(queued.normal);

            stack.push();
            stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-queued.bodyYaw));

            FormUtilsClient.render(queued.form, new FormRenderingContext()
                .set(FormRenderType.ENTITY, queued.entity, stack, queued.light, queued.overlay, queued.tickDelta)
                .camera(MinecraftClient.getInstance().gameRenderer.getCamera()));

            stack.pop();
        }

        QUEUE.clear();
    }

    private static boolean canRender()
    {
        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (menu instanceof UIDashboard dashboard)
        {
            UIDashboardPanel panel = dashboard.getPanels().panel;

            if (panel instanceof UIMorphingPanel morphingPanel)
            {
                return !morphingPanel.palette.editor.isEditing();
            }
        }

        return true;
    }

    private static class Queued
    {
        public Form form;
        public IEntity entity;
        public Matrix4f pose;
        public Matrix3f normal;
        public int light;
        public int overlay;
        public float bodyYaw;
        public float tickDelta;
    }
}
