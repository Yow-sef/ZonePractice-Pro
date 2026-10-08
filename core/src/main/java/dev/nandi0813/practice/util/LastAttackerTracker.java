package dev.nandi0813.practice.util;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LastAttackerTracker {

    private static final long LAST_ATTACKER_EXPIRY_MS = 4_000L;

    private final Map<UUID, AttackRecord> lastAttackerMap = new HashMap<>();

    public void recordAttack(Player victim, Player attacker) {
        if (victim == null || attacker == null || victim.equals(attacker)) {
            return;
        }

        lastAttackerMap.put(
                victim.getUniqueId(),
                new AttackRecord(attacker.getUniqueId(), System.currentTimeMillis())
        );
    }

    public @Nullable Player getLastAttacker(
            Player victim,
            Collection<? extends Player> candidates
    ) {
        AttackRecord record = lastAttackerMap.get(victim.getUniqueId());

        if (record == null || System.currentTimeMillis() - record.time() > LAST_ATTACKER_EXPIRY_MS) {
            return null;
        }

        for (Player candidate : candidates) {
            if (record.attacker().equals(candidate.getUniqueId())) {
                return candidate;
            }
        }

        return null;
    }

    private record AttackRecord(UUID attacker, long time) {
    }
}