/*
 * TouchImageView.java
 * Original work By: Michael Ortiz (https://github.com/MikeOrtiz/TouchImageView)
 * Refactored & Expanded by: Mahram Z. Foadi
 * Updated By: Patrick Lackemacher
 * Updated By: Babay88
 * Updated By: @ipsilondev
 * -------------------
 * Extends Android ImageView to include pinch zooming, panning, fling and double tap zoom.
 */

package ca.mahram.android;

import static ca.mahram.android.TouchImageView.State.ANIMATE_ZOOM;
import static ca.mahram.android.TouchImageView.State.DRAG;
import static ca.mahram.android.TouchImageView.State.FLING;
import static ca.mahram.android.TouchImageView.State.NONE;
import static ca.mahram.android.TouchImageView.State.ZOOM;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.Scroller;

public class TouchImageView extends ImageView {

    private static final String LOGTAG = "TouchImageView";

    //
    // SuperMin and SuperMax multipliers. Determine how much the image can be
    // zoomed below or above the zoom boundaries, before animating back to the
    // min/max zoom boundary.
    //
    private final float minScaleBounceBackMultiplier;
    private final float maxScaleBounceBackMultiplier;

    //
    // Scale of image ranges from minScale to maxScale, where minScale == 1
    // when the image is stretched to fit view.
    //
    private float normalizedScale;
    
    //
    // Matrix applied to image. MSCALE_X and MSCALE_Y should always be equal.
    // MTRANS_X and MTRANS_Y are the other values used. prevMatrix is the matrix
    // saved prior to the screen rotating.
    //
	private Matrix matrix;
    private Matrix prevMatrix;

    public static enum State { NONE, DRAG, ZOOM, FLING, ANIMATE_ZOOM };
    private State state;

    private float minScale;
    private float maxScale;
    private float lowerBounceBackScale;
    private float upperBounceBackScale;
    private float[] matrixValues;

    private Context context;
    private Fling fling;

    //
    // Size of view and previous view size (ie before rotation)
    //
    private int viewWidth, viewHeight, prevViewWidth, prevViewHeight;
    
    //
    // Size of image when it is stretched to fit view. Before and After rotation.
    //
    private float matchViewWidth, matchViewHeight, prevMatchViewWidth, prevMatchViewHeight;
    
    //
    // After setting image, a value of true means the new image should maintain
    // the zoom of the previous image. False means it should be resized within the view.
    //
    private boolean maintainZoomAfterSetImage;
    
    //
    // True when maintainZoomAfterSetImage has been set to true and setImage has been called.
    //
    private boolean setImageCalledRecenterImage;
    
    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGestureDetector;

    public TouchImageView(final Context context) {
        super(context);
        minScaleBounceBackMultiplier = DEFAULT_MINIMUM_BOUNCEBACK_MULTIPLIER;
        maxScaleBounceBackMultiplier = DEFAULT_MAXIMUM_BOUNCEBACK_MULTIPLIER;
        minScale = DEFAULT_MIN_SCALE;
        maxScale = DEFAULT_MAX_SCALE;
        mScaleDetector = new ScaleGestureDetector (context, new ScaleListener ());
        sharedConstructing(context);
    }

    public TouchImageView(final Context context, final AttributeSet attrs) {
        this (context, attrs, 0);
    }
    
    public TouchImageView(final Context context, final AttributeSet attrs, final int defStyle) {
    	super(context, attrs, defStyle);

        TypedArray ta = context.obtainStyledAttributes (attrs, R.styleable.TouchImageView);

        final boolean allowScale = true;
//        final boolean allowRotate;

        try {
            minScale = ta.getFloat (R.styleable.TouchImageView_minScale, DEFAULT_MIN_SCALE);
            maxScale = ta.getFloat (R.styleable.TouchImageView_maxScale, DEFAULT_MAX_SCALE);

            minScaleBounceBackMultiplier = ta.getFloat(R.styleable.TouchImageView_minScaleBounceBackMultiplier, DEFAULT_MINIMUM_BOUNCEBACK_MULTIPLIER);
            maxScaleBounceBackMultiplier = ta.getFloat(R.styleable.TouchImageView_maxScaleBounceBackMultiplier, DEFAULT_MAXIMUM_BOUNCEBACK_MULTIPLIER);

//            allowDoubleTap = ta.getBoolean (R.styleable.TouchImageView_allowDoubleTap, true);
//            allowFling = ta.getBoolean (R.styleable.TouchImageView_allowFling, true);
//            allowScale = ta.getBoolean (R.styleable.TouchImageView_allowScale, true);
//            allowRotate = ta.getBoolean (R.styleable.TouchImageView_allowRotate, true);
        } finally {
            ta.recycle ();
        }

        mScaleDetector = allowScale
                ? new ScaleGestureDetector (context, new ScaleListener ())
                : null;


        sharedConstructing(context);
    }

    private void sharedConstructing(Context context) {
        super.setClickable(true);
        this.context = context;
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        mGestureDetector = new GestureDetector(context, new GestureListener());
        matrix = new Matrix();
        prevMatrix = new Matrix();
        matrixValues = new float[9];
        normalizedScale = 1;
        minScale = 1;
        maxScale = 3;
        lowerBounceBackScale = minScaleBounceBackMultiplier * minScale;
        upperBounceBackScale = maxScaleBounceBackMultiplier * maxScale;
        maintainZoomAfterSetImage = true;
        setImageMatrix(matrix);
        setScaleType(ScaleType.MATRIX);
        setState(NONE);
        setOnTouchListener(new TouchImageViewListener());
    }
    
    @Override
    public void setImageResource(int resId) {
    	super.setImageResource(resId);
    	setImageCalled();
    	savePreviousImageValues();
    	fitImageToView();
    }
    
    @Override
    public void setImageBitmap(Bitmap bm) {
    	super.setImageBitmap(bm);
    	setImageCalled();
    	savePreviousImageValues();
    	fitImageToView();
    }
    
    @Override
    public void setImageDrawable(Drawable drawable) {
    	super.setImageDrawable(drawable);
    	setImageCalled();
    	savePreviousImageValues();
    	fitImageToView();
    }
    
    @Override
    public void setImageURI(Uri uri) {
    	super.setImageURI(uri);
    	setImageCalled();
    	savePreviousImageValues();
    	fitImageToView();
    }
    
    private void setImageCalled() {
    	if (!maintainZoomAfterSetImage) {
    		setImageCalledRecenterImage = true;
    	}
    }
    
    /**
     * Save the current matrix and view dimensions
     * in the prevMatrix and prevView variables.
     */
    private void savePreviousImageValues() {
    	if (matrix != null) {
	    	matrix.getValues(matrixValues);
	    	prevMatrix.setValues(matrixValues);
	    	prevMatchViewHeight = matchViewHeight;
	        prevMatchViewWidth = matchViewWidth;
	        prevViewHeight = viewHeight;
	        prevViewWidth = viewWidth;
    	}
    }
    
    @Override
    public Parcelable onSaveInstanceState() {
      Bundle bundle = new Bundle();
      bundle.putParcelable(KEY_INSTANCE_STATE, super.onSaveInstanceState());
      bundle.putFloat(KEY_NORMALIZED_SCALE, normalizedScale);
      bundle.putFloat(KEY_MATCH_VIEW_HEIGHT, matchViewHeight);
      bundle.putFloat(KEY_MATCH_VIEW_WIDTH, matchViewWidth);
      bundle.putInt(KEY_VIEW_WIDTH, viewWidth);
      bundle.putInt(KEY_VIEW_HEIGHT, viewHeight);
      matrix.getValues(matrixValues);
      bundle.putFloatArray(KEY_MATRIX_VALUES, matrixValues);
      return bundle;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
      	if (state instanceof Bundle) {
	        Bundle bundle = (Bundle) state;
	        normalizedScale = bundle.getFloat(KEY_NORMALIZED_SCALE);
	        matrixValues = bundle.getFloatArray(KEY_MATRIX_VALUES);
	        prevMatrix.setValues(matrixValues);
	        prevMatchViewHeight = bundle.getFloat(KEY_MATCH_VIEW_HEIGHT);
	        prevMatchViewWidth = bundle.getFloat(KEY_MATCH_VIEW_WIDTH);
	        prevViewHeight = bundle.getInt(KEY_VIEW_HEIGHT);
	        prevViewWidth = bundle.getInt(KEY_VIEW_WIDTH);
	        super.onRestoreInstanceState(bundle.getParcelable(KEY_INSTANCE_STATE));
	        return;
      	}

      	super.onRestoreInstanceState(state);
    }
    
    /**
     * Get the max zoom multiplier.
     * @return max zoom multiplier.
     */
    public float getMaxZoom() {
    	return maxScale;
    }

    /**
     * Set the max zoom multiplier. Default value: 3.
     * @param max max zoom multiplier.
     */
    public void setMaxZoom(float max) {
        maxScale = max;
        upperBounceBackScale = maxScaleBounceBackMultiplier * maxScale;
    }
    
    /**
     * Get the min zoom multiplier.
     * @return min zoom multiplier.
     */
    public float getMinZoom() {
    	return minScale;
    }
    
    /**
     * After setting image, a value of true means the new image should maintain
     * the zoom of the previous image. False means the image should be resized within
     * the view. Defaults value is true.
     * @param maintainZoom
     */
    public void maintainZoomAfterSetImage(boolean maintainZoom) {
    	maintainZoomAfterSetImage = maintainZoom;
    }
    
    /**
     * Get the current zoom. This is the zoom relative to the initial
     * scale, not the original resource.
     * @return current zoom multiplier.
     */
    public float getCurrentZoom() {
    	return normalizedScale;
    }
    
    /**
     * Set the min zoom multiplier. Default value: 1.
     * @param min min zoom multiplier.
     */
    public void setMinZoom(float min) {
    	minScale = min;
    	lowerBounceBackScale = minScaleBounceBackMultiplier * minScale;
    }
    
    /**
     * For a given point on the view (ie, a touch event), returns the
     * point relative to the original drawable's coordinate system.
     * @param x
     * @param y
     * @return PointF relative to original drawable's coordinate system.
     */
    public PointF getDrawablePointFromTouchPoint(float x, float y) {
    	return transformCoordTouchToBitmap(x, y, true);
    }
    
    /**
     * For a given point on the view (ie, a touch event), returns the
     * point relative to the original drawable's coordinate system.
     * @param p
     * @return PointF relative to original drawable's coordinate system.
     */
    public PointF getDrawablePointFromTouchPoint(PointF p) {
    	return transformCoordTouchToBitmap(p.x, p.y, true);
    }
    
    /**
     * Performs boundary checking and fixes the image matrix if it 
     * is out of bounds.
     */
    private void fixTrans() {
        matrix.getValues(matrixValues);
        float transX = matrixValues[Matrix.MTRANS_X];
        float transY = matrixValues[Matrix.MTRANS_Y];
        
        float fixTransX = getFixTrans(transX, viewWidth, getImageWidth());
        float fixTransY = getFixTrans(transY, viewHeight, getImageHeight());
        
        if (fixTransX != 0 || fixTransY != 0) {
            matrix.postTranslate(fixTransX, fixTransY);
        }
    }
    
    /**
     * When transitioning from zooming from focus to zoom from center (or vice versa)
     * the image can become unaligned within the view. This is apparent when zooming
     * quickly. When the content size is less than the view size, the content will often
     * be centered incorrectly within the view. fixScaleTrans first calls fixTrans() and 
     * then makes sure the image is centered correctly within the view.
     */
    private void fixScaleTrans() {
    	fixTrans();
    	matrix.getValues(matrixValues);
    	if (getImageWidth() < viewWidth) {
    		matrixValues[Matrix.MTRANS_X] = (viewWidth - getImageWidth()) / 2;
    	}
    	
    	if (getImageHeight() < viewHeight) {
    		matrixValues[Matrix.MTRANS_Y] = (viewHeight - getImageHeight()) / 2;
    	}
    	matrix.setValues(matrixValues);
    }

    private float getFixTrans(float trans, float viewSize, float contentSize) {
        float minTrans, maxTrans;

        if (contentSize <= viewSize) {
            minTrans = 0;
            maxTrans = viewSize - contentSize;
            
        } else {
            minTrans = viewSize - contentSize;
            maxTrans = 0;
        }

        if (trans < minTrans)
            return -trans + minTrans;
        if (trans > maxTrans)
            return -trans + maxTrans;
        return 0;
    }
    
    private float getFixDragTrans(float delta, float viewSize, float contentSize) {
        if (contentSize <= viewSize) {
            return 0;
        }
        return delta;
    }
    
    private float getImageWidth() {
    	return matchViewWidth * normalizedScale;
    }
    
    private float getImageHeight() {
    	return matchViewHeight * normalizedScale;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Drawable drawable = getDrawable();
        if (drawable == null || drawable.getIntrinsicWidth() == 0 || drawable.getIntrinsicHeight() == 0) {
        	setMeasuredDimension(0, 0);
        	return;
        }
        
        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        viewWidth = setViewSize(widthMode, widthSize, drawableWidth);
        viewHeight = setViewSize(heightMode, heightSize, drawableHeight);
        
        //
        // Set view dimensions
        //
        setMeasuredDimension(viewWidth, viewHeight);
        
        //
        // Fit content within view
        //
        fitImageToView();
    }
    
    /**
     * If the normalizedScale is equal to 1, then the image is made to fit the screen. Otherwise,
     * it is made to fit the screen according to the dimensions of the previous image matrix. This
     * allows the image to maintain its zoom after rotation.
     */
    private void fitImageToView() {
    	Drawable drawable = getDrawable();
        if (drawable == null || drawable.getIntrinsicWidth() == 0 || drawable.getIntrinsicHeight() == 0) {
        	return;
        }
        if (matrix == null || prevMatrix == null) {
        	return;
        }
        
        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();
    	
    	//
    	// Scale image for view
    	//
        float scaleX = (float) viewWidth / drawableWidth;
        float scaleY = (float) viewHeight / drawableHeight;
        float scale = Math.min(scaleX, scaleY);

        //
        // Center the image
        //
        float redundantYSpace = viewHeight - (scale * drawableHeight);
        float redundantXSpace = viewWidth - (scale * drawableWidth);
        matchViewWidth = viewWidth - redundantXSpace;
        matchViewHeight = viewHeight - redundantYSpace;
        if (normalizedScale == 1 || setImageCalledRecenterImage) {
        	//
        	// Stretch and center image to fit view
        	//
        	matrix.setScale(scale, scale);
        	matrix.postTranslate(redundantXSpace / 2, redundantYSpace / 2);
        	normalizedScale = 1;
        	setImageCalledRecenterImage = false;
        	
        } else {
        	prevMatrix.getValues(matrixValues);
        	
        	//
        	// Rescale Matrix after rotation
        	//
        	matrixValues[Matrix.MSCALE_X] = matchViewWidth / drawableWidth * normalizedScale;
        	matrixValues[Matrix.MSCALE_Y] = matchViewHeight / drawableHeight * normalizedScale;
        	
        	//
        	// TransX and TransY from previous matrix
        	//
            float transX = matrixValues[Matrix.MTRANS_X];
            float transY = matrixValues[Matrix.MTRANS_Y];
            
            //
            // Width
            //
            float prevActualWidth = prevMatchViewWidth * normalizedScale;
            float actualWidth = getImageWidth();
            translateMatrixAfterRotate(Matrix.MTRANS_X, transX, prevActualWidth, actualWidth, prevViewWidth, viewWidth, drawableWidth);
            
            //
            // Height
            //
            float prevActualHeight = prevMatchViewHeight * normalizedScale;
            float actualHeight = getImageHeight();
            translateMatrixAfterRotate(Matrix.MTRANS_Y, transY, prevActualHeight, actualHeight, prevViewHeight, viewHeight, drawableHeight);
            
            //
            // Set the matrix to the adjusted scale and translate values.
            //
            matrix.setValues(matrixValues);
        }
        setImageMatrix(matrix);
    }
    
    /**
     * Set view dimensions based on layout params
     * 
     * @param mode 
     * @param size
     * @param drawableWidth
     * @return
     */
    private int setViewSize(int mode, int size, int drawableWidth) {
    	int viewSize;
    	switch (mode) {
		case MeasureSpec.EXACTLY:
			viewSize = size;
			break;
			
		case MeasureSpec.AT_MOST:
			viewSize = Math.min(drawableWidth, size);
			break;
			
		case MeasureSpec.UNSPECIFIED:
			viewSize = drawableWidth;
			break;
			
		default:
			viewSize = size;
		 	break;
		}
    	return viewSize;
    }
    
    /**
     * After rotating, the matrix needs to be translated. This function finds the area of image 
     * which was previously centered and adjusts translations so that is again the center, post-rotation.
     * 
     * @param axis Matrix.MTRANS_X or Matrix.MTRANS_Y
     * @param trans the value of trans in that axis before the rotation
     * @param prevImageSize the width/height of the image before the rotation
     * @param imageSize width/height of the image after rotation
     * @param prevViewSize width/height of view before rotation
     * @param viewSize width/height of view after rotation
     * @param drawableSize width/height of drawable
     */
    private void translateMatrixAfterRotate(int axis, float trans, float prevImageSize, float imageSize, int prevViewSize, int viewSize, int drawableSize) {
    	if (imageSize < viewSize) {
        	//
        	// The width/height of image is less than the view's width/height. Center it.
        	//
        	matrixValues[axis] = (viewSize - (drawableSize * matrixValues[Matrix.MSCALE_X])) * 0.5f;
        	
        } else if (trans > 0) {
        	//
        	// The image is larger than the view, but was not before rotation. Center it.
        	//
        	matrixValues[axis] = -((imageSize - viewSize) * 0.5f);
        	
        } else {
        	//
        	// Find the area of the image which was previously centered in the view. Determine its distance
        	// from the left/top side of the view as a fraction of the entire image's width/height. Use that percentage
        	// to calculate the trans in the new view width/height.
        	//
        	float percentage = (Math.abs(trans) + (0.5f * prevViewSize)) / prevImageSize;
        	matrixValues[axis] = -((percentage * imageSize) - (viewSize * 0.5f));
        }
    }
    
    private void setState(State state) {
    	this.state = state;
    }
    
    /**
     * Gesture Listener detects a single click or long click and passes that on
     * to the view's listener.
     * @author Ortiz
     *
     */
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
    	
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e)
        {
        	return performClick();
        }
        
        @Override
        public void onLongPress(MotionEvent e)
        {
        	performLongClick();
        }
        
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
        {
        	if (fling != null) {
        		//
        		// If a previous fling is still active, it should be cancelled so that two flings
        		// are not run simultaenously.
        		//
        		fling.cancelFling();
        	}
        	fling = new Fling((int) velocityX, (int) velocityY);
        	compatPostOnAnimation(fling);
        	return super.onFling(e1, e2, velocityX, velocityY);
        }
        
        @Override
        public boolean onDoubleTap(MotionEvent e) {
        	boolean consumed = false;
        	if (state == NONE) {
	        	float targetZoom = (normalizedScale == minScale) ? maxScale : minScale;
	        	DoubleTapZoom doubleTap = new DoubleTapZoom(targetZoom, e.getX(), e.getY(), false);
	        	compatPostOnAnimation(doubleTap);
	        	consumed = true;
        	}
        	return consumed;
        }
    }
    
    /**
     * Responsible for all touch events. Handles the heavy lifting of drag and also sends
     * touch events to Scale Detector and Gesture Detector.
     * @author Ortiz
     *
     */
    private class TouchImageViewListener implements OnTouchListener {
    	
    	//
        // Remember last point position for dragging
        //
        private PointF last = new PointF();
    	
    	@Override
        public boolean onTouch(View v, MotionEvent event) {
            mScaleDetector.onTouchEvent(event);
            mGestureDetector.onTouchEvent(event);
            PointF curr = new PointF(event.getX(), event.getY());
            
            if (state == NONE || state == DRAG || state == FLING) {
	            switch (event.getAction()) {
	                case MotionEvent.ACTION_DOWN:
	                	last.set(curr);
	                    if (fling != null)
	                    	fling.cancelFling();
	                    setState(DRAG);
	                    break;
	                    
	                case MotionEvent.ACTION_MOVE:
	                    if (state == DRAG) {
	                        float deltaX = curr.x - last.x;
	                        float deltaY = curr.y - last.y;
	                        float fixTransX = getFixDragTrans(deltaX, viewWidth, getImageWidth());
	                        float fixTransY = getFixDragTrans(deltaY, viewHeight, getImageHeight());
	                        matrix.postTranslate(fixTransX, fixTransY);
	                        fixTrans();
	                        last.set(curr.x, curr.y);
	                    }
	                    break;
	
	                case MotionEvent.ACTION_UP:
	                case MotionEvent.ACTION_POINTER_UP:
	                    setState(NONE);
	                    break;
	            }
            }
            
            setImageMatrix(matrix);
            //
            // indicate event was handled
            //
            return true;
        }
    }

    /**
     * ScaleListener detects user two finger scaling and scales image.
     * @author Ortiz
     *
     */
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            setState(ZOOM);
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
        	scaleImage(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY(), true);
            return true;
        }
        
        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
        	super.onScaleEnd(detector);
        	setState(NONE);
        	boolean animateToZoomBoundary = false;
        	float targetZoom = normalizedScale;
        	if (normalizedScale > maxScale) {
        		targetZoom = maxScale;
        		animateToZoomBoundary = true;
        		
        	} else if (normalizedScale < minScale) {
        		targetZoom = minScale;
        		animateToZoomBoundary = true;
        	}
        	
        	if (animateToZoomBoundary) {
	        	DoubleTapZoom doubleTap = new DoubleTapZoom(targetZoom, viewWidth / 2, viewHeight / 2, true);
	        	compatPostOnAnimation(doubleTap);
        	}
        }
    }
    
    private void scaleImage(float deltaScale, float focusX, float focusY, boolean stretchImageToSuper) {
    	
    	float lowerScale, upperScale;
    	if (stretchImageToSuper) {
    		lowerScale = lowerBounceBackScale;
    		upperScale = upperBounceBackScale;
    		
    	} else {
    		lowerScale = minScale;
    		upperScale = maxScale;
    	}
    	
    	float origScale = normalizedScale;
        normalizedScale *= deltaScale;
        if (normalizedScale > upperScale) {
            normalizedScale = upperScale;
            deltaScale = upperScale / origScale;
        } else if (normalizedScale < lowerScale) {
            normalizedScale = lowerScale;
            deltaScale = lowerScale / origScale;
        }
        
        matrix.postScale(deltaScale, deltaScale, focusX, focusY);
        fixScaleTrans();
    }
    
    /**
     * DoubleTapZoom calls a series of runnables which apply
     * an animated zoom in/out graphic to the image.
     * @author Ortiz
     *
     */
    private class DoubleTapZoom implements Runnable {
    	
    	private long startTime;
    	private static final float ZOOM_TIME = 500;
    	private float startZoom, targetZoom;
    	private float bitmapX, bitmapY;
    	private boolean stretchImageToSuper;
    	private AccelerateDecelerateInterpolator interpolator = new AccelerateDecelerateInterpolator();
    	private PointF startTouch;
    	private PointF endTouch;

    	DoubleTapZoom(float targetZoom, float focusX, float focusY, boolean stretchImageToSuper) {
    		setState(ANIMATE_ZOOM);
    		startTime = System.currentTimeMillis();
    		this.startZoom = normalizedScale;
    		this.targetZoom = targetZoom;
    		this.stretchImageToSuper = stretchImageToSuper;
    		PointF bitmapPoint = transformCoordTouchToBitmap(focusX, focusY, false);
    		this.bitmapX = bitmapPoint.x;
    		this.bitmapY = bitmapPoint.y;
    		
    		//
    		// Used for translating image during scaling
    		//
    		startTouch = transformCoordBitmapToTouch(bitmapX, bitmapY);
    		endTouch = new PointF(viewWidth / 2, viewHeight / 2);
    	}

		@Override
		public void run() {
			float t = interpolate();
			float deltaScale = calculateDeltaScale(t);
			scaleImage(deltaScale, bitmapX, bitmapY, stretchImageToSuper);
			translateImageToCenterTouchPosition(t);
			fixScaleTrans();
			setImageMatrix(matrix);
			
			if (t < 1f) {
				//
				// We haven't finished zooming
				//
				compatPostOnAnimation(this);
				
			} else {
				//
				// Finished zooming
				//
				setState(NONE);
			}
		}
		
		/**
		 * Interpolate between where the image should start and end in order to translate
		 * the image so that the point that is touched is what ends up centered at the end
		 * of the zoom.
		 * @param t
		 */
		private void translateImageToCenterTouchPosition(float t) {
			float targetX = startTouch.x + t * (endTouch.x - startTouch.x);
			float targetY = startTouch.y + t * (endTouch.y - startTouch.y);
			PointF curr = transformCoordBitmapToTouch(bitmapX, bitmapY);
			matrix.postTranslate(targetX - curr.x, targetY - curr.y);
		}
		
		/**
		 * Use interpolator to get t
		 * @return
		 */
		private float interpolate() {
			long currTime = System.currentTimeMillis();
			float elapsed = (currTime - startTime) / ZOOM_TIME;
			elapsed = Math.min(1f, elapsed);
			return interpolator.getInterpolation(elapsed);
		}
		
		/**
		 * Interpolate the current targeted zoom and get the delta
		 * from the current zoom.
		 * @param t
		 * @return
		 */
		private float calculateDeltaScale(float t) {
			float zoom = startZoom + t * (targetZoom - startZoom);
			return zoom / normalizedScale;
		}
    }
    
    /**
     * This function will transform the coordinates in the touch event to the coordinate 
     * system of the drawable that the imageview contain
     * @param x x-coordinate of touch event
     * @param y y-coordinate of touch event
     * @param clipToBitmap Touch event may occur within view, but outside image content. True, to clip return value
     * 			to the bounds of the bitmap size.
     * @return Coordinates of the point touched, in the coordinate system of the original drawable.
     */
    private PointF transformCoordTouchToBitmap(float x, float y, boolean clipToBitmap) {
         matrix.getValues(matrixValues);
         float origW = getDrawable().getIntrinsicWidth();
         float origH = getDrawable().getIntrinsicHeight();
         float transX = matrixValues[Matrix.MTRANS_X];
         float transY = matrixValues[Matrix.MTRANS_Y];
         float finalX = ((x - transX) * origW) / getImageWidth();
         float finalY = ((y - transY) * origH) / getImageHeight();
         
         if (clipToBitmap) {
        	 finalX = Math.min(Math.max(x, 0), origW);
        	 finalY = Math.min(Math.max(y, 0), origH);
         }
         
         return new PointF(finalX , finalY);
    }
    
    /**
     * Inverse of transformCoordTouchToBitmap. This function will transform the coordinates in the
     * drawable's coordinate system to the view's coordinate system.
     * @param bx x-coordinate in original bitmap coordinate system
     * @param by y-coordinate in original bitmap coordinate system
     * @return Coordinates of the point in the view's coordinate system.
     */
    private PointF transformCoordBitmapToTouch(float bx, float by) {
        matrix.getValues(matrixValues);
        float origW = getDrawable().getIntrinsicWidth();
        float origH = getDrawable().getIntrinsicHeight();
        float px = bx / origW;
        float py = by / origH;
        float finalX = matrixValues[Matrix.MTRANS_X] + getImageWidth() * px;
        float finalY = matrixValues[Matrix.MTRANS_Y] + getImageHeight() * py;
        return new PointF(finalX , finalY);
    }
    
    /**
     * Fling launches sequential runnables which apply
     * the fling graphic to the image. The values for the translation
     * are interpolated by the Scroller.
     * @author Ortiz
     *
     */
    private class Fling implements Runnable {
    	
        Scroller scroller;
    	int currX, currY;
    	
    	Fling(int velocityX, int velocityY) {
    		setState(FLING);
    		scroller = new Scroller(context);
    		matrix.getValues(matrixValues);
    		
    		int startX = (int) matrixValues[Matrix.MTRANS_X];
    		int startY = (int) matrixValues[Matrix.MTRANS_Y];
    		int minX, maxX, minY, maxY;
    		
    		if (getImageWidth() > viewWidth) {
    			minX = viewWidth - (int) getImageWidth();
    			maxX = 0;
    			
    		} else {
    			minX = maxX = startX;
    		}
    		
    		if (getImageHeight() > viewHeight) {
    			minY = viewHeight - (int) getImageHeight();
    			maxY = 0;
    			
    		} else {
    			minY = maxY = startY;
    		}
    		
    		scroller.fling(startX, startY, (int) velocityX, (int) velocityY, minX,
                    maxX, minY, maxY);
    		currX = startX;
    		currY = startY;
    	}
    	
    	public void cancelFling() {
    		if (scroller != null) {
    			setState(NONE);
    			scroller.forceFinished(true);
    		}
    	}
    	
		@Override
		public void run() {
			if (scroller.isFinished()) {
        		scroller = null;
        		return;
        	}
			
			if (scroller.computeScrollOffset()) {
	        	int newX = scroller.getCurrX();
	            int newY = scroller.getCurrY();
	            int transX = newX - currX;
	            int transY = newY - currY;
	            currX = newX;
	            currY = newY;
	            matrix.postTranslate(transX, transY);
	            fixTrans();
	            setImageMatrix(matrix);
	            compatPostOnAnimation(this);
        	}
		}
    }
    
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void compatPostOnAnimation(Runnable runnable) {
    	if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
            postOnAnimation(runnable);
            
        } else {
            postDelayed(runnable, 1000/60);
        }
    }
    
    private void printMatrixInfo() {
    	matrix.getValues(matrixValues);
    	Log.d(LOGTAG, "Scale: " + matrixValues[Matrix.MSCALE_X] + " TransX: " + matrixValues[Matrix.MTRANS_X] + " TransY: " + matrixValues[Matrix.MTRANS_Y]);
    }

    // default minimum scale is 1
    private static final float DEFAULT_MIN_SCALE = 1.0f;
    // default maximum scale is 3
    private static final float DEFAULT_MAX_SCALE = 3.0f;

    // default minimum BounceBackScaleMultiplier
    private static final float DEFAULT_MINIMUM_BOUNCEBACK_MULTIPLIER = .85f;
    private static final float DEFAULT_MAXIMUM_BOUNCEBACK_MULTIPLIER = 1.15f;

    public static final String KEY_INSTANCE_STATE = "TouchImageView_instanceState";
    public static final String KEY_NORMALIZED_SCALE = "TouchImageView_normalizedScale";
    public static final String KEY_MATCH_VIEW_HEIGHT = "TouchImageView_matchViewHeight";
    public static final String KEY_MATCH_VIEW_WIDTH = "TouchImageView_matchViewWidth";
    public static final String KEY_VIEW_WIDTH = "TouchImageView_viewWidth";
    public static final String KEY_VIEW_HEIGHT = "TouchImageView_viewHeight";
    public static final String KEY_MATRIX_VALUES = "TouchImageView_matrix";
}