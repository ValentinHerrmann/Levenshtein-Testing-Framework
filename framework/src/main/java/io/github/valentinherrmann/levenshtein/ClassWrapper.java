package io.github.valentinherrmann.levenshtein;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;

import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence;
import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.*;
import static org.assertj.core.api.Assertions.fail;

/**
 * Wrapper for classes that verifies their existence, name, modifiers, superclass, and interfaces
 * using Levenshtein distance for fuzzy matching. Provides methods to instantiate objects
 * and retrieve attribute, method, and constructor wrappers.
 *
 * <p>Abstract classes and interfaces are instantiated through a Byte Buddy subclass; concrete classes always
 * through their own constructor (so {@code final} classes and {@code getClass()}-based {@code equals} work).</p>
 *
 * @param <T> the type of the class being wrapped
 */
public abstract class ClassWrapper<T> extends Wrapper<T>
{
    /**
     * The expected package name where the class should be located.
     */
    private final String expectedPackage;

    /**
     * The actual class found via reflection.
     */
    private Class<T> clazz;

    /**
     * A class that is already known (see {@link GenericClassWrapper}); no lookup by name is done then.
     */
    private final Class<T> knownClass;

    /**
     * A default instance of the class for testing purposes.
     */
    protected T obj;

    /**
     * Constructor and arguments the cached {@link #obj} was created with ({@code null} if set externally).
     */
    private ConstructorWrapper<?> objConstructor;
    private Object[] objArgs;
    private boolean objPinned;

    /**
     * Cached Byte Buddy subclass for abstract classes and interfaces.
     */
    private Class<?> dynamicSubclass;

    /**
     * Cached Byte Buddy subclass for {@link Mock}s (no constructors, every method intercepted).
     */
    private MockClasses.Generated mockSubclass;

    /**
     * Wrapper for the expected and actual superclass.
     */
    WrapperProperty<ClassWrapper<?>> superClassWrapper;

    /**
     * Wrapper for the expected and actual interfaces implemented by the class.
     */
    WrapperProperty<ClassWrapper<?>[]> interfaceWrappers;

    /**
     * Constructs a new ClassWrapper with superclass and interface expectations.
     *
     * @param expectedName the expected (simple or binary nested, e.g. {@code Outer$Inner}) name of the class
     * @param expectedPackage the expected package name ({@code ""} for the default package)
     * @param superClassWrapper the expected superclass wrapper (null if extends Object)
     * @param interfaceWrappers the expected interface wrappers (null or empty if none)
     * @param modifiers the expected modifiers (e.g., "public", "abstract")
     */
    public ClassWrapper(String expectedName, String expectedPackage, ClassWrapper<?> superClassWrapper, ClassWrapper<?>[] interfaceWrappers, String... modifiers) {
        super(null, expectedName, modifiers);
        this.expectedPackage = expectedPackage == null ? "" : expectedPackage;
        this.superClassWrapper = new WrapperProperty<>(superClassWrapper);
        this.interfaceWrappers = new WrapperProperty<>(interfaceWrappers == null ? new ClassWrapper<?>[0] : interfaceWrappers);
        this.knownClass = null;
    }

    /**
     * Constructs a wrapper for a class that is already available via reflection.
     *
     * @param knownClass the class to wrap
     */
    protected ClassWrapper(Class<T> knownClass) {
        super(null, binaryNameIn(knownClass), Modifier.toString(knownClass.getModifiers()));
        this.expectedPackage = knownClass.getPackageName();
        this.superClassWrapper = new WrapperProperty<>(null);
        this.interfaceWrappers = new WrapperProperty<>(new ClassWrapper<?>[0]);
        this.knownClass = knownClass;
    }

    private static String binaryNameIn(Class<?> c) {
        String pkg = c.getPackageName();
        return pkg.isEmpty() ? c.getName() : c.getName().substring(pkg.length() + 1);
    }

    /**
     * Constructs a new ClassWrapper without superclass or interface expectations.
     *
     * @param expectedName the expected name of the class
     * @param expectedPackage the expected package name
     * @param modifiers the expected modifiers (e.g., "public", "abstract")
     */
    public ClassWrapper(String expectedName, String expectedPackage, String... modifiers) {
        this(expectedName, expectedPackage, null, null, modifiers);
    }

    @Override
    public void verifyExistence(boolean throwAssertion)
    {
        super.verifyExistence(Messages.CLASS_NOT_IMPLEMENTED.format(name.expected, expectedPackage), throwAssertion);
    }

    /**
     * A class can be used in behavioral tests as soon as it was found, even if e.g. its superclass deviates.
     */
    /**
     * Interfaces are implicitly {@code abstract}, enums {@code final} (or {@code abstract} if a constant has a
     * body), records {@code final}; nested interfaces, enums and records as well as types declared in an
     * interface are implicitly {@code static}.
     */
    @Override
    protected int implicitModifiers() {
        int implicit = super.implicitModifiers();
        Class<T> c = clazz;
        if (c == null) {
            return implicit;
        }
        boolean member = c.isMemberClass();
        if (c.isInterface()) {
            implicit |= Modifier.ABSTRACT | (member ? Modifier.STATIC : 0);
        }
        if (c.isEnum()) {
            implicit |= Modifier.FINAL | Modifier.ABSTRACT | (member ? Modifier.STATIC : 0);
        }
        if (c.isRecord()) {
            implicit |= Modifier.FINAL | (member ? Modifier.STATIC : 0);
        }
        if (member && c.getEnclosingClass() != null && c.getEnclosingClass().isInterface()) {
            implicit |= Modifier.STATIC;
        }
        return implicit;
    }

    @Override
    protected Existence usability() {
        return clazz == null ? MISSING : EXACT;
    }

    /**
     * Retrieves the actual class found via reflection.
     * Verifies existence without throwing an assertion.
     *
     * @return the actual class, or null if not found
     */
    public Class<T> getClazz()
    {
        ensureChecked();
        return clazz;
    }

    /**
     * @return the expected package of the class
     */
    public String getExpectedPackage() {
        return expectedPackage;
    }

    private String qualify(String simpleName) {
        return expectedPackage.isEmpty() ? simpleName : expectedPackage + "." + simpleName;
    }

    private static ClassLoader classLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : ClassWrapper.class.getClassLoader();
    }

    /**
     * Loads a class without initializing it (student static initializers must not run during the structural check).
     *
     * @return the class, or null if it does not exist or cannot be linked
     */
    @SuppressWarnings("unchecked")
    private Class<T> tryLoad(String simpleName) {
        try {
            Class<?> loaded = Class.forName(qualify(simpleName), false, classLoader());
            // on case-insensitive file systems "car" may resolve to Car.class; only accept the real name
            return binaryName(loaded).equals(simpleName) ? (Class<T>) loaded : null;
        }
        catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private String binaryName(Class<?> c) {
        return binaryNameIn(c);
    }

    /**
     * Searches for the class in the expected package, using
     * {@link LevenshteinSettings#getClassNameDeviationThreshold()} for deviating names.
     */
    @Override
    protected void findWithDeviation() {
        if (knownClass != null) {
            clazz = knownClass;
            name.actual = name.expected;
            name.existence = modifiers.existence = superClassWrapper.existence = interfaceWrappers.existence = EXACT;
            modifiers.actual = modifiers.expected;
            return;
        }
        clazz = tryLoad(name.expected);
        if (clazz != null) {
            name.actual = binaryName(clazz);
            name.existence = EXACT;
        }
        else {
            Set<String> candidates = new LinkedHashSet<>(classNamesInPackage());
            candidates.addAll(generateClassNameVariations(name.expected));
            boolean expectNested = name.expected.contains("$");
            String best = candidates.stream()
                    .filter(c -> !c.equals(name.expected))
                    .filter(c -> expectNested || !c.contains("$"))
                    .filter(c -> Utils.isNameWithinDeviation(name.expected, c, LevenshteinSettings.getClassNameDeviationThreshold()))
                    .sorted(Comparator.<String>comparingInt(c -> Utils.levenshteinDistance(name.expected, c))
                            .thenComparing(Comparator.naturalOrder()))
                    .filter(c -> tryLoad(c) != null)
                    .findFirst()
                    .orElse(null);
            if (best != null) {
                clazz = tryLoad(best);
                name.actual = best;
                name.existence = DEVIATES;
            }
        }
        if (clazz == null) {
            name.existence = modifiers.existence = MISSING;
        }
        else {
            verifyModifiers(clazz.getModifiers());
            verifySuperClass();
            verifyInterfaces();
        }
    }

    /**
     * Lists the binary names of all classes in the expected package that live in a directory on the class path
     * (i.e. the compiled student code, not classes inside JARs).
     */
    private List<String> classNamesInPackage() {
        List<String> names = new ArrayList<>();
        String path = expectedPackage.replace('.', '/');
        try {
            Enumeration<URL> roots = classLoader().getResources(path);
            while (roots.hasMoreElements()) {
                URL url = roots.nextElement();
                if (!"file".equals(url.getProtocol())) {
                    continue;
                }
                Path dir = Path.of(url.toURI());
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.class")) {
                    for (Path file : files) {
                        String fileName = file.getFileName().toString();
                        names.add(fileName.substring(0, fileName.length() - ".class".length()));
                    }
                }
            }
        }
        catch (IOException | URISyntaxException | RuntimeException e) {
            // scanning is a best effort; the generated variations below still apply
        }
        return names;
    }

    /**
     * Verifies that the actual superclass matches or is compatible with the expected superclass.
     * Updates the superClassWrapper's existence state based on exact match, indirect inheritance, or mismatch.
     * The implicit superclass of a record ({@link Record}) or an enum ({@link Enum}) counts as no superclass,
     * like {@link Object}.
     */
    public void verifySuperClass() {
        Class<?> actualSuper = clazz.getSuperclass();
        boolean implicitSuper = actualSuper == Object.class
                || (clazz.isRecord() && actualSuper == Record.class)
                || (clazz.isEnum() && actualSuper == Enum.class);
        boolean hasSuper = actualSuper != null && !implicitSuper;
        superClassWrapper.actual = hasSuper ? new GenericClassWrapper<>(actualSuper) : null;

        ClassWrapper<?> expectedWrapper = superClassWrapper.expected;
        if (expectedWrapper == null) {
            superClassWrapper.existence = hasSuper ? DEVIATES : EXACT;
            return;
        }
        Class<?> expectedSuper = expectedWrapper.getClazz();
        if (expectedSuper == null || !hasSuper) {
            superClassWrapper.existence = MISSING;
        }
        else if (expectedSuper.equals(actualSuper)) {
            superClassWrapper.existence = EXACT;
        }
        else if (expectedSuper.isAssignableFrom(actualSuper)) {
            superClassWrapper.existence = DEVIATES; // inherits indirectly
        }
        else {
            superClassWrapper.existence = MISSING;
        }
    }

    /**
     * Verifies that the actual interfaces match the expected interfaces.
     * Expected interfaces are compared by the classes their wrappers resolved (so a deviating interface name
     * still counts). An interface that is only inherited indirectly, or additional interfaces, are deviations.
     * Updates the interfaceWrappers' existence state accordingly.
     */
    public void verifyInterfaces() {
        Class<?>[] direct = clazz.getInterfaces();
        interfaceWrappers.actual = Arrays.stream(direct).map(GenericClassWrapper::new).toArray(ClassWrapper<?>[]::new);

        Existence result = EXACT;
        Set<Class<?>> expectedClasses = new LinkedHashSet<>();
        for (ClassWrapper<?> expectedWrapper : interfaceWrappers.expected) {
            Class<?> expectedInterface = expectedWrapper.getClazz();
            if (expectedInterface == null) {
                interfaceWrappers.existence = MISSING;
                return;
            }
            expectedClasses.add(expectedInterface);
            if (Arrays.asList(direct).contains(expectedInterface)) {
                continue;
            }
            if (expectedInterface.isAssignableFrom(clazz)) {
                result = Existence.worst(result, DEVIATES); // implemented indirectly
            }
            else {
                interfaceWrappers.existence = MISSING;
                return;
            }
        }
        boolean additional = Arrays.stream(direct).anyMatch(i -> !expectedClasses.contains(i));
        interfaceWrappers.existence = additional ? Existence.worst(result, DEVIATES) : result;
    }


    /**
     * Generates possible variations of a class name to try when the package cannot be scanned.
     * This includes singular/plural forms, single deletions, transpositions and a different first letter case.
     */
    private static List<String> generateClassNameVariations(String className) {
        List<String> variations = new ArrayList<>();

        // Add singular/plural variations
        if (className.endsWith("s")) {
            variations.add(className.substring(0, className.length() - 1)); // Remove 's'
        } else {
            variations.add(className + "s"); // Add 's'
        }

        // Add variations with common suffixes removed/added
        if (className.endsWith("es")) {
            variations.add(className.substring(0, className.length() - 2)); // Remove 'es'
        }

        // Different case of the first letter (e.g. "car" instead of "Car")
        if (!className.isEmpty()) {
            char first = className.charAt(0);
            char swapped = Character.isUpperCase(first) ? Character.toLowerCase(first) : Character.toUpperCase(first);
            variations.add(swapped + className.substring(1));
        }

        // Add common character-level variations (one character removed at different positions)
        for (int i = 0; i < className.length(); i++) {
            String variation = className.substring(0, i) + className.substring(i + 1);
            if (!variation.isEmpty()) {
                variations.add(variation);
            }
        }

        // Add variations with characters swapped (transpositions)
        for (int i = 0; i < className.length() - 1; i++) {
            char[] chars = className.toCharArray();
            char temp = chars[i];
            chars[i] = chars[i + 1];
            chars[i + 1] = temp;
            variations.add(new String(chars));
        }

        return variations;
    }

    /**
     * Returns the default instance of the class represented by this ClassWrapper.
     * Concrete wrapper subclasses implement it by calling
     * {@link #getObj(boolean, ConstructorWrapper, Object...)} with a default constructor and arguments.
     * Abstract classes and interfaces are instantiated through a Byte Buddy subclass, concrete classes through
     * their own constructor.
     *
     * @param forceNew whether to force creation of a new instance instead of reusing the cached one
     * @return an instance of the wrapped class
     */
    public abstract Object getObj(boolean forceNew);

    /**
     * Returns the (possibly cached) default instance of the class.
     *
     * @return an instance of the wrapped class which might have been cached
     */
    public Object getObj() {
        return getObj(false);
    }

    /**
     * Creates a new default instance of the class (never cached from earlier calls).
     *
     * @return a new instance of the wrapped class
     */
    public Object newObj() {
        return getObj(true);
    }

    /**
     * Returns an instance of the class created with a specific constructor.
     * A cached instance is only reused if it was created with the same constructor and arguments
     * (or set via {@link #setCachedObj(Object)}) and {@code forceNew} is false.
     * Abstract classes and interfaces are instantiated through a Byte Buddy subclass.
     *
     * <p>Pass a single array argument as {@code (Object) array}: otherwise Java spreads an object array
     * (e.g. {@code String[]}) into several arguments. A wrong number of arguments is reported as
     * {@link IllegalArgumentException}.</p>
     *
     * @param forceNew whether to force creation of a new instance
     * @param constructorWrapper the constructor wrapper to use for instantiation (null: no-argument constructor)
     * @param constructorArgs the arguments to pass to the constructor
     * @return an instance of the wrapped class
     */
    @SuppressWarnings("unchecked")
    public T getObj(boolean forceNew, ConstructorWrapper<?> constructorWrapper, Object... constructorArgs) {
        // null as in ConstructorWrapper.invoke: one null argument for a single parameter, otherwise none
        Object[] args = constructorWrapper == null
                ? (constructorArgs == null ? new Object[0] : constructorArgs)
                : Invocations.normalizeNull(constructorWrapper.getActualParamTypes(), constructorArgs);
        if (constructorWrapper == null && args.length > 0) {
            // the no-argument constructor takes no arguments, also when a cached or pinned object would be returned
            verifyExistence(true);
            Invocations.checkArguments(new Class<?>[0], args, name.expected + "()");
        }
        boolean sameRequest = objPinned
                || (objConstructor == constructorWrapper && Arrays.deepEquals(objArgs, args));
        if (obj != null && !forceNew && sameRequest) {
            return obj;
        }
        verifyExistence(true);
        Class<T> c = getClazz();
        T created;
        if (Modifier.isAbstract(c.getModifiers()) || c.isInterface()) {
            created = (T) getDynamicSubclassObj(constructorWrapper, args);
        }
        else if (constructorWrapper != null) {
            created = (T) constructorWrapper.invoke(args);
        }
        else {
            created = (T) instantiateWithNoArgConstructor(c);
        }
        obj = created;
        objConstructor = constructorWrapper;
        objArgs = args.clone();
        objPinned = false;
        return obj;
    }

    /**
     * Creates the outer instance that the constructor of an inner class needs: with the no-argument constructor
     * of the outer class if possible, otherwise without running a constructor.
     *
     * @param outer the enclosing class
     * @return an object of the enclosing class
     */
    static Object outerInstance(Class<?> outer) {
        try {
            return Invocations.newInstance(outer.getDeclaredConstructor());
        }
        catch (Throwable t) {
            Invocations.rethrowIfCritical(t);
        }
        try {
            return MockClasses.allocate(outer);
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return fail(Messages.CLASS_INSTANTIATION_FAILED.format(outer.getSimpleName()) + "\n" + Invocations.describe(e), e);
        }
    }

    private Object instantiateWithNoArgConstructor(Class<T> c) {
        try {
            if (ConstructorWrapper.isInnerClass(c)) { // the constructor takes the outer instance
                Constructor<T> withOuter = c.getDeclaredConstructor(c.getEnclosingClass());
                return Invocations.newInstance(withOuter, outerInstance(c.getEnclosingClass()));
            }
            Constructor<T> noArg = c.getDeclaredConstructor();
            return Invocations.newInstance(noArg);
        }
        catch (NoSuchMethodException e) {
            return fail(Messages.CLASS_INSTANTIATION_FAILED.format(name.expected));
        }
        catch (Throwable t) {
            Invocations.rethrowIfCritical(t);
            return fail(Messages.CLASS_INSTANTIATION_FAILED.format(name.expected) + "\n" + Invocations.describe(t), t);
        }
    }

    /**
     * Sets the cached default instance, e.g. to an object created in a test. It is returned by
     * {@link #getObj(boolean, ConstructorWrapper, Object...)} until {@code forceNew} is requested.
     *
     * @param newObj the instance to cache (null clears the cache)
     */
    @SuppressWarnings("unchecked")
    public void setCachedObj(Object newObj) {
        obj = (T) newObj;
        objConstructor = null;
        objArgs = null;
        objPinned = newObj != null;
    }

    /**
     * Creates a dynamic subclass instance using ByteBuddy for abstract classes and interfaces.
     * This allows testing abstract classes by creating concrete implementations at runtime.
     * Abstract methods of the instance throw {@link AbstractMethodError} when called.
     *
     * <p>Pass a single array argument as {@code (Object) array}: otherwise Java spreads an object array
     * into several arguments. A wrong number of arguments is reported as {@link IllegalArgumentException}.</p>
     *
     * @param constructorWrapper the constructor to imitate (null for the no-argument constructor)
     * @param constructorArgs the arguments to pass to the constructor
     * @return a new instance of the dynamic subclass
     */
    public Object getDynamicSubclassObj(ConstructorWrapper<?> constructorWrapper, Object... constructorArgs) {
        Class<?>[] types = new Class<?>[0];
        if (constructorWrapper != null) {
            constructorWrapper.verifyExistence(true);
            types = constructorWrapper.getActualParamTypes();
        }
        Object[] args = Invocations.checkArguments(types, constructorArgs,
                constructorWrapper != null ? constructorWrapper.actualToString() : name.expected + "()");
        Object[] shown = Invocations.snapshotArguments(args);
        Class<?>[] fullTypes = types;
        Object[] fullArgs = args;
        if (constructorWrapper != null) {
            fullTypes = constructorWrapper.fullParamTypes();
            fullArgs = constructorWrapper.withOuterInstance(args);
        }
        else if (ConstructorWrapper.isInnerClass(getClazz())) {
            fullTypes = new Class<?>[] {getClazz().getEnclosingClass()};
            fullArgs = new Object[] {outerInstance(getClazz().getEnclosingClass())};
        }
        try {
            return Invocations.newInstance(dynamicSubclassConstructor(fullTypes), fullArgs);
        }
        catch (Throwable e) {
            if (e instanceof AssertionError failure) {
                throw Invocations.withCallDetails(failure, null, shown);
            }
            Invocations.rethrowIfCritical(e);
            return fail(Invocations.withArguments(
                    Messages.CLASS_SUBCLASS_INSTANTIATION_FAILED.format(name.expected, Invocations.describe(e)), shown), e);
        }
    }

    /**
     * The constructor of the Byte Buddy subclass (created once) that calls the class's constructor with the given
     * parameter types. Calling it runs the student's constructor, whose exceptions are not wrapped.
     *
     * @param types the parameter types of the student's constructor
     * @return the constructor of the generated subclass
     */
    Constructor<?> dynamicSubclassConstructor(Class<?>[] types) {
        try {
            if (dynamicSubclass == null) {
                Class<T> base = getClazz();
                dynamicSubclass = new ByteBuddy()
                        .subclass(base)
                        .make()
                        .load(base.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                        .getLoaded();
            }
            return dynamicSubclass.getDeclaredConstructor(types);
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return fail(Messages.CLASS_SUBCLASS_INSTANTIATION_FAILED.format(name.expected, Invocations.describe(e)), e);
        }
    }

    /**
     * Starts a {@link Mock} of the class: an object whose methods the test can replace and whose calls are
     * recorded, created without running a constructor of the class. Use it to keep tests independent, e.g. when
     * the method under test calls another student method, or needs an object of another student class.
     * <pre>{@code
     * Mock<?> guitarist = guitaristWrapper.mock()
     *         .override(guitaristWrapper.calculateLoudness, args -> (int) args[0] * 7);
     * Object g = guitarist.obj();
     * }</pre>
     * A missing class fails with the usual message; a {@code final} class, record, enum or sealed class fails with
     * a readable message.
     *
     * @return a new mock
     * @see #newObjOverriding(Map)
     * @see #newObjWithoutConstructor()
     */
    public Mock<T> mock() {
        return new Mock<>(this);
    }

    /**
     * Creates an object of a subclass of the wrapped class in which each given method returns the result of its
     * function. No constructor of the class runs, so attributes keep their default values. Methods that are not
     * overridden run the student's code. Same as {@code mock().override(...)...obj()}; use {@link #mock()} to
     * check the calls as well.
     *
     * @param overrides the methods to override (of this class, its superclasses or interfaces) and their functions,
     *                  which get the arguments and return the result
     * @return the new object
     * @see Mock#override(MethodWrapper, Function)
     */
    public Object newObjOverriding(Map<MethodWrapper<?, ?>, Function<Object[], Object>> overrides) {
        Mock<T> mock = new Mock<>(this, false); // nobody can ask this mock for its calls
        overrides.forEach(mock::override);
        return mock.obj();
    }

    /**
     * Creates an object of the wrapped class without running any of its constructors (attributes keep their
     * default values {@code 0}, {@code false}, {@code null}; field initializers do not run either). A missing or
     * broken constructor then cannot fail the test. Concrete classes (also {@code final} ones) are instantiated
     * directly; abstract classes and interfaces through the subclass of a {@link Mock} without overrides.
     * The cached default instance ({@link #getObj()}) is not changed.
     *
     * @return the new object
     */
    public Object newObjWithoutConstructor() {
        verifyExistence(true);
        Class<T> c = getClazz();
        if (c.isEnum()) {
            return fail(Messages.MOCK_ENUM_NOT_INSTANTIABLE.format(name.expected));
        }
        if (c.isInterface() || Modifier.isAbstract(c.getModifiers())) {
            return new Mock<>(this, false).obj();
        }
        try {
            return MockClasses.allocate(c);
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return fail(Messages.CLASS_INSTANTIATION_FAILED.format(name.expected) + "\n" + Invocations.describe(e), e);
        }
    }

    /**
     * The generated subclass for mocks of this class, created once.
     */
    MockClasses.Generated mockSubclass() {
        if (mockSubclass == null) {
            try {
                mockSubclass = MockClasses.subclass(getClazz());
            }
            catch (RuntimeException | LinkageError e) {
                return fail(Messages.CLASS_SUBCLASS_INSTANTIATION_FAILED.format(name.expected, Invocations.describe(e)), e);
            }
        }
        return mockSubclass;
    }

    /**
     * Retrieves all AttributeWrapper fields defined in this ClassWrapper subclass.
     * Uses reflection on the wrapper (not on student code) to find all fields of type AttributeWrapper.
     *
     * @return a list of attribute wrappers for this class
     */
    public List<Wrapper<T>> getAttributeWrappers() {
        return wrapperFields(AttributeWrapper.class);
    }

    /**
     * Retrieves all MethodWrapper fields defined in this ClassWrapper subclass.
     *
     * @return a list of method wrappers for this class
     */
    public List<Wrapper<T>> getMethodWrappers() {
        return wrapperFields(MethodWrapper.class);
    }

    /**
     * Retrieves all ConstructorWrapper fields defined in this ClassWrapper subclass.
     *
     * @return a list of constructor wrappers for this class
     */
    public List<Wrapper<T>> getConstructorWrappers() {
        return wrapperFields(ConstructorWrapper.class);
    }

    @SuppressWarnings("unchecked")
    private List<Wrapper<T>> wrapperFields(Class<?> wrapperType) {
        List<Wrapper<T>> wrappers = new ArrayList<>();
        List<Field> fields = ReflectionSupport.findFields(getClass(),
                // only fields of concrete wrapper subclasses; framework-internal fields (e.g. the cache key) are no wrappers
                f -> wrapperType.isAssignableFrom(f.getType()) && !Modifier.isStatic(f.getModifiers())
                        && !f.getDeclaringClass().isAssignableFrom(ClassWrapper.class),
                HierarchyTraversalMode.TOP_DOWN);
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(this);
                if (value != null) {
                    wrappers.add((Wrapper<T>) value);
                }
            }
            catch (ReflectiveOperationException | RuntimeException ignored) {
                // a wrapper field that cannot be read is simply not part of the structural test
            }
        }
        return wrappers;
    }



    @Override
    protected void parseExistence() {
        parseExistence(name, modifiers, superClassWrapper, interfaceWrappers);
    }

    private static String kind(boolean isInterface) {
        return isInterface ? "interface" : "class";
    }

    private static String modifierPrefix(String mods) {
        String cleaned = Arrays.stream(mods == null ? new String[0] : mods.split("\\s+"))
                .filter(m -> !m.isEmpty() && !m.equals("interface"))
                .collect(Collectors.joining(" "));
        return cleaned.isEmpty() ? "" : cleaned + " ";
    }

    private static String names(ClassWrapper<?>[] wrappers, boolean expected) {
        return Arrays.stream(wrappers)
                .map(w -> expected ? w.getExpectedName() : w.getClazz().getSimpleName())
                .collect(Collectors.joining(", "));
    }

    @Override
    public String expectedToString()
    {
        boolean isInterface = isModifierExpected("interface");
        String superName = superClassWrapper.expected != null ? " extends " + superClassWrapper.expected.getExpectedName() : "";
        String interfaceString = interfaceWrappers.expected.length > 0
                ? (isInterface ? " extends " : " implements ") + names(interfaceWrappers.expected, true)
                : "";
        return modifierPrefix(modifiers.expected) + kind(isInterface) + " " + name.expected + superName + interfaceString;
    }

    @Override
    public String actualToString() {
        ensureChecked();
        if(clazz == null) {
            return "<missing>";
        }
        boolean isInterface = clazz.isInterface();
        String superName = superClassWrapper.actual != null ? " extends " + superClassWrapper.actual.getClazz().getSimpleName() : "";
        String interfaceString = interfaceWrappers.actual != null && interfaceWrappers.actual.length > 0
                ? (isInterface ? " extends " : " implements ") + names(interfaceWrappers.actual, false)
                : "";
        return modifierPrefix(modifiers.actual) + kind(isInterface) + " " + name.actual + superName + interfaceString;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if(other instanceof ClassWrapper<?> o) {
            return name.expected.equals(o.name.expected) && expectedPackage.equals(o.expectedPackage);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name.expected, expectedPackage);
    }

    @Override
    public String toString()
    {
        String intro = switch (getOverallExistence()) {
            case EXACT -> expectedToString();
            case DEVIATES -> Messages.CLASS_DEVIATION.get();
            case MISSING -> Messages.CLASS_MISSING.get();
            default -> Messages.CLASS_UNCHECKED.get();
        };
        String pkg = expectedPackage.isEmpty() ? Messages.DEFAULT_PACKAGE.get() : expectedPackage;
        return Messages.CLASS_REPORT.format(intro, pkg, expectedToString(), actualToString());
    }

    /**
     * Tests that a getter method returns the value of an attribute.
     * Same as {@link #testGettersSetters(AttributeWrapper, MethodWrapper[])} with only a getter.
     *
     * @param attribute the attribute wrapper to test
     * @param getter the getter method wrapper to test
     */
    public void testGetter(AttributeWrapper<?,?> attribute, MethodWrapper<?,?> getter)  {
        testGettersSetters(attribute, getter);
    }

    /**
     * Tests the getter and/or setter of an attribute without conditions, i.e. the setter stores every value and
     * the getter returns the stored value. The role of each method wrapper follows from its parameters
     * (none: getter, one: setter). With generated sample values (at least five per type), up to three subtests run,
     * each on a new instance: setter -> attribute, attribute -> getter (values injected via reflection) and
     * setter -> getter. If one fails, the test fails with a message describing all three.
     * The cached default instance ({@link #getObj()}) is not changed.
     *
     * @param attribute the attribute the getter and setter belong to
     * @param gettersSetters a getter (no parameters) and/or a setter (one parameter)
     * @throws IllegalArgumentException if the method wrappers are neither one getter nor one setter
     */
    public void testGettersSetters(AttributeWrapper<?, ?> attribute, MethodWrapper<?, ?>... gettersSetters) {
        runGettersSettersTest(attribute, null, null, gettersSetters);
    }

    /**
     * Like {@link #testGettersSetters(AttributeWrapper, MethodWrapper[])}, but the setter must only store values
     * for which {@code setterCondition} holds. For other values it must keep the old value or throw an exception.
     * A condition that throws itself (e.g. a {@link NullPointerException} for {@code null}) marks the value invalid.
     *
     * @param attribute the attribute the getter and setter belong to
     * @param setterCondition the condition for values the setter accepts, e.g. {@code (Double p) -> p >= 0}
     * @param gettersSetters a setter (one parameter) and optionally a getter (no parameters)
     * @param <V> the type of values the condition is written for
     */
    public <V> void testGettersSetters(AttributeWrapper<?, ?> attribute, Predicate<V> setterCondition,
                                       MethodWrapper<?, ?>... gettersSetters) {
        runGettersSettersTest(attribute, null, setterCondition, gettersSetters);
    }

    /**
     * Like {@link #testGettersSetters(AttributeWrapper, MethodWrapper[])}, but with own sample values instead of
     * generated ones (required for types without generated values, e.g. own classes).
     *
     * @param attribute the attribute the getter and setter belong to
     * @param sampleValues the values to set (use several different ones to catch hard-coded values)
     * @param gettersSetters a getter (no parameters) and/or a setter (one parameter)
     * @param <V> the type of the sample values
     */
    public <V> void testGettersSetters(AttributeWrapper<?, ?> attribute, List<V> sampleValues,
                                       MethodWrapper<?, ?>... gettersSetters) {
        runGettersSettersTest(attribute, sampleValues, null, gettersSetters);
    }

    /**
     * Combines {@link #testGettersSetters(AttributeWrapper, List, MethodWrapper[])} (own sample values) and
     * {@link #testGettersSetters(AttributeWrapper, Predicate, MethodWrapper[])} (setter condition).
     *
     * @param attribute the attribute the getter and setter belong to
     * @param sampleValues the values to set, valid and invalid ones
     * @param setterCondition the condition for values the setter accepts
     * @param gettersSetters a setter (one parameter) and optionally a getter (no parameters)
     * @param <V> the type of the sample values
     */
    public <V> void testGettersSetters(AttributeWrapper<?, ?> attribute, List<V> sampleValues, Predicate<V> setterCondition,
                                       MethodWrapper<?, ?>... gettersSetters) {
        runGettersSettersTest(attribute, sampleValues, setterCondition, gettersSetters);
    }

    private void runGettersSettersTest(AttributeWrapper<?, ?> attribute, List<?> sampleValues, Predicate<?> setterCondition,
                                       MethodWrapper<?, ?>[] gettersSetters) {
        GettersSettersTest test = new GettersSettersTest(this, attribute, sampleValues, setterCondition, gettersSetters);
        T savedObj = obj;
        ConstructorWrapper<?> savedConstructor = objConstructor;
        Object[] savedArgs = objArgs;
        boolean savedPinned = objPinned;
        try {
            test.run();
        }
        finally {
            obj = savedObj;
            objConstructor = savedConstructor;
            objArgs = savedArgs;
            objPinned = savedPinned;
        }
    }
}
