package io.github.valentinherrmann.levenshtein.fixtures;

/**
 * Student code that must neither earn a pass it did not deserve nor break the report of a failure
 * ({@code RobustnessTest}).
 */
public final class RobustnessFixtures {

    private RobustnessFixtures() {
    }

    /** Outcomes that must not count as the expected exception when a test expects a supertype. */
    public static class Critical {
        public Critical() { }
        public Critical(String text) { throw new SecurityException("forbidden by the sandbox"); }
        public Critical(int depth) { throw new StackOverflowError(); }

        public void forbidden() { throw new SecurityException("forbidden by the sandbox"); }
        public void failAssertion() { throw new AssertionError("checked by Ares"); }
        public void overflow() { throw new StackOverflowError(); }
        public void outOfMemory() { throw new OutOfMemoryError("too much"); }
        public void boom() { throw new IllegalStateException("boom"); }
    }

    /** An object whose state every call changes. */
    public static class Counter {
        private int count;

        public Counter() { }
        public Counter(int start) { this.count = start; }

        public int increment() { return ++count; }
        public Counter self() { return this; }
    }

    /** An inner class (the student left out {@code static}) that changes its outer object. */
    public static class Outer {
        private int uses;

        public class Inner {
            public int use() { return ++uses; }
        }
    }

    /**
     * An attribute that is {@code static} although an instance attribute is expected, in a class whose static
     * initializer ends with an error of the virtual machine (thrown as it is, not as
     * {@link ExceptionInInitializerError}).
     */
    public static class OverflowingInit {
        private static int counter = overflow();

        private static int overflow() { throw new StackOverflowError(); }
    }

    /** A static attribute of a class that cannot be initialised; read by a test. */
    public static class BrokenRead {
        public static int value = 1;

        static {
            if (true) {
                throw new IllegalStateException("broken static");
            }
        }
    }

    /** A static attribute of a class that cannot be initialised; written by a test. */
    public static class BrokenWrite {
        public static int value = 1;

        static {
            if (true) {
                throw new IllegalStateException("broken static");
            }
        }
    }

    /** An exception whose {@code getMessage()} fails. */
    public static class BrokenMessage extends RuntimeException {
        @Override public String getMessage() { throw new IllegalStateException("no message"); }
    }

    /** An exception whose {@code getMessage()} does something Ares forbids. */
    public static class ForbiddenMessage extends RuntimeException {
        @Override public String getMessage() { throw new SecurityException("forbidden by the sandbox"); }
    }

    /** An object whose {@code toString()} fails. */
    public static class BrokenToString {
        @Override public String toString() { throw new IllegalStateException("no text"); }
    }

    /** An object whose {@code equals} fails. */
    public static class BrokenEquals {
        @Override public boolean equals(Object o) { throw new IllegalStateException("no equals"); }
        @Override public int hashCode() { return 1; }
    }

    /** An object whose {@code equals} does something Ares forbids. */
    public static class ForbiddenEquals {
        @Override public boolean equals(Object o) { throw new SecurityException("forbidden by the sandbox"); }
        @Override public int hashCode() { return 1; }
    }

    /** Methods whose exceptions and results are hard to show. */
    public static class Thrower {
        public void broken() { throw new BrokenMessage(); }
        public void forbiddenMessage() { throw new ForbiddenMessage(); }
        public void longMessage() { throw new IllegalStateException("x".repeat(5000)); }
        public Object weird() { return new BrokenToString(); }
    }

    /** A constructor that takes any object. */
    public static class TakesObject {
        public final Object value;

        public TakesObject(Object value) { this.value = value; }
    }
}
