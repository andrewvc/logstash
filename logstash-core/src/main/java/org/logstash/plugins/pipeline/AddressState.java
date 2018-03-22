package org.logstash.plugins.pipeline;

import java.util.HashSet;
import java.util.Set;

/**
 * Class for representing the state of an internal address.
 * Not threadsafe.
 */
public class AddressState {
    private final String address;
    private final Set<PipelineOutput> outputs = new HashSet<>();
    private PipelineInput input = null;

    AddressState(String address) {
        this.address = address;
    }

    /**
     * Add the given output and ensure associated input's receivers are updated
     * @param output
     * @return
     */
    public boolean addOutput(PipelineOutput output) {
        if (this.input != null) {
            output.updateAddressReceiver(address, input::internalReceive);
        }
        return outputs.add(output);
    }

    public boolean removeOutput(PipelineOutput output) {
        return outputs.remove(output);
    }

    public PipelineInput getInput() {
        return input;
    }

    /**
     * Assigns an input to listen on this address. Will return false if another input is already listening.
     * @param newInput
     * @return true if successful, false if another input is listening
     */
    public boolean assignInputIfMissing(PipelineInput newInput) {
        if (input != newInput && input != null) return false;

        this.input = newInput;
        this.outputs.forEach(output -> output.updateAddressReceiver(address, newInput::internalReceive));

        return true;
    }

    /**
     * Unsubscribes the given input from this address
     * @param unsubscribingInput
     * @return true if this input was listening, false otherwise
     */
    public boolean unassignInput(PipelineInput unsubscribingInput) {
        if (input != unsubscribingInput) return false;

        input = null;
        return true;
    }

    public boolean isRunning() {
        return input != null && input.isRunning();
    }

    public boolean isEmpty() {
        return (input == null) && outputs.isEmpty();
    }

    // Just for tests
    boolean hasOutput(PipelineOutput output) {
        return outputs.contains(output);
    }
}
