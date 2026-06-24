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
    private Rect rgbBox;
    private Rect irBox;
    private ClassificationResult result;
    private boolean showIr;
    private boolean calibrationMode;

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
    }

    public void setShowIr(boolean showIr) {
        this.showIr = showIr;
        invalidate();
    }

    public void setCalibrationMode(boolean calibrationMode) {
        this.calibrationMode = calibrationMode;
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
        Rect source = showIr ? irBox : rgbBox;
        if (source == null) return;
        Rect box = map(source, 432, 768, getWidth(), getHeight(), true);
        int color = result == null ? Color.YELLOW
                : result.topIndex == 0 ? Color.rgb(0, 230, 118) : Color.rgb(255, 82, 82);
        boxPaint.setColor(color);
        canvas.drawRect(box, boxPaint);
        float titleY = Math.max(32f, box.top - 10f);
        if (result == null) {
            canvas.drawText("FACE", box.left, titleY, textPaint);
            return;
        }
        canvas.drawText(String.format(Locale.US, "%s %.1f%%",
                ClassificationResult.LABELS[result.topIndex], result.probabilities[result.topIndex] * 100f), box.left, titleY, textPaint);

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
