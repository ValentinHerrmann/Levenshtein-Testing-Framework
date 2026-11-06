package io.github.valentinherrmann.levenshtein;

import de.tum.cit.ase.ares.api.MirrorOutput;
import de.tum.cit.ase.ares.api.StrictTimeout;
import de.tum.cit.ase.ares.api.jupiter.Public;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a test class as a <b>public</b> Ares 2 test class using the Levenshtein Testing Framework.
 *
 * <p>This is a composed annotation of</p>
 * <ul>
 *   <li>{@link Public} - activates Ares 2 supervision for every test of the class. Without an Ares
 *       test-type annotation a test runs <b>completely unsupervised</b>, even if it carries a
 *       {@code @Policy}.</li>
 *   <li>{@link StrictTimeout}{@code (5)} - default deadline per test. Put your own {@code @StrictTimeout}
 *       on the class or a method to override it.</li>
 *   <li>{@link MirrorOutput} - mirror student console output into the test log.</li>
 * </ul>
 *
 * <p>The security policy is exam specific and therefore <b>not</b> part of this annotation. Every exam
 * test class must add its own {@code @Policy}:</p>
 * <pre>{@code
 * @LevenshteinTest
 * @Policy(value = "test/SecurityPolicy.yaml", withinPath = "classes/org/example/exam")
 * class ExamTest { ... }
 * }</pre>
 *
 * <p>Use {@link HiddenLevenshteinTest} for hidden tests.</p>
 *
 * @see HiddenLevenshteinTest
 */
@Public
@StrictTimeout(5)
@MirrorOutput
@Inherited
@Documented
@Retention(RUNTIME)
@Target({TYPE, ANNOTATION_TYPE})
public @interface LevenshteinTest {
}
