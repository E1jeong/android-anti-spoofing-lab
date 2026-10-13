package com.virditech.ac7000.capture;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable validated capture configuration snapshot for one 100-sample session.
 */
public final class CaptureConfig {
    public static final String ENV_INDOOR = "indoor";
    public static final String ENV_OUTDOOR = "outdoor";

    public static final String QUALITY_HIGH = CaptureStorage.QUALITY_HIGH;
    public static final String QUALITY_MEDIUM = CaptureStorage.QUALITY_MEDIUM;
    public static final String QUALITY_OFF = CaptureStorage.QUALITY_OFF;

    public static final int LEVEL_HIGH = 2; // FaceQualityLevel.HIGH
    public static final int LEVEL_MEDIUM = 1; // COLLECTION_MEDIUM_QUALITY_LEVEL
    public static final int LEVEL_NONE = -1;

    public static final List<String> SUPPORTED_CLASSES = Collections.unmodifiableList(Arrays.asList(
            "live",
            "display",
            "picture",
            "print",
            "mask",
            "pmask",
            "curved_picture",
            "curved_print",
            "curved_mask",
            "curved_pmask",
            "dental_white",
            "dental_black"
    ));

    public final String className;
    public final String environment;
    public final String qualityMode;
    public final int minQualityLevel;

    private CaptureConfig(String className, String environment, String qualityMode, int minQualityLevel) {
        this.className = className;
        this.environment = environment;
        this.qualityMode = qualityMode;
        this.minQualityLevel = minQualityLevel;
    }

    public static CaptureConfig create(String className, String environment, String qualityMode) {
        if (className == null || className.trim().isEmpty()) {
            throw new IllegalArgumentException("className must not be empty");
        }
        String normalizedClass = className.trim().toLowerCase(Locale.US);
        if (!SUPPORTED_CLASSES.contains(normalizedClass)) {
            throw new IllegalArgumentException("Unsupported class: " + className);
        }

        if (environment == null || environment.trim().isEmpty()) {
            throw new IllegalArgumentException("environment must not be empty");
        }
        String normalizedEnv = environment.trim().toLowerCase(Locale.US);
        if (!ENV_INDOOR.equals(normalizedEnv) && !ENV_OUTDOOR.equals(normalizedEnv)) {
            throw new IllegalArgumentException("Invalid environment (must be indoor or outdoor): " + environment);
        }

        String normalizedQuality = qualityMode == null ? null : qualityMode.trim().toLowerCase(Locale.US);
        if ("live".equals(normalizedClass)) {
            if (ENV_INDOOR.equals(normalizedEnv)) {
                if (QUALITY_HIGH.equals(normalizedQuality)) {
                    return new CaptureConfig(normalizedClass, normalizedEnv, QUALITY_HIGH, LEVEL_HIGH);
                } else if (QUALITY_MEDIUM.equals(normalizedQuality)) {
                    return new CaptureConfig(normalizedClass, normalizedEnv, QUALITY_MEDIUM, LEVEL_MEDIUM);
                } else {
                    throw new IllegalArgumentException("live indoor capture requires quality 'high' or 'medium', got: " + qualityMode);
                }
            } else {
                // Outdoor live capture must force quality 'off'
                if (normalizedQuality != null && !QUALITY_OFF.equals(normalizedQuality)) {
                    throw new IllegalArgumentException("live outdoor capture forces quality 'off', got: " + qualityMode);
                }
                return new CaptureConfig(normalizedClass, normalizedEnv, QUALITY_OFF, LEVEL_NONE);
            }
        } else {
            // Non-live classes do not use quality evaluation
            if (normalizedQuality != null && !normalizedQuality.isEmpty()) {
                throw new IllegalArgumentException("Non-live class '" + normalizedClass + "' does not permit quality mode: " + qualityMode);
            }
            return new CaptureConfig(normalizedClass, normalizedEnv, null, LEVEL_NONE);
        }
    }

    public boolean isLive() {
        return "live".equals(className);
    }

    public String toSummaryLabel() {
        if (isLive()) {
            return String.format(Locale.US, "%s · %s · %s",
                    className.toUpperCase(Locale.US),
                    environment.toUpperCase(Locale.US),
                    qualityMode.toUpperCase(Locale.US));
        } else {
            return String.format(Locale.US, "%s · %s",
                    className.toUpperCase(Locale.US),
                    environment.toUpperCase(Locale.US));
        }
    }

    public boolean isQualityCheckRequired() {
        return isLive() && !QUALITY_OFF.equals(qualityMode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CaptureConfig that = (CaptureConfig) o;
        return minQualityLevel == that.minQualityLevel
                && Objects.equals(className, that.className)
                && Objects.equals(environment, that.environment)
                && Objects.equals(qualityMode, that.qualityMode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, environment, qualityMode, minQualityLevel);
    }

    @Override
    public String toString() {
        return "CaptureConfig{"
                + "class='" + className + '\''
                + ", env='" + environment + '\''
                + ", quality='" + qualityMode + '\''
                + ", minLevel=" + minQualityLevel
                + '}';
    }
}
