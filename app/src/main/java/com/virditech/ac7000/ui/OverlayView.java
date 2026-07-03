package com.virditech.ac7000.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import com.virditech.ac7000.model.ClassificationResult;

import java.util.Locale;

public final class OverlayView extends View {
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint collectionGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint collectionFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint collectionTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint countdownPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Rect rgbBox;
    private Rect irBox;
    private ClassificationResult result;
    private boolean showIr;
    private boolean calibrationMode;
    private boolean isCollecting;
    private int collectionSector;
    private int collectionCountdownSeconds;

    public OverlayView(Context context) {
        super(context);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5f);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28f);
        textPaint.setShadowLayer(5f, 1f, 1f, Color.BLACK);
        guidePaint.setColor(Color.WHITE);
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(6f);
        collectionGridPaint.setColor(Color.argb(96, 255, 255, 255));
        collectionGridPaint.setStyle(Paint.Style.STROKE);
        collectionGridPaint.setStrokeWidth(4f);
        collectionFillPaint.setColor(Color.argb(72, 0, 200, 83));
        collectionFillPaint.setStyle(Paint.Style.FILL);
        collectionTextPaint.setColor(Color.WHITE);
        collectionTextPaint.setTextSize(26f);
        collectionTextPaint.setTextAlign(Paint.Align.CENTER);
        collectionTextPaint.setShadowLayer(5f, 1f, 1f, Color.BLACK);
        countdownPaint.setColor(Color.WHITE);
        countdownPaint.setTextSize(120f);
        countdownPaint.setTextAlign(Paint.Align.CENTER);
        countdownPaint.setShadowLayer(8f, 2f, 2f, Color.BLACK);
    }

    public void setShowIr(boolean showIr) {
        this.showIr = showIr;
        invalidate();
    }

    public void setCalibrationMode(boolean calibrationMode) {
        this.calibrationMode = calibrationMode;
        invalidate();
    }

    public void setCollecting(boolean collecting) {
        this.isCollecting = collecting;
        if (!collecting) {
            collectionSector = 0;
            collectionCountdownSeconds = 0;
        }
        invalidate();
    }

    public void setCollectionGuide(int sector, int countdownSeconds) {
        this.collectionSector = sector;
        this.collectionCountdownSeconds = countdownSeconds;
        invalidate();
    }

    public void showFace(Rect rgbBox, Rect irBox) {
        this.rgbBox = new Rect(rgbBox);
        this.irBox = new Rect(irBox);
        invalidate();
    }

    public void showResult(ClassificationResult result) {
        this.result = result;
        invalidate();
    }

    public void clearResult() {
        rgbBox = null;
        irBox = null;
        result = null;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (calibrationMode) drawCalibrationGuide(canvas);
        if (isCollecting) drawCollectionGuide(canvas);
        Rect source = showIr ? irBox : rgbBox;
        if (source == null) return;
        Rect box = map(source, 432, 768, getWidth(), getHeight(), true);
        int color;
        if (isCollecting) {
            color = Color.WHITE;
        } else {
            color = result == null ? Color.YELLOW
                    : result.topIndex == 0 ? Color.rgb(0, 230, 118) : Color.rgb(255, 82, 82);
        }
        boxPaint.setColor(color);
        canvas.drawRect(box, boxPaint);

        if (isCollecting) return;

        float titleY = Math.max(32f, box.top - 10f);
        if (result == null) {
            canvas.drawText("FACE", box.left, titleY, textPaint);
            return;
        }
        canvas.drawText(String.format(Locale.US, "%s %.1f%%",
                ClassificationResult.LABELS[result.topIndex], result.probabilities[result.topIndex] * 100f), box.left, titleY, textPaint);

    }

    private void drawCollectionGuide(Canvas canvas) {
        if (collectionSector < 1 || collectionSector > 9) return;
        float cellWidth = getWidth() / 3f;
        float cellHeight = getHeight() / 3f;
        int row = (collectionSector - 1) / 3;
        int column = (collectionSector - 1) % 3;
        float left = column * cellWidth;
        float top = row * cellHeight;
        canvas.drawRect(left, top, left + cellWidth, top + cellHeight, collectionFillPaint);

        for (int i = 1; i < 3; i++) {
            float x = i * cellWidth;
            float y = i * cellHeight;
            canvas.drawLine(x, 0f, x, getHeight(), collectionGridPaint);
            canvas.drawLine(0f, y, getWidth(), y, collectionGridPaint);
        }
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), collectionGridPaint);

        for (int sector = 1; sector <= 9; sector++) {
            int sectorRow = (sector - 1) / 3;
            int sectorColumn = (sector - 1) % 3;
            float cx = sectorColumn * cellWidth + cellWidth / 2f;
            float cy = sectorRow * cellHeight + cellHeight / 2f + 9f;
            canvas.drawText(String.valueOf(sector), cx, cy, collectionTextPaint);
        }

        if (collectionCountdownSeconds > 0) {
            float y = getHeight() / 2f - (countdownPaint.ascent() + countdownPaint.descent()) / 2f;
            canvas.drawText(String.valueOf(collectionCountdownSeconds), getWidth() / 2f, y, countdownPaint);
        }
    }

    private void drawCalibrationGuide(Canvas canvas) {
        float size = getWidth() * 0.5f;
        float left = (getWidth() - size) / 2f;
        float top = getHeight() * 0.3125f;
        canvas.drawRect(left, top, left + size, top + size, guidePaint);
    }

    private static Rect map(Rect source, int imageWidth, int imageHeight, int viewWidth, int viewHeight, boolean mirror) {
        Rect displayed = mirror
                ? new Rect(imageWidth - source.right, source.top, imageWidth - source.left, source.bottom)
                : source;
        float sx = viewWidth / (float) imageWidth;
        float sy = viewHeight / (float) imageHeight;
        return new Rect(Math.round(displayed.left * sx), Math.round(displayed.top * sy),
                Math.round(displayed.right * sx), Math.round(displayed.bottom * sy));
    }
}
