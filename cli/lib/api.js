// REST client for Mucker Server

export class MuckerClient {
  constructor(baseUrl = 'http://localhost:8080') {
    this.baseUrl = baseUrl.replace(/\/+$/, '');
  }

  async getStatus() {
    const res = await fetch(`${this.baseUrl}/api/status`);
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    return await res.json();
  }

  async getRules() {
    const res = await fetch(`${this.baseUrl}/api/rules`);
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    return await res.json();
  }

  async addRule(rule) {
    const res = await fetch(`${this.baseUrl}/api/rules`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(rule),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    return await res.json();
  }

  async removeRule(ruleId) {
    const res = await fetch(`${this.baseUrl}/api/rules/${encodeURIComponent(ruleId)}`, {
      method: 'DELETE',
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    return await res.json();
  }

  async clearRules() {
    const res = await fetch(`${this.baseUrl}/api/rules`, {
      method: 'DELETE',
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    return await res.json();
  }

  async getHistory() {
    const res = await fetch(`${this.baseUrl}/api/history`);
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    return await res.json();
  }

  async fulfillPaused(requestId, { statusCode = 200, responseHeaders = {}, responseBody = '' } = {}) {
    const res = await fetch(`${this.baseUrl}/api/paused/${encodeURIComponent(requestId)}/fulfill`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ statusCode, responseHeaders, responseBody }),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    return await res.json();
  }

  async continuePaused(requestId) {
    const res = await fetch(`${this.baseUrl}/api/paused/${encodeURIComponent(requestId)}/continue`, {
      method: 'POST',
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    return await res.json();
  }

  async getCdpVersion() {
    const res = await fetch(`${this.baseUrl}/json/version`);
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    return await res.json();
  }

  async getCdpTargets() {
    const res = await fetch(`${this.baseUrl}/json/list`);
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    return await res.json();
  }
}
