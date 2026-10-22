package com.dquran.app5d;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AyahNumberSpan extends ReplacementSpan {

    private final int ayahNumber;
    private final int circleColor;
    private final int textColor;
    private final int extraPadding;

    public AyahNumberSpan(int ayahNumber, int circleColor, int textColor) {
        this.ayahNumber = ayahNumber;
        this.circleColor = circleColor;
        this.textColor = textColor;
        this.extraPadding = 16; // pixels padding around the circle
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
        float size = paint.getTextSize() * 0.90f;
        return (int) (size + extraPadding * 2);
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        float textSize = paint.getTextSize();
        float radius = textSize * 0.44f;
        float totalWidth = radius * 2 + extraPadding * 2;
        float cx = x + totalWidth / 2f;
        float cy = top + (bottom - top) / 2f;

        // Save original paint settings
        Paint.Style origStyle = paint.getStyle();
        int origColor = paint.getColor();
        float origStrokeWidth = paint.getStrokeWidth();
        float origTextSize = paint.getTextSize();
        Paint.Align origAlign = paint.getTextAlign();

        // 1. Draw outer circle
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(circleColor);
        paint.setStrokeWidth(Math.max(2.5f, textSize * 0.055f));
        canvas.drawCircle(cx, cy, radius, paint);

        // 2. Draw subtle inner decorative circle
        paint.setStrokeWidth(Math.max(1f, textSize * 0.02f));
        paint.setColor((circleColor & 0x00FFFFFF) | 0x66000000);
        canvas.drawCircle(cx, cy, radius * 0.85f, paint);

        // 3. Draw ayah number inside circle
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(textColor);
        paint.setTextAlign(Paint.Align.CENTER);
        // Size text to fit inside circle comfortably
        float numTextSize = (ayahNumber >= 100) ? (textSize * 0.40f) : ((ayahNumber >= 10) ? (textSize * 0.48f) : (textSize * 0.54f));
        paint.setTextSize(numTextSize);

        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(String.valueOf(ayahNumber), cx, textY, paint);

        // Restore original paint settings
        paint.setStyle(origStyle);
        paint.setColor(origColor);
        paint.setStrokeWidth(origStrokeWidth);
        paint.setTextSize(origTextSize);
        paint.setTextAlign(origAlign);
    }
}
