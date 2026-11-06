package io.github.valentinherrmann.levenshtein;

import de.tum.cit.ase.ares.api.MirrorOutput;
import de.tum.cit.ase.ares.api.StrictTimeout;
import de.tum.cit.ase.ares.api.jupiter.Hidden;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Hidden counterpart of {@link LevenshteinTest}: composes {@link Hidden}, {@link StrictTimeout}{@code (5)}
 * and {@link MirrorOutput}.
 *
 * <p>Ares requires a {@code @Deadline} for hidden tests; add it to the test class (or method) together
 * with the exam specific {@code @Policy}.</p>
 *
 * @see LevenshteinTest
 */
@Hidden
@StrictTimeout(5)
@MirrorOutput
@Inherited
@Documented
@Retention(RUNTIME)
@Target({TYPE, ANNOTATION_TYPE})
public @interface HiddenLevenshteinTest {
}
