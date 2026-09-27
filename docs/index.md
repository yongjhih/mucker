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
    <span>One-Line Instant Demo</span>
    <span>•</span>
    <span>Cloud DevContainer Ready</span>
  </div>

  <h1>
    Muck with your network.<br>
    <span class="gradient-text">Zero certs required.</span>
  </h1>

  <p class="hero-subtitle">
    The missing mocking sidekick for OkHttp. Intercept, pause, and inject mock API responses on Android in real time — via phone WebView, desktop browser, CLI, or automated CDP scripts.
  </p>

  <div class="hero-cta">
    <a href="#quickstart" class="btn btn-primary" style="font-weight: 700;">
      <span>⚡ Instant Demo</span>
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
    </a>
    <a href="{{ '/dashboard/' | relative_url }}" class="btn btn-secondary" style="border-color: #6366f1; color: #818cf8; font-weight: 600;">
      <span>⚡ Live Dashboard</span>
    </a>
    <a href="https://codespaces.new/yongjhih/mucker" class="btn btn-secondary" target="_blank" rel="noopener" style="display:inline-flex;align-items:center;gap:6px;" title="Open in GitHub Codespaces with zero local setup">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12c0 4.42 2.87 8.17 6.84 9.5.5.08.66-.23.66-.5v-1.69c-2.77.6-3.36-1.34-3.36-1.34-.46-1.16-1.11-1.47-1.11-1.47-.91-.62.07-.6.07-.6 1 .07 1.53 1.03 1.53 1.03.87 1.52 2.34 1.07 2.91.83.1-.65.35-1.09.63-1.34-2.22-.25-4.55-1.11-4.55-4.92 0-1.11.38-2 1.03-2.71-.1-.25-.45-1.29.1-2.64 0 0 .84-.27 2.75 1.02.79-.22 1.65-.33 2.5-.33.85 0 1.71.11 2.5.33 1.91-1.29 2.75-1.02 2.75-1.02.55 1.35.2 2.39.1 2.64.65.71 1.03 1.6 1.03 2.71 0 3.82-2.34 4.66-4.57 4.91.36.31.69.92.69 1.85V21c0 .27.16.59.67.5C19.14 20.16 22 16.42 22 12A10 10 0 0 0 12 2z"/></svg>
      <span>Cloud DevContainer</span>
    </a>
    <a href="{{ '/getting-started' | relative_url }}" class="btn btn-secondary">
      <span>Quickstart Docs</span>
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
      <span class="terminal-title">bash — one-command automated demo &amp; dashboard</span>
    </div>
    <div class="terminal-body">
      <div><span class="prompt">$</span> <span class="cmd">git clone https://github.com/yongjhih/mucker.git &amp;&amp; cd mucker &amp;&amp; npm run demo</span></div>
      <br>
      <div class="res-dim">[1/5] Checking connected Android devices...</div>
      <div class="res-green">✔ Found active Android device/emulator: Pixel_8_Pro (API 34)</div>
      <div class="res-dim">[2/5] Compiling and installing Mucker Demo App (:app:installDebug)...</div>
      <div class="res-green">✔ BUILD SUCCESSFUL in 2.6s (APK installed on device)</div>
      <div class="res-dim">[3/5] Forwarding ADB port 8080 (tcp:8080 -> tcp:8080)...</div>
      <div class="res-green">✔ Port forward active: localhost:8080 -> device:8080</div>
      <div class="res-dim">[4/5] Launching Demo Application on device (io.github.mucker.demo)...</div>
      <div class="res-dim">[5/5] Launching Mucker Web Dashboard in your browser...</div>
      <div class="res-green" style="font-weight:700;">✔ Mucker is now live at http://localhost:8080!</div>
      <div class="res-dim">Press endpoint buttons in the Android app to watch requests stream live onto the timeline!</div>
    </div>
  </div>
</div>

<!-- Quickstart Instant Experience Section -->
<section id="quickstart" class="quickstart-section">
  <div class="section-header">
    <h2>🚀 60-Second Instant Experience</h2>
    <p>Zero configuration, zero certificate installation. Run the demo locally in one command, or launch entirely in your browser with GitHub Codespaces.</p>
  </div>

  <div class="quickstart-grid">
    <!-- Card 1: 1-Line Local Runner -->
    <div class="quickstart-card featured">
      <div class="quickstart-card-header">
        <span class="quickstart-badge">⚡ Instant One-Liner</span>
        <span style="font-size:0.75rem;color:var(--text-dim);font-family:var(--font-mono);">macOS / Linux</span>
      </div>
      <h3>One-Line Automated Demo</h3>
      <p>No manual ADB configuration or Gradle wrangling required. One single command boots the emulator, installs the demo APK, forwards port 8080, and opens the Web Dashboard in your browser.</p>
      
      <div class="quickstart-code-box">
        <code>npm run demo</code>
        <button class="btn btn-sm" onclick="navigator.clipboard.writeText('npm run demo')" title="Copy command">Copy</button>
      </div>

      <ul class="quickstart-features-list">
        <li><span class="bullet">✓</span> Auto-detects connected phone or starts local AVD emulator</li>
        <li><span class="bullet">✓</span> Builds &amp; installs <code>:app:installDebug</code> in seconds</li>
        <li><span class="bullet">✓</span> Auto-wires <code>adb forward tcp:8080 tcp:8080</code></li>
        <li><span class="bullet">✓</span> Automatically opens <code>http://localhost:8080</code> in your browser</li>
      </ul>
    </div>

    <!-- Card 2: Cloud DevContainer -->
    <div class="quickstart-card">
      <div class="quickstart-card-header">
        <span class="quickstart-badge purple">☁️ Zero-Install Cloud</span>
        <span style="font-size:0.75rem;color:var(--text-dim);font-family:var(--font-mono);">1-Click Browser</span>
      </div>
      <h3>GitHub Codespaces / Dev Container</h3>
      <p>Don't have Android Studio, JDK 17, or Android SDK installed on your machine? Run everything inside a pre-built cloud container with zero local dependencies.</p>
      
      <div style="margin: 4px 0 8px;">
        <a href="https://codespaces.new/yongjhih/mucker" target="_blank" rel="noopener">
          <img src="https://github.com/codespaces/badge.svg" alt="Open in GitHub Codespaces" style="height: 32px;">
        </a>
      </div>

      <ul class="quickstart-features-list">
        <li><span class="bullet">✓</span> Pre-configured with Ubuntu 24.04, OpenJDK 17, Android SDK 34, Node 22</li>
        <li><span class="bullet">✓</span> Auto-runs <code>./gradlew :app:assembleDebug</code> on container creation</li>
        <li><span class="bullet">✓</span> Automatically forwards port 8080 for web dashboard preview</li>
        <li><span class="bullet">✓</span> Also supports VS Code <em>Remote - Containers</em> locally</li>
      </ul>
    </div>

    <!-- Card 3: Playwright & Puppeteer Chaos CI -->
    <div class="quickstart-card">
      <div class="quickstart-card-header">
        <span class="quickstart-badge green">🎭 QA &amp; CI/CD Chaos</span>
        <span style="font-size:0.75rem;color:var(--text-dim);font-family:var(--font-mono);">Automated Testing</span>
      </div>
      <h3>Playwright / Puppeteer Chaos</h3>
      <p>Randomly inject HTTP 500 errors and 504 timeouts into native Android OkHttp in CI/CD using standard Node.js scripts without root or CA certificates.</p>
      
      <div class="quickstart-code-box">
        <code>npm run chaos:playwright</code>
        <button class="btn btn-sm" onclick="navigator.clipboard.writeText('npm run chaos:playwright')" title="Copy command">Copy</button>
      </div>

      <ul class="quickstart-features-list">
        <li><span class="bullet">✓</span> Connects over CDP (<code>chromium.connectOverCDP</code>)</li>
        <li><span class="bullet">✓</span> Random fault injection: HTTP 500, 504 Timeout, 429 Rate Limit</li>
        <li><span class="bullet">✓</span> Verifies app recovery, retry buttons, and error UI in CI/CD</li>
        <li><span class="bullet">✓</span> Zero certificates or proxy servers to maintain</li>
      </ul>
    </div>

    <!-- Card 4: Zero-Friction Live Response Mocking -->
    <div class="quickstart-card">
      <div class="quickstart-card-header">
        <span class="quickstart-badge">✏️ Direct In-Place Editing</span>
        <span style="font-size:0.75rem;color:var(--text-dim);font-family:var(--font-mono);">Impeccable UX</span>
      </div>
      <h3>Zero-Friction Live Mocking</h3>
      <p>No extra "Enter Edit Mode" button barrier. Select any network request from the list and immediately edit its JSON response payload in-place.</p>
      
      <div class="quickstart-code-box">
        <code>⌘S / Ctrl+Enter to Save Mock Rule</code>
      </div>

      <ul class="quickstart-features-list">
        <li><span class="bullet">✓</span> Embedded CodeMirror editor with syntax highlighting &amp; code folding</li>
        <li><span class="bullet">✓</span> Direct click-to-edit with instant <code>● Unsaved</code> state tracking</li>
        <li><span class="bullet">✓</span> Bi-directional hover link: highlights waterfall bar &amp; guide timeline</li>
        <li><span class="bullet">✓</span> One-click JSON formatting, copying, and reverting</li>
      </ul>
    </div>
  </div>

  <!-- Interactive Dashboard Screenshots Showcase -->
  <div class="dashboard-showcase">
    <div class="showcase-header">
      <div class="showcase-title-group">
        <h3>Live Dashboard &amp; Inspector Showcase</h3>
        <p>Watch network requests stream onto the waterfall timeline, inspect headers, and edit mock responses in-place.</p>
      </div>
      <div class="showcase-tabs">
        <button class="showcase-tab-btn active" data-showcase="panel-desktop">Desktop Dashboard</button>
        <button class="showcase-tab-btn" data-showcase="panel-hover">Waterfall Vivid Hover</button>
        <button class="showcase-tab-btn" data-showcase="panel-mobile">Mobile In-App</button>
      </div>
    </div>

    <!-- Panel 1: Desktop Live Inspector -->
    <div class="showcase-panel active" id="panel-desktop">
      <div class="browser-frame">
        <div class="browser-header">
          <span class="term-dot red"></span>
          <span class="term-dot yellow"></span>
          <span class="term-dot green"></span>
          <div class="browser-address">http://localhost:8080 &mdash; Mucker Web Dashboard</div>
        </div>
        <img src="{{ '/assets/images/dashboard-desktop-live.png' | relative_url }}" alt="Mucker Desktop Web Dashboard with Live CodeMirror Mock Editor" loading="lazy">
      </div>
      <div class="showcase-caption">
        <span><strong>Direct In-Place Response Editing:</strong> Click any request and edit JSON immediately in CodeMirror without any "Enter Edit Mode" button barrier. Press <code>&#8984;S</code> / <code>Ctrl+Enter</code> to activate the mock rule live.</span>
        <a href="{{ '/dashboard/' | relative_url }}" class="btn btn-sm" style="color:var(--accent);">Open Live Demo &rarr;</a>
      </div>
    </div>

    <!-- Panel 2: Waterfall Vivid Hover Guideline -->
    <div class="showcase-panel" id="panel-hover">
      <div class="browser-frame">
        <div class="browser-header">
          <span class="term-dot red"></span>
          <span class="term-dot yellow"></span>
          <span class="term-dot green"></span>
          <div class="browser-address">http://localhost:8080 &mdash; Waterfall Bi-Directional Hover</div>
        </div>
        <img src="{{ '/assets/images/dashboard-hover-highlight.png' | relative_url }}" alt="Mucker Timeline Waterfall with Vivid Hover Highlight" loading="lazy">
      </div>
      <div class="showcase-caption">
        <span><strong>Bi-Directional Hover Guideline:</strong> Hovering any list item dims background bars, projects a vertical dashed cyan guide, displays a ruler timestamp badge (<code>+1.87s</code>), and renders a floating popover tooltip.</span>
      </div>
    </div>

    <!-- Panel 3: Mobile In-App WebView & Demo -->
    <div class="showcase-panel" id="panel-mobile">
      <div style="display:grid;grid-template-columns:repeat(auto-fit, minmax(280px, 1fr));gap:20px;align-items:start;">
        <div class="browser-frame">
          <div class="browser-header">
            <span class="term-dot green"></span>
            <div class="browser-address">Android Notification &gt; In-App WebView</div>
          </div>
          <img src="{{ '/assets/images/dashboard-inapp-mobile.png' | relative_url }}" alt="Mucker In-App WebView Dashboard on Android" loading="lazy">
        </div>
        <div class="browser-frame">
          <div class="browser-header">
            <span class="term-dot green"></span>
            <div class="browser-address">Mucker Demo App (MainActivity)</div>
          </div>
          <img src="{{ '/assets/images/demo-app-native.png' | relative_url }}" alt="Mucker Native Demo App with Test Buttons" loading="lazy">
        </div>
      </div>
      <div class="showcase-caption">
        <span><strong>Zero Setup on Phone:</strong> Tap the "Mucker Active" system notification to open the full dashboard in an in-app WebView without leaving your app, or test endpoints directly in the demo.</span>
      </div>
    </div>
  </div>
</section>

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

    <div class="feature-card">
      <div class="feature-icon">
        <svg width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"/></svg>
      </div>
      <h3>Modular Dashboard & BYOF</h3>
      <p>Decoupled and open. Plug in custom panels (GraphQL, Chaos) or rewrite the frontend with React, Vue, or Svelte and bundle it into your APK.</p>
    </div>
  </div>
</section>

<!-- Architecture Flow Section with Mermaid -->
<section class="features-section" style="margin-top: 48px;">
  <div class="section-header">
    <h2>End-to-End Workflow</h2>
    <p>Zero-configuration synchronization between OkHttp, the Embedded Micro-Server, and developer clients.</p>
  </div>

  <div class="mermaid">
flowchart TD
    subgraph AndroidApp["Android App Runtime (OkHttp)"]
        direction TB
        OK["OkHttp Client"] -->|intercept| MI["MuckerInterceptor"]
        MI -->|1. match rule| ME["MockEngine"]
        ME -->|mock response| MI
        MI -.->|2. bypass| RemoteAPI[("Remote Backend")]
        ME <-->|sync & events| MS["MuckerHttpServer (Port 8080)"]
    end

    subgraph Clients["Developer Clients (Zero Certs Required)"]
        direction TB
        CLI["Mucker CLI (forward / listen)"]
        SPA["Web Dashboard (Waterfall & Editor)"]
        CDP["Playwright / Puppeteer (CDP Fetch Chaos)"]
        InApp["Mobile Notification & In-App WebView"]
    end

    MS <== "HTTP REST & WebSocket (CDP)" ==> Clients
  </div>
</section>

