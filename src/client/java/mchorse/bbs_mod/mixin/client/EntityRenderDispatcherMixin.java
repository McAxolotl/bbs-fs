package mchorse.bbs_mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// TODO(1.21.11 render): re-port and re-enable in bbs.client.mixins.json
// EntityRenderDispatcher renamed to EntityRenderManager; its render(Entity,...) method
// is gone (state-based pipeline). The @WrapOperation target string + the white-overlay
// computation can't be ported until the render foundation lands; body neutralized below.
@Mixin(EntityRenderManager.class)
public class EntityRenderDispatcherMixin
{
    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"
        )
    )
    private <E extends Entity> void wrapRender(
        EntityRenderer<E, ?> renderer, E entity, float yaw, float tickDelta,
        MatrixStack matrices, VertexConsumerProvider vcp, int light,
        Operation<Void> original
    ) {
        // TODO(1.21.11 render): re-port. LivingEntityRenderer.getOverlay now takes a
        // LivingEntityRenderState (not LivingEntity) and the render path is state-based.
        // Body neutralized so this stays compilable; mixin is not applied at runtime.
        if (entity instanceof LivingEntity livingEntity)
        {
            int o = net.minecraft.client.render.OverlayTexture.DEFAULT_UV;

            if (MorphRenderer.renderLivingEntity(livingEntity, yaw, tickDelta, matrices, vcp, light, o))
            {
                return;
            }
        }

        original.call(renderer, entity, yaw, tickDelta, matrices, vcp, light);
    }
}