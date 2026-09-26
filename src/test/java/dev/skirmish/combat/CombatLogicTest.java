package dev.skirmish.combat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatLogicTest {
    private static final Combatant ME = new Combatant(1, UUID.randomUUID(), "Me", true, true);
    private static final Combatant FOE = new Combatant(2, UUID.randomUUID(), "Foe", true, false);
    private static final Combatant OTHER = new Combatant(3, UUID.randomUUID(), "Other", true, false);
    private static final Combatant ZOMBIE = new Combatant(4, UUID.randomUUID(), "Zombie", false, false);

    private final List<String> log = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private CombatLogic logic;
    private boolean playersOnly = true;

    @BeforeEach
    void setUp() {
        logic = new CombatLogic(new CombatLogic.Config() {
            @Override
            public long killWindowMs() {
                return 10_000;
            }

            @Override
            public long fightTimeoutMs() {
                return 20_000;
            }

            @Override
            public boolean playersOnly() {
                return playersOnly;
            }
        }, log::add, (msg, t) -> {
            throw new AssertionError(msg, t);
        });
        logic.addListener(new CombatListener() {
            @Override
            public void onFightStart(Fight fight) {
                events.add("start " + fight.opponent().name());
            }

            @Override
            public void onKill(Fight fight) {
                events.add("kill " + fight.opponent().name());
            }

            @Override
            public void onFightEnd(Fight fight) {
                events.add("end " + fight.opponent().name() + " " + fight.endReason());
            }

            @Override
            public void onTotemPop(Combatant entity, Fight fight) {
                events.add("totem " + entity.name() + (fight == null ? "" : " in fight"));
            }

            @Override
            public void onOwnDeath(OwnDeath death) {
                events.add("own death killer=" + (death.killer() == null ? "none" : death.killer().name()));
            }
        });
    }

    private void hit(Combatant attacker, Combatant victim, long t) {
        logic.onDamage(new DamageInfo(victim, attacker, attacker, "minecraft:player_attack", t));
    }

    @Test
    void killWithinWindowIsAttributed() {
        hit(ME, FOE, 1_000);
        logic.onHealth(FOE, 20, 0, 20, 1_001);
        hit(ME, FOE, 2_000);
        logic.onHealth(FOE, 14, 0, 20, 2_050);
        hit(ME, FOE, 3_000);
        logic.onHealth(FOE, 0, 0, 20, 3_050);
        logic.onDeath(FOE, "entity event 3", 3_060);

        assertEquals(List.of("start Foe", "kill Foe", "end Foe KILL"), events);
        Fight fight = logic.lastFinished();
        assertNotNull(fight);
        assertEquals(3, fight.hitsDealt());
        assertEquals(20f, fight.damageDealt(), 1e-4);
        assertTrue(fight.isDamageKnown());
        assertEquals(2_050, fight.durationMs(99_999));
        assertTrue(logic.activeFights().isEmpty());
    }

    @Test
    void finalBlowAddsLastKnownHealthWhenDeathEventComesFirst() {
        hit(ME, FOE, 1_000);
        logic.onHealth(FOE, 20, 0, 20, 1_001);
        hit(ME, FOE, 2_000);
        logic.onHealth(FOE, 12, 2, 20, 2_030);
        hit(ME, FOE, 3_000);
        logic.onDeath(FOE, "entity event 3", 3_001);
        logic.onHealth(FOE, 0, 0, 20, 3_030);
        Fight fight = logic.lastFinished();
        assertEquals(FightEndReason.KILL, fight.endReason());
        assertEquals(20f, fight.damageDealt(), 1e-4);
    }

    @Test
    void finalBlowIsNotGuessedWhenHealthWasNeverSeenDropping() {
        hit(ME, FOE, 1_000);
        logic.onHealth(FOE, 20, 0, 20, 1_001);
        hit(ME, FOE, 2_000);
        logic.onDeath(FOE, "entity event 3", 2_001);
        Fight fight = logic.lastFinished();
        assertEquals(FightEndReason.KILL, fight.endReason());
        assertFalse(fight.isDamageKnown());
        assertEquals(0f, fight.damageDealt());
    }

    @Test
    void deathLongAfterMyLastHitIsNotAKill() {
        hit(ME, FOE, 1_000);
        hit(FOE, ME, 12_000);
        logic.onDeath(FOE, "entity event 3", 15_000);
        assertEquals(FightEndReason.OPPONENT_DIED, logic.lastFinished().endReason());
        assertFalse(events.contains("kill Foe"));
        assertTrue(log.stream().anyMatch(l -> l.contains("kill NOT counted") && l.contains("> kill window")));
    }

    @Test
    void hiddenHealthMeansDamageUnknown() {
        hit(ME, FOE, 1_000);
        logic.onHealth(FOE, 20, 0, 20, 1_010);
        hit(ME, FOE, 2_000);
        logic.onHealth(FOE, 20, 0, 20, 2_010);
        Fight fight = logic.fightWith(FOE.uuid());
        assertNotNull(fight);
        assertFalse(fight.isDamageKnown());
        assertEquals(0f, fight.damageDealt());
    }

    @Test
    void healthDropAfterSomeoneElsesHitIsNotMine() {
        hit(ME, FOE, 1_000);
        logic.onHealth(FOE, 20, 0, 20, 1_001);
        hit(OTHER, FOE, 2_000);
        logic.onHealth(FOE, 15, 0, 20, 2_010);
        assertEquals(0f, logic.fightWith(FOE.uuid()).damageDealt());
    }

    @Test
    void absorptionCountsTowardsDamage() {
        hit(ME, FOE, 1_000);
        logic.onHealth(FOE, 20, 4, 20, 1_001);
        hit(ME, FOE, 2_000);
        logic.onHealth(FOE, 18, 0, 20, 2_010);
        assertEquals(6f, logic.fightWith(FOE.uuid()).damageDealt(), 1e-4);
    }

    @Test
    void totemsAreCountedPerFight() {
        hit(ME, FOE, 1_000);
        logic.onTotem(FOE, 1_500);
        logic.onTotem(FOE, 5_000);
        logic.onTotem(OTHER, 5_100);
        assertEquals(2, logic.fightWith(FOE.uuid()).opponentTotems());
        assertTrue(events.contains("totem Foe in fight"));
        assertTrue(events.contains("totem Other"));
    }

    @Test
    void ownDeathNamesKillerAndEndsFights() {
        hit(ME, FOE, 1_000);
        hit(FOE, ME, 2_000);
        hit(ME, OTHER, 2_500);
        OwnDeath death = logic.onOwnDeath(null, 3_000);
        assertSame(FOE, death.killer());
        assertEquals(2, death.fights().size());
        assertTrue(logic.activeFights().isEmpty());
        assertEquals("own death killer=Foe", events.get(2));
        assertEquals(FightEndReason.OWN_DEATH, logic.lastFinished().endReason());
    }

    @Test
    void ownDeathWithoutRecentAttackerHasNoKiller() {
        hit(FOE, ME, 1_000);
        OwnDeath death = logic.onOwnDeath(null, 30_000);
        assertNull(death.killer());
    }

    @Test
    void fightTimesOut() {
        hit(ME, FOE, 1_000);
        logic.tick(20_000);
        assertEquals(1, logic.activeFights().size());
        logic.tick(21_001);
        assertTrue(logic.activeFights().isEmpty());
        assertEquals(FightEndReason.TIMEOUT, logic.lastFinished().endReason());
    }

    @Test
    void deathSignalsAreDeduplicated() {
        hit(ME, FOE, 1_000);
        logic.onHealth(FOE, 10, 0, 20, 1_001);
        logic.onHealth(FOE, 0, 0, 20, 1_500);
        logic.onDeath(FOE, "entity event 3", 1_510);
        assertEquals(1, events.stream().filter(e -> e.startsWith("kill")).count());
    }

    @Test
    void mobsIgnoredWhenPlayersOnly() {
        hit(ME, ZOMBIE, 1_000);
        assertTrue(logic.activeFights().isEmpty());
        playersOnly = false;
        hit(ME, ZOMBIE, 2_000);
        assertEquals(1, logic.activeFights().size());
    }

    @Test
    void environmentalDamageStartsNoFight() {
        logic.onDamage(new DamageInfo(ME, null, null, "minecraft:fall", 1_000));
        assertTrue(logic.activeFights().isEmpty());
    }

    @Test
    void worldChangeEndsEverything() {
        hit(ME, FOE, 1_000);
        logic.onHealth(FOE, 20, 0, 20, 1_001);
        logic.reset("test", 2_000);
        assertTrue(logic.activeFights().isEmpty());
        assertEquals(FightEndReason.WORLD_CHANGE, logic.lastFinished().endReason());
        assertEquals(0, logic.trackedHealthCount());
    }

    @Test
    void killerNamedByTheServerWhenNoHitPointsAtAnyone() {
        OwnDeath death = logic.onOwnDeath(null, 5_000, OTHER);
        assertEquals(OTHER, death.killer());
    }

    @Test
    void anonymousHitsAttributedToMeStillMakeAKill() {
        // What CombatTracker produces on HolyWorld once the attacker is inferred from my click.
        logic.onDamage(new DamageInfo(FOE, ME, null, "minecraft:generic (inferred)", 1_000));
        logic.onDeath(FOE, "entity event 3", 1_400);
        assertTrue(events.contains("kill Foe"), events.toString());
    }
}
