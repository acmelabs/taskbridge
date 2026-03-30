package app.acmelabs.taskbridge.server;

import app.acmelabs.taskbridge.server.dto.AcquiredJobResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LongPollRegistry {

    private static final Logger log = LoggerFactory.getLogger(LongPollRegistry.class);

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<DeferredResult<List<AcquiredJobResponse>>>> waiters =
            new ConcurrentHashMap<>();

    public void park(String topic, DeferredResult<List<AcquiredJobResponse>> deferred) {
        Queue<DeferredResult<List<AcquiredJobResponse>>> queue =
                waiters.computeIfAbsent(topic, t -> new ConcurrentLinkedQueue<>());
        queue.add(deferred);
        log.debug("Parked long-poll request for topic={}, parkedCount={}", topic, queue.size());

        deferred.onTimeout(() -> {
            log.debug("Long-poll request timed out for topic={}", topic);
            remove(topic, deferred);
            deferred.setResult(Collections.emptyList());
        });

        deferred.onCompletion(() -> remove(topic, deferred));
    }

    public boolean wakeUp(String topic) {
        Queue<DeferredResult<List<AcquiredJobResponse>>> queue = waiters.get(topic);
        if (queue == null) {
            return false;
        }
        DeferredResult<List<AcquiredJobResponse>> deferred = queue.poll();
        if (deferred == null) {
            return false;
        }
        log.debug("Waking parked long-poll request for topic={}", topic);
        deferred.setResult(Collections.emptyList());
        return true;
    }

    public void wakeUpAll(String topic) {
        Queue<DeferredResult<List<AcquiredJobResponse>>> queue = waiters.get(topic);
        if (queue == null) {
            return;
        }
        DeferredResult<List<AcquiredJobResponse>> deferred;
        while ((deferred = queue.poll()) != null) {
            log.debug("Waking all parked long-poll requests for topic={}", topic);
            deferred.setResult(Collections.emptyList());
        }
    }

    public int parkedCount(String topic) {
        Queue<DeferredResult<List<AcquiredJobResponse>>> queue = waiters.get(topic);
        return queue == null ? 0 : queue.size();
    }

    private void remove(String topic, DeferredResult<?> deferred) {
        Queue<DeferredResult<List<AcquiredJobResponse>>> queue = waiters.get(topic);
        if (queue != null) {
            queue.remove(deferred);
            if (queue.isEmpty()) {
                waiters.remove(topic, queue);
            }
        }
    }
}
