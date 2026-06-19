package mchorse.bbs_mod.client.renderer.item;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.items.GunProperties;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;

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
public class GunItemRenderer
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
            item.properties.update(item.formEntity);
            item.formEntity.update();
        }
    }

    public void render(ItemStack stack, ItemDisplayContext mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        // TODO(1.21.11 render): BuiltinItemRendererRegistry/DynamicItemRenderer removed (1.21.4
        // item-model rewrite). Re-implement custom item rendering via the item-model /
        // SpecialModelRenderer system. Previously this rendered the gun's Form (with first-person
        // zoom + zoom-section editor preview handling) for the given ItemDisplayContext through
        // FormUtilsClient.render(...).
        this.get(stack);
    }

    public Item get(ItemStack stack)
    {
        if (stack == null || stack.getItem() != BBSMod.GUN_ITEM)
        {
            return null;
        }

        if (this.map.containsKey(stack))
        {
            return this.map.get(stack);
        }

        Item item = new Item(GunProperties.get(stack));

        this.map.put(stack, item);

        return item;
    }

    public static class Item
    {
        public GunProperties properties;
        public IEntity formEntity;
        public int expiration = 20;

        public Item(GunProperties properties)
        {
            this.properties = properties;
            this.formEntity = new StubEntity(MinecraftClient.getInstance().world);
        }
    }
}