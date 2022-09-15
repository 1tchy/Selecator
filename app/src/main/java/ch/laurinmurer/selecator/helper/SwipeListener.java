package ch.laurinmurer.selecator.helper;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ScrollView;

import java.util.function.Predicate;

public class SwipeListener implements View.OnTouchListener {
	private static final int SWIPE_SPEED = 2;
	private static final int MOVE_SPEED = 2;
	private final boolean leftToRight;
	private final ScrollView mListView;
	private final Predicate<View> action;
	private float mDownX;
	private int mSwipeSlop = -1;
	private boolean mSwiping = false;
	private boolean mItemPressed = false;

	public SwipeListener(boolean leftToRight, ScrollView mListView, Predicate<View> action) {
		this.mListView = mListView;
		this.leftToRight = leftToRight;
		this.action = action;
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if (mSwipeSlop < 0) {
			mSwipeSlop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
		}
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				if (mItemPressed) {
					// Multi-item swipes not handled
					return false;
				}
				mItemPressed = true;
				mDownX = event.getX();
				break;
			case MotionEvent.ACTION_CANCEL:
				v.setAlpha(1);
				v.setTranslationX(0);
				mItemPressed = false;
				break;
			case MotionEvent.ACTION_MOVE: {
				float x = event.getX() + v.getTranslationX();
				float deltaX = x - mDownX;
				float deltaXAbs = Math.abs(deltaX);
				if (!mSwiping) {
					if ((leftToRight && deltaX > mSwipeSlop) || (!leftToRight && -deltaX > mSwipeSlop)) {
						mSwiping = true;
						mListView.requestDisallowInterceptTouchEvent(true);
//								mBackgroundContainer.showBackground(v.getTop(), v.getHeight());
					}
				}
				if (mSwiping) {
					v.setTranslationX((x - mDownX));
					v.setAlpha(1 - deltaXAbs / v.getWidth());
				}
			}
			break;
			case MotionEvent.ACTION_UP: {
				// User let go - figure out whether to animate the view out, or back into place
				if (mSwiping) {
					float x = event.getX() + v.getTranslationX();
					float deltaX = x - mDownX;
					float deltaXAbs = Math.abs(deltaX);
					float fractionCovered;
					float endX;
					float endAlpha;
					final boolean remove;
					if (deltaXAbs > v.getWidth() / 4.) {
						// Greater than a quarter of the width - animate it out
						fractionCovered = deltaXAbs / v.getWidth();
						endX = deltaX < 0 ? -v.getWidth() : v.getWidth();
						endAlpha = 0;
						remove = true;
					} else {
						// Not far enough - animate it back
						fractionCovered = 1 - (deltaXAbs / v.getWidth());
						endX = 0;
						endAlpha = 1;
						remove = false;
					}
					// Animate position and alpha of swiped item
					// NOTE: This is a simplified version of swipe behavior, for the
					// purposes of this demo about animation. A real version should use
					// velocity (via the VelocityTracker class) to send the item off or
					// back at an appropriate speed.
					long duration = Math.max(1, (int) ((1 - fractionCovered) * v.getWidth() / SWIPE_SPEED));
					mListView.setEnabled(false);
					v.animate().setDuration(duration)
							.alpha(endAlpha).translationX(endX)
							.withEndAction(() -> {
								if (remove) {
									if (action.test(v)) {
										int height = v.getHeight();
										Animation resize = new Animation() {
											private boolean finished = false;

											@Override
											protected void applyTransformation(float interpolatedTime, Transformation t) {
												if (finished) {
													return;
												}
												v.getLayoutParams().height = height - (int) (height * interpolatedTime);
												v.requestLayout();
												if (interpolatedTime >= 1f) {
													finished = true;
//																binding.scrollViewLayout.removeView(v);
													mSwiping = false;
													mListView.setEnabled(true);
												}
											}

										};
										resize.setDuration(v.getHeight() / MOVE_SPEED);
										v.startAnimation(resize);
									} else {
										v.setAlpha(1);
										v.setTranslationX(0);
										mSwiping = false;
										mListView.setEnabled(true);
									}
								} else {
									// Restore animated values
									v.setAlpha(1);
									v.setTranslationX(0);
//												mBackgroundContainer.hideBackground();
									mSwiping = false;
									mListView.setEnabled(true);
								}
							});
				} else {
					v.performClick();
				}
			}
			mItemPressed = false;
			break;
			default:
				return false;
		}
		return true;
	}
}
