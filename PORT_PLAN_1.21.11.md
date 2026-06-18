# BBS Port Strategy: Minecraft 1.21.1 → 1.21.11 (Fabric / Yarn)

**Status:** Authoritative port plan. Verification target for the current effort = a green `./gradlew build` (compile), NOT runtime testing.
**Branch:** `port/1.21.11` (currently byte-identical to `1.21.1`, itself a completed 1.20.4→1.21.1 port).
**Decisions fixed by owner:** stay on Yarn mappings (not Mojmap); stay on Fabric single-loader; stub/disable Iris/Sodium/Indium/Distant Horizons initially (vanilla rendering first); shader-mod compat is a later phase.

---

## 1. Executive Summary

- **Scope.** Move ~1,154 `.java` files (452 common/server + 702 client) from MC 1.21.1 to 1.21.11. The non-rendering surface is mostly mechanical renames and is low-risk. The **client rendering layer is a near-total rewrite**: MC 1.21.5 replaced the entire immediate-mode pipeline (`RenderSystem.setShader` + `BufferRenderer` + GL-id framebuffers) with a `GpuDevice`/`RenderPipeline`/`RenderPass` abstraction, and 1.21.6 / 1.21.9 / 1.21.11 piled GUI, submit-model, and RenderSetup reworks on top.
- **Biggest risks (rendering).** The mod's custom 2D batcher (`Batcher2D`), custom shader engine (`BBSShaders`, 7+ programs), GL30 framebuffers (`graphics/Framebuffer`), direct-GL VAOs (`cubic/render/vao/*`), the viewport gizmo (`Gizmo`, 33 `RenderSystem` calls), and the 12 form renderers all hit removed APIs. ~255 `RenderSystem` calls across 51 files and ~279 `BufferBuilder`/`VertexConsumer` calls across 79 files must be re-expressed.
- **Biggest risks (non-rendering).** Two persistence cliffs: NBT primitive getters became `Optional`-returning in 1.21.5, and entity/block-entity serialization moved to `ReadView`/`WriteView` in 1.21.6. Plus pervasive mechanical renames: `Identifier.of` (already adopted here), registry query methods (1.21.2), attribute de-prefixing (1.21.2), `getWorld()`→`getEntityWorld()` (1.21.9), `ActionResult` unification (1.21.2).
- **Phase 0 debt.** `master` is **73 commits / 171 files ahead** of the port branch, written entirely against the old 1.20.4 render/tick API. This delta must be reconciled FIRST or it will be reconciled twice. Only 3 textual conflicts, but hundreds of silent compile breaks (`tickDelta()`, old `BufferBuilder`, `MatrixStack`).
- **Realistic outcome for a build-only port.** Achievable, but rendering is the long pole — expect the bulk of effort in Phases 3–4. Iris/Sodium stubbing (Phase 5) removes 12 mixins from the compile graph and de-risks the largest unknown. Yarn `1.21.11+build.6` is the **last** Yarn release ever; this port is viable but is the final version where the owner's "stay on Yarn" decision holds.
- **Already partly done.** Gradle wrapper is already at **9.2.0** and the mixin compatibility level is already `JAVA_21`, so two of the toolchain prerequisites are met on the branch today.

---

## 2. Target Toolchain

| Component | Current (1.21.1) | Target (1.21.11) | Confidence |
|---|---|---|---|
| `minecraft_version` | `1.21.1` | `1.21.11` | Confirmed (released 2025-12-09, "Mounts of Mayhem") |
| `yarn_mappings` | `1.21.1+build.3` | `1.21.11+build.6` | Confirmed latest at report time; **VERIFY `<latest>` in Yarn maven-metadata at execution** |
| `loader_version` (Fabric Loader) | `0.16.14` | `0.18.1` (any `0.18.x+`) | Likely; blog-post recommendation, not re-verified for newest patch |
| `fabric_version` (Fabric API) | `0.116.12+1.21.1` | `0.141.3+1.21.11` (any `0.14x.y+1.21.11`) | Likely; the `+1.21.11` suffix is what matters |
| Fabric Loom plugin | `1.15-SNAPSHOT` | `1.14` (official stable for 1.21.11) | Confirmed; **note current `1.15-SNAPSHOT` is non-standard — pin to `1.14`** |
| Gradle wrapper | **already `9.2.0`** | `>= 9.2` (Loom 1.14 requires Gradle 9.2) | Confirmed; already satisfied |
| Java toolchain | Java 21 (mixin level already `JAVA_21`) | **Java 21** (hard runtime requirement) | Confirmed; verify `build.gradle` `sourceCompatibility`/`JavaLanguageVersion` is 21 |
| MixinExtras | bundled via Loader | `0.5.0` (1.21.9+ requirement) | Confirmed; pulled transitively by Loom/Loader — verify resolved version |
| Fabric Mixin | bundled | `0.16.3+mixin.0.8.7` (1.21.9+) | Confirmed; verify resolved version |
| Access-widener format | v2 | v2 (no format change in range) | Confirmed |

**Paste block for `gradle.properties`:**
```
minecraft_version=1.21.11
yarn_mappings=1.21.11+build.6
loader_version=0.18.1
fabric_version=0.141.3+1.21.11
```
**`build.gradle`:** set Loom plugin to `1.14`, confirm Java toolchain 21. **Gradle wrapper already at 9.2.0 — no change needed.**

> **Yarn end-of-life caveat:** Yarn/Intermediary will NOT be updated after 1.21.11. Any port *beyond* 1.21.11 forces a Mojmap migration. This is the last stop for the current mappings decision.

---

## 3. High-Level Strategy & Phase Sequencing

Principle: **reconcile first, then bump, then compile inside-out (core → UI 2D → 3D/shaders), then stub external-mod mixins, then iterate to green.** Build after every phase to create checkpoints.

### Phase 0 — Reconcile `master` delta into the port branch
- **Goal:** land the 73-commit feature delta (`master`) onto the 1.21.1-based branch *before* the 1.21.11 bump, so features are ported once, not twice.
- **Key areas:** `ui/framework/elements/input` (gizmo/transform/keyframes), `ui/particles`, `ui/film`, `cubic/ik`, `cubic/render`, `cubic/physics`, `cubic/model/loaders`; light core changes in `BBSSettings`, `utils/pose/*`, `utils/interps/*`, `settings/values/*`, keyframe factories.
- **Tasks:** incremental theme-grouped cherry-pick onto a working branch (NOT a single `git merge`); see §7 for ordering and conflict files. Translate the 3 recurring old-API patterns (`tickDelta()`, old `BufferBuilder` begin/next/end, model-view `MatrixStack`→`Matrix4fStack`) as you go; `./gradlew build` after each theme group.
- **Risks:** ~3 textual conflicts (1 hard: `cubic/ik/ModelIKDebug.java`) but hundreds of silent compile breaks against the now-removed 1.20.4 render/tick API. This is still on the 1.21.1 toolchain.
- **Effort: L** (this is real work even before the version bump).

### Phase 1 — Bump build/toolchain to 1.21.11
- **Goal:** Gradle resolves and downloads 1.21.11 + Yarn + Fabric API; remap succeeds even though code won't compile yet.
- **Key files:** `gradle.properties`, `build.gradle` (Loom `1.14`, Java 21), `gradle/wrapper/gradle-wrapper.properties` (already 9.2.0), `fabric.mod.json` (bump `depends` ranges for MC/loader/fabric-api/java).
- **Tasks:** apply §2 version block; pin Loom to `1.14`; confirm MixinExtras 0.5.0 / Fabric Mixin 0.16.3+ resolve; run `./gradlew --refresh-dependencies genSources`.
- **Risks:** Loom `1.15-SNAPSHOT`→`1.14` may shift behavior; snapshot was non-standard. Yarn build number drift.
- **Effort: S.**

### Phase 2 — CORE / non-UI migration to compile
- **Goal:** `src/main/java` (server/common) and non-rendering client logic compile against 1.21.11.
- **Key files/areas:** `BBSMod.java` (registries, sound events), `network/ServerNetwork.java` (35+ payload hits), `blocks/entities/ModelBlockEntity.java`, `blocks/ModelBlock.java`, `entity/ActorEntity.java` + `GunProjectileEntity.java` (attributes/DataTracker), `actions/**` (block/world/item state), `items/GunProperties.java`, the 11 server mixins.
- **Tasks (mechanical-first):**
  1. `Identifier.of` — already adopted; confirm no `new Identifier(` remain.
  2. Registry query renames (1.21.2): `getEntry`→`getOptional`, `entryOf`→`getOrThrow`, `getOrThrow`→`getValueOrThrow`, `DynamicRegistryManager#get`→`getOrThrow`, etc.
  3. Attribute de-prefixing (1.21.2): `EntityAttributes.GENERIC_*`→`EntityAttributes.*` in `ActorEntity.createActorAttributes()`.
  4. `ActionResult` unification (1.21.2): drop `TypedActionResult`; `result.withNewHandStack(...)`; `UseItemCallback` now returns `ActionResult`. Touch item-use action clips (`UseItemActionClip`, `UseBlockItemActionClip`), `GunItem`.
  5. Mandatory `registryKey(...)` on item/block/entity/BE settings (1.21.2); `FabricItemSettings`/`FabricBlockSettings` removed; `BlockEntityType.Builder`→`FabricBlockEntityTypeBuilder`.
  6. `EntityType.Builder#build(RegistryKey)`, `EntityType#create(world, SpawnReason)`, `convertTo(type, ctx)` (1.21.2).
  7. `getWorld()`→`getEntityWorld()` (1.21.9); server-only `Entity#damage(ServerWorld, ...)` guards (1.21.2) in `DamageControl`/`LivingEntityMixin`.
  8. **NBT Optional getters (1.21.5):** `getInt/getString/...` now return `Optional`; use defaulted overloads or `getCompoundOrEmpty`. Array getters have no defaulted overload. Hits `data/**`, `ModelBlockEntity`, `ServerNetwork`, keyframe factories.
  9. **ReadView/WriteView (1.21.6):** BE `readNbt/writeNbt(NbtCompound, WrapperLookup)`→`readData(ReadView)`/`writeData(WriteView)`; Entity `readCustomDataFromNbt/writeCustomDataToNbt`→`readCustomData(ReadView)`/`writeCustomData(WriteView)`. Hits `ModelBlockEntity`, `PlayerEntityMixin` (writes/reads morph NBT at TAIL — signature/target changes), `GunProjectileEntity`.
  10. `DataTracker` handler registration→`FabricTrackedDataRegistry.registerHandler` (1.21.6) if custom handlers are registered.
  11. Game rules onto registry + `registerLarge(...)` opt-in packet splitter (1.21.11) — only if used.
- **Risks:** ReadView/WriteView is the deepest rework; PlayerEntityMixin's NBT inject points change. `Profilers.get()` (1.21.2) if profiler used.
- **Effort: L.**

### Phase 3 — UI + 2D rendering migration
- **Goal:** the 2D UI framework and immediate-mode 2D drawing compile and produce valid pipelines.
- **Hot files:** `ui/framework/elements/utils/Batcher2D.java` (de-facto 2D core — DrawContext + Matrix4f + Tessellator/BufferBuilder + custom shader binding), `graphics/Draw.java` (still `MatrixStack`), `ui/framework/UIRenderingContext.java`, `graphics/FontRenderer.java`, text widgets (`UIText`/`UITextarea`/`UITextEditor`/`UIBaseTextbox`), the 416-file `ui/**` tree.
- **Tasks:**
  1. `DrawContext.blit/drawTexture` now require a `Function<Identifier,RenderLayer>` + mandatory PNG size args; `setColor` is a parameter; `drawManaged`/sprite overloads removed (1.21.2).
  2. Migrate `Batcher2D` immediate-mode draws to the build-a-`BufferBuilder`→`BuiltBuffer`→draw-via-`RenderLayer`(pipeline) model (1.21.5). Per-draw custom uniforms can't ride a batched RenderLayer — drive a `RenderPass` directly where needed.
  3. **Two-phase GUI (1.21.6):** `GuiGraphics`/`DrawContext` constructed with a `GuiRenderState`; **`pose()` returns `Matrix3x2fStack` (2D)** — fix all code assuming a 4×4 GUI pose; `flush()` removed; `drawString` needs non-zero alpha ARGB. Integrate batcher z-order with strata or accept ordering breakage.
  4. Font: `Font.drawInBatch`→void + `prepareText`/`PreparedText`/`GlyphVisitor` (1.21.6); text via `submitText` (1.21.9). Adapt `FontRenderer` wrapper.
  5. Finish `MatrixStack`→`Matrix4f`/`Matrix3x2f` transition in `Draw.java` (partial already in `Batcher2D`).
- **Risks:** **very high** — the 2D batcher bypasses `GuiGraphics`, so the 1.21.6 deferred/submit GUI model and the 2D matrix-stack switch are the concrete breaks. Z-ordering regressions likely even when it compiles.
- **Effort: XL.**

### Phase 4 — 3D / cubic + custom shader engine migration
- **Goal:** model rendering, the viewport, form renderers, custom shaders, and framebuffers compile on the new pipeline.
- **Hot files:** `client/BBSShaders.java` (7+ custom programs), `client/BBSRendering.java` (framebuffer/video integration, 674 LOC), `ui/utils/Gizmo.java` (33 `RenderSystem` calls), `forms/renderers/ModelFormRenderer.java` + the other 11 renderers (~3.5k LOC), `forms/CustomVertexConsumerProvider.java` (+ `RenderLayerMixin`), `cubic/render/CubicRenderer.java`/`CubicVAORenderer.java`, `cubic/render/vao/*` (direct GL30 VAO/VBO), `graphics/Framebuffer.java`/`FramebufferManager.java` (GL30 FBO wrapper), `graphics/texture/Texture.java`.
- **Tasks:**
  1. **Custom shaders → `RenderPipeline` (1.21.5):** shader-program JSONs are gone; declare pipelines in code (`RenderPipelines.register(...)`), seed from snippets. Re-express the 7 `BBSShaders` programs; replace every `RenderSystem.setShader(() -> program)` (62+ callsites). GLSL `.vsh`/`.fsh` sources remain valid/overridable.
  2. Adopt **std140 uniform blocks** for those shaders (1.21.6): `Std140Builder`/`Std140SizeCalculator`.
  3. **Framebuffers (1.21.5):** `graphics/Framebuffer` GL30 wrapper must move to `GpuTexture` + `RenderPass`. `bindWrite/unbindWrite/setClearColor/framebufferId/checkStatus` removed; draw into a target via `createCommandEncoder().createRenderPass(colorTexture, ...)`; accessors return `GpuTextureView` (1.21.6). Reconcile with the `MinecraftClient.framebuffer` access-widener (§6).
  4. **RenderLayer/RenderType:** `RenderType.create` takes a `RenderPipeline` (1.21.5); then RenderStateShard→`RenderSetup`+`RenderType` split (1.21.11); `_BLOCK`/`_TERRAIN` pipeline split; items moved to a separate `minecraft:items` atlas (1.21.11). Update form-renderer layer selection.
  5. **VAOs:** `cubic/render/vao/*` use raw `GL30`; verify they still function alongside the new device abstraction (may keep direct GL where it doesn't conflict, but framebuffer/texture interop changed).
  6. **Submit model (1.21.9):** custom world/entity geometry injected at old render phases must go through `SubmitNodeCollector.submitCustomGeometry(...)`; `RenderLayer.render()`→`submit()`. Affects form renderers driven from entity-render mixins and `WorldRendererMixin`/`RenderLayerMixin` interception.
  7. **BakedQuad (1.21.11):** no longer raw `int[]` — use `position(int)`/`packedUV(int)`; `BlockElementRotation` takes `Vector3fc`/`Matrix4fc`. Affects model/quad code.
  8. **GpuSampler split (1.21.11):** sampling config is its own object; bind via `RenderPass.bindTexture(name, view, sampler)`; cache via `RenderSystem.getSamplerCache()`. Affects `Texture.java`/texture binding.
- **Risks:** **very high / largest single rewrite.** `RenderSystem.shaderLightDirections` access-widener (§6) likely gone. `CustomVertexConsumerProvider` recoloring depends on `RenderLayer.draw` hook that became `submit`.
- **Effort: XL.**

### Phase 5 — Stub / disable Iris / Sodium / Indium / Distant Horizons mixins
- **Goal:** remove all external shader/perf-mod coupling from the compile graph so vanilla rendering can go green independently.
- **Key files:** the 11 `iris.*` mixins + 1 `sodium.*` mixin in `bbs.client.mixins.json`; wrappers `forms/RecolorVertexSodiumConsumer.java`, `IrisTextureWrapper*`, `IrisUtils`, `ShaderCurves`, `SodiumUtils`.
- **Tasks:** remove the 12 entries from `bbs.client.mixins.json`; exclude/neutralize the wrapper utilities (compile-guard or delete from source set); ensure no remaining vanilla code hard-references Iris/Sodium types. Keep `RecolorVertexConsumer` (vanilla path) if still wired through `RenderLayerMixin`/the submit model.
- **Risks:** low for compile (removal is straightforward); the cost is deferred re-integration against 1.21.11-matched Sodium/Iris builds later — those mods rewrote internals at exactly 1.21.5/1.21.6/1.21.9/1.21.11.
- **Effort: M** (mostly deletion + reference cleanup).

### Phase 6 — Iterate `./gradlew build` to green
- **Goal:** clean compile of both source sets; mixins apply (refMap resolves); jar builds.
- **Tasks:** loop build, fix residual remap/mixin-target failures (see §5), confirm access-widener entries still resolve (§6), confirm custom shader/pipeline registration compiles. Validate `genSources` names against assumptions in this doc.
- **Risks:** mixin injection points shifted by Yarn renames or the render reworks (esp. `GameRendererMixin`, `WorldRendererMixin`, `RenderTickCounterMixin`, `RenderLayerMixin`).
- **Effort: M–L** (depends on how clean Phases 3–4 land).

---

## 4. Rendering Overhaul Deep-Dive

The pipeline changes that matter to this mod, with the migration approach and the specific hot files. Two cliffs dominate: **1.21.5 (GPU abstraction)** and the cumulative **1.21.6 / 1.21.9 / 1.21.11** GUI/submit/RenderSetup reworks.

### 4.1 `RenderSystem` → `GpuDevice` / `RenderPipeline` / `RenderPass` (1.21.5 — the critical break)
**Removed:** `BufferRenderer` (entirely), `RenderSystem.setShader(...)`, `BufferRenderer.drawWithGlobalProgram`, shader-program JSON definitions, direct `GlStateManager` state object references (now enum-driven: `BlendFunction`, `DepthTestFunction`, `FilterMode`, etc.).
**New shape:** `RenderSystem.getDevice()` → `GpuDevice` (`createBuffer/createTexture/createCommandEncoder`); `CommandEncoder.createRenderPass(...)`; `RenderPass` (AutoCloseable) does `setPipeline/setVertexBuffer/bindSampler/setUniform/draw`. `RenderPipeline.builder()...build()` + `RenderPipelines.register(...)` replaces shader-JSON + fixed-function state.
**Mod impact (very high):**
- `ui/utils/Gizmo.java` (33 calls), `forms/renderers/ModelFormRenderer.java` (14), `graphics/Draw.java` (6), `ui/framework/elements/utils/Batcher2D.java` (13), `client/BBSShaders.java`, `forms/CustomVertexConsumerProvider.java`, all `cubic/render/*`.
- **Texture/color binding:** `setShaderTexture`→`RenderPass.bindSampler/bindTexture`; `setShaderColor`→pipeline uniform.
- **Immediate-mode path:** `Tessellator.begin(mode, fmt)`→`BufferBuilder`→`buildOrThrow()`→`BuiltBuffer`/`MeshData`, then draw via a `RenderLayer` that carries a `RenderPipeline` (`RenderLayer.of(name, size, PIPELINE, params)`), or drive a `RenderPass` directly for per-draw uniforms. The `begin/buildOrThrow` shape is already in the 1.21.1 base; only *upload+draw* changes.

### 4.2 Vertex formats / buffers
`begin/buildOrThrow` stable. Upload changes at 1.21.5 (`GpuBuffer` via `GpuDevice.createBuffer`) and again at 1.21.6 (`createBuffer` signature dropped `BufferUsage`/`BufferType`→`int`; `GpuBufferSlice`; `MappableRingBuffer`). The 7 `BBSShaders` vertex formats (`POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL`, `POSITION_TEXTURE_COLOR`, etc.) carry forward as `VertexFormats.*` but are now bound to the pipeline, not to a global shader. **`Batcher2D` and the VAO classes (`cubic/render/vao/*`) are the buffer hot spots.**

### 4.3 RenderLayer / RenderType
1.21.5: `RenderType.create` takes a `RenderPipeline`. 1.21.9: `render()`→`submit()`, `RenderType` exposes its `pipeline`. 1.21.11: RenderStateShard→`RenderSetup`+`RenderType`; `SOLID`/`CUTOUT`/`TRANSLUCENT`/`TRIPWIRE` split into `_BLOCK`/`_TERRAIN`. **Files:** form renderers, `RenderLayerMixin` (hook `draw`→submit), `WorldRendererMixin` (`renderLayer` injects).

### 4.4 Shaders
JSON programs removed at 1.21.5 → declare `RenderPipeline`s in code; GLSL sources stay. std140 uniform blocks at 1.21.6. File renames at 1.21.9 (`blit_screen.json`→`screenquad.json`). **`client/BBSShaders.java`** must be rewritten from `ShaderProgram`+`ProxyResourceFactory` loading to code-defined pipelines; every `RenderSystem.setShader(() -> program)` callsite (`ModelInstance`, form renderers) is removed.

### 4.5 Framebuffers
1.21.5: `Framebuffer`/`RenderTarget` lose GL ids — `getColorTexture()`/`getDepthTexture()` return `GpuTexture`; `bindWrite/unbindWrite/setClearColor/framebufferId/checkStatus` removed; draw via `RenderPass`. 1.21.6: accessors return `GpuTextureView`. **Files:** `graphics/Framebuffer.java` (GL30 wrapper, 178 LOC), `graphics/FramebufferManager.java`, `forms/renderers/FramebufferFormRenderer.java` (220 LOC), `client/BBSRendering.java` (video export, framebuffer swap). Reconcile with the `MinecraftClient.framebuffer` access-widener.

### 4.6 DrawContext / GUI
1.21.2: `blit/drawTexture` require a RenderType function + PNG sizes; `setColor` removed (now a param). 1.21.6: two-phase GUI — `GuiGraphics(GuiRenderState)`, **`pose()`→`Matrix3x2fStack`**, `flush()` removed, `drawString` needs non-zero alpha. **Files:** `Batcher2D`, `UIRenderingContext`, `UIPixelsEditor`, `UIScreen`. The batcher's 4×4-pose assumptions break here.

### 4.7 Submit model (1.21.9)
Custom geometry in many render methods moves to `SubmitNodeCollector.submitCustomGeometry(MatrixStack, RenderType, fn)`; text via `submitText`. Affects entity-render-driven form rendering (`EntityRenderDispatcherMixin`, `LivingEntityRendererMixin`, `RenderLayerMixin`) and `Draw`/line rendering injected into the world.

---

## 5. Mixin Migration Table

Server mixins (11) and vanilla client mixins (24) must be verified against 1.21.11 Yarn; render-touching ones are high-risk because their target methods were reworked.

### Server mixins (`bbs.mixins.json`)
| Mixin | Target | Risk | Action |
|---|---|---|---|
| PlayerEntityMixin | `entity.player.PlayerEntity` | **High** | `writeCustomDataToNbt`/`readCustomDataFromNbt` TAIL injects → retarget to `writeCustomData(WriteView)`/`readCustomData(ReadView)` (1.21.6); verify `getBaseDimensions` RETURN |
| PlayerEntityMorphMixin | `entity.player.PlayerEntity` | Med | Interface + `baseTick` override; verify signature |
| ServerPlayNetworkHandlerMixin | `server.network.ServerPlayNetworkHandler` | Med | `parse` HEAD + `onPlayerInteractBlock` REDIRECT; verify targets |
| BlockItemMixin | `item.BlockItem` | Med | `place` HEAD; verify `ActionResult` return-type change (1.21.2) |
| ServerWorldMixin | `server.world.ServerWorld` | Med | `setBlockBreakingInfo`/`spawnEntity` HEAD |
| ItemStackMixin | `item.ItemStack` | Med | `use`/`useOnBlock` HEAD; `use` now returns `ActionResult` (1.21.2) |
| WorldChunkMixin | `world.chunk.WorldChunk` | Med | `setBlockState` HEAD+RETURN, `@Share`/`LocalRef`; verify MixinExtras 0.5.0 |
| LivingEntityMixin (server) | `entity.LivingEntity` | Med | `applyDamage` HEAD; damage is now server-only/`ServerWorld` (1.21.2) |
| ServerPlayerEntityMixin | `server.network.ServerPlayerEntity` | Low | `dropItem` RETURN |
| LimbAnimatorAccessor | `entity.LimbAnimator` | Low | Accessor; verify field names survive remap |
| LevelPropertiesAccessor | `world.level.LevelProperties` | Low | Accessor; verify `levelInfo` field |

### Client vanilla mixins (`bbs.client.mixins.json`)
| Mixin | Target | Risk | Action |
|---|---|---|---|
| GameRendererMixin | `render.GameRenderer` | **High** | `bobView/getFov/tiltViewWhenHurt/renderHand/renderWorld/render` — render path heavily reworked; re-find all injection points |
| WorldRendererMixin | `render.WorldRenderer` | **High** | `renderSky/renderLayer/loadEntityOutlinePostProcessor/onResized/setupFrustum` — sky/layer/fog rewritten across range; verify each |
| RenderTickCounterMixin | `render.RenderTickCounter.Dynamic` | Med | `beginRenderTick` HEAD; verify class still exists |
| RenderLayerMixin | `render.RenderLayer` | **High** | `draw` HEAD → became `submit` (1.21.9); retarget the custom-VertexConsumer hook |
| LivingEntityRendererMixin | `render.entity.LivingEntityRenderer` | **High** | INVOKE-ordinal inject — submit model (1.21.9) likely moves the target |
| EntityRenderDispatcherMixin | `render.entity.EntityRenderDispatcher` | **High** | `@WrapOperation` on render — verify target under submit model |
| EntityRendererMixin | `render.entity.EntityRenderer` | Med | `renderLabelIfPresent` HEAD |
| PlayerEntityRendererMixin | `render.entity.PlayerEntityRenderer` | Med | `render/getPositionOffset/renderArm` |
| BlockEntityRenderDispatcherMixin | `render.block.entity.BlockEntityRenderDispatcher` | Med | three render overloads HEAD |
| InGameHudMixin | `gui.hud.InGameHud` | Med | `render` HEAD+TAIL — GUI two-phase (1.21.6) may change shape |
| CameraMixin | `render.Camera` | Low–Med | `update` RETURN |
| WindowMixin | `client.util.Window` | Low | dimension getters |
| MouseMixin / KeyboardMixin / KeyboardInputMixin | input | Low–Med | keybinding category rework is registration-side (1.21.9), not these targets |
| SimpleOptionMixin | `client.option.SimpleOption` | Low | `getValue` HEAD |
| WorldMixin / ClientWorldPropertiesMixin | world | Low | `getRainGradient`/`getTimeOfDay` HEAD |
| LanguageManagerMixin / ResourceReloadLoggerMixin | resource | Low–Med | reload hooks; resource-loader reworked 1.21.9 — verify `finish`/`reload` still exist |
| LivingEntityMixin (client) / LivingEntityUpdateMixin | `entity.LivingEntity` | Low | interface + `baseTick` TAIL |
| IntegratedServerMixin | `server.integrated.IntegratedServer` | Low | `tick` `@WrapOperation` |
| EntityRendererDispatcherInvoker / LivingEntityRendererInvoker / ClientPlayerEntityAccessor / RenderTickCounterAccessor | accessors/invokers | Low | verify names survive remap |

### Stubbed / disabled (remove from `bbs.client.mixins.json` in Phase 5)
**Iris (11):** `iris.SystemTimeUniformsTimerMixin`, `iris.EntityVertexMixin`, `iris.IrisRenderingPipelineAccessor`, `iris.CustomUniformsBuilderMixin`, `iris.CustomUniformsAccessor`, `iris.JcppProcessorMixin`, `iris.ShaderPackMixin`, `iris.IrisMixin`, `iris.SliderElementWidgetMixin`, `iris.StringElementWidgetInvoker` (+ `iris.*` is also where pseudo-mixins/`remap=false` live).
**Sodium (1):** `sodium.ColorAttributeMixin`.
**Indium / Distant Horizons:** no dedicated mixins found in the inventory; ensure no soft references remain. If any DH/Indium wrapper utility exists, neutralize alongside the Sodium wrappers.

---

## 6. Access-Widener Review (`src/main/resources/bbs.accesswidener`, v2, 10 entries)

| Entry | Risk | Likely action |
|---|---|---|
| `accessible com/mojang/blaze3d/systems/RenderSystem shaderLightDirections [Lorg/joml/Vector3f;` | **High** | Field almost certainly removed/renamed by the 1.21.5 shader-state overhaul. Expect to delete; lighting now flows through pipeline uniforms / `RenderSystem.setShaderLights` replacements. Fix `FramebufferFormRenderer` (the consumer). |
| `accessible com/mojang/blaze3d/platform/GlStateManager TEXTURES [...Texture2DState;` | **High** | `GlStateManager` state objects largely gone (1.21.5). Likely delete; rework texture-unit logic onto `GpuTexture`/sampler. |
| `accessible com/mojang/blaze3d/platform/GlStateManager activeTexture I` | **High** | Same as above. |
| `accessible com/mojang/blaze3d/platform/GlStateManager$Texture2DState` | **High** | Inner class likely gone; delete. |
| `accessible net/minecraft/client/MinecraftClient framebuffer L...gl/Framebuffer;` | **Med–High** | Field likely survives but `Framebuffer` type/internals changed (GpuTexture/View). Verify path + the `gl/Framebuffer` descriptor. |
| `mutable net/minecraft/client/MinecraftClient framebuffer L...gl/Framebuffer;` | **Med–High** | Same; needed for video-recording framebuffer swap in `BBSRendering`. |

**Net:** the 3 `GlStateManager` entries and the `RenderSystem.shaderLightDirections` entry are the most likely to require removal and code rework (Phase 4). The two `MinecraftClient.framebuffer` entries probably survive but need descriptor verification once `genSources` runs. No AW format change in the range; some upstream transitive AWs (1.21.4/1.21.9) *may* let you drop other entries but nothing forces it.

---

## 7. Master-Delta Reconciliation Plan (Phase 0)

**Decision: incremental theme-grouped cherry-pick onto a working branch — do NOT `git merge master`.** A single merge surfaces only 3 textual conflicts then buries you in hundreds of opaque compile errors from removed 1.20.4 render/tick APIs, with no per-feature checkpoints.

**Order (dependency- and conflict-isolated):**
1. **Core-only, compile-clean first:** interpolation/keyframe factories (`AutoBezier`, `Interpolations`, `PoseKeyframeFactory`), `BBSSettings`/`ValueLinks`/`LinkUtils`, pose-data changes, lowercase track names, framerate option.
2. **New self-contained files:** model loaders (`IModelLoader`, `ModelMesh`/`ModelGroup`, multi-texture OBJ/BOBJ), particle `UIDockLayout`/Molang preview — clean adds; translate render API only in `cubic/physics/ModelPhysicsDebug.java` (+376, all old API).
3. **IK (theme B):** then resolve the one hard conflict `cubic/ik/ModelIKDebug.java`, re-expressing pole-target debug markers in the new `BufferBuilder` API.
4. **Particles → Gizmo/transform → Film/replay:** the API-heavy UI groups; fix `tickDelta()`/`BufferBuilder`/`Matrix4fStack` per group; `./gradlew build` after each.

**Conflict-risk files:**
- **Hard textual:** `src/client/java/mchorse/bbs_mod/cubic/ik/ModelIKDebug.java`.
- **Easy textual:** `src/client/java/mchorse/bbs_mod/ui/film/UIFilmPreview.java`, `src/client/java/mchorse/bbs_mod/ui/film/controller/FilmEditorController.java`.
- **Silent compile-break hotspots (auto-merge clean, won't compile):** `film/BaseFilmController.java`, `WorldFilmController.java`, `FilmControllerContext.java` (5× `tickDelta()`), `cubic/physics/ModelPhysicsDebug.java`, `ui/utils/Gizmo.java`, `utils/MatrixStackUtils.java` (model-view `Matrix4fStack`), plus ~43 `Tessellator...getBuffer()`, ~47 two-step `.begin(...)`, ~245 `.next()` across master's new client code.

**Recurring codemod patterns to mechanize:** `context.tickDelta()`→`context.tickCounter().getTickDelta(false)`; old `BufferBuilder` begin/next/end→new build/draw; model-view `MatrixStack`→`Matrix4fStack` (`pushMatrix/mul/rotate/popMatrix`). Note `git rerere` helps little (only 3 textual conflicts); per-group builds are the real safeguard.

> **Sequencing note:** Phase 0 is performed on the **1.21.1 toolchain** (translating only 1.20.4→1.21.1 API). The 1.21.5/.6/.9/.11 render rewrites then happen once, in Phases 3–4, over the reconciled code.

---

## 8. Risk Register (Top 10)

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| 1 | 2D batcher (`Batcher2D`) rewrite for 1.21.5 pipelines + 1.21.6 two-phase GUI / `Matrix3x2fStack` | High | XL | Isolate batcher; build a thin pipeline/RenderPass adapter; accept z-order regressions as runtime-deferred |
| 2 | Custom shader engine (`BBSShaders`, 7 programs) JSON→code pipelines + std140 | High | XL | Rewrite as `RenderPipelines`; port GLSL sources unchanged; convert uniforms to std140 blocks |
| 3 | Framebuffer GL30 wrapper (`graphics/Framebuffer`, `BBSRendering` video export) → GpuTexture/RenderPass | High | L | Re-target onto `GpuTexture`/`GpuTextureView` + `RenderPass`; verify `MinecraftClient.framebuffer` AW |
| 4 | Render-touching mixins lose injection points (GameRenderer/WorldRenderer/RenderLayer/Living+EntityRenderDispatcher) | High | L | Re-derive targets from `genSources`; adopt submit-model (`submit()`/`SubmitNodeCollector`) where needed |
| 5 | ReadView/WriteView persistence rewrite (entities + BEs) incl. `PlayerEntityMixin` NBT injects | High | M | Migrate `readData/writeData`/`readCustomData/writeCustomData`; codec-based `view.read/put`; retarget mixin |
| 6 | NBT Optional getters (1.21.5) silently mis-defaulted across `data/**` + keyframe factories | High | M | Use defaulted overloads / `getCompoundOrEmpty`; manual handling for array getters (no default overload) |
| 7 | Phase 0 master delta auto-merges clean but won't compile (old render/tick API) | High | L | Theme-grouped cherry-pick + per-group `./gradlew build`; mechanize the 3 codemod patterns |
| 8 | Access-widener `GlStateManager`/`RenderSystem.shaderLightDirections` entries invalid | High | M | Delete dead AW entries; rework the few consumers (`FramebufferFormRenderer`) onto new APIs |
| 9 | Loom `1.15-SNAPSHOT`→`1.14` / Yarn build drift / MixinExtras 0.5.0 resolution | Med | M | Pin Loom `1.14`; verify Yarn `<latest>` and resolved MixinExtras/Mixin versions before bump |
| 10 | Deferred Iris/Sodium re-integration is effectively a second port (their internals rewrote at 1.21.5–1.21.11) | High (later) | L (this session) | Out of scope now; stub in Phase 5; budget a dedicated later phase against 1.21.11-matched builds |

---

## 9. Verification Plan

**Definition of done THIS session = a green `./gradlew build`:**
- Both source sets (`main`, `client`) compile against 1.21.11 / Yarn `1.21.11+build.6`.
- All non-stubbed mixins apply: refMap generates, injection points resolve, no `defaultRequire` failures.
- Access-widener resolves with no missing members.
- Custom `RenderPipeline` registration and shader/GLSL loading compile.
- Jar assembles.

**Build-loop tactics:** `./gradlew --refresh-dependencies genSources` first (Phase 1); then iterate `./gradlew build`, fixing compile then mixin-apply errors; validate every named class/method in this doc against the freshly generated 1.21.11 sources (assume nothing).

**Deferred runtime checklist (NOT this session):**
- Dashboard + editors render (Batcher2D, DrawContext, fonts).
- Viewport gizmo displays and manipulates (33 `RenderSystem`-equivalent paths).
- Form previews: `ModelFormRenderer`, `FramebufferFormRenderer`, billboard/particle/trail/label/block/item.
- Color picker, graphs, keyframe editors.
- Block placement / break / interact action clips; model-block custom NBT persistence (ReadView/WriteView).
- Networking send/receive + crusher; gun projectile spawn + form sync (data components).
- Video export framebuffer swap; slow-mo/frame-rate-limit recording paths.
- Then, as a separate effort: re-enable Iris/Sodium against 1.21.11-matched builds.

---

## 10. Open Questions / UNCONFIRMED Items (resolve before/early in the bump)

1. **Yarn build number.** Confirm `<latest>`/`<release>` in the Yarn maven-metadata at execution; `1.21.11+build.6` was latest at report time but may have advanced.
2. **Fabric Loader / API newest patch.** `0.18.1` and `0.141.3+1.21.11` are likely-but-not-re-verified for the newest patch; any `0.18.x` loader and any `0.14x.y+1.21.11` API are safe — pick current latest.
3. **Loom version.** Branch is on non-standard `1.15-SNAPSHOT`; plan pins `1.14` (official for 1.21.11). Confirm `1.14` builds cleanly with the existing Gradle 9.2.0 wrapper, or decide to keep a 1.15 line if it's a real published build.
4. **Java toolchain in `build.gradle`.** Mixin compat is already `JAVA_21`; verify `sourceCompatibility`/`targetCompatibility`/`JavaLanguageVersion` are all 21 (runtime mandates Java 21).
5. **MixinExtras / Fabric Mixin resolved versions.** 1.21.9+ requires MixinExtras `0.5.0` + Fabric Mixin `0.16.3+mixin.0.8.7`; confirm Loom/Loader pull these (or pin explicitly).
6. **Indium / Distant Horizons surface.** Inventory found Iris (11) + Sodium (1) mixins but no DH/Indium mixins; confirm there is no other DH/Indium soft-dependency to stub.
7. **`MinecraftClient.framebuffer` AW descriptor.** Verify the `net/minecraft/client/gl/Framebuffer` type/path still exists post-1.21.6 (GpuTextureView migration) before trusting the two AW entries.
8. **Whether any custom post-effect chains exist.** Report F found no `.json` shader assets/post-chains; confirm during Phase 4 (post-chain migration is the heaviest shader churn if any exist).
