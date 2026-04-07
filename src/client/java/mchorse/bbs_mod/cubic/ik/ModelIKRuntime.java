package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModelIKRuntime
{
    private ModelIKRuntime()
    {
    }

    public static void clearCache()
    {
        ModelIKCache.clear();
    }

    public static void invalidate(String modelId)
    {
        ModelIKCache.invalidate(modelId);
    }

    public static void apply(ModelInstance instance)
    {
        apply(instance, null);
    }

    public static void apply(ModelInstance instance, Map<String, Vector3f> controllerTargets)
    {
        if (instance == null || !(instance.model instanceof Model model))
        {
            return;
        }

        ModelIKCache.Compiled compiled = ModelIKCache.get(instance.id, model);

        if (compiled == null)
        {
            return;
        }

        List<ModelIKCache.CompiledChain> chains = compiled.chains();

        if (chains == null || chains.isEmpty())
        {
            return;
        }

        ModelIKApplier.apply(model, chains, controllerTargets);
    }

    public static List<String> getControllers(ModelInstance instance)
    {
        if (instance == null || !(instance.model instanceof Model model))
        {
            return java.util.Collections.emptyList();
        }

        ModelIKCache.Compiled compiled = ModelIKCache.get(instance.id, model);

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        Set<String> unique = new LinkedHashSet<>();

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            if (chain != null && chain.controller() != null && !chain.controller().isEmpty())
            {
                unique.add(chain.controller());
            }
        }

        return unique.isEmpty() ? java.util.Collections.emptyList() : new ArrayList<>(unique);
    }
}
