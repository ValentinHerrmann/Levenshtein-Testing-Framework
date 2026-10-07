package io.github.valentinherrmann.levenshtein;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for generating structural validation tests using wrapper objects.
 * Provides methods to create JUnit DynamicTests that verify class structures with various levels of detail.
 *
 * <p>Student code is only touched inside the executables of the generated tests (never while the list of
 * tests is built), so every check runs under the supervision of the enclosing Ares test.</p>
 */
public final class StructuralLevenshtein {

    private StructuralLevenshtein() {
    }

    /**
     * Enumeration defining the level of detail for structural test generation.
     */
    public enum DetailLevel {
        /** Creates a single test for all structural elements */
        ONE_FOR_EVERYTHING,
        /** Creates one test per class, including all its members */
        ONE_PER_CLASS,
        /** Creates separate tests for class, constructors, attributes, and methods */
        ONE_PER_MEMBER_CATEGORY,
        /** Creates individual tests for each member */
        ONE_PER_MEMBER
    }

    /**
     * Template method for structural testing that checks wrapper existence states.
     * Collects all wrappers with MISSING or DEVIATES states and fails if any are found.
     * A wrapper whose check itself fails unexpectedly is reported instead of aborting the whole test.
     *
     * @param wrappers the list of wrappers to verify
     */
    public static void structuralTestTemplate(List<? extends Wrapper<?>> wrappers) {
        List<String> msg = new ArrayList<>();
        for(Wrapper<?> wrap : wrappers) {
            try {
                switch (wrap.getOverallExistence()) {
                    case MISSING, DEVIATES -> msg.add(wrap.toString());
                    default -> { }
                }
            }
            catch (RuntimeException | LinkageError e) {
                msg.add(wrap.getParentClassWrapper().getExpectedName() + ": " + wrap.expectedToString()
                        + " could not be checked: " + Invocations.describe(e));
            }
        }
        Assertions.assertThat(msg).withFailMessage("\n"+String.join("\n", msg)+"\n").isEmpty();
    }

    /**
     * Factory method for creating dynamic structural tests based on class wrappers.
     * Generates JUnit DynamicTests organized according to the specified detail level, in a deterministic order.
     *
     * @param detailsLevel the level of detail for test organization
     * @param classWrappers the class wrappers to generate tests for
     * @return a list of DynamicTest objects ready to be used in a @TestFactory method
     */
    public static List<DynamicTest> structuralTestFactory(DetailLevel detailsLevel, ClassWrapper<?>... classWrappers) {
        Map<String,List<Wrapper<?>>> wrappers = new LinkedHashMap<>();
        switch (detailsLevel) {
            case ONE_FOR_EVERYTHING -> {
                List<Wrapper<?>> all = new ArrayList<>();
                for (ClassWrapper<?> classWrap : classWrappers) {
                    all.addAll(classAndMembers(classWrap));
                }
                put(wrappers, "Structural[all]", all);
            }
            case ONE_PER_CLASS -> {
                for (ClassWrapper<?> classWrap : classWrappers) {
                    put(wrappers, "Structural[" + classWrap.getExpectedName() + "]", classAndMembers(classWrap));
                }
            }
            case ONE_PER_MEMBER_CATEGORY -> {
                for (ClassWrapper<?> classWrap : classWrappers) {
                    String key = "[" + classWrap.getExpectedName() + "]";
                    put(wrappers, "Class" + key, List.of(classWrap));
                    put(wrappers, "Constructors" + key, new ArrayList<>(classWrap.getConstructorWrappers()));
                    put(wrappers, "Attributes" + key, new ArrayList<>(classWrap.getAttributeWrappers()));
                    put(wrappers, "Methods" + key, new ArrayList<>(classWrap.getMethodsWrappers()));
                }
            }
            case ONE_PER_MEMBER -> {
                for (ClassWrapper<?> classWrap : classWrappers) {
                    String cls = classWrap.getExpectedName();
                    put(wrappers, "Class[" + cls + "]", List.of(classWrap));
                    for (Wrapper<?> constructor : classWrap.getConstructorWrappers()) {
                        put(wrappers, "Constructor[" + constructor.expectedToString() + "]", List.of(constructor));
                    }
                    for (Wrapper<?> attribute : classWrap.getAttributeWrappers()) {
                        put(wrappers, "Attribute[" + cls + "." + attribute.getExpectedName() + "]", List.of(attribute));
                    }
                    for (Wrapper<?> method : classWrap.getMethodsWrappers()) {
                        put(wrappers, "Method[" + cls + "." + method.expectedToString() + "]", List.of(method));
                    }
                }
            }
            default -> throw new IllegalArgumentException("Unknown DetailLevel: " + detailsLevel);
        }

        List<DynamicTest> tests = new ArrayList<>();
        for(Map.Entry<String, List<Wrapper<?>>> x : wrappers.entrySet()) {
            List<Wrapper<?>> group = x.getValue();
            tests.add(DynamicTest.dynamicTest(x.getKey(), () -> structuralTestTemplate(group)));
        }
        return tests;
    }

    private static List<Wrapper<?>> classAndMembers(ClassWrapper<?> classWrap) {
        List<Wrapper<?>> list = new ArrayList<>();
        list.add(classWrap);
        list.addAll(classWrap.getConstructorWrappers());
        list.addAll(classWrap.getAttributeWrappers());
        list.addAll(classWrap.getMethodsWrappers());
        return list;
    }

    /** Adds a group; a duplicate name gets a numeric suffix instead of silently replacing the earlier group. */
    private static void put(Map<String, List<Wrapper<?>>> map, String key, List<Wrapper<?>> value) {
        String unique = key;
        for (int i = 2; map.containsKey(unique); i++) {
            unique = key + " #" + i;
        }
        map.put(unique, value);
    }
}
