package mchorse.bbs_mod.cubic.physics;

import java.util.Map;

public record ModelPhysicsConfig(Map<String, Bone> bones)
{
    public record Bone(String end, String targetBone, float gravity, float damping, int iterations)
    {
    }
}

