'use strict';

const http = require('http');
const path = require('path');
const crypto = require('crypto');
const express = require('express');
const { WebSocketServer, WebSocket } = require('ws');
const rateLimit = require('express-rate-limit');

const PORT = parseInt(process.env.PORT, 10) || 3000;
const SESSION_TTL_SECONDS = parseInt(process.env.SESSION_TTL_SECONDS, 10) || 1800; // 30 minutes default
const HEARTBEAT_INTERVAL_MS = 25000;

// Character set for 6-character ephemeral pairing codes
// Excludes visually ambiguous characters (0, O, 1, I) for effortless readability across a room
const CODE_CHARACTERS = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
const CODE_LENGTH = 6;

const app = express();

// Trust reverse proxy headers (X-Forwarded-For, X-Forwarded-Proto) for Cloudflare Tunnels, Synology, Traefik, etc.
app.set('trust proxy', 1);

// Enable CORS for NAS-hosted web files, playlists (M3U/M3U8), and EPG XML files
app.use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.header('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept, Authorization');
  if (req.method === 'OPTIONS') {
    return res.sendStatus(200);
  }
  next();
});

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Configure custom MIME types for IPTV playlists & guides hosted on the NAS
express.static.mime.define({
  'application/vnd.apple.mpegurl': ['m3u8'],
  'application/x-mpegurl': ['m3u'],
  'application/xml': ['xml'],
  'video/mp2t': ['ts']
});

// Serve the static Admin Web Portal and NAS-hosted files
app.use(express.static(path.join(__dirname, 'public')));

// Rate Limiting
const initLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 10, // Max 10 session creations per minute per IP
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many pairing requests from this IP. Please wait a minute and try again.' }
});

const pushLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 15, // Max 15 submissions per minute per IP
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many configuration push attempts from this IP. Please wait a minute and try again.' }
});

// In-memory state: Map<string, { ws: WebSocket | null, timer: NodeJS.Timeout, createdAt: number }>()
const sessions = new Map();

/**
 * Generate a cryptographically secure, collision-free ephemeral 6-character code
 */
function generateUniqueCode() {
  let attempts = 0;
  while (attempts < 100) {
    attempts++;
    const randomBytes = crypto.randomBytes(CODE_LENGTH);
    let code = '';
    for (let i = 0; i < CODE_LENGTH; i++) {
      code += CODE_CHARACTERS[randomBytes[i] % CODE_CHARACTERS.length];
    }
    if (!sessions.has(code)) {
      return code;
    }
  }
  throw new Error('Unable to generate unique pairing code');
}

/**
 * Clean up and purge a session from memory
 */
function purgeSession(code, reason = 'purged') {
  const session = sessions.get(code);
  if (!session) return;

  if (session.timer) {
    clearTimeout(session.timer);
  }

  if (session.ws && session.ws.readyState === WebSocket.OPEN) {
    try {
      session.ws.close(1000, reason);
    } catch (_) {
      // Ignore close errors during purge
    }
  }

  sessions.delete(code);
}

// -----------------------------------------------------------------------------
// REST Endpoints
// -----------------------------------------------------------------------------

/**
 * POST /api/pair/init
 * Generates an ephemeral 6-character code with a 10-minute TTL.
 */
app.post('/api/pair/init', initLimiter, (req, res) => {
  try {
    const code = generateUniqueCode();

    const timer = setTimeout(() => {
      const session = sessions.get(code);
      if (session && session.ws && session.ws.readyState === WebSocket.OPEN) {
        try {
          session.ws.close(4000, 'Session Expired');
        } catch (_) {}
      }
      sessions.delete(code);
      console.log(`[Session Expired] Code ${code} purged after ${SESSION_TTL_SECONDS}s`);
    }, SESSION_TTL_SECONDS * 1000);

    // Unref timer so it does not block Node process exit if empty
    sessions.set(code, {
      ws: null,
      timer,
      sources: [],
      createdAt: Date.now()
    });

    console.log(`[Session Created] Code: ${code} (expires in ${SESSION_TTL_SECONDS}s)`);

    res.status(200).json({
      code,
      expiresIn: SESSION_TTL_SECONDS,
      ttl: SESSION_TTL_SECONDS
    });
  } catch (err) {
    console.error('Failed to create pairing session:', err);
    res.status(500).json({ error: 'Failed to initialize pairing session' });
  }
});

/**
 * GET /api/pair/session
 * Check session status and retrieve existing playlists loaded on the remote TV.
 */
app.get('/api/pair/session', (req, res) => {
  const code = (req.query.code || '').trim().toUpperCase();
  if (!code || !sessions.has(code)) {
    return res.status(404).json({ error: 'Session not found or expired' });
  }

  const session = sessions.get(code);
  const connected = !!(session.ws && session.ws.readyState === WebSocket.OPEN);

  res.status(200).json({
    code,
    connected,
    sources: session.sources || []
  });
});

/**
 * POST /api/xtream/categories
 * Proxies live categories lookup to an Xtream server so the Admin Portal
 * can display interactive include/exclude category selectors.
 */
app.post('/api/xtream/categories', async (req, res) => {
  const { serverUrl, username, password } = req.body;

  if (!serverUrl || !username || !password) {
    return res.status(400).json({ error: 'Server URL, username, and password are required.' });
  }

  let cleanUrl = serverUrl.trim().replace(/\/+$/, '');
  if (!/^https?:\/\//i.test(cleanUrl)) {
    cleanUrl = `http://${cleanUrl}`;
  }

  const endpoint = `${cleanUrl}/player_api.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&action=get_live_categories`;

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 10000);

  try {
    const response = await fetch(endpoint, {
      signal: controller.signal,
      headers: {
        'User-Agent': 'OpenTV/0.1 (Android)'
      }
    });

    clearTimeout(timeoutId);

    if (!response.ok) {
      return res.status(response.status).json({ error: `Xtream server returned HTTP ${response.status}` });
    }

    const categories = await response.json();
    if (!Array.isArray(categories)) {
      return res.status(502).json({ error: 'Server did not return a valid categories list.' });
    }

    const cleaned = categories.map(cat => ({
      id: String(cat.category_id || cat.id || ''),
      name: String(cat.category_name || cat.name || '')
    })).filter(cat => cat.id && cat.name);

    return res.status(200).json({ categories: cleaned });
  } catch (err) {
    clearTimeout(timeoutId);
    console.error('Error fetching Xtream categories:', err.message);
    return res.status(502).json({ error: `Could not reach Xtream server: ${err.message}` });
  }
});

/**
 * POST /api/pair/push
 * Accepts provisioning data from the Admin Portal and relays it to the connected IPTV client.
 * Supports both multiple playlists (playlists array) and single playlist legacy format.
 */
app.post('/api/pair/push', pushLimiter, (req, res) => {
  const { code, playlistUrl, epgUrl, xtreamData, playlists } = req.body;

  if (!code || typeof code !== 'string') {
    return res.status(400).json({ error: 'Pairing code is required.' });
  }

  const normalizedCode = code.trim().toUpperCase();
  const session = sessions.get(normalizedCode);

  if (!session) {
    return res.status(404).json({
      error: 'Invalid or expired pairing code. Please refresh the code on your TV.'
    });
  }

  if (!session.ws || session.ws.readyState !== WebSocket.OPEN) {
    return res.status(400).json({
      error: 'Device is not connected. Make sure OpenTV is open on the Remote Setup screen.'
    });
  }

  // Normalize into an array of playlists
  let normalizedPlaylists = [];

  if (Array.isArray(playlists) && playlists.length > 0) {
    for (const item of playlists) {
      if (!item || typeof item !== 'object') continue;

      const isXtream = item.kind === 'xtream' || (item.serverUrl && item.username);
      if (isXtream) {
        if (!item.serverUrl || !item.username || !item.password) continue;
        normalizedPlaylists.push({
          id: item.id || null,
          name: (item.name && item.name.trim()) || 'Xtream Provider',
          kind: 'xtream',
          serverUrl: item.serverUrl.trim(),
          username: item.username.trim(),
          password: item.password,
          epgUrl: item.epgUrl ? item.epgUrl.trim() : null,
          options: {
            includeLive: item.options?.includeLive !== false,
            includeVod: !!item.options?.includeVod,
            includeSeries: !!item.options?.includeSeries,
            excludeKeywords: item.options?.excludeKeywords ? String(item.options.excludeKeywords).trim() : '',
            includeKeywords: item.options?.includeKeywords ? String(item.options.includeKeywords).trim() : '',
            excludeCategories: Array.isArray(item.options?.excludeCategories) ? item.options.excludeCategories : [],
            includeCategories: Array.isArray(item.options?.includeCategories) ? item.options.includeCategories : []
          }
        });
      } else if (item.playlistUrl && item.playlistUrl.trim()) {
        let pUrl = item.playlistUrl.trim();
        if (!/^https?:\/\//i.test(pUrl)) {
          pUrl = 'http://' + pUrl;
        }
        let pEpg = item.epgUrl ? item.epgUrl.trim() : null;
        if (pEpg && !/^https?:\/\//i.test(pEpg)) {
          pEpg = 'http://' + pEpg;
        }
        normalizedPlaylists.push({
          id: item.id || null,
          name: (item.name && item.name.trim()) || 'M3U Playlist',
          kind: 'm3u',
          playlistUrl: pUrl,
          epgUrl: pEpg
        });
      }
    }
  } else {
    // Single playlist legacy format
    const hasM3u = playlistUrl && typeof playlistUrl === 'string' && playlistUrl.trim().length > 0;
    const hasXtream = xtreamData && typeof xtreamData === 'object' && xtreamData.serverUrl && xtreamData.username && xtreamData.password;

    if (hasXtream) {
      normalizedPlaylists.push({
        name: (req.body.name && req.body.name.trim()) || 'Xtream Provider',
        kind: 'xtream',
        serverUrl: xtreamData.serverUrl.trim(),
        username: xtreamData.username.trim(),
        password: xtreamData.password,
        epgUrl: epgUrl ? epgUrl.trim() : null,
        options: {
          includeLive: req.body.options?.includeLive !== false,
          includeVod: !!req.body.options?.includeVod,
          includeSeries: !!req.body.options?.includeSeries,
          excludeKeywords: req.body.options?.excludeKeywords || '',
          includeKeywords: req.body.options?.includeKeywords || '',
          excludeCategories: Array.isArray(req.body.options?.excludeCategories) ? req.body.options.excludeCategories : [],
          includeCategories: Array.isArray(req.body.options?.includeCategories) ? req.body.options.includeCategories : []
        }
      });
    } else if (hasM3u) {
      let pUrl = playlistUrl.trim();
      if (!/^https?:\/\//i.test(pUrl)) {
        pUrl = 'http://' + pUrl;
      }
      let pEpg = epgUrl ? epgUrl.trim() : null;
      if (pEpg && !/^https?:\/\//i.test(pEpg)) {
        pEpg = 'http://' + pEpg;
      }
      normalizedPlaylists.push({
        name: (req.body.name && req.body.name.trim()) || 'M3U Playlist',
        kind: 'm3u',
        playlistUrl: pUrl,
        epgUrl: pEpg
      });
    }
  }

  if (normalizedPlaylists.length === 0) {
    return res.status(400).json({
      error: 'Please provide at least one valid playlist or Xtream Codes login.'
    });
  }

  // Construct payload with backward compatibility
  const primary = normalizedPlaylists[0];
  const payload = {
    type: 'provision',
    playlists: normalizedPlaylists,
    // Legacy fields for backward compatibility
    playlistType: primary.kind,
    playlistUrl: primary.playlistUrl || null,
    epgUrl: primary.epgUrl || null,
    xtreamData: primary.kind === 'xtream' ? {
      serverUrl: primary.serverUrl,
      username: primary.username,
      password: primary.password,
      options: primary.options
    } : null,
    timestamp: Date.now()
  };

  try {
    // Send configuration data in real time to the TV client
    session.ws.send(JSON.stringify(payload));
    console.log(`[Provision Delivered] Sent ${normalizedPlaylists.length} playlist(s) to code ${normalizedCode}`);

    // Acknowledge HTTP caller
    res.status(200).json({
      success: true,
      count: normalizedPlaylists.length,
      message: `${normalizedPlaylists.length} playlist(s) successfully delivered to device.`
    });

    // Cleanly close socket (code 1000) and purge session immediately
    setTimeout(() => {
      try {
        if (session.ws && session.ws.readyState === WebSocket.OPEN) {
          session.ws.close(1000, 'Provisioning complete');
        }
      } catch (_) {}
      clearTimeout(session.timer);
      sessions.delete(normalizedCode);
      console.log(`[Session Purged] Code ${normalizedCode} closed and removed.`);
    }, 250);

  } catch (err) {
    console.error(`[Provision Failed] Could not deliver payload to code ${normalizedCode}:`, err);
    return res.status(500).json({ error: 'Failed to transmit payload to device.' });
  }
});

/**
 * Health check endpoint
 */
app.get('/health', (req, res) => {
  res.status(200).json({
    status: 'ok',
    uptime: process.uptime(),
    activeSessions: sessions.size
  });
});

// Fallback route for SPA / Web Portal
app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// -----------------------------------------------------------------------------
// HTTP & WebSocket Server Setup
// -----------------------------------------------------------------------------

const server = http.createServer(app);
const wss = new WebSocketServer({ noServer: true });

// Upgrade HTTP connection to WebSocket
server.on('upgrade', (request, socket, head) => {
  const host = request.headers.host || 'localhost';
  let urlObj;
  try {
    urlObj = new URL(request.url, `http://${host}`);
  } catch (_) {
    socket.write('HTTP/1.1 400 Bad Request\r\n\r\n');
    socket.destroy();
    return;
  }

  const rawCode = urlObj.searchParams.get('code');
  const code = rawCode ? rawCode.trim().toUpperCase() : null;

  if (!code || !sessions.has(code)) {
    console.warn(`[WS Rejected] Code invalid or expired: ${code}`);
    wss.handleUpgrade(request, socket, head, (ws) => {
      // 4001: Session Not Found / Expired
      ws.close(4001, 'Session Not Found');
    });
    return;
  }

  wss.handleUpgrade(request, socket, head, (ws) => {
    wss.emit('connection', ws, request, code);
  });
});

wss.on('connection', (ws, request, code) => {
  const session = sessions.get(code);

  if (!session) {
    ws.close(4001, 'Session Not Found');
    return;
  }

  // If a previous socket exists for this code, close it
  if (session.ws && session.ws.readyState === WebSocket.OPEN) {
    try {
      session.ws.close(4002, 'Replaced by new connection');
    } catch (_) {}
  }

  session.ws = ws;
  ws.isAlive = true;

  console.log(`[WS Connected] Device paired to session code ${code}`);

  // Send an immediate connection acknowledgment to the client
  ws.send(JSON.stringify({
    type: 'connected',
    code,
    message: 'Paired to provisioning service. Awaiting configuration.'
  }));

  ws.on('message', (data) => {
    try {
      const msg = JSON.parse(data.toString());
      if (msg.type === 'device_info') {
        session.sources = Array.isArray(msg.sources) ? msg.sources : [];
        console.log(`[Device Info] Session ${code} reported ${session.sources.length} existing source(s) from device.`);
      }
    } catch (_) {}
  });

  ws.on('pong', () => {
    ws.isAlive = true;
    ws.missedPings = 0;
  });

  ws.on('close', (closeCode, reason) => {
    console.log(`[WS Closed] Code ${code} closed (${closeCode}: ${reason || 'no reason'})`);
    if (session.ws === ws) {
      session.ws = null;
    }
  });

  ws.on('error', (err) => {
    console.error(`[WS Error] Socket error for code ${code}:`, err.message);
    if (session.ws === ws) {
      session.ws = null;
    }
  });
});

// Periodic heartbeat to prevent proxies/firewalls from dropping idle sockets
const pingInterval = setInterval(() => {
  wss.clients.forEach((ws) => {
    if (ws.isAlive === false) {
      ws.missedPings = (ws.missedPings || 0) + 1;
      if (ws.missedPings >= 2) {
        return ws.terminate();
      }
    } else {
      ws.missedPings = 0;
    }
    ws.isAlive = false;
    try {
      ws.ping();
    } catch (_) {}
  });
}, HEARTBEAT_INTERVAL_MS);

wss.on('close', () => {
  clearInterval(pingInterval);
});

// Graceful shutdown
process.on('SIGTERM', () => {
  console.log('SIGTERM received. Shutting down pairing service...');
  clearInterval(pingInterval);
  for (const [code] of sessions) {
    purgeSession(code, 'Server shutting down');
  }
  server.close(() => {
    process.exit(0);
  });
});

process.on('SIGINT', () => {
  console.log('SIGINT received. Shutting down pairing service...');
  clearInterval(pingInterval);
  for (const [code] of sessions) {
    purgeSession(code, 'Server shutting down');
  }
  server.close(() => {
    process.exit(0);
  });
});

server.listen(PORT, () => {
  console.log(`=======================================================`);
  console.log(`OpenTV Remote Pairing Service running on port ${PORT}`);
  console.log(`Public Web Portal: http://localhost:${PORT}`);
  console.log(`=======================================================`);
});
