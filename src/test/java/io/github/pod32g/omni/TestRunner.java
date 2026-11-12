package io.github.pod32g.omni;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * A tiny stand-in for JUnit, so the project builds and tests with nothing but a JDK. It runs every
 * {@link Test} method of the classes named on the command line and exits non-zero on any failure.
 */
public final class TestRunner {
    private TestRunner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: TestRunner <test class>...");
            System.exit(2);
        }
        int passed = 0;
        List<String> failed = new ArrayList<>();
        for (String className : args) {
            Class<?> testClass = Class.forName(className);
            Method[] methods = testClass.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName));
            for (Method method : methods) {
                if (!method.isAnnotationPresent(Test.class)) {
                    continue;
                }
                String id = testClass.getSimpleName() + "." + method.getName();
                try {
                    Object instance = testClass.getDeclaredConstructor().newInstance();
                    method.invoke(instance);
                    passed++;
                    System.out.println("  ok    " + id);
                } catch (InvocationTargetException e) {
                    failed.add(id);
                    Throwable cause = e.getCause();
                    System.out.println("  FAIL  " + id);
                    System.out.println("        " + cause);
                    StackTraceElement where = firstFrameIn(cause, testClass);
                    if (where != null) {
                        System.out.println("        at " + where);
                    }
                }
            }
        }
        System.out.printf("%n%d passed, %d failed%n", passed, failed.size());
        System.exit(failed.isEmpty() ? 0 : 1);
    }

    private static StackTraceElement firstFrameIn(Throwable t, Class<?> testClass) {
        for (StackTraceElement frame : t.getStackTrace()) {
            if (frame.getClassName().startsWith(testClass.getName())) {
                return frame;
            }
        }
        return null;
    }
}
