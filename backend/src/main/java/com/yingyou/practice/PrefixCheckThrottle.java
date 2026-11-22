package com.yingyou.practice;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单实例内的预检限流，避免逐字输入造成过高请求量。
 */
@Component
public class PrefixCheckThrottle {

    private static final int LIMIT = 25;
    private static final long WINDOW_MILLIS = 5_000;

    private final ConcurrentHashMap<UUID, Deque<Long>> windows = new ConcurrentHashMap<>();

    public boolean allow(UUID sessionId) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = windows.computeIfAbsent(sessionId, key -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MILLIS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= LIMIT) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
