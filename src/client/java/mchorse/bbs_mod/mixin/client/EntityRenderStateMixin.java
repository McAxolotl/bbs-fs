package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.renderer.IRenderStateEntityHolder;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Carries the live entity + tickDelta on the render state so the morph path can recover them after the
 * 1.21.2 render-state split. See {@link IRenderStateEntityHolder}.
 */
@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements IRenderStateEntityHolder
{
    @Unique
    private Entity bbs$entity;

    @Unique
    private float bbs$tickDelta;

    @Override
    public Entity bbs$getRenderedEntity()
    {
        return this.bbs$entity;
    }

    @Override
    public float bbs$getRenderedTickDelta()
    {
        return this.bbs$tickDelta;
    }

    @Override
    public void bbs$setRenderedEntity(Entity entity, float tickDelta)
    {
        this.bbs$entity = entity;
        this.bbs$tickDelta = tickDelta;
    }
}
