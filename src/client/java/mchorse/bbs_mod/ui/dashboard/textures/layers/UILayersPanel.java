package mchorse.bbs_mod.ui.dashboard.textures.layers;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.textures.UITextureEditor;
import mchorse.bbs_mod.ui.dashboard.textures.UITexturePainter;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.colors.Colors;

public class UILayersPanel extends UIElement
{
    private UITexturePainter painter;
    private UIScrollView list;
    private UIButton addLayer;
    
    public UITextureEditor currentEditor;

    public UILayersPanel(UITexturePainter painter)
    {
        this.painter = painter;

        this.list = UI.scrollView(5, 5);
        this.list.scroll.cancelScrolling();
        this.list.relative(this).y(20).w(1F).h(1F, -UIConstants.CONTROL_HEIGHT - 25);

        UILabel label = UI.label(UIKeys.TEXTURES_LAYERS).background();
        label.relative(this).w(1F).h(20);
        label.labelAnchor(0.5F, 0.5F);

        this.addLayer = new UIButton(UIKeys.TEXTURES_LAYERS_ADD, (b) -> this.addLayer());
        this.addLayer.relative(this).y(1F, -UIConstants.CONTROL_HEIGHT - 5).w(1F).h(UIConstants.CONTROL_HEIGHT);

        this.add(label, this.list, this.addLayer);
    }

    public void setEditor(UITextureEditor editor)
    {
        this.currentEditor = editor;
        this.updateLayers();
    }

    private void addLayer()
    {
        if (this.currentEditor != null && this.currentEditor.getPixels() != null)
        {
            mchorse.bbs_mod.utils.resources.Pixels newPixels = mchorse.bbs_mod.utils.resources.Pixels.fromSize(this.currentEditor.getPixels().width, this.currentEditor.getPixels().height);
            TextureLayer layer = new TextureLayer(UIKeys.TEXTURES_LAYERS_DEFAULT_NAME.format(String.valueOf(this.currentEditor.layers.size() + 1)).get(), newPixels);
            this.currentEditor.layers.add(layer);
            this.currentEditor.activeLayerIndex = this.currentEditor.layers.size() - 1;
            this.currentEditor.setActiveLayer(this.currentEditor.activeLayerIndex);
            this.updateLayers();
            this.currentEditor.dirty();
        }
    }

    public void updateLayers()
    {
        this.list.removeAll();

        if (this.currentEditor != null)
        {
            for (int i = this.currentEditor.layers.size() - 1; i >= 0; i--)
            {
                TextureLayer layer = this.currentEditor.layers.get(i);
                this.list.add(new UILayerElement(this, layer, i));
            }
        }

        this.list.resize();
    }
}