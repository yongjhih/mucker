#!/usr/bin/env node

/**
 * Mucker Mobile Chaos Injection Script for CI/CD Pipelines
 *
 * Demonstrates how to use pure Node.js + WebSocket (CDP Fetch domain)
 * to randomly inject HTTP 500 errors and simulated network timeouts
 * into an Android app running in CI/CD without rooting or certificates.
 *
 * Usage:
 *   node chaos-injector.mjs --host=localhost:8080 --rate=0.4 --timeout=3000
 */

const WS = typeof globalThis.WebSocket !== 'undefined'
  ? globalThis.WebSocket
  : (await import('ws')).default;

const args = process.argv.slice(2).reduce((acc, arg) => {
  if (arg.startsWith('--')) {
    const [k, v] = arg.slice(2).split('=');
    acc[k] = v || true;
  }
  return acc;
}, {});

const TARGET_HOST = args.host || process.env.MUCKER_HOST || '127.0.0.1:8080';
const WS_URL = `ws://${TARGET_HOST}/devtools/page`;
const FAILURE_RATE = parseFloat(args.rate || process.env.CHAOS_RATE || '0.35');
const TIMEOUT_DELAY_MS = parseInt(args.timeout || '4000', 10);
const DURATION_SEC = parseInt(args.duration || '0', 10);

console.log(`
┌──────────────────────────────────────────────────────────┐
│   🌪️  Mucker Android Chaos Network Injector for CI/CD   │
└──────────────────────────────────────────────────────────┘
• Target:       ${WS_URL}
• Failure Rate: ${(FAILURE_RATE * 100).toFixed(0)}% (HTTP 500 / Timeout)
• Timeout Latency: ${TIMEOUT_DELAY_MS}ms
• Duration:     ${DURATION_SEC > 0 ? `${DURATION_SEC}s` : 'Continuous'}
`);

let msgId = 1;
const stats = {
  total: 0,
  passed: 0,
  injected500: 0,
  injectedTimeout: 0
};

const ws = new WS(WS_URL);

ws.addEventListener('open', () => {
  console.log(`[✓] Connected to Mucker Engine on Android device!`);
  console.log(`[✓] Enabling CDP Fetch.enable interception domain...`);

  // Enable Fetch domain to pause requests matching patterns
  ws.send(JSON.stringify({
    id: msgId++,
    method: 'Fetch.enable',
    params: {
      patterns: [{ urlPattern: '*' }]
    }
  }));

  console.log(`[⚡] Chaos engine active. Listening for OkHttp requests from Android App...\n`);
});

ws.addEventListener('message', async (event) => {
  try {
    const raw = typeof event.data === 'string' ? event.data : event.data.toString();
    const msg = JSON.parse(raw);

    if (msg.method === 'Fetch.requestPaused') {
      const { requestId, request } = msg.params;
      stats.total++;

      const rand = Math.random();
      const url = request.url;
      const method = request.method;

      if (rand < FAILURE_RATE * 0.6) {
        // Mode A: Random 500 Internal Server Error
        stats.injected500++;
        console.log(`[🔥 CHAOS 500] ${method} ${url}`);

        const errorPayload = JSON.stringify({
          error: 'Simulated Chaos Outage',
          code: 'SERVICE_UNAVAILABLE',
          timestamp: new Date().toISOString(),
          injectedBy: 'mucker-chaos-ci'
        });

        ws.send(JSON.stringify({
          id: msgId++,
          method: 'Fetch.fulfillRequest',
          params: {
            requestId,
            responseCode: 500,
            responseHeaders: [
              { name: 'Content-Type', value: 'application/json' },
              { name: 'X-Injected-By', value: 'Mucker-Chaos-CI' }
            ],
            body: Buffer.from(errorPayload).toString('base64')
          }
        }));
      } else if (rand < FAILURE_RATE) {
        // Mode B: Artificial Timeout & High Latency
        stats.injectedTimeout++;
        console.log(`[⏳ CHAOS TIMEOUT] ${method} ${url} (delay ${TIMEOUT_DELAY_MS}ms)`);

        setTimeout(() => {
          const timeoutPayload = JSON.stringify({
            error: 'Gateway Timeout (Simulated)',
            code: 'GATEWAY_TIMEOUT',
            delay: TIMEOUT_DELAY_MS
          });

          ws.send(JSON.stringify({
            id: msgId++,
            method: 'Fetch.fulfillRequest',
            params: {
              requestId,
              responseCode: 504,
              responseHeaders: [
                { name: 'Content-Type', value: 'application/json' },
                { name: 'X-Injected-By', value: 'Mucker-Chaos-Timeout' }
              ],
              body: Buffer.from(timeoutPayload).toString('base64')
            }
          }));
        }, TIMEOUT_DELAY_MS);
      } else {
        // Normal Pass-Through: Allow request to proceed to real server
        stats.passed++;
        console.log(`[✓ PASS] ${method} ${url}`);

        ws.send(JSON.stringify({
          id: msgId++,
          method: 'Fetch.continueRequest',
          params: { requestId }
        }));
      }
    }
  } catch (err) {
    console.error('Error handling message:', err);
  }
});

ws.addEventListener('error', (err) => {
  console.error('[✖] WebSocket connection error:', err.message || err);
  console.error('    Make sure ADB port forwarding is active:');
  console.error('    adb forward tcp:8080 tcp:8080');
  process.exit(1);
});

ws.addEventListener('close', () => {
  printReport();
  console.log('[!] Connection closed.');
});

function printReport() {
  console.log(`\n──────────────────────────────────────────────────────────`);
  console.log(`📊 Chaos Test Run Summary:`);
  console.log(`• Total Requests Intercepted: ${stats.total}`);
  console.log(`• Passed to Live Server:     ${stats.passed}`);
  console.log(`• Injected HTTP 500:         ${stats.injected500}`);
  console.log(`• Injected Timeouts:         ${stats.injectedTimeout}`);
  console.log(`──────────────────────────────────────────────────────────\n`);
}

process.on('SIGINT', () => {
  printReport();
  process.exit(0);
});

if (DURATION_SEC > 0) {
  setTimeout(() => {
    printReport();
    ws.close();
    process.exit(0);
  }, DURATION_SEC * 1000);
}
