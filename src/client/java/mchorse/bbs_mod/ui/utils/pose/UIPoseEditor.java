package mchorse.bbs_mod.ui.utils.pose;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UIDeltaPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.resizers.AutomaticResizer;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UIDataContextMenu;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseManager;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class UIPoseEditor extends UIElement
{
    private static String lastLimb = "";

    /** The bone list never shrinks below this height when it gets stretched to fill the panel. */
    private static final int MIN_LIST_HEIGHT = UIStringList.DEFAULT_HEIGHT * 4;

    public UIPoseBoneStringList groups;
    public UITrackpad fix;
    public UIColor color;
    public UIToggle lighting;
    public UIPropTransform transform;

    /** The search + pose-toggle row above the bone list; a field so subclasses can re-lay-out. */
    public UIElement toggles;

    /* Pose-edit toggles sitting above the bone list: mirror-edit applies the edit to each bone's
     * left/right counterpart; alternate-invert flips the rotation of every second selected bone. */
    private UIIcon mirror;
    private UIIcon invert;

    /* Bone search next to the toggles. allGroups keeps the unfiltered source so the list can be
     * re-filtered as the query changes; sortGroups remembers whether the source was sorted. */
    private UITextbox search;
    private final List<String> allGroups = new ArrayList<>();
    private boolean sortGroups;

    private String group = "";
    private Pose pose;
    protected IModel model;
    protected Map<String, String> flippedParts;

    public UIPoseEditor()
    {
        this.groups = new UIPoseBoneStringList(this::pickBones);
        this.groups.background().h(UIStringList.DEFAULT_HEIGHT * 8 - 8);
        this.groups.scroll.cancelScrolling();
        this.groups.context(() ->
        {
            UIDataContextMenu menu = new UIDataContextMenu(PoseManager.INSTANCE, this.group, () -> this.pose.toData(), this::pastePose);
            UIIcon flip = new UIIcon(Icons.CONVERT, (b) -> this.flipPose());

            flip.tooltip(UIKeys.POSE_CONTEXT_FLIP_POSE);
            menu.row.addBefore(menu.save, flip);

            return menu;
        });
        this.fix = new UITrackpad((v) -> this.applyFixToSelection(v.floatValue()));
        this.fix.limit(0D, 1D).increment(0.1D).values(0.1, 0.05D, 0.2D);
        this.fix.tooltip(UIKeys.POSE_CONTEXT_FIX_TOOLTIP);
        this.fix.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.applyChildren((p) -> this.setFix(p, (float) this.fix.getValue()));
            });
        });
        this.color = new UIColor((c) -> this.applyColorToSelection(c));
        this.color.withAlpha();
        this.color.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.applyChildren((p) -> this.setColor(p, this.color.picker.color.getARGBColor()));
            });
        });
        this.lighting = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_LIGHTING, (b) -> this.applyLightingToSelection(b.getValue()));
        this.lighting.h(UIConstants.CONTROL_HEIGHT);
        this.lighting.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.POSE_CONTEXT_APPLY, () ->
            {
                this.applyChildren((p) -> this.setLighting(p, this.lighting.getValue()));
            });
        });
        this.transform = this.createTransformEditor();
        this.transform.setModel();

        this.search = new UITextbox(100, (str) -> this.applyGroupsFilter(false)).placeholder(UIKeys.GENERAL_SEARCH);
        this.search.h(20);

        this.mirror = new UIIcon(Icons.CONVERT, (b) -> this.toggleMirrorEdit());
        this.mirror.tooltip(UIKeys.TRANSFORMS_MIRROR_EDIT);
        this.mirror.wh(20, 20);
        this.invert = new UIIcon(Icons.REVERSE, (b) -> this.toggleAlternateInvert());
        this.invert.tooltip(UIKeys.TRANSFORMS_ALTERNATE_INVERT);
        this.invert.wh(20, 20);

        this.toggles = new UIElement();
        this.toggles.h(20).row(0).height(20);
        /* The bone search fills the row; the two pose toggles sit fixed-width at the right (all 20px tall). */
        this.toggles.add(this.search, this.mirror, this.invert);

        this.keys().register(Keys.TRANSFORMATIONS_TOGGLE_FIX, this::toggleFix).category(UIKeys.TRANSFORMS_KEYS_CATEGORY);
        this.keys().register(Keys.TRANSFORMATIONS_MIRROR_EDIT, this::toggleMirrorEdit).category(UIKeys.TRANSFORMS_KEYS_CATEGORY);

        this.column().vertical().stretch();
        this.add(this.toggles, this.groups.marginTop(-UIConstants.MARGIN), UI.label(UIKeys.POSE_CONTEXT_FIX), this.fix, UI.row(this.color, this.lighting), this.transform.marginTop(4));
    }

    @Override
    public void resize()
    {
        if (this.stretchesBoneList())
        {
            this.stretchBoneList();
        }

        super.resize();
    }

    /**
     * Whether the bone list grows to fill the viewport. Only the film editor's pose keyframe editor
     * opts in; the form pose editor keeps the list at its fixed height, so the collapsible sections
     * below it (transform, shape keys) lay out predictably instead of fighting the stretch.
     */
    protected boolean stretchesBoneList()
    {
        return false;
    }

    private void stretchBoneList()
    {
        UIScrollView viewport = this.getViewport();

        if (viewport == null || this.area.h <= 0 || this.groups.getParent() == null)
        {
            return;
        }

        int target = viewport.area.ey() - this.getViewportPadding(viewport);
        int height = this.groups.getFlex().getH() + (target - this.area.ey());

        this.groups.h(Math.max(height, MIN_LIST_HEIGHT));
    }

    private UIScrollView getViewport()
    {
        UIElement element = this.getParent();

        while (element != null)
        {
            if (element instanceof UIScrollView)
            {
                return (UIScrollView) element;
            }

            element = element.getParent();
        }

        return null;
    }

    /** The scroll content lays itself out with this much padding at the bottom; leaving exactly
     *  that gap below the list is what keeps the panel from overflowing into a stray scrollbar. */
    private int getViewportPadding(UIScrollView viewport)
    {
        if (viewport.getFlex().post instanceof AutomaticResizer resizer)
        {
            return resizer.padding;
        }

        return UIConstants.SCROLL_PADDING;
    }

    private void toggleMirrorEdit()
    {
        BBSSettings.poseMirrorEdit.set(!BBSSettings.poseMirrorEdit.get());
        UIUtils.playClick();
    }

    private void toggleAlternateInvert()
    {
        BBSSettings.poseAlternateInvert.set(!BBSSettings.poseAlternateInvert.get());
        UIUtils.playClick();
    }

    @Override
    public void render(UIContext context)
    {
        if (BBSSettings.poseMirrorEdit.get())
        {
            UIDashboardPanels.renderHighlight(context.batcher, this.mirror.area, Direction.BOTTOM);
        }

        if (BBSSettings.poseAlternateInvert.get())
        {
            UIDashboardPanels.renderHighlight(context.batcher, this.invert.area, Direction.BOTTOM);
        }

        super.render(context);
    }

    private void applyChildren(Consumer<PoseTransform> consumer)
    {
        if (this.model == null)
        {
            return;
        }

        for (String bone : this.groups.getCurrent())
        {
            Collection<String> keys = this.model.getAllChildrenKeys(bone);

            for (String key : keys)
            {
                consumer.accept(this.pose.get(key));
            }
        }
    }

    public Pose getPose()
    {
        return this.pose;
    }

    /**
     * First selected bone name (for keyframe paths and legacy callers).
     */
    public String getGroup()
    {
        return this.groups.getCurrentFirst();
    }

    protected void pastePose(MapType data)
    {
        this.restoreSelectionAfter(() -> this.pose.fromData(data));
    }

    protected void flipPose()
    {
        this.restoreSelectionAfter(() -> this.pose.flip(this.flippedParts));
    }

    private void restoreSelectionAfter(Runnable action)
    {
        List<String> current = new ArrayList<>(this.groups.getCurrent());

        action.run();
        this.groups.setCurrent(current);
        this.pickBones(this.groups.getCurrent());
    }

    public void setPose(Pose pose, String group)
    {
        this.pose = pose;
        this.group = group;
    }

    public void fillGroups(Collection<String> groups, boolean reset)
    {
        this.model = null;
        this.flippedParts = null;

        this.fillInGroups(groups, reset, true);
    }

    public void fillGroups(IModel model, Map<String, String> flippedParts, boolean reset)
    {
        this.fillGroups(model, flippedParts, reset, null);
    }

    public void fillGroups(IModel model, Map<String, String> flippedParts, boolean reset, Collection<String> disabledBones)
    {
        this.model = model;
        this.flippedParts = flippedParts;

        if (model == null)
        {
            this.fillInGroups(Collections.emptyList(), reset, false);
            return;
        }

        List<String> bones = new ArrayList<>(model.getGroupKeysInHierarchyOrder());

        bones.removeIf((bone) -> PoseBones.isHidden(disabledBones, bone));
        this.fillInGroups(bones, reset, false);
    }

    private void fillInGroups(Collection<String> groups, boolean reset, boolean sort)
    {
        this.allGroups.clear();
        this.allGroups.addAll(groups);
        this.sortGroups = sort;

        this.applyGroupsFilter(reset);
    }

    /**
     * Repopulate the visible bone list from {@link #allGroups}, keeping only the bones whose name
     * contains the search query. Runs both on a fresh fill and on every keystroke in the search box.
     */
    private void applyGroupsFilter(boolean reset)
    {
        String query = this.search == null ? "" : this.search.getText().trim().toLowerCase();
        List<String> visible = new ArrayList<>();

        for (String bone : this.allGroups)
        {
            if (query.isEmpty() || bone.toLowerCase().contains(query))
            {
                visible.add(bone);
            }
        }

        this.groups.clear();
        this.groups.add(visible);
        if (this.sortGroups)
        {
            this.groups.sort();
        }

        boolean hasBones = !this.allGroups.isEmpty();
        this.fix.setVisible(hasBones);
        this.color.setVisible(hasBones);
        this.transform.setVisible(hasBones);

        List<String> list = this.groups.getList();
        int i = Math.max(reset ? 0 : list.indexOf(lastLimb), 0);

        this.groups.setCurrentScroll(CollectionUtils.getSafe(list, i));
        this.pickBones(this.groups.getCurrent());
    }

    public void selectBone(String bone)
    {
        lastLimb = bone;

        this.groups.setCurrentScroll(bone);
        this.pickBones(this.groups.getCurrent());
    }

    /* Subclass overridable methods */

    protected UIPropTransform createTransformEditor()
    {
        return new UIPosePropTransform();
    }

    /**
     * Applies each transform edit as a per-channel delta to every selected bone,
     * so a multi-selection keeps each bone's own pose instead of collapsing onto
     * the primary's. See {@link UIDeltaPropTransform}.
     */
    private class UIPosePropTransform extends UIDeltaPropTransform
    {
        UIPosePropTransform()
        {
            this.enableHotkeys();
        }

        @Override
        protected boolean supportsMirror()
        {
            return true;
        }

        @Override
        protected void applyToSelection(Consumer<Transform> consumer)
        {
            for (Map.Entry<String, BoneEdit> target : UIPoseEditor.this.resolveBoneEdits(this.isMirrorEdit(), this.isAlternateInvert()).entrySet())
            {
                UIPoseEditor.this.applyToBone(target.getValue(), UIPoseEditor.this.pose.get(target.getKey()), consumer);
            }
        }

        @Override
        protected void reset()
        {
            this.preCallback();
            this.applyToTarget((t) ->
            {
                t.translate.set(0F, 0F, 0F);
                t.scale.set(1F, 1F, 1F);
                t.rotate.set(0F, 0F, 0F);
            });
            this.postCallback();

            this.syncTargetTransform();
        }

        @Override
        public void setR2(Axis axis, double x, double y, double z)
        {
            super.setR2(axis, x, y, z);
            this.syncTargetTransform();
        }
    }

    protected void pickBone(String bone)
    {
        if (bone == null || bone.isEmpty())
        {
            this.pickBones(Collections.emptyList());
            return;
        }

        this.pickBones(Collections.singletonList(bone));
    }

    protected void pickBones(List<String> bones)
    {
        if (bones == null || bones.isEmpty())
        {
            lastLimb = "";
            this.fix.setValue(0F);
            this.color.setColor(Colors.WHITE);
            this.lighting.setValue(false);
            this.transform.setTransform(null);

            return;
        }

        String primary = bones.get(0);

        lastLimb = primary;

        PoseTransform poseTransform = this.pose.get(primary);

        this.fix.setValue(poseTransform.fix);
        this.color.setColor(poseTransform.color.getARGBColor());
        this.lighting.setValue(poseTransform.lighting == 0F);
        this.transform.setTransform(poseTransform);
    }

    private void forEachSelectedPose(Consumer<? super PoseTransform> consumer)
    {
        for (String bone : this.groups.getCurrent())
        {
            consumer.accept(this.pose.get(bone));
        }
    }

    /** How a single bone should receive an edit: reflected onto its left/right
     *  counterpart ({@link #mirror}) and/or with its rotation flipped ({@link #invert}). */
    public static class BoneEdit
    {
        public final boolean mirror;
        public final boolean invert;

        public BoneEdit(boolean mirror, boolean invert)
        {
            this.mirror = mirror;
            this.invert = invert;
        }
    }

    /**
     * Bones an edit should touch and how. Selected bones are drivers; with
     * {@code invert} on, every second selected bone (2nd, 4th, ... in selection
     * order) has its rotation flipped. With {@code mirror} on, each driver's
     * left/right counterpart is added reflected across the model's symmetry
     * &mdash; even when unselected &mdash; so editing one bone mirrors onto its
     * pair live. A counterpart that is itself selected stays a driver (never
     * double-applied). Shared by the model panel and film pose editors.
     */
    public Map<String, BoneEdit> resolveBoneEdits(boolean mirror, boolean invert)
    {
        Map<String, BoneEdit> edits = new LinkedHashMap<>();
        List<String> selected = this.groups.getCurrent();

        for (int i = 0; i < selected.size(); i++)
        {
            edits.put(selected.get(i), new BoneEdit(false, invert && i % 2 == 1));
        }

        if (mirror)
        {
            for (String bone : new ArrayList<>(edits.keySet()))
            {
                String partner = this.mirrorPartner(bone);

                if (partner != null && !edits.containsKey(partner))
                {
                    edits.put(partner, new BoneEdit(true, false));
                }
            }
        }

        return edits;
    }

    /**
     * The opposite-side counterpart of a bone (the model's flip map first, then
     * the left/right name patterns), or null when it has none or the resolved
     * name isn't an actual bone.
     */
    private String mirrorPartner(String bone)
    {
        String partner = null;

        if (this.flippedParts != null && !this.flippedParts.isEmpty())
        {
            partner = this.flippedParts.get(bone);

            if (partner == null)
            {
                for (Map.Entry<String, String> entry : this.flippedParts.entrySet())
                {
                    if (bone.equals(entry.getValue()))
                    {
                        partner = entry.getKey();

                        break;
                    }
                }
            }
        }

        if (partner == null)
        {
            String mirrored = Pose.getMirrorName(bone);

            partner = mirrored.equals(bone) ? null : mirrored;
        }

        return partner != null && this.groups.getList().contains(partner) ? partner : null;
    }

    /**
     * Applies the edit to one bone: reflecting it across the model's symmetry when
     * {@code edit.mirror} (the same negation as {@link Pose#flip}), and/or flipping
     * its rotation when {@code edit.invert}. Both are involutions wrapped around the
     * write, so whatever the edit does to that channel is reflected/inverted.
     */
    public void applyToBone(BoneEdit edit, PoseTransform pt, Consumer<Transform> consumer)
    {
        if (edit.mirror)
        {
            mirrorTransform(pt);
        }

        if (edit.invert)
        {
            negateRotation(pt);
        }

        consumer.accept(pt);

        if (edit.invert)
        {
            negateRotation(pt);
        }

        if (edit.mirror)
        {
            mirrorTransform(pt);
        }
    }

    private static void mirrorTransform(Transform transform)
    {
        transform.translate.mul(-1F, 1F, 1F);
        transform.rotate.mul(1F, -1F, -1F);
    }

    private static void negateRotation(Transform transform)
    {
        transform.rotate.mul(-1F, -1F, -1F);
    }

    private void applyFixToSelection(float value)
    {
        this.forEachSelectedPose((pt) -> this.setFix(pt, value));
        this.fix.setValue(value);
    }

    private void applyColorToSelection(int argb)
    {
        this.forEachSelectedPose((pt) -> this.setColor(pt, argb));
        this.color.setColor(argb);
    }

    private void applyLightingToSelection(boolean value)
    {
        this.forEachSelectedPose((pt) -> this.setLighting(pt, value));
        this.lighting.setValue(value);
    }

    private void toggleFix()
    {
        if (this.groups.getCurrent().isEmpty())
        {
            return;
        }

        float next = this.fix.getValue() >= 0.5F ? 0F : 1F;

        this.applyFixToSelection(next);
    }

    protected void setFix(PoseTransform transform, float value)
    {
        transform.fix = value;
    }

    protected void setColor(PoseTransform transform, int value)
    {
        transform.color.set(value);
    }

    protected void setLighting(PoseTransform poseTransform, boolean value)
    {
        poseTransform.lighting = value ? 0F : 1F;
    }
}
