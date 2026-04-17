package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

final class ModelIKCache
{
    private ModelIKCache()
    {
    }

    public record CompiledChain(String controller, String locator, String root, float poleX, float poleY, float poleZ, ModelIKConfig.PoleSpace poleSpace, float weight, List<String> chainRootToEffector)
    {
    }

    public record Compiled(List<CompiledChain> chains)
    {
    }

    private static final WeakHashMap<MapType, EmbeddedCompiled> EMBEDDED = new WeakHashMap<>();

    private record EmbeddedCompiled(Model model, List<CompiledChain> chains)
    {
    }

    public static void clear()
    {
        EMBEDDED.clear();
    }

    public static Compiled getFromData(Model model, MapType data)
    {
        if (model == null || data == null)
        {
            return null;
        }

        EmbeddedCompiled cached = EMBEDDED.get(data);

        if (cached != null && cached.model == model)
        {
            return new Compiled(cached.chains);
        }

        ModelIKConfig config = ModelIKIO.fromData(data);
        List<CompiledChain> compiled = compile(model, config);

        EmbeddedCompiled next = new EmbeddedCompiled(model, compiled);
        EMBEDDED.put(data, next);

        return new Compiled(compiled);
    }

    private static List<CompiledChain> compile(Model model, ModelIKConfig config)
    {
        if (config == null || config.chains() == null || config.chains().isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        List<CompiledChain> out = new ArrayList<>(config.chains().size());

        for (ModelIKConfig.Chain chain : config.chains())
        {
            if (chain == null)
            {
                continue;
            }

            if (!chain.enabled())
            {
                continue;
            }

            if (chain.weight() <= 0F)
            {
                continue;
            }

            ModelGroup controller = model.getGroup(chain.controller());
            ModelGroup effector = model.getGroup(chain.locator());
            ModelGroup root = model.getGroup(chain.root());

            if (controller == null || effector == null || root == null)
            {
                continue;
            }

            List<String> chainIds = buildChainIds(effector, root);

            if (chainIds.size() < 2)
            {
                continue;
            }

            out.add(new CompiledChain(chain.controller(), chain.locator(), chain.root(), chain.poleX(), chain.poleY(), chain.poleZ(), chain.poleSpace(), chain.weight(), chainIds));
        }

        return out;
    }

    private static List<String> buildChainIds(ModelGroup effector, ModelGroup root)
    {
        List<String> list = new ArrayList<>();
        ModelGroup group = effector;

        while (group != null)
        {
            list.add(group.id);

            if (group == root)
            {
                java.util.Collections.reverse(list);
                return list;
            }

            group = group.parent;
        }

        return java.util.Collections.emptyList();
    }
}
