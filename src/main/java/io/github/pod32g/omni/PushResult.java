package io.github.pod32g.omni;

/** What the server appended for a push. */
public record PushResult(int samplesAppended, int seriesTouched) {
}
