package com.logstash.pipeline;

import com.logstash.Event;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * Created by andrewvc on 2/20/16.
 */
public class Worker {
    private final int batchDelay;
    private final int batchSize;
    private final SynchronousQueue<Event> queue;

    Worker(SynchronousQueue<Event> queue, int batchSize, int batchDelayMs) {
        this.queue = queue;
        this.batchSize = batchSize;
        this.batchDelay = batchDelayMs;
    }

    public void work() {
        Batch batch = takeBatch();
    }

    public Batch takeBatch() {
        boolean flush = false;
        boolean shutdown = false;
        List<Event> events = new ArrayList<>(batchSize);

        for (int i = 0; i < batchSize; i++) {
            Event event;

            try {
                if (i == 0) {
                    event = queue.take();
                } else {
                    event = queue.poll(batchDelay, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                break;
            }

            if (event == Constants.flushEvent) {
                flush = true;
            } else if (event == Constants.shutdownEvent) {
                shutdown = true;
            } else {
                events.add(event);
            }
        }

        return new Batch(events, flush, shutdown);
    }

    public Batch processBatch(Batch batch) {
        return batch;
    }
}
