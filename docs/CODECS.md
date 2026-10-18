# Software decoders (FFmpeg extension)

## Why this exists

Android provides no free decoder for **MP2, AC-3, E-AC-3 or DTS**. A device can only play those if
its firmware has a licensed decoder, or if it passes the raw bitstream over HDMI to a TV/receiver
that decodes it. (MP2 is MPEG-1 Layer II — the audio several of the 24/7 music channels here
stream. TiviMate plays them because it ships FFmpeg; OpenTV was silent.)

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

## What is bundled now

`app/libs/media3-ffmpeg-decoder-1.5.0+1.aar` is a prebuilt of that extension, from the
[Jellyfin Media3 fork](https://github.com/jellyfin/jellyfin-androidx-media) — Maven Central
`org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1`, listed GPL-3.0 like this project itself. It is
built from the same Media3 release this app uses, so its classes line up with the `1.5.0` in
`gradle/libs.versions.toml`:

- sha256 `6B58E4576492BE77FC8F9540DB0A38793DF45319D0659039904F161F7780A328` (2.9 MB)
- ships `androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer` with the exact constructor Media3's
  `DefaultRenderersFactory` looks up, and `libffmpegJNI.so` for armeabi-v7a, arm64-v8a, x86 and
  x86_64.
- its FFmpeg includes the decoders that matter here: mp1, mp2, mp3, ac3, eac3, dca, truehd, mlp,
  flac, alac, vorbis, opus, aac, wmav2 (verified in the shipped `.so`).

When Media3 moves past 1.5.0, replace the file with the matching Jellyfin artifact (upstream bumps
the `+N` suffix), or build your own with the workflow below. `app/proguard-rules.pro` keeps the
four extension classes by name, because a minified release only reaches them reflectively.

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
  (armeabi-v7a, arm64-v8a, x86, x86_64), and so does the bundled Jellyfin AAR (~1.3 MB per ABI).
  All current target devices are ARM, so trimming x86 is a future size win — edit the ABI list in
  Media3's `build_ffmpeg.sh`, or filter ABIs in the app.
- **Licensing.** Media3's module is Apache 2.0, but it links FFmpeg, which is licensed separately.
  The decoders enabled above are covered by FFmpeg's LGPL configuration, which is compatible with
  this project's GPLv3; keep it that way if you add decoders.
- **Verify it loaded.** When the player is created, Media3 logs `Loaded FfmpegAudioRenderer.` (tag
  `DefaultRenderersFactory`) if the class is found and instantiated; if it is present but broken,
  player creation throws `Error instantiating FFmpeg extension` instead. No log line at all means
  the class was missing and only the platform decoders are in play.
