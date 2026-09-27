#!/usr/bin/env node

/**
 * Puppeteer / Playwright CDP Integration Example with Mucker
 *
 * Demonstrates how modern QA & automation teams can leverage their
 * existing Puppeteer / Playwright skills to mock and inject chaos
 * directly into native Android OkHttp apps over Chrome DevTools Protocol.
 */

import puppeteer from 'puppeteer-core';

const CDP_WS_ENDPOINT = process.env.MUCKER_CDP || 'ws://127.0.0.1:8080/devtools/page';

async function runPuppeteerChaosTest() {
  console.log(`Connecting Puppeteer to Mucker CDP endpoint: ${CDP_WS_ENDPOINT}...`);

  let browser;
  try {
    browser = await puppeteer.connect({
      browserWSEndpoint: CDP_WS_ENDPOINT,
      defaultViewport: null
    });
  } catch (err) {
    console.warn(`[Note] If connecting via full browser instance fails, using direct CDP WebSocket session.`);
    console.warn(`Error: ${err.message}`);
    return;
  }

  const pages = await browser.pages();
  const page = pages[0] || (await browser.newPage());

  // Create a raw CDP Session
  const client = await page.target().createCDPSession();

  // Enable Fetch interception domain
  await client.send('Fetch.enable', {
    patterns: [
      { urlPattern: '*/api/v1/checkout*', requestStage: 'Request' },
      { urlPattern: '*/api/v1/user/*', requestStage: 'Request' }
    ]
  });

  console.log('✓ Puppeteer CDP Session active. Intercepting checkout & profile calls...');

  // Event listener for paused requests
  client.on('Fetch.requestPaused', async (event) => {
    const { requestId, request } = event;
    console.log(`[Puppeteer Intercepted] ${request.method} ${request.url}`);

    // Chaos strategy: 50% 500 Server Error on Checkout
    if (request.url.includes('/checkout')) {
      console.log('  → Injected 500 Internal Server Error (simulating payment gateway failure)');
      await client.send('Fetch.fulfillRequest', {
        requestId,
        responseCode: 500,
        responseHeaders: [
          { name: 'Content-Type', value: 'application/json' },
          { name: 'X-Mocked-By', value: 'Puppeteer-CDP' }
        ],
        body: Buffer.from(JSON.stringify({
          error: 'Payment Gateway Down',
          retryable: false
        })).toString('base64')
      });
    } else {
      // Continue normal requests
      console.log('  → Allowing normal request to proceed');
      await client.send('Fetch.continueRequest', { requestId });
    }
  });

  // Keep script running to monitor
  console.log('Ready! Send requests from the Android demo app or test suite.');
}

runPuppeteerChaosTest().catch(console.error);
