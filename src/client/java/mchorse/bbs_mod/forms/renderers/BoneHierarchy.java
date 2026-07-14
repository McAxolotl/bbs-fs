package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A renderer-independent, string-only view of a form's editable bone hierarchy.
 */
public final class BoneHierarchy
{
    public static final BoneHierarchy EMPTY = new BoneHierarchy(Collections.emptyList());

    private final List<Bone> bones;
    private final List<String> boneIds;
    private final Map<String, Bone> bonesById;
    private final Map<String, String> aliases;

    public BoneHierarchy(List<Bone> bones)
    {
        this(bones, Collections.emptyMap());
    }

    BoneHierarchy(List<Bone> bones, Map<String, String> aliases)
    {
        List<Bone> uniqueBones = new ArrayList<>(bones.size());
        List<String> boneIds = new ArrayList<>(bones.size());
        Map<String, Bone> bonesById = new LinkedHashMap<>();

        for (Bone bone : bones)
        {
            if (bone == null || bone.id() == null || bonesById.putIfAbsent(bone.id(), bone) != null)
            {
                continue;
            }

            uniqueBones.add(bone);
            boneIds.add(bone.id());
        }

        this.bones = Collections.unmodifiableList(uniqueBones);
        this.boneIds = Collections.unmodifiableList(boneIds);
        this.bonesById = Collections.unmodifiableMap(bonesById);
        this.aliases = Collections.unmodifiableMap(new LinkedHashMap<>(aliases));
    }

    public List<Bone> getBones()
    {
        return this.bones;
    }

    public List<String> getBoneIds()
    {
        return this.boneIds;
    }

    public Bone getBone(String id)
    {
        return this.bonesById.get(id);
    }

    /**
     * Builds readable labels while retaining stable IDs as list values. Repeated vanilla part
     * names are qualified by model layer, and repeated names inside one layer also include their
     * hierarchy path.
     */
    public Map<String, String> getLabels(boolean indent)
    {
        Map<String, Integer> names = new HashMap<>();
        Map<String, Integer> layerNames = new HashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();

        for (Bone bone : this.bones)
        {
            names.merge(bone.name(), 1, Integer::sum);
            layerNames.merge(this.getLayerNameKey(bone), 1, Integer::sum);
        }

        for (Bone bone : this.bones)
        {
            String label = bone.name();

            if (names.getOrDefault(bone.name(), 0) > 1)
            {
                String layer = getLayerName(bone.layerId());

                if (layerNames.getOrDefault(this.getLayerNameKey(bone), 0) > 1)
                {
                    String path = this.getPath(bone);

                    label += layer.isEmpty() ? " (" + path + ")" : " (" + layer + ": " + path + ")";
                }
                else if (!layer.isEmpty())
                {
                    label += " (" + layer + ")";
                }
            }

            if (indent)
            {
                label = "  ".repeat(bone.depth()) + label;
            }

            labels.put(bone.id(), label);
        }

        return Collections.unmodifiableMap(labels);
    }

    /** Replaces legacy bone names with their stable IDs, preserving an existing stable-ID edit. */
    public void migratePose(Pose pose)
    {
        if (pose == null || this.aliases.isEmpty())
        {
            return;
        }

        for (String alias : new ArrayList<>(pose.transforms.keySet()))
        {
            String id = this.aliases.get(alias);

            if (id == null || id.equals(alias))
            {
                continue;
            }

            PoseTransform transform = pose.transforms.remove(alias);

            pose.transforms.putIfAbsent(id, transform);
        }
    }

    public List<Bone> getAdjacent(String id)
    {
        Bone selected = this.getBone(id);

        if (selected == null)
        {
            return Collections.emptyList();
        }

        List<Bone> adjacent = new ArrayList<>();

        for (Bone bone : this.bones)
        {
            if (bone.layerId().equals(selected.layerId()) && sameParent(bone.parentId(), selected.parentId()))
            {
                adjacent.add(bone);
            }
        }

        return adjacent;
    }

    /** Returns every descendant of the selected bone in hierarchy order, excluding itself. */
    public List<Bone> getDescendants(String id)
    {
        if (this.getBone(id) == null)
        {
            return Collections.emptyList();
        }

        List<Bone> descendants = new ArrayList<>();

        for (Bone candidate : this.bones)
        {
            Bone parent = candidate.parentId() == null ? null : this.getBone(candidate.parentId());

            while (parent != null)
            {
                if (id.equals(parent.id()))
                {
                    descendants.add(candidate);
                    break;
                }

                parent = parent.parentId() == null ? null : this.getBone(parent.parentId());
            }
        }

        return descendants;
    }

    /** Returns the ancestry path from the root down to the selected bone. */
    public List<Bone> getAncestors(String id)
    {
        List<Bone> ancestors = new ArrayList<>();
        Bone bone = this.getBone(id);

        while (bone != null)
        {
            ancestors.add(bone);
            bone = bone.parentId() == null || bone.parentId().isEmpty() ? null : this.getBone(bone.parentId());
        }

        Collections.reverse(ancestors);

        return ancestors;
    }

    private static boolean sameParent(String a, String b)
    {
        return a == null ? b == null : a.equals(b);
    }

    private static String getLayerName(String layerId)
    {
        String name = layerId.startsWith("minecraft:") ? layerId.substring("minecraft:".length()) : layerId;

        return name.replace("#", " / ").replace('_', ' ');
    }

    private String getLayerNameKey(Bone bone)
    {
        return bone.layerId() + '\u0000' + bone.name();
    }

    private String getPath(Bone bone)
    {
        StringBuilder path = new StringBuilder();

        for (Bone ancestor : this.getAncestors(bone.id()))
        {
            if (!path.isEmpty())
            {
                path.append('/');
            }

            path.append(ancestor.name());
        }

        return path.toString();
    }

    public record Bone(String id, String name, String parentId, int depth, String layerId)
    {
        public Bone
        {
            name = name == null ? id : name;
            depth = Math.max(0, depth);
            layerId = layerId == null ? "" : layerId;
        }
    }
}
