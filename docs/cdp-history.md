---
layout: doc
title: Evolution of CDP Mock Response
subtitle: How Chrome DevTools Protocol evolved from raw HTTP bytes to semantic Fetch.fulfillRequest and DevTools UI Overrides.
---

When investigating how to mock network requests on mobile using Chrome technologies, a frequent historical question emerges:

> *"When did Chrome DevTools Protocol (CDP) first support Mock Responses and 'Override content'?"*

The answer depends on the layer of the Chrome stack you examine: **the underlying CDP wire protocol** versus **the DevTools Graphical User Interface (UI)**.

---

## 1. Phase 1: The Early Experimental Era (`Network` Domain, ~2017)

CDP introduced experimental request and response interception around **Chrome 60–62 (mid-2017)** directly inside the `Network` domain.

### Key CDP APIs:
* `Network.setRequestInterception`: Enabled interception patterns at specific stages (e.g., `HeadersReceived`).
* `Network.requestIntercepted`: Emitted when an outgoing request was paused.
* `Network.continueInterceptedRequest`: Allowed supplying `rawResponse` (a Base64-encoded string representing the complete HTTP wire protocol packet, including status line and headers).

### Architectural Limitations:
* **Cumbersome Wire Protocol**: Developers had to manually assemble raw HTTP strings (e.g., `HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n...`).
* **High Process Coupling**: Tied intimately to Chromium's internal network process lifecycle.
* **Deprecation**: As Chromium refactored its network service architecture, the interception APIs in the `Network` domain were declared deprecated and slated for phase-out.

---

## 2. Phase 2: The Modern Semantic Standard (`Fetch` Domain, ~2019)

To solve the fragility and complexity of `Network` interception, the Chromium team extracted a dedicated domain: **`Fetch`**, officially released around **Chrome 74–75 (spring 2019)**.

The `Fetch` domain is the official, stable standard for request interception and response mocking.

### Key CDP APIs:
* **`Fetch.enable`**: Activates interception with structured glob/regex URL patterns:
  ```json
  { "method": "Fetch.enable", "params": { "patterns": [{ "urlPattern": "*/api/*" }] } }
  ```
* **`Fetch.requestPaused`**: Event dispatched whenever a matching network request is intercepted:
  ```json
  {
    "method": "Fetch.requestPaused",
    "params": {
      "requestId": "req_84920",
      "request": {
        "url": "https://api.example.com/v1/user/profile",
        "method": "GET",
        "headers": { "Authorization": "Bearer ..." }
      },
      "resourceType": "XHR"
    }
  }
  ```
* **`Fetch.fulfillRequest`** *(The Core Mocking Primitive)*: Fulfills the paused request with a custom mock response without hitting the network:
  ```json
  {
    "method": "Fetch.fulfillRequest",
    "params": {
      "requestId": "req_84920",
      "responseCode": 200,
      "responseHeaders": [
        { "name": "Content-Type", "value": "application/json" }
      ],
      "body": "eyJzdGF0dXMiOiAibW9ja2VkIn0="
    }
  }
  ```
* **`Fetch.continueRequest`**: Resumes the request to allow it to hit the real network.
* **`Fetch.continueResponse` / `Fetch.getResponseBody`**: Modifies the response after the server replies.

This protocol standard powers modern automation frameworks like Puppeteer (`request.respond()`) and Playwright (`route.fulfill()`).

---

## 3. Phase 3: The DevTools Visual UI: "Override Content" (Chrome 117, Sept 2023)

Many developers first discovered mocking in Chrome when Google released **Chrome 117 (September 2023)**, which introduced the right-click **"Override content"** option directly inside the Network panel:

![Chrome DevTools Override Content](https://developer.chrome.com/static/blog/devtools-tips-34/image/network-override-content_856.png)

### Why the Difference in Dates?
* **Protocol Layer (2019, Chrome 74)**: The programmatic JSON-RPC API (`Fetch.fulfillRequest`) was ready for automation tools and Headless Chrome.
* **UI Panel Layer (2023, Chrome 117)**: Google connected the DevTools visual frontend with Chromium's Local Workspaces filesystem mapping, providing a 1-click button for human developers to save mock files to their local disk.

---

## 4. Why Stetho and Flipper Missed This Wave

* **Facebook Stetho (Discontinued ~2018–2019)**:
  Stetho was created during the early `Network` domain era. It mapped read-only logs (`Network.requestWillBeSent`, `Network.responseReceived`) into Chrome, but was discontinued right before the `Fetch` domain was standardized.
* **Meta Flipper (Archived ~2023)**:
  Flipper abandoned Chrome entirely in favor of an Electron desktop app with custom TLS/WebSocket RPCs. However, the heavy C++ native dependencies and fragile certificate handshakes made it difficult to maintain.

---

## 5. Mucker's Design Principle

Mucker takes the lessons learned from this 8-year evolution:

1. **Adopt the Standard**: Mucker implements the **CDP `Fetch` domain** (`Fetch.enable`, `Fetch.requestPaused`, `Fetch.fulfillRequest`, `Fetch.continueRequest`).
2. **Decouple from Chrome DevTools Internals**: Instead of forcing Android View hierarchies into fake HTML DOM trees (which broke Stetho), Mucker focuses exclusively on network traffic.
3. **Dual Surface**: Serve both a zero-dependency **Web Dashboard** and standard **CDP JSON-RPC endpoints**, providing instant visual mocking for humans and scriptable automation for bots.
