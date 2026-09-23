package dev.skirmish.combat;

/** Kills and deaths since joining the current server (reset on every join). */
public final class SessionStats implements CombatListener {
    private int kills;
    private int deaths;

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    void reset() {
        kills = 0;
        deaths = 0;
    }

    @Override
    public void onKill(Fight fight) {
        kills++;
    }

    @Override
    public void onOwnDeath(OwnDeath death) {
        deaths++;
    }
}
