package ch.laurinmurer.selecator.helper;

import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

public class ZoomInZoomOut implements View.OnTouchListener {
	private static final String TAG = "Touch";

	// These matrices will be used to scale points of the image
	private final Matrix newMatrix = new Matrix();
	private final Matrix matrixBeforeAction = new Matrix();

	private CurrentAction mode = CurrentAction.NONE;

	// these PointF objects are used to record the point(s) the user is touching
	private final PointF firstTouchPoint = new PointF();
	private final PointF middleBetweenInitialTouchPoints = new PointF();
	private float distanceBetweenInitialTouchPoints = 1f;

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		ImageView view = (ImageView) v;
		view.setScaleType(ImageView.ScaleType.MATRIX);

		dumpEvent(event);
		// Handle touch events here...

		switch (event.getAction() & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_DOWN:   // first finger down only
				newMatrix.set(view.getImageMatrix());
				matrixBeforeAction.set(newMatrix);
				firstTouchPoint.set(event.getX(), event.getY());
				Log.d(TAG, "mode=DRAG");
				mode = CurrentAction.DRAG;
				break;

			case MotionEvent.ACTION_POINTER_UP: // second finger lifted
			case MotionEvent.ACTION_UP: // first finger lifted
				if (mode == CurrentAction.DRAG && matrixBeforeAction.equals(newMatrix)) {
					v.performClick();
				}
				mode = CurrentAction.NONE;
				Log.d(TAG, "mode=NONE");
				break;

			case MotionEvent.ACTION_POINTER_DOWN: // first and second finger down
				distanceBetweenInitialTouchPoints = spacing(event);
				Log.d(TAG, "distanceBetweenInitialTouchPoints=" + distanceBetweenInitialTouchPoints);
				if (distanceBetweenInitialTouchPoints > 5f) {
					matrixBeforeAction.set(newMatrix);
					middleBetweenInitialTouchPoints.set(
							(event.getX(0) + event.getX(1)) / 2,
							(event.getY(0) + event.getY(1)) / 2
					);
					mode = CurrentAction.ZOOM;
					Log.d(TAG, "mode=ZOOM");
				}
				break;

			case MotionEvent.ACTION_MOVE:
				if (mode == CurrentAction.DRAG) {
					newMatrix.set(matrixBeforeAction);
					newMatrix.postTranslate(event.getX() - firstTouchPoint.x, event.getY() - firstTouchPoint.y); // create the transformation in the matrix  of points
				} else if (mode == CurrentAction.ZOOM) {
					// pinch zooming
					float newDist = spacing(event);
					Log.d(TAG, "newDist=" + newDist);
					if (newDist > 5f) {
						newMatrix.set(matrixBeforeAction);
						float scale = newDist / distanceBetweenInitialTouchPoints; // setting the scaling of the
						// matrix...if scale > 1 means
						// zoom in...if scale < 1 means
						// zoom out
						newMatrix.postScale(scale, scale, middleBetweenInitialTouchPoints.x, middleBetweenInitialTouchPoints.y);
					}
				}
				break;
		}

		view.setImageMatrix(newMatrix); // display the transformation on screen

		return true; // indicate event was handled
	}

	/*
	 * --------------------------------------------------------------------------
	 * Method: spacing Parameters: MotionEvent Returns: float Description:
	 * checks the spacing between the two fingers on touch
	 * ----------------------------------------------------
	 */
	private float spacing(MotionEvent event) {
		float x = event.getX(0) - event.getX(1);
		float y = event.getY(0) - event.getY(1);
		return (float) Math.sqrt(x * x + y * y);
	}

	/*
	 * --------------------------------------------------------------------------
	 * Method: midPoint Parameters: PointF object, MotionEvent Returns: void
	 * Description: calculates the midpoint between the two fingers
	 * ------------------------------------------------------------
	 */

	/**
	 * Show an event in the LogCat view, for debugging
	 */
	private void dumpEvent(MotionEvent event) {
		String[] names = {"DOWN", "UP", "MOVE", "CANCEL", "OUTSIDE", "POINTER_DOWN", "POINTER_UP", "7?", "8?", "9?"};
		StringBuilder sb = new StringBuilder();
		int action = event.getAction();
		int actionCode = action & MotionEvent.ACTION_MASK;
		sb.append("event ACTION_").append(names[actionCode]);

		if (actionCode == MotionEvent.ACTION_POINTER_DOWN || actionCode == MotionEvent.ACTION_POINTER_UP) {
			sb.append("(pid ").append(action >> MotionEvent.ACTION_POINTER_INDEX_SHIFT);
			sb.append(")");
		}

		sb.append("[");
		for (int i = 0; i < event.getPointerCount(); i++) {
			sb.append("#").append(i);
			sb.append("(pid ").append(event.getPointerId(i));
			sb.append(")=").append((int) event.getX(i));
			sb.append(",").append((int) event.getY(i));
			if (i + 1 < event.getPointerCount())
				sb.append(";");
		}

		sb.append("]");
		Log.d("Touch Events ---------", sb.toString());
	}

	// The 3 states (events) which the user is trying to perform
	private enum CurrentAction {NONE, DRAG, ZOOM}
}
