package dev.skirmish.module.killfx;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.component.FireworkExplosion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillPatternsTest {
    private record P(KillPatterns.Role role, double x, double y, double z, double vx, double vy, double vz) {
    }

    private static List<P> emit(KillFxModule.Burst burst, float viewYaw, int[] fireworks) {
        List<P> out = new ArrayList<>();
        KillPatterns.emit(burst, RandomSource.create(1), 10, 64, -5, viewYaw, 1f, new int[]{0xFF0000}, new KillPatterns.Sink() {
            @Override
            public void particle(KillPatterns.Role role, double x, double y, double z, double vx, double vy, double vz) {
                out.add(new P(role, x, y, z, vx, vy, vz));
            }

            @Override
            public void firework(FireworkExplosion.Shape shape, int[] colors, double x, double y, double z) {
                fireworks[0]++;
            }
        });
        return out;
    }

    @Test
    void everyParticleBurstHasAShape() {
        for (KillFxModule.Burst b : KillFxModule.Burst.values()) {
            if (b.fx() != null) {
                continue;
            }
            int[] fw = {0};
            List<P> ps = emit(b, 0f, fw);
            assertTrue(ps.size() >= 12, b + " emits " + ps.size());
            assertEquals(b == KillFxModule.Burst.FIREWORK ? 1 : 0, fw[0], b + " fireworks");
        }
    }

    @Test
    void theHeartFacesTheViewer() {
        // Looking along +Z (yaw 0): the heart lies in the X/Y plane, all at the body's Z.
        for (P p : emit(KillFxModule.Burst.HEARTS, 0f, new int[1])) {
            if (p.role() == KillPatterns.Role.MAIN) {
                assertEquals(-5, p.z(), 1e-9);
            }
        }
        // Turned to look along X (yaw 90): now it lies in the Z/Y plane.
        for (P p : emit(KillFxModule.Burst.HEARTS, 90f, new int[1])) {
            if (p.role() == KillPatterns.Role.MAIN) {
                assertEquals(10, p.x(), 1e-9);
            }
        }
    }

    @Test
    void sparkRaysAreStraightLinesFromTheChest() {
        List<P> ps = emit(KillFxModule.Burst.SPARKS, 0f, new int[1]);
        // Each ray's particles move along the line from the chest through them, faster the farther out they are.
        for (P p : ps) {
            double ox = p.x() - 10;
            double oy = p.y() - 65.1;
            double oz = p.z() + 5;
            double cross = Math.abs(oy * p.vz() - oz * p.vy()) + Math.abs(oz * p.vx() - ox * p.vz()) + Math.abs(ox * p.vy() - oy * p.vx());
            assertEquals(0, cross, 1e-9);
        }
    }

    @Test
    void fireworkShapesHaveSparks() {
        for (FireworkExplosion.Shape s : FireworkExplosion.Shape.values()) {
            assertTrue(KillPatterns.fireworkSparks(s, RandomSource.create(2)).length >= 12, s.name());
        }
    }
}
