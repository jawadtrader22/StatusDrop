#!/usr/bin/env bash
# Rebuilds libwebp with 16 KB ELF alignment for the ffmpeg bundled by youtubedl-android.
#
# youtubedl-android's ffmpeg zip ships 4 KB-aligned libwebp*, which makes ffmpeg fail to load
# ("ffmpeg not found") on 16 KB page devices. App.kt copies these over the extracted ones on
# such devices only. Re-run when youtubedl-android bumps its ffmpeg, and delete this script
# plus the jniLibs/*/libsd16k_*.so files once upstream ships aligned webp libs.
#
# Usage: ANDROID_NDK=/path/to/ndk scripts/build-webp-16k.sh [libwebp-version]
set -euo pipefail

VERSION="${1:-1.5.0}"
NDK="${ANDROID_NDK:?set ANDROID_NDK to an NDK r27+ directory}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${WORK_DIR:-$ROOT/build/webp-16k}"
CMAKE="${CMAKE:-cmake}"

mkdir -p "$WORK"
cd "$WORK"
[ -d "libwebp-$VERSION" ] || {
    curl -sL -o libwebp.tar.gz "https://github.com/webmproject/libwebp/archive/refs/tags/v$VERSION.tar.gz"
    tar xzf libwebp.tar.gz
}

for ABI in arm64-v8a x86_64; do
    "$CMAKE" -S "libwebp-$VERSION" -B "build-$ABI" -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-24 \
        -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
        -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384" \
        -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON \
        -DWEBP_BUILD_ANIM_UTILS=OFF -DWEBP_BUILD_CWEBP=OFF -DWEBP_BUILD_DWEBP=OFF \
        -DWEBP_BUILD_GIF2WEBP=OFF -DWEBP_BUILD_IMG2WEBP=OFF -DWEBP_BUILD_VWEBP=OFF \
        -DWEBP_BUILD_WEBPINFO=OFF -DWEBP_BUILD_WEBPMUX=OFF -DWEBP_BUILD_EXTRAS=OFF \
        -DWEBP_BUILD_LIBWEBPMUX=ON
    "$CMAKE" --build "build-$ABI"

    OUT="$ROOT/app/src/main/jniLibs/$ABI"
    mkdir -p "$OUT"
    for LIB in webp sharpyuv webpdecoder webpdemux webpmux; do
        # lib*.so name so the APK packages it; App.kt renames it back when copying
        cp "build-$ABI/lib$LIB.so" "$OUT/libsd16k_$LIB.so"
        "$NDK"/toolchains/llvm/prebuilt/*/bin/llvm-strip --strip-unneeded "$OUT/libsd16k_$LIB.so"
    done
done
echo "Done: app/src/main/jniLibs/*/libsd16k_*.so"
