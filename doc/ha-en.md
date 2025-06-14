
# High Availability of api-flow with `vector.dev`

This document describes how to achieve high availability of HTTP endpoints in a api-flow using the `vector.dev` proxy, which enables:

- buffering of requests during backend outages,
- retrying on target failures,
- request routing based on partitions (e.g., by URL parameter).

---

## 🧱 1. Basic Architecture — 1 Backend

```
[ klienti ] ---> [ api-flow ]
```

### Features:

- A single backend `api-flow`

a restart of `api-flow` may result in lost requests. If clients calling `api-flow` have a retry policy, high availability can be ensured.
---

## 🛠️ 2. Vector as a Kubernetes Service

```
[ klienti ] ---> [ K8s Service (vector) ]
                       |
                 +-----+------+
                 |            |
           [ vector pod ] [ vector pod ]
                       |   |
                   [ api-flow ]
```

### Features:

- Viacero replikácií Vectoru za Service
- Klienti sa pripájajú cez Service (HA príjem požiadaviek)
- Vectory posielajú požiadavky na jeden `api-flow` pod

### `vector.toml` configuration:

```toml
[sources.http_in]
type = "http_server"
address = "0.0.0.0:8080"

[sinks.http_out]
type = "http"
inputs = ["http_in"]
uri = "http://api-flow:8080"
encoding.codec = "json"

retries = 5
backoff = "exponential"

[sinks.http_out.buffer]
type = "disk"
max_size = 100_000_000 # 100 MB
---


## 🧩 3. Multiple `api-flow` Backends with `partition` Routing

```
[ klienti ]
    |
[ vector.dev ] -- partitions --> 
[ vector.dev ] -- partitions --> 
                                 [ api-flow-0 ]
                                 [ api-flow-1 ]
                                 [ api-flow-2 ]
```

### Features:

- Each request extracts `X-CASE-ID`
- Vector calculates a partition hash and routes to a specific `api-flow-{N}`
- Ensures that all requests for a given process go to the same instance

### `vector.toml` with `partition` routing:

```toml
[sources.http_in]
type = "http_server"
address = "0.0.0.0:8080"

[transforms.partition]
type = "partition"
inputs = ["http_in"]
partition.key = "{{ headers.x-process-id }}"
partitions = 3

# Sink pre partition 0
[sinks.http_0]
type = "http"
inputs = ["partition"]
uri = "http://api-flow-0:8080"
condition = "partition == 0"

# Sink pre partition 1
[sinks.http_1]
type = "http"
inputs = ["partition"]
uri = "http://api-flow-1:8080"
condition = "partition == 1"

# Sink pre partition 2
[sinks.http_2]
type = "http"
inputs = ["partition"]
uri = "http://api-flow-2:8080"
condition = "partition == 2"
```

---

## 🧠 Operational Notes

- `partition.key` must be deterministic — e.g., `x-process-id` header
- `partitions` is a static number — changing it requires a rollout of the Vector configuration
- Vector cannot dynamically change routing, so use external tools for scaling (e.g., Envoy)

---

## 🟢 Conclusion

Thanks to `vector.dev` you can:

- achieve **high availability even with a single backend instance** (via buffer/retry),
- **horizontally scale** stateful applications if you can ensure sticky routing,
- **consistently route requests based on application logic**, e.g., by process or user ID.

All these techniques are suitable for Kubernetes without requiring client modifications.

---

## 📚 References

- [vector.dev](https://vector.dev)
- [Transform: partition](https://vector.dev/docs/reference/configuration/transforms/partition/)
- [Sink: http](https://vector.dev/docs/reference/configuration/sinks/http/)
- [Buffering options](https://vector.dev/docs/reference/configuration/buffers/)
