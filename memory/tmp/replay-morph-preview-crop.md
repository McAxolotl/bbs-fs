---
name: replay-morph-preview-crop
description: TEMP task — replay-editor morph-list model previews not cropped to the cell; fix = carry live GUI scissor into the special-element composite
metadata:
  type: project
---

# Task: replay-editor morph list — model form preview must crop to a square (it rendered full-size, overflowing the row)

Branch: `fix/replay-morph-preview-crop` (from `1.21.11`). Build green (`compileClientJava`).

## Root cause
The model-form list thumbnail goes through the ported two-phase special-element path
(`ModelFormRenderer.renderInUI` → `DrawContext.state.addSpecialElement(BbsFormGuiElementRenderState)`
→ `BbsFormGuiElementRenderer` renders off-screen + composites a `TexturedQuadGuiElementRenderState`).
The composite quad already passes `state.scissorArea()` (BbsFormGuiElementRenderer:118), but
`renderInUI` submitted the state with `scissorArea = null`. So the GL scissor set by the caller's
`context.batcher.clip(...)` was ignored and the full model composited beyond the cell.

In `1.21.1/main` the caller (`UIReplayList.renderElementPart` ~line 2227) brackets the form preview with
`context.batcher.clip(formX, y, 40, 20, context)` … `unclip`, and the original immediate 3D draw respected
the GL scissor → cropped. The clip lines are present & identical in the port; only the special-element path
dropped the scissor.

## Fix (1 line of intent)
`ModelFormRenderer.renderInUI`: read the live GUI scissor `bbs$dc.scissorStack.peekLast()` (same idiom as
`Batcher2D.drawQuadMesh` line 191 and the keyframe/line render path, commit d75dfab3) and pass it as the
`scissorArea` arg instead of `null`. General: every renderUI caller wrapped in `batcher.clip` now crops
its model thumbnail (UIReplayList, UIFormCategory grid, ReplayContextAction, UIFilmController, UIForms,
UISelectorList) — faithful to the original GL-scissor behaviour.

scissorStack/peek returns absolute screen coords; the composite quad's pose carries scroll translate and
x1..y2 are already global, so the absolute scissor crops correctly. `BbsFormGuiElementRenderState` convenience
ctor also recomputes `bounds = createBounds(x1..y2, scissorArea)` → tighter (more correct) layer bounds.

## Status: code done + builds. Runtime NOT run (per instructions). Committed only ModelFormRenderer.java.
