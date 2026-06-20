package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.VideoRecorder;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderTickCounter.Dynamic.class)
public class RenderTickCounterMixin
{
    @Shadow
    public float tickProgress;

    @Shadow
    public float dynamicDeltaTicks;

    @Shadow
    private long lastTimeMillis;

    private int heldFrames;

    private long lastFrameTime;

    @Inject(method = "beginRenderTick", at = @At("HEAD"), cancellable = true)
    public void onBeginRenderTick(long timeMillis, boolean tick, CallbackInfoReturnable<Integer> info)
    {
        VideoRecorder videoRecorder = BBSModClient.getVideoRecorder();

        if (videoRecorder.isRecording())
        {
            if (videoRecorder.getCounter() == 0)
            {
                this.tickProgress = 0;
            }

            if (this.heldFrames == 0)
            {
                // TODO(1.21.11 render merge): video frame-rate LIMITING — re-port against 1.21.11 RenderTickCounter.
                // (was: when BBSSettings.videoLimitFrameRate was on, the recording was throttled to wall-clock by
                //  holding the frame — BBSRendering.canRender=false, info.setReturnValue(0), return — until
                //  frameInterval = 1000/getVideoFrameRate() ms had elapsed since lastFrameTime. It advanced the OLD
                //  RenderTickCounter fields tickDelta/lastFrameDuration/prevTimeMillis, which no longer exist; the
                //  1.21.11 Dynamic counter uses tickProgress/dynamicDeltaTicks/lastTimeMillis instead. The hold/skip
                //  logic must be re-expressed against those shadowed fields. BBSSettings.videoLimitFrameRate +
                //  BBSRendering.canRender still exist, and lastFrameTime is already a field on this mixin.)
                this.dynamicDeltaTicks = 20F / (float) BBSRendering.getVideoFrameRate();
                this.lastTimeMillis = timeMillis;
                this.tickProgress += this.dynamicDeltaTicks;

                int i = (int) this.tickProgress;

                this.tickProgress -= (float) i;

                videoRecorder.serverTicks += i;
                BBSRendering.canRender = true;

                info.setReturnValue(i);
            }
            else
            {
                BBSRendering.canRender = false;

                info.setReturnValue(0);
            }

            this.heldFrames += 1;

            if (this.heldFrames >= BBSSettings.videoHeldFrames.get())
            {
                this.heldFrames = 0;
            }
        }
        else
        {
            this.heldFrames = 0;
            this.lastFrameTime = 0;
        }
    }
}