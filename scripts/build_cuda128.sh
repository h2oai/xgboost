#!/usr/bin/env bash
#
# build_cuda128.sh — build the h2oai xgboost 2.1.4 fork wheel against CUDA 12.8
# with Blackwell (sm_100 / sm_120) support, then run smoke checks.
#
# Background: this is the h2oai fork rebased onto upstream v2.1.4 (branch
# h2oai-2.1.4). Unlike the legacy 1.5 fork, 2.1.4 already:
#   * uses CCCL bundled with the CUDA toolkit (no vendored `cub` submodule), and
#   * its CMake already targets archs "50 60 70 80 90 100 120" for CUDA >= 12.8.
# So a CUDA 12.8 build needs no C++ archaeology — only a recent toolchain.
#
# Intended to run inside an NVIDIA CUDA 12.8 devel image, e.g.:
#   docker run --rm -it --gpus all \
#     -v "$PWD":/src -w /src nvidia/cuda:12.8.0-devel-ubuntu22.04 \
#     bash scripts/build_cuda128.sh
#
# The GPU smoke test (step 5) requires a visible GPU; on a Blackwell card it
# validates native sm_100/sm_120 execution. Compile + import + CPU checks run
# without a GPU.
#
# Env overrides:
#   GPU_COMPUTE_VER   GPU arch list (default: 70;75;80;86;90;100;120)
#   USE_NCCL          ON/OFF (default: ON)  — multi-GPU support
#   BUILD_JOBS        parallel build jobs (default: nproc)
#   SKIP_GPU_TEST     1 to skip the GPU training smoke check
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GPU_COMPUTE_VER="${GPU_COMPUTE_VER:-70;75;80;86;90;100;120}"
USE_NCCL="${USE_NCCL:-ON}"
BUILD_JOBS="${BUILD_JOBS:-$(nproc)}"
BUILD_DIR="${REPO_ROOT}/build"

log()  { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
fail() { printf '\n\033[1;31mFAIL: %s\033[0m\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------------------
log "0. Toolchain preflight"
command -v nvcc  >/dev/null || fail "nvcc not found — run inside a CUDA 12.8 devel image"
command -v cmake >/dev/null || fail "cmake not found"
CUDA_VER="$(nvcc --version | sed -n 's/.*release \([0-9]*\.[0-9]*\).*/\1/p')"
CMAKE_VER="$(cmake --version | sed -n 's/cmake version \([0-9.]*\).*/\1/p')"
echo "CUDA  toolkit: ${CUDA_VER}"
echo "CMake version: ${CMAKE_VER}"
echo "GPU archs    : ${GPU_COMPUTE_VER}"
# sm_100/sm_120 codegen requires CUDA >= 12.8 and CMake >= 3.30.
awk "BEGIN{exit !(${CUDA_VER} >= 12.8)}"  || fail "need CUDA >= 12.8 for Blackwell (got ${CUDA_VER})"
# version-sort guard for cmake >= 3.30
printf '3.30\n%s\n' "${CMAKE_VER}" | sort -V -C \
  || fail "need CMake >= 3.30 for sm_100/sm_120 (got ${CMAKE_VER}); install a newer cmake"

# ---------------------------------------------------------------------------
log "1. Fetch submodules (dmlc-core, gputreeshap — note: NO vendored cub in 2.1.4)"
# In a container git runs as root while the bind-mounted repo is owned by the
# host user, which trips git's "dubious ownership" guard. Mark it safe (the
# container is ephemeral, so a global config here is harmless).
git config --global --add safe.directory "${REPO_ROOT}" 2>/dev/null || true
git config --global --add safe.directory '*' 2>/dev/null || true
git -C "${REPO_ROOT}" submodule update --init --recursive

# ---------------------------------------------------------------------------
log "2. Configure + build libxgboost.so (CUDA=ON, NCCL=${USE_NCCL})"
cmake -S "${REPO_ROOT}" -B "${BUILD_DIR}" \
  -DUSE_CUDA=ON \
  -DUSE_NCCL="${USE_NCCL}" \
  -DGPU_COMPUTE_VER="${GPU_COMPUTE_VER}" \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DHIDE_CXX_SYMBOLS=ON \
  -GNinja 2>/dev/null || \
cmake -S "${REPO_ROOT}" -B "${BUILD_DIR}" \
  -DUSE_CUDA=ON \
  -DUSE_NCCL="${USE_NCCL}" \
  -DGPU_COMPUTE_VER="${GPU_COMPUTE_VER}" \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DHIDE_CXX_SYMBOLS=ON   # fall back to default generator (make) if ninja absent

cmake --build "${BUILD_DIR}" -j "${BUILD_JOBS}"
test -f "${REPO_ROOT}/lib/libxgboost.so" || fail "libxgboost.so not produced in lib/"
log "   built $(ls -lh "${REPO_ROOT}/lib/libxgboost.so" | awk '{print $5}') libxgboost.so"

# ---------------------------------------------------------------------------
log "3. Build the Python wheel (PEP517 / hatchling — reuses the lib just built)"
# 2.1.4 uses the packager backend; with lib/libxgboost.so present it is detected
# via locate_local_libxgboost() and NOT recompiled.
# --no-isolation means the build frontend will NOT install the backend's own
# build deps, so they must be present: the packager.pep517 backend wraps
# hatchling (plus packaging). Install them here so the import succeeds.
python3 -m pip install --upgrade pip build "hatchling>=1.12.1" "packaging>=21.3" >/dev/null
( cd "${REPO_ROOT}/python-package" && python3 -m build --wheel --no-isolation )
WHEEL="$(ls -t "${REPO_ROOT}"/python-package/dist/xgboost-*.whl | head -1)"
[ -n "${WHEEL}" ] || fail "no wheel produced"
log "   wheel: ${WHEEL}"

# ---------------------------------------------------------------------------
log "4. Smoke check: install + import + verify fork customizations present"
python3 -m pip install --force-reinstall "${WHEEL}" >/dev/null
python3 "${REPO_ROOT}/scripts/smoke_cuda128.py" \
  ${SKIP_GPU_TEST:+--skip-gpu} || fail "smoke checks failed"

log "DONE — wheel built and smoke-checked: ${WHEEL}"
echo "Blackwell native run is validated by step 5 only when run on an sm_100/sm_120 GPU."
