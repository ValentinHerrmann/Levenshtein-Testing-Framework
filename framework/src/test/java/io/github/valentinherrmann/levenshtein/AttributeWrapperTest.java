package io.github.valentinherrmann.levenshtein;

import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.valentinherrmann.levenshtein.fixtures.BaseFixtures;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link AttributeWrapper} and the modifier grading of {@link Wrapper}.
 */
class AttributeWrapperTest {

    private final TestClassWrapper<Object> modifiers = TestClassWrapper.base("Modifiers", "public", "static");

    private <V> AttributeWrapper<Object, V> attribute(ClassWrapper<Object> parent, String name, Class<V> type, String... mods) {
        return new AttributeWrapper<>(parent, name, type, mods);
    }

    @AfterEach
    void resetSettings() {
        LevenshteinSettings.reset();
    }

    @Nested
    class Modifiers { // docs/matching.md, section "Modifiers"

        private WrapperProperty.Existence grade(String name, String... mods) {
            return attribute(modifiers, name, int.class, mods).getOverallExistence();
        }

        @Test
        void noExpectedModifierMeansPackagePrivate() {
            assertThat(grade("packagePrivate")).isEqualTo(EXACT);
            assertThat(grade("publicField")).isEqualTo(DEVIATES);
            assertThat(grade("protectedField")).isEqualTo(DEVIATES);
            assertThat(grade("privateField")).isEqualTo(DEVIATES);
        }

        @Test
        void allExpectedModifiersPresentIsExact() {
            assertThat(grade("publicField", "public")).isEqualTo(EXACT);
            assertThat(grade("protectedField", "protected")).isEqualTo(EXACT);
            assertThat(grade("privateField", "private")).isEqualTo(EXACT);
            assertThat(grade("staticField", "public static")).isEqualTo(EXACT);
            assertThat(grade("finalField", "public", "final")).isEqualTo(EXACT);
        }

        @Test
        void missingModifiersDeviate() {
            // a missing static is a deviation since #35: the attribute is used on the default object
            assertThat(grade("publicField", "public static")).isEqualTo(DEVIATES);
            assertThat(grade("publicField", "public final")).isEqualTo(DEVIATES);
            assertThat(grade("privateField", "public")).isEqualTo(DEVIATES);
            assertThat(grade("publicField", "private")).isEqualTo(DEVIATES);
        }

        @Test
        void unexpectedStaticDeviatesForClassesOnly() {
            assertThat(grade("staticField", "public")).isEqualTo(DEVIATES);
            TestClassWrapper<Object> constants = TestClassWrapper.base("Constants", "public static abstract interface");
            assertThat(attribute(constants, "LIMIT", int.class, "public final").getOverallExistence()).isEqualTo(EXACT);
        }

        @Test
        void actualModifiersAreReported() {
            assertThat(attribute(modifiers, "publicField", int.class, "public").getActualModifiers()).isEqualTo("public");
            assertThat(attribute(modifiers, "staticField", int.class, "public").getActualModifiers()).isEqualTo("public static");
            assertThat(attribute(modifiers, "packagePrivate", int.class).getActualModifiers()).isEmpty();
            assertThat(attribute(modifiers, "ghostAttribute", int.class).getActualModifiers()).isNull();
        }

        @Test
        void parentOfAClassWrapperIsTheClassWrapperItself() {
            assertThat(modifiers.getParentClassWrapper()).isSameAs(modifiers);
            assertThat(attribute(modifiers, "publicField", int.class).getParentClassWrapper()).isSameAs(modifiers);
        }

        @Test
        void expectedInterfaceIsShownAsInterface() {
            TestClassWrapper<Object> constants = TestClassWrapper.base("Constants", "public static abstract interface");
            assertThat(constants.expectedToString()).isEqualTo("public static abstract interface BaseFixtures$Constants");
            assertThat(modifiers.expectedToString()).isEqualTo("public static class BaseFixtures$Modifiers");
        }
    }

    @Nested
    class Lookup {

        @Test
        void typeAndFinalnessAreAvailableWithoutPriorCheck() {
            assertThat(attribute(modifiers, "text", String.class, "private").actualFieldType()).isEqualTo(String.class);
            assertThat(attribute(modifiers, "texts", Object.class, "private").actualFieldType()).isEqualTo(String.class);
            assertThat(attribute(modifiers, "ghostAttribute", int.class).actualFieldType()).isNull();
            assertThat(attribute(modifiers, "finalField", int.class, "public final").isFinal()).isTrue();
            assertThat(attribute(modifiers, "publicField", int.class, "public").isFinal()).isFalse();
            assertThat(attribute(modifiers, "ghostAttribute", int.class).isFinal()).isFalse();
        }

        @Test
        void tooDifferentNameIsMissing() {
            AttributeWrapper<Object, Integer> ghost = attribute(modifiers, "ghostAttribute", int.class, "private");
            assertThat(ghost.getOverallExistence()).isEqualTo(MISSING);
            assertThat(ghost.getActualName()).isNull();
            assertThat(ghost.actualToString()).isEqualTo("<missing>");
        }

        @Test
        void attributeOfAMissingClassIsMissing() {
            TestClassWrapper<Object> missing = TestClassWrapper.base("DoesNotExistAtAll", "public");
            AttributeWrapper<Object, Integer> a = attribute(missing, "value", int.class, "private");
            assertThat(a.getOverallExistence()).isEqualTo(MISSING);
            assertThat(a.actualFieldType()).isNull();
            assertThatCode(a::toString).doesNotThrowAnyException();
        }

        @Test
        void deviatingNameIsFoundAndShown() {
            AttributeWrapper<Object, Integer> a = attribute(modifiers, "privatField", int.class, "private");
            assertThat(a.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(a.getActualName()).isEqualTo("privateField");
            assertThat(a.expectedToString()).isEqualTo("private int privatField");
            assertThat(a.actualToString()).isEqualTo("private int privateField");
        }

        @Test
        void signaturesAreShownWithoutSurplusSpaces() { // also without a prior lookup
            assertThat(attribute(modifiers, "packagePrivate", int.class).expectedToString()).isEqualTo("int packagePrivate");
            assertThat(attribute(modifiers, "packagePrivate", int.class).actualToString()).isEqualTo("int packagePrivate");
            assertThat(attribute(modifiers, "texts", null, "private").expectedToString()).isEqualTo("private <missing> texts");
        }

        @Test
        void deviatingTypeIsShown() {
            // the student's type is a supertype of the expected one
            AttributeWrapper<Object, String> a = attribute(modifiers, "box", String.class, "private");
            assertThat(a.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(a.actualToString()).isEqualTo("private Object box");
            // a subtype is no deviation
            assertThat(attribute(modifiers, "text", Object.class, "private").getOverallExistence()).isEqualTo(MISSING);
        }

        @Test
        void wrapperToStringDependsOnTheState() {
            LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
            assertThat(attribute(modifiers, "publicField", int.class, "public")).hasToString("public int publicField");
            assertThat(attribute(modifiers, "publicFeld", int.class, "public").toString())
                    .contains("DEVIATION in BaseFixtures$Modifiers")
                    .contains("public int publicFeld")
                    .contains("public int publicField");
            assertThat(attribute(modifiers, "ghostAttribute", int.class, "public").toString())
                    .contains("MISSING in BaseFixtures$Modifiers")
                    .contains("public int ghostAttribute");
        }

        @Test
        void syntheticFieldsAreNoCandidates() {
            TestClassWrapper<Object> inner = TestClassWrapper.base("Inner", "public");
            // the reference to the enclosing instance is the synthetic field this$0
            assertThat(attribute(inner, "this$1", BaseFixtures.class).getOverallExistence()).isEqualTo(MISSING);
        }

        /** Two wrappers for similar names; "countrr" is as close to "counter" as to "countr". */
        static class Similar extends TestClassWrapper<Object> {
            final AttributeWrapper<Object, Integer> counter = new AttributeWrapper<>(this, "counter", int.class, "private");
            final AttributeWrapper<Object, Integer> countrr = new AttributeWrapper<>(this, "countrr", int.class, "private");

            Similar() {
                super("BaseFixtures$Similar", PKG, "public", "static");
            }
        }

        @Test
        void fieldExpectedByAnotherWrapperIsNotTaken() { // docs/matching.md, section "Claiming"
            Similar similar = new Similar();
            assertThat(similar.countrr.getActualName()).isEqualTo("countr");
            assertThat(similar.counter.getActualName()).isEqualTo("counter");

            // without the other wrapper, the tie is broken by name: "counter" < "countr"
            TestClassWrapper<Object> alone = TestClassWrapper.base("Similar", "public", "static");
            assertThat(attribute(alone, "countrr", int.class, "private").getActualName()).isEqualTo("counter");
        }
    }

    @Nested
    class Values {

        @Test
        void valueOfTheDefaultInstanceCanBeReadAndWritten() {
            AttributeWrapper<Object, String> text = attribute(modifiers, "text", String.class, "private");
            assertThat(text.getValue()).isEqualTo("start");
            text.setValue("changed");
            assertThat(text.getValue()).isEqualTo("changed");
            assertThat(text.getValue(modifiers.getObj())).isEqualTo("changed");
            assertThat(text.getValue(modifiers.newObj())).isEqualTo("start");
        }

        @Test
        void valueOfAGivenInstanceCanBeReadAndWritten() {
            AttributeWrapper<Object, Integer> field = attribute(modifiers, "privateField", int.class, "private");
            Object obj = new BaseFixtures.Modifiers();
            field.setValue(obj, 42);
            assertThat(field.getValue(obj)).isEqualTo(42);
            assertThat(field.getValue()).isZero();
        }

        @Test
        void staticAttributesNeedNoInstance() {
            TestClassWrapper<Object> notInstantiable = TestClassWrapper.base("NotInstantiable", "public", "static");
            AttributeWrapper<Object, Integer> counter = attribute(notInstantiable, "counter", int.class, "private static");
            assertThat(counter.getValue()).isEqualTo(5);
            try {
                counter.setValue(9);
                assertThat(counter.getValue()).isEqualTo(9);
            }
            finally {
                counter.setValue(5);
            }
            // an instance attribute of that class needs the (throwing) constructor
            AttributeWrapper<Object, Integer> value = attribute(notInstantiable, "value", int.class, "private");
            assertThatThrownBy(value::getValue).isInstanceOf(AssertionError.class);
        }

        @Test
        void finalAttributeCannotBeSet() {
            LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
            AttributeWrapper<Object, Integer> fixed = attribute(modifiers, "finalField", int.class, "public final");
            Object obj = modifiers.newObj();
            assertThatThrownBy(() -> fixed.setValue(obj, 2))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Cannot set value of attribute finalField")
                    .hasMessageContaining("declared final");
            assertThat(fixed.getValue(obj)).isEqualTo(1);
        }

        @Test
        void valueOfAnotherTypeCannotBeSet() {
            LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
            AttributeWrapper<Object, Integer> field = attribute(modifiers, "publicField", int.class, "public");
            assertThatThrownBy(() -> field.setValue(modifiers.newObj(), "seven"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Could not set value of attribute 'publicField'");
        }

        @Test
        void numbersAreConvertedToTheFieldType() {
            AttributeWrapper<Object, Long> field = attribute(modifiers, "publicField", long.class, "public");
            Object obj = modifiers.newObj();
            field.setValue(obj, 12L);
            assertThat(((BaseFixtures.Modifiers) obj).publicField).isEqualTo(12);
            assertThat(field.getValue(obj)).isEqualTo(12L);
            field.setValue(obj, 'A'); // a char is no number: reflection widens it
            assertThat(field.getValue(obj)).isEqualTo(65L);
        }

        @Test
        void missingAttributeCannotBeUsed() {
            AttributeWrapper<Object, Integer> ghost = attribute(modifiers, "ghostAttribute", int.class, "private");
            assertThatThrownBy(ghost::getValue).isInstanceOf(AssertionError.class);
            assertThatThrownBy(() -> ghost.setValue(1)).isInstanceOf(AssertionError.class);
        }
    }

    @Nested
    class InaccessibleFields { // fields of java.base cannot be made accessible: the getter is used instead

        private final TestClassWrapper<Object> thread = new TestClassWrapper<>("Thread", "java.lang", "public");

        @Test
        void getterIsUsedIfTheFieldIsInaccessible() {
            AttributeWrapper<Object, String> name = attribute(thread, "name", String.class, "private volatile");
            assertThat(name.getOverallExistence()).isEqualTo(EXACT);
            assertThat(name.getValue(new Thread("worker"))).isEqualTo("worker");
        }

        @Test
        void isGetterIsUsedForBooleans() {
            AttributeWrapper<Object, Boolean> interrupted = attribute(thread, "interrupted", boolean.class, "volatile");
            Thread t = new Thread("worker");
            assertThat(interrupted.getValue(t)).isFalse();
            t.interrupt();
            assertThat(t.isInterrupted()).isTrue();
            assertThat(interrupted.getValue(t)).isTrue();
        }

        @Test
        void inaccessibleFieldWithoutGetterFails() {
            LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
            AttributeWrapper<Object, Long> eetop = attribute(thread, "eetop", long.class, "private volatile");
            assertThatThrownBy(() -> eetop.getValue(new Thread("worker")))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Could not access value of attribute 'eetop' in Thread")
                    .hasMessageContaining("InaccessibleObjectException");
        }
    }
}
