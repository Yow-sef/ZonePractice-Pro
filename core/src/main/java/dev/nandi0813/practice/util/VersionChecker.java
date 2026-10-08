package dev.nandi0813.practice.util;

import dev.nandi0813.practice.ZonePractice;
import org.bukkit.Bukkit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for detecting the running Bukkit/MC version.
 */
public final class VersionChecker {

    private static final Pattern MC_VERSION_PATTERN =
            Pattern.compile("\\(MC: ([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)\\)");

    private static volatile BukkitVersion bukkitVersion;

    /**
     * Returns the detected BukkitVersion, or null if the server version is unsupported.
     * The result is cached after the first successful detection.
     */
    public static BukkitVersion getBukkitVersion() {
        if (bukkitVersion != null) {
            return bukkitVersion;
        }

        synchronized (VersionChecker.class) {
            if (bukkitVersion != null) {
                return bukkitVersion;
            }

            final String versionString = Bukkit.getVersion();
            final String mcVersion = extractMcVersion(versionString);

            if (mcVersion == null) {
                ZonePractice.getInstance().getLogger().warning("Could not extract MC version from: " + versionString);
                return null;
            }

            if (mcVersion.equals("1.21") || mcVersion.startsWith("1.21.")) {
                bukkitVersion = BukkitVersion.v1_21_R3;
            } else if (mcVersion.equals("26.1") || mcVersion.startsWith("26.1.")) {
                bukkitVersion = BukkitVersion.v_26_1_R1;
            } else if (mcVersion.equals("26.2") || mcVersion.startsWith("26.2.")) {
                bukkitVersion = BukkitVersion.v_26_2_R1;
            } else if (mcVersion.equals("26.3") || mcVersion.startsWith("26.3.")) {
                bukkitVersion = BukkitVersion.v_26_3_R1;
            } else {
                ZonePractice.getInstance().getLogger().warning("Could not extract MC version from: " + versionString);
            }

            return bukkitVersion;
        }
    }

    /**
     * Returns whether the running server version is at least the given version.
     */
    public static boolean isAtLeast(final BukkitVersion minimum) {
        final BukkitVersion current = getBukkitVersion();
        return current != null && minimum != null
                && current.ordinal() >= minimum.ordinal();
    }

    private static String extractMcVersion(final String versionString) {
        if (versionString == null) {
            return null;
        }

        final Matcher matcher = MC_VERSION_PATTERN.matcher(versionString);
        return matcher.find() ? matcher.group(1) : null;
    }

    public enum BukkitVersion {
        v1_21_R3,
        v_26_1_R1,
        v_26_2_R1,
        v_26_3_R1
    }
}