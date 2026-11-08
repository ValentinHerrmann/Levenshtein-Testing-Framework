package io.github.valentinherrmann.levenshtein;

import static io.github.valentinherrmann.levenshtein.StructuralLevenshtein.DetailLevel.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;

/**
 * Tests of the generated structural tests in {@link StructuralLevenshtein}.
 */
class StructuralLevenshteinTest {

    /** A class with a constructor, an existing and a missing attribute, and a method. */
    static class Members extends TestClassWrapper<Object> {
        final ConstructorWrapper<Object> constructor = new ConstructorWrapper<>(this, "public");
        final AttributeWrapper<Object, Integer> publicField = new AttributeWrapper<>(this, "publicField", int.class, "public");
        final AttributeWrapper<Object, Integer> ghost = new AttributeWrapper<>(this, "ghostAttribute", int.class, "private");
        final MethodWrapper<Object, String> describe = new MethodWrapper<>(this, "describe", String.class, "public");

        Members() {
            super("BaseFixtures$Modifiers", PKG, "public", "static");
        }
    }

    /** A class whose members all exist exactly. */
    static class Complete extends TestClassWrapper<Object> {
        final AttributeWrapper<Object, Integer> publicField = new AttributeWrapper<>(this, "publicField", int.class, "public");

        Complete() {
            super("BaseFixtures$Modifiers", PKG, "public", "static");
        }
    }

    @AfterEach
    void resetSettings() {
        LevenshteinSettings.reset();
    }

    private static List<String> names(List<DynamicTest> tests) {
        return tests.stream().map(DynamicTest::getDisplayName).toList();
    }

    private static void execute(DynamicTest test) throws Throwable {
        test.getExecutable().execute();
    }

    @Test
    void oneTestForEverything() {
        List<DynamicTest> tests = StructuralLevenshtein.structuralTestFactory(ONE_FOR_EVERYTHING, new Members(), new Complete());
        assertThat(names(tests)).containsExactly("Structural[all]");
        assertThatThrownBy(() -> execute(tests.getFirst()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ghostAttribute")
                .hasMessageNotContaining("publicField");
    }

    @Test
    void oneTestPerMemberNamesEachMember() {
        List<DynamicTest> tests = StructuralLevenshtein.structuralTestFactory(ONE_PER_MEMBER, new Members());
        assertThat(names(tests)).containsExactly(
                "Class[BaseFixtures$Modifiers]",
                "Constructor[" + new Members().constructor.expectedToString() + "]",
                "Attribute[BaseFixtures$Modifiers.publicField]",
                "Attribute[BaseFixtures$Modifiers.ghostAttribute]",
                "Method[BaseFixtures$Modifiers.public String describe()]");
    }

    @Test
    void onlyTheTestOfTheMissingMemberFails() throws Throwable {
        List<DynamicTest> tests = StructuralLevenshtein.structuralTestFactory(ONE_PER_MEMBER, new Members());
        execute(tests.get(0));
        execute(tests.get(1));
        execute(tests.get(2));
        assertThatThrownBy(() -> execute(tests.get(3))).isInstanceOf(AssertionError.class);
        execute(tests.get(4));
    }

    @Test
    void duplicateGroupNamesAreNumbered() {
        List<DynamicTest> tests = StructuralLevenshtein.structuralTestFactory(ONE_PER_CLASS,
                new Complete(), new Complete(), new Complete());
        assertThat(names(tests)).containsExactly(
                "Structural[BaseFixtures$Modifiers]",
                "Structural[BaseFixtures$Modifiers] #2",
                "Structural[BaseFixtures$Modifiers] #3");
    }

    @Test
    void completeClassPasses() {
        for (StructuralLevenshtein.DetailLevel level : StructuralLevenshtein.DetailLevel.values()) {
            for (DynamicTest test : StructuralLevenshtein.structuralTestFactory(level, new Complete())) {
                assertThatCode(() -> execute(test)).as(level + " " + test.getDisplayName()).doesNotThrowAnyException();
            }
        }
    }

    @Test
    void templateReportsDeviationsAndMissingElements() {
        LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
        TestClassWrapper<Object> parent = TestClassWrapper.base("Modifiers", "public", "static");
        AttributeWrapper<Object, Integer> exact = new AttributeWrapper<>(parent, "publicField", int.class, "public");
        AttributeWrapper<Object, Integer> deviating = new AttributeWrapper<>(parent, "publicFeld", int.class, "public");
        AttributeWrapper<Object, Integer> missing = new AttributeWrapper<>(parent, "ghostAttribute", int.class, "public");
        assertThatCode(() -> StructuralLevenshtein.structuralTestTemplate(List.of(exact))).doesNotThrowAnyException();
        assertThatThrownBy(() -> StructuralLevenshtein.structuralTestTemplate(List.of(exact, deviating, missing)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("DEVIATION")
                .hasMessageContaining("publicFeld")
                .hasMessageContaining("MISSING")
                .hasMessageContaining("ghostAttribute");
    }
}
