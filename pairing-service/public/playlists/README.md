# Synology NAS Hosted Playlists & Guides

Drop your `.m3u`, `.m3u8`, `.xml`, and `.xml.gz` files into this directory (`/docker/opentv-pairing/public/playlists/` on your Synology NAS).

They will be served with CORS enabled and proper IPTV MIME types:
- **Local Network**: `http://<YOUR_SYNOLOGY_IP>:3000/playlists/your_channels.m3u`
- **Reverse Proxy**: `https://pair.yourdomain.com/playlists/your_channels.m3u`

In OpenTV, you can enter this URL directly or push it to your device using the Pairing Portal!
