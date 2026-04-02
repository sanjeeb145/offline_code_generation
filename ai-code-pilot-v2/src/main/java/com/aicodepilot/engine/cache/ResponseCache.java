package com.aicodepilot.engine.cache;

import com.aicodepilot.model.AIResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe LRU cache for AI inference responses.
 *
 * Prevents redundant model calls for repeated identical prompts.
 * This is particularly useful during iterative development when the
 * developer re-analyzes the same code multiple times.
 *
 * Cache key = hash of (prompt + maxNewTokens).
 * Max entries = configurable (default 50).
 */
public class ResponseCache {

    private final int maxSize;
    private final Map<String, AIResponse> cache;

    public ResponseCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache   = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, AIResponse> eldest) {
                return size() > maxSize;
            }
        };
    }

    public synchronized Optional<AIResponse> get(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    public synchronized void put(String key, AIResponse response) {
        if (response.isSuccess()) {
            cache.put(key, response);
        }
    }

    public synchronized void invalidate(String key) {
        cache.remove(key);
    }

    public synchronized void clear() {
        cache.clear();
    }

    public synchronized int size() {
        return cache.size();
    }

    /**
     * Builds a stable cache key from prompt content and token budget.
     */
    public static String buildKey(String prompt, int maxNewTokens) {
        return String.valueOf(prompt.hashCode()) + "_" + maxNewTokens;
    }
}
