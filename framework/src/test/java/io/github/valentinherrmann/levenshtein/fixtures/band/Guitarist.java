package io.github.valentinherrmann.levenshtein.fixtures.band;

/** Refers to {@link Drummer}, which refers back (lazy member types, issue #33). */
public class Guitarist {
    private Drummer drummer;
    private Object bassist; // the student did not write a class Bassist
    private double volume;

    public Guitarist(double volume, Drummer drummer) {
        this.volume = volume;
        this.drummer = drummer;
    }

    public Drummer getDrummer() { return drummer; }

    public void setDrummer(Drummer drummer) { this.drummer = drummer; }

    public Object getBassist() { return bassist; }

    public void setBassist(Object bassist) { this.bassist = bassist; }

    public double getVolume() { return volume; }

    /** Only differs from {@link #setDrummer(Drummer)} in the parameter type: must not be taken for it. */
    public void setDrummer(String name) { }
}
