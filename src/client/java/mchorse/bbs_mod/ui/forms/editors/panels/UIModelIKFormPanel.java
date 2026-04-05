package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UIModelIKFormPanel extends UIFormPanel<ModelForm>
{
    public UIStringList bones;

    public UIButton locator;
    public UIButton root;
    public UIButton apply;

    private String selectedBone = "";
    private Map<String, IKData> ikData = new HashMap<>();

    private static class IKData
    {
        public String locator = "";
        public String root = "";
    }

    public UIModelIKFormPanel(UIForm editor)
    {
        super(editor);

        this.bones = new UIStringList((l) ->
        {
            this.selectedBone = l.isEmpty() ? "" : l.get(0);
            this.updateLabels();
        });
        this.bones.background().h(UIConstants.LIST_ITEM_HEIGHT * 8);

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

        this.apply = new UIButton(UIKeys.FORMS_EDITORS_MODEL_IK_APPLY, (b) -> this.save());

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_MODEL_IK_BONES),
            this.bones,
            this.locator,
            this.root,
            this.apply.marginTop(UIConstants.SECTION_GAP)
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

            this.load(bones);
        }

        this.updateLabels();
        this.options.resize();
    }

    private void setElementsEnabled(boolean enabled)
    {
        this.bones.setEnabled(enabled);
        this.locator.setEnabled(enabled);
        this.root.setEnabled(enabled);
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
        if (this.locator == null || this.root == null)
        {
            return;
        }

        IKData data = this.ikData.get(this.selectedBone);
        
        String locatorLabel = data == null ? "" : data.locator;
        String rootLabel = data == null ? "" : data.root;

        this.locator.label = UIKeys.FORMS_EDITORS_MODEL_IK_LOCATOR.format(this.formatBone(locatorLabel));
        this.root.label = UIKeys.FORMS_EDITORS_MODEL_IK_ROOT.format(this.formatBone(rootLabel));
    }

    private IKData getOrCreateData(String bone)
    {
        return this.ikData.computeIfAbsent(bone, k -> new IKData());
    }

    private String formatBone(String bone)
    {
        return bone == null || bone.isEmpty() ? "-" : bone;
    }

    private void load(List<String> bones)
    {
        this.ikData.clear();
        MapType ik = this.readIK();
        
        for (String bone : ik.keys())
        {
            if (ik.has(bone, BaseType.TYPE_MAP))
            {
                MapType boneData = ik.getMap(bone);
                IKData data = new IKData();
                data.locator = boneData.getString("locator");
                data.root = boneData.getString("root");
                this.ikData.put(bone, data);
            }
        }
    }

    private void save()
    {
        File file = this.getIKFile();

        if (file == null)
        {
            this.getContext().notifyError(UIKeys.FORMS_EDITORS_MODEL_IK_SAVE_ERROR);
            return;
        }

        file.getParentFile().mkdirs();

        MapType ik = new MapType();

        for (Map.Entry<String, IKData> entry : this.ikData.entrySet())
        {
            IKData data = entry.getValue();
            
            if (!data.locator.isEmpty() || !data.root.isEmpty())
            {
                MapType boneData = new MapType();
                boneData.putString("locator", data.locator);
                boneData.putString("root", data.root);
                ik.put(entry.getKey(), boneData);
            }
        }

        if (DataToString.writeSilently(file, ik, true))
        {
            this.getContext().notifySuccess(UIKeys.FORMS_EDITORS_MODEL_IK_SAVED);
        }
        else
        {
            this.getContext().notifyError(UIKeys.FORMS_EDITORS_MODEL_IK_SAVE_ERROR);
        }
    }

    private MapType readIK()
    {
        File file = this.getIKFile();

        if (file != null && file.exists())
        {
            try
            {
                return DataToString.mapFromString(IOUtils.readText(file));
            }
            catch (Exception e)
            {}
        }

        return new MapType();
    }

    private File getIKFile()
    {
        String model = this.form.model.get();

        if (model != null && !model.isEmpty())
        {
            return BBSMod.getAssetsPath(ModelManager.MODELS_PREFIX + model + "/ik.json");
        }

        return null;
    }
}
