package io.github.valentinherrmann.levenshtein.fixtures.band;

/** Refers to {@link Guitarist}, which refers back (lazy member types, issue #33). */
public class Drummer {
    private Guitarist guitarist;

    public Drummer() { }

    public Drummer(double tempo, Guitarist guitarist) {
        this.guitarist = guitarist;
    }

    public Guitarist getGuitarist() { return guitarist; }

    public void setGuitarist(Guitarist guitarist) { this.guitarist = guitarist; }
}
