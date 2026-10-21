package com.dquran.app5d;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * FlowLayout that wraps children onto multiple lines.
 * Fully supports RTL (Right-to-Left) reading direction for Arabic/Quran word-by-word.
 */
public class FlowLayout extends ViewGroup {

    private final List<List<View>> allLines = new ArrayList<>();
    private final List<Integer> lineHeights = new ArrayList<>();
    private final List<Integer> lineWidths = new ArrayList<>();

    public FlowLayout(Context context) {
        super(context);
    }

    public FlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FlowLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        allLines.clear();
        lineHeights.clear();
        lineWidths.clear();

        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        int maxWidth = widthSize - getPaddingLeft() - getPaddingRight();

        int currentLineWidth = 0;
        int currentLineHeight = 0;
        List<View> currentLineViews = new ArrayList<>();

        int childCount = getChildCount();

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;

            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);

            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int childWidth = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            int childHeight = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;

            if (currentLineWidth + childWidth > maxWidth && !currentLineViews.isEmpty()) {
                allLines.add(currentLineViews);
                lineHeights.add(currentLineHeight);
                lineWidths.add(currentLineWidth);

                currentLineViews = new ArrayList<>();
                currentLineWidth = 0;
                currentLineHeight = 0;
            }

            currentLineViews.add(child);
            currentLineWidth += childWidth;
            currentLineHeight = Math.max(currentLineHeight, childHeight);
        }

        if (!currentLineViews.isEmpty()) {
            allLines.add(currentLineViews);
            lineHeights.add(currentLineHeight);
            lineWidths.add(currentLineWidth);
        }

        int totalHeight = getPaddingTop() + getPaddingBottom();
        for (int h : lineHeights) {
            totalHeight += h;
        }

        int maxRecordedLineWidth = 0;
        for (int w : lineWidths) {
            maxRecordedLineWidth = Math.max(maxRecordedLineWidth, w);
        }
        int totalWidth = maxRecordedLineWidth + getPaddingLeft() + getPaddingRight();

        setMeasuredDimension(
                widthMode == MeasureSpec.EXACTLY ? widthSize : Math.min(totalWidth, widthSize),
                heightMode == MeasureSpec.EXACTLY ? heightSize : totalHeight
        );
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int parentWidth = getMeasuredWidth();
        int top = getPaddingTop();

        for (int lineIndex = 0; lineIndex < allLines.size(); lineIndex++) {
            List<View> lineViews = allLines.get(lineIndex);
            int lineHeight = lineHeights.get(lineIndex);

            if (isRtl) {
                // In RTL, start placing from the right edge
                int right = parentWidth - getPaddingRight();
                for (View child : lineViews) {
                    if (child.getVisibility() == View.GONE) continue;
                    MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                    int cl = right - lp.rightMargin - child.getMeasuredWidth();
                    int ct = top + lp.topMargin;
                    int cr = right - lp.rightMargin;
                    int cb = ct + child.getMeasuredHeight();

                    child.layout(cl, ct, cr, cb);
                    right -= (child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
                }
            } else {
                // In LTR, start placing from the left edge
                int left = getPaddingLeft();
                for (View child : lineViews) {
                    if (child.getVisibility() == View.GONE) continue;
                    MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                    int cl = left + lp.leftMargin;
                    int ct = top + lp.topMargin;
                    int cr = cl + child.getMeasuredWidth();
                    int cb = ct + child.getMeasuredHeight();

                    child.layout(cl, ct, cr, cb);
                    left += (child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
                }
            }
            top += lineHeight;
        }
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return new MarginLayoutParams(p);
    }
}
