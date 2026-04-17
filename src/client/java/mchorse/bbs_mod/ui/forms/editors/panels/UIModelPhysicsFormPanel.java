package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsConfig;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsIO;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UIModelPhysicsFormPanel extends UIFormPanel<ModelForm>
{
    private static final float DEFAULT_GRAVITY = 1F;
    private static final float DEFAULT_WEIGHT = 1F;
    private static final float DEFAULT_DAMPING = 0.15F;
    private static final int DEFAULT_ITERATIONS = 4;
    private static final float DEFAULT_RADIUS = 0.1F;

    public UIButton end;
    public UIButton targetBone;
    public UIStringList bones;
    public UIToggle enabled;
    public UITrackpad gravity;
    public UITrackpad weight;
    public UIToggle relativeGravity;
    public UITrackpad relativeGravityRotateX;
    public UITrackpad relativeGravityRotateY;
    public UITrackpad relativeGravityRotateZ;
    public UITrackpad damping;
    public UITrackpad iterations;
    public UIToggle collisions;
    public UITrackpad radius;
    public UIButton clear;
    public UIButton apply;

    private List<String> availableBones = Collections.emptyList();
    private String selectedBone = "";
    private String modelId = "";
    private final Map<String, BoneData> data = new HashMap<>();
    private ModelInstance modelInstance;

    private static class BoneData
    {
        public String end = "";
        public String targetBone = "";
        public float gravity = DEFAULT_GRAVITY;
        public float weight = DEFAULT_WEIGHT;
        public boolean relativeGravity;
        public float relativeGravityRotateX;
        public float relativeGravityRotateY;
        public float relativeGravityRotateZ;
        public float damping = DEFAULT_DAMPING;
        public int iterations = DEFAULT_ITERATIONS;
        public boolean collisions;
        public float radius = DEFAULT_RADIUS;
    }

    public UIModelPhysicsFormPanel(UIForm editor)
    {
        super(editor);

        IKey axis = IKey.constant("%s (%s)");

        this.bones = new UIStringList((l) ->
        {
            this.selectedBone = l.isEmpty() ? "" : l.get(0);
            this.updateFields();
        });
        this.bones.background().h(UIConstants.LIST_ITEM_HEIGHT * 8);

        this.enabled = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ENABLED, (b) ->
        {
            if (this.selectedBone.isEmpty())
            {
                return;
            }

            if (b.getValue())
            {
                BoneData d = this.data.computeIfAbsent(this.selectedBone, (k) -> new BoneData());

                if (d.end == null || d.end.isEmpty())
                {
                    d.end = this.selectedBone;
                }
            }
            else
            {
                this.data.remove(this.selectedBone);
            }

            this.updateFields();
        });

        this.gravity = new UITrackpad((v) ->
        {
            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.gravity = v.floatValue();
            }
        });
        this.gravity.onlyNumbers().values(0.1D, 0.01D, 0.5D).increment(0.01D).limit(0D, 10D);
        this.gravity.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_GRAVITY);

        this.weight = new UITrackpad((v) ->
        {
            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.weight = v.floatValue();
            }
        });
        this.weight.onlyNumbers().values(0.1D, 0.01D, 0.25D).increment(0.01D).limit(0D, 1D);
        this.weight.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WEIGHT);

        this.relativeGravity = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY, (b) ->
        {
            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.relativeGravity = b.getValue();
            }
        });

        this.relativeGravityRotateX = axisTrackpad((v) ->
        {
            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.relativeGravityRotateX = v.floatValue();
            }
        }, Colors.RED, axis.format(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY_ROTATION, UIKeys.GENERAL_X));
        this.relativeGravityRotateY = axisTrackpad((v) ->
        {
            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.relativeGravityRotateY = v.floatValue();
            }
        }, Colors.GREEN, axis.format(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY_ROTATION, UIKeys.GENERAL_Y));
        this.relativeGravityRotateZ = axisTrackpad((v) ->
        {
            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.relativeGravityRotateZ = v.floatValue();
            }
        }, Colors.BLUE, axis.format(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY_ROTATION, UIKeys.GENERAL_Z));

        this.damping = new UITrackpad((v) ->
        {
            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.damping = v.floatValue();
            }
        });
        this.damping.onlyNumbers().values(0.05D, 0.01D, 0.2D).increment(0.01D).limit(0D, 1D);
        this.damping.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_DAMPING);

        this.iterations = new UITrackpad((v) ->
        {
            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.iterations = v.intValue();
            }
        });
        this.iterations.onlyNumbers().integer().values(1D).increment(1D).limit(1D, 20D, true);
        this.iterations.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ITERATIONS);

        this.collisions = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_COLLISIONS, (b) ->
        {
            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.collisions = b.getValue();
            }
        });

        this.radius = new UITrackpad((v) ->
        {
            BoneData d = this.getSelectedData();

            if (d != null)
            {
                d.radius = v.floatValue();
            }
        });
        this.radius.onlyNumbers().values(0.05D, 0.01D, 0.2D).increment(0.01D).limit(0D, 1D);
        this.radius.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RADIUS);

        this.end = new UIButton(IKey.EMPTY, (b) ->
        {
            BoneData d = this.getSelectedData();

            if (d == null)
            {
                return;
            }

            this.openEndMenu(d.end, (bone) ->
            {
                d.end = bone;
                this.updateFields();
            });
        });

        this.targetBone = new UIButton(IKey.EMPTY, (b) ->
        {
            BoneData d = this.getSelectedData();

            if (d == null)
            {
                return;
            }

            this.getContext().replaceContextMenu((menu) ->
            {
                menu.action(Icons.CLOSE, UIKeys.GENERAL_NONE, d.targetBone.isEmpty(), () ->
                {
                    d.targetBone = "";
                    this.updateFields();
                });

                for (String bone : this.availableBones)
                {
                    boolean selected = bone.equals(d.targetBone);
                    menu.action(Icons.LIMB, IKey.constant(bone), selected, () ->
                    {
                        d.targetBone = bone;
                        this.updateFields();
                    });
                }
            });
        });

        this.clear = new UIButton(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_CLEAR, (b) ->
        {
            if (this.selectedBone.isEmpty())
            {
                return;
            }

            this.data.remove(this.selectedBone);
            this.updateFields();
        });

        this.apply = new UIButton(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_APPLY, (b) -> this.save());

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_CHAINS),
            this.bones,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_SETTINGS).background().marginTop(UIConstants.SECTION_GAP),
            this.enabled,
            this.end,
            this.targetBone,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_GRAVITY),
            this.gravity,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WEIGHT),
            this.weight,
            this.relativeGravity,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY_ROTATION),
            UI.row(this.relativeGravityRotateX, this.relativeGravityRotateY, this.relativeGravityRotateZ),
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_DAMPING),
            this.damping,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ITERATIONS),
            this.iterations,
            this.collisions.marginTop(UIConstants.SECTION_GAP),
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RADIUS),
            this.radius,
            UI.row(this.clear, this.apply).marginTop(UIConstants.SECTION_GAP)
        );
    }

    @Override
    public void startEdit(ModelForm form)
    {
        super.startEdit(form);

        ModelInstance model = ModelFormRenderer.getModel(form);
        this.modelInstance = model;
        this.modelId = form.model.get();

        if (model == null || model.model == null)
        {
            this.availableBones = Collections.emptyList();
            this.data.clear();
            this.bones.setList(Collections.emptyList());
            this.bones.deselect();
            this.selectedBone = "";
            this.setElementsEnabled(false);
        }
        else
        {
            List<String> bones = new ArrayList<>(model.model.getGroupKeysInHierarchyOrder());
            bones.removeIf(model.disabledBones::contains);
            this.availableBones = bones;

            this.setElementsEnabled(true);
            this.load();
            this.bones.setList(this.availableBones);

            if (!this.availableBones.isEmpty())
            {
                this.selectBone(this.availableBones.get(0));
            }
        }

        this.options.resize();
    }

    private void setElementsEnabled(boolean enabled)
    {
        this.bones.setEnabled(enabled);
        this.enabled.setEnabled(enabled);
        this.end.setEnabled(enabled);
        this.targetBone.setEnabled(enabled);
        this.gravity.setEnabled(enabled);
        this.weight.setEnabled(enabled);
        this.relativeGravity.setEnabled(enabled);
        this.relativeGravityRotateX.setEnabled(enabled);
        this.relativeGravityRotateY.setEnabled(enabled);
        this.relativeGravityRotateZ.setEnabled(enabled);
        this.damping.setEnabled(enabled);
        this.iterations.setEnabled(enabled);
        this.collisions.setEnabled(enabled);
        this.radius.setEnabled(enabled);
        this.clear.setEnabled(enabled);
        this.apply.setEnabled(enabled);
    }

    private BoneData getSelectedData()
    {
        return this.selectedBone.isEmpty() ? null : this.data.get(this.selectedBone);
    }

    private void selectBone(String bone)
    {
        this.selectedBone = bone == null ? "" : bone;
        this.bones.setCurrentScroll(this.selectedBone);
        this.updateFields();
    }

    @Override
    public void pickBone(String bone)
    {
        if (bone != null && !bone.isEmpty() && this.availableBones.contains(bone))
        {
            this.selectBone(bone);
        }
    }

    private void updateFields()
    {
        boolean panelEnabled = this.bones.isEnabled();
        boolean boneSelected = !this.selectedBone.isEmpty();
        BoneData d = this.getSelectedData();
        boolean active = panelEnabled && boneSelected && d != null;

        this.enabled.setEnabled(panelEnabled && boneSelected);
        this.enabled.setValue(d != null);

        this.end.setEnabled(active);
        this.targetBone.setEnabled(active);
        this.gravity.setEnabled(active);
        this.weight.setEnabled(active);
        this.relativeGravity.setEnabled(active);
        this.relativeGravityRotateX.setEnabled(active);
        this.relativeGravityRotateY.setEnabled(active);
        this.relativeGravityRotateZ.setEnabled(active);
        this.damping.setEnabled(active);
        this.iterations.setEnabled(active);
        this.collisions.setEnabled(active);
        this.radius.setEnabled(active);
        this.clear.setEnabled(panelEnabled && boneSelected && d != null);
        this.apply.setEnabled(panelEnabled && this.form != null);

        if (d == null)
        {
            this.end.label = UIKeys.FORMS_EDITORS_MODEL_PHYSICS_END.format("-");
            this.targetBone.label = UIKeys.FORMS_EDITORS_MODEL_PHYSICS_TARGET.format("-");
            this.gravity.setValue(DEFAULT_GRAVITY);
            this.weight.setValue(DEFAULT_WEIGHT);
            this.relativeGravity.setValue(false);
            this.relativeGravityRotateX.setValue(0);
            this.relativeGravityRotateY.setValue(0);
            this.relativeGravityRotateZ.setValue(0);
            this.damping.setValue(DEFAULT_DAMPING);
            this.iterations.setValue(DEFAULT_ITERATIONS);
            this.collisions.setValue(false);
            this.radius.setValue(DEFAULT_RADIUS);
        }
        else
        {
            this.end.label = UIKeys.FORMS_EDITORS_MODEL_PHYSICS_END.format(d.end == null || d.end.isEmpty() ? "-" : d.end);
            this.targetBone.label = UIKeys.FORMS_EDITORS_MODEL_PHYSICS_TARGET.format(d.targetBone == null || d.targetBone.isEmpty() ? "-" : d.targetBone);
            this.gravity.setValue(d.gravity);
            this.weight.setValue(d.weight);
            this.relativeGravity.setValue(d.relativeGravity);
            this.relativeGravityRotateX.setValue(d.relativeGravityRotateX);
            this.relativeGravityRotateY.setValue(d.relativeGravityRotateY);
            this.relativeGravityRotateZ.setValue(d.relativeGravityRotateZ);
            this.damping.setValue(d.damping);
            this.iterations.setValue(d.iterations);
            this.collisions.setValue(d.collisions);
            this.radius.setValue(d.radius);
        }
    }

    private void openEndMenu(String current, java.util.function.Consumer<String> callback)
    {
        if (this.availableBones.isEmpty() || this.selectedBone.isEmpty())
        {
            return;
        }

        this.getContext().replaceContextMenu((menu) ->
        {
            List<String> candidates = this.getEndCandidates(this.selectedBone);

            if (candidates.isEmpty())
            {
                candidates = this.availableBones;
            }

            for (String bone : candidates)
            {
                boolean selected = bone.equals(current);
                menu.action(Icons.LIMB, IKey.constant(bone), selected, () -> callback.accept(bone));
            }
        });
    }

    private void load()
    {
        this.data.clear();
        ModelPhysicsConfig config = null;
        if (this.form != null && this.form.physics.get() instanceof mchorse.bbs_mod.data.types.MapType map)
        {
            config = ModelPhysicsIO.fromData(map);
        }

        if (config == null || config.bones() == null)
        {
            return;
        }

        for (Map.Entry<String, ModelPhysicsConfig.Bone> entry : config.bones().entrySet())
        {
            String root = entry.getKey();
            ModelPhysicsConfig.Bone bone = entry.getValue();

            if (root == null || root.isEmpty() || bone == null || bone.end() == null || bone.end().isEmpty())
            {
                continue;
            }

            BoneData d = new BoneData();
            d.end = bone.end();
            d.targetBone = bone.targetBone() == null ? "" : bone.targetBone();
            d.gravity = bone.gravity();
            d.weight = bone.weight();
            d.relativeGravity = bone.relativeGravity();
            d.relativeGravityRotateX = bone.relativeGravityRotateX();
            d.relativeGravityRotateY = bone.relativeGravityRotateY();
            d.relativeGravityRotateZ = bone.relativeGravityRotateZ();
            d.damping = bone.damping();
            d.iterations = bone.iterations();
            d.collisions = bone.collisions();
            d.radius = bone.radius();
            this.data.put(root, d);
        }
    }

    private void save()
    {
        if (this.form == null)
        {
            this.getContext().notifyError(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_SAVE_ERROR);
            return;
        }

        for (Map.Entry<String, BoneData> entry : this.data.entrySet())
        {
            String root = entry.getKey();
            BoneData d = entry.getValue();

            if (d == null || root == null || root.isEmpty() || d.end == null || d.end.isEmpty())
            {
                continue;
            }

            if (!this.isValidChain(root, d.end))
            {
                this.getContext().notifyError(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_INVALID_CHAIN.format(root, d.end));
                return;
            }
        }

        Map<String, ModelPhysicsConfig.Bone> bones = new HashMap<>();

        for (Map.Entry<String, BoneData> entry : this.data.entrySet())
        {
            String root = entry.getKey();
            BoneData d = entry.getValue();

            bones.put(root, new ModelPhysicsConfig.Bone(d.end, d.targetBone, d.gravity, d.damping, d.iterations, d.relativeGravity, d.relativeGravityRotateX, d.relativeGravityRotateY, d.relativeGravityRotateZ, d.collisions, d.radius, d.weight));
        }

        ModelPhysicsConfig config = new ModelPhysicsConfig(bones);
        this.form.physics.set(ModelPhysicsIO.toData(config));
        this.getContext().notifySuccess(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_SAVED);
    }

    private boolean isValidChain(String rootId, String endId)
    {
        if (this.modelInstance == null || this.modelInstance.model == null)
        {
            return true;
        }

        if (!(this.modelInstance.model instanceof Model model))
        {
            return true;
        }

        ModelGroup root = model.getGroup(rootId);
        ModelGroup end = model.getGroup(endId);

        if (root == null || end == null)
        {
            return false;
        }

        ModelGroup group = end;

        while (group != null)
        {
            if (group == root)
            {
                return true;
            }

            group = group.parent;
        }

        return false;
    }

    private List<String> getEndCandidates(String rootId)
    {
        if (rootId == null || rootId.isEmpty() || this.availableBones.isEmpty())
        {
            return Collections.emptyList();
        }

        List<String> out = new ArrayList<>();

        for (String bone : this.availableBones)
        {
            if (this.isValidChain(rootId, bone))
            {
                out.add(bone);
            }
        }

        return out;
    }

    private static UITrackpad axisTrackpad(java.util.function.Consumer<Double> callback, int color, IKey tooltip)
    {
        UITrackpad t = new UITrackpad(callback).degrees().onlyNumbers().limit(-180D, 180D);
        t.textbox.setColor(color);
        t.tooltip(tooltip);
        return t;
    }

}
