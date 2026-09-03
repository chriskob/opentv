'use strict';

const http = require('http');
const { WebSocket } = require('ws');

// Import server (start it)
process.env.PORT = '3456';
process.env.SESSION_TTL_SECONDS = '10';

require('./server.js');

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function request(options, data) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, res => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, data: JSON.parse(body) });
        } catch (_) {
          resolve({ status: res.statusCode, data: body });
        }
      });
    });
    req.on('error', reject);
    if (data) {
      req.write(typeof data === 'string' ? data : JSON.stringify(data));
    }
    req.end();
  });
}

async function runTests() {
  console.log('--- Starting Integration Tests ---');
  await delay(1000); // wait for server to listen

  // Test 1: Health check
  console.log('Test 1: Health check');
  const health = await request({
    host: 'localhost',
    port: 3456,
    path: '/health',
    method: 'GET'
  });
  console.assert(health.status === 200, `Expected 200, got ${health.status}`);
  console.assert(health.data.status === 'ok', 'Expected status: ok');
  console.log('✓ Health check passed');

  // Test 2: Invalid code on WebSocket
  console.log('Test 2: Invalid code WebSocket rejection');
  let wsClosedCode = null;
  const invalidWs = new WebSocket('ws://localhost:3456/?code=NOCODE');
  await new Promise(resolve => {
    invalidWs.on('close', (code, reason) => {
      wsClosedCode = code;
      resolve();
    });
  });
  console.assert(wsClosedCode === 4001, `Expected 4001, got ${wsClosedCode}`);
  console.log('✓ Invalid code WebSocket rejected with 4001');

  // Test 3: POST /api/pair/init
  console.log('Test 3: POST /api/pair/init');
  const initRes = await request({
    host: 'localhost',
    port: 3456,
    path: '/api/pair/init',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, {});
  console.assert(initRes.status === 200, `Expected 200, got ${initRes.status}`);
  const code = initRes.data.code;
  console.assert(typeof code === 'string' && code.length === 6, `Expected 6-char code, got ${code}`);
  console.assert(initRes.data.expiresIn === 10, `Expected expiresIn 10, got ${initRes.data.expiresIn}`);
  console.log(`✓ Got pairing code: ${code}`);

  // Test 4: Connect WebSocket with valid code
  console.log('Test 4: Connect WebSocket with valid code');
  const validWs = new WebSocket(`ws://localhost:3456/?code=${code}`);
  let receivedConnected = false;
  let receivedProvision = null;
  let cleanClose = null;

  await new Promise((resolve, reject) => {
    validWs.on('message', data => {
      const parsed = JSON.parse(data.toString());
      if (parsed.type === 'connected') {
        receivedConnected = true;
        resolve();
      } else if (parsed.type === 'provision') {
        receivedProvision = parsed;
      }
    });
    validWs.on('error', reject);
    validWs.on('close', (code, reason) => {
      cleanClose = code;
    });
  });
  console.assert(receivedConnected === true, 'Expected connected event');
  console.log('✓ Received WebSocket connected confirmation');

  // Test 5: POST /api/pair/push
  console.log('Test 5: POST /api/pair/push');
  const pushRes = await request({
    host: 'localhost',
    port: 3456,
    path: '/api/pair/push',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, {
    code: code,
    playlistUrl: 'https://test.com/stream.m3u8',
    epgUrl: 'https://test.com/epg.xml'
  });
  console.assert(pushRes.status === 200, `Expected 200, got ${pushRes.status}`);
  console.assert(pushRes.data.success === true, 'Expected success: true');
  console.log('✓ Push acknowledged with 200 OK');

  // Wait for provision message on WebSocket
  await delay(300);
  console.assert(receivedProvision !== null, 'Expected provision message on WebSocket');
  console.assert(receivedProvision.playlistUrl === 'https://test.com/stream.m3u8', 'Expected playlistUrl');
  console.assert(cleanClose === 1000, `Expected clean socket close (1000), got ${cleanClose}`);
  console.log('✓ WebSocket received payload and was closed cleanly with code 1000');

  // Test 6: Verify session is purged
  console.log('Test 6: Verify session is purged immediately after push');
  const secondPush = await request({
    host: 'localhost',
    port: 3456,
    path: '/api/pair/push',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, {
    code: code,
    playlistUrl: 'https://test.com/stream.m3u8'
  });
  console.assert(secondPush.status === 404, `Expected 404 for purged session, got ${secondPush.status}`);
  console.log('✓ Second push returned 404: session purged');

  // Test 7: Multi-playlist batch with Xtream options
  console.log('Test 7: Multi-playlist batch push with Xtream options');
  const init2 = await request({
    host: 'localhost',
    port: 3456,
    path: '/api/pair/init',
    method: 'POST'
  });
  const code2 = init2.data.code;
  const ws2 = new WebSocket(`ws://localhost:3456/?code=${code2}`);
  let receivedBatch = null;
  ws2.on('message', data => {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'provision') receivedBatch = msg;
  });
  await delay(200);

  const multiPushRes = await request({
    host: 'localhost',
    port: 3456,
    path: '/api/pair/push',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, {
    code: code2,
    playlists: [
      {
        name: 'M3U Channels',
        kind: 'm3u',
        playlistUrl: 'https://test.com/playlist.m3u8'
      },
      {
        name: 'Xtream Premium',
        kind: 'xtream',
        serverUrl: 'http://provider.tv:8080',
        username: 'testuser',
        password: 'testpassword',
        options: {
          includeLive: true,
          includeVod: false,
          includeSeries: false,
          excludeKeywords: 'Adult, XXX',
          excludeCategories: ['18', '99']
        }
      }
    ]
  });

  console.assert(multiPushRes.status === 200, `Expected 200, got ${multiPushRes.status}`);
  console.assert(multiPushRes.data.count === 2, `Expected count 2, got ${multiPushRes.data.count}`);
  await delay(200);
  console.assert(receivedBatch !== null, 'Expected batch provision message');
  console.assert(Array.isArray(receivedBatch.playlists), 'Expected playlists array');
  console.assert(receivedBatch.playlists.length === 2, 'Expected 2 playlists');
  console.assert(receivedBatch.playlists[1].options.includeLive === true, 'Expected includeLive true');
  console.assert(receivedBatch.playlists[1].options.excludeKeywords === 'Adult, XXX', 'Expected excludeKeywords');
  console.log('✓ Multi-playlist batch with options verified');

  console.log('\n=======================================');
  console.log('ALL INTEGRATION TESTS PASSED SUCCESSFULLY!');
  console.log('=======================================');
  process.exit(0);
}

runTests().catch(err => {
  console.error('Integration test failed:', err);
  process.exit(1);
});
