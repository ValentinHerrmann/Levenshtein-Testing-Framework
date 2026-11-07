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

    /** Getters and setters, correct ones and typical student mistakes. */
    public static class GettersSetters {
        private double price;
        private int count;
        private String name;
        private String label = "start";
        private String title = "start";
        private String tag = "start";
        private int amount;
        private final int fixed = 3;
        private int other;
        private Object box;

        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }

        public int getCount() { return 42; }
        public void setCount(int count) { this.count = count; }

        public String getName() { return name; }
        public void setName(String name) { }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = java.util.Objects.requireNonNull(label); }

        public String getTitle() { return title; }
        public void setTitle(String title) { if (title != null) { this.title = title; } }

        public String getTag() { return tag; }
        public void setTag(String tag) { this.tag = tag; }

        public Integer getAmount() { return amount; }
        public void setAmount(Integer amount) { this.amount = amount; }

        public int getFixed() { return fixed; }

        public int getWrong() { return count; }
        public void setOther(int other) { this.other = other; }

        public Object getBox() { return box; }
        public void setBox(Object box) { this.box = box; }
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

    /** Attributes whose numeric type deviates from the expected one (#22). */
    public static class NumericDeviations {
        private float price;   // expected double
        private long count;    // expected int
        private short small;   // expected int
        private int whole;     // expected double
        private char letter;   // expected int

        public float getPrice() { return price; }
        public void setPrice(float price) { this.price = price; }

        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }

        public short getSmall() { return small; }
        public void setSmall(short small) { this.small = small; }

        public int getWhole() { return whole; }
        public void setWhole(int whole) { this.whole = whole; }

        public char getLetter() { return letter; }
        public void setLetter(char letter) { this.letter = letter; }
    }

    /** Array attributes (#25). */
    public static class ArrayAttributes {
        private boolean[] spotlights = new boolean[0];
        private String[] names;
        private boolean[] broken = {true};

        public boolean[] getSpotlights() { return spotlights.clone(); }
        public void setSpotlights(boolean[] spotlights) { this.spotlights = spotlights.clone(); }

        public String[] getNames() { return names; }
        public void setNames(String[] names) { this.names = names; }

        public boolean[] getBroken() { return new boolean[] {false}; }
        public void setBroken(boolean[] broken) { this.broken = broken; }

        private String text = "start";
        private int[] levels = {1};

        public String getText() { return text == null ? null : new String(text); }
        public void setText(String text) { this.text = text; }

        public int[] getLevels() { return levels; }
        public void setLevels(int[] levels) { if (levels.length > 0) { this.levels = levels; } }
    }

    /** Calls that throw for some inputs, and array parameters (#27, #28). */
    public static class Calls {
        public Calls() { }

        public Calls(String[] names) {
            if (names.length == 0) {
                throw new IllegalArgumentException("no names");
            }
        }

        public double divide(double a, double b) {
            if (b == 0) {
                throw new ArithmeticException("division by zero");
            }
            return a / b;
        }

        public int sum(java.util.List<Integer> values) { throw new IllegalStateException("not implemented"); }
        public int count(String[] names) { return names.length; }
        public int countFlags(boolean[] flags) { return flags.length; }
        public boolean isNull(String s) { return s == null; }
        public void boom() { throw new IllegalStateException("boom"); }
    }

    /** An abstract class whose constructor always throws (#27). */
    public abstract static class AbstractThrowingCtor {
        protected AbstractThrowingCtor(String name) { throw new IllegalStateException("broken " + name); }
    }

    /** A subclass without own attributes, constructors wrappers or methods (#26). */
    public static class NoOwnMembers extends Base { }

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
