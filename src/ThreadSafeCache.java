import java.util.*;
import java.util.concurrent.locks.*;

class Node {
    int key, value;
    long expiry;
    Node prev, next;

    Node(int key, int value, long expiry) {
        this.key = key;
        this.value = value;
        this.expiry = expiry;
    }
}

public class ThreadSafeCache {
    private final int capacity;
    private final long defaultTTL;
    private final Map<Integer, Node> map;
    private final Node head, tail;
    private final ReadWriteLock lock;
    private final Stats stats;

    public ThreadSafeCache(int capacity, long defaultTTL) {
        this.capacity = capacity;
        this.defaultTTL = defaultTTL;
        this.map = new HashMap<>();
        this.head = new Node(-1, -1, -1);
        this.tail = new Node(-1, -1, -1);
        head.next = tail;
        tail.prev = head;
        this.lock = new ReentrantReadWriteLock();
        this.stats = new Stats();
        startCleanup();
    }

    public void put(int key, int value, Long ttl) {
        lock.writeLock().lock();
        try {
            if (map.containsKey(key)) remove(map.get(key));
            else if (map.size() >= capacity) {
                map.remove(tail.prev.key);
                remove(tail.prev);
                stats.evictions++;
            }
            long expiry = System.currentTimeMillis() + (ttl != null ? ttl : defaultTTL);
            Node newNode = new Node(key, value, expiry);
            insertAtFront(newNode);
            map.put(key, newNode);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Integer get(int key) {
        lock.writeLock().lock();
        try {
            stats.totalRequests++;
            if (!map.containsKey(key)) {
                stats.misses++;
                return null;
            }
            Node node = map.get(key);
            if (System.currentTimeMillis() > node.expiry) {
                map.remove(key);
                remove(node);
                stats.expiredRemovals++;
                stats.misses++;
                return null;
            }
            remove(node);
            insertAtFront(node);
            stats.hits++;
            return node.value;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void delete(int key) {
        lock.writeLock().lock();
        try {
            if (map.containsKey(key)) {
                remove(map.get(key));
                map.remove(key);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            map.clear();
            head.next = tail;
            tail.prev = head;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<String, Object> getStats() {
        lock.readLock().lock();
        try {
            double hitRate = stats.totalRequests == 0 ? 0 : (double) stats.hits / stats.totalRequests;
            return Map.of(
                    "hits", stats.hits,
                    "misses", stats.misses,
                    "hit_rate", hitRate,
                    "total_requests", stats.totalRequests,
                    "current_size", map.size(),
                    "evictions", stats.evictions,
                    "expired_removals", stats.expiredRemovals
            );
        } finally {
            lock.readLock().unlock();
        }
    }

    private void insertAtFront(Node node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    private void remove(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void startCleanup() {
        Thread cleaner = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                    lock.writeLock().lock();
                    long now = System.currentTimeMillis();
                    for (Iterator<Map.Entry<Integer, Node>> it = map.entrySet().iterator(); it.hasNext(); ) {
                        Map.Entry<Integer, Node> entry = it.next();
                        if (now > entry.getValue().expiry) {
                            remove(entry.getValue());
                            it.remove();
                            stats.expiredRemovals++;
                        }
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    lock.writeLock().unlock();
                }
            }
        });
        cleaner.setDaemon(true);
        cleaner.start();
    }

    static class Stats {
        int hits = 0;
        int misses = 0;
        int totalRequests = 0;
        int evictions = 0;
        int expiredRemovals = 0;
    }

    // Usage demo
    public static void main(String[] args) throws InterruptedException {
        ThreadSafeCache cache = new ThreadSafeCache(3, 3000);

        cache.put(1, 100, null);
        cache.put(2, 200, 2000L);
        System.out.println("GET 1:"+ cache.get(1));

        Thread.sleep(2500);
        System.out.println("GET 2 (expired):"+ cache.get(2));

        cache.put(3, 300, null);
        cache.put(4, 400, null); // Triggers eviction

        System.out.println("Stats:"+ cache.getStats());
    }
}
