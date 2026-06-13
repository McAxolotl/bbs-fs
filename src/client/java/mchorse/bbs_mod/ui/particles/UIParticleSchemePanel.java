package mchorse.bbs_mod.ui.particles;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.settings.values.ui.ValueEditorLayout;
import mchorse.bbs_mod.forms.renderers.ParticleFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.DataTab;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.UIDataTabs;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.layout.ILayoutSource;
import mchorse.bbs_mod.ui.framework.elements.layout.UIDockLayout;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextEditor;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeAppearanceSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeCollisionSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeCurvesSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeExpirationSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeGeneralSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeInitializationSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeLifetimeSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeLightingSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeMotionSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeRateSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeShapeSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeSpaceSection;
import mchorse.bbs_mod.ui.particles.utils.MolangSyntaxHighlighter;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class UIParticleSchemePanel extends UIDataDashboardPanel<ParticleScheme>
{
    /**
     * Default particle placeholder that comes with the engine.
     */
    public static final Link PARTICLE_PLACEHOLDER = Link.assets("particles/default_placeholder.json");

    public UITextEditor textEditor;
    public UIParticleSchemeRenderer renderer;
    public UIScrollView sectionsView;
    public UIElement sectionsPanel;
    public UIDockLayout dock;
    public UIParticleSelectionPanel selectionPanel;

    public List<UIParticleSchemeSection> sections = new ArrayList<>();

    private String molangId;

    public UIParticleSchemePanel(UIDashboard dashboard)
    {
        super(dashboard);
        this.enableTabs();

        this.renderer = new UIParticleSchemeRenderer();

        this.textEditor = new UITextEditor(null).highlighter(new MolangSyntaxHighlighter());
        this.sectionsView = UI.scrollView(20, 10);
        this.sectionsView.scroll.cancelScrolling().opposite().scrollSpeed *= 3;

        /* Sections panel: scrollable sections with the MoLang strip pinned to its bottom. */
        this.sectionsPanel = new UIElement();
        this.textEditor.background().relative(this.sectionsPanel).y(1F, -60).w(1F).h(60);
        this.sectionsView.relative(this.sectionsPanel).w(1F).hTo(this.textEditor.area);
        this.sectionsPanel.add(this.sectionsView, this.textEditor);

        /* Dockable layout: 3D preview + sections, sharing the system with the film editor. */
        this.dock = new UIDockLayout();
        this.dock.relative(this.editor).w(1F).h(1F);
        this.dock.source(this.createLayoutSource())
            .frameless("preview")
            .gate(() -> this.data != null);
        this.dock.addPanel("sections", this.sectionsPanel, Icons.EDITOR);
        this.dock.addPanel("preview", this.renderer, Icons.VIDEO_CAMERA);
        this.dock.mount();
        this.editor.add(this.dock);

        this.add(new UIRenderable(this::drawOverlay));

        this.selectionPanel = new UIParticleSelectionPanel(this);
        this.selectionPanel.relative(this).y(UIDataTabs.TABS_HEIGHT_PX).wTo(this.iconBar.area).h(1F, -UIDataTabs.TABS_HEIGHT_PX);
        this.add(this.selectionPanel);

        UIIcon close = new UIIcon(Icons.CLOSE, (b) -> this.editMoLang(null, null, null));

        close.relative(this.textEditor).x(1F, -20);
        this.textEditor.add(close);
        this.overlay.namesList.setFileIcon(Icons.PARTICLE);

        UIIcon restart = new UIIcon(Icons.REFRESH, (b) ->
        {
            this.renderer.setScheme(this.data);
        });
        restart.tooltip(UIKeys.SNOWSTORM_RESTART_EMITTER, Direction.LEFT);

        this.iconBar.add(restart);

        UIIcon layout = new UIIcon(Icons.ALL_DIRECTIONS, (b) -> this.dock.toggleLock());
        layout.tooltip(UIKeys.FILM_LAYOUT_UNLOCK, Direction.LEFT);
        layout.context((menu) ->
        {
            menu.action(this.dock.isLocked() ? Icons.UNLOCKED : Icons.LOCKED, this.dock.isLocked() ? UIKeys.FILM_LAYOUT_UNLOCK : UIKeys.FILM_LAYOUT_LOCK, this.dock::toggleLock);
            menu.action(Icons.REFRESH, UIKeys.FILM_LAYOUT_RESET, this.dock::resetLayout);
        });

        this.iconBar.add(layout);

        this.addSection(new UIParticleSchemeGeneralSection(this));
        this.addSection(new UIParticleSchemeCurvesSection(this));
        this.addSection(new UIParticleSchemeSpaceSection(this));
        this.addSection(new UIParticleSchemeInitializationSection(this));
        this.addSection(new UIParticleSchemeRateSection(this));
        this.addSection(new UIParticleSchemeLifetimeSection(this));
        this.addSection(new UIParticleSchemeShapeSection(this));
        this.addSection(new UIParticleSchemeMotionSection(this));
        this.addSection(new UIParticleSchemeExpirationSection(this));
        this.addSection(new UIParticleSchemeAppearanceSection(this));
        this.addSection(new UIParticleSchemeLightingSection(this));
        this.addSection(new UIParticleSchemeCollisionSection(this));

        this.fill(null);
    }

    public void editMoLang(String id, Consumer<String> callback, MolangExpression expression)
    {
        this.molangId = id;
        this.textEditor.callback = callback;
        this.textEditor.setText(expression == null ? "" : expression.toString());
        this.textEditor.setVisible(callback != null);

        if (callback != null)
        {
            this.sectionsView.hTo(this.textEditor.area);
        }
        else
        {
            this.sectionsView.h(1F);
        }

        this.sectionsView.resize();
    }

    @Override
    protected IKey getTitle()
    {
        return UIKeys.SNOWSTORM_TITLE;
    }

    @Override
    public ContentType getType()
    {
        return ContentType.PARTICLES;
    }

    @Override
    public Icon getTabIcon(DataTab tab)
    {
        return tab != null && tab.dataId == null ? Icons.SEARCH : Icons.PARTICLE;
    }

    public void dirty()
    {
        this.renderer.emitter.setupVariables();
    }

    private ILayoutSource createLayoutSource()
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

        return new ILayoutSource()
        {
            @Override
            public BaseValue value()
            {
                return layout;
            }

            @Override
            public EditorLayoutNode getRoot()
            {
                return layout.getParticleLayoutRoot();
            }

            @Override
            public void setRoot(EditorLayoutNode root)
            {
                layout.setParticleLayoutRoot(root);
            }

            @Override
            public List<EditorLayoutNode.SplitterNode> getSplitters()
            {
                return layout.getParticleSplitters();
            }

            @Override
            public List<EditorLayoutNode.SplitterNode> getSplittersForWrite()
            {
                return layout.getParticleSplittersForWrite();
            }

            @Override
            public EditorLayoutNode getDefault()
            {
                return EditorLayoutNode.defaultParticleLayout();
            }
        };
    }

    private void addSection(UIParticleSchemeSection section)
    {
        this.sections.add(section);
        this.sectionsView.add(section);
    }

    @Override
    protected void fillData(ParticleScheme data)
    {
        this.editMoLang(null, null, null);

        this.selectionPanel.setVisible(data == null);

        if (this.data != null)
        {
            this.renderer.setScheme(this.data);

            for (UIParticleSchemeSection section : this.sections)
            {
                section.setScheme(this.data);
            }

            this.sectionsView.resize();
        }
        else
        {
            this.renderer.setScheme(null);
        }

        /* Dock gate shows/hides the preview + sections panels based on data presence. */
        this.dock.setupFlex(true);
    }

    @Override
    public void fillNames(Collection<String> names)
    {
        super.fillNames(names);

        if (this.selectionPanel != null)
        {
            this.selectionPanel.fillNames(names);
        }
    }

    @Override
    public void forceSave()
    {
        super.forceSave();

        ParticleFormRenderer.lastUpdate = System.currentTimeMillis();
    }

    @Override
    public void fillDefaultData(ParticleScheme data)
    {
        super.fillDefaultData(data);

        try (InputStream asset = BBSMod.getProvider().getAsset(PARTICLE_PLACEHOLDER))
        {
            MapType map = DataToString.mapFromString(IOUtils.readText(asset));

            ParticleScheme.PARSER.fromData(data, map);
        }
        catch (Exception e)
        {}
    }

    @Override
    public void appear()
    {
        super.appear();

        this.textEditor.updateHighlighter();
    }

    @Override
    protected boolean shouldAutoOpenListOnFirstResize()
    {
        return false;
    }

    @Override
    public void close()
    {
        if (this.renderer.emitter != null)
        {
            this.renderer.emitter.particles.clear();
        }
    }

    @Override
    public void resize()
    {
        super.resize();

        if (this.dock != null)
        {
            this.dock.setupFlex(true);
        }
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        if (this.iconBar.isVisible())
        {
            int bg = this.selectionPanel != null && this.selectionPanel.isVisible() ? Colors.A100 : Colors.A50;

            this.iconBar.area.render(context.batcher, bg);
            context.batcher.gradientHBox(this.iconBar.area.x - 6, this.iconBar.area.y, this.iconBar.area.x, this.iconBar.area.ey(), 0, 0x29000000);
        }
    }

    private void drawOverlay(UIContext context)
    {
        /* Draw debug info */
        if (this.editor.isVisible())
        {
            ParticleEmitter emitter = this.renderer.emitter;
            String label = emitter.particles.size() + "P - " + emitter.age + "A";

            int y = (this.textEditor.isVisible() ? this.textEditor.area.y : this.area.ey()) - 12;

            context.batcher.textShadow(label, this.editor.area.ex() - 4 - context.batcher.getFont().getWidth(label), y);
        }
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        if (this.molangId != null)
        {
            FontRenderer font = context.batcher.getFont();
            int w = font.getWidth(this.molangId);

            context.batcher.textCard(this.molangId, this.textEditor.area.ex() - 6 - w, this.textEditor.area.ey() - 6 - font.getHeight());
        }
    }
}