package com.virditech.ac7000.recognition;

/**
 * Result of face identification or 1:1 verification.
 */
public record RecognitionResult(boolean isRecognized, FaceTemplate matchedTemplate,
                                float similarityScore, float threshold, long elapsedMs,
                                String statusMessage) {

    public static RecognitionResult notRecognized(float similarityScore, float threshold, long elapsedMs, String message) {
        return new RecognitionResult(false, null, similarityScore, threshold, elapsedMs, message);
    }

    public static RecognitionResult success(FaceTemplate template, float similarityScore, float threshold, long elapsedMs) {
        return new RecognitionResult(true, template, similarityScore, threshold, elapsedMs, "Success");
    }
}
