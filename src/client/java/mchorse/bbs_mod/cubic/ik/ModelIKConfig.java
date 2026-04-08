package mchorse.bbs_mod.cubic.ik;

import java.util.List;

public record ModelIKConfig(List<Chain> chains)
{
    public enum PoleSpace { WORLD, ROOT, CONTROLLER }

    public record Chain(String controller, String locator, String root, boolean enabled, float poleX, float poleY, float poleZ, PoleSpace poleSpace)
    {
        public Chain(String controller, String locator, String root, boolean enabled)
        {
            this(controller, locator, root, enabled, 0F, 0F, 0F, PoleSpace.ROOT);
        }
    }
}
