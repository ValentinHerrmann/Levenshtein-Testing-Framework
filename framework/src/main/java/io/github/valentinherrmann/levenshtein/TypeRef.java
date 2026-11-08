package io.github.valentinherrmann.levenshtein;

import java.util.function.Supplier;

/**
 * An expected attribute, return or parameter type: either a fixed {@link Class} or a student class given by its
 * {@link ClassWrapper} (directly or via a {@link Supplier}). A wrapped type is only looked up when it is resolved,
 * i.e. when the member that uses it is checked, so wrappers of classes that refer to each other can be built in
 * any order, and a missing student class only makes the type missing.
 */
sealed interface TypeRef permits TypeRef.Fixed, TypeRef.Wrapped, TypeRef.Supplied {

    /** Shown instead of a type name if the supplier of a wrapper returns {@code null}. */
    String UNKNOWN = "<unknown>";

    /**
     * @return the type, or {@code null} if the student class is missing
     */
    Class<?> resolve();

    /**
     * Never triggers a lookup in the student code, so it can be used in messages at any time.
     *
     * @return the simple name of the expected type, e.g. {@code Drummer}
     */
    String expectedSimpleName();

    /**
     * @return whether the type is a fixed class (known without a lookup)
     */
    default boolean isFixed() {
        return false;
    }

    /**
     * A type that is known when the test is written.
     */
    record Fixed(Class<?> type) implements TypeRef {
        @Override
        public Class<?> resolve() {
            return type;
        }

        @Override
        public String expectedSimpleName() {
            return type.getSimpleName();
        }

        @Override
        public boolean isFixed() {
            return true;
        }
    }

    /**
     * A student class, resolved with {@link ClassWrapper#getClazz()}.
     */
    record Wrapped(ClassWrapper<?> wrapper) implements TypeRef {
        @Override
        public Class<?> resolve() {
            return wrapper.getClazz();
        }

        @Override
        public String expectedSimpleName() {
            return simpleName(wrapper);
        }
    }

    /**
     * A student class whose wrapper may not exist yet when the member wrapper is built.
     */
    record Supplied(Supplier<? extends ClassWrapper<?>> supplier, String position) implements TypeRef {
        @Override
        public Class<?> resolve() {
            ClassWrapper<?> wrapper = supplier.get();
            if (wrapper == null) {
                throw new IllegalStateException("The type supplier for " + position
                        + " returned null: is the class wrapper initialized yet?");
            }
            return wrapper.getClazz();
        }

        @Override
        public String expectedSimpleName() {
            ClassWrapper<?> wrapper = supplier.get();
            return wrapper == null ? UNKNOWN : simpleName(wrapper);
        }
    }

    /**
     * @return the expected simple name of the wrapped class ({@code Inner} for {@code Outer$Inner})
     */
    private static String simpleName(ClassWrapper<?> wrapper) {
        String name = wrapper.getExpectedName();
        return name.substring(name.lastIndexOf('$') + 1);
    }

    /**
     * Creates a reference to a fixed class; {@code null} is kept as an unknown type (as before for return and
     * attribute types).
     *
     * @param type the class, or {@code null}
     * @return the reference, or {@code null}
     */
    static TypeRef ofNullableClass(Class<?> type) {
        return type == null ? null : new Fixed(type);
    }

    /**
     * Creates a reference to a student class.
     *
     * @param wrapper the wrapper of the class, not {@code null}
     * @param position the position in the specification, used in the error message
     * @return the reference
     * @throws IllegalArgumentException if the wrapper is {@code null}
     */
    static TypeRef ofWrapper(ClassWrapper<?> wrapper, String position) {
        if (wrapper == null) {
            throw new IllegalArgumentException("The expected type of " + position + " is null");
        }
        return new Wrapped(wrapper);
    }

    /**
     * Creates a reference to a student class whose wrapper is supplied later.
     *
     * @param supplier supplies the wrapper of the class, not {@code null}
     * @param position the position in the specification, used in the error message
     * @return the reference
     * @throws IllegalArgumentException if the supplier is {@code null}
     */
    static TypeRef ofSupplier(Supplier<? extends ClassWrapper<?>> supplier, String position) {
        if (supplier == null) {
            throw new IllegalArgumentException("The expected type of " + position + " is null");
        }
        return new Supplied(supplier, position);
    }

    /**
     * Creates a reference from a {@link Class}, a {@link ClassWrapper} or a {@link Supplier} of a class wrapper.
     *
     * @param type the type specification
     * @param position the position in the specification, used in the error message
     * @return the reference
     * @throws IllegalArgumentException for {@code null} and any other object
     */
    @SuppressWarnings("unchecked")
    static TypeRef of(Object type, String position) {
        if (type instanceof Class<?> c) {
            return new Fixed(c);
        }
        if (type instanceof ClassWrapper<?> w) {
            return new Wrapped(w);
        }
        if (type instanceof Supplier<?> s) {
            return new Supplied((Supplier<? extends ClassWrapper<?>>) s, position);
        }
        throw new IllegalArgumentException("The expected type of " + position + " must be a Class, a ClassWrapper "
                + "or a Supplier of a ClassWrapper, but is " + (type == null ? "null" : type.getClass().getName()));
    }

    /**
     * Creates references for parameter types.
     *
     * @param types the parameter types ({@code null} for none)
     * @return the references
     */
    static TypeRef[] ofAll(Object[] types) {
        if (types == null) {
            return new TypeRef[0];
        }
        TypeRef[] refs = new TypeRef[types.length];
        for (int i = 0; i < types.length; i++) {
            refs[i] = of(types[i], "parameter " + i);
        }
        return refs;
    }

    /**
     * @param refs the references
     * @return the resolved types; a missing student class is {@code null}
     */
    static Class<?>[] resolveAll(TypeRef[] refs) {
        Class<?>[] types = new Class<?>[refs.length];
        for (int i = 0; i < refs.length; i++) {
            types[i] = refs[i].resolve();
        }
        return types;
    }

    /**
     * @param refs the references
     * @return the comma separated expected simple names, e.g. {@code double, Guitarist}
     */
    static String simpleNames(TypeRef[] refs) {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < refs.length; i++) {
            if (i > 0) {
                names.append(", ");
            }
            names.append(refs[i].expectedSimpleName());
        }
        return names.toString();
    }
}
