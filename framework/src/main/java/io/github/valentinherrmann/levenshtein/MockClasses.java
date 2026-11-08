package io.github.valentinherrmann.levenshtein;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;

import sun.reflect.ReflectionFactory;

import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.isFinal;
import static net.bytebuddy.matcher.ElementMatchers.isVirtual;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * Byte Buddy subclasses for {@link Mock}s and allocation of objects without running a constructor.
 *
 * <p>A mock subclass has no constructors and intercepts every method that can be overridden (except those
 * of {@link Object} that the class does not override itself). Each call goes to the {@link InvocationHandler}
 * stored in the object's field {@value #HANDLER_FIELD}, so one generated class serves every mock of a class.</p>
 */
final class MockClasses {

    /** The instance field of a mock subclass that holds the handler of that object. */
    static final String HANDLER_FIELD = "$levenshteinMock";

    private MockClasses() {
    }

    /** Which method of {@link Object} an intercepted method overrides, if any. */
    enum ObjectMethod { NONE, EQUALS, HASH_CODE, TO_STRING }

    /**
     * What a mock needs to know about an intercepted method, computed once per class and method.
     */
    static final class MethodInfo {
        private final Class<?> base;
        private final Class<?> subclass;
        private final Method method;
        private final String key;
        private final boolean implemented;
        private final ObjectMethod objectMethod;
        private volatile MethodHandle superCall;

        MethodInfo(Class<?> base, Class<?> subclass, Method method) {
            this.base = base;
            this.subclass = subclass;
            this.method = method;
            this.key = MethodWrapper.signatureKey(method);
            this.implemented = hasImplementation(base, method.getName(), method.getParameterTypes());
            this.objectMethod = objectMethod(method);
        }

        /** @return the signature key, see {@link MethodWrapper#signatureKey(Method)} */
        String key() {
            return key;
        }

        /** @return whether the student's class has a non-abstract implementation */
        boolean implemented() {
            return implemented;
        }

        /** @return the method of {@link Object} this method overrides, if any */
        ObjectMethod objectMethod() {
            return objectMethod;
        }

        /**
         * @return the student's implementation, called like {@code super.method(...)} from the subclass; not bound
         *         to an object, so the first argument is the object actually called
         */
        MethodHandle superCall() throws ReflectiveOperationException {
            MethodHandle handle = superCall;
            if (handle == null) {
                handle = MethodHandles.privateLookupIn(subclass, MethodHandles.lookup())
                        .findSpecial(base, method.getName(),
                                MethodType.methodType(method.getReturnType(), method.getParameterTypes()), subclass);
                superCall = handle;
            }
            return handle;
        }

        private static ObjectMethod objectMethod(Method m) {
            Class<?>[] p = m.getParameterTypes();
            return switch (m.getName()) {
                case "equals" -> p.length == 1 && p[0] == Object.class ? ObjectMethod.EQUALS : ObjectMethod.NONE;
                case "hashCode" -> p.length == 0 ? ObjectMethod.HASH_CODE : ObjectMethod.NONE;
                case "toString" -> p.length == 0 ? ObjectMethod.TO_STRING : ObjectMethod.NONE;
                default -> ObjectMethod.NONE;
            };
        }
    }

    /**
     * A generated subclass.
     *
     * @param base the mocked class
     * @param type the subclass
     * @param samePackage whether it is in the same runtime package (class loader and package) as its base,
     *                    which is needed to override package-private methods
     * @param methods the information about intercepted methods, filled on first use
     */
    record Generated(Class<?> base, Class<?> type, boolean samePackage, Map<Method, MethodInfo> methods) {

        Generated(Class<?> base, Class<?> type, boolean samePackage) {
            this(base, type, samePackage, new ConcurrentHashMap<>());
        }

        /**
         * @param method an intercepted method
         * @return its information, computed on the first call
         */
        MethodInfo info(Method method) {
            return methods.computeIfAbsent(method, m -> new MethodInfo(base, type, m));
        }
    }

    /**
     * Generates the mock subclass of a class. It is defined in the class loader and package of the class if
     * possible (so package-private methods can be overridden), otherwise in a new class loader.
     *
     * @param base a class that is not final, or an interface
     * @return the generated subclass
     */
    static Generated subclass(Class<?> base) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(base, MethodHandles.lookup());
            return new Generated(base, make(base).load(base.getClassLoader(), ClassLoadingStrategy.UsingLookup.of(lookup))
                    .getLoaded(), true);
        }
        catch (IllegalAccessException | RuntimeException | LinkageError e) {
            return new Generated(base, make(base).load(base.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                    .getLoaded(), false);
        }
    }

    private static DynamicType.Unloaded<?> make(Class<?> base) {
        return new ByteBuddy()
                .subclass(base, ConstructorStrategy.Default.NO_CONSTRUCTORS)
                .defineField(HANDLER_FIELD, InvocationHandler.class, Visibility.PUBLIC)
                .method(isVirtual().and(not(isFinal())).and(not(isDeclaredBy(Object.class))))
                .intercept(InvocationHandlerAdapter.toField(HANDLER_FIELD))
                .make();
    }

    /**
     * Creates an object of a class without running any of its constructors (like Objenesis): only the
     * constructor of {@link Object} runs, so all attributes keep their default values ({@code 0}, {@code false},
     * {@code null}); field initializers do not run either.
     *
     * @param c a concrete class
     * @return the new object
     * @throws ReflectiveOperationException if the object cannot be created
     */
    static Object allocate(Class<?> c) throws ReflectiveOperationException {
        return ReflectionFactory.getReflectionFactory()
                .newConstructorForSerialization(c, Object.class.getDeclaredConstructor())
                .newInstance();
    }

    /**
     * @param type a type
     * @return the default value of the type: {@code 0} of the matching primitive type, {@code false},
     *         {@code '\0'}, or {@code null} (also for {@code void})
     */
    static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    /**
     * Whether a class has an implementation of a method (declared in the class, a superclass, or as default
     * method of an interface), i.e. whether a call can go to the student's code.
     *
     * @param base the class
     * @param name the method name
     * @param parameterTypes the parameter types
     * @return true if there is a non-abstract implementation
     */
    static boolean hasImplementation(Class<?> base, String name, Class<?>[] parameterTypes) {
        Method inClass = declaredInClassChain(base, name, parameterTypes);
        if (inClass != null) {
            return !Modifier.isAbstract(inClass.getModifiers());
        }
        Deque<Class<?>> interfaces = new ArrayDeque<>();
        Set<Class<?>> seen = new HashSet<>();
        for (Class<?> c = base; c != null; c = c.getSuperclass()) {
            if (c.isInterface()) {
                interfaces.add(c);
            }
            interfaces.addAll(java.util.List.of(c.getInterfaces()));
        }
        while (!interfaces.isEmpty()) {
            Class<?> i = interfaces.poll();
            if (!seen.add(i)) {
                continue;
            }
            Method declared = declaredMethod(i, name, parameterTypes);
            if (declared != null && declared.isDefault()) {
                return true;
            }
            interfaces.addAll(java.util.List.of(i.getInterfaces()));
        }
        return false;
    }

    /**
     * @return the method as declared by the class itself or its nearest superclass, or {@code null}
     */
    static Method declaredInClassChain(Class<?> base, String name, Class<?>[] parameterTypes) {
        for (Class<?> c = base; c != null; c = c.getSuperclass()) {
            Method declared = declaredMethod(c, name, parameterTypes);
            if (declared != null) {
                return declared;
            }
        }
        return null;
    }

    private static Method declaredMethod(Class<?> c, String name, Class<?>[] parameterTypes) {
        try {
            return c.getDeclaredMethod(name, parameterTypes);
        }
        catch (NoSuchMethodException | LinkageError e) {
            return null;
        }
    }
}
