package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.utils.pose.Pose;

public class MobForm extends Form implements PoseForm
{
    public final ValueString mobID = new ValueString("mobId", "minecraft:chicken");
    public final ValueString mobNBT = new ValueString("mobNbt", "");

    public final ValueLink texture = new ValueLink("texture", null);
    public final ValueBoolean slim = new ValueBoolean("slim", false);

    public final ValuePose pose = new ValuePose("pose", new Pose());
    public final ValuePose poseOverlay = new ValuePose("pose_overlay", new Pose());
    public final ValueBoolean boneTracks = new ValueBoolean("bone_tracks", true);

    public MobForm()
    {
        this.slim.invisible();

        this.add(this.mobID);
        this.add(this.mobNBT);
        this.add(this.pose);
        this.add(this.poseOverlay);
        this.boneTracks.invisible();
        this.add(this.boneTracks);
        this.add(this.texture);
        this.add(this.slim);
    }

    @Override
    public ValuePose getPose()
    {
        return this.pose;
    }

    @Override
    public ValuePose getPoseOverlay()
    {
        return this.poseOverlay;
    }

    @Override
    public ValueBoolean getBoneTracks()
    {
        return this.boneTracks;
    }

    @Override
    protected String getDefaultDisplayName()
    {
        return this.mobID.get().isEmpty() ? super.getDefaultDisplayName() : this.mobID.get();
    }

    public boolean isPlayer()
    {
        return this.mobID.get().equals("minecraft:player");
    }
}
