#!/usr/bin/env bash
# Explicitly stages the pinned llama.cpp source for the Android LLM build.
# This is the only network step; Gradle and CMake never fetch source or weights.
set -euo pipefail

readonly REPOSITORY="https://github.com/ggml-org/llama.cpp.git"
readonly COMMIT="a7a98e0fffed794396b3fbad4dcdbbc184963645"
readonly TAG="b6500"
readonly TREE="b611e5e7692c49a935fa09fc9c90a6512466db09"

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
dest="${here}/../third_party/llama.cpp"
work="$(mktemp -d)"
stage=""
trap 'rm -rf "${work}"; if [[ -n "${stage}" ]]; then rm -rf "${stage}"; fi' EXIT

source_repo=""
if [[ $# -eq 2 && "$1" == "--from" ]]; then
    source_repo="$2"
    git -C "${source_repo}" cat-file -e "${COMMIT}^{commit}"
elif [[ $# -eq 0 ]]; then
    source_repo="${work}/source"
    git init --quiet "${source_repo}"
    git -C "${source_repo}" remote add origin "${REPOSITORY}"
    git -C "${source_repo}" fetch --quiet --depth 1 origin "${COMMIT}"
else
    echo "Usage: $0 [--from verified-git-checkout]" >&2
    exit 2
fi

actual_commit="$(git -C "${source_repo}" rev-parse "${COMMIT}^{commit}")"
actual_tree="$(git -C "${source_repo}" rev-parse "${actual_commit}^{tree}")"
if [[ "${actual_commit}" != "${COMMIT}" || "${actual_tree}" != "${TREE}" ]]; then
    echo "Pinned source mismatch: expected ${COMMIT} tree ${TREE}, got ${actual_commit} tree ${actual_tree}" >&2
    exit 1
fi

if [[ -e "${dest}" ]]; then
    if [[ -f "${dest}/.sakshi-source-lock" ]] && grep -qx "commit=${COMMIT}" "${dest}/.sakshi-source-lock" && \
       grep -qx "tree=${TREE}" "${dest}/.sakshi-source-lock" && [[ -f "${dest}/CMakeLists.txt" ]] && \
       [[ -f "${dest}/.sakshi-source-files.sha256" ]] && \
       (cd "${dest}" && sha256sum --check --status .sakshi-source-files.sha256); then
        echo "Pinned llama.cpp ${TAG} already prepared and verified at ${dest}"
        exit 0
    fi
    echo "Refusing to replace existing source at ${dest}; inspect it and move it explicitly." >&2
    exit 1
fi

mkdir -p "$(dirname "${dest}")"
stage="$(mktemp -d "$(dirname "${dest}")/.llama-stage.XXXXXX")"
git -C "${source_repo}" archive "${actual_commit}" | tar -x -C "${stage}"
(
    cd "${stage}"
    find . -type f ! -name '.sakshi-source-lock' ! -name '.sakshi-source-files.sha256' -print0 |
        sort -z | xargs -0 sha256sum > .sakshi-source-files.sha256
)
{
    echo "repository=${REPOSITORY}"
    echo "tag=${TAG}"
    echo "commit=${COMMIT}"
    echo "tree=${TREE}"
    echo "license=MIT (upstream LICENSE; review bundled dependency notices separately)"
} > "${stage}/.sakshi-source-lock"
mv "${stage}" "${dest}"
stage=""
# The source is ignored and staged by an explicit command only.
trap - EXIT
echo "Pinned llama.cpp ${TAG} prepared at ${dest}"
