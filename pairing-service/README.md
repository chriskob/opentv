# OpenTV Remote Pairing & Provisioning Service

A lightweight, production-ready, self-hosted pairing and provisioning microservice for the [OpenTV](https://github.com) IPTV player app.

## Overview

Entering long M3U URLs, XMLTV EPG links, or Xtream Codes passwords using an Android TV remote control is frustrating. This service allows an OpenTV client on a TV to display an ephemeral 6-character code and QR code. An administrator or user scans the QR code or opens the web portal on their phone/laptop, enters the IPTV credentials, and clicks **Send to Device**. The credentials are transmitted in real time over a secure WebSocket to the TV, which instantly saves them and dismisses the setup screen.

```
┌─────────────────┐             ┌─────────────────────────┐             ┌─────────────────────┐
│  OpenTV Client  │             │     Pairing Service     │             │  Admin Web Portal   │
│  (Android TV)   │             │  (Node.js + WebSockets) │             │   (Phone/Browser)   │
└────────┬────────┘             └────────────┬────────────┘             └──────────┬──────────┘
         │                                   │                                     │
         │  1. POST /api/pair/init           │                                     │
         ├──────────────────────────────────>│                                     │
         │  <─ Code: "K9X2B4", TTL: 600s    │                                     │
         │                                   │                                     │
         │  2. Connect wss://.../?code=K9X2B4│                                     │
         ├──────────────────────────────────>│                                     │
         │  <─ WS "connected" ack           │                                     │
         │                                   │                                     │
         │                                   │  3. Navigate to /?code=K9X2B4       │
         │                                   │<────────────────────────────────────┤
         │                                   │                                     │
         │                                   │  4. POST /api/pair/push (M3U/Xtream)│
         │                                   │<────────────────────────────────────┤
         │  5. Real-time WS push (payload)   │                                     │
         │<──────────────────────────────────┤  <─ 200 OK (Acknowledged)          │
         │                                   ├────────────────────────────────────>│
         │  6. Socket cleanly closed (1000)  │                                     │
         │<──────────────────────────────────┤  7. Session purged from memory      │
```

---

## Features

- **Ephemeral & Secure**: Pairing codes are cryptographically generated (6-character uppercase alphanumeric without ambiguous characters `0`/`O`/`1`/`I`). Codes expire in 10 minutes (TTL) and are automatically purged from memory immediately upon successful transmission.
- **Zero External Database**: In-memory JavaScript `Map` tracks active pairing sessions. No PostgreSQL, Redis, or SQLite required.
- **Reverse Proxy & Tunnel Ready**: Built with `trust proxy = 1` to seamlessly handle `X-Forwarded-For` and `X-Forwarded-Proto` behind Cloudflare Tunnels, Synology Reverse Proxy, Traefik, or Nginx.
- **Rate-Limiting Built-in**: Protects against brute-force and spam (10 initializations/min/IP, 15 submissions/min/IP).
- **Embedded Web Portal**: Beautiful, dark-mode, mobile-optimized administration portal in vanilla HTML/CSS/JS (zero external CDN or runtime dependencies).
- **Ultra-Lightweight Container**: Optimized Alpine Node.js 18 image running under an unprivileged `node` user with `dumb-init` process supervisor.

---

## Quick Start (Local Node.js)

### Prerequisites
- Node.js 18+
- npm 9+

### Installation & Run
```bash
cd pairing-service
npm install
npm start
```
The service will start on `http://localhost:3000`.

---

## Docker & Docker Compose Deployment

### Using Docker Compose (Recommended)
```bash
docker compose up -d --build
```
The service will be accessible on `http://localhost:3000`.

### Dockerfile Run Directly
```bash
docker build -t opentv-pairing-service .
docker run -d \
  --name opentv-pairing \
  --restart unless-stopped \
  -p 3000:3000 \
  -e PORT=3000 \
  -e SESSION_TTL_SECONDS=600 \
  -e TRUST_PROXY=1 \
  opentv-pairing-service
```

---

---

## Deployment on Synology NAS (Container Manager)

Synology DSM 7.2+ includes **Container Manager** (the evolution of Synology Docker) with native Docker Compose ("Project") support.

### Step 1: Copy the Service to Your Synology NAS
1. Open **File Station** on DSM.
2. Navigate to your shared `docker` folder (e.g. `/docker`).
3. Create a subfolder named `opentv-pairing`.
4. Copy the files from `opentv/pairing-service` into `/docker/opentv-pairing`:
   ```
   /docker/opentv-pairing/
   ├── Dockerfile
   ├── docker-compose.yml
   ├── package.json
   ├── server.js
   └── public/
       ├── index.html        <-- Remote Setup Web Portal
       ├── manager.html      <-- Channel Manager Portal
       ├── css/
       │   └── style.css     <-- Dark mode stylesheet (editable on NAS)
       ├── js/
       │   └── app.js        <-- Portal frontend logic (editable on NAS)
       └── playlists/
           └── README.md     <-- Drop .m3u and .xml EPG files here!
   ```

### Step 2: Create the Project in Container Manager
1. In DSM, open **Container Manager**.
2. Click **Project** in the left sidebar, then click **Create**.
3. Fill in the wizard:
   - **Project Name**: `opentv-pairing`
   - **Path**: Click **Set Path** and choose `/docker/opentv-pairing`.
   - **Source**: Select **Use existing docker-compose.yml**.
4. Click **Next**, and proceed through the summary to **Done**.
5. Container Manager will build the Alpine image, mount your `./public` directory from the NAS disk (`/volume1/docker/opentv-pairing/public`), and start the service on port `3000`.

> **Live Web Files on NAS**: Because `docker-compose.yml` mounts `./public:/app/public`, all HTML, CSS, JS, and M3U playlists live directly on your NAS storage. You can edit `index.html` or drop new `.m3u` files into `playlists/` via DSM File Station, and they are served instantly without rebuilding the container!

> **Port Conflict Note**: If port `3000` is already taken by another container or service on your Synology NAS, simply edit `docker-compose.yml` to change `3000:3000` to e.g. `3030:3000`. OpenTV supports any custom port.

### Step 3: Access via LAN or Setup Reverse Proxy / SSL

#### Option A: Direct Local LAN
You can immediately use `http://<YOUR_SYNOLOGY_IP>:3000` on your home network. On your TV in OpenTV, enter this address in the Server Settings dialog.

#### Option B: DSM Built-in Reverse Proxy (with Let's Encrypt SSL)
If you want to use a friendly HTTPS domain (e.g. `https://pair.mydomain.com`):
1. Go to DSM **Control Panel** -> **Login Portal** -> **Advanced** tab -> **Reverse Proxy**.
2. Click **Create**:
   - **Reverse Proxy Name**: `OpenTV Pairing`
   - **Source**:
     - Protocol: `HTTPS`
     - Hostname: `pair.mydomain.com`
     - Port: `443`
     - Enable HSTS: Checked
   - **Destination**:
     - Protocol: `HTTP`
     - Hostname: `localhost`
     - Port: `3000`
3. Click the **Custom Header** tab:
   - Click **Create** -> **WebSocket**.
   - DSM will automatically add:
     - `Upgrade: $http_upgrade`
     - `Connection: $connection_upgrade`
4. Click **Save**. Now your mobile admin portal and WebSockets work securely under `https://pair.mydomain.com`!

---

## Deployment with Cloudflare Tunnel

1. In the Cloudflare Zero Trust Dashboard, go to **Networks** -> **Tunnels**.
2. Add a Public Hostname pointing to your pairing service:
   - **Service Type**: `HTTP`
   - **URL**: `localhost:3000` (or the local container IP)
3. Under **Additional application settings**:
   - **HTTP Settings**: Enable **HTTP2** / **WebSockets**.
   - Cloudflare Tunnels forward WebSockets by default.

---

## REST & WebSocket API Specification

### 1. `POST /api/pair/init`
Generates a new pairing code and initiates an ephemeral session.

- **Request Body**: `{}`
- **Response (200 OK)**:
```json
{
  "code": "K9X2B4",
  "expiresIn": 600
}
```

### 2. `WebSocket /?code=XXXXXX`
The IPTV client opens this WebSocket connection immediately after receiving a code.

- **Query Param**: `code` (e.g. `K9X2B4`)
- **Connection Handshake**:
  - If code is invalid or expired: Server closes connection with code `4001` (`"Session Not Found"`).
  - If code is valid: Connection accepted.
- **Server Hello**:
```json
{
  "type": "connected",
  "code": "K9X2B4",
  "message": "Paired to provisioning service. Awaiting configuration."
}
```
- **Provision Event (incoming to client)**:
```json
{
  "type": "provision",
  "playlistType": "m3u",
  "playlistUrl": "https://provider.com/playlist.m3u8",
  "epgUrl": "https://provider.com/epg.xml",
  "xtreamData": null,
  "timestamp": 1725372400000
}
```
*Or for Xtream:*
```json
{
  "type": "provision",
  "playlistType": "xtream",
  "playlistUrl": null,
  "epgUrl": null,
  "xtreamData": {
    "serverUrl": "http://provider.tv:8080",
    "username": "user123",
    "password": "secretpassword"
  },
  "timestamp": 1725372400000
}
```

### 3. `POST /api/pair/push`
Pushes configuration details entered by the administrator to the connected device.

- **Request Body (M3U)**:
```json
{
  "code": "K9X2B4",
  "playlistUrl": "https://provider.com/playlist.m3u8",
  "epgUrl": "https://provider.com/epg.xml"
}
```
- **Request Body (Xtream Codes)**:
```json
{
  "code": "K9X2B4",
  "xtreamData": {
    "serverUrl": "http://provider.tv:8080",
    "username": "user123",
    "password": "secretpassword"
  }
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Configuration successfully delivered to device."
}
```

---

## Client Integration Examples

Production-ready integration source snippets are provided in `client-examples/`:
- [RemotePairingClient.kt](file:///c:/Users/buick/Desktop/data/opentv/pairing-service/client-examples/RemotePairingClient.kt) - Android Kotlin implementation with OkHttp & WebSockets.
- [remote_pairing_client.dart](file:///c:/Users/buick/Desktop/data/opentv/pairing-service/client-examples/remote_pairing_client.dart) - Flutter/Dart implementation with `web_socket_channel`.
