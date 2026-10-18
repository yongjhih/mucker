# Mucker WebMCP Implementation Handover Guide

> **交接目標**：為 Mucker 的靜態文件站點（`docs/`，發布於 `https://yongjhih.github.io/mucker/`）實作 WebMCP 端點（`docs/mcp.html`），使其可無縫掛載進使用者的 `ai-proxy`（`127.0.0.1:8788`），讓 Claude Code、Cursor、Windsurf 及本機 AI Agent 能直接調用 Mucker 的各案工具。

---

## 1. 目前已就緒的基礎設施

1. **一鍵新增 MCP（One-Click Connect）深層連結與 Badge**：
   - `README.md` 已加入標準徽章：
     ```markdown
     [![MCP Pages: Connect](https://img.shields.io/badge/MCP%20Pages-Connect-238636?logo=github&logoColor=white)](https://yongjhih.github.io/ai-proxy/dashboard.html#add-url=https%3A%2F%2Fyongjhih.github.io%2Fmucker%2Fmcp)
     ```
   - `docs/index.md` Hero CTA 區塊已加入 `🔌 Add to ai-proxy` 按鈕。
   - 點擊後會開啟 `ai-proxy` 控制台，自動探測本機 Port 8788 Relay，並自動帶入新增 `https://yongjhih.github.io/mucker/mcp`。

2. **WebMCP 前端模組已 Vendor**：
   - 已執行 `npx ai-proxy vendor docs/web`。
   - `docs/web/` 目錄已包含 7 個零依賴 ES 模組：
     - `client.mjs`, `dispatch.mjs`, `handshake.mjs`, `index.mjs`, `model-context.mjs`, `ui.mjs`, `url.mjs`。

---

## 2. `mucker` Session 需執行的實作項目

### 任務 1：建立 `docs/mcp.html`

在 `docs/` 下建立純前端靜態頁面 `mcp.html`。
使用 `mountMcpPageUi`（源自 `./web/ui.mjs`），並遵循 MCP Pages 規範：
- 不使用任何伺服器端後端。
- 嚴格落實 Fragment 金鑰隔離（模組已自動處理 `#mcp-key` 至 `history.state`，絕不寫入 localStorage/cookie）。
- 工具標註準確：唯讀查詢設定 `annotations: { readOnlyHint: true }`。

#### 建議實作的 5 大 WebMCP Tools：

```javascript
const tools = [
  {
    name: 'get_mock_rule_template',
    description: 'Generate a JSON mock rule template for Mucker or CDP Fetch.fulfillRequest matching a URL pattern.',
    inputSchema: {
      type: 'object',
      properties: {
        urlPattern: { type: 'string', description: 'URL regex or glob pattern (e.g. *://api.example.com/v1/users*)' },
        method: { type: 'string', enum: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'], description: 'HTTP method' },
        statusCode: { type: 'integer', default: 200, description: 'HTTP status code to mock' },
        responseBody: { type: 'string', description: 'JSON response body' },
        delayMs: { type: 'integer', default: 0, description: 'Simulated response latency in milliseconds' }
      },
      required: ['urlPattern']
    },
    annotations: {
      title: 'Generate Mock Rule',
      readOnlyHint: true,
      destructiveHint: false,
      idempotentHint: true
    },
    async execute(args) {
      // 產生 Mucker REST API 與 CDP 格式的 Mock 規則
    }
  },
  {
    name: 'get_cli_command',
    description: 'Generate a ready-to-run Mucker CLI command for mocking, forwarding, or inspecting OkHttp traffic.',
    inputSchema: {
      type: 'object',
      properties: {
        action: { type: 'string', enum: ['mock', 'breakpoint', 'list', 'clear', 'export'], description: 'CLI action' },
        url: { type: 'string', description: 'Target URL or pattern' },
        statusCode: { type: 'integer', description: 'Status code for mock' }
      },
      required: ['action']
    },
    annotations: {
      title: 'Get CLI Command',
      readOnlyHint: true,
      destructiveHint: false,
      idempotentHint: true
    },
    async execute(args) {
      // 產生 mucker mock / mucker breakpoint / mucker list 指令
    }
  },
  {
    name: 'get_cdp_spec',
    description: 'Retrieve the Chrome DevTools Protocol Fetch domain specification supported by Mucker.',
    inputSchema: {
      type: 'object',
      properties: {
        command: {
          type: 'string',
          enum: ['all', 'Fetch.enable', 'Fetch.requestPaused', 'Fetch.fulfillRequest', 'Fetch.continueRequest', 'Fetch.failRequest'],
          description: 'CDP command name'
        }
      }
    },
    annotations: {
      title: 'Get CDP Spec',
      readOnlyHint: true,
      destructiveHint: false,
      idempotentHint: true
    },
    async execute(args) {
      // 回傳 Fetch 領域的 JSON-RPC 參數格式與行為規範
    }
  },
  {
    name: 'consult_integration',
    description: 'Get setup and integration guidance for OkHttp 4.x/5.x, Gradle debugImplementation vs releaseImplementation no-op split, and zero CA setup.',
    inputSchema: {
      type: 'object',
      properties: {
        buildSystem: { type: 'string', enum: ['gradle-kts', 'gradle-groovy', 'maven'], description: 'Build system' },
        client: { type: 'string', enum: ['okhttp', 'retrofit', 'ktor'], description: 'HTTP client framework' }
      }
    },
    annotations: {
      title: 'Consult Integration',
      readOnlyHint: true,
      destructiveHint: false,
      idempotentHint: true
    },
    async execute(args) {
      // 回傳 Gradle 依賴配置程式碼與 Interceptor 初始化範例
    }
  },
  {
    name: 'search_docs',
    description: 'Search Mucker documentation topics, architecture, and guides.',
    inputSchema: {
      type: 'object',
      properties: {
        query: { type: 'string', description: 'Search keywords' }
      },
      required: ['query']
    },
    annotations: {
      title: 'Search Docs',
      readOnlyHint: true,
      destructiveHint: false,
      idempotentHint: true
    },
    async execute(args) {
      // 搜尋 docs 相關主題（如 zero-ca, cdp, breakpoints, dashboard）
    }
  }
];
```

---

### 任務 2：執行驗證與 Doctor 檢查

在專案根目錄下執行：

```bash
npx ai-proxy doctor --dir docs
```

必須確認 3 項檢查全部回報 `"ok": true`：
1. `mcp_page_exists`: `mcp.html` 存在於 `docs/`。
2. `vendored_web_modules`: `docs/web/` 具備 7 個模組。
3. `mcp_page_wiring`: `mcp.html` 正確引入並呼叫 `mountMcpPageUi`。

---

### 任務 3：Jekyll 與 GitHub Pages 相容性確認

- `docs/_config.yml` 若有 `include` 設定，請確認 `web/` 與 `mcp.html` 不會被 Jekyll 忽略。
- 若使用純靜態或跳過 Jekyll，可在 `docs/` 加入 `.nojekyll`。

---

## 3. 直接交接給 `mucker` Session 的 Prompt

您可以複製以下 Prompt 直接發給 `mucker` session：

```markdown
請根據專案根目錄下的 `MCP_HANDOVER.md` 與 `skills/mcp-pages/SKILL.md`：
1. 檢視當前分支 `feat/webmcp-pages`。`README.md`、`docs/index.md` 的 Connect Badge 與 `docs/web/` 模組已就緒。
2. 在 `docs/mcp.html` 實作 Mucker WebMCP 頁面，包含 `get_mock_rule_template`、`get_cli_command`、`get_cdp_spec`、`consult_integration`、`search_docs` 5 個工具，並使用 `mountMcpPageUi` 掛載。
3. 執行 `npx ai-proxy doctor --dir docs`，確認三項檢查（mcp_page_exists, vendored_web_modules, mcp_page_wiring）全部 pass（ok: true）。
4. 提交 commit 並發布 PR，使 GitHub Pages 在 https://yongjhih.github.io/mucker/mcp 提供即時 WebMCP 工具。
```
