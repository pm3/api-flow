
# Vysoká dostupnosť api-flow pomocou `vector.dev`

Tento dokument popisuje, ako možno dosiahnuť vysokú dostupnosť HTTP endpointov api-flow pomocou `vector.dev` proxy, ktorá umožňuje:

- buffering požiadaviek počas výpadku backendu,
- retry pri výpadku cieľa,
- routing požiadaviek podľa partition (napr. podľa URL parametra).

---

## 🧱 1. Základná architektúra — 1 backend

```
[ klienti ] ---> [ api-flow ]
```

### Vlastnosti:

- Jeden backend `api-flow`

pri reštarte api-flow môže dôjsť k strate requestov. Ak klienti ktorý volajú
api-flow majú retry policy, dá sa zabezpečiť vysoká dostupnosť.
---

## 🛠️ 2. Vector ako Kubernetes Service

```
[ klienti ] ---> [ K8s Service (vector) ]
                       |
                 +-----+------+
                 |            |
           [ vector pod ] [ vector pod ]
                       |   |
                   [ api-flow ]
```

### Vlastnosti:

- Viacero replikácií Vectoru za Service
- Klienti sa pripájajú cez Service (HA príjem požiadaviek)
- Vectory posielajú požiadavky na jeden `api-flow` pod

### `vector.toml` konfigurácia:

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
```


## 🧩 3. Viac `api-flow` backendov s `partition` routingom

```
[ klienti ]
    |
[ vector.dev ] -- partitions --> 
[ vector.dev ] -- partitions --> 
                                 [ api-flow-0 ]
                                 [ api-flow-1 ]
                                 [ api-flow-2 ]
```

### Vlastnosti:

- Každý požiadavok extrahuje `X-CASE-ID`
- Vector vypočíta partition hash a smeruje na konkrétny `api-flow-{N}`
- Zabezpečí, že všetky požiadavky pre daný proces idú do tej istej inštancie

### `vector.toml` s `partition` routingom:

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

## 🧠 Poznámky k prevádzke

- `partition.key` musí byť deterministický — napr. `x-process-id` header
- `partitions` je statické číslo — jeho zmena vyžaduje rollout Vector konfigurácie
- Vector nevie dynamicky meniť routovanie, takže na škálovanie použite externé nástroje (napr. Envoy)

---

## 🟢 Záver

Vďaka `vector.dev` vieš:

- dosiahnuť **vysokú dostupnosť aj pri jednej inštancii backendu** (cez buffer/retry),
- **škálovať horizontálne** stavové aplikácie, ak vieš zabezpečiť sticky routing,
- **konzistentne smerovať požiadavky podľa logiky aplikácie**, napr. podľa procesu alebo user ID.

Všetky tieto techniky sú vhodné pre Kubernetes, bez nutnosti úprav klientov.

---

## 📚 Odkazy

- [vector.dev](https://vector.dev)
- [Transform: partition](https://vector.dev/docs/reference/configuration/transforms/partition/)
- [Sink: http](https://vector.dev/docs/reference/configuration/sinks/http/)
- [Buffering options](https://vector.dev/docs/reference/configuration/buffers/)
