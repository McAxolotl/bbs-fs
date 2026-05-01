package mchorse.bbs_mod.ui.film.controller;

import java.util.HashMap;
import java.util.Map;

import org.joml.Intersectiond;
import org.joml.Intersectionf;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.joml.Vectors;

public class OrbitFilmCameraController implements ICameraController
{
    private final UIFilmController controller;

    public boolean enabled;
    private boolean bindToReplay = true;

    private boolean orbiting;
    private boolean centering;
    private int orbitButton = -1;
    private final Vector2f rotation = new Vector2f();
    private final Vector2i last = new Vector2i();

    /* Bound orbit stores an offset relative to replay's anchor. */
    private final Vector3f boundOffset = new Vector3f();
    /* Free orbit stores a world-space pivot point. */
    private final Vector3f freePivot = new Vector3f();
    private final FreePanState freePanState = new FreePanState();

    private float distance;
    private float offsetY;

    protected final Vector3i velocityPosition = new Vector3i();
    private final Map<String, OrbitState> replayStates = new HashMap<>();

    public OrbitFilmCameraController(UIFilmController controller)
    {
        this.controller = controller;
        this.reset();
    }

    public void start(UIContext context)
    {
        if (!this.canStart(context))
        {
            return;
        }

        this.orbitButton = context.mouseButton;
        this.orbiting = true;
        this.centering = this.bindToReplay && this.orbitButton == 0;
        this.last.set(context.mouseX, context.mouseY);

        if (this.isFreePanning())
        {
            this.cacheFreePanState(context);
        }
        else if (this.centering)
        {
            this.cacheCenteringState();
        }
    }

    public void stop()
    {
        if (this.centering)
        {
            this.applyCenteringOffset();
        }

        this.orbiting = false;
        this.orbitButton = -1;
        this.centering = false;
    }

    public boolean keyPressed(UIContext context, Area area)
    {
        if (!this.enabled || context.isFocused())
        {
            return false;
        }

		if (area.isInside(context) || (!this.velocityPosition.equals(0, 0, 0) && context.getKeyAction() == KeyAction.RELEASED))
		{
			if (BBSSettings.editorOrbitMovementRequiresFlight.get() && !this.controller.panel.isFlying())
			{
				return false;
			}

			int x = this.getFactor(context, Keys.FLIGHT_LEFT, Keys.FLIGHT_RIGHT, this.velocityPosition.x);
            int y = this.getFactor(context, Keys.FLIGHT_UP, Keys.FLIGHT_DOWN, this.velocityPosition.y);
            int z = this.getFactor(context, Keys.FLIGHT_FORWARD, Keys.FLIGHT_BACKWARD, this.velocityPosition.z);
            boolean changed = x != this.velocityPosition.x || y != this.velocityPosition.y || z != this.velocityPosition.z;

            this.velocityPosition.set(x, y, z);

            return changed;
        }

        return false;
    }

    protected int getFactor(UIContext context, KeyCombo positive, KeyCombo negative, int x)
    {
        if (context.isPressed(positive.getMainKey()))
        {
            return 1;
        }
        else if (context.isPressed(negative.getMainKey()))
        {
            return -1;
        }
        else if (
            (context.isReleased(positive.getMainKey()) && x > 0) ||
            (context.isReleased(negative.getMainKey()) && x < 0)
        ) {
            return 0;
        }

        return x;
    }

    public void handleOrbiting(UIContext context)
    {
        if (!this.orbiting)
        {
            return;
        }

        int x = context.mouseX;
        int y = context.mouseY;
        int dx = x - this.last.x;
        int dy = y - this.last.y;

        if (this.orbitButton == 2)
        {
            if (this.bindToReplay)
            {
                this.panBound(dx, dy);
            }
            else
            {
                this.panFree(context);
            }
        }
        else
        {
            this.rotate(dx, dy);
        }

        this.last.set(x, y);
    }

    public boolean zoom(double mouseWheel)
    {
        if (!this.enabled || this.controller.panel.isFlying() || mouseWheel == 0D)
        {
            return false;
        }

        float zoomMultiplier = Window.isCtrlPressed() ? 18F : 6F;
        float zoomStep = this.getSpeed() * zoomMultiplier;
        float signedZoom = (float) Math.copySign(zoomStep, mouseWheel);

        if (!this.bindToReplay || this.centering)
        {
            this.distance = MathUtils.clamp(this.distance - signedZoom, 0.5F, 256F);

            return true;
        }

        float length = this.boundOffset.length();

        if (length <= 0.0001F)
        {
            this.boundOffset.set(0F, 0F, -1F);
            length = 1F;
        }

        float newLength = MathUtils.clamp(length - signedZoom, 0.5F, 256F);

        this.boundOffset.mul(newLength / length);
        this.distance = newLength;

        return true;
    }

    public boolean update(UIContext context)
    {
        if (!this.enabled || context.isFocused())
        {
            return false;
        }

		if (BBSSettings.editorOrbitMovementRequiresFlight.get() && !this.controller.panel.isFlying())
		{
			this.velocityPosition.set(0, 0, 0);

			return false;
		}

		boolean changed = false;

		if (this.velocityPosition.lengthSquared() > 0 && !this.centering)
        {
            Vector3f delta = this.rotateVector(-this.velocityPosition.x, this.velocityPosition.y, -this.velocityPosition.z, this.rotation.y, this.rotation.x).mul(this.getSpeed());

            if (this.bindToReplay)
            {
                this.boundOffset.add(delta);
            }
            else
            {
                this.freePivot.add(delta);
            }

            changed = true;
        }
        else if (this.centering)
        {
            this.applyCenteringOffset();
        }

        return changed;
    }

    protected float getSpeed()
    {
        return this.controller.panel.dashboard.orbit.getSpeed();
    }

    protected Vector3f rotateVector(float x, float y, float z, float yaw, float pitch)
    {
        return this.rotateVector(x, y, z, yaw, pitch, BBSSettings.editorHorizontalFlight.get());
    }

    protected Vector3f rotateVector(float x, float y, float z, float yaw, float pitch, boolean horizontal)
    {
        Matrix3f rotation = new Matrix3f();
        Vector3f rotate = new Vector3f(x, y, z);

        rotation.rotateY(yaw);

        if (!horizontal)
        {
            rotation.rotateX(pitch);
        }

        rotation.transform(rotate);

        return rotate;
    }

    private Vector3d calculateOnPlane(UIContext context)
    {
        Area viewport = this.controller.panel.preview.getViewport();
        Vector3d vector = new Vector3d();
        Vector3d origin = new Vector3d(this.freePanState.camera.position).sub(this.freePanState.pivot.x, this.freePanState.pivot.y, this.freePanState.pivot.z);
        Vector3d destination = new Vector3d(
            this.freePanState.camera.getMouseDirection(context.mouseX, context.mouseY, viewport.x, viewport.y, viewport.w, viewport.h)
        ).mul(Math.max(this.distance, 0.5F) * 2F).add(origin);

        Intersectiond.intersectLineSegmentPlane(
            origin.x,
            origin.y,
            origin.z,
            destination.x,
            destination.y,
            destination.z,
            this.freePanState.plane.x,
            this.freePanState.plane.y,
            this.freePanState.plane.z,
            0,
            vector
        );

        return vector;
    }

    @Override
    public void setup(Camera camera, float transition)
    {
        if (!this.bindToReplay)
        {
            Vector3f offset = this.getFreeOffset();

            camera.position.set(this.freePivot);
            camera.position.add(offset);
            camera.rotation.set(-this.rotation.x, -this.rotation.y, 0F);
            camera.fov = BBSSettings.getFov();

            return;
        }

        OrbitTarget target = this.getOrbitTarget(transition);

        if (target != null)
        {
            float renderYaw = target.renderYaw;
            Vector3f offset = this.rotateVector(this.boundOffset.x, this.boundOffset.y, this.boundOffset.z, renderYaw, 0F);

            if (this.centering)
            {
                offset = this.rotateVector(0F, 0F, 1F, this.rotation.y + renderYaw, this.rotation.x, false).mul(this.distance);
                offset.add(0, this.offsetY, 0);
            }

            camera.position.set(target.position);
            camera.position.add(offset);
            camera.rotation.set(-this.rotation.x, -(this.rotation.y + renderYaw), 0);
            camera.fov = BBSSettings.getFov();
        }
    }

    @Override
    public int getPriority()
    {
        return 20;
    }

    public boolean isBindToReplay()
    {
        return this.bindToReplay;
    }

    public Vector3d getOrbitCenter(float transition)
    {
        if (!this.bindToReplay)
        {
            return new Vector3d(this.freePivot);
        }

        OrbitTarget target = this.getOrbitTarget(transition);

        return target == null ? null : new Vector3d(target.position);
    }

    public void toggleBindToReplay()
    {
        this.setBindToReplay(!this.bindToReplay);
    }

    public void teleportPivotToReplay()
    {
        if (this.bindToReplay)
        {
            return;
        }

        OrbitTarget target = this.getOrbitTarget(this.getCurrentTransition());

        if (target == null)
        {
            return;
        }

        this.freePivot.set((float) target.position.x, (float) target.position.y, (float) target.position.z);
    }

    public void setBindToReplay(boolean bindToReplay)
    {
        if (this.bindToReplay == bindToReplay)
        {
            return;
        }

        Camera camera = this.controller.panel.getCamera();
        float transition = this.getCurrentTransition();

        if (bindToReplay)
        {
            OrbitTarget target = this.getOrbitTarget(transition);

            if (camera != null && target != null)
            {
                Vector3f offset = new Vector3f(
                    (float) (camera.position.x - target.position.x),
                    (float) (camera.position.y - target.position.y),
                    (float) (camera.position.z - target.position.z)
                );

                this.boundOffset.set(this.rotateVector(offset.x, offset.y, offset.z, -target.renderYaw, 0F, false));
                this.rotation.set(-camera.rotation.x, -camera.rotation.y - target.renderYaw);
                this.distance = Math.max(0.5F, this.boundOffset.length());
                this.offsetY = 0F;
            }
        }
        else if (camera != null)
        {
            this.rotation.set(-camera.rotation.x, -camera.rotation.y);
            OrbitTarget target = this.getOrbitTarget(transition);

            if (target != null)
            {
                this.freePivot.set((float) target.position.x, (float) target.position.y, (float) target.position.z);
                this.distance = MathUtils.clamp((float) camera.position.distance(target.position.x, target.position.y, target.position.z), 0.5F, 256F);
            }
            else
            {
                this.distance = MathUtils.clamp(Math.max(this.distance, 4F), 0.5F, 256F);

                Vector3f offset = this.getFreeOffset();

                this.freePivot.set(
                    (float) (camera.position.x - offset.x),
                    (float) (camera.position.y - offset.y),
                    (float) (camera.position.z - offset.z)
                );
            }
        }

        this.bindToReplay = bindToReplay;
        this.centering = false;
    }

    public void saveReplayState(Replay replay)
    {
        if (replay == null)
        {
            return;
        }

        this.replayStates.put(replay.getId(), new OrbitState(this.boundOffset, this.freePivot, this.rotation, this.distance, this.bindToReplay));
    }

    public void restoreReplayState(Replay replay, boolean resetIfMissing)
    {
        if (replay == null)
        {
            if (resetIfMissing)
            {
                this.reset();
            }

            return;
        }

        OrbitState state = this.replayStates.get(replay.getId());

        if (state == null)
        {
            if (resetIfMissing)
            {
                this.reset();
            }

            return;
        }

        this.boundOffset.set(state.boundOffset);
        this.freePivot.set(state.freePivot);
        this.rotation.set(state.rotation);
        this.distance = state.distance;
        this.bindToReplay = state.bindToReplay;
        this.centering = false;
        this.offsetY = 0F;
    }

    public void clearReplayStates()
    {
        this.replayStates.clear();
    }

    public void reset()
    {
        this.boundOffset.set(0F, 0F, -4F);
        this.freePivot.set(0F, 0F, 0F);
        this.rotation.set(0F, Math.PI);
        this.distance = this.boundOffset.length();
        this.offsetY = 0F;
        this.bindToReplay = true;
        this.orbiting = false;
        this.orbitButton = -1;
        this.centering = false;
        this.velocityPosition.set(0, 0, 0);
    }

    private OrbitTarget getOrbitTarget(float transition)
    {
        IEntity entity = this.controller.getCurrentEntity();

        if (entity == null)
        {
            return null;
        }

        float renderYaw = MathUtils.toRad(-Lerps.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), transition) + 180F);
        Form form = entity.getForm();
        double h = entity.getPickingHitbox().h / 2;
        double x = Lerps.lerp(entity.getPrevX(), entity.getX(), transition);
        double y = Lerps.lerp(entity.getPrevY(), entity.getY(), transition) + h;
        double z = Lerps.lerp(entity.getPrevZ(), entity.getZ(), transition);

        if (form != null)
        {
            MatrixCache map = FormUtilsClient.getRenderer(form).collectMatrices(entity, transition);
            String group = "anchor";

            if (form instanceof ModelForm modelForm)
            {
                ModelInstance model = ModelFormRenderer.getModel(modelForm);

                if (model != null)
                {
                    String anchor = model.getAnchor();

                    group = anchor.isEmpty() ? group : anchor;
                }
            }

            Matrix4f anchor = map.get(group).matrix();

            if (anchor != null)
            {
                Anchor v = form.anchor.get();
                Matrix4f defaultMatrix = BaseFilmController.getMatrixForRenderWithRotation(entity, x, y, z, transition);
                Pair<Matrix4f, Float> totalMatrix = BaseFilmController.getTotalMatrix(this.controller.getEntities(), v, defaultMatrix, x, y, z, transition, 0);

                if (totalMatrix.a != null)
                {
                    defaultMatrix = totalMatrix.a;
                }

                defaultMatrix.mul(anchor);

                Vector3f translate = defaultMatrix.getTranslation(Vectors.TEMP_3F);

                x += translate.x;
                y += translate.y;
                z += translate.z;
            }
        }

        return new OrbitTarget(new Vector3d(x, y, z), renderYaw);
    }

    private boolean canStart(UIContext context)
    {
        if (this.controller.panel.isFlying())
        {
            return context.mouseButton == 0;
        }

        return context.mouseButton == 0 || context.mouseButton == 2;
    }

    private boolean isFreePanning()
    {
        return !this.bindToReplay && this.orbitButton == 2;
    }

    private void cacheCenteringState()
    {
        Vector3f rayDirection = this.rotateVector(0F, 0F, -1F, this.rotation.y, this.rotation.x, false);
        Vector3f normal = Vectors.TEMP_3F.set(rayDirection).mul(-1F, 0F, -1F).normalize();

        float t = Intersectionf.intersectRayPlane(this.boundOffset, rayDirection, new Vector3f(0, this.offsetY, 0), normal, 0.0001F);
        Vector3f point = new Vector3f(rayDirection).mul(t).add(this.boundOffset);

        point.x = 0;
        point.z = 0;

        this.distance = this.boundOffset.distance(point);
        this.offsetY = point.y;
    }

    private void applyCenteringOffset()
    {
        this.boundOffset.set(this.rotateVector(0F, 0F, 1F, this.rotation.y, this.rotation.x, false).mul(this.distance));
        this.boundOffset.add(0F, this.offsetY, 0F);
    }

    private void cacheFreePanState(UIContext context)
    {
        this.freePanState.pivot.set(this.freePivot);
        this.freePanState.camera.copy(this.controller.panel.getCamera());
        this.freePanState.plane.set(this.freePanState.camera.getLookDirection()).normalize();
        this.freePanState.intersection.set(this.calculateOnPlane(context));
    }

    private void panBound(int dx, int dy)
    {
        float panFactor = Math.max(0.01F, Math.max(this.distance, this.boundOffset.length()) / 300F);
        Vector3f right = this.rotateVector(1F, 0F, 0F, this.rotation.y, this.rotation.x, false);
        Vector3f up = this.rotateVector(0F, 1F, 0F, this.rotation.y, this.rotation.x, false);

        this.boundOffset.fma(-dx * panFactor, right).fma(dy * panFactor, up);
    }

    private void panFree(UIContext context)
    {
        Vector3d point = this.calculateOnPlane(context);

        this.freePivot.set(this.freePanState.pivot);
        this.freePivot.sub((float) point.x, (float) point.y, (float) point.z);
        this.freePivot.add((float) this.freePanState.intersection.x, (float) this.freePanState.intersection.y, (float) this.freePanState.intersection.z);
    }

    private void rotate(int dx, int dy)
    {
        float orbitSpeed = this.controller.panel.dashboard.orbit.getAngleSpeed() * 4F;

        this.rotation.add(-dy * orbitSpeed, -dx * orbitSpeed);
    }

    private Vector3f getFreeOffset()
    {
        return this.rotateVector(0F, 0F, 1F, this.rotation.y, this.rotation.x, false).mul(this.distance);
    }

    private float getCurrentTransition()
    {
        UIContext context = this.controller.getContext();

        return context == null ? 0F : context.getTransition();
    }

    private record OrbitState(Vector3f boundOffset, Vector3f freePivot, Vector2f rotation, float distance, boolean bindToReplay)
    {
        private OrbitState(Vector3f boundOffset, Vector3f freePivot, Vector2f rotation, float distance, boolean bindToReplay)
        {
            this.boundOffset = new Vector3f(boundOffset);
            this.freePivot = new Vector3f(freePivot);
            this.rotation = new Vector2f(rotation);
            this.distance = distance;
            this.bindToReplay = bindToReplay;
        }
    }

    private record OrbitTarget(Vector3d position, float renderYaw)
    {}

    private static class FreePanState
    {
        private final Vector3f pivot = new Vector3f();
        private final Camera camera = new Camera();
        private final Vector3d plane = new Vector3d();
        private final Vector3d intersection = new Vector3d();
    }
}
