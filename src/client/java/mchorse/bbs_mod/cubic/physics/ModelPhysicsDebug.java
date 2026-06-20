package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import net.minecraft.client.util.math.MatrixStack;

/**
 * TODO(1.21.11 render merge): STUBBED. This was a brand-new 1.21.1 feature file (physics debug overlay) that
 * arrived with the 1.21.1 → 1.21.11 merge. Its full body drew the simulated physics chains the same way the
 * IK debug overlay does — thin wires through round joint dots, the pinned anchor in a warm accent and the
 * simulated tip in a cool one, with each attach bone mirrored into the picking pass so a click selects its
 * bone. It was written entirely against the OLD 1.21.1 immediate-mode render API
 * ({@code Tessellator.begin}/{@code BufferRenderer.drawWithGlobalProgram}, {@code RenderSystem.disable/enable*},
 * {@code GameRenderer::getPositionColorProgram}), none of which exists in 1.21.11.
 *
 * <p>Both entry points are now no-ops so the merge compiles. {@link #renderStencil} is still wired from
 * {@code ModelFormRenderer} (and {@code render} from the same renderer, currently dropped). Re-port against the
 * pipeline RenderLayer flush path the IK overlay already uses ({@code ModelIKDebug.getLinesLayer()}/
 * {@code getTrisLayer()}); the full original implementation is preserved in git history (this file's pre-merge
 * version on the {@code 1.21.1} branch).
 *
 * <p>Gated globally by {@link #enabled}.
 */
public final class ModelPhysicsDebug
{
    public static boolean enabled;

    private ModelPhysicsDebug()
    {
    }

    /** TODO(1.21.11 render merge): no-op stub — see class javadoc. Re-port the physics chain overlay onto the pipeline RenderLayer flush path. */
    public static void render(MatrixStack stack, IModel model, MapType physicsData, String selectedRoot)
    {
    }

    /** TODO(1.21.11 render merge): no-op stub — see class javadoc. Re-port the pickable attach-bone markers onto the pipeline stencil pass. */
    public static void renderStencil(MatrixStack stack, IModel model, MapType physicsData, StencilMap stencilMap, Form form)
    {
    }
}
