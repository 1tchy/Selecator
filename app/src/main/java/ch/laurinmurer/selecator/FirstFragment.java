package ch.laurinmurer.selecator;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;
import ch.laurinmurer.selecator.databinding.FragmentFirstBinding;
import ch.laurinmurer.selecator.helper.ScrollSynchronizer;
import ch.laurinmurer.selecator.helper.SwipeListener;
import com.google.android.material.snackbar.Snackbar;
import com.obsez.android.lib.filechooser.ChooserDialog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class FirstFragment extends Fragment {

	private static final String PREFS_NAME = "Selecator.FirstFragment";
	private FragmentFirstBinding binding;
	private View.OnTouchListener leftToRightSwipeListener;
	private View.OnTouchListener rightToLeftSwipeListener;
	private FirstFragmentSide fromSide;
	private FirstFragmentSide toSide;
	private final AtomicBoolean canFilesNowBeLoaded = new AtomicBoolean(false);

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		binding = FragmentFirstBinding.inflate(inflater, container, false);
		binding.fromPath.setOnClickListener(v ->
				new ChooserDialog(requireContext())
						.withFilter(true, false)
						.withChosenListener((path, pathFile) -> setFromPath(pathFile, requireContext()))
						.build().show()
		);
		binding.toPath.setOnClickListener(v ->
				new ChooserDialog(requireContext())
						.withFilter(true, false)
						.withChosenListener((path, pathFile) -> setToPath(pathFile, requireContext()))
						.build().show()
		);
		List<Consumer<AppCompatImageView>> leftToRightSwipedObserver = new ArrayList<>(1);
		leftToRightSwipeListener = new SwipeListener(true, binding.fromScrollView, v -> {
			File anImage = fromSide.getFileForImage((AppCompatImageView) v);
			Path target = toSide.getPath().resolve(anImage.getName());
			boolean moveSuccessful = move(v, anImage, target);
			if (moveSuccessful) {
				AppCompatImageView newView = toSide.loadImage(rightToLeftSwipeListener, target.toFile());
				leftToRightSwipedObserver.forEach(observer -> observer.accept(newView));
			}
			return moveSuccessful;
		});
		List<Consumer<AppCompatImageView>> rightToLeftSwipedObserver = new ArrayList<>(1);
		rightToLeftSwipeListener = new SwipeListener(false, binding.toScrollView, v -> {
			File anImage = toSide.getFileForImage((AppCompatImageView) v);
			Path target = fromSide.getPath().resolve(anImage.getName());
			boolean moveSuccessful = move(v, anImage, target);
			if (moveSuccessful) {
				AppCompatImageView newView = fromSide.loadImage(leftToRightSwipeListener, target.toFile());
				rightToLeftSwipedObserver.forEach(observer -> observer.accept(newView));
			}
			return moveSuccessful;
		});
		fromSide = new FirstFragmentSide(binding.fromPath,
				binding.fromScrollViewLayout,
				leftToRightSwipeListener,
				this::checkIntroductionStillNeeded,
				requireContext(),
				requireActivity()::runOnUiThread,
				canFilesNowBeLoaded);
		toSide = new FirstFragmentSide(binding.toPath,
				binding.toScrollViewLayout,
				rightToLeftSwipeListener,
				this::checkIntroductionStillNeeded,
				requireContext(),
				requireActivity()::runOnUiThread,
				canFilesNowBeLoaded);
		AtomicReference<ObjectAnimator> toScrollViewAnimationHolder = new AtomicReference<>();
		AtomicReference<ObjectAnimator> fromScrollViewAnimationHolder = new AtomicReference<>();
		AtomicBoolean isToSideBeingScrolledFromOtherSide = new AtomicBoolean();
		AtomicBoolean isFromSideBeingScrolledFromOtherSide = new AtomicBoolean();
		ScrollSynchronizer leftToRightScrollSynchronizer = new ScrollSynchronizer(
				binding.fromScrollView, binding.fromScrollViewLayout, fromSide, isFromSideBeingScrolledFromOtherSide,
				binding.toScrollView, binding.toScrollViewLayout, toSide, toScrollViewAnimationHolder, isToSideBeingScrolledFromOtherSide
		);
		leftToRightScrollSynchronizer.register();
		leftToRightSwipedObserver.add(leftToRightScrollSynchronizer::centerOtherView);
		ScrollSynchronizer rightToLeftScrollSynchronizer = new ScrollSynchronizer(
				binding.toScrollView, binding.toScrollViewLayout, toSide, isToSideBeingScrolledFromOtherSide,
				binding.fromScrollView, binding.fromScrollViewLayout, fromSide, fromScrollViewAnimationHolder, isFromSideBeingScrolledFromOtherSide
		);
		rightToLeftScrollSynchronizer.register();
		rightToLeftSwipedObserver.add(rightToLeftScrollSynchronizer::centerOtherView);

		restorePreferences(requireContext());
		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			public void onGlobalLayout() {
				view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
				//After the view is fully loaded
				canFilesNowBeLoaded.set(true);
				loadAllFiles();
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		if (canFilesNowBeLoaded.get()) {
			loadAllFiles();
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		fromSide.onDestroyView();
		fromSide = null;
		toSide.onDestroyView();
		toSide = null;
		binding = null;
	}

	private void restorePreferences(Context context) {
		SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
		String fromPathFromPreferences = settings.getString("fromPath", null);
		String toPathFromPreferences = settings.getString("toPath", null);
		if (fromPathFromPreferences != null) {
			fromSide.setPathVariables(Paths.get(fromPathFromPreferences).toFile());
		}
		if (toPathFromPreferences != null) {
			toSide.setPathVariables(Paths.get(toPathFromPreferences).toFile());
		}
	}

	private void savePreferences(Context context) {
		SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		if (fromSide.getPath() != null) {
			editor.putString("fromPath", fromSide.getPath().toString());
		}
		if (toSide.getPath() != null) {
			editor.putString("toPath", toSide.getPath().toString());
		}
		editor.apply();
	}

	private void setFromPath(File path, Context context) {
		fromSide.setPathVariables(path);
		savePreferences(context);
	}

	private void setToPath(File path, Context context) {
		toSide.setPathVariables(path);
		savePreferences(context);
	}

	private void checkIntroductionStillNeeded() {
		if (fromSide.hasValidDirectorySelected() && toSide.hasValidDirectorySelected()) {
			binding.introductionExplanationLabel.setVisibility(View.GONE);
		}
	}

	private void loadAllFiles() {
		if (fromSide != null && toSide != null) {
			fromSide.loadFilesInNewThread();
			toSide.loadFilesInNewThread();
		}
	}

	@SuppressWarnings("unused")
	private static boolean move(View view, File anImage, Path target) {
		try {
			Files.move(anImage.toPath(), target);
			MediaScannerConnection.scanFile(view.getContext(), new String[]{anImage.getAbsolutePath()}, null /*mimeTypes*/, (s, uri) -> {
			});
			MediaScannerConnection.scanFile(view.getContext(), new String[]{target.toFile().getAbsolutePath()}, null /*mimeTypes*/, (s, uri) -> {
			});
		} catch (IOException e) {
			Snackbar.make(view, "Failed to move " + anImage.getName() + " to " + target + ": " + e.getLocalizedMessage(), Snackbar.LENGTH_LONG)
					.setAction("Action", null).show();
			Log.e("Error", "Failed to move " + anImage.getName() + " to " + target, e);
			return false;
		}
		Log.i("Success", "Moved " + anImage.getName() + " to " + target);
		return true;
	}

	@SuppressWarnings("unused")
	private static boolean fakeMove(View view, File anImage, Path target) {
		if (Math.random() > 0.1) {
			Snackbar.make(view, "Would have moved " + anImage.getName() + " to " + target, Snackbar.LENGTH_LONG)
					.setAction("Action", null).show();
		} else {
			Snackbar.make(view, "Failed to move " + anImage.getName() + " to " + target + ": Hier könnte der Fehler stehen", Snackbar.LENGTH_LONG)
					.setAction("Action", null).show();
			Log.e("Error", "Failed to move " + anImage.getName() + " to " + target);
			return false;
		}
		Log.i("Success", "Moved " + anImage.getName() + " to " + target);
		return true;
	}
}