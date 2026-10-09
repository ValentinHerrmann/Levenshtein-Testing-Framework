package io.github.valentinherrmann.levenshtein.fixtures;

/** Classes standing in for student code in the framework's own tests. */
public final class Fixtures {
    private Fixtures() {
    }

    public interface Drivable { void drive(); }
    public interface Honkable { }
    public interface Electric extends Drivable { }

    public static class TwoInterfaces implements Drivable, Honkable {
        public void drive() { }
    }

    public static class IndirectInterface implements Electric {
        public void drive() { }
    }

    public record Point(int x, int y) { }
    public enum Color { RED, GREEN }

    public static class Base { }
    public static class NoSuper { }
    public static class Derived extends Base { }
    public static class DerivedTwice extends Derived { }

    public static class Typos {
        private double manufacturr;
        private double manufactur;
        private Integer count = 5;
        private static int counter;
        private String nothing;

        public double calculateCots() { return 1; }
        public int getMinSpeed() { return 10; }
        public int add(Integer a, Integer b) { return a + b; }
        public String getNothing() { return nothing; }
        public int divide(int a, int b) { return a / b; }
        public int total() { return 7; }
        public long scale(int factor) { return factor; }
        public boolean flag() { return true; }
        public void fail(String why) { throw new IllegalArgumentException(why); }
    }

    public static class Ctors {
        public final String how;
        protected Ctors(String how) { this.how = how; }
        private Ctors() { this("private"); }
        public Ctors(Integer value) { this("integer " + value); }
    }

    public abstract static class AbstractWithProtectedCtor {
        protected final String name;
        protected AbstractWithProtectedCtor(String name) { this.name = name; }
        public String getName() { return name; }
        public abstract double cost();
    }

    public static final class FinalCar {
        private final String brand;
        public FinalCar(String brand) { this.brand = brand; }
        public FinalCar() { this("default"); }
        public String getBrand() { return brand; }

        @Override
        public boolean equals(Object o) {
            return o != null && getClass() == o.getClass() && brand.equals(((FinalCar) o).brand);
        }

        @Override
        public int hashCode() { return brand.hashCode(); }
    }

    public static class ExplodingInit {
        public static final int VALUE;
        static {
            if (true) {
                throw new IllegalStateException("static initializer must not run during the structural check");
            }
            VALUE = 1;
        }
    }
}
