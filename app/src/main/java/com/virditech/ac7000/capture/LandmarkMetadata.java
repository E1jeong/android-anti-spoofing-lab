package com.virditech.ac7000.capture;

import org.json.JSONException;
import org.json.JSONObject;

/** Immutable labels belonging to one saved, unmirrored RGB frame. */
public final class LandmarkMetadata {
    private static final String[] NAMES = {
            "eyeImageLeft", "eyeImageRight", "noseTip", "mouthImageLeft", "mouthImageRight"
    };
    private final int width;
    private final int height;
    private final RectValue face;
    private final float[][] points = new float[5][];
    private final String extractor;
    private final String version;
    private final long timestampNs;
    private final String className;

    public LandmarkMetadata(int width, int height, RectValue face, float[][] points,
                            String extractor, String version, long timestampNs, String className) {
        if (width <= 0 || height <= 0 || face == null) {
            throw new IllegalArgumentException("RGB dimensions and face are required");
        }
        this.width = width;
        this.height = height;
        this.face = face;
        this.extractor = extractor;
        this.version = version;
        this.timestampNs = timestampNs;
        this.className = className;
        if (points != null) {
            for (int i = 0; i < Math.min(5, points.length); i++) {
                if (points[i] != null && points[i].length >= 2) {
                    this.points[i] = new float[] {points[i][0], points[i][1]};
                }
            }
        }
    }

    public String build(String imageSha256) {
        try {
            JSONObject root = new JSONObject();
            root.put("schemaVersion", 1);
            root.put("image", "RGB.bmp");
            root.put("coordinateSystem", "original_image_pixels");
            root.put("imageWidth", width);
            root.put("imageHeight", height);
            root.put("imageSha256", imageSha256);
            root.put("rgbTimestampNs", timestampNs);
            JSONObject provenance = new JSONObject();
            provenance.put("name", extractor);
            provenance.put("version", version == null ? JSONObject.NULL : version);
            root.put("extractor", provenance);
            root.put("labelType", "pseudo_label");
            root.put("reviewStatus", "auto_filtered");
            JSONObject selection = new JSONObject();
            selection.put("version", "android-capture-v1");
            selection.put("manualReviewPerformed", false);
            selection.put("faceExpansionPerSide", 0.25);
            root.put("trainingSelection", selection);
            int[] indices = {0, 1, 2, 3, 4};
            sortImagePair(indices, 0, 1);
            sortImagePair(indices, 3, 4);
            JSONObject records = new JSONObject();
            boolean extracted = false;
            for (int i = 0; i < NAMES.length; i++) {
                int sourceIndex = indices[i];
                float[] point = points[sourceIndex];
                boolean finite = point != null && Float.isFinite(point[0]) && Float.isFinite(point[1]);
                extracted |= finite;
                boolean masked = i >= 2 && ("dental_white".equals(className)
                        || "dental_black".equals(className));
                String reason = point == null ? "missing" : !finite ? "non_finite"
                        : point[0] < 0 || point[0] >= width || point[1] < 0 || point[1] >= height
                                ? "outside_image"
                        : !insideExpandedFace(point) ? "outside_expanded_face"
                        : masked ? "masked_by_class" : "selected";
                JSONObject record = new JSONObject();
                record.put("x", finite ? point[0] : JSONObject.NULL);
                record.put("y", finite ? point[1] : JSONObject.NULL);
                record.put("sourceIndex", sourceIndex);
                record.put("useForTraining", "selected".equals(reason));
                record.put("reason", reason);
                // FaceMe coordinates do not establish point visibility.
                record.put("visibility", masked ? "occluded_by_class" : "unknown");
                records.put(NAMES[i], record);
            }
            root.put("status", extracted ? "extracted" : "not_detected");
            root.put("points", records);
            return root.toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException("Unable to build landmark metadata", e);
        }
    }

    private void sortImagePair(int[] indices, int first, int second) {
        float[] a = points[first];
        float[] b = points[second];
        if (a != null && b != null && Float.isFinite(a[0]) && Float.isFinite(b[0])
                && a[0] > b[0]) {
            indices[first] = second;
            indices[second] = first;
        }
    }

    private boolean insideExpandedFace(float[] point) {
        float marginX = (face.right - face.left) * 0.25f;
        float marginY = (face.bottom - face.top) * 0.25f;
        return face.right > face.left && face.bottom > face.top
                && point[0] >= face.left - marginX && point[0] <= face.right + marginX
                && point[1] >= face.top - marginY && point[1] <= face.bottom + marginY;
    }
}
