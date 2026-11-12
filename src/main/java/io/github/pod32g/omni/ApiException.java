package io.github.pod32g.omni;

import java.io.Serial;

/**
 * Thrown when the server replies with a {@code {status:"error"}} envelope or a non-2xx status.
 */
public final class ApiException extends OmniException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final String type;
    private final String serverMessage;

    public ApiException(int statusCode, String type, String serverMessage) {
        super(describe(statusCode, type, serverMessage));
        this.statusCode = statusCode;
        this.type = type == null ? "" : type;
        this.serverMessage = serverMessage == null ? "" : serverMessage;
    }

    private static String describe(int statusCode, String type, String serverMessage) {
        if (type != null && !type.isEmpty()) {
            return "omni: api error (" + type + "): " + serverMessage;
        }
        return "omni: api error (HTTP " + statusCode + "): " + serverMessage;
    }

    /** The HTTP status code of the reply. */
    public int statusCode() {
        return statusCode;
    }

    /** The server's errorType, e.g. {@code "bad_data"}; empty when the reply had none. */
    public String type() {
        return type;
    }

    /** The server's error text (or a body snippet / status text for a non-envelope reply). */
    public String serverMessage() {
        return serverMessage;
    }
}
