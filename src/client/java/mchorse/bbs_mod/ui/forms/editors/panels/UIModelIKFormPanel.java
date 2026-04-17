package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.ModelIKConfig;
import mchorse.bbs_mod.cubic.ik.ModelIKIO;
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

public class UIModelIKFormPanel extends UIFormPanel<ModelForm>
{
    private static final float DEFAULT_WEIGHT = ModelIKConfig.DEFAULT_WEIGHT;

    public UIStringList bones;

    public UIToggle enabled;
    public UIButton locator;
    public UIButton root;
    public UITrackpad weight;
    public UITrackpad poleX;
    public UITrackpad poleY;
    public UITrackpad poleZ;
    public UIButton clear;
    public UIButton apply;

    private String selectedBone = "";
    private Map<String, IKData> ikData = new HashMap<>();

    private static class IKData
    {
        public String locator = "";
        public String root = "";
        public boolean enabled = true;
        public float weight = DEFAULT_WEIGHT;
        public float poleX;
        public float poleY;
        public float poleZ;
    }

    public UIModelIKFormPanel(UIForm editor)
    {
        super(editor);

        IKey raw = IKey.constant("%s (%s)");

        this.bones = new UIStringList((l) ->
        {
            this.selectedBone = l.isEmpty() ? "" : l.get(0);
            this.updateLabels();
        });
        this.bones.background().h(UIConstants.LIST_ITEM_HEIGHT * 8);

        this.enabled = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_ENABLED, (b) ->
        {
            if (this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.enabled = b.getValue();
            this.updateLabels();
        });

        this.locator = new UIButton(IKey.EMPTY, (b) ->
        {
            if (this.selectedBone.isEmpty()) return;
            
            IKData data = this.getOrCreateData(this.selectedBone);
            this.openBoneMenu(data.locator, (bone) ->
            {
                data.locator = bone;
                this.updateLabels();
            });
        });

        this.root = new UIButton(IKey.EMPTY, (b) ->
        {
            if (this.selectedBone.isEmpty()) return;

            IKData data = this.getOrCreateData(this.selectedBone);
            this.openBoneMenu(data.root, (bone) ->
            {
                data.root = bone;
                this.updateLabels();
            });
        });

        this.weight = new UITrackpad((v) ->
        {
            if (this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.weight = v.floatValue();
        });
        this.weight.onlyNumbers().values(0.1D, 0.01D, 0.25D).increment(0.01D).limit(0D, 1D);
        this.weight.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_WEIGHT);

        this.poleX = new UITrackpad((v) ->
        {
            if (this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.poleX = v.floatValue();
        });
        this.poleX.block().onlyNumbers();
        this.poleX.tooltip(raw.format(UIKeys.FORMS_EDITORS_MODEL_IK_POLE, UIKeys.GENERAL_X));
        this.poleX.textbox.setColor(Colors.RED);
        this.poleY = new UITrackpad((v) ->
        {
            if (this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.poleY = v.floatValue();
        });
        this.poleY.block().onlyNumbers();
        this.poleY.tooltip(raw.format(UIKeys.FORMS_EDITORS_MODEL_IK_POLE, UIKeys.GENERAL_Y));
        this.poleY.textbox.setColor(Colors.GREEN);
        this.poleZ = new UITrackpad((v) ->
        {
            if (this.selectedBone.isEmpty())
            {
                return;
            }

            IKData data = this.getOrCreateData(this.selectedBone);
            data.poleZ = v.floatValue();
        });
        this.poleZ.block().onlyNumbers();
        this.poleZ.tooltip(raw.format(UIKeys.FORMS_EDITORS_MODEL_IK_POLE, UIKeys.GENERAL_Z));
        this.poleZ.textbox.setColor(Colors.BLUE);

        this.clear = new UIButton(UIKeys.FORMS_EDITORS_MODEL_IK_CLEAR, (b) ->
        {
            if (this.selectedBone.isEmpty())
            {
                return;
            }

            this.ikData.remove(this.selectedBone);
            this.updateLabels();
        });

        this.apply = new UIButton(UIKeys.FORMS_EDITORS_MODEL_IK_APPLY, (b) -> this.save());

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_MODEL_IK_BONES),
            this.bones,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_IK_SETTINGS).background().marginTop(UIConstants.SECTION_GAP),
            this.enabled,
            this.locator,
            this.root,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_IK_WEIGHT),
            this.weight,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_IK_POLE).marginTop(UIConstants.SECTION_GAP),
            UI.row(2, 0, UIConstants.CONTROL_HEIGHT, this.poleX, this.poleY, this.poleZ),
            UI.row(this.clear, this.apply).marginTop(UIConstants.SECTION_GAP)
        );
    }

    @Override
    public void startEdit(ModelForm form)
    {
        super.startEdit(form);

        ModelInstance model = ModelFormRenderer.getModel(form);

        if (model == null || model.model == null)
        {
            this.bones.setList(Collections.emptyList());
            this.bones.deselect();
            this.selectedBone = "";
            this.ikData.clear();

            this.setElementsEnabled(false);
        }
        else
        {
            List<String> bones = new ArrayList<>(model.model.getGroupKeysInHierarchyOrder());
            bones.removeIf(model.disabledBones::contains);

            this.bones.setList(bones);
            this.setElementsEnabled(true);

            this.load();
        }

        this.updateLabels();
        this.options.resize();
    }

    private void setElementsEnabled(boolean enabled)
    {
        this.bones.setEnabled(enabled);
        this.enabled.setEnabled(enabled);
        this.locator.setEnabled(enabled);
        this.root.setEnabled(enabled);
        this.weight.setEnabled(enabled);
        this.poleX.setEnabled(enabled);
        this.poleY.setEnabled(enabled);
        this.poleZ.setEnabled(enabled);
        this.clear.setEnabled(enabled);
        this.apply.setEnabled(enabled);
    }

    @Override
    public void pickBone(String bone)
    {
        if (bone == null || bone.isEmpty())
        {
            return;
        }

        if (this.bones.getList().contains(bone))
        {
            this.selectedBone = bone;
            this.bones.setCurrentScroll(bone);
        }
    }

    private void openBoneMenu(String current, java.util.function.Consumer<String> callback)
    {
        if (this.bones.getList().isEmpty())
        {
            return;
        }

        this.getContext().replaceContextMenu((menu) ->
        {
            boolean none = current == null || current.isEmpty();

            menu.action(Icons.REMOVE, UIKeys.GENERAL_NONE, none, () -> callback.accept(""));

            for (String bone : this.bones.getList())
            {
                boolean selected = bone.equals(current);

                menu.action(Icons.LIMB, IKey.constant(bone), selected, () -> callback.accept(bone));
            }
        });
    }

    private void updateLabels()
    {
        if (this.locator == null || this.root == null || this.enabled == null)
        {
            return;
        }

        IKData data = this.ikData.get(this.selectedBone);
        
        String locatorLabel = data == null ? "" : data.locator;
        String rootLabel = data == null ? "" : data.root;
        boolean active = data != null && data.enabled;
        boolean canEdit = !this.selectedBone.isEmpty() && this.bones.isEnabled() && active;

        this.locator.label = UIKeys.FORMS_EDITORS_MODEL_IK_LOCATOR.format(this.formatBone(locatorLabel));
        this.root.label = UIKeys.FORMS_EDITORS_MODEL_IK_ROOT.format(this.formatBone(rootLabel));
        this.weight.setValue(data == null ? DEFAULT_WEIGHT : data.weight);
        this.poleX.setValue(data == null ? 0F : data.poleX);
        this.poleY.setValue(data == null ? 0F : data.poleY);
        this.poleZ.setValue(data == null ? 0F : data.poleZ);
        this.enabled.setEnabled(this.bones.isEnabled() && !this.selectedBone.isEmpty());
        this.enabled.setValue(active);
        this.locator.setEnabled(canEdit);
        this.root.setEnabled(canEdit);
        this.weight.setEnabled(canEdit);
        this.poleX.setEnabled(canEdit);
        this.poleY.setEnabled(canEdit);
        this.poleZ.setEnabled(canEdit);
        this.clear.setEnabled(this.bones.isEnabled() && !this.selectedBone.isEmpty() && data != null);
        this.apply.setEnabled(this.bones.isEnabled() && this.form != null);
    }

    private IKData getOrCreateData(String bone)
    {
        return this.ikData.computeIfAbsent(bone, k -> new IKData());
    }

    private String formatBone(String bone)
    {
        return bone == null || bone.isEmpty() ? "-" : bone;
    }

    private void load()
    {
        this.ikData.clear();

        ModelIKConfig config = null;
        if (this.form != null && this.form.ik.get() instanceof mchorse.bbs_mod.data.types.MapType map)
        {
            config = ModelIKIO.fromData(map);
        }

        if (config == null || config.chains() == null)
        {
            return;
        }

        for (ModelIKConfig.Chain chain : config.chains())
        {
            if (chain == null || chain.controller() == null || chain.controller().isEmpty())
            {
                continue;
            }

            IKData data = new IKData();
            data.locator = chain.locator();
            data.root = chain.root();
            data.enabled = chain.enabled();
            data.weight = chain.weight();
            data.poleX = chain.poleX();
            data.poleY = chain.poleY();
            data.poleZ = chain.poleZ();
            this.ikData.put(chain.controller(), data);
        }
    }

    private void save()
    {
        if (this.form == null)
        {
            this.getContext().notifyError(UIKeys.FORMS_EDITORS_MODEL_IK_SAVE_ERROR);
            return;
        }

        List<ModelIKConfig.Chain> out = new ArrayList<>();

        for (Map.Entry<String, IKData> entry : this.ikData.entrySet())
        {
            String controller = entry.getKey();
            IKData data = entry.getValue();

            if (controller == null || controller.isEmpty() || data == null)
            {
                continue;
            }

            if (data.locator == null || data.locator.isEmpty() || data.root == null || data.root.isEmpty())
            {
                continue;
            }

            out.add(new ModelIKConfig.Chain(controller, data.locator, data.root, data.enabled, data.poleX, data.poleY, data.poleZ, ModelIKConfig.PoleSpace.ROOT, data.weight));
        }

        ModelIKConfig config = out.isEmpty() ? null : new ModelIKConfig(out);
        this.form.ik.set(config == null ? null : ModelIKIO.toData(config));
        this.getContext().notifySuccess(UIKeys.FORMS_EDITORS_MODEL_IK_SAVED);
    }
}
