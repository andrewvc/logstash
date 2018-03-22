package org.logstash.plugins.pipeline;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.logstash.RubyUtil;
import org.logstash.ext.JrubyEventExtLibrary;

import java.util.*;

/**
 * This class is essentially the communication bus / central state for the `pipeline` inputs/outputs to talk to each
 * other. This is all static due to the fact that this is inherently a singleton since it crosses pipelines.
 * We might, in the future, be able to make this an instance tied to the Agent, but with the Agent in ruby that's tricky.
 *
 * This class is threadsafe. All locking is coarse grained with `synchronized` since contention for all these method
 * shouldn't matter
 */
public class PipelineBus {
    final HashMap<String, AddressState> addressStates = new HashMap<>();

    private static final Logger logger = LogManager.getLogger(PipelineBus.class);

    public void sendEvents(Iterable<JrubyEventExtLibrary.RubyEvent> events, Map<String, PipelineInput> addressReceivers, boolean ensureDelivery) {
        for (JrubyEventExtLibrary.RubyEvent event : events) {
            sendEvent(event, addressReceivers, ensureDelivery);
        }
    }

    public void sendEvent(JrubyEventExtLibrary.RubyEvent event, Map<String, PipelineInput> addressesReceivers, boolean ensureDelivery) {
        addressesReceivers.forEach( (address, input) -> {
            JrubyEventExtLibrary.RubyEvent clone = event.ruby_clone(RubyUtil.RUBY);

            boolean sendWasSuccess;
            sendWasSuccess = input.internalReceive(clone);

            while (ensureDelivery && !sendWasSuccess) {
                String message = String.format("Attempted to send event to '%s' but that address was unavailable. " +
                        "Maybe the destination pipeline is down or stopping? Will Retry.", address);
                logger.warn(message);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.error("Sleep unexpectedly interrupted in bus retry loop", e);
                }
            }
        });
    }

    /**
     * Calculate a summary of addresses, binned by run state
     * @return the summary
     */
    synchronized AddressesByRunState addressesByRunState() {
        final AddressesByRunState addressesByRunState = new AddressesByRunState();
        addressStates.forEach( (address, state) -> {
            if (state.isRunning()) {
                addressesByRunState.running.add(address);
            } else {
                addressesByRunState.notRunning.add(address);
            }
        });
        return addressesByRunState;
    }

    /**
     * Should be called by an output on register
     * @param output
     * @param addresses
     */
    public synchronized void registerSender(final PipelineOutput output, final Iterable<String> addresses) {
        addresses.forEach((String address) -> {
            final AddressState state = addressStates.computeIfAbsent(address, AddressState::new);
            state.addOutput(output);
        });

        updateOutputReceivers(output);
    }

    /**
     * Should be called by an output on close
     * @param output output that will be unregistered
     * @param addresses collection of addresses this sender was registered with
     */
    public synchronized void unregisterSender(final PipelineOutput output, final Iterable<String> addresses) {
        addresses.forEach(address -> {
            final AddressState state = addressStates.get(address);
            if (state != null) {
                state.removeOutput(output);
                if (state.isEmpty()) addressStates.remove(address);
            }
        });

        output.updateAddressReceivers(Collections.emptyMap());
    }

    private synchronized void updateOutputReceivers(final PipelineOutput output) {
        Map<String, PipelineInput> receivers = new HashMap<>();
        addressStates.forEach( (address, state) -> {
            if (state.hasOutput(output) && state.getInput() != null) receivers.put(address, state.getInput());
        });

        output.updateAddressReceivers(receivers);
    }

    /**
     * Listens to a given address with the provided listener
     * Only one listener can listen on an address at a time
     * @param address
     * @param input
     * @return true if the listener successfully subscribed
     */
    public synchronized boolean listen(final PipelineInput input, final String address) {
        final AddressState state = addressStates.computeIfAbsent(address, AddressState::new);
        if (state.assignInputIfMissing(input)) {
            state.getOutputs().forEach(this::updateOutputReceivers);
            return true;
        }
        return false;
    }

    /**
     * Stop listing on the given address with the given listener
     * @param address
     * @param input
     */
    public synchronized void unlisten(final PipelineInput input, final String address) {
        final AddressState state = addressStates.get(address);
        if (state != null) {
            state.unassignInput(input);
            if (state.isEmpty()) addressStates.remove(address);
        }
        state.getOutputs().forEach(this::updateOutputReceivers);
    }

    /**
     * Simple container that provides a tuple of running/notRunning lists of addresses
     */
    static class AddressesByRunState {
        private final List<String> running;
        private final List<String> notRunning;

        AddressesByRunState() {
            this.running = new ArrayList<>();
            this.notRunning = new ArrayList<>();
        }

        public List<String> getRunning() {
            return running;
        }

        public List<String> getNotRunning() {
            return notRunning;
        }
    }
}
