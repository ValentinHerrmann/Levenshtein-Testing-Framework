package io.github.valentinherrmann.levenshtein.fixtures.misnamed;

/** The student misspelled the class {@code Drummer} as {@link Drumer} (lazy member types, issue #33). */
public class Guitarist {
    private Drumer drummer;

    public Guitarist(double volume, Drumer drummer) {
        this.drummer = drummer;
    }

    public Drumer getDrummer() { return drummer; }

    public void setDrummer(Drumer drummer) { this.drummer = drummer; }
}
