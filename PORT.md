# h2oai xgboost fork — port to upstream 2.1.4 (CUDA 12.8 / Blackwell)

Branch `h2oai-2.1.4`, cut from upstream tag `v2.1.4` (`62e792361`). This is
**Option B** from `DAI_CUDA128_MIGRATION_NOTES.md`: keep the h2oai
customizations, forward-ported onto a CUDA-12-capable upstream rather than
porting the legacy 1.5 fork's C++ to CUDA 12.8.

## Why 2.1.4 made this small
- `cub` is no longer a vendored submodule (2.1.4 uses the toolkit's CCCL), so
  the CUDA-11-era CUB/Thrust conflict that blocked the 1.5 fork is gone.
- 2.1.4's CMake already targets archs `50 60 70 80 90 100 120` for CUDA ≥ 12.8
  (`cmake/Utils.cmake`), i.e. **Blackwell sm_100/sm_120 support is built in**.
- The build is PEP517/hatchling (`python -m build`), not `setup.py bdist_wheel`.

## Customizations ported (live, load-bearing)
| Customization | Files | Notes |
|---|---|---|
| Between-bin quantile splits (PR #94) | `src/common/quantile.cc` (`AddCutPoint`), `src/common/quantile.cu` | one-line each; CPU site templated upstream, GPU kernel unchanged from 1.5 |
| XGBFI feature interactions | `src/analysis/xgbfi.{cc,h}`, `src/c_api/c_api.cc`, `include/xgboost/c_api.h`, `python-package/xgboost/core.py` | C-API adapted to `bst->GetThreadLocal()`; parses text dump (decoupled from internals); compiled automatically via `GLOB_RECURSE` in `src/CMakeLists.txt` |
| `DAI_XGBOOST_AVOID_LOGGER` env gate | `src/logging.cc` | added `<cstdio>/<cstdlib>` includes |
| sklearn `pred_leaf`/`pred_contribs`/`approx_contribs` | `python-package/xgboost/sklearn.py` | on `XGBModel.predict`, `XGBClassifier.predict`, `predict_proba`; bypasses inplace-predict and label post-processing when raw outputs requested |
| dask `ntree_limit` reinstatement | `python-package/xgboost/dask/__init__.py` | maps `ntree_limit`→`iteration_range` on `DaskScikitLearnBase.predict`/`apply` |

## Customizations added (new — not in the 1.5 fork)
| Customization | Files | Notes |
|---|---|---|
| Legacy GPU-param back-compat shim | `python-package/xgboost/core.py` (`_remap_legacy_gpu_params`, hooked in `Booster.set_param`) | Accepts the pre-2.0 GPU parameters DAI still sets and translates them to the 2.x device API, so DAI's ~25 call sites do not need rewriting. `set_param` is the single chokepoint for `Booster.__init__`, `train()` and direct `set_param()`. |

**Shim translation rules** (an explicit `device=` is never overridden):

| Legacy parameter | Becomes |
|---|---|
| `tree_method='gpu_hist'` | `tree_method='hist'` + `device='cuda'` |
| `updater='gpu_coord_descent'` | `updater='coord_descent'` + `device='cuda'` |
| `predictor='gpu_predictor'` | `device='cuda'` (key dropped) |
| `predictor='cpu_predictor'` | `device='cpu'` (key dropped) |
| `gpu_id=N` | device ordinal → `device='cuda:N'` (key dropped) |
| `n_gpus`, `single_precision_histogram` | dropped with a `UserWarning` (no 2.x equivalent; multi-GPU is Dask-driven) |

Note: the shim covers the *parameters* DAI passes in. DAI code that **reads
params back** — e.g. `self.model.get_params().get('predictor') == 'gpu_predictor'`
(`models_xgboost.py:1011`) — will no longer see `predictor`/`gpu_id`, so those
read sites still need DAI-side adjustment.

## Customizations dropped (obsolete in 2.1.4 — confirmed)
- **AUC-PR clamp** — upstream already tolerates the ratio via
  `CHECK_LE(score, 1.0 + kRtEps)` (`auc.cc`, `rank_metric.cc`).
- **GPU→CPU predictor rewrite on `Load`** — `gpu_predictor`/`cpu_predictor`
  removed in the 2.0 device-API change; no site to patch. (The new legacy
  GPU-param shim above now handles `predictor=` at param-set time instead.)
- **Binary-snapshot save fallback + `enable_experimental_json_serialization`
  / `gpu_page_size`** — gone upstream; were already dead in 1.5.
- **MOJO `ModelVisitor` + `format="mojo"` dump** — unused by DAI (MOJO goes
  through `model2proto` + `save_model()` JSON).
- **`use_label_encoder=False` default, `base_margin.copy()`, `DMatrix.__del__`
  swallow, cudf/cupy `sys.modules` guard** — upstream resolved differently.

## Build
`scripts/build_cuda128.sh` (+ `scripts/smoke_cuda128.py`, `Dockerfile.cuda128`):
build inside a CUDA 12.8 devel image; produces the wheel and runs the smoke
checks — import/version, sklearn `pred_contribs`/`pred_leaf`, the XGBFI C-API,
between-bin `hist` training, the legacy GPU-param shim, and (on a GPU) a
`gpu_hist`/`gpu_id` → `device='cuda'` train+predict. Requires CMake ≥ 3.30 for
sm_100/sm_120 codegen. The Blackwell **native** run is validated only when the
smoke test runs on an sm_100/sm_120 GPU — do that in GPU CI.

Status: full build + all smoke checks pass on a Blackwell GPU.

## DAI-side follow-ups (NOT in this repo)
1. Device-API: the legacy GPU-param shim (above) accepts DAI's existing
   `tree_method='gpu_hist'` + `gpu_id`/`predictor`/`n_gpus` params, so the
   ~25 *set* sites in `h2oaicore/models_xgboost.py` no longer need rewriting.
   What still needs DAI changes: sites that **read params back** (e.g.
   `get_params().get('predictor')` at `models_xgboost.py:1011`,
   `restore_booster_params` at ~1274) — those should switch to reading
   `device`. Verify on a real run with the shim's deprecation warnings.
2. `make/req_env.mk build_xgboost`: build via CMake + `python -m build` (not
   `setup.py bdist_wheel`); drop the `sed` that pins arch `35;...;86` and the
   `git submodule update cub` line; bump CMake; set CUDA 12.8 image.
3. Install the **legacy 1.5 fork wheel as `xgboost_prev`** for loading old
   experiments (the 1.5 between-bin trees differ from 2.1.4 — see below).
4. Move the `models_xgboost.py` version boundary to `"2.5.0"`.
5. MOJO: the `save_model('.json')` tree schema that `model2proto`'s
   `xgb_marsheller.py` parses (`learner.gradient_booster.model.trees[*]` with
   `split_indices`/`split_conditions`/`left_children`/`right_children`/
   `default_left`, plus `tree_info`/`num_trees`) is **still emitted unchanged
   by 2.1.4** (verified in `src/tree/tree_model.cc`), so numeric-model MOJO
   export should work as-is. Caveat: 2.x adds categorical-split fields
   (`categories`, `categories_segments`, `split_type`) that the marshaller does
   NOT read — if `enable_categorical=True` is ever used, MOJO would silently
   mis-export. Add a fail-fast guard there before enabling categoricals.

## Reproducibility note
The between-bin split patch is carried, but 2.1.4's GPU sketch is not bit-identical
to the 1.5 fork's. Models retrained on this build will not match legacy DAI
experiment scores exactly — retraining is fine; backtesting/parity is not.
