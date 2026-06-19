package mchorse.bbs_mod.client.renderer.item;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * TODO(1.21.11 render): BuiltinItemRendererRegistry / DynamicItemRenderer were removed in
 * the 1.21.4 item-model rewrite. This class no longer implements that interface and its
 * render(...) body is neutralized. The class + fields are kept so it can be re-wired to the
 * new item-model / SpecialModelRenderer system later. Registration in BBSModClient must be
 * removed (see report).
 */
public class ModelBlockItemRenderer
{
    private Map<ItemStack, Item> map = new HashMap<>();

    public void update()
    {
        Iterator<Item> it = this.map.values().iterator();

        while (it.hasNext())
        {
            Item item = it.next();

            if (item.expiration <= 0)
            {
                it.remove();
            }

            item.expiration -= 1;
            item.entity.getProperties().update(item.formEntity);
            item.formEntity.update();
        }
    }

    public void render(ItemStack stack, ItemDisplayContext mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        // TODO(1.21.11 render): BuiltinItemRendererRegistry/DynamicItemRenderer removed (1.21.4
        // item-model rewrite). Re-implement custom item rendering via the item-model /
        // SpecialModelRenderer system. Previously this rendered the block's Form for the given
        // ItemDisplayContext through FormUtilsClient.render(...).
        this.get(stack);
    }

    public Item get(ItemStack stack)
    {
        if (stack == null || stack.getItem() != BBSMod.MODEL_BLOCK_ITEM)
        {
            return null;
        }

        if (this.map.containsKey(stack))
        {
            return this.map.get(stack);
        }

        ModelBlockEntity entity = new ModelBlockEntity(BlockPos.ORIGIN, BBSMod.MODEL_BLOCK.getDefaultState());
        Item item = new Item(entity);

        this.map.put(stack, item);

        TypedEntityData<?> beComponent = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);

        if (beComponent == null)
        {
            return item;
        }

        NbtCompound nbt = beComponent.copyNbtWithoutId();

        // TODO(1.21.11 render): populate the ModelBlockEntity from `nbt`. The old
        // entity.readNbt(nbt, registryManager) was removed by the 1.21.6 persistence rewrite;
        // ModelBlockEntity.readData(ReadView) is protected and not callable from here. Re-wire
        // once the item-model render path is restored (e.g. via NbtReadView + an accessor).

        return item;
    }

    public static class Item
    {
        public ModelBlockEntity entity;
        public IEntity formEntity;
        public int expiration = 20;

        public Item(ModelBlockEntity entity)
        {
            this.entity = entity;
            this.formEntity = new StubEntity(MinecraftClient.getInstance().world);
        }
    }
}