package online.davisfamily.warehouse.sim.dsp.io;

import java.util.List;

/** Immutable outcome of loading independently supplied 12N documents. */
public record TwelveNLoadResult(
        List<TwelveNInputMessage> messages,
        List<TwelveNRejectedInputMessage> rejectedMessages) {

    public TwelveNLoadResult {
        if (messages == null || messages.stream().anyMatch(message -> message == null)) {
            throw new IllegalArgumentException("messages must not be null or contain null");
        }
        if (rejectedMessages == null
                || rejectedMessages.stream().anyMatch(message -> message == null)) {
            throw new IllegalArgumentException(
                    "rejectedMessages must not be null or contain null");
        }
        messages = List.copyOf(messages);
        rejectedMessages = List.copyOf(rejectedMessages);
    }

    public List<TwelveNInputMessage> loadedMessages() {
        return messages;
    }

    public List<TwelveNInputMessage> successfulMessages() {
        return messages;
    }
}
