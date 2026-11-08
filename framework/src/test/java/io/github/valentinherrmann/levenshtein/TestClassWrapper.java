package io.github.valentinherrmann.levenshtein;

/**
 * Minimal class wrapper for the tests of the base classes; {@link #base(String, String...)} wraps a nested class of
 * {@link io.github.valentinherrmann.levenshtein.fixtures.BaseFixtures}.
 */
class TestClassWrapper<T> extends ClassWrapper<T> {

    static final String PKG = "io.github.valentinherrmann.levenshtein.fixtures";

    TestClassWrapper(String name, String pkg, String... modifiers) {
        super(name, pkg, modifiers);
    }

    static <T> TestClassWrapper<T> base(String simpleName, String... modifiers) {
        return new TestClassWrapper<>("BaseFixtures$" + simpleName, PKG, modifiers);
    }

    @Override
    public Object getObj(boolean forceNew) {
        return getObj(forceNew, null);
    }
}
