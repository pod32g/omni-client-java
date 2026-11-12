package io.github.pod32g.omni;

/** The shape of a query result. */
public enum ResultType {
    VECTOR("vector"),
    MATRIX("matrix"),
    SCALAR("scalar");

    private final String wireName;

    ResultType(String wireName) {
        this.wireName = wireName;
    }

    /** The name the API uses, e.g. {@code "vector"}. */
    public String wireName() {
        return wireName;
    }

    /** The type with this wire name, or null when the name is unknown. */
    static ResultType fromWireName(String name) {
        for (ResultType t : values()) {
            if (t.wireName.equals(name)) {
                return t;
            }
        }
        return null;
    }
}
