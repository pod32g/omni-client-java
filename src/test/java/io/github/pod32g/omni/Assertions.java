package io.github.pod32g.omni;

import java.util.Objects;

/**
 * The handful of assertions the tests use. Names and argument order match JUnit 5's
 * {@code org.junit.jupiter.api.Assertions}, so moving to JUnit later is mostly an import change.
 */
final class Assertions {
    private Assertions() {
    }

    @FunctionalInterface
    interface Executable {
        void execute() throws Throwable;
    }

    static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    static void assertEquals(double expected, double actual) {
        if (Double.compare(expected, actual) != 0) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    static void assertNull(Object actual) {
        if (actual != null) {
            throw new AssertionError("expected null but was <" + actual + ">");
        }
    }

    static void assertNotNull(Object actual) {
        if (actual == null) {
            throw new AssertionError("expected a value but was null");
        }
    }

    static AssertionError fail(String message) {
        throw new AssertionError(message);
    }

    static <T extends Throwable> T assertThrows(Class<T> expected, Executable action) {
        try {
            action.execute();
        } catch (Throwable t) {
            if (expected.isInstance(t)) {
                return expected.cast(t);
            }
            throw new AssertionError("expected " + expected.getSimpleName() + " but got " + t, t);
        }
        throw new AssertionError("expected " + expected.getSimpleName() + " but nothing was thrown");
    }
}
