package io.github.pod32g.omni;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Marks a test method for {@link TestRunner}. Same name as JUnit's, so swapping is an import change. */
@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target(ElementType.METHOD)
@interface Test {
}
