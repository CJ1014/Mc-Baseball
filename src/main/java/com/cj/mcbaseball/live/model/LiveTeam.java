package com.cj.mcbaseball.live.model;

import java.util.Locale;

/**
 * A real-world team as reported by a data provider. All strings are non-null (possibly empty);
 * use the display helpers, which fall back gracefully when a provider omits a field.
 */
public record LiveTeam(int id, String name, String abbreviation, String clubName, String locationName) {

    public static final LiveTeam UNKNOWN = new LiveTeam(0, "", "", "", "");

    public LiveTeam {
        name = name == null ? "" : name;
        abbreviation = abbreviation == null ? "" : abbreviation;
        clubName = clubName == null ? "" : clubName;
        locationName = locationName == null ? "" : locationName;
    }

    /** "NYY". Falls back to the first letters of the name, then "???". */
    public String displayAbbr() {
        if (!this.abbreviation.isBlank()) {
            return this.abbreviation;
        }
        String base = !this.clubName.isBlank() ? this.clubName : this.name;
        base = base.replaceAll("[^A-Za-z]", "");
        if (base.isEmpty()) {
            return "???";
        }
        return base.substring(0, Math.min(3, base.length())).toUpperCase(Locale.ROOT);
    }

    /** "Yankees". Falls back to the full name, then the abbreviation. */
    public String displayShort() {
        if (!this.clubName.isBlank()) {
            return this.clubName;
        }
        if (!this.name.isBlank()) {
            return this.name;
        }
        return this.displayAbbr();
    }

    /** "New York Yankees". Falls back to the short name. */
    public String displayFull() {
        return !this.name.isBlank() ? this.name : this.displayShort();
    }
}
