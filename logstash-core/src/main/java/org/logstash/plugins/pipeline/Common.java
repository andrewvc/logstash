package org.logstash.plugins.pipeline;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * This class is essentially the communication bus / central state for the `pipeline` inputs/outputs to talk to each
 * other. This is all static due to the fact that this is inherently a singleton since it crosses pipelines.
 * We might, in the future, be able to make this an instance tied to the Agent, but with the Agent in ruby that's tricky.
 *
 * This class is threadsafe. All locking is coarse grained with `synchronized` since contention for all these method
 * shouldn't matter
 */
public class Common {
    static final HashMap<String, AddressState> ADDRESS_STATES = new HashMap<>();

    /**
     * Calculate a summary of addresses, binned by run state
     * @return the summary
     */
    static synchronized AddressesByRunState addressesByRunState() {
        AddressesByRunState addressesByRunState = new AddressesByRunState();
        ADDRESS_STATES.forEach( (address, state) -> {
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
    public static synchronized void registerSender(PipelineOutput output, Collection<String> addresses) {
        addresses.forEach((String address) -> {
            AddressState state = ADDRESS_STATES.computeIfAbsent(address, AddressState::new);
            state.addOutput(output);
        });
    }

    /**
     * Should be called by an output on close
     * @param output
     */
    public static synchronized void unregisterSender(PipelineOutput output) {
        ADDRESS_STATES.forEach( (address, state) -> {
            boolean removed = state.removeOutput(output);
            if (removed) output.removeAddressReceiver(address);
            if (state.isEmpty()) ADDRESS_STATES.remove(address);
        });
    }

    /**
     * Listens to a given address with the provided listener
     * Only one listener can listen on an address at a time
     * @param address
     * @param input
     * @return true if the listener successfully subscribed
     */
    public static synchronized boolean listen(final PipelineInput input, final String address) {
        AddressState state = ADDRESS_STATES.computeIfAbsent(address, AddressState::new);
        return state.assignInputIfMissing(input);
    }

    /**
     * Stop listing on the given address with the given listener
     * @param address
     * @param input
     */
    public static synchronized void unlisten(final PipelineInput input, final String address) {
        AddressState state = ADDRESS_STATES.get(address);
        if (state != null) state.unassignInput(input);

        prune(address);
    }

    /**
     *  Checks the given address to see if it's empty, removing it if it is
     */
    private static void prune(String address) {
        ADDRESS_STATES.computeIfPresent(address, (a, state) -> {
           return (state.isEmpty()) ? null : state;
        });
    }

    /**
     * Iterate over the provided addresses and call the fn with their state, creating
     * the state if it does not yet exist. This is an internal helper for a common pattern
     * @param addresses
     * @param fn
     */
    private static synchronized void forEachAddressState(Iterable<String> addresses,
                                                         BiFunction<String, AddressState, Void> fn) {
        for (String address : addresses) {
            AddressState state = ADDRESS_STATES.computeIfAbsent(address, AddressState::new);
            fn.apply(address, state);
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
