package io.github.pod32g.omni;

import java.io.Serial;

/** A failure talking to the server: network, timeout, interruption, or an undecodable reply. */
public class OmniException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public OmniException(String message) {
        super(message);
    }

    public OmniException(String message, Throwable cause) {
        super(message, cause);
    }
}
