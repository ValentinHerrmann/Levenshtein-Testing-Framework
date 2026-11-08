package io.github.valentinherrmann.levenshtein.fixtures;

/** Fixtures for unexpected, missing, optional and implicit modifiers (#35). */
@SuppressWarnings("unused")
public final class ModifierFixtures {

    public ModifierFixtures() {
    }

    /** SE01E03: the spotlights should belong to each stage, but are shared by all stages. */
    public static class FestivalStage {
        private static boolean[] spotlights = new boolean[3];
        private static int[][] grid = {{0, 0}, {0, 0}};

        public void switchOn(int i) {
            spotlights[i] = true;
            grid[0][i] = 1;
        }

        public boolean isOn(int i) {
            return spotlights[i];
        }
    }

    /** Counts hits in a static array although every object should count on its own. */
    public static class ResetStage {
        private static int[] hits = new int[1];

        public void hit() {
            hits[0]++;
        }

        public int hits() {
            return hits[0];
        }
    }

    /** SE01E02: a utility class that may be final. */
    public static final class SetlistTools {
        private SetlistTools() {
        }
    }

    public static class SetlistToolsNotFinal {
    }

    public abstract static class AbstractButShouldNotBe {
        public int value() {
            return 7;
        }
    }

    public static class Extras {
        public final int finalField = 1;
        public transient int transientField;
        public volatile int volatileField;
        public int instanceField = 5;

        public synchronized void sync() {
        }

        public static int staticMethod() {
            return 3;
        }

        public int instanceMethod() {
            return 4;
        }

        public final void finalMethod() {
        }

        public void varargs(int... xs) {
        }
    }

    public interface Api {
        int LIMIT = 3;

        void run();

        default int twice() {
            return 2;
        }

        static int one() {
            return 1;
        }

        class InInterface {
        }
    }

    public enum Plain { A, B }

    public enum WithBodies {
        A {
            @Override
            int v() {
                return 1;
            }
        };

        abstract int v();
    }

    public record Point(int x, int y) {
    }

    /** An inner class where a static nested class is expected. */
    public class Inner {
        private final int v;

        public Inner(int v) {
            this.v = v;
        }

        public int v() {
            return v;
        }
    }

    public class InnerNoArg {
        public int v() {
            return 9;
        }
    }

    public abstract class AbstractInner {
        public int v() {
            return 11;
        }
    }
}
