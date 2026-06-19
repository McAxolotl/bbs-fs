# morph-picker-forms (temp notes)

Branch: `fix/morph-picker-forms` (off `1.21.11`).
Task: fix Billboard / Extruded / bbs:block / bbs:particle previews in the morph-picker LIST.

## ROOT CAUSE
Morph picker list = form list (UIFormCategory.render -> FormUtilsClient.renderUI -> FormRenderer.renderInUI).
Only ModelForm was ported to the special-element FBO path (BbsFormGuiElementRenderState hardcoded to ModelFormRenderer +
ModelForm-only renderUIPreview). Billboard/Extruded/Block/Particle still do an IMMEDIATE draw in renderInUI, which runs in
the GUI RECORD phase (two-phase GUI 1.21.6+) -> draw is dropped -> blank. Their WORLD render3D draws (getModelLayer /
consumers.draw / getParticlesLayer) are user-confirmed working.

Particle has a SECOND bug: ParticleEmitter.renderUI built POSITION_TEXTURE_COLOR then `built.close()` (dropped the buffer,
old picker-layer no-op) -> draws nothing even in the right phase.

## FIX DESIGN (faithful to original; reuse the working ModelForm FBO path)
1. Generalize special element: BbsFormGuiElementRenderState.renderer ModelFormRenderer -> FormRenderer<?>;
   BbsFormGuiElementRenderer.acquire key -> FormRenderer<?>; import FormRenderer.
2. FormRenderer base: add `public void renderUIPreview(MatrixStack, angle, transition, x1..y2)` (no-op default) +
   shared `submitUIPreview(UIContext, x1..y2)` that does batcher.flush + angle + capture pose(Matrix3x2f) + scissor +
   dc.state.addSpecialElement(new BbsFormGuiElementRenderState(this, ...)).
3. ModelFormRenderer: extract static `getUIPreviewMatrix(angle, y1, y2)` = scale(cellScale,-cellScale,-cellScale)·
   rotateX(PI/8)·rotateY(angle) [cellScale=(y2-y1)/2.5]; renderInUI -> submitUIPreview; renderUIPreview @Override uses helper.
4. Billboard/Extruded/Block: renderInUI -> submitUIPreview; add renderUIPreview mirroring their OLD renderInUI post-ops but
   using base pre-transformed stack + getUIPreviewMatrix (NOT getUIMatrix). Block keeps no applyTransforms (faithful).
5. Particle: renderInUI -> submitUIPreview; renderUIPreview translates base origin from 0.85h up to cell centre
   (translate 0,-0.35*(y2-y1),0) + scale((y2-y1)/2) then emitter.renderUI. Fix ParticleEmitter.renderUI to build
   POSITION_TEXTURE_COLOR_LIGHT + draw via BBSShaders.getParticlesLayer(); ParticleComponentAppearanceBillboard.writeVertexUI
   add .light(MAX). (Tinting.renderUI writes no verts.)

## KEY FACTS
- Base FBO renderer pre-applies translate(w/2, 0.85h, 0)·scale(f,f,-f), f=wsf*scale(1.0). renderUIPreview reconstructs the
  rest of getUIMatrix; the extra -Z in getUIPreviewMatrix cancels base -f to net the original +Z handedness.
- Fog NOT a concern: ModelForm preview already draws via RenderLayer.draw->bindDefaultUniforms(global Fog) in the same GUI
  prepare phase and works; getModelLayer/getParticlesLayer use the same Fog UBO binding.
- MODEL + PARTICLES pipelines both `withCull(false)` -> -Z flip won't cull; matches original disableCull for particle UI.
- ModelPreviewRenderer.ACTIVE (set by base renderer) only affects cubic ModelInstance.render; irrelevant to other forms.

## STATUS: DONE (build green, committed 2ab8ecd9 on fix/morph-picker-forms). Runtime not run (per task). User to verify visually.

## DEVIATION FROM PLAN (concurrency)
getUIPreviewMatrix was first put in ModelFormRenderer (natural home next to getUIMatrix), but a CONCURRENT
session reset ModelFormRenderer.java to HEAD mid-work and wiped it. Moved it to FormRenderer base (same package,
I own that file) and call it unqualified from the 4 subclasses. ModelFormRenderer ended up UNTOUCHED.

## CONCURRENCY HAZARD (flag to user)
The working tree has parallel uncommitted work from OTHER sessions: BBSPickerRenderer.java (M, +65/-13 = bone/picking
work), memory/tmp/bone-picking.md, memory/tmp/gizmo-render.md. ModelFormRenderer.java was reset under me once.
I committed ONLY my 10 files by explicit path; did not touch the others. If builds/commits look odd, suspect the
concurrent sessions.

## FILES CHANGED (commit 2ab8ecd9)
BbsFormGuiElementRenderState.java, BbsFormGuiElementRenderer.java, FormRenderer.java, Billboard/Extruded/Block/
ParticleFormRenderer.java, ParticleEmitter.java, ParticleComponentAppearanceBillboard.java. compileClientJava green.

## RISK NOTES (for runtime verify)
- Fog tint: Billboard/Extruded(getModelLayer) + Particle(getParticlesLayer) draw via RenderLayer.draw in GUI prepare
  with ambient global fog. ModelForm preview (vanilla entityCutoutNoCull) proves ambient fog is benign there, but the
  BBS layers were not previously exercised in the GUI FBO — watch for a fog-colour wash.
- Particle re-framing math verified on paper (origin 0.85h->0.5h via translate -0.35*(y2-y1); scale (y2-y1)/2).
