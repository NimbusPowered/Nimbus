# WebSocket API

Nimbus provides two WebSocket endpoints for real-time communication: a global event stream and per-service console access.

## Authentication

WebSocket clients cannot set HTTP headers during the handshake, so authentication uses a `token` query parameter instead of the `Authorization` header:

```
ws://host:port/api/events?token=your-secret-token
ws://host:port/api/services/Lobby-1/console?token=your-secret-token
```

If no token is configured on the server, the `?token=` parameter can be omitted.

::: warning
If authentication fails, the connection is immediately closed with code `1008` (Policy Violation) and the message: `Authentication required -- provide ?token= query parameter`.
:::

---

## Event Stream

### `ws://host:port/api/events`

Subscribes to all Nimbus events in real-time. Messages are JSON-encoded `EventMessage` objects:

```json
{
  "type": "SERVICE_READY",
  "timestamp": "2025-01-15T10:30:15.123Z",
  "data": {
    "service": "Lobby-1",
    "group": "Lobby"
  }
}
```

The connection receives every event emitted by the system. See the [Event Reference](./events) for all event types and their payloads.

### Connection Example (JavaScript)

```javascript
const token = 'your-secret-token';
const ws = new WebSocket(`ws://localhost:8080/api/events?token=${token}`);

ws.onopen = () => {
  console.log('Connected to Nimbus event stream');
};

ws.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  console.log(`[${msg.type}]`, msg.data);

  switch (msg.type) {
    case 'SERVICE_READY':
      console.log(`Service ${msg.data.service} is ready`);
      break;
    case 'PLAYER_CONNECTED':
      console.log(`${msg.data.player} joined ${msg.data.service}`);
      break;
    case 'SCALE_UP':
      console.log(`Scaling ${msg.data.group}: ${msg.data.from} -> ${msg.data.to}`);
      break;
  }
};

ws.onclose = (event) => {
  console.log(`Disconnected: ${event.code} ${event.reason}`);
};
```

### Connection Example (Python)

```python
import asyncio
import json
import websockets

async def listen():
    uri = "ws://localhost:8080/api/events?token=your-secret-token"
    async with websockets.connect(uri) as ws:
        print("Connected to Nimbus event stream")
        async for message in ws:
            event = json.loads(message)
            print(f"[{event['type']}] {event['data']}")

            if event["type"] == "SERVICE_CRASHED":
                print(f"ALERT: {event['data']['service']} crashed "
                      f"(exit code {event['data']['exitCode']})")

asyncio.run(listen())
```

---

## Console Stream

### `ws://host:port/api/services/{name}/console`

Bidirectional WebSocket for interacting with a service's process. Receive stdout lines and send stdin commands.

**Receiving:** Each frame is a single line of the service's stdout output (plain text).

**Sending:** Each text frame you send is written to the service's stdin as a command.

### Connection Lifecycle

1. Connect with authentication
2. If the service doesn't exist, the connection is closed with code `1003` and message `Service '{name}' not found`
3. If no process handle is available, closed with `1003` and `No process handle for '{name}'`
4. On success, stdout lines begin streaming immediately
5. Send text frames to execute commands on the service
6. Close the connection when done

### Example (JavaScript)

```javascript
const token = 'your-secret-token';
const service = 'Lobby-1';
const ws = new WebSocket(
  `ws://localhost:8080/api/services/${service}/console?token=${token}`
);

ws.onopen = () => {
  console.log(`Attached to ${service} console`);
};

ws.onmessage = (event) => {
  // Each message is a line of server output
  console.log(event.data);
};

// Send a command to the server
function sendCommand(command) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(command);
  }
}

// Example: whitelist a player
sendCommand('whitelist add Steve');

// Example: broadcast a message
sendCommand('say Server restarting in 5 minutes!');
```

### Example (Python)

```python
import asyncio
import websockets

async def console_session():
    uri = "ws://localhost:8080/api/services/Lobby-1/console?token=your-secret-token"
    async with websockets.connect(uri) as ws:
        # Start reading output in background
        async def read_output():
            async for line in ws:
                print(f"[Lobby-1] {line}")

        reader = asyncio.create_task(read_output())

        # Send commands
        await ws.send("list")
        await asyncio.sleep(2)
        await ws.send("say Hello from Python!")
        await asyncio.sleep(5)

        reader.cancel()

asyncio.run(console_session())
```

---

## Configuration

WebSocket settings are configured in Nimbus's Ktor server:

| Setting | Value | Description |
|---------|-------|-------------|
| Ping interval | 15 seconds | Server sends ping frames to detect dead connections |
| Timeout | 30 seconds | Connection closed if no pong received |
| Max frame size | 64 KB | Maximum size of a single WebSocket frame |

---

## Reconnection

Nimbus does not provide built-in reconnection. Clients should implement their own reconnection logic with exponential backoff:

```javascript
function connectWithRetry(url, maxRetries = 10) {
  let retries = 0;

  function connect() {
    const ws = new WebSocket(url);

    ws.onopen = () => {
      console.log('Connected');
      retries = 0; // Reset on successful connection
    };

    ws.onclose = (event) => {
      if (retries < maxRetries) {
        const delay = Math.min(1000 * Math.pow(2, retries), 30000);
        console.log(`Reconnecting in ${delay}ms...`);
        retries++;
        setTimeout(connect, delay);
      } else {
        console.log('Max retries reached');
      }
    };

    ws.onmessage = (event) => {
      // Handle messages
    };
  }

  connect();
}

connectWithRetry('ws://localhost:8080/api/events?token=your-secret');
```

::: tip
The `/api/health` REST endpoint can be used to check if the API is available before attempting a WebSocket connection.
:::
