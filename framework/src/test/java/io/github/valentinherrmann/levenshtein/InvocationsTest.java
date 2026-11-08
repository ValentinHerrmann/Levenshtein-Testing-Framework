package io.github.valentinherrmann.levenshtein;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.valentinherrmann.levenshtein.fixtures.CallFixtures;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The shared helpers for calls of student code: argument checks, snapshots, copies and failure messages.
 */
class InvocationsTest {

    @BeforeEach
    void english() {
        LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
    }

    @AfterEach
    void resetSettings() {
        LevenshteinSettings.reset();
    }

    @Test
    void criticalThrowablesAreRethrown() {
        AssertionError assertion = new AssertionError("a");
        SecurityException security = new SecurityException("s");
        OutOfMemoryError vmError = new OutOfMemoryError("m");
        assertThatThrownBy(() -> Invocations.rethrowIfCritical(assertion)).isSameAs(assertion);
        assertThatThrownBy(() -> Invocations.rethrowIfCritical(security)).isSameAs(security);
        assertThatThrownBy(() -> Invocations.rethrowIfCritical(vmError)).isSameAs(vmError);
        assertThatCode(() -> Invocations.rethrowIfCritical(new IllegalStateException())).doesNotThrowAnyException();
        assertThatCode(() -> Invocations.rethrowIfCritical(new Exception())).doesNotThrowAnyException();
    }

    @Test
    void describeThrowables() {
        assertThat(Invocations.describe(null)).isEqualTo("<none>");
        assertThat(Invocations.describe(new IllegalArgumentException("year < 0"))).isEqualTo("IllegalArgumentException: year < 0");
        assertThat(Invocations.describe(new IllegalArgumentException(" "))).isEqualTo("IllegalArgumentException");
        assertThat(Invocations.describe(new ExceptionInInitializerError(new IllegalStateException("static"))))
                .isEqualTo("ExceptionInInitializerError: IllegalStateException: static");
    }

    @Test
    void copyArraysCopiesNestedArraysUpToTheMaximumDepth() {
        int[] primitive = {1, 2};
        int[][] nested = {{3}};
        Object[] level1 = {new Object[]{new Object[]{new int[]{4}}}}; // levels 1 to 4
        String text = "kept";
        Object[] copy = Invocations.copyArrays(new Object[]{primitive, nested, level1, text, null});

        assertThat(copy[0]).isNotSameAs(primitive).isEqualTo(primitive);
        assertThat(copy[1]).isNotSameAs(nested);
        assertThat(((int[][]) copy[1])[0]).isNotSameAs(nested[0]).containsExactly(3);
        assertThat(copy[3]).isSameAs(text);
        assertThat(copy[4]).isNull();

        Object[] c1 = (Object[]) copy[2];
        Object[] c2 = (Object[]) c1[0];
        Object[] c3 = (Object[]) c2[0];
        Object[] o2 = (Object[]) level1[0];
        Object[] o3 = (Object[]) o2[0];
        assertThat(c1).isNotSameAs(level1);
        assertThat(c2).isNotSameAs(o2);
        assertThat(c3).isNotSameAs(o3);
        assertThat(c3[0]).isSameAs(o3[0]); // beyond the maximum depth: kept as it is

        // a change of the original does not change the copy
        primitive[0] = 9;
        nested[0][0] = 9;
        assertThat((int[]) copy[0]).containsExactly(1, 2);
        assertThat(((int[][]) copy[1])[0]).containsExactly(3);
    }

    @Test
    void copyArraysEndsForAnArrayThatContainsItself() {
        Object[] self = new Object[1];
        self[0] = self;
        Object[] copy = (Object[]) Invocations.copyArrays(new Object[]{self})[0];
        assertThat(copy).isNotSameAs(self);
    }

    @Test
    void arrayArgumentChecks() {
        Class<?>[] types = {int.class, String[].class};
        assertThatThrownBy(() -> Invocations.checkArguments(types, new Object[]{1, "a"}, "m(int, String[])"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("m(int, String[]) expects an array of type String[] as argument 2, but got \"a\" (String).");
        assertThat(Invocations.checkArguments(types, new Object[]{1, null}, "m")).containsExactly(1, null);
        String[] names = {"a"};
        assertThat(Invocations.checkArguments(types, new Object[]{1, names}, "m")).containsExactly(1, names);
    }

    @Test
    void hintOnlyForObjectArrays() {
        assertThatThrownBy(() -> Invocations.checkArguments(new Class<?>[]{int[].class}, new Object[0], "m"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("(Object) array");
        assertThatThrownBy(() -> Invocations.checkArguments(new Class<?>[]{int.class}, new Object[0], "m"))
                .hasMessageNotContaining("(Object) array");
        assertThatThrownBy(() -> Invocations.checkArguments(new Class<?>[]{Integer[].class}, new Object[0], "m"))
                .hasMessageContaining("(Object) array");
    }

    @Test
    void numericArgumentsAreConvertedOnlyWhenNeeded() {
        Object[] args = Invocations.checkArguments(new Class<?>[]{long.class, double.class, int.class, String.class, Object.class},
                new Object[]{3, 'A', 'B', "7", 5}, "m");
        assertThat(args).containsExactly(3L, 65.0, 66, "7", 5);
        assertThat(args[0]).isInstanceOf(Long.class);
        assertThat(args[4]).isInstanceOf(Integer.class);
        // a value that is no number is left to Ares
        assertThat(Invocations.checkArguments(new Class<?>[]{int.class}, new Object[]{"x"}, "m")).containsExactly("x");
        assertThat(Invocations.checkArguments(new Class<?>[]{String.class}, new Object[]{5}, "m")).containsExactly(5);
        // the arguments passed are not changed
        Object[] passed = {3};
        Invocations.checkArguments(new Class<?>[]{long.class}, passed, "m");
        assertThat(passed).containsExactly(3);
    }

    @Test
    void nullArgumentsAreNormalised() {
        assertThat(Invocations.normalizeNull(new Class<?>[]{String.class}, null)).containsExactly((Object) null);
        assertThat(Invocations.normalizeNull(new Class<?>[0], null)).isEmpty();
        assertThat(Invocations.normalizeNull(new Class<?>[]{int.class, int.class}, null)).isEmpty();
        Object[] args = {1};
        assertThat(Invocations.normalizeNull(new Class<?>[]{int.class}, args)).isSameAs(args);
    }

    @Test
    void snapshotKeepsArraysAndJdkCollectionsAsPassed() {
        int[] values = {3, 1, 2};
        List<Integer> list = new ArrayList<>(List.of(1, 2));
        CallFixtures.StudentCollection own = new CallFixtures.StudentCollection(new ArrayList<>(List.of(4)));
        Object[] shown = Invocations.snapshotArguments(new Object[]{values, list, own, "text", null});
        values[0] = 9;
        list.clear();
        assertThat(shown[0]).isEqualTo(new Invocations.Snapshot(List.of(3, 1, 2), 3));
        assertThat(shown[1]).isEqualTo(new Invocations.Snapshot(List.of(1, 2), 2));
        assertThat(shown[2]).isSameAs(own); // not iterated before the call
        assertThat(own.iterations).isZero();
        assertThat(shown[3]).isEqualTo("text");
        assertThat(shown[4]).isNull();
        assertThat(Invocations.describeArguments(shown)).isEqualTo("[3, 1, 2], [1, 2], [4], \"text\", null");
    }

    @Test
    void snapshotOfLongCollectionKeepsTheFirstElementsAndTheSize() {
        List<Integer> values = new ArrayList<>(java.util.stream.IntStream.range(0, 25).boxed().toList());
        Invocations.Snapshot s = (Invocations.Snapshot) Invocations.snapshotArguments(new Object[]{values})[0];
        assertThat(s.head()).hasSize(Invocations.MAX_ELEMENTS);
        assertThat(s.size()).isEqualTo(25);
    }

    @Test
    void snapshotOfNestedValuesEndsAtTheMaximumDepth() {
        Object[] inner = {new int[]{1}};
        Object[] value = {List.of((Object) inner)}; // array, list, array, array
        Invocations.Snapshot s0 = (Invocations.Snapshot) Invocations.snapshotArguments(new Object[]{value})[0];
        Invocations.Snapshot s1 = (Invocations.Snapshot) s0.head().get(0);
        Invocations.Snapshot s2 = (Invocations.Snapshot) s1.head().get(0);
        assertThat(s2.head().get(0)).isInstanceOf(int[].class); // kept as it is
        assertThat(Invocations.describeArguments(new Object[]{s0})).isEqualTo("[[[[…]]]]");

        Object[] self = new Object[1];
        self[0] = self;
        assertThat(Invocations.describeArguments(Invocations.snapshotArguments(new Object[]{self}))).isEqualTo("[[[[…]]]]");
    }

    @Test
    void snapshotOfAFailingCollectionKeepsTheCollection() {
        CallFixtures.BrokenCollection broken = new CallFixtures.BrokenCollection(new IllegalStateException("no"));
        java.util.Collection<Integer> failing = java.util.Collections.unmodifiableCollection(broken);
        assertThat(Invocations.snapshotArguments(new Object[]{failing})[0]).isSameAs(failing);
    }

    @Test
    void containersBeyondTheMaximumDepthAreShortened() {
        assertThat(Invocations.describeValue(new int[][][][]{{{{1}}}})).isEqualTo("[[[[…]]]]");
        assertThat(Invocations.describeValue(List.of(List.of(List.of(List.of(1)))))).isEqualTo("[[[[…]]]]");
        assertThat(Invocations.describeValue(new Object[]{new Object[]{new Object[]{new Invocations.Snapshot(List.of(1), 1)}}}))
                .isEqualTo("[[[[…]]]]");
        assertThat(Invocations.describeValue(new Object[]{new Object[]{new Object[]{"deep", 'c', 3}}}))
                .isEqualTo("[[[\"deep\", 'c', 3]]]");
    }

    @Test
    void valuesAreCutAfterTheMaximumLength() {
        String exact = "x".repeat(Invocations.MAX_VALUE_LENGTH);
        assertThat(Invocations.describeValue(exact)).isEqualTo("\"" + exact + "\"");
        assertThat(Invocations.describeValue(exact + "y")).isEqualTo("\"" + exact + "…\"");
    }

    @Test
    void failingCollectionIsShownWithItsToString() {
        assertThat(Invocations.describeValue(new CallFixtures.BrokenCollection(new IllegalStateException("no"))))
                .isEqualTo("broken collection");
        CallFixtures.BrokenCollection forbidden = new CallFixtures.BrokenCollection(new SecurityException("sandbox"));
        assertThatThrownBy(() -> Invocations.describeValue(forbidden))
                .isInstanceOf(SecurityException.class)
                .hasMessage("sandbox");
    }

    @Test
    void failingToStringIsShownReadablyUnlessItIsAnErrorOfTheVirtualMachine() {
        assertThat(Invocations.describeValue(new CallFixtures.FailingToString()))
                .isEqualTo("FailingToString (toString() threw IllegalStateException)");
        assertThatThrownBy(() -> Invocations.describeValue(new CallFixtures.FatalToString()))
                .isInstanceOf(OutOfMemoryError.class);
    }

    @Test
    void argumentLineOnlyForArguments() {
        assertThat(Invocations.withArguments("failed", null)).isEqualTo("failed");
        assertThat(Invocations.withArguments("failed", new Object[0])).isEqualTo("failed");
        assertThat(Invocations.withArguments("failed", new Object[]{1, "a"})).isEqualTo("failed\nArguments: (1, \"a\")");
        assertThat(Invocations.describeArguments(null)).isEqualTo("null");
    }

    @Test
    void contextIsPutInFront() {
        assertThat(Invocations.withContext(null, "failed")).isEqualTo("failed");
        assertThat(Invocations.withContext(() -> "  case 1 ", "failed")).isEqualTo("case 1\nfailed");
        assertThat(Invocations.withContext(() -> null, "failed")).isEqualTo("failed");
        assertThat(Invocations.withContext(() -> { throw new IllegalStateException(); }, "failed")).isEqualTo("failed");
    }

    @Test
    void callDetailsAreAddedOnlyIfThereAreAny() {
        AssertionError e = new AssertionError("failed");
        assertThat(Invocations.withCallDetails(e, null, null)).isSameAs(e);
        assertThat(Invocations.withCallDetails(e, null, new Object[0])).isSameAs(e);

        AssertionError withArgs = Invocations.withCallDetails(e, null, new Object[]{1});
        assertThat(withArgs).hasMessage("failed\nArguments: (1)").hasCause(e);
        AssertionError withContext = Invocations.withCallDetails(e, () -> "case 1", null);
        assertThat(withContext).hasMessage("case 1\nfailed").hasCause(e);
        AssertionError withBoth = Invocations.withCallDetails(e, () -> "case 1", new Object[]{1});
        assertThat(withBoth).hasMessage("case 1\nfailed\nArguments: (1)");

        AssertionError noMessage = new AssertionError();
        assertThat(Invocations.withCallDetails(noMessage, null, new Object[]{1})).hasMessage("AssertionError\nArguments: (1)");
    }
}
