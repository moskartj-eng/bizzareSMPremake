package de.trimabilities;

import java.util.Locale;

public enum Ability {
    SENTRY("Pfeilsalve: schießt mehrere Pfeile nach vorne"),
    DUNE("Dash: schleudert dich nach vorne"),
    COAST("Welle: stößt Gegner weg + Delfingunst"),
    WILD("Enterhaken: zieht dich an Block oder Entity"),
    WARD("Schild: Absorption + Resistenz"),
    EYE("Laser: 3 Sekunden Laserstrahl, der deinem Blick folgt"),
    VEX("Dash: schneller Vorwärts-Dash"),
    TIDE("Strudel: zieht Gegner zu dir"),
    SNOUT("Wut: Stärke + Feuerresistenz"),
    RIB("Inferno: setzt Gegner in der Nähe in Brand"),
    SPIRE("Blitz: Blitzschlag auf das anvisierte Ziel"),
    SILENCE("Stille: Dunkelheit + Verlangsamung für Gegner"),
    RAISER("Gegner in der Nähe in die Luft schleudern"),
    HOST("Gegner in Spinnweben fangen"),
    SHAPER("Lange Unsichtbarkeit + Schadensresistenz"),
    WAYFINDER("Pfadfinder: Geschwindigkeit + Sprungkraft"),
    FLOW("Windstoß: schleudert dich in die Luft"),
    BOLT("Schock: Blitz-Effekt + Schaden um dich herum");

    private final String description;

    Ability(String description) { this.description = description; }

    public String key() { return name().toLowerCase(Locale.ROOT); }
    public String description() { return description; }

    public static Ability fromKey(String key) {
        try { return valueOf(key.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return null; }
    }
}
