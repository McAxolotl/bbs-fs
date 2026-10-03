package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.utils.shapes.UIShapeControllers;
import mchorse.bbs_mod.utils.undo.IUndo;

/** Preview deformation shares the editor's history, but is never part of the saved model config. */
public class ModelShapePreviewUndo implements IUndo<ValueGroup>
{
    private final ModelForm form;
    private final ShapeKeys before;
    private ShapeKeys after;
    private boolean mergeable = true;

    public ModelShapePreviewUndo(ModelForm form, ShapeKeys before, ShapeKeys after)
    {
        this.form = form;
        this.before = before.copy();
        this.after = after.copy();
    }

    @Override public String toString() { return UIShapeControllers.key("preview").get(); }
    @Override public IUndo<ValueGroup> noMerging() { this.mergeable = false; return this; }
    @Override public boolean isMergeable(IUndo<ValueGroup> undo)
    {
        return this.mergeable && undo instanceof ModelShapePreviewUndo other && this.form == other.form;
    }
    @Override public void merge(IUndo<ValueGroup> undo) { this.after = ((ModelShapePreviewUndo) undo).after.copy(); }
    @Override public void undo(ValueGroup context) { this.form.shapeKeys.set(this.before.copy()); }
    @Override public void redo(ValueGroup context) { this.form.shapeKeys.set(this.after.copy()); }
}
