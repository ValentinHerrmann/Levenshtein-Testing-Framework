package io.github.valentinherrmann.levenshtein.fixtures;

import java.util.ArrayList;
import java.util.List;

/** Classes standing in for student code in the tests of the base classes (Utils, wrappers, getter/setter test). */
public final class BaseFixtures {
    private BaseFixtures() {
    }

    /** Fields with all kinds of modifiers, for the modifier grading. */
    public static class Modifiers {
        int packagePrivate;
        public int publicField;
        protected int protectedField;
        private int privateField;
        public static int staticField;
        public final int finalField = 1;
        private String text = "start";
        private Object box;

        public String describe() { return text; }
    }

    /** Interface constants are implicitly {@code public static final}. */
    public interface Constants {
        int LIMIT = 10;
    }

    /** Static attributes of this class can be read and written without an instance. */
    public static class NotInstantiable {
        private static int counter = 5;
        private int value;

        public NotInstantiable() {
            throw new IllegalStateException("must not be instantiated");
        }
    }

    /** An inner class: its reference to the enclosing instance is a synthetic field ({@code this$0}). */
    public class Inner {
        public int outerHash() {
            return BaseFixtures.this.hashCode();
        }
    }

    /** Several similar attribute names, for claiming. */
    public static class Similar {
        private int counter;
        private int countr;
    }

    /** Getters and setters whose types deviate from the expected ones or that misbehave. */
    public static class GettersSetters {
        /** The values passed to the recording setters, in order. */
        public static final List<Object> SET = new ArrayList<>();

        // expected int: the field is a float, getter and setter use char
        private float level;
        public char getLevel() { return (char) level; }
        public void setLevel(char level) { SET.add(level); this.level = level; }

        // expected Integer: the student uses Long throughout
        private Long total;
        public Long getTotal() { return total; }
        public void setTotal(Long total) { SET.add(total); this.total = total; }

        // expected int: only the getter deviates (char)
        private int code;
        public char getCode() { return (char) code; }
        public void setCode(int code) { this.code = code; }

        // expected int: only the setter deviates (char)
        private int key;
        public int getKey() { return key; }
        public void setKey(char key) { this.key = key; }

        // expected Integer: only the field is primitive
        private int count;
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }

        // expected Integer: only the getter is primitive
        private Integer amount = 0;
        public int getAmount() { return amount; }
        public void setAmount(Integer amount) { this.amount = amount; }

        // expected Integer: only the setter is primitive
        private Integer number = 0;
        public Integer getNumber() { return number; }
        public void setNumber(int number) { this.number = number; }

        // the setter always throws
        private int broken;
        public int getBroken() { return broken; }
        public void setBroken(int broken) { throw new IllegalStateException("nope"); }

        // the setter rejects negative values with an assertion of the student
        private int checked = 1;
        public int getChecked() { return checked; }
        public void setChecked(int checked) { if (checked < 0) throw new AssertionError("no negatives"); this.checked = checked; }

        // the getter always throws
        private int fragile = 3;
        public int getFragile() { throw new UnsupportedOperationException("later"); }
        public void setFragile(int fragile) { this.fragile = fragile; }

        // the setter stores every value, but throws for negative ones
        private int positive = 1;
        public int getPositive() { return positive; }
        public void setPositive(int positive) {
            this.positive = positive;
            if (positive < 0) {
                throw new IllegalArgumentException("negative");
            }
        }

        // the getter's name deviates and it is hard-coded
        private int size = 7;
        public int getSizee() { return 42; }

        // a final attribute with a wrong getter
        private final int limit = 5;
        public int getLimit() { return 6; }

        // a correct char getter and setter
        private char initial = 'a';
        public char getInitial() { return initial; }
        public void setInitial(char initial) { this.initial = initial; }

        // a correct double getter and setter
        private double price;
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }

        // a correct int getter and setter
        private int plain;
        public int getPlain() { return plain; }
        public void setPlain(int plain) { this.plain = plain; }

        // a label that must not be null
        private String label = "start";
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = java.util.Objects.requireNonNull(label); }
    }

    /** An enum with more constants than generated sample values. */
    public enum Weekday { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

    /** An enum without constants. */
    public enum Empty { }
}
