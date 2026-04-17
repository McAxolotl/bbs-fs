package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsConfig;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsIO;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UIModelConstraintsFormPanel extends UIFormPanel<ModelForm>
{
    private static final float DEFAULT_MIN = -180F;
    private static final float DEFAULT_MAX = 180F;

    public UIStringList bones;

    public UIToggle enabled;
    public UITrackpad minX;
    public UITrackpad minY;
    public UITrackpad minZ;
    public UITrackpad maxX;
    public UITrackpad maxY;
    public UITrackpad maxZ;

    private String selectedBone = "";
    private final Map<String, ModelConstraintsConfig.BoneConstraint> data = new HashMap<>();
    private boolean syncingUI;

    public UIModelConstraintsFormPanel(UIForm editor)
    {
        super(editor);

        IKey axis = IKey.constant("%s (%s)");

        this.bones = new UIStringList((l) ->
        {
            this.selectedBone = l.isEmpty() ? "" : l.get(0);
            this.updateFields();
        });
        this.bones.background().h(UIConstants.LIST_ITEM_HEIGHT * 8);

        this.enabled = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_ENABLED, (b) ->
        {
            if (this.syncingUI || this.selectedBone.isEmpty())
            {
                return;
            }

            if (b.getValue())
            {
                this.data.put(this.selectedBone, this.readFromFields(true));
            }
            else
            {
                this.data.remove(this.selectedBone);
            }

            this.updateFieldsEnabled();
            this.commitChanges();
        });

        this.minX = axisTrackpad((v) -> this.onFieldChanged(), Colors.RED, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_X));
        this.minY = axisTrackpad((v) -> this.onFieldChanged(), Colors.GREEN, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_Y));
        this.minZ = axisTrackpad((v) -> this.onFieldChanged(), Colors.BLUE, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_Z));
        this.maxX = axisTrackpad((v) -> this.onFieldChanged(), Colors.RED, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_X));
        this.maxY = axisTrackpad((v) -> this.onFieldChanged(), Colors.GREEN, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_Y));
        this.maxZ = axisTrackpad((v) -> this.onFieldChanged(), Colors.BLUE, axis.format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_Z));

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_BONES),
            this.bones,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_SETTINGS).background().marginTop(UIConstants.SECTION_GAP),
            this.enabled,
            UI.label(IKey.constant("%s / %s").format(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX)).marginTop(UIConstants.SECTION_GAP),
            UI.label(UIKeys.GENERAL_X),
            UI.row(this.minX, this.maxX),
            UI.label(UIKeys.GENERAL_Y),
            UI.row(this.minY, this.maxY),
            UI.label(UIKeys.GENERAL_Z),
            UI.row(this.minZ, this.maxZ)
        );
    }

    private static UITrackpad axisTrackpad(java.util.function.Consumer<Double> c, int color, IKey tooltip)
    {
        UITrackpad t = new UITrackpad(c).degrees().onlyNumbers().limit(-180D, 180D);
        t.textbox.setColor(color);
        t.tooltip(tooltip);
        return t;
    }

    @Override
    public void startEdit(ModelForm form)
    {
        super.startEdit(form);

        ModelInstance model = ModelFormRenderer.getModel(form);

        this.data.clear();
        this.selectedBone = "";

        if (model == null || model.model == null)
        {
            this.bones.setList(Collections.emptyList());
            this.bones.deselect();
            this.setElementsEnabled(false);
            this.setDefaults();
            this.enabled.setValue(false);
            this.options.resize();
            return;
        }

        List<String> bones = new ArrayList<>(model.model.getGroupKeysInHierarchyOrder());
        bones.removeIf(model.disabledBones::contains);

        this.bones.setList(bones);
        this.setElementsEnabled(true);

        ModelConstraintsConfig config = null;
        if (this.form != null && this.form.constraints.get() instanceof MapType map)
        {
            config = ModelConstraintsIO.fromData(map);
        }

        if (config != null && config.bones() != null)
        {
            this.data.putAll(config.bones());
        }

        if (!bones.isEmpty())
        {
            this.selectBone(bones.get(0));
        }
        else
        {
            this.setDefaults();
            this.enabled.setValue(false);
        }

        this.options.resize();
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
            this.selectBone(bone);
        }
    }

    private void selectBone(String bone)
    {
        this.selectedBone = bone == null ? "" : bone;

        if (!this.selectedBone.isEmpty())
        {
            this.bones.setCurrentScroll(this.selectedBone);
        }

        this.updateFields();
    }

    private void updateFields()
    {
        if (this.selectedBone.isEmpty())
        {
            this.syncingUI = true;
            this.enabled.setValue(false);
            this.setDefaults();
            this.syncingUI = false;
            this.updateFieldsEnabled();
            return;
        }

        ModelConstraintsConfig.BoneConstraint c = this.data.get(this.selectedBone);

        this.syncingUI = true;

        try
        {
            if (c == null || !c.enabled())
            {
                this.enabled.setValue(false);
                this.setDefaults();
            }
            else
            {
                this.enabled.setValue(true);
                this.minX.setValue(c.minX());
                this.minY.setValue(c.minY());
                this.minZ.setValue(c.minZ());
                this.maxX.setValue(c.maxX());
                this.maxY.setValue(c.maxY());
                this.maxZ.setValue(c.maxZ());
            }
        }
        finally
        {
            this.syncingUI = false;
        }

        this.updateFieldsEnabled();
    }

    private void updateFieldsEnabled()
    {
        boolean panelEnabled = this.bones.isEnabled();
        boolean active = panelEnabled && this.enabled.getValue() && !this.selectedBone.isEmpty();
        this.minX.setEnabled(active);
        this.minY.setEnabled(active);
        this.minZ.setEnabled(active);
        this.maxX.setEnabled(active);
        this.maxY.setEnabled(active);
        this.maxZ.setEnabled(active);
    }

    private void setDefaults()
    {
        this.minX.setValue(DEFAULT_MIN);
        this.minY.setValue(DEFAULT_MIN);
        this.minZ.setValue(DEFAULT_MIN);
        this.maxX.setValue(DEFAULT_MAX);
        this.maxY.setValue(DEFAULT_MAX);
        this.maxZ.setValue(DEFAULT_MAX);
    }

    private void onFieldChanged()
    {
        if (this.syncingUI || !this.enabled.getValue() || this.selectedBone.isEmpty())
        {
            return;
        }

        this.data.put(this.selectedBone, this.readFromFields(true));
        this.commitChanges();
    }

    private ModelConstraintsConfig.BoneConstraint readFromFields(boolean enabled)
    {
        return new ModelConstraintsConfig.BoneConstraint(
            enabled,
            (float) this.minX.getValue(),
            (float) this.minY.getValue(),
            (float) this.minZ.getValue(),
            (float) this.maxX.getValue(),
            (float) this.maxY.getValue(),
            (float) this.maxZ.getValue()
        );
    }

    private void commitChanges()
    {
        if (this.form == null)
        {
            return;
        }

        this.form.constraints.set(ModelConstraintsIO.toData(new ModelConstraintsConfig(this.data)));
    }

    private void setElementsEnabled(boolean enabled)
    {
        this.bones.setEnabled(enabled);
        this.enabled.setEnabled(enabled);
        this.minX.setEnabled(enabled);
        this.minY.setEnabled(enabled);
        this.minZ.setEnabled(enabled);
        this.maxX.setEnabled(enabled);
        this.maxY.setEnabled(enabled);
        this.maxZ.setEnabled(enabled);
        this.updateFieldsEnabled();
    }
}
