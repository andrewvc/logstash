package org.logstash.plugins.pipeline;

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
    }

    /**
     * Should be called by an output on close
     * @param output output that will be unregistered
     * @param addresses collection of addresses this sender was registered with
     */
    public synchronized void unregisterSender(final PipelineOutput output, final Iterable<String> addresses) {
        addresses.forEach(address -> {
            output.removeAddressReceiver(address);

            final AddressState state = addressStates.get(address);
            if (state != null) {
                state.removeOutput(output);
                if (state.isEmpty()) addressStates.remove(address);
            }
        });
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
        return state.assignInputIfMissing(input);
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
