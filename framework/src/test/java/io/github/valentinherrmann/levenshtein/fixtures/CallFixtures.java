package io.github.valentinherrmann.levenshtein.fixtures;

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.List;

/** Classes standing in for student code in the tests of method lookup, calls and mocks. */
public final class CallFixtures {
    private CallFixtures() {
    }

    /** Candidates for the fuzzy lookup of methods. */
    public static class Lookup {
        public int computeTotal() { return 1; }
        public int computeTotaal() { return 2; }   // further from "computeTotalz", but first by name

        public int calculateb(int x) { return 1; }       // identical parameter type
        public int calculatea(Integer x) { return 2; }   // boxed parameter type, first by name

        public int measureb(Integer x) { return 1; }     // boxed parameter type
        public int measurea(long x) { return 2; }        // other numeric type, first by name

        public double calculateCots() { return 3; }      // misspelt, but the right parameters
        public double calculateCost(int x) { return 4; } // the right name, but other parameters
    }

    /** A generic interface, so the class has a bridge method {@code compareTo(Object)}. */
    public static class Ranked implements Comparable<Ranked> {
        @Override
        public int compareTo(Ranked o) { return 0; }
    }

    /** Calls with various outcomes. */
    public static class Outcomes {
        public Outcomes() { throw new IllegalStateException("broken constructor"); }

        public static int answer() { return 42; }
        public long big() { return 5L; }
        public int none() { return 7; }
        public int pair(int a, int b) { return a + b; }
        public int countOrMinusOne(int[] values) { return values == null ? -1 : values.length; }
        public void forbidden() { throw new SecurityException("forbidden by the sandbox"); }
        public void outOfMemory() { throw new OutOfMemoryError("too much"); }
        public void failAssertion() { throw new AssertionError("checked by Ares"); }
        public void boom() { throw new IllegalStateException("boom"); }
        public void clearAndFail(java.util.Collection<Integer> values) {
            values.clear();
            throw new IllegalStateException("cleared");
        }
    }

    /** A method that a subclass redeclares as {@code final}. */
    public static class Parent {
        public int value() { return 1; }
    }

    public static class FinalChild extends Parent {
        @Override
        public final int value() { return 2; }
    }

    /** All kinds of return types, and overloads named like the methods of {@link Object}. */
    public static class Defaults {
        public boolean flag() { return true; }
        public char letter() { return 'x'; }
        public byte tiny() { return 1; }
        public short small() { return 1; }
        public int whole() { return 1; }
        public long large() { return 1; }
        public float single() { return 1; }
        public double precise() { return 1; }
        public String text() { return "real"; }
        public void nothing() { }

        public boolean equals(Object a, Object b) { return true; }
        public boolean equals(Defaults other) { return true; }
        public int hashCode(int seed) { return 5; }
        public String toString(int radix) { return "real"; }
    }

    /** Default and abstract methods of interfaces. */
    public interface Greeter {
        default String hello() { return "hello " + name(); }
        String name();
    }

    public interface LoudGreeter extends Greeter { }

    public abstract static class PoliteGreeter implements LoudGreeter { }

    /** Methods that can or cannot be overridden. */
    public static class Secretive {
        private int secret() { return 1; }
        public String describe() { return "real"; }
        public void log(String message) { }
        public String echo(String message) { return message; }
        public int scale(int x) { return x; }
    }

    /** A package-private method that a subclass in another package cannot override. */
    public static class ForeignBase {
        int secret() { return 1; }
        protected int guarded() { return 1; }
        public int open() { return secret() + guarded(); }
    }

    /** A class that is not public. */
    static class Hidden {
        public int value() { return 1; }
    }

    /** A collection of a student class: it must not be iterated before the call. */
    public static class StudentCollection extends AbstractCollection<Integer> {
        public int iterations;
        private final List<Integer> values;

        public StudentCollection(List<Integer> values) { this.values = values; }

        @Override
        public Iterator<Integer> iterator() {
            iterations++;
            return values.iterator();
        }

        @Override
        public int size() { return values.size(); }
    }

    /** A collection whose iteration fails; shown with its {@code toString()} instead. */
    public static class BrokenCollection extends AbstractCollection<Integer> {
        private final RuntimeException failure;

        public BrokenCollection(RuntimeException failure) { this.failure = failure; }

        @Override
        public Iterator<Integer> iterator() { throw failure; }

        @Override
        public int size() { return 1; }

        @Override
        public String toString() { return "broken collection"; }
    }

    /** A {@code toString()} that fails with an error of the virtual machine. */
    public static class FatalToString {
        @Override
        public String toString() { throw new OutOfMemoryError("toString"); }
    }

    /** A {@code toString()} that fails with an ordinary exception. */
    public static class FailingToString {
        @Override
        public String toString() { throw new IllegalStateException("toString"); }
    }
}
