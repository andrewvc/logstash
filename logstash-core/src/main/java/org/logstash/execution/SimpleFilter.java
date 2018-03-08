package org.logstash.execution;

import org.logstash.Event;

import java.util.Arrays;
import java.util.Collection;

public interface SimpleFilter extends Filter {
    void filterSimple(Event event);

    @Override
    default QueueReader filter(QueueReader reader) {
        return new QueueReader() {
            @Override
            public long poll(final Event event) {
                final long seq = reader.poll(event);
                if (seq > -1L) {
                    filterSimple(event);
                }
                return seq;
            }

            @Override
            public long poll(Event event, long millis) {
                final long seq = reader.poll(event, millis);
                if (seq > -1L) {
                    filterSimple(event);
                }
                return seq;
            }

            @Override
            public void acknowledge(long sequenceNum) {
                reader.acknowledge(sequenceNum);
            }
        };
    }

    @LogstashPlugin(name = "mutate")
    final class Mutate implements SimpleFilter {
        private static final PluginConfigSpec<String> FIELD_CONFIG =
            LsConfiguration.requiredStringSetting("field");

        private static final PluginConfigSpec<String> VALUE_CONFIG =
            LsConfiguration.requiredStringSetting("value");

        private final String field;

        private final String value;

        /**
         * Required Constructor Signature only taking a {@link LsConfiguration}.
         * @param configuration Logstash Configuration
         * @param context Logstash Context
         */
        public Mutate(final LsConfiguration configuration, final LsContext context) {
            this.field = configuration.get(FIELD_CONFIG);
            this.value = configuration.get(VALUE_CONFIG);
        }

        @Override
        public void filterSimple(final Event event) {
            event.setField(field, value);
        }

        @Override
        public Collection<PluginConfigSpec<?>> configSchema() {
            return Arrays.asList(FIELD_CONFIG, VALUE_CONFIG);
        }
    }
}
