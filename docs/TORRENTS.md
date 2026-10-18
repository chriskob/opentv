# Torrents

The Torrents tab plays a magnet link with a BitTorrent engine that ships inside the APK. This is the
document for the engine itself: what it is, how it is wired in, the promises the app makes about it
and how those promises are kept, and how to move it to a newer version.

## What is bundled

| | |
| --- | --- |
| Engine | [TorrServer](https://github.com/YouROK/TorrServer) `MatriX.147` |
| Licence | GPL-3.0 (OpenTV is GPL-3.0-or-later, so bundling is compatible) |
| Asset | `TorrServer-android-arm7` from the release page for that tag |
| File | `app/src/main/jniLibs/armeabi-v7a/libtorrserver.so` |
| Size | 63,929,180 bytes |
| SHA-256 | `8cb44454ff201bd902a9c454e6f046d4fb25937a3b628270d0c7b48f1cc7b94c` |

The file is not committed to git — 64 MB would sit in every clone's history forever. It is
fetched and checksum-verified instead: `sh ./fetch-torrserver.sh` places it for a fresh clone,
and both CI workflows run the same script before assembling, so a build either contains the
engine or cannot produce an APK at all. The pin (URL and SHA-256 above) lives in that script.

It is a statically linked Go program for 32-bit ARM, ELF type `DYN` (PIE), which is what Android
requires of anything it executes.

The engine is GPL-3.0. The corresponding source is the upstream repository at that tag — there are no
local patches to it, the shipped file is the published release asset byte for byte (the SHA-256 above
is the check). Upstream publishes no `-gst` build for Android, and the one bundled has no transcoder,
which is the subject of *Known limits* below.

## Why it is bundled, and why as a child process

Bundling is what makes "paste a magnet, it plays" a single-app story. The alternative designs were
all worse for a player: a home server means having a server and keeping it running; a companion app
means installing the engine plus its UI and dealing with two apps that both want to be a player; a
runtime download of the engine means the feature is broken on first use and needs a working
connection to a third party just to exist.

Running it as a **child process** rather than linking it in matters more than it looks:

- A torrent engine is the most failure-prone thing in this app — other people's clients, unreliable
  peers, half-broken files, a 64 MB working set. As a child process it can be killed outright, and
  the UI survives to say why and offer to start it again.
- Nothing of it lives in the app's own address space, so there is no native state to unwind when the
  app exits: `stop()` signals it, and the process is gone.
- It is the same program the engine's users already run and test, with the same HTTP API — not a
  re-implementation with a library rewired into an app lifecycle.

## How it runs on Android

Android 10 and later refuse `exec` on a file in the app's own data directory (W^X). The one directory
an app may execute from is its **native library directory**, which the installer unpacks as root with
mode 755. So the engine ships as a `jniLibs/<abi>/*.so` file — a library in name only — and two
settings make that work:

- `android:extractNativeLibs="true"` in `AndroidManifest.xml`,
- `packaging { jniLibs { useLegacyPackaging = true } }` in `app/build.gradle.kts`.

Both are load-bearing and both are needed. AGP's newer default keeps native libraries compressed
inside the APK and maps them from there, which is faster and smaller — and impossible to execute,
because a file inside an APK is not a file you can run. Without either setting the engine would not
start, and the failure would look like "the torrent engine is not running" with no further
explanation.

`TorrentEngine` then:

- finds the binary in `applicationInfo.nativeLibraryDir` and reports `Unsupported` if this build
  carries none for the device's CPU;
- creates its data directory under `filesDir/torrents` (the engine will not create it itself — it
  exits with `open <dir>/config.db: No such file or directory`);
- writes `settings.json` **if it is not already there** (see below);
- stops any engine left behind by an OpenTV process that was killed rather than exited: it scans
  `/proc` for a command line holding its own data directory, and only signals a process owned by its
  own UID — a pid is recycled, and a filename alone is not proof;
- picks a port: **8090** when free (predictable for logs and manual testing) and otherwise any free
  port, so a user already running TorrServer on the device cannot break the bundled copy;
- runs `libtorrserver.so -p <port> -i 127.0.0.1 -d <filesDir>/torrents`, with stdout and stderr
  redirected to `engine.log` in that directory (truncated on each start, so it stays small);
- waits for `GET /echo` to answer, up to 20 seconds, and reports the log tail if the process dies or
  never answers.

`-i 127.0.0.1` is deliberate: the engine is this app's own child, on the same device, so nothing —
not the user's laptop, not their phone, not anything on the LAN — has any reason to reach it.

`SIGTERM` stops the engine gracefully (its Android entry point catches it, calls `server.Stop()`, and
exits), which is what `Process.destroy()` sends.

## The settings promise

The engine's defaults are a *server's* defaults, and three of them are wrong for a player somebody
runs in their living room:

| Default | Why it is wrong here | What OpenTV sets |
| --- | --- | --- |
| `DisableUpload: false` | The engine seeds every torrent it holds, from the user's connection | `true` — never upload |
| `DisableUPNP: false` | It asks the home router to open an inbound port over UPnP | `true` — no port forwarding |
| `EnableLPD`, `EnableBonjour` (both true) | It advertises itself to every device on the local network | both `false` |
| `UseDisk: false` (already off) | Kept deliberately: the cache is a bounded 64 MB pool in RAM | unchanged |

These are applied by writing `settings.json` into the engine's data directory **before its first
launch**, never by changing settings at runtime, because the engine's own settings API replaces the
entire settings object: a call with two fields in it silently zeroes everything else, including the
tracker list a magnet needs to find peers. That was verified against a real build, not inferred.

The file shipped is the one the engine writes for itself on first run, changed in the five keys
above and nowhere else. It has to be complete: the engine's loader is not a merge, so a key missing
from the file is a zero value, and `TorrentEngineSettingsTest` asserts that its key set is exactly
the 41 keys a MatriX.147 build writes.

Because the engine is a separate program that versions on its own schedule, OpenTV does not take its
own word for any of this: after the engine answers on `/echo`, the app reads `POST /settings
{action:"get"}` back and compares `DisableUpload`, `UseDisk`, `EnableLPD`, `EnableBonjour` and
`DisableUPNP`. Anything that does not match is named on the Torrents tab. If a future engine renames
a key, the app says so instead of claiming a promise it is no longer keeping.

What the promise does **not** cover, because the protocol does not allow it: even with uploads off,
the engine handshakes with peers and announces to trackers and the DHT, which tells the swarm this
address is interested in that info hash. No client can fetch a torrent while being invisible to the
swarm.

## What the app asks of the engine

All of it is in `TorrServerClient`, and the whole API surface used is this:

| Call | Used for |
| --- | --- |
| `GET /echo` | liveness and version, no auth, used as the readiness probe |
| `POST /torrents {"action":"add","link":…}` | add a pasted link, no `save_to_db` |
| `POST /torrents {"action":"list"}` | every torrent the engine holds; the one status read |
| `POST /torrents {"action":"rem","hash":…}` | forget a torrent (stops it, frees its cache, deletes its files) |
| `POST /torrents {"action":"drop","hash":…}` | stop it and free the memory without forgetting it — used when the player is left |
| `POST /torrents {"action":"wipe"}` | Remove all |
| `POST /settings {"action":"get"}` | the promise check above |
| `GET /stream/<name>?link=…&index=…&play&preload` | the URL handed to the player |

Two details in that table are not obvious and both were taken from the engine's source:

- **`list`, never `get`, for a status refresh.** `get` on a hash that is not in memory loads it from
  the engine's database and *starts downloading it again*: a one-second status poll through `get`
  would resurrect a torrent the engine had let go and begin fetching it from zero. Pasted links are
  added without being saved precisely so that there is nothing to resurrect, and `list` only reports
  what the engine actually holds — a hash missing from it is the clean signal that the engine no
  longer has the torrent, which is the normal end of a session.
- **`play` and `preload` are checked for presence, not for a value.** `?play&preload` is the flag
  form; the URL is built with OkHttp's `addQueryParameter`, which writes `play=`, and the engine's
  handler reads it with `c.GetQuery` — present with an empty value still counts. The magnet goes
  through the same builder so its `&dn=` and `&tr=` survive percent-encoding.

The path segment in the stream URL is the file's own name, extension included, because that is how
the player decides what it is looking at. `preload` makes the engine start fetching that file's
window before the player asks for its first byte.

## Lifecycle and cleanup

| When | What happens |
| --- | --- |
| App opened | the phone-add page starts listening on the LAN, if the box has a private address |
| Torrents tab opened | engine starts (first time only), promise check runs |
| Link pasted | `add`, then the row polls for metadata; playback starts by itself when the files are known |
| Link sent from a phone (app on any screen) | the Torrents tab opens and the link continues as if it had been pasted |
| Playing | the engine caches a sliding window of the file the player is reading |
| Player left | `drop` — fetching and peer connections stop at once |
| Torrents tab left | polling stops, so the engine's own 30-second no-reader timeout takes the torrent |
| Remove / Remove all | `rem` / `wipe` — the torrent is forgotten, and its directory with it |
| App closed | the phone-add page stops with the app; `SIGTERM` to the engine, then force-kill if it has not exited within 5 seconds |
| App killed by the system | the engine is found and stopped on the next start (the `/proc` sweep above) |

Nothing is written to the disk at any point in that table: `UseDisk` is false, so the cache is a
single 64 MB pool shared by every torrent the engine holds, with eviction across them — not 64 MB per
torrent, and not a growing file.

## Adding from a phone

Typing a magnet with a TV remote is the worst part of the feature, so the tab also serves a small page
on the local network for a phone to paste it into. The Torrents screen shows a QR code for it; nothing
is typed on the TV and there is no code to read back.

The page is **up for as long as the app runs**, not only while the tab is open, so a link can be sent
while the TV is on any screen — the app brings the Torrents tab to the front and starts the torrent as
if it had been pasted there. Its address is **stable between visits**: the socket prefers the fixed
port `8767` (a port already taken falls back to a random one, and the QR always shows the one in use)
and the key in the address is generated once and kept. That is what lets a phone hold a bookmark or a
Shortcuts action pointing at it. With the app closed the address simply does not answer.

- The socket binds to the device's Wi-Fi/Ethernet address, and only ever answers requests from
  private addresses. A VPN, a cellular interface or the wider internet cannot reach it.
- Every route needs the key: `/?t=…` opens the form and `/add?t=…&link=…` takes the link directly. A
  request without the key gets a 404, so knowing the address is not enough.
- Nothing is stored: the page is one self-contained document and the link goes straight to the
  engine.
- The phone is answered with what actually happened — `Sent to the TV`, an invalid-link message, or
  the engine's own refusal — because the request waits for the app's real outcome.

### iPhone: a Shortcut for a shared link

The page itself lays this out under *iPhone: send links without opening this page*, with its own
address filled in. In short:

1. New shortcut; in its settings turn on **Show in Share Sheet**, accepting URLs and Text.
2. Add a **Text** action containing `http://<box>:8767/add?t=<key>&link=` and insert the **Shortcut
   Input** right after it.
3. Add **Get Contents of URL**.
4. Share a magnet link to the shortcut. To copy a magnet and run the shortcut from the Home Screen
   instead, read the **Clipboard** in place of the share input.

The link is taken to the end of the address, percent-encoded or not: iOS Shortcuts sends a magnet's
`&dn=` and `&tr=` fields raw, and they are not treated as extra parameters.

## Known limits

- **32-bit ARM only.** The APK carries a `armeabi-v7a` engine, so the tab works on 32-bit ARM
  devices (which includes the ARM32 userspace on several cheap TV boxes) and reports plainly on
  anything else. Adding `arm64-v8a` and `x86_64` is copying two more release assets into `jniLibs`
  and updating this table; it costs about 64 MB each.
- **It is a big feature.** The engine is 64 MB in the APK and about the same again once installed.
  That is the real cost of a bundled engine, and it is paid by every install whether or not the tab
  is ever opened.
- **No transcoding.** Upstream publishes no `-gst` Android build, so the engine cannot remux. The
  player is Media3, which opens Matroska, MP4 and the transport-stream family but has no AVI or ASF
  extractor — an `.avi` in a torrent will not play, which is why the row says so before the player
  opens rather than after it fails.
- **Downloads are only as good as the swarm.** A magnet with no seeders cannot play, and the app
  says so after 45 seconds instead of looking frozen.
- **A new engine means a new OpenTV release.** The binary is compiled in; there is no download and
  no side-loading of the engine at runtime.

## Updating the engine

1. Take the `TorrServer-android-arm7` asset from the new release (the rules above also apply to
   `-arm64`, `-x86_64`, `-386` if more ABIs are ever added).
2. Update the version, size and SHA-256 in the table at the top of this file *and* the pin in
   `fetch-torrserver.sh`, then delete `app/src/main/jniLibs/armeabi-v7a/libtorrserver.so` so the
   next build fetches the new engine rather than keeping the old one.
3. Run the new binary's own defaults against the shipped file — start it once with an empty `-d`
   directory, and compare its `settings.json` with the template in `TorrentEngine`: any key it added,
   removed or renamed must be reflected there, because the loader does not merge.
4. Read `server/torr/state/state.go` in the new tag for `TorrentStatus` field changes, and
   `server/web/api/route.go` for endpoint changes.
5. `gradlew :app:testDebugUnitTest` — `TorrentEngineSettingsTest` fails if the key set drifted, and
   `TorrServerClientTest` covers the parts of the wire format the app depends on.
6. Check the promised settings once on a device (`adb shell` below), because a rename that the tests
   cannot see would show up as a warning line on the Torrents tab.

## Checking it by hand

The engine listens on the device's loopback, so from a development machine it is reached through the
box itself. With the Torrents tab open:

```sh
# Is it running, and what was it started with?
adb shell ps -A | grep torrserver

# Is it bound to loopback only? 0100007F:1F9A is 127.0.0.1:8090.
adb shell cat /proc/net/tcp | grep 1F9A

# What does it say it is configured to do?
adb shell 'printf "POST /settings HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: application/json\r\nContent-Length: 17\r\n\r\n{\"action\":\"get\"}" | nc 127.0.0.1 8090'
```

The settings answer should show `"DisableUpload":true`, `"UseDisk":false`, `"EnableLPD":false`,
`"EnableBonjour":false` and `"DisableUPNP":true`. `upload_speed` in a torrent's status should stay at
0 while it downloads; that, and not a byte count of zero, is the honest measure of "never uploads".
