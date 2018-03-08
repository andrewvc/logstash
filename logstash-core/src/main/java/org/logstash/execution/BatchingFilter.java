package org.logstash.execution;

import org.logstash.Event;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.IntStream;

public interface BatchingFilter extends Filter {
    void filterBatch(Event[] event);
    int batchSize();

    @Override
    default QueueReader filter(QueueReader reader) {
        final Event[] eventPool = new Event[batchSize()];
        final long[] eventSeqs = new long[batchSize()];

        for (int i = 0; i < batchSize(); i++) {
            eventPool[i] = new Event();
        }
        // One element array to deal with closure
        final int[] nextPoolIndex = {-1};

        return new QueueReader() {
            @Override
            public long poll(final Event event) {
                while (true) {
                    long seq = poll(event, 50); // We need a default...
                    if (seq > -1L) {
                        return seq;
                    }
                }
            }

            @Override
            public long poll(Event event, long millis) {
                // If we have more in the batch to return
                if (nextPoolIndex[0] > -1) {
                    int currentPoolIndex = nextPoolIndex[0];
                    long currentPoolSeq = eventSeqs[currentPoolIndex];

                    // If we have more in the batch to return
                    if (currentPoolSeq > -1L) {
                        nextPoolIndex[0]++;
                        event.overwrite(eventPool[currentPoolIndex]);
                        return eventSeqs[currentPoolIndex];
                    }

                    // We hit the end, stop iterating over this batch
                    nextPoolIndex[0] = -1;
                }

                // Accumulate events into a batch
                final int size = batchSize();
                eventPool[size] = event;
                int accumulatedEventsCount = 0;
                for (int i = 0; i < size; i++) {
                    final Event poolEvent = eventPool[i];
                    long seq = reader.poll(poolEvent, millis);
                    eventSeqs[i] = seq;
                    accumulatedEventsCount = i-1;
                    if (seq < 0) break;
                }

                if (accumulatedEventsCount == 0) {
                    return -1;
                }

                // Make an array of the exact size of data we have, the batch may come up short
                final Event[] accumulatedEvents = new Event[accumulatedEventsCount];
                System.arraycopy(eventPool, 0, accumulatedEvents, 0, accumulatedEventsCount);

                // Actually do the user's work
                filterBatch(accumulatedEvents);

                nextPoolIndex[0] = 1;
                event.overwrite(eventPool[0]);
                return eventSeqs[0];
            }

            @Override
            public void acknowledge(long sequenceNum) {
                reader.acknowledge(sequenceNum);
            }
        };
    }

    @LogstashPlugin(name = "mutateBatch")
    final class MutateBatch implements BatchingFilter {
        private static final PluginConfigSpec<String> FIELD_CONFIG =
                LsConfiguration.requiredStringSetting("field");

        private static final PluginConfigSpec<String> VALUE_CONFIG =
                LsConfiguration.requiredStringSetting("value");

        private final String field;

        private final String value;

        private final int batchSize;

        /**
         * Required Constructor Signature only taking a {@link LsConfiguration}.
         * @param configuration Logstash Configuration
         * @param context Logstash Context
         * @param batchSize
         */
        public MutateBatch(final LsConfiguration configuration, final LsContext context, final int batchSize) {
            this.field = configuration.get(FIELD_CONFIG);
            this.value = configuration.get(VALUE_CONFIG);
            this.batchSize = batchSize;
        }

        @Override
        public Collection<PluginConfigSpec<?>> configSchema() {
            return Arrays.asList(FIELD_CONFIG, VALUE_CONFIG);
        }

        @Override
        public void filterBatch(Event[] event) {
            // Do some batch operation to each event
        }

        @Override
        public int batchSize() {
            return batchSize;
        }
    }
}
