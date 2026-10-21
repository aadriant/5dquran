package com.dquran.app5d;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class WritingCanvasView extends View {

    private static class Stroke {
        Path path;
        int color;
        float width;

        Stroke(Path path, int color, float width) {
            this.path = path;
            this.color = color;
            this.width = width;
        }
    }

    private final List<Stroke> strokes = new ArrayList<>();
    private Path currentPath;
    private Paint paint;
    private int currentColor = 0xFFF0C060; // Gold
    private float currentStrokeWidth = 10f;
    private float lastX, lastY;

    public WritingCanvasView(Context context) {
        super(context);
        init();
    }

    public WritingCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WritingCanvasView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setStrokeColor(int color) {
        this.currentColor = color;
    }

    public void setStrokeWidth(float width) {
        this.currentStrokeWidth = width;
    }

    public void undo() {
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            invalidate();
        }
    }

    public void clear() {
        strokes.clear();
        currentPath = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw existing strokes
        for (Stroke stroke : strokes) {
            paint.setColor(stroke.color);
            paint.setStrokeWidth(stroke.width);
            canvas.drawPath(stroke.path, paint);
        }

        // Draw current stroke
        if (currentPath != null) {
            paint.setColor(currentColor);
            paint.setStrokeWidth(currentStrokeWidth);
            canvas.drawPath(currentPath, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                currentPath = new Path();
                currentPath.moveTo(x, y);
                lastX = x;
                lastY = y;
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (currentPath != null) {
                    float dx = Math.abs(x - lastX);
                    float dy = Math.abs(y - lastY);
                    if (dx >= 4 || dy >= 4) {
                        currentPath.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2);
                        lastX = x;
                        lastY = y;
                        invalidate();
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (currentPath != null) {
                    currentPath.lineTo(lastX, lastY);
                    strokes.add(new Stroke(currentPath, currentColor, currentStrokeWidth));
                    currentPath = null;
                    invalidate();
                }
                return true;
        }

        return super.onTouchEvent(event);
    }

    public Bitmap getBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        draw(canvas);
        return bitmap;
    }
}
