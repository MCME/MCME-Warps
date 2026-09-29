package com.mcmiddleearth.warps.core.messageprotocols;

/**
 * Thrown when an incoming plugin message cannot be trusted: wrong protocol version, unknown opcode,
 * a truncated body, or trailing bytes. Callers must reject the message instead of acting on
 * half-parsed or mis-aligned data.
 * <p>
 * Checked on purpose: the proxy and backend jars are deployed independently, so version skew is a
 * normal operational state. Forcing every {@code read} call site to handle this exception is how we
 * turn "silently teleport to garbage coordinates" into "log it and drop the message" (root cause #2).
 */
public class MalformedMessageException extends Exception {
    public MalformedMessageException(String message) {
        super(message);
    }

    public MalformedMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
