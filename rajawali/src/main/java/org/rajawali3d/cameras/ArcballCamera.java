package org.rajawali3d.cameras;

import android.app.Activity;
import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.rajawali3d.Object3D;
import org.rajawali3d.math.MathUtil;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector2;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.util.RajLog;

/**
 * https://braintrekking.wordpress.com/2012/08/21/tutorial-of-arcball-without-quaternions/
 * @author dennis.ippel
 */
public class ArcballCamera extends Camera {
    private Context mContext;
    private ScaleGestureDetector mScaleDetector;
    private View.OnTouchListener mGestureListener;
    private GestureDetector mDetector;
    private View mView;
    private float mScaleFactor;
    private boolean mIsRotating = false;
    private Vector3 mPrevVec = new Vector3();
    private Vector3 mCurrVec = new Vector3();
    private Vector2 mPrevCoord = new Vector2();
    private Vector2 mCurrCoord = new Vector2();
    private Quaternion mStartOrientation = new Quaternion();
    private Quaternion mCurrentOrientation = new Quaternion();
    private Object3D mObj;
    private double mStartFOV;

    public ArcballCamera(Context context, View view) {
        super();
        mContext = context;
        mView = view;
        mLookAtEnabled = true;
        mObj = new Object3D();
        mObj.setPosition(0, 0, 1);
        mStartFOV = mFieldOfView;
        addListeners();
    }

    @Override
    public void setProjectionMatrix(int width, int height) {
        super.setProjectionMatrix(width, height);
    }

    private void mapToSphere(final float x, final float y, Vector3 out)
    {
        float lengthSquared = x * x + y * y;
        if (lengthSquared > 1)
        {
            out.setAll(x, y, 0);
            out.normalize();
        }
        else
        {
            out.setAll(x, y, Math.sqrt(1 - lengthSquared));
        }
    }

    private void mapToScreen(final float x, final float y, Vector2 out)
    {
        out.setX((2 * x - mLastWidth) / mLastWidth);
        out.setY(-(2 * y - mLastHeight) / mLastHeight);
    }

    private void startRotation(final float x, final float y)
    {
        mapToScreen(x, y, mPrevCoord);

        mCurrCoord.setAll(mPrevCoord.getX(), mPrevCoord.getY());

        mIsRotating = true;
    }

    private void updateRotation(final float x, final float y)
    {
        mapToScreen(x, y, mCurrCoord);

        applyRotation();
    }

    private void endRotation()
    {
        Quaternion q = new Quaternion(mStartOrientation);
        q.multiply(mCurrentOrientation);
        mStartOrientation.setAll(q);
    }

    private void applyRotation()
    {
        if (mIsRotating)
        {
            mapToSphere((float) mPrevCoord.getX(), (float) mPrevCoord.getY(), mPrevVec);
            mapToSphere((float) mCurrCoord.getX(), (float) mCurrCoord.getY(), mCurrVec);

            Vector3 rotationAxis = mPrevVec.clone();
            rotationAxis.cross(mCurrVec);
            rotationAxis.normalize();

            double rotationAngle = Math.acos(Math.min(1, mPrevVec.dot(mCurrVec)));
            mCurrentOrientation.fromAngleAxis(rotationAxis, MathUtil.radiansToDegrees(rotationAngle));
            mCurrentOrientation.normalize();

            Quaternion q = new Quaternion(mStartOrientation);
            q.multiply(mCurrentOrientation);

            mObj.setOrientation(q);
        }
    }

    public Matrix4 getViewMatrix() {
        synchronized (mFrustumLock) {
            Matrix4 m = super.getViewMatrix();
            m.rotate(mObj.getOrientation());
            return m;
        }
    }

    public void setFieldOfView(double fieldOfView) {
        synchronized (mFrustumLock) {
            mStartFOV = fieldOfView;
            super.setFieldOfView(fieldOfView);
        }
    }

    private void addListeners() {
        ((Activity) mContext).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDetector = new GestureDetector(mContext, new GestureListener());
                mScaleDetector = new ScaleGestureDetector(mContext, new ScaleListener());

                mGestureListener = new View.OnTouchListener() {
                    public boolean onTouch(View v, MotionEvent event) {
                        mDetector.onTouchEvent(event);
                        if(event.getAction() == MotionEvent.ACTION_UP) {
                            if(mIsRotating) {
                                endRotation();
                                mIsRotating = false;
                            }
                        }

                        return true;
                    }
                };
                ((View)mView.getParent()).setOnTouchListener(mGestureListener);
            }
        });
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX, float distanceY) {
            if(mIsRotating == false) {
                startRotation(event1.getX(), event1.getY());
                return true;
            }
            mIsRotating = true;
            updateRotation(event2.getX(), event2.getY());
            return true;
        }
    }

    private class ScaleListener
            extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mScaleFactor *= detector.getScaleFactor();
            mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 2.0f));
            mFieldOfView = mStartFOV * mScaleFactor;
            return true;
        }
    }
}
