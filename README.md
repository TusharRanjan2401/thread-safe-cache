# Thread-Safe In-Memory Cache 

A high-performance, thread-safe in-memory cache system implemented in Java.  
Supports TTL, LRU eviction, background cleanup, and real-time stats.

## Run the Project

```bash
cd src
javac ThreadSafeCache.java
java ThreadSafeCache

```

## Features

### Basic Operations

- put(key, value, ttl) – Inserts data with optional TTL

- get(key) – Retrieves value if not expired

- delete(key) – Removes a specific key

- clear() – Clears the cache

### Thread Safety

- Uses ReentrantReadWriteLock for safe concurrent reads/writes

- Cleanup thread runs in background for TTL-based expiry

### LRU Eviction

- When cache exceeds maxSize, the least recently used (LRU) item is evicted

### TTL Support

- Entries expire based on custom or default TTL (Time-To-Live)

- Expired entries are cleaned on access or periodically by background thread

### Statistics (getStats())

Returns a JSON-style map:

```bash

{
  "hits": 10,
  "misses": 3,
  "hit_rate": 0.769,
  "total_requests": 13,
  "current_size": 5,
  "evictions": 2,
  "expired_removals": 1
}
```

### Sample Output

```bash
GET 1: 100
GET 2 (expired): null
Stats: {hits=1, misses=1, hit_rate=0.5, total_requests=2, current_size=3, evictions=0, expired_removals=1}
```

## Author

@TusharRanjan
