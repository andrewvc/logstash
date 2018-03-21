package org.logstash.plugins.pipeline;

import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;
import org.logstash.ext.JrubyEventExtLibrary;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class CommonTest {
    static String address = "fooAddr";
    static String otherAddress = "fooAddr";
    static Collection<String> addresses = Arrays.asList(address, otherAddress);

    @Test
    public void subscribeUnsubscribe() {
        PipelineInput input = createInput();

        assertThat(Common.listen(input, address)).isTrue();
        assertThat(Common.ADDRESS_STATES.get(address).getInput() == input);

        Common.unlisten(input, address);

        assertThat(Common.ADDRESS_STATES.get(address)).isNull();
    }

    @Test
    public void senderRegisterUnregister() {
        PipelineOutput output = createOutput();

        Common.registerSender(output, addresses);

        assertThat(Common.ADDRESS_STATES.get(address).hasOutput(output)).isTrue();
    }

    PipelineInput createInput() {
        return new PipelineInput() {
            public int eventCount = 0;

            @Override
            public boolean internalReceive(JrubyEventExtLibrary.RubyEvent event) {
                eventCount++;
                return true;
            }

            @Override
            public boolean isRunning() {
                return true;
            }
        };
    }

    PipelineOutput createOutput() {
        return new PipelineOutput() {
            Map<String, Function<JrubyEventExtLibrary.RubyEvent, Boolean>> receivers = new HashMap<>();
            @Override
            public void updateAddressReceiver(String address, Function<JrubyEventExtLibrary.RubyEvent, Boolean> receiverFn) {
                receivers.put(address, receiverFn);
            }

            @Override
            public void removeAddressReceiver(String address) {
                receivers.remove(address);
            }
        };
    }
}