---
layout: default
title: Mucker — CDP-compatible OkHttp Mock Engine
description: Zero-cert OkHttp network mocking and Chrome DevTools Protocol inspection engine for Android.
---

<div class="hero">
  <div class="hero-glow"></div>
  <div class="hero-tag">
    <span>⚡ Zero CA Certs Required</span>
    <span>•</span>
    <span>Chrome DevTools Protocol (CDP)</span>
  </div>

  <h1>
    Muck with your network.<br>
    <span class="gradient-text">Zero certs required.</span>
  </h1>

  <p class="hero-subtitle">
    The missing mocking sidekick for OkHttp. Intercept, pause, and inject mock API responses on Android in real time — via phone WebView, desktop browser, CLI, or automated CDP scripts.
  </p>

  <div class="hero-cta">
    <a href="{{ '/getting-started' | relative_url }}" class="btn btn-primary">
      <span>Get Started</span>
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
    </a>
    <a href="{{ '/cdp-history' | relative_url }}" class="btn btn-secondary">
      <span>CDP Evolution & History</span>
    </a>
    <a href="https://github.com/yongjhih/mucker" class="btn btn-github" target="_blank" rel="noopener">
      <span>View on GitHub</span>
    </a>
  </div>

  <!-- Hero Visual / Terminal Mockup -->
  <div class="hero-visual">
    <div class="terminal-header">
      <span class="term-dot red"></span>
      <span class="term-dot yellow"></span>
      <span class="term-dot green"></span>
      <span class="terminal-title">bash — mucker cli & cdp inspector</span>
    </div>
    <div class="terminal-body">
      <div><span class="prompt">$</span> <span class="cmd">mucker forward</span></div>
      <div class="res-green">✔ Detected 1 device: Pixel_8_Pro</div>
      <div class="res-green">✔ Successfully forwarded tcp:8080 -> tcp:8080</div>
      <div class="res-dim">Open Dashboard: http://localhost:8080</div>
      <br>
      <div><span class="prompt">$</span> <span class="cmd">mucker rules add "/api/v1/user/profile" --status 200 --body '{"name":"Alex Mercer","role":"Lead"}'</span></div>
      <div class="res-green">✔ Added mock rule [rule_user]: GET /api/v1/user/profile -> 200 OK</div>
      <br>
      <div><span class="prompt">$</span> <span class="cmd">mucker listen</span></div>
      <div class="res-dim">Connecting to Mucker live event stream at ws://localhost:8080/devtools/page...</div>
      <div><span class="res-purple">[MOCKED]</span> <span class="res-green">200</span> https://dummyjson.com/api/v1/user/profile <span class="res-dim">(2ms)</span></div>
      <div><span class="res-yellow">[PAUSED]</span> POST https://dummyjson.com/api/v1/checkout <span class="res-dim">(Waiting for dev action...)</span></div>
    </div>
  </div>
</div>

<!-- Features Section -->
<section class="features-section">
  <div class="section-header">
    <h2>Why Mucker?</h2>
    <p>Solving the fundamental pain points of mobile network inspection and mocking.</p>
  </div>

  <div class="feature-grid">
    <div class="feature-card">
      <div class="feature-icon">
        <svg width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
      </div>
      <h3>Zero CA Certificates</h3>
      <p>No more struggling with Android 7.0+ user certificate restrictions or modifying <code>network_security_config.xml</code>. Intercepts directly in the OkHttp application layer.</p>
    </div>

    <div class="feature-card">
      <div class="feature-icon">
        <svg width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg>
      </div>
      <h3>Native Chrome DevTools (CDP)</h3>
      <p>Full support for the modern CDP <strong>Fetch Domain</strong> (<code>Fetch.enable</code>, <code>Fetch.requestPaused</code>, <code>Fetch.fulfillRequest</code>). Connect via Puppeteer, Playwright, or browser.</p>
    </div>

    <div class="feature-card">
      <div class="feature-icon">
        <svg width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M4 6h16M4 12h16m-7 6h7"/></svg>
      </div>
      <h3>In-App + Desktop Web Dashboard</h3>
      <p>Click the system notification on your phone to open the in-app WebView, or open <code>http://&lt;phone-ip&gt;:8080</code> on your laptop. Same impeccable real-time SPA dashboard.</p>
    </div>

    <div class="feature-card">
      <div class="feature-icon">
        <svg width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M13 10V3L4 14h7v7l9-11h-7z"/></svg>
      </div>
      <h3>Zero Native C++ Drag</h3>
      <p>Unlike Flipper which dragged in OpenSSL, Boost, and Folly C++ libraries that broke builds, Mucker is 100% lightweight Kotlin with zero build overhead and safe no-op stubs.</p>
    </div>

    <div class="feature-card">
      <div class="feature-icon">
        <svg width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
      </div>
      <h3>Live Breakpoint Interception</h3>
      <p>Pause requests in mid-air! Inspect request payload, tweak response body, modify status codes, and click <em>Fulfill</em> or <em>Pass Through</em> with automatic safe timeout protection.</p>
    </div>

    <div class="feature-card">
      <div class="feature-icon">
        <svg width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M8 9l3 3-3 3m5 0h3M5 20h14a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/></svg>
      </div>
      <h3>Mucker CLI & AI Skills</h3>
      <p>Drive mocking directly from terminal or AI coding agents. One command to forward ports, add latency rules, stream live traffic, and mock payment failures.</p>
    </div>
  </div>
</section>
