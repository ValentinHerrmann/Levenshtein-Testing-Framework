package io.github.valentinherrmann.levenshtein;

/**
 * A generic class wrapper for actually existing classes to make them compatible with the other wrappers.
 * This wrapper is used to represent the actual superclass and interfaces of other wrapped classes.
 * It wraps a class that already exists (loaded via reflection) and provides minimal functionality.
 * Unlike full ClassWrapper subclasses, it does not support creating instances.
 * Only use this if you already have the class available via reflection and NOT for classes which
 * you expect to always exist!
 *
 * @param <T> the type of the class being wrapped
 */
public class GenericClassWrapper<T> extends ClassWrapper<T> {

    /**
     * Constructs a new GenericClassWrapper for an existing class.
     *
     * @param clz the class to wrap
     */
    public GenericClassWrapper(Class<T> clz) {
        super(clz);
    }

    /**
     * This method is not supported for GenericClassWrapper as it only wraps existing classes.
     *
     * @param forceNew ignored
     * @param useByteBuddy ignored
     * @return always null
     */
    @Override
    public Object getObj(boolean forceNew, boolean useByteBuddy) {
        return null;
    }
}
