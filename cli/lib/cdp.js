// WebSocket client for live listening and CDP interaction using Node 22 native WebSocket

export function listenLive(wsUrl, onEvent, onError) {
  const ws = new WebSocket(wsUrl);

  ws.addEventListener('open', () => {
    // Send standard CDP Fetch.enable to receive interception events
    ws.send(JSON.stringify({
      id: 1,
      method: 'Fetch.enable',
      params: {
        patterns: [{ urlPattern: '*' }]
      }
    }));
  });

  ws.addEventListener('message', (event) => {
    try {
      const data = JSON.parse(event.data);
      onEvent(data, ws);
    } catch (e) {
      // ignore malformed
    }
  });

  ws.addEventListener('error', (err) => {
    if (onError) onError(err);
  });

  return ws;
}
