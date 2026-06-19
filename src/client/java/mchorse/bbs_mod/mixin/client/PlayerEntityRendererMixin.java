package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.morphing.Morph;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// TODO(1.21.11 render): re-add when render path ported
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin
{
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void onRender(AbstractClientPlayerEntity abstractClientPlayerEntity, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, CallbackInfo info)
    {
        if (MorphRenderer.renderPlayer(abstractClientPlayerEntity, f, g, matrixStack, vertexConsumerProvider, i))
        {
            info.cancel();
        }
    }

    @Inject(method = "getPositionOffset", at = @At("HEAD"), cancellable = true)
    public void onPositionOffset(AbstractClientPlayerEntity abstractClientPlayerEntity, float f, CallbackInfoReturnable<Vec3d> info)
    {
        Morph morph = Morph.getMorph(abstractClientPlayerEntity);

        if (morph != null && morph.getForm() != null)
        {
            info.setReturnValue(Vec3d.ZERO);
        }
    }

    @Inject(method = "renderArm", at = @At("HEAD"), cancellable = true)
    public void onRenderArmBegin(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve, CallbackInfo info)
    {
        Morph morph = Morph.getMorph(player);

        if (morph != null)
        {
            Form form = morph.getForm();

            if (form != null)
            {
                FormRenderer renderer = FormUtilsClient.getRenderer(form);
                // getModel() on a raw PlayerEntityRenderer erases to EntityModel (no rightArm);
                // PlayerEntityModel inherits rightArm from BipedEntityModel, so cast the model to it.
                net.minecraft.client.render.entity.model.PlayerEntityModel model =
                    (net.minecraft.client.render.entity.model.PlayerEntityModel) ((PlayerEntityRenderer<?>) (Object) this).getModel();
                Hand hand = model.rightArm == arm ? Hand.MAIN_HAND : Hand.OFF_HAND;

                if (renderer != null && renderer.renderArm(matrices, light, player, hand))
                {
                    info.cancel();
                }
            }
        }
    }
}