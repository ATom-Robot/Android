package xyz.atombot.Youmen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by ava on 2017/10/26.
 */
public class YoumengView extends View {
    private static final String TAG = "QuadrotorView";
    private OnYoumengChangeListener listener;

    // private final int holo_blue_dark = 0xff0099cc;
    private final int buttonGray = 0xFFFF0033;
    private final int While = 0xFFFFFFFF;
    private final int black = 0xFF000000;
    private final int SlowHui = 0xFFFF9900;
    private int buttonColor = buttonGray;

    private float x, y; // These are in the intern coordinates
    private double lastX, lastY; // These are in the external coordinates
    private float buttonRadius;
    private float joystickRadius = 0;
    private float centerX;
    private float centerY;
    private Paint p = new Paint();

    public YoumengView(Context context) {
        super(context);
    }

    public YoumengView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public YoumengView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec); // Make the layout square
        setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (joystickRadius == 0) { // Check if it has been set yet
            joystickRadius = (float) (getWidth() / 2.5);
            buttonRadius = joystickRadius / 4;
            centerX = (float) getWidth() / 2;
            centerY = (float) getHeight() / 2;
            x = centerX;
            y = centerY;
        }

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(10);
        p.setColor(black);
        canvas.drawCircle(centerX, centerY, joystickRadius, p);

        p.setColor(SlowHui);
        p.setStrokeWidth(15);
        canvas.drawCircle(centerX, centerY, (float) (joystickRadius / 1.08), p);

        p.setColor(While);
        p.setStrokeWidth(15);
        canvas.drawCircle(centerX, centerY, (float) (joystickRadius / 1.2), p);

        p.setColor(While);
        p.setStrokeWidth(35);
        canvas.drawCircle(centerX, centerY, (float) (joystickRadius / 1.3), p);

        p.setColor(buttonColor);
        p.setStyle(Paint.Style.FILL);
        p.setColor(buttonColor);
        p.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, y, (float) (buttonRadius / 1.5), p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        x = event.getX();
        y = event.getY();
        //Log.d(TAG, "onTouchEvent : getWidth"+getWidth()+"          getHeight:"+getHeight());
        //Log.d(TAG, "onTouchEvent x: " + x + "      y:" + y);
        float abs = (float) Math.sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY));
        if (abs > joystickRadius) {
            x = ((x - centerX) * joystickRadius / abs + centerX);
            y = ((y - centerY) * joystickRadius / abs + centerY);
        }

        if (lastX == 0 && lastY == 0 && (getXValue() > 0.50 || getXValue() < -0.50 || getYValue() > 0.50 || getYValue() < -0.50)) {
            x = centerX;
            y = centerY;
            return true;
        }
        lastX = getXValue();
        lastY = getYValue();

        invalidate();

        if (listener != null) {
            int actionMask = event.getActionMasked();
            if (actionMask == MotionEvent.ACTION_DOWN) {
                buttonColor = SlowHui;
                listener.setOnTouchListener(getXValue(), getYValue(), true);
                return true;
            } else if (actionMask == MotionEvent.ACTION_MOVE) {
                buttonColor = SlowHui;
                listener.setOnMovedListener(getXValue(), getYValue(), true);
                return true;
            }
            //归中
            else if (actionMask == MotionEvent.ACTION_UP || actionMask == MotionEvent.ACTION_CANCEL) {
                buttonColor = buttonGray;
                x = centerX;
                y = centerY;
                lastX = 0;
                lastY = 0;
                listener.setOnReleaseListener(0, 0, true);
                return true;
            }
        }
        return false;
    }

    public double getXValue() {
        return (x - centerX) / joystickRadius; // X-axis is positive at the right side
    }

    public double getYValue() {
        return -((y - centerY) / joystickRadius); // Y-axis should be positive upwards
    }

    public void setOnJoystickChangeListener(OnYoumengChangeListener listener) {
        this.listener = listener;
    }

    public interface OnYoumengChangeListener {
        void setOnTouchListener(double xValue, double yValue, boolean temp);

        void setOnMovedListener(double xValue, double yValue, boolean temp);

        void setOnReleaseListener(double xValue, double yValue, boolean temp);
    }
}
