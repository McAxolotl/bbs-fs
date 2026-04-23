package mchorse.bbs_mod.cubic.ik;

import java.util.List;

public record ModelIKConfig(List<Chain> chains)
{
    public enum PoleSpace { WORLD, ROOT, CONTROLLER }
    public static final float DEFAULT_WEIGHT = 1F;

    public record Chain(String controller, String locator, String root, boolean enabled, float poleX, float poleY, float poleZ, PoleSpace poleSpace, float weight)
    {
        public Chain
        {
            poleSpace = poleSpace == null ? PoleSpace.ROOT : poleSpace;
            weight = clamp01(weight);
        }

        public Chain(String controller, String locator, String root, boolean enabled)
        {
            this(controller, locator, root, enabled, 0F, 0F, 0F, PoleSpace.ROOT, DEFAULT_WEIGHT);
        }

        private static float clamp01(float value)
        {
            if (value < 0F)
            {
                return 0F;
            }

            return Math.min(value, 1F);
        }
    }
}
