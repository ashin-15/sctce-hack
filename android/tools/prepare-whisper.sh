#!/usr/bin/env bash
# Explicit preparation step for the speech-to-text module (:processing:stt). This is the ONLY step that uses the
# network; the Gradle build never downloads anything and fails with a pointer to this script if the source is missing.
#
# Downloads the GitHub tag tarball of ONE pinned whisper.cpp release, checks its SHA-256 and extracts it to
# android/third_party/whisper.cpp/ (git-ignored).
#
# Release tag v1.9.4 was the newest stable release (not a pre-release) of ggml-org/whisper.cpp on 3 October 2026.
# The SHA-256 below was recorded by this project on first download on 3 October 2026 (trust on first use). It is NOT
# a checksum published by upstream. GitHub does not promise byte-stable tag tarballs, so a mismatch means "stop and
# look", not necessarily tampering.
set -euo pipefail

TAG="v1.9.4"
TARBALL_SHA256="57e280cee375ab02425b806ad5146b99f6eb9357e3c2b31357c8a6af2e2e44ae"
URL="https://github.com/ggml-org/whisper.cpp/archive/refs/tags/${TAG}.tar.gz"

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
dest="${here}/../third_party/whisper.cpp"
work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT

if [[ -f "${dest}/CMakeLists.txt" && -f "${dest}/.sakshi-tag" && "$(cat "${dest}/.sakshi-tag")" == "${TAG}" ]]; then
    echo "whisper.cpp ${TAG} already prepared at ${dest}"
    exit 0
fi

echo "Downloading ${URL}"
curl --fail --location --silent --show-error --output "${work}/whisper.tar.gz" "${URL}"

actual="$(sha256sum "${work}/whisper.tar.gz" | cut -d' ' -f1)"
if [[ "${actual}" != "${TARBALL_SHA256}" ]]; then
    echo "SHA-256 mismatch for ${TAG}: expected ${TARBALL_SHA256}, got ${actual}" >&2
    exit 1
fi

mkdir -p "${work}/extract"
tar -xzf "${work}/whisper.tar.gz" -C "${work}/extract" --strip-components=1
echo "${TAG}" > "${work}/extract/.sakshi-tag"

rm -rf "${dest}"
mkdir -p "$(dirname "${dest}")"
mv "${work}/extract" "${dest}"
echo "whisper.cpp ${TAG} prepared at ${dest}"
