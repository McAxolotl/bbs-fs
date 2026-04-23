package mchorse.bbs_mod.ui.dashboard.textures;

import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.textures.undo.PixelsUndo;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UICanvasEditor;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.interps.rasterizers.LineRasterizer;
import mchorse.bbs_mod.utils.resources.Pixels;
import mchorse.bbs_mod.utils.undo.IUndo;
import mchorse.bbs_mod.utils.undo.UndoManager;
import org.joml.Vector2d;
import org.joml.Vector2i;
import org.lwjgl.opengl.GL11;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UIPixelsEditor extends UICanvasEditor
{
    public UIElement toolbar;

    private int brushSize = 1;

    private Texture temporary;
    private Pixels pixels;

    private boolean editing;
    private Color drawColor;
    private Vector2i lastPixel;

    protected UndoManager<Pixels> undoManager;
    private PixelsUndo pixelsUndo;

    private Supplier<Float> backgroundSupplier = () -> 0.7F;
    private Supplier<Color> colorSupplier = Color::white;
    private Consumer<Color> pickColorConsumer = (c) -> {};

    private Supplier<TexturePaintTool> toolSupplier = () -> TexturePaintTool.BRUSH;

    public UIPixelsEditor()
    {
        super();

        this.toolbar = new UIElement();
        this.toolbar.relative(this).w(1F).h(30).row(0).resize().padding(5);

        this.add(this.toolbar);

        IKey category = UIKeys.TEXTURES_KEYS_CATEGORY;
        Supplier<Boolean> texture = () -> this.pixels != null;
        Supplier<Boolean> editing = () -> this.editing;

        this.keys().register(Keys.COPY, this::copyPixel).label(UIKeys.TEXTURES_VIEWER_CONTEXT_COPY_HEX).inside().active(texture).category(category);
        this.keys().register(Keys.UNDO, this::undo).inside().active(editing).category(category);
        this.keys().register(Keys.REDO, this::redo).inside().active(editing).category(category);

        this.setEditing(false);
    }

    public UIPixelsEditor colorSupplier(Supplier<Color> supplier)
    {
        this.colorSupplier = supplier;

        return this;
    }

    protected Color getActiveDrawColor()
    {
        return this.colorSupplier.get();
    }

    public UIPixelsEditor backgroundSupplier(Supplier<Float> supplier)
    {
        this.backgroundSupplier = supplier;

        return this;
    }

    public UIPixelsEditor pickColorConsumer(Consumer<Color> consumer)
    {
        this.pickColorConsumer = consumer != null ? consumer : (c) -> {};

        return this;
    }

    public Pixels getPixels()
    {
        return this.pixels;
    }

    public int getBrushSize()
    {
        return this.brushSize;
    }

    public UIPixelsEditor setBrushSize(int size)
    {
        this.brushSize = MathUtils.clamp(size, 1, 256);

        return this;
    }

    public UIPixelsEditor toolSupplier(Supplier<TexturePaintTool> supplier)
    {
        this.toolSupplier = supplier != null ? supplier : () -> TexturePaintTool.BRUSH;

        return this;
    }

    protected TexturePaintTool getActivePaintTool()
    {
        TexturePaintTool tool = this.toolSupplier.get();

        return tool == null ? TexturePaintTool.BRUSH : tool;
    }

    /**
     * Invoked for the flood-fill tool on LMB down. Default does nothing;
     * {@link UITextureEditor} performs {@link UITextureEditor#fillColor}.
     */
    protected void onFillAt(Vector2i pixel)
    {}

    private boolean isStrokePaintTool()
    {
        TexturePaintTool t = this.getActivePaintTool();

        return t == TexturePaintTool.BRUSH || t == TexturePaintTool.ERASER;
    }

    private void paint(int x, int y)
    {
        int left = (this.brushSize - 1) / 2;
        int right = this.brushSize / 2;

        for (int dx = -left; dx <= right; dx++)
        {
            for (int dy = -left; dy <= right; dy++)
            {
                this.pixelsUndo.setColor(this.pixels, x + dx, y + dy, this.drawColor);
            }
        }
    }

    protected void wasChanged()
    {}

    public boolean isEditing()
    {
        return this.editing;
    }

    public void toggleEditor()
    {
        this.setEditing(!this.editing);
    }

    public void setEditing(boolean editing)
    {
        this.editing = editing;

        this.toolbar.setVisible(editing);

        if (editing)
        {
            this.undoManager = new UndoManager<>();
            this.undoManager.setCallback(this::handleUndo);
        }
        else
        {
            this.undoManager = null;
        }

        this.pixelsUndo = null;
    }

    private void handleUndo(IUndo<Pixels> pixelsIUndo, boolean redo)
    {
        this.updateTexture();
    }

    private void copyPixel()
    {
        UIContext context = this.getContext();
        int pixelX = (int) Math.floor(this.scaleX.from(context.mouseX)) + this.w / 2;
        int pixelY = (int) Math.floor(this.scaleY.from(context.mouseY)) + this.h / 2;
        Color color = this.pixels.getColor(pixelX, pixelY);

        if (color != null)
        {
            Window.setClipboard(color.stringify());

            UIUtils.playClick();
        }
    }

    protected void updateTexture()
    {
        this.pixels.rewindBuffer();
        this.temporary.bind();
        this.temporary.updateTexture(this.pixels);
    }

    public void undo()
    {
        if (this.undoManager != null && this.undoManager.undo(this.pixels))
        {
            UIUtils.playClick();
        }
    }

    public void redo()
    {
        if (this.undoManager != null && this.undoManager.redo(this.pixels))
        {
            UIUtils.playClick();
        }
    }

    public void deleteTexture()
    {
        if (this.temporary != null)
        {
            this.temporary.delete();
            this.temporary = null;
        }
    }

    public void fillPixels(Pixels pixels)
    {
        this.lastPixel = null;

        this.deleteTexture();
        this.setEditing(false);

        this.pixels = pixels;

        if (pixels != null)
        {
            this.temporary = new Texture();
            this.temporary.setFilter(GL11.GL_NEAREST);

            this.updateTexture();
            this.setSize(pixels.width, pixels.height);
        }
    }

    @Override
    protected boolean isMouseButtonAllowed(int mouseButton)
    {
        return super.isMouseButtonAllowed(mouseButton);
    }

    @Override
    protected void startDragging(UIContext context)
    {
        super.startDragging(context);

        if (!this.editing || this.mouse != 0 || this.pixelsUndo != null)
        {
            return;
        }

        TexturePaintTool tool = this.getActivePaintTool();

        if (tool == TexturePaintTool.FILL)
        {
            Vector2i pixel = this.getHoverPixel(context.mouseX, context.mouseY);

            this.onFillAt(pixel);

            return;
        }

        if (tool == TexturePaintTool.PIPETTE)
        {
            Vector2i pixel = this.getHoverPixel(context.mouseX, context.mouseY);
            Color color = this.pixels.getColor(pixel.x, pixel.y);

            if (color != null)
            {
                this.pickColorConsumer.accept(color);
            }

            return;
        }

        if (this.isStrokePaintTool())
        {
            this.pixelsUndo = new PixelsUndo();
            this.drawColor = tool == TexturePaintTool.ERASER ? new Color(0, 0, 0, 0) : this.colorSupplier.get();

            Vector2i pixel = this.getHoverPixel(context.mouseX, context.mouseY);

            this.paint(pixel.x, pixel.y);
            this.updateTexture();

            this.wasChanged();
        }
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.dragging && this.pixelsUndo != null)
        {
            Vector2i hoverPixel = this.getHoverPixel(context.mouseX, context.mouseY);

            if (Window.isShiftPressed() && this.lastPixel != null)
            {
                LineRasterizer rasterizer = new LineRasterizer(
                    new Vector2d(this.lastPixel.x, this.lastPixel.y),
                    new Vector2d(hoverPixel.x, hoverPixel.y)
                );
                Set<Vector2i> pixels = new HashSet<>();

                rasterizer.setupRange(0F, 1F, 1F / (float) this.lastPixel.distance(hoverPixel));
                rasterizer.solve(pixels);

                for (Vector2i pixel : pixels)
                {
                    this.paint(pixel.x, pixel.y);
                }

                this.updateTexture();
            }

            this.undoManager.pushUndo(this.pixelsUndo);

            this.pixelsUndo = null;
            this.lastPixel = hoverPixel;
        }

        return super.subMouseReleased(context);
    }

    @Override
    protected void renderBackground(UIContext context)
    {}

    @Override
    protected void renderCanvasFrame(UIContext context)
    {
        int x = -this.w / 2;
        int y = -this.h / 2;
        Area area = this.calculate(x, y, x + this.w, y + this.h);
        Texture texture = this.getRenderTexture(context);

        context.batcher.fullTexturedBox(texture, area.x, area.y, area.w, area.h);

        /* Draw brush preview for stroke tools */
        if (this.isStrokePaintTool())
        {
            int pixelX = (int) Math.floor(this.scaleX.from(context.mouseX));
            int pixelY = (int) Math.floor(this.scaleY.from(context.mouseY));

            int left = (this.brushSize - 1) / 2;
            int right = this.brushSize / 2;

            context.batcher.outline(
                (int) Math.round(this.scaleX.to(pixelX - left)), (int) Math.round(this.scaleY.to(pixelY - left)),
                (int) Math.round(this.scaleX.to(pixelX + right + 1)), (int) Math.round(this.scaleY.to(pixelY + right + 1)),
                Colors.A50
            );
        }

        if (this.editing && this.dragging && (this.lastX != context.mouseX || this.lastY != context.mouseY) && this.mouse == 0 && this.isStrokePaintTool())
        {
            Vector2i last = this.getHoverPixel(this.lastX, this.lastY);
            Vector2i current = this.getHoverPixel(context.mouseX, context.mouseY);

            double distance = Math.max(new Vector2d(current.x, current.y).distance(last.x, last.y), 1);

            for (int i = 0; i <= distance; i++)
            {
                int xx = (int) Lerps.lerp(last.x, current.x, i / distance);
                int yy = (int) Lerps.lerp(last.y, current.y, i / distance);

                this.paint(xx, yy);
            }

            this.wasChanged();
            this.updateTexture();

            this.lastX = context.mouseX;
            this.lastY = context.mouseY;
        }
    }

    protected Texture getRenderTexture(UIContext context)
    {
        return this.temporary;
    }

    @Override
    protected void renderCheckboard(UIContext context, Area area)
    {
        int brightness = (int) (this.backgroundSupplier.get() * 255);
        int color = Colors.setA(brightness << 16 | brightness << 8 | brightness, 1F);

        context.batcher.iconArea(Icons.CHECKBOARD, color, area.x, area.y, area.w, area.h);
    }

    @Override
    protected void renderForeground(UIContext context)
    {
        super.renderForeground(context);
    }
}
