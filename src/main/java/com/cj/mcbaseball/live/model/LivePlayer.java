package com.cj.mcbaseball.live.model;

/**
 * A real player as far as Live Mode needs to know. Strings are never null (may be empty).
 *
 * @param shortName "A. Judge" style
 * @param jersey    jersey number as text ("99"), "" if unknown
 * @param position  defensive position abbreviation in this game ("CF", "DH", "P"), "" if unknown
 * @param battingOrder lineup slot 1-9, or -1 if not in the batting order
 */
public record LivePlayer(int id, String fullName, String shortName, String jersey, String position, String batSide, String pitchHand, int battingOrder) {

    public static final LivePlayer NONE = new LivePlayer(0, "", "", "", "", "", "", -1);

    public LivePlayer {
        fullName = fullName == null ? "" : fullName;
        shortName = shortName == null ? "" : shortName;
        jersey = jersey == null ? "" : jersey;
        position = position == null ? "" : position;
        batSide = batSide == null ? "" : batSide;
        pitchHand = pitchHand == null ? "" : pitchHand;
    }

    public boolean known() {
        return this.id > 0 || !this.fullName.isEmpty();
    }

    /** "A. Judge", falling back to the full name. */
    public String display() {
        return !this.shortName.isEmpty() ? this.shortName : this.fullName;
    }

    /** "A. Judge #99" */
    public String displayWithNumber() {
        return this.jersey.isEmpty() ? this.display() : this.display() + " #" + this.jersey;
    }

    /** Builds "A. Judge" from a first name and last name; tolerant of blanks. */
    public static String shortName(String first, String last, String full) {
        String f = first == null ? "" : first.trim();
        String l = last == null ? "" : last.trim();
        if (!f.isEmpty() && !l.isEmpty()) {
            return f.charAt(0) + ". " + l;
        }
        if (!l.isEmpty()) {
            return l;
        }
        return full == null ? "" : full.trim();
    }
}
