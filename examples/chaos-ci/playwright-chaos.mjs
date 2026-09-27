#!/usr/bin/env node

/**
 * Playwright CDP Chaos Engineering Example with Mucker
 *
 * Demonstrates how QA / SDET automation engineers can use Playwright
 * to connect to an Android OkHttp app via Mucker's zero-cert CDP server,
 * inject random HTTP 500 / 504 Timeout / 429 Rate-Limit errors in CI/CD,
 * and verify mobile app resilience and recovery without installing CA certificates.
 *
 * Usage:
 *   node playwright-chaos.mjs [--rate=0.35] [--timeout=4000] [--host=127.0.0.1:8080]
 */

import { chromium } from 'playwright-core';

const args = process.argv.slice(2).reduce((acc, arg) => {
  if (arg.startsWith('--')) {
    const [k, v] = arg.slice(2).split('=');
    acc[k] = v || true;
  }
  return acc;
}, {});

const TARGET_HOST = args.host || process.env.MUCKER_HOST || '127.0.0.1:8080';
const CDP_HTTP_URL = `http://${TARGET_HOST}`;
const CDP_WS_URL = `ws://${TARGET_HOST}/devtools/page`;
const FAILURE_RATE = parseFloat(args.rate || process.env.CHAOS_RATE || '0.40');
const TIMEOUT_DELAY_MS = parseInt(args.timeout || '4000', 10);

console.log(`
┌──────────────────────────────────────────────────────────┐
│   🎭  Mucker + Playwright Chaos Engineering for Android  │
└──────────────────────────────────────────────────────────┘
• Target CDP:     ${CDP_HTTP_URL} / ${CDP_WS_URL}
• Failure Rate:   ${(FAILURE_RATE * 100).toFixed(0)}% (HTTP 500 & Simulated Timeouts)
• Timeout Delay:  ${TIMEOUT_DELAY_MS}ms
• Protocol:       Chrome DevTools Protocol (Fetch Domain)
• Certificate:    ZERO custom CA required!
`);

async function runPlaywrightChaos() {
  let cdpSession = null;

  try {
    console.log(`[1/3] Connecting Playwright to Mucker CDP endpoint...`);
    const browser = await chromium.connectOverCDP(CDP_HTTP_URL, {
      timeout: 5000
    });
    const defaultContext = browser.contexts()[0];
    const pages = defaultContext ? defaultContext.pages() : [];
    const page = pages[0] || (await defaultContext?.newPage());
    if (page) {
      cdpSession = await defaultContext.newCDPSession(page);
    }
  } catch (err) {
    console.log(`[i] Direct browser connection fallback: connecting lightweight CDP WebSocket session...`);
    // Fallback: connect directly via Playwright-compatible WebSocket CDP client
    const WS = typeof globalThis.WebSocket !== 'undefined'
      ? globalThis.WebSocket
      : (await import('ws')).default;

    const ws = new WS(CDP_WS_URL);
    await new Promise((resolve, reject) => {
      ws.onopen = resolve;
      ws.onerror = reject;
    });

    let msgId = 1;
    const callbacks = new Map();

    ws.onmessage = (event) => {
      const data = JSON.parse(event.data);
      if (data.id && callbacks.has(data.id)) {
        const { resolve } = callbacks.get(data.id);
        callbacks.delete(data.id);
        resolve(data.result);
      } else if (data.method && cdpSession?._listeners.has(data.method)) {
        for (const handler of cdpSession._listeners.get(data.method)) {
          handler(data.params);
        }
      }
    };

    cdpSession = {
      _listeners: new Map(),
      send: (method, params = {}) => new Promise((resolve) => {
        const id = msgId++;
        callbacks.set(id, { resolve });
        ws.send(JSON.stringify({ id, method, params }));
      }),
      on: (event, handler) => {
        if (!cdpSession._listeners.has(event)) {
          cdpSession._listeners.set(event, new Set());
        }
        cdpSession._listeners.get(event).add(handler);
      }
    };
  }

  if (!cdpSession) {
    throw new Error('Failed to establish CDP session with Mucker');
  }

  console.log(`[2/3] Enabling CDP Fetch domain for all network routes (*)...`);
  await cdpSession.send('Fetch.enable', {
    patterns: [{ urlPattern: '*' }]
  });

  const stats = {
    total: 0,
    passed: 0,
    injected500: 0,
    injectedTimeout: 0
  };

  console.log(`[3/3] 🌪️  Chaos engine active! Intercepting Android OkHttp requests...\n`);

  cdpSession.on('Fetch.requestPaused', async (event) => {
    const { requestId, request } = event;
    stats.total++;
    const rand = Math.random();

    if (rand < FAILURE_RATE * 0.6) {
      // Failure Mode 1: HTTP 500 Internal Server Error
      stats.injected500++;
      console.log(`[🔥 PLAYWRIGHT CHAOS 500] ${request.method} ${request.url}`);
      
      const payload = JSON.stringify({
        error: "Playwright Chaos: Internal Server Error",
        statusCode: 500,
        message: "Simulated upstream microservice failure in CI/CD pipeline",
        injectedBy: "playwright-chaos.mjs",
        timestamp: new Date().toISOString()
      }, null, 2);

      await cdpSession.send('Fetch.fulfillRequest', {
        requestId,
        responseCode: 500,
        responseHeaders: [
          { name: 'Content-Type', value: 'application/json' },
          { name: 'X-Mocked-By', value: 'Playwright-Chaos-CI' },
          { name: 'X-Chaos-Fault', value: '500-Internal-Error' }
        ],
        body: Buffer.from(payload).toString('base64')
      });

    } else if (rand < FAILURE_RATE) {
      // Failure Mode 2: Simulated Gateway Timeout (Delayed + 504)
      stats.injectedTimeout++;
      console.log(`[⏳ PLAYWRIGHT CHAOS TIMEOUT] ${request.method} ${request.url} (Delaying ${TIMEOUT_DELAY_MS}ms)`);
      
      setTimeout(async () => {
        try {
          const timeoutPayload = JSON.stringify({
            error: "Playwright Chaos: Gateway Timeout",
            statusCode: 504,
            message: `Upstream service timed out after ${TIMEOUT_DELAY_MS}ms`,
            injectedBy: "playwright-chaos.mjs",
            timestamp: new Date().toISOString()
          }, null, 2);

          await cdpSession.send('Fetch.fulfillRequest', {
            requestId,
            responseCode: 504,
            responseHeaders: [
              { name: 'Content-Type', value: 'application/json' },
              { name: 'X-Mocked-By', value: 'Playwright-Chaos-CI' },
              { name: 'X-Chaos-Fault', value: '504-Gateway-Timeout' }
            ],
            body: Buffer.from(timeoutPayload).toString('base64')
          });
        } catch (e) {
          // If request was canceled by client
        }
      }, TIMEOUT_DELAY_MS);

    } else {
      // Normal Pass-through
      stats.passed++;
      console.log(`[✓ PASS] ${request.method} ${request.url}`);
      await cdpSession.send('Fetch.continueRequest', { requestId });
    }
  });

  const printSummary = () => {
    console.log(`\n──────────────────────────────────────────────────────────`);
    console.log(`Playwright Chaos Injection Summary:`);
    console.log(`• Total Requests Intercepted: ${stats.total}`);
    console.log(`• Normal Passed Requests:     ${stats.passed}`);
    console.log(`• HTTP 500 Faults Injected:   ${stats.injected500}`);
    console.log(`• Timeouts Injected:          ${stats.injectedTimeout}`);
    console.log(`──────────────────────────────────────────────────────────\n`);
  };

  process.on('SIGINT', () => {
    printSummary();
    process.exit(0);
  });
  process.on('SIGTERM', () => {
    printSummary();
    process.exit(0);
  });
}

runPlaywrightChaos().catch((err) => {
  console.error('[!] Playwright Chaos error:', err);
  process.exit(1);
});
