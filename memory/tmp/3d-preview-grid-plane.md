# fix/3d-preview-grid-plane (branch from 1.21.11)

## BUG
In the 3D model/form preview (form properties editor), the ground **grid plane** (11x11 line grid with coloured X/Z centre axes) was missing, and the preview looked different from `1.21.1/main`.

## ROOT CAUSE
During the 1.21.11 port, `UIModelRenderer.renderGrid` was stubbed out to an empty body with a TODO, AND its call site was dropped. In the original 1.21.1 `renderModel`, the grid was drawn (gated by `this.grid`) right before `renderUserModel`, inside the camera-stack push. When `renderModel` was rewritten into the world-phase `renderModelToTexture` (FBO path), only `renderUserModel` was kept — so the grid never rendered.

The stub reason: original used `RenderSystem.setShader(GameRenderer::getPositionColorProgram)` + DEBUG_LINES `BufferBuilder` + `BufferRenderer.drawWithGlobalProgram` (all removed in the 1.21.5 GPU rewrite), and read the 3D matrix from `DrawContext.getMatrices()` (now 2D).

## FIX (faithful to original, 2 forced deviations)
1. **`Draw.java`** — added a BBS POSITION_COLOR **DEBUG_LINES** pipeline `POSITION_COLOR_LINES` (seeded from `RenderPipelines.POSITION_COLOR_SNIPPET` = `core/position_color`, the exact shader the old `getPositionColorProgram` used; LEQUAL depth test, no cull, translucent blend) + lazy `positionColorLinesLayer` + public `flushLines(BufferBuilder)`. Mirrors the existing `POSITION_COLOR_TRIS` pattern.
2. **`UIModelRenderer.renderGrid`** — restored the original 11x11 grid verbatim (centre Z axis blue `(0,0,1)`, centre X axis red `(1,0,0)`, rest grey `0.25`). Forced deviations: (a) 3D matrix from `createCameraStack().peek().getPositionMatrix()` instead of `DrawContext.getMatrices()` — this is the SAME `camera.view * translate(-pos) * transform` stack baked into the model vertices by `renderUserModel` (UIFormRenderer/UIPickableFormRenderer both use `createCameraStack()`), so the grid sits in the same frame as the model; (b) flush via `Draw.flushLines(builder)`.
3. **`UIModelRenderer.renderModelToTexture`** — re-added the call: inside the `ModelPreviewRenderer.begin/end` bracket, `if (this.grid) this.renderGrid(context);` BEFORE `renderUserModel` (matches original ordering; depth-tested so the model occludes grid lines behind it). Draws into the off-screen preview FBO with perspective projection already set + global model-view identity.

Imports added to UIModelRenderer: `mchorse.bbs_mod.graphics.Draw`, `com.mojang.blaze3d.vertex.VertexFormat` (NOTE: VertexFormat moved to `com.mojang.blaze3d.vertex` in 1.21.11, NOT `net.minecraft.client.render`), `BufferBuilder`, `Tessellator`, `VertexFormats`.

Subclasses that inherit the grid via `super.renderGrid`: `UIPickableFormRenderer` (gated on `renderForm`), `UIParticleSchemeRenderer`. The keyframe-graph `renderGrid` methods are unrelated 2D classes.

## STATUS
Build green (`compileClientJava` BUILD SUCCESSFUL). Runtime NOT yet verified (user runs runtime). Commits on branch `fix/3d-preview-grid-plane`.

## FILES
- `src/client/java/mchorse/bbs_mod/graphics/Draw.java` (lines pipeline + flushLines)
- `src/client/java/mchorse/bbs_mod/ui/framework/elements/utils/UIModelRenderer.java` (renderGrid impl + renderModelToTexture call + imports)
