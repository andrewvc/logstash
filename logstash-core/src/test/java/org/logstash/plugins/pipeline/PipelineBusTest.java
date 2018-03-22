package org.logstash.plugins.pipeline;

import org.junit.Before;
import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;

import org.logstash.RubyUtil;
import org.logstash.ext.JrubyEventExtLibrary;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class PipelineBusTest {
    static String address = "fooAddr";
    static String otherAddress = "fooAddr";
    static Collection<String> addresses = Arrays.asList(address, otherAddress);

    PipelineBus bus;
    TestPipelineInput input;
    TestPipelineOutput output;

    @Before
    public void setup() {
        bus = new PipelineBus();
        input = new TestPipelineInput();
        output = new TestPipelineOutput();
    }

    @Test
    public void subscribeUnsubscribe() {
        assertThat(bus.listen(input, address)).isTrue();
        assertThat(bus.addressStates.get(address).getInput()).isSameAs(input);

        bus.unlisten(input, address);

        // Key should have been pruned
        assertThat(bus.addressStates.containsKey(address)).isFalse();
    }

    @Test
    public void senderRegisterUnregister() {
        bus.registerSender(output, addresses);

        assertThat(bus.addressStates.get(address).hasOutput(output)).isTrue();

        bus.unregisterSender(output, addresses);

        // We should have pruned this address
        assertThat(bus.addressStates.containsKey(address)).isFalse();
    }

    @Test
    public void activeSenderPreventsPrune() {
        bus.registerSender(output, addresses);
        bus.listen(input, address);
        bus.unlisten(input, address);

        assertThat(bus.addressStates.containsKey(address)).isTrue();
        bus.unregisterSender(output, addresses);
        assertThat(bus.addressStates.containsKey(address)).isFalse();
    }


    @Test
    public void activeListenerPreventsPrune() {
        bus.registerSender(output, addresses);
        bus.listen(input, address);
        bus.unregisterSender(output, addresses);

        assertThat(bus.addressStates.containsKey(address)).isTrue();
        bus.unlisten(input, address);
        assertThat(bus.addressStates.containsKey(address)).isFalse();
    }

    @Test
    public void registerUnregisterListenerUpdatesOutputs() {
        bus.registerSender(output, addresses);
        bus.listen(input, address);
        assertThat(output.receivers.size()).isEqualTo(1);

        bus.unregisterSender(output, addresses);
        assertThat(output.receivers.size()).isEqualTo(0);

        bus.registerSender(output, addresses);
        assertThat(output.receivers.size()).isEqualTo(1);

    }

    @Test
    public void listenUnlistenUpdatesOutputReceivers() {
        bus.registerSender(output, addresses);
        bus.listen(input, address);
        output.receivers.get(address).apply(rubyEvent());
        assertThat(input.eventCount).isEqualTo(1);

        bus.unlisten(input, address);

        TestPipelineInput newInput = new TestPipelineInput();
        bus.listen(newInput, address);
        output.receivers.get(address).apply(rubyEvent());

        // The new event went to the new input, not the old one
        assertThat(newInput.eventCount).isEqualTo(1);
        assertThat(input.eventCount).isEqualTo(1);
    }


    private JrubyEventExtLibrary.RubyEvent rubyEvent() {
      return new JrubyEventExtLibrary.RubyEvent(RubyUtil.RUBY, RubyUtil.RUBY_EVENT_CLASS);
    }

    static class TestPipelineInput implements PipelineInput {
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
    }

    static class TestPipelineOutput implements PipelineOutput {
        public Map<String, Function<JrubyEventExtLibrary.RubyEvent, Boolean>> receivers = new HashMap<>();

        @Override
        public void updateAddressReceiver(String address, Function<JrubyEventExtLibrary.RubyEvent, Boolean> receiverFn) {
            receivers.put(address, receiverFn);
        }

        @Override
        public void removeAddressReceiver(String address) {
            receivers.remove(address);
        }
    }
}