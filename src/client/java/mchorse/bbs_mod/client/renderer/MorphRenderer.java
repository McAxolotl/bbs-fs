package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.film.BaseFilmController;
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
import mchorse.bbs_mod.utils.MatrixStackUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Morph rendering for the 1.21.11 (render-state) pipeline.
 *
 * <p>1.21.2+ made entity {@code render()} a build phase that only enqueues into the
 * OrderedRenderCommandQueue, so a morph form cannot be drawn there (the BBS immediate form pipeline
 * needs the camera transform supplied through the WorldRenderContext MatrixStack, which only holds
 * once the entity queue is flushed). So {@code LivingEntityRendererMorphMixin} COLLECTS each morph
 * and cancels the vanilla render, then {@link #renderQueued(WorldRenderContext)} draws the collected
 * forms from WorldRenderEvents.AFTER_ENTITIES (via {@code BBSRendering.renderCoolStuff}) — the same
 * proven path as replay/film forms ({@link BaseFilmController#renderEntity}).
 */
public class MorphRenderer
{
    public static boolean hidePlayer = false;

    private static final List<Queued> QUEUE = new ArrayList<>();

    /**
     * Collect a player morph for deferred rendering. Returns true to suppress the vanilla render.
     */
    public static boolean collectPlayer(AbstractClientPlayerEntity player, int light, int overlay, float tickDelta)
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
                queue(morph.getForm(), morph.entity, light, overlay, tickDelta);
            }

            return true;
        }

        return false;
    }

    /**
     * Collect a selector-owner (mob) morph for deferred rendering. Returns true to suppress the
     * vanilla render.
     */
    public static boolean collectLivingEntity(LivingEntity livingEntity, int light, int overlay, float tickDelta)
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
            queue(form, owner.entity, light, overlay, tickDelta);

            return true;
        }

        return false;
    }

    private static void queue(Form form, IEntity entity, int light, int overlay, float tickDelta)
    {
        Queued queued = new Queued();

        queued.form = form;
        queued.entity = entity;
        queued.light = light;
        queued.overlay = overlay;
        queued.tickDelta = tickDelta;

        QUEUE.add(queued);
    }

    /**
     * Draw all collected morph forms. Called from WorldRenderEvents.AFTER_ENTITIES, where the entity
     * command queue has already flushed and the WorldRenderContext MatrixStack carries the camera
     * transform — the only world context where the BBS immediate form pipeline lands correctly. This
     * mirrors {@link BaseFilmController#renderEntity}: build the camera-relative matrix from the
     * entity's world position and multiply it onto the context MatrixStack.
     */
    public static void renderQueued(WorldRenderContext context)
    {
        if (QUEUE.isEmpty())
        {
            return;
        }

        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        double cx = camera.getCameraPos().x;
        double cy = camera.getCameraPos().y;
        double cz = camera.getCameraPos().z;
        MatrixStack stack = context.matrices();

        for (Queued queued : QUEUE)
        {
            Matrix4f target = BaseFilmController.getMatrixForRenderWithRotation(queued.entity, cx, cy, cz, queued.tickDelta);

            stack.push();
            MatrixStackUtils.multiply(stack, target);

            FormUtilsClient.render(queued.form, new FormRenderingContext()
                .set(FormRenderType.ENTITY, queued.entity, stack, queued.light, queued.overlay, queued.tickDelta)
                .camera(camera));

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
        public int light;
        public int overlay;
        public float tickDelta;
    }
}
