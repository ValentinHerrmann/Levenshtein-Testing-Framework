package io.github.valentinherrmann.levenshtein;

import de.tum.cit.ase.ares.api.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Invocation of student code through Ares 2's {@link ReflectionTestUtils}.
 *
 * <p>The rethrowing variants are used, so an exception thrown by student code reaches the framework
 * unchanged (instead of being wrapped into a generic failure). Ares' own failures for wrong arguments
 * or inaccessible members arrive as {@link AssertionError}.</p>
 */
final class Invocations {

    private Invocations() {
    }

    static Object invoke(Method method, Object target, Object... args) throws Throwable {
        return ReflectionTestUtils.invokeNonPublicMethodRethrowing(target, method, args);
    }

    static Object newInstance(Constructor<?> constructor, Object... args) throws Throwable {
        return ReflectionTestUtils.newInstanceFromNonPublicConstructorRethrowing(constructor, args);
    }

    /**
     * Rethrows throwables that must never be turned into an ordinary test failure:
     * assertion failures (they already are failures, often from Ares), security violations reported by
     * Ares, and virtual machine errors.
     *
     * @param t the throwable caught while running student code
     */
    static void rethrowIfCritical(Throwable t) {
        if (t instanceof AssertionError e) {
            throw e;
        }
        if (t instanceof SecurityException e) {
            throw e;
        }
        if (t instanceof VirtualMachineError e) {
            throw e;
        }
    }

    /**
     * @param t a throwable
     * @return a short, student-readable description like {@code IllegalArgumentException: year < 0}
     */
    static String describe(Throwable t) {
        if (t == null) {
            return "<none>";
        }
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
