package io.github.valentinherrmann.levenshtein.fixtures;

/** Classes standing in for student code in {@code ClassWrapperTest} and {@code ConstructorWrapperTest}. */
public final class ClassFixtures {
    private ClassFixtures() {
    }

    /** Target of the generated spellings: plural, one character too many, swapped characters. */
    public static class Gear { }

    /** Target of the added plural "s". */
    public static class Wheels { }

    /** Target of the "es" plural: neither "Boxe" nor "Boxs" exist. */
    public static class Box { }

    /** Two classes close to "Seax": "Seat" is closer (1 edit), "Sbat" sorts first alphabetically (2 edits). */
    public static class Seat { }
    public static class Sbat { }

    /** Inherits {@link Fixtures.Drivable} only through its superclass and has no own interfaces. */
    public static class InheritsInterface extends Fixtures.TwoInterfaces { }

    /** An interface extending another one, to print "interface ... extends ...". */
    public interface Steerable extends Fixtures.Drivable, Fixtures.Honkable { }

    /** Package-private, without any modifier. */
    static class Plain { }

    /** An abstract class with a no-argument constructor, instantiated through a generated subclass. */
    public abstract static class AbstractWithDefaultCtor {
        public final String origin;
        protected AbstractWithDefaultCtor() { this.origin = "default"; }
        protected AbstractWithDefaultCtor(String origin) { this.origin = origin; }
        public abstract int wheels();
    }

    /** An abstract class whose constructor rejects invalid values, as a constructor test would expect. */
    public abstract static class AbstractValidating {
        protected AbstractValidating(int wheels) {
            if (wheels < 0) {
                throw new IllegalArgumentException("negative: " + wheels);
            }
        }
    }

    /** A concrete class without a no-argument constructor. */
    public static class NoDefaultCtor {
        public NoDefaultCtor(String name) { }
    }

    /** A concrete class whose no-argument constructor throws. */
    public static class ThrowingDefaultCtor {
        public ThrowingDefaultCtor() { throw new IllegalStateException("not today"); }
    }

    /** Constructors throwing an {@link AssertionError} or a {@link VirtualMachineError}. */
    public static class CriticalCtors {
        public CriticalCtors() { throw new AssertionError("student assertion"); }
        public CriticalCtors(String text) { throw new AssertionError("student assertion " + text); }
        public CriticalCtors(int depth) { throw new StackOverflowError("too deep " + depth); }
        public CriticalCtors(long size) { throw new OutOfMemoryError("too big " + size); }
        public CriticalCtors(String text, int value) { }
    }

    /** Like {@link CriticalCtors}, but abstract (instantiated through a generated subclass). */
    public abstract static class AbstractCriticalCtors {
        protected AbstractCriticalCtors(String text) { throw new AssertionError("student assertion " + text); }
        protected AbstractCriticalCtors(int depth) { throw new StackOverflowError("too deep " + depth); }
    }

    /**
     * Several constructors that deviate from an expected {@code (int)}: the primitive/wrapper pair is the closest
     * one, although numeric deviations are declared before and after it.
     */
    public static class Overloads {
        public final String chosen;
        public Overloads(long value) { this.chosen = "long"; }
        public Overloads(Integer value) { this.chosen = "Integer"; }
        public Overloads(short value) { this.chosen = "short"; }
    }

    /** An exact {@code (int, String)} constructor and a deviating one for {@code (long)}. */
    public static class Garage {
        public final String chosen;
        public Garage(int places, String name) { this.chosen = name + places; }
        protected Garage(Integer places) { this.chosen = "Integer " + places; }
    }
}
