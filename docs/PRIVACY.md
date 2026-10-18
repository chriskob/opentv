# Privacy

OpenTV collects nothing. There is no analytics SDK, no crash reporter, no telemetry, and no
network call to any domain the project controls — because the project controls none.

## What is stored, and where

Everything lives in a SQLite database in the app's private storage on your device:

- Your provider's address, username and password
- The channel, movie and series catalog downloaded from your provider
- The guide (EPG)
- Your favorites, hidden channels and resume positions

None of it is uploaded anywhere. There is no account to create and no server to sync with.

## Who your device talks to

Only your provider, and only at the address you entered — unless you use the Torrents tab, which
talks to the BitTorrent swarm as described below. Channel logos and posters are fetched
from whatever URLs your provider puts in its catalog, which are usually its own servers but
may be third parties — that is your provider's choice, not ours.

## The torrent engine

The Torrents tab plays magnet links with a BitTorrent engine bundled inside the app. It is the one
part of OpenTV that talks to the internet without being handed a specific address, so it is worth
being precise about what it sends and to whom.

The engine runs only while it is being used: it is started when you open the Torrents tab and stopped
when the app is closed. While it runs it contacts:

- **The swarm the links point at.** That is how a magnet finds anyone at all — the engine announces
  "I am looking for this info hash" to the torrent's trackers and to the DHT, and other clients
  answer with their addresses. This tells the swarm the info hash you asked for and your IP address.
  Nothing else about you is attached to it: no account, no device identifier, no history.
- **The peers it then connects to**, to fetch the pieces of the file.

What it does not do:

- **It never uploads.** Uploads are disabled in the engine's configuration: no piece of any file you
  play is ever sent to anyone. The app reads that setting back from the running engine at startup and
  says so on the Torrents tab if it is not what it should be, rather than assuming it.
- **It never asks your router to open a port.** UPnP port forwarding is off, so nothing from outside
  your network can reach the device unprompted.
- **It never advertises itself on your network.** Local peer discovery and Bonjour/mDNS are off.
- **It writes nothing to the disk.** The cache is a fixed 64 MB pool in RAM, and pasted links are
  added without being saved, so stopping leaves nothing behind.
- **It never leaves the device.** The engine listens on `127.0.0.1` only: other devices on your
  network cannot reach it, and neither can anything on the internet.

One caveat, stated plainly because it is easy to claim more than the protocol delivers: even with
uploads off, the engine still sends the peer protocol's own chatter — handshakes, bitfields, tracker
and DHT announces. To the swarm, that says this address is interested in that info hash. Anyone who
needs a downloader that is invisible to a swarm should know that no BitTorrent client is; the
difference here is that nothing is ever given back.

## Cleartext HTTP

The app allows plain HTTP (`usesCleartextTraffic="true"`). The overwhelming majority of IPTV
panels are HTTP-only; refusing them would make the app useless to most of the people it is for.

Be aware of what that means: **on an HTTP connection, your provider username and password are
sent unencrypted**, and anyone able to observe the network between you and your provider can
read them. This is a property of how these services are built, not of OpenTV. If your provider
offers an HTTPS endpoint, use it.

## Uninstalling

Removing the app removes the database and everything in it.

## Reporting a privacy problem

Open an issue. If it involves something you would rather not post publicly, say so in the issue
without the details and a maintainer will arrange somewhere better.
