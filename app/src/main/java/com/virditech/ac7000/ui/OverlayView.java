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
    private Rect rgbBox;
    private Rect irBox;
    private ClassificationResult result;
    private boolean showIr;

    public OverlayView(Context context) {
        super(context);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5f);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28f);
        textPaint.setShadowLayer(5f, 1f, 1f, Color.BLACK);
    }

    public void setShowIr(boolean showIr) {
        this.showIr = showIr;
        invalidate();
    }

    public void show(Rect rgbBox, Rect irBox, ClassificationResult result) {
        this.rgbBox = new Rect(rgbBox);
        this.irBox = new Rect(irBox);
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
        Rect source = showIr ? irBox : rgbBox;
        if (source == null || result == null) return;
        Rect box = map(source, 432, 768, getWidth(), getHeight());
        int color = result.topIndex == 0 ? Color.rgb(0, 230, 118) : Color.rgb(255, 82, 82);
        boxPaint.setColor(color);
        canvas.drawRect(box, boxPaint);
        float titleY = Math.max(32f, box.top - 10f);
        canvas.drawText(String.format(Locale.US, "%s %.1f%%",
                ClassificationResult.LABELS[result.topIndex], result.probabilities[result.topIndex] * 100f), box.left, titleY, textPaint);
        float y = box.bottom + 34f;
        for (int i = 0; i < ClassificationResult.LABELS.length; i++) {
            canvas.drawText(String.format(Locale.US, "%s %.1f%%", ClassificationResult.LABELS[i], result.probabilities[i] * 100f),
                    box.left, y, textPaint);
            y += 30f;
        }
    }

    private static Rect map(Rect source, int imageWidth, int imageHeight, int viewWidth, int viewHeight) {
        Rect mirrored = new Rect(imageWidth - source.right, source.top, imageWidth - source.left, source.bottom);
        float sx = viewWidth / (float) imageWidth;
        float sy = viewHeight / (float) imageHeight;
        return new Rect(Math.round(mirrored.left * sx), Math.round(mirrored.top * sy),
                Math.round(mirrored.right * sx), Math.round(mirrored.bottom * sy));
    }
}
