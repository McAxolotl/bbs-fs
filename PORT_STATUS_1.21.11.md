# BBS 1.21.11 port — status

Branch: `port/1.21.11` (based off `1.21.1`). Target this session: **build-only** port to MC 1.21.11 (no runtime testing — decided by owner).
See `PORT_PLAN_1.21.11.md` for the full strategy.

## ✅ RESULT: `./gradlew build` is GREEN — `bbs-2.2.1-1.21.11.jar` builds

The entire mod (both source sets, ~1,154 `.java`) compiles and packages on Minecraft 1.21.11.

| Check | Result |
|---|---|
| `./gradlew genSources` | BUILD SUCCESSFUL |
| `./gradlew compileJava` (main / server / common, ~452 files) | BUILD SUCCESSFUL — 0 errors |
| `./gradlew compileClientJava` (client, ~702 files) | BUILD SUCCESSFUL — 0 errors (down from 1702) |
| `:validateAccessWidener` | passes |
| `./gradlew build` (compile + mixin refMap + remapJar + jar) | **BUILD SUCCESSFUL** → `build/libs/bbs-2.2.1-1.21.11.jar` |

### Target toolchain (final)
MC `1.21.11`, Yarn `1.21.11+build.6`, Fabric Loader `0.19.3`, Fabric API `0.141.4+1.21.11`, Loom `1.15.5`, Gradle `9.2`, Java `21`. Iris/Sodium deps commented out (stubbed). Mappings: Yarn (as decided).

### What was migrated (compiles correctly)
- **Toolchain & config**: gradle.properties, build.gradle, fabric.mod.json, access-widener (dead RenderSystem/GlStateManager entries removed).
- **Iris/Sodium/Indium/DH decoupling**: 20 files deleted, 11 external mixins removed, `BBSRendering` stubbed, `ShaderCurves` trimmed to its vanilla data model.
- **Core / common / server**: registration (`EntityType.Builder`+`registryKey`, `AbstractBlock.Settings`, `TypedEntityData` block-entity-data component, `GameRule<Boolean>`), attributes de-prefix, `getEntityWorld`/`getEntityPos`/`last*` renames, **persistence rewrite** (`ReadView`/`WriteView` for block-entities & entities incl. `PlayerEntityMixin`), `ActionResult` unification, server-side `damage(ServerWorld,…)`, NBT `Optional` getters, `ModelTransformationMode`→`ItemDisplayContext`, networking component changes, server mixins, `PlayerConfigEntry`.
- **Render foundation**: `BBSShaders` 7 programs → `RenderPipeline`/`RenderLayer`; `FormRenderType` → `RenderLayer.of(RenderSetup)`; `Texture` (`GlStateManager` blaze3d.opengl, `_genTexture`/`_activeTexture`); `Framebuffer`/`FontRenderer` (raw-LWJGL, unchanged); 2D core `Draw`/`Batcher2D`/`UIRenderingContext` (Matrix3x2f GUI, RenderLayer draws).
- **Client downstream**: all `ui/**`, `forms/renderers/**`, `cubic/**`, `client/renderer/**` (entity render-state classes, item renderers neutralized), `particles/**`, `film/**`, `BBSRendering`, `BBSModClient`, graphics/utils residuals.

## ⚠️ Build-green ≠ render-correct — runtime work remains (deferred by design)

The port compiles and the jar builds, but **rendering was migrated build-only without runtime validation** (per the owner's decision). There are **185 `// TODO(1.21.11 render)` markers** across ~50 files where a draw/effect is implemented against the new API but needs in-game verification, or is a temporary no-op stub. These must be exercised against a running 1.21.11 client.

### Known stubbed / no-op-until-tuned (high level)
- **Custom shaders**: `BBSShaders` pipelines declared, but the GLSL `.vsh/.fsh` assets are still 1.21.1-style (`#version 150`, loose uniforms) — they need rewriting to 1.21.5 std140 UBO blocks before they link; per-draw custom uniforms (ColorModulator, Target, Blur, light dirs, …) need UBO/DynamicUniforms wiring.
- **Textured 2D drawing** in `Batcher2D` is a no-op stub (needs `Texture`→`GpuTextureView`/sampler bridge) — icons/textured UI won't render yet.
- **Framebuffer compositing** (`FramebufferFormRenderer`, `BBSRendering` video export, subtitles): `Framebuffer.beginWrite`/blit removed → stubbed (needs `GpuTexture`/`RenderPass`).
- **Item forms** (`ItemFormRenderer`, `ModelBlockItemRenderer`, `GunItemRenderer`): the 1.21.4 item-model rewrite removed `BuiltinItemRendererRegistry`; custom item rendering is neutralized (renders vanilla/nothing) until rewired to the item-model / `SpecialModelRenderer` + `OrderedRenderCommandQueue` path.
- **Model/VAO rendering** (`ModelVAORenderer`, `ModelInstance`, `BOBJModelVAO`, `ModelVAO`, `ExtrudedFormRenderer`): PORTED. The raw-GL VAO + ShaderProgram draw was removed in 1.21.5+; geometry/skinning are now baked CPU-side into a `BufferBuilder` (POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, TRIANGLES) and drawn through `BBSShaders.getModelLayer()` — the same proven model layer cubic Models/Billboards use. Revives BOBJ models + Extruded forms (+ previews). Iris tangent/mid-uv attributes are NOT carried by this format (deferred to a custom pipeline pass).
- **Picking** (gizmo, form pickers): the per-object Target-index uniform upload is unwired → selection won't read back correct indices yet.
- **Vanilla armor** (`ArmorRenderer`): equipment-render rewrite (1.21.4) — texture/trim ids computed, draw stubbed.
- **3D viewport** (`UIModelRenderer`, `Gizmo`): GPU draw/stencil stubbed; camera/math preserved.

### Temporarily DISABLED client render mixins (removed from `bbs.client.mixins.json`, marked TODO to restore)
Their target methods were removed/reworked by the 1.21.2 render-state / 1.21.9 submit-model rewrite:
`WorldRendererMixin`, `EntityRenderDispatcherMixin`, `LivingEntityRendererMixin`, `PlayerEntityRendererMixin`, `BlockEntityRenderDispatcherMixin`, `EntityRendererDispatcherInvoker`, `LivingEntityRendererInvoker`.
Functionality lost until re-ported: morph rendering over living/player entities, custom shadows, chroma-sky background, chunk-layer hook, entity-outline framebuffer resize. (Their `.java` files were made to compile but are inert.)

### Recommended next steps (need a running 1.21.11 client)
1. Stand up a 1.21.11 Prism instance + 1.21.11 dependency mods; launch dev client (`./gradlew runClient`).
2. Rewrite the GLSL shader assets to 1.21.5 UBO style; wire per-draw uniforms; un-stub `Batcher2D` textured drawing (Texture→GpuTextureView).
3. Re-port the framebuffer compositing onto `GpuTexture`/`RenderPass`.
4. Re-port item rendering onto the new item-model system.
5. Re-port & re-enable the 7 disabled render mixins (submit model / render-state).
6. Re-integrate Iris/Sodium against 1.21.11-matched builds.
7. Forward-port the `master` feature delta (deferred — see git: branch was based on `1.21.1`, ~73 master commits not yet merged).

## Resume aids
- Decompiled Yarn jars (ground truth): `~/.gradle/caches/fabric-loom/1.21.11/net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2/{common,clientOnly}-unpicked.jar` — inspect with JDK21 `javap` (see `.port/REF.md`, gitignored, for commands + migration patterns).
- Find render TODOs: `grep -rn "TODO(1.21.11 render)" src/client/java`.
- Per-phase checkpoints in `git log port/1.21.11`.
