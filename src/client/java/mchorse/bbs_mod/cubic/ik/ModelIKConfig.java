package mchorse.bbs_mod.cubic.ik;

import java.util.List;

public record ModelIKConfig(List<Chain> chains)
{
    public static final float DEFAULT_WEIGHT = 1F;
    public static final float DEFAULT_POLE_ANGLE = 0F;
    public static final int DEFAULT_CHAIN_LENGTH = 0;

    /**
     * One IK constraint, modeled after Blender: it lives on the {@code tip}
     * bone, reaches {@code target}, spans {@code chainLength} bones up the
     * hierarchy ({@code 0} = up to the root), and bends towards {@code pole}
     * offset by {@code poleAngle} degrees.
     */
    public record Chain(String tip, String target, int chainLength, String pole, float poleAngle, float weight, boolean enabled)
    {
        public Chain
        {
            tip = tip == null ? "" : tip;
            target = target == null ? "" : target;
            pole = pole == null ? "" : pole;
            chainLength = Math.max(0, chainLength);
            weight = clamp01(weight);
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
