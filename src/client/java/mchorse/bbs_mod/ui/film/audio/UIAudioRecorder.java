package mchorse.bbs_mod.ui.film.audio;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.audio.wav.WaveWriter;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.interps.Lerps;
import org.lwjgl.glfw.GLFW;

import java.io.File;

public class UIAudioRecorder extends UIElement
{
    private static String lastInput = "";

    private final OpenALRecorder recorder;
    private float volume;
    private float[][] waveform;

    public UIAudioRecorder(OpenALRecorder recorder)
    {
        this.recorder = recorder;

        this.eventPropagataion(EventPropagation.BLOCK);
    }

    public static void addOption(UIFilmPanel filmPanel, ContextMenuManager menu)
    {
        UIContext context = filmPanel.getContext();
        String suggestion = suggestAudioName(filmPanel);
        String value = lastInput.isEmpty() ? suggestion : lastInput;

        menu.action(Icons.SOUND, UIKeys.CAMERA_TIMELINE_CONTEXT_RECORD_MICROPHONE, () ->
        {
            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.CAMERA_TIMELINE_CONTEXT_RECORD_MICROPHONE_TITLE,
                UIKeys.CAMERA_TIMELINE_CONTEXT_RECORD_MICROPHONE_DESCRIPTION,
                (t) ->
                {
                    String newT = t.isEmpty() ? suggestion : t;

                    UIElement overlay = context.menu.overlay;
                    OpenALRecorder recorder = new OpenALRecorder((wave) ->
                    {
                        try
                        {
                            File file = new File(BBSMod.getAudioFolder(), newT + ".wav");
                            AudioClientClip clip = new AudioClientClip();
                            Clips clips = filmPanel.cameraEditor.clips.getClips();

                            file.getParentFile().mkdirs();
                            WaveWriter.write(file, wave);
                            clip.audio.set(Link.assets("audio/" + newT + ".wav"));
                            clip.duration.set((int) (wave.getDuration() * 20));
                            clip.layer.set(clips.getTopLayer() + 1);

                            clips.addClip(clip);
                            filmPanel.cameraEditor.clips.clearSelection();
                            filmPanel.cameraEditor.clips.pickClip(clip);

                            lastInput = newT.equals(value) ? "" : newT;
                        }
                        catch (Exception e)
                        {}
                    });
                    UIAudioRecorder audioRecorder = new UIAudioRecorder(recorder);

                    audioRecorder.full(overlay);
                    audioRecorder.resize();
                    overlay.add(audioRecorder);

                    Thread thread = new Thread(recorder, "Супер классный, я записываю твой микрофон хихихи :3");

                    thread.start();
                }
            );

            panel.text.setText(value);
            panel.text.path();

            UIOverlay.addOverlay(context, panel);
        });
    }

    /**
     * Default recording name: the film's id (which carries its folder path) followed by the
     * first free trailing number, so repeated recordings of the same film don't clash. Falls
     * back to a timestamp when no film is loaded or it has no id yet.
     */
    private static String suggestAudioName(UIFilmPanel filmPanel)
    {
        Film film = filmPanel.getData();
        String base = film == null ? null : film.getId();

        if (base == null || base.isEmpty())
        {
            return StringUtils.createTimestampFilename();
        }

        File folder = BBSMod.getAudioFolder();
        int number = 1;

        while (new File(folder, base + "/" + number + ".wav").exists())
        {
            number++;
        }

        return base + "/" + number;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.recorder.stop();
            context.render.postRunnable(this::removeFromParent);

            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        this.volume = Lerps.lerp(this.volume, Interpolations.CUBIC_OUT.interpolate(0F, 1F, this.recorder.getVolume()), 0.5F);

        String label = UIKeys.CAMERA_TIMELINE_CONTEXT_RECORD_MICROPHONE_LABEL
            .format(this.recorder.getTime() / 1000F)
            .get();
        int x = this.area.mx();
        int y = this.area.my();
        int w = context.batcher.getFont().getWidth(label);
        double volume = Interpolations.EXP_OUT.interpolate(0F, 1F, this.volume);

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A50);
        context.batcher.icon(Icons.SPHERE, Colors.RED | Colors.A100, x - w / 2 - 12, y + context.batcher.getFont().getHeight() / 2, 0.5F, 0.5F);
        context.batcher.textShadow(label, x - w / 2, y);

        label = UIKeys.CAMERA_TIMELINE_CONTEXT_RECORD_MICROPHONE_SUBLABEL.get();
        w = context.batcher.getFont().getWidth(label);

        context.batcher.textShadow(label, x - w / 2, this.area.y(0.75F));

        x -= w / 2;

        context.batcher.box(x, y + 16, x + w, y + 20, Colors.A100);
        context.batcher.box(x, y + 16, x + (int) (w * volume), y + 20, Colors.WHITE);

        this.renderWaveform(context, x, y + 24, w, 28);

        super.render(context);
    }

    /**
     * Live incoming microphone waveform: a mirrored peak envelope scrolling in from the
     * right, clamped to the same width as the volume bar above it.
     */
    private void renderWaveform(UIContext context, int x, int top, int w, int h)
    {
        if (w <= 0)
        {
            return;
        }

        this.waveform = this.recorder.getWaveform(this.waveform);

        float[] peak = this.waveform[0];
        float[] average = this.waveform[1];
        int n = peak.length;
        int mid = top + h / 2;
        int half = h / 2;
        int averageColor = Colors.mulRGB(Colors.WHITE, 0.8F);

        context.batcher.box(x, top, x + w, top + h, Colors.A50);

        for (int px = 0; px < w; px++)
        {
            int idx = Math.min(n - 1, (int) (px / (float) w * n));
            int cx = x + px;

            /* Bright peak envelope, then the darker mean amplitude on top as an inner core,
             * matching mchorse.bbs_mod.audio.Waveform's max/average layering. */
            int peakAmp = (int) (Interpolations.EXP_OUT.interpolate(0F, 1F, Math.min(1F, peak[idx])) * half);
            int avgAmp = (int) (Interpolations.EXP_OUT.interpolate(0F, 1F, Math.min(1F, average[idx])) * half);

            context.batcher.box(cx, mid - peakAmp, cx + 1, mid + peakAmp + 1, Colors.WHITE);
            context.batcher.box(cx, mid - avgAmp, cx + 1, mid + avgAmp + 1, averageColor);
        }
    }
}