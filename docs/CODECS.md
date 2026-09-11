# Software decoders (FFmpeg extension)

## Why this exists

Android provides no free decoder for **AC-3, E-AC-3 or DTS**. A device can only play those if its
firmware has a licensed decoder, or if it passes the raw bitstream over HDMI to a TV/receiver that
decodes it.

That is why a channel can play in VLC or TiviMate and fail in OpenTV: those apps **ship their own
software decoders**, and we do not. A raw MPEG-TS channel carrying Dolby audio is the common case —
for example a stream whose PMT lists `H.264/AVC` alongside `AC-3` will play its video and then die on
audio unless something can decode it.

ExoPlayer has a decoder fallback, which OpenTV now enables (`setEnableDecoderFallback(true)`), but
Android has no AOSP AC-3 decoder for it to fall back *to*. The only real fix is to bundle one.

## What Media3 provides

`FfmpegAudioRenderer`, in Media3's `decoder_ffmpeg` module. It is **not published to Google Maven**
(see [ExoPlayer issue 2781](https://github.com/google/ExoPlayer/issues/2781)) — it must be
cross-compiled from source with the Android NDK. That is why it is not simply a line in
`build.gradle.kts`.

## How to build it

### On CI (recommended)

Run the **Build FFmpeg decoders** workflow (`.github/workflows/codecs.yml`) from the Actions tab.
It only produces an artifact — it never touches a release, so it cannot break the normal release
path. Defaults:

- `media3_tag` must match the `media3` version in `gradle/libs.versions.toml` (currently `1.5.0`).
- `decoders` defaults to `ac3 eac3 dca mlp truehd mp2 opus flac alac vorbis`.

Download the `media3-decoder-ffmpeg` artifact when it finishes and copy the AAR into `app/libs/`.
The build then picks it up automatically (see the `fileTree("libs")` hook in
`app/build.gradle.kts`) and `FfmpegAudioRenderer` is used wherever `MediaCodecAudioRenderer` cannot
handle the format.

### Locally (Linux or macOS)

Media3's own instructions, for reference:

```bash
./build_ffmpeg.sh \
  "${FFMPEG_MODULE_PATH}" "${NDK_PATH}" "${HOST_PLATFORM}" "${ANDROID_ABI}" "${ENABLED_DECODERS[@]}"
```

`ANDROID_ABI` is the API level for the native code and must not exceed the app's `minSdk`; the NDK
version Media3 tests against is r26b. Windows is not supported for this build.

## Notes and costs

- **APK size.** FFmpeg adds roughly 2-3 MB per ABI. The workflow builds every ABI its script lists
  (armeabi-v7a, arm64-v8a, x86, x86_64). All current target devices are ARM, so trimming x86 is a
  future size win — edit the ABI list in Media3's `build_ffmpeg.sh`, or filter ABIs in the app.
- **Licensing.** Media3's module is Apache 2.0, but it links FFmpeg, which is licensed separately.
  The decoders enabled above are covered by FFmpeg's LGPL configuration, which is compatible with
  this project's GPLv3; keep it that way if you add decoders.
- **Verify it loaded.** After installing a build with the AAR, Media3 logs an error at startup if a
  configured extension is missing. No such line means `FfmpegAudioRenderer` is in the renderer list.
