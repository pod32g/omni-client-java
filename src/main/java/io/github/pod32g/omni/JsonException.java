package io.github.pod32g.omni;

import java.io.Serial;

/** Malformed JSON, or JSON of the wrong shape. Internal: the client rethrows it as OmniException. */
final class JsonException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    JsonException(String message) {
        super(message);
    }
}
