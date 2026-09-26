#!/usr/bin/env node

import { c } from '../lib/colors.js';
import { findAdbPath, listDevices, forwardPort } from '../lib/adb.js';
import { MuckerClient } from '../lib/api.js';
import { listenLive } from '../lib/cdp.js';
import { exec } from 'node:child_process';

const VERSION = '1.0.0';

function printBanner() {
  console.log(`
${c.cyan(c.bold('   __  _____  ______  ______  __ ___________ '))}
${c.cyan(c.bold('  /  |/  / / / / __ \\/ ____/ / //_/ ____/ __ \\'))}
${c.cyan(c.bold(' / /|_/ / / / / / / / /_    / ,< / __/ / /_/ /'))}
${c.cyan(c.bold('/ /  / / /_/ / /_/ / __/   / /| / /___/ _, _/ '))}
${c.cyan(c.bold('/_/  /_/\\____/\\____/_/     /_/ |_/_____/_/ |_|  '))} ${c.dim(`v${VERSION}`)}
  ${c.dim('Muck with your network, zero certs required. CDP-compatible Mock Engine.')}
`);
}

function printHelp() {
  printBanner();
  console.log(`
${c.bold('USAGE:')}
  ${c.green('mucker')} <command> [options]

${c.bold('COMMANDS:')}
  ${c.yellow('forward')} [port]               Forward adb port to connected device (default: 8080)
  ${c.yellow('status')}                     Check Mucker status & connected app
  ${c.yellow('open')}                       Open the Mucker Web Dashboard in your browser
  ${c.yellow('listen')}                     Stream intercepted requests live to console
  ${c.yellow('rules list')}                 List active mock rules
  ${c.yellow('rules add')} <pattern> [opts] Add a mock rule
  ${c.yellow('rules remove')} <ruleId>      Remove a mock rule by ID
  ${c.yellow('rules clear')}                Remove all mock rules
  ${c.yellow('fulfill')} <reqId> [opts]     Fulfill a paused request with mock response
  ${c.yellow('continue')} <reqId>           Continue a paused request without mocking
  ${c.yellow('cdp')}                        Show CDP endpoints for Puppeteer/Playwright
  ${c.yellow('devices')}                    List connected Android devices

${c.bold('OPTIONS:')}
  ${c.cyan('--host')} <url>                 Mucker server URL (default: http://localhost:8080)
  ${c.cyan('--status')} <code>              HTTP status code (default: 200)
  ${c.cyan('--delay')} <ms>                 Artificial delay in milliseconds (default: 0)
  ${c.cyan('--method')} <GET|POST|ALL>     HTTP method to match (default: ALL)
  ${c.cyan('--body')} <string|json>         Mock response body string/JSON
  ${c.cyan('--header')} <Key:Value>         Custom response header (repeatable)
  ${c.cyan('-h, --help')}                   Show this help menu
  ${c.cyan('-v, --version')}                Show version

${c.bold('EXAMPLES:')}
  $ mucker forward
  $ mucker status
  $ mucker rules add "/api/v1/user" --status 200 --body '{"id":1,"name":"Alice"}'
  $ mucker rules add "/api/v1/checkout" --status 500 --delay 1000 --body '{"error":"Failed"}'
  $ mucker listen
`);
}

function parseArgs(args) {
  const parsed = {
    command: args[0],
    subcommand: args[1],
    positionals: [],
    flags: {},
  };

  let i = 0;
  while (i < args.length) {
    const arg = args[i];
    if (arg.startsWith('--')) {
      const key = arg.slice(2);
      const next = args[i + 1];
      if (next && !next.startsWith('-')) {
        if (!parsed.flags[key]) {
          parsed.flags[key] = next;
        } else if (Array.isArray(parsed.flags[key])) {
          parsed.flags[key].push(next);
        } else {
          parsed.flags[key] = [parsed.flags[key], next];
        }
        i += 2;
        continue;
      } else {
        parsed.flags[key] = true;
        i++;
        continue;
      }
    } else if (arg.startsWith('-')) {
      const key = arg.slice(1);
      parsed.flags[key] = true;
      i++;
      continue;
    } else {
      parsed.positionals.push(arg);
      i++;
    }
  }

  return parsed;
}

function openBrowser(url) {
  const start = process.platform === 'darwin' ? 'open' : (process.platform === 'win32' ? 'start' : 'xdg-open');
  exec(`${start} "${url}"`, (err) => {
    if (err) {
      console.log(`${c.dim('Could not open browser automatically. Please visit:')} ${c.underline(url)}`);
    }
  });
}

async function main() {
  const args = process.argv.slice(2);
  const parsed = parseArgs(args);
  const host = parsed.flags.host || 'http://localhost:8080';
  const client = new MuckerClient(host);

  if (parsed.flags.v || parsed.flags.version || parsed.command === 'version') {
    console.log(`v${VERSION}`);
    return;
  }

  if (!parsed.command || parsed.flags.h || parsed.flags.help || parsed.command === 'help') {
    printHelp();
    return;
  }

  switch (parsed.command) {
    case 'forward': {
      const port = Number(parsed.positionals[1] || parsed.flags.port || 8080);
      const adb = findAdbPath();
      console.log(`${c.dim('Using ADB:')} ${c.cyan(adb)}`);
      const devices = listDevices(adb);
      if (devices.length === 0) {
        console.log(c.yellow('⚠ No active Android devices/emulators detected via ADB.'));
        console.log(c.dim('Attempting port forwarding anyway...'));
      } else {
        console.log(c.green(`✔ Detected ${devices.length} device(s):`));
        devices.forEach(d => console.log(`  • ${c.bold(d.id)} (${d.model})`));
      }

      const res = forwardPort(port, adb);
      if (res.success) {
        console.log(c.green(c.bold(`✔ Successfully forwarded tcp:${port} -> tcp:${port}`)));
        console.log(`\nOpen Dashboard in browser: ${c.cyan(c.underline(`http://localhost:${port}`))}\n`);
      } else {
        console.error(c.red(`✖ Failed to forward port: ${res.error}`));
        process.exit(1);
      }
      break;
    }

    case 'devices': {
      const adb = findAdbPath();
      const devices = listDevices(adb);
      if (devices.length === 0) {
        console.log(c.yellow('No active Android devices or emulators found.'));
      } else {
        console.log(c.green(`Connected Android devices (${devices.length}):`));
        devices.forEach(d => console.log(`  ${c.bold(d.id)} \t${c.cyan(d.model)}`));
      }
      break;
    }

    case 'status': {
      try {
        const status = await client.getStatus();
        console.log(c.green(c.bold('\n✔ Mucker Engine Connected!')));
        console.log(`  ${c.bold('Status:')}             ${c.green(status.status || 'running')}`);
        console.log(`  ${c.bold('App Name:')}           ${c.cyan(status.app || 'Android App')}`);
        console.log(`  ${c.bold('Server Port:')}        ${c.yellow(status.port || 8080)}`);
        console.log(`  ${c.bold('Active Rules:')}       ${status.activeRulesCount ?? 0}`);
        console.log(`  ${c.bold('Paused Requests:')}    ${status.pausedRequestsCount ?? 0}`);
        console.log(`  ${c.bold('Web Dashboard:')}      ${c.underline(`http://localhost:${status.port || 8080}`)}`);
        console.log(`  ${c.bold('CDP WebSocket:')}      ${c.dim(`ws://localhost:${status.port || 8080}/devtools/page`)}\n`);
      } catch (err) {
        console.error(c.red(`\n✖ Could not connect to Mucker at ${host}`));
        console.error(c.dim(`Make sure your app is running in debug mode and port forward is active:\n  $ mucker forward\n`));
        process.exit(1);
      }
      break;
    }

    case 'open': {
      const targetUrl = host;
      console.log(`Opening Mucker Web Dashboard at ${c.cyan(targetUrl)}...`);
      openBrowser(targetUrl);
      break;
    }

    case 'rules': {
      const sub = parsed.positionals[1] || 'list';
      if (sub === 'list') {
        try {
          const rules = await client.getRules();
          if (!rules || rules.length === 0) {
            console.log(c.yellow('\nNo active mock rules found. Add one with:'));
            console.log(c.dim('  $ mucker rules add "/api/v1/..." --status 200 --body \'{"hello":"world"}\'\n'));
            return;
          }
          console.log(c.bold(`\nActive Mock Rules (${rules.length}):`));
          console.log(c.dim('─'.repeat(72)));
          rules.forEach((r, idx) => {
            const statusColor = r.statusCode < 300 ? c.green : (r.statusCode < 400 ? c.cyan : c.red);
            const state = r.isEnabled ? c.green('● ENABLED') : c.gray('○ DISABLED');
            console.log(`${idx + 1}. [${r.id}] ${state}  ${c.bold(r.method || 'ALL')} ${c.cyan(r.urlPattern)}`);
            console.log(`   Returns: ${statusColor(String(r.statusCode))} | Delay: ${r.delayMs || 0}ms`);
            if (r.responseBody) {
              const preview = r.responseBody.length > 60 ? r.responseBody.slice(0, 57) + '...' : r.responseBody;
              console.log(`   Body: ${c.dim(preview)}`);
            }
          });
          console.log(c.dim('─'.repeat(72)) + '\n');
        } catch (err) {
          console.error(c.red(`Failed to fetch rules: ${err.message}`));
          process.exit(1);
        }
      } else if (sub === 'add') {
        const pattern = parsed.positionals[2];
        if (!pattern) {
          console.error(c.red('Error: Missing pattern. Usage: mucker rules add <pattern> [--status 200] [--body "..."]'));
          process.exit(1);
        }
        const rule = {
          urlPattern: pattern,
          method: (parsed.flags.method || 'ALL').toUpperCase(),
          statusCode: Number(parsed.flags.status || 200),
          delayMs: Number(parsed.flags.delay || 0),
          responseBody: parsed.flags.body || '{"message":"mocked by mucker"}',
          responseHeaders: { 'Content-Type': 'application/json', 'X-Mocked-By': 'Mucker-CLI' },
          isEnabled: true,
        };
        try {
          const res = await client.addRule(rule);
          console.log(c.green(`✔ Added mock rule [${res.id}]: ${rule.method} ${rule.urlPattern} -> ${rule.statusCode}`));
        } catch (err) {
          console.error(c.red(`Failed to add rule: ${err.message}`));
          process.exit(1);
        }
      } else if (sub === 'remove' || sub === 'rm') {
        const ruleId = parsed.positionals[2];
        if (!ruleId) {
          console.error(c.red('Error: Missing rule ID. Usage: mucker rules remove <ruleId>'));
          process.exit(1);
        }
        try {
          await client.removeRule(ruleId);
          console.log(c.green(`✔ Removed rule [${ruleId}]`));
        } catch (err) {
          console.error(c.red(`Failed to remove rule: ${err.message}`));
          process.exit(1);
        }
      } else if (sub === 'clear') {
        try {
          await client.clearRules();
          console.log(c.green('✔ Cleared all mock rules.'));
        } catch (err) {
          console.error(c.red(`Failed to clear rules: ${err.message}`));
          process.exit(1);
        }
      }
      break;
    }

    case 'listen': {
      const wsUrl = host.replace(/^http/, 'ws') + '/devtools/page';
      console.log(c.cyan(`\nConnecting to Mucker live event stream at ${wsUrl}...`));
      console.log(c.dim('Press Ctrl+C to stop listening.\n'));

      listenLive(wsUrl, (msg, ws) => {
        if (msg.method === 'Fetch.requestPaused') {
          const { requestId, request } = msg.params;
          console.log(`${c.yellow(c.bold('[PAUSED]'))} ${c.bold(request.method)} ${c.cyan(request.url)}  ${c.dim(`(ID: ${requestId})`)}`);
        } else if (msg.method === 'Network.requestWillBeSent') {
          const { request } = msg.params;
          console.log(`${c.blue('[OUT]')}    ${c.bold(request.method)} ${request.url}`);
        } else if (msg.method === 'Network.responseReceived') {
          const { response } = msg.params;
          const status = response.status;
          const isMocked = response.headers?.['x-mocked-by'] || response.headers?.['X-Mocked-By'];
          const tag = isMocked ? c.magenta(c.bold('[MOCKED]')) : c.green('[REAL]');
          const statusColor = status < 300 ? c.green : (status < 400 ? c.cyan : c.red);
          console.log(`${tag}   ${statusColor(String(status))} ${response.url}  ${c.dim(`(${response.responseTime || 0}ms)`)}`);
        }
      }, (err) => {
        console.error(c.red(`WebSocket error: ${err.message}`));
      });
      break;
    }

    case 'fulfill': {
      const reqId = parsed.positionals[1];
      if (!reqId) {
        console.error(c.red('Error: Missing requestId. Usage: mucker fulfill <requestId> [--status 200] [--body "..."]'));
        process.exit(1);
      }
      try {
        await client.fulfillPaused(reqId, {
          statusCode: Number(parsed.flags.status || 200),
          responseBody: parsed.flags.body || '{"status":"ok"}',
          responseHeaders: { 'Content-Type': 'application/json' },
        });
        console.log(c.green(`✔ Fulfill request [${reqId}] with status ${parsed.flags.status || 200}`));
      } catch (err) {
        console.error(c.red(`Failed to fulfill request: ${err.message}`));
      }
      break;
    }

    case 'continue': {
      const reqId = parsed.positionals[1];
      if (!reqId) {
        console.error(c.red('Error: Missing requestId. Usage: mucker continue <requestId>'));
        process.exit(1);
      }
      try {
        await client.continuePaused(reqId);
        console.log(c.green(`✔ Resumed request [${reqId}] to real network.`));
      } catch (err) {
        console.error(c.red(`Failed to resume request: ${err.message}`));
      }
      break;
    }

    case 'cdp': {
      const wsUrl = host.replace(/^http/, 'ws') + '/devtools/page';
      console.log(c.bold('\nChrome DevTools Protocol (CDP) Endpoints:'));
      console.log(`  Discovery Version:  ${c.cyan(`${host}/json/version`)}`);
      console.log(`  Target List:        ${c.cyan(`${host}/json/list`)}`);
      console.log(`  WebSocket Endpoint: ${c.green(wsUrl)}`);
      console.log(`
${c.bold('Integration Sample (Puppeteer / Playwright / Node.js):')}
${c.dim(`import WebSocket from 'ws';
const ws = new WebSocket('${wsUrl}');
ws.on('open', () => {
  ws.send(JSON.stringify({ id: 1, method: 'Fetch.enable', params: { patterns: [{ urlPattern: '*' }] } }));
});
ws.on('message', (data) => {
  const event = JSON.parse(data.toString());
  if (event.method === 'Fetch.requestPaused') {
    // Fulfill or continue
  }
});`)}
`);
      break;
    }

    default:
      console.error(c.red(`Unknown command: ${parsed.command}`));
      printHelp();
      process.exit(1);
  }
}

main().catch(err => {
  console.error(c.red(`\nUnexpected error: ${err.message}`));
  process.exit(1);
});
