package mchorse.bbs_mod.client.renderer;

import net.minecraft.entity.Entity;

/**
 * Duck interface implemented on {@link net.minecraft.client.render.entity.state.EntityRenderState}
 * (via EntityRenderStateMixin) so the morph render path can recover the live entity + tickDelta that
 * produced a render state.
 *
 * <p>The 1.21.2 render-state split froze entity data into the state object and stopped threading the
 * live {@link Entity} through the {@code render()} call. BBS morphs must still look the entity up
 * ({@code Morph} for players, {@code SelectorOwner} for selector mobs) to find its form, so we stash
 * the entity here during {@code EntityRenderer.getAndUpdateRenderState}.
 */
public interface IRenderStateEntityHolder
{
    Entity bbs$getRenderedEntity();

    float bbs$getRenderedTickDelta();

    void bbs$setRenderedEntity(Entity entity, float tickDelta);
}
