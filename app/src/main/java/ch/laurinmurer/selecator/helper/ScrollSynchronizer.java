package ch.laurinmurer.selecator.helper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import ch.laurinmurer.selecator.FirstFragmentSide;
import ch.laurinmurer.selecator.SelecatorRecyclerViewAdapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

public class ScrollSynchronizer {
	private static final int SECONDS_TO_SYNC_SCROLL = 8;
	private final String sideName;
	private final RecyclerView recyclerView;
	private final FirstFragmentSide side;
	private final List<AtomicReference<Instant>> finishTimesOfSideBeingScrolledFromOtherSide;
	private final RecyclerView otherRecyclerView;
	private final SelecatorRecyclerViewAdapter otherRecyclerViewAdapter;
	private final FirstFragmentSide otherSide;
	private final AtomicReference<SelecatorSmoothScroller> otherSideAnimationHolder;
	private final List<AtomicReference<Instant>> finishTimesOfOtherSideScrollsFromThisSide;

	public ScrollSynchronizer(String sideName, RecyclerView recyclerView, FirstFragmentSide side, List<AtomicReference<Instant>> finishTimesOfSideBeingScrolledFromOtherSide, RecyclerView otherRecyclerView, SelecatorRecyclerViewAdapter otherRecyclerViewAdapter, FirstFragmentSide otherSide, AtomicReference<SelecatorSmoothScroller> otherSideAnimationHolder, List<AtomicReference<Instant>> finishTimesOfOtherSideScrollsFromThisSide) {
		this.sideName = sideName;
		this.recyclerView = recyclerView;
		this.side = side;
		this.finishTimesOfSideBeingScrolledFromOtherSide = finishTimesOfSideBeingScrolledFromOtherSide;
		this.otherRecyclerView = otherRecyclerView;
		this.otherRecyclerViewAdapter = otherRecyclerViewAdapter;
		this.otherSide = otherSide;
		this.otherSideAnimationHolder = otherSideAnimationHolder;
		this.finishTimesOfOtherSideScrollsFromThisSide = finishTimesOfOtherSideScrollsFromThisSide;
	}

	@SuppressLint("ClickableViewAccessibility") //Sorry! Because this is an image app, I hope I don't offend anyone
	public void register() {
		recyclerView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
			boolean sideBeingScrolledFromOtherSide = isSideBeingScrolledFromOtherSide();
			if (!sideBeingScrolledFromOtherSide) {
				transmitScroll();
			}
		});
		otherRecyclerView.setOnTouchListener((v, event) -> {
			Optional.ofNullable(otherSideAnimationHolder.get()).ifPresent(SelecatorSmoothScroller::doStop);
			return false;
		});
	}

	private boolean isSideBeingScrolledFromOtherSide() {
		Instant referenceTime = Instant.now().minusSeconds(SECONDS_TO_SYNC_SCROLL).minusMillis(200);
		while (!finishTimesOfSideBeingScrolledFromOtherSide.isEmpty()) {
			AtomicReference<Instant> aFinishTimeReference = finishTimesOfSideBeingScrolledFromOtherSide.get(0);
			Instant aFinishTime = aFinishTimeReference == null ? null : aFinishTimeReference.get();
			if (aFinishTime != null && aFinishTime.isBefore(referenceTime)) {
				finishTimesOfSideBeingScrolledFromOtherSide.remove(aFinishTimeReference);
			} else {
				return true;
			}
		}
		return false;
	}

	private void transmitScroll() {
		Optional<TopBottom<View>> sichtbareBilder = getTopAndBottomInFocusArea(recyclerView, 0.35f);
		if (sichtbareBilder.isPresent()) {
			TopBottom<Instant> sichtbarerZeitbereich = sichtbareBilder.get()
					.map(bild -> side.getLastModifiedForImage((AppCompatImageView) bild));
			Optional<TopBottom<View>> sichtbareBilderAndereSeite = getTopAndBottomInFocusArea(otherRecyclerView, 0.4f);
			if (sichtbareBilderAndereSeite.isPresent()) {
				TopBottom<Instant> sichtbarerZeitbereichAndereSeite = sichtbareBilderAndereSeite.get()
						.map(bild -> otherSide.getLastModifiedForImage((AppCompatImageView) bild));
				boolean shouldShowImageFurtherUp = sichtbarerZeitbereichAndereSeite.top().isBefore(sichtbarerZeitbereich.bottom());
				boolean shouldShowImageFurtherDown = sichtbarerZeitbereich.top().isBefore(sichtbarerZeitbereichAndereSeite.bottom());
				if (shouldShowImageFurtherDown) {
					if (((LinearLayoutManager) requireNonNull(recyclerView.getLayoutManager())).findFirstVisibleItemPosition() == 0) {
						return;//do not scroll down other side if this side is still showing the first element
					}
					Instant maxTimeToShow = sichtbarerZeitbereich.top();
					int indexToScrollTo = 0;
					for (; indexToScrollTo < otherRecyclerViewAdapter.getItemCount(); indexToScrollTo++) {
						Instant lastModifiedAtIndex = otherRecyclerViewAdapter.getData(indexToScrollTo).lastModifiedInstant();
						if (lastModifiedAtIndex.isBefore(maxTimeToShow)) {
							indexToScrollTo--;
							Log.i("DEB", "Will scroll other side of '" + sideName + "' further down to image above the one at " + lastModifiedAtIndex + " because topmost is from " + maxTimeToShow);
							break;
						}
					}
					centerOtherView(indexToScrollTo);
				} else if (shouldShowImageFurtherUp) {
					Instant minTimeToShow = sichtbarerZeitbereich.bottom();
					int indexToScrollTo = 0;
					for (; indexToScrollTo < otherRecyclerViewAdapter.getItemCount(); indexToScrollTo++) {
						Instant lastModifiedAtIndex = otherRecyclerViewAdapter.getData(indexToScrollTo).lastModifiedInstant();
						if (!lastModifiedAtIndex.isAfter(minTimeToShow)) {
							Log.i("DEB", "Will scroll other side of '" + sideName + "' further up to image of " + lastModifiedAtIndex);
							break;
						}
					}
					centerOtherView(indexToScrollTo);
				}
			}
		}
	}

	public void centerOtherView(int childToScrollTo) {
		Boolean shouldSmoothScroll = getTopAndBottomInFocusArea(otherRecyclerView, 0)
				.map(topAndBottomViews -> {
					int topIndex = otherRecyclerViewAdapter.indexOf(otherRecyclerViewAdapter.getCurrentBinding((AppCompatImageView) topAndBottomViews.top()));
					int bottomIndex = otherRecyclerViewAdapter.indexOf(otherRecyclerViewAdapter.getCurrentBinding((AppCompatImageView) topAndBottomViews.bottom()));
					int maxIndexesToScroll = Math.max(1, bottomIndex - topIndex) * 2;
					return childToScrollTo > topIndex - maxIndexesToScroll && childToScrollTo < bottomIndex + maxIndexesToScroll;
				}).orElse(true);
		if (!shouldSmoothScroll) {
			otherRecyclerView.scrollToPosition(childToScrollTo);
		}
		LinearSmoothScroller smoothScroller = new SelecatorSmoothScroller(otherRecyclerView.getContext(), "other side of " + sideName, finishTimesOfOtherSideScrollsFromThisSide);
		smoothScroller.setTargetPosition(childToScrollTo);
		requireNonNull(otherRecyclerView.getLayoutManager()).startSmoothScroll(smoothScroller);
	}

	public void centerOtherView(SelecatorRecyclerViewAdapter.Data childToScrollTo) {
		if (childToScrollTo != null) {
			otherRecyclerViewAdapter.scrollTo(childToScrollTo);
		}
	}

	private static Optional<TopBottom<View>> getTopAndBottomInFocusArea(RecyclerView recyclerView, float noneFocusAreaPart) {
		int i;
		int scrollY = recyclerView.getScrollY();
		View topVisibleChild = null;
		for (i = 0; i < recyclerView.getChildCount(); i++) {
			View child = recyclerView.getChildAt(i);
			if (child.getY() + child.getHeight() >= scrollY + (recyclerView.getHeight() * noneFocusAreaPart)) {
				topVisibleChild = child;
				break;
			}
		}
		if (topVisibleChild == null) {
			return Optional.empty();
		}
		View bottomVisibleChild = null;
		for (; i < recyclerView.getChildCount(); i++) {
			View child = recyclerView.getChildAt(i);
			bottomVisibleChild = child;
			if (child.getY() + child.getHeight() >= scrollY + (recyclerView.getHeight() * (1 - noneFocusAreaPart))) {
				break;
			}
		}
		return Optional.of(new TopBottom<>(topVisibleChild, bottomVisibleChild));
	}

	public static class SelecatorSmoothScroller extends LinearSmoothScroller {
		private final AtomicReference<Instant> finishTimeReference = new AtomicReference<>();
		/**
		 * @noinspection unused, FieldCanBeLocal - useful for debugging
		 */
		private final String sideName;

		public SelecatorSmoothScroller(Context context, String sideName, List<AtomicReference<Instant>> finishTimesOfOtherSideScrollsFromThisSide) {
			super(context);
			this.sideName = sideName;
			finishTimesOfOtherSideScrollsFromThisSide.add(finishTimeReference);
		}

		@Override
		protected void onStop() {
			super.onStop();
			finishTimeReference.set(Instant.now());
		}

		@Override
		protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
			return super.calculateSpeedPerPixel(displayMetrics) * 2 * SECONDS_TO_SYNC_SCROLL;
		}

		public void doStop() {
			stop();
		}
	}
}
