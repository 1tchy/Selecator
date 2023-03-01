package ch.laurinmurer.selecator.helper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.appcompat.widget.AppCompatImageView;
import ch.laurinmurer.selecator.FirstFragmentSide;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ScrollSynchronizer {
	private final ScrollView scrollView;
	private final LinearLayout scrollViewLayout;
	private final FirstFragmentSide side;
	private final AtomicBoolean isSideBeingScrolledFromOtherSide;
	private final ScrollView otherScrollView;
	private final android.widget.LinearLayout otherScrollViewLayout;
	private final FirstFragmentSide otherSide;
	private final AtomicReference<ObjectAnimator> otherSideAnimationHolder;
	private final AtomicBoolean isOtherSideBeingScrolledFromThisSide;

	public ScrollSynchronizer(ScrollView scrollView, LinearLayout scrollViewLayout, FirstFragmentSide side, AtomicBoolean isSideBeingScrolledFromOtherSide, ScrollView otherScrollView, LinearLayout otherScrollViewLayout, FirstFragmentSide otherSide, AtomicReference<ObjectAnimator> otherSideAnimationHolder, AtomicBoolean isOtherSideBeingScrolledFromThisSide) {
		this.scrollView = scrollView;
		this.scrollViewLayout = scrollViewLayout;
		this.side = side;
		this.isSideBeingScrolledFromOtherSide = isSideBeingScrolledFromOtherSide;
		this.otherScrollView = otherScrollView;
		this.otherScrollViewLayout = otherScrollViewLayout;
		this.otherSide = otherSide;
		this.isOtherSideBeingScrolledFromThisSide = isOtherSideBeingScrolledFromThisSide;
		this.otherSideAnimationHolder = otherSideAnimationHolder;
	}

	@SuppressLint("ClickableViewAccessibility") //Sorry! Because this is an image app, I hope I don't offend anyone
	public void register() {
		scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
			if (!isSideBeingScrolledFromOtherSide.get()) {
				transmitScroll();
			}
		});
		otherScrollView.setOnTouchListener((v, event) -> {
			Optional.ofNullable(otherSideAnimationHolder.get()).ifPresent(ValueAnimator::cancel);
			return false;
		});

	}

	private void transmitScroll() {
		Optional<TopBottom<View>> sichtbareBilder = getTopAndBottomInFocusArea(scrollView, scrollViewLayout, 0.35f);
		if (sichtbareBilder.isPresent()) {
			TopBottom<Instant> sichtbarerZeitbereich = sichtbareBilder.get()
					.map(bild -> side.getLastModifiedForImage((AppCompatImageView) bild));
			Optional<TopBottom<View>> sichtbareBilderAndereSeite = getTopAndBottomInFocusArea(otherScrollView, otherScrollViewLayout, 0.4f);
			if (sichtbareBilderAndereSeite.isPresent()) {
				TopBottom<Instant> sichtbarerZeitbereichAndereSeite = sichtbareBilderAndereSeite.get()
						.map(bild -> otherSide.getLastModifiedForImage((AppCompatImageView) bild));
				boolean shouldShowImageFurtherUp = sichtbarerZeitbereichAndereSeite.top().isBefore(sichtbarerZeitbereich.bottom());
				boolean shouldShowImageFurtherDown = sichtbarerZeitbereich.top().isBefore(sichtbarerZeitbereichAndereSeite.bottom());
				if (shouldShowImageFurtherDown) {
					if (scrollView.getScrollY() < scrollView.getHeight() * 0.8) {
						return;//do not scroll down other side if this side is still on the first page
					}
					Instant maxTimeToShow = sichtbarerZeitbereich.top();
					int indexToScrollTo = 0;
					for (; indexToScrollTo < otherScrollViewLayout.getChildCount(); indexToScrollTo++) {
						Instant lastModifiedAtIndex = otherSide.getLastModifiedForImage((AppCompatImageView) otherScrollViewLayout.getChildAt(indexToScrollTo));
						if (lastModifiedAtIndex.isBefore(maxTimeToShow)) {
							indexToScrollTo--;
							Log.i("DEB", "Will scroll further down to image above the one at " + lastModifiedAtIndex + " because topmost is from " + maxTimeToShow);
							break;
						}
					}
					View scrollTarget = otherScrollViewLayout.getChildAt(indexToScrollTo);
					centerOtherView(scrollTarget);
				} else if (shouldShowImageFurtherUp) {
					Instant minTimeToShow = sichtbarerZeitbereich.bottom();
					int indexToScrollTo = 0;
					for (; indexToScrollTo < otherScrollViewLayout.getChildCount(); indexToScrollTo++) {
						Instant lastModifiedAtIndex = otherSide.getLastModifiedForImage((AppCompatImageView) otherScrollViewLayout.getChildAt(indexToScrollTo));
						if (!lastModifiedAtIndex.isAfter(minTimeToShow)) {
							Log.i("DEB", "Will scroll further up to image of " + lastModifiedAtIndex);
							break;
						}
					}
					View scrollTarget = otherScrollViewLayout.getChildAt(indexToScrollTo);
					centerOtherView(scrollTarget);
				}
			}
		}
	}

	public void centerOtherView(View childToScrollTo) {
		if (childToScrollTo != null) {
			int childOffset = getDeepChildOffsetOfOtherScrollView(childToScrollTo.getParent(), childToScrollTo);
			int scrollOffsetForChildOffset = childOffset - ((otherScrollView.getHeight() - childToScrollTo.getHeight()) / 2);
			scrollOtherViewToOffset(scrollOffsetForChildOffset);
		}
	}

	private void scrollOtherViewToOffset(int childOffset) {
		ObjectAnimator objectAnimator = ObjectAnimator.ofInt(otherScrollView, "scrollY", childOffset);
		objectAnimator.setDuration(8000);
		objectAnimator.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				isOtherSideBeingScrolledFromThisSide.set(false);
			}

			@Override
			public void onAnimationCancel(Animator animation) {
				isOtherSideBeingScrolledFromThisSide.set(false);
			}
		});
		ObjectAnimator previousAnimation = otherSideAnimationHolder.getAndSet(objectAnimator);
		Optional.ofNullable(previousAnimation).ifPresent(ValueAnimator::cancel);
		isOtherSideBeingScrolledFromThisSide.set(true);
		objectAnimator.start();
	}

	/**
	 * We need to scroll to a child in the other ScrollView, but the child may not be a direct child to the other ScrollView.
	 * To get the correct child position to scroll to, we need to iterate through all of its parent views till the main parent (otherScrollView).
	 */
	private int getDeepChildOffsetOfOtherScrollView(ViewParent parent, View child) {
		ViewGroup parentGroup = (ViewGroup) parent;
		int offset = child.getTop();
		if (parentGroup.equals(otherScrollView)) {
			return offset;
		}
		return offset + getDeepChildOffsetOfOtherScrollView(parentGroup.getParent(), parentGroup);
	}

	private static Optional<TopBottom<View>> getTopAndBottomInFocusArea(ScrollView fromScrollView, LinearLayout linearLayout, float noneFocusAreaPart) {
		int i;
		int scrollY = fromScrollView.getScrollY();
		View topVisibleChild = null;
		for (i = 0; i < linearLayout.getChildCount(); i++) {
			View child = linearLayout.getChildAt(i);
			if (child.getY() + child.getHeight() >= scrollY + (fromScrollView.getHeight() * noneFocusAreaPart)) {
				topVisibleChild = child;
				break;
			}
		}
		if (topVisibleChild == null) {
			return Optional.empty();
		}
		View bottomVisibleChild = null;
		for (; i < linearLayout.getChildCount(); i++) {
			View child = linearLayout.getChildAt(i);
			bottomVisibleChild = child;
			if (child.getY() + child.getHeight() >= scrollY + (fromScrollView.getHeight() * (1 - noneFocusAreaPart))) {
				break;
			}
		}
		return Optional.of(new TopBottom<>(topVisibleChild, bottomVisibleChild));
	}
}
