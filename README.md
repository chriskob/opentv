# OpenTV

**A free, open-source IPTV player for Android TV, Fire TV, phones and tablets.**

No account. No subscription. No server of ours between you and your provider.

[![Build](https://github.com/OWNER/opentv/actions/workflows/build.yml/badge.svg)](https://github.com/OWNER/opentv/actions/workflows/build.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

---

## Why this exists

There is a recurring pattern in this corner of the app world. A promising IPTV player appears.
It looks good. It sells a "lifetime" subscription. The lifetime turns out to be shorter than
the buyer's — the developer burns out, or moves on, or simply stops replying, and everyone who
paid is left with an app that no longer works and no way to fix it.

The people affected are not helpless. They are often technical. What they lack is not skill
but *access*: the code is closed, so nobody else can pick it up.

OpenTV is the same category of app built so that cannot happen. The source is public and
GPL-licensed. If the current maintainers vanish tomorrow, anyone can fork it, build it and
keep it alive. That is the entire point.

**OpenTV is not a fork or a decompilation of any existing app.** It is written from scratch.

## What it does

- **Live TV** from Xtream Codes logins and plain M3U/M3U8 playlists
- **A guide that stays put** — XMLTV EPG that survives restarts, refreshes incrementally, and
  never wipes itself
- **Movies and series** with resume
- **Favourites, categories, search**
- **D-pad first** — designed for a remote, works with a touchscreen
- **One APK** for Android TV, Fire TV, phones and tablets

## What it deliberately does not do

- **No cloud sync, no web dashboard, no account.** Your provider credentials never leave your
  device. This is a feature, not a gap — see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for
  the reasoning, and note that "sync your channel list to our servers" is precisely the
  feature whose hosting bill makes a one-off payment unsustainable.
- **No content.** OpenTV is a player. You bring a service you already pay for. The project has
  no affiliation with any provider and does not help you find one.

## Install

**→ [Install page](https://OWNER.github.io/opentv/)** — step-by-step for Chromecast with
Google TV, Android TV boxes, Fire TV Sticks, and phones.

On a TV, the quickest route is the **Downloader** app pointed at:

```
OWNER.github.io/opentv/apk
```

That address always redirects to the newest APK, so it never goes stale. Or grab it straight
from [Releases](../../releases).

Every release is built by GitHub Actions from a tagged commit, and the workflow that built it
is public — you can check the APK against the source, or rebuild it yourself, rather than
trusting anyone's word.

New to this? [SETUP.md](SETUP.md) walks through getting the repo, the install page and the
first build running.

## Build it yourself

```bash
git clone https://github.com/OWNER/opentv.git
cd opentv
./gradlew assembleDebug
```

You need JDK 17+ and the Android SDK (API 35). Android Studio will fetch it for you. The debug
APK lands in `app/build/outputs/apk/debug/`.

Run the tests:

```bash
./gradlew test
```

## Contributing

Please do. See [CONTRIBUTING.md](CONTRIBUTING.md).

The project is explicitly looking for people who own devices the maintainers do not. Most
"works for me" bugs in this category are device-specific — an Ugoos box, a first-gen ONN, a
Fire Stick Lite — and a bug report from someone who owns one is worth more than a week of
guessing. If a channel fails on your setup, [open an issue](../../issues/new/choose).

## Supporting the project

OpenTV is free and always will be. There is nothing to buy, no premium tier, and no lifetime
subscription — the failure mode this project was built in response to is a promise nobody
could keep, so we are not making one.

If it saves you money or annoyance and you want to say thanks, the maintainers accept coffees.
See [.github/FUNDING.yml](.github/FUNDING.yml). Contributions fund nothing except caffeine;
nobody is owed a feature for a tip, and nothing is gated behind one.

## Licence

[GPL-3.0-or-later](LICENSE).

Chosen deliberately. GPL means anyone can take this code, but if they ship it they must ship
their source too. Nobody gets to close it, rebrand it, and sell a lifetime subscription on top
of work the community did for free.

## Legal

OpenTV is a media player, comparable to VLC. It ships with no channels, no playlists, and no
links to any. What you point it at, and whether you are entitled to, is between you and your
provider.
