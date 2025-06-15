package ch.laurinmurer.selecator;

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
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;

import com.google.android.material.snackbar.Snackbar;
import com.obsez.android.lib.filechooser.ChooserDialog;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import ch.laurinmurer.selecator.databinding.FragmentFirstBinding;
import ch.laurinmurer.selecator.helper.ScrollSynchronizer;
import ch.laurinmurer.selecator.helper.SwipeListener;

public class FirstFragment extends Fragment {

	private static final String PREFS_NAME = "Selecator.FirstFragment";
	private FragmentFirstBinding binding;
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
		List<Consumer<SelecatorRecyclerViewAdapter.Data>> leftToRightSwipedObserver = new ArrayList<>(1);
		View.OnTouchListener leftToRightSwipeListener = new SwipeListener(true, binding.fromRecyclerView, v -> {
			SelecatorRecyclerViewAdapter.Data imageData = fromSide.getImageDataForView((AppCompatImageView) v);
			String fileName = imageData.imageFileName();
			Path anImage = fromSide.getPath().resolve(fileName);
			Path target = toSide.getPath().resolve(fileName);
			boolean moveSuccessful = move(v, anImage, target);
			if (moveSuccessful) {
				SelecatorRecyclerViewAdapter.Data movedFile = toSide.loadImage(target.toFile());
				fromSide.removeImage(imageData);
				leftToRightSwipedObserver.forEach(observer -> observer.accept(movedFile));
			}
		});
		List<Consumer<SelecatorRecyclerViewAdapter.Data>> rightToLeftSwipedObserver = new ArrayList<>(1);
		View.OnTouchListener rightToLeftSwipeListener = new SwipeListener(false, binding.toRecyclerView, v -> {
			SelecatorRecyclerViewAdapter.Data imageData = toSide.getImageDataForView((AppCompatImageView) v);
			String fileName = imageData.imageFileName();
			Path anImage = toSide.getPath().resolve(fileName);
			Path target = fromSide.getPath().resolve(fileName);
			boolean moveSuccessful = move(v, anImage, target);
			if (moveSuccessful) {
				SelecatorRecyclerViewAdapter.Data movedFile = fromSide.loadImage(target.toFile());
				toSide.removeImage(imageData);
				rightToLeftSwipedObserver.forEach(observer -> observer.accept(movedFile));
			}
		});

		AtomicReference<Path> fromPath = new AtomicReference<>();
		SelecatorRecyclerViewAdapter fromSideRecyclerViewAdapter = new SelecatorRecyclerViewAdapter(requireContext(), binding.fromRecyclerView, leftToRightSwipeListener, requireActivity()::runOnUiThread, fromPath);
		fromSide = new FirstFragmentSide("from",
				binding.fromPath,
				this::checkIntroductionStillNeeded,
				canFilesNowBeLoaded,
				fromPath,
				fromSideRecyclerViewAdapter);
		binding.fromRecyclerView.setAdapter(fromSideRecyclerViewAdapter);
		binding.fromRecyclerView.addItemDecoration(createDividerItemDecoration(requireContext()));

		AtomicReference<Path> toPath = new AtomicReference<>();
		SelecatorRecyclerViewAdapter toSideRecyclerViewAdapter = new SelecatorRecyclerViewAdapter(requireContext(), binding.toRecyclerView, rightToLeftSwipeListener, requireActivity()::runOnUiThread, toPath);
		toSide = new FirstFragmentSide("to",
				binding.toPath,
				this::checkIntroductionStillNeeded,
				canFilesNowBeLoaded,
				toPath,
				toSideRecyclerViewAdapter);
		binding.toRecyclerView.setAdapter(toSideRecyclerViewAdapter);
		binding.toRecyclerView.addItemDecoration(createDividerItemDecoration(requireContext()));

		AtomicReference<ScrollSynchronizer.SelecatorSmoothScroller> toScrollViewAnimationHolder = new AtomicReference<>();
		AtomicReference<ScrollSynchronizer.SelecatorSmoothScroller> fromScrollViewAnimationHolder = new AtomicReference<>();
		List<AtomicReference<Instant>> finishTimesOfToSideBeingScrolledFromOtherSide = Collections.synchronizedList(new ArrayList<>());
		List<AtomicReference<Instant>> finishTimesOfFromSideBeingScrolledFromOtherSide = Collections.synchronizedList(new ArrayList<>());
		ScrollSynchronizer leftToRightScrollSynchronizer = new ScrollSynchronizer(
				"left", binding.fromRecyclerView, fromSide,
				finishTimesOfFromSideBeingScrolledFromOtherSide, binding.toRecyclerView, toSideRecyclerViewAdapter, toSide, toScrollViewAnimationHolder, finishTimesOfToSideBeingScrolledFromOtherSide
		);
		leftToRightScrollSynchronizer.register();
		leftToRightSwipedObserver.add(leftToRightScrollSynchronizer::centerOtherView);
		ScrollSynchronizer rightToLeftScrollSynchronizer = new ScrollSynchronizer("right",
				binding.toRecyclerView, toSide,
				finishTimesOfToSideBeingScrolledFromOtherSide, binding.fromRecyclerView, fromSideRecyclerViewAdapter, fromSide, fromScrollViewAnimationHolder, finishTimesOfFromSideBeingScrolledFromOtherSide
		);
		rightToLeftScrollSynchronizer.register();
		rightToLeftSwipedObserver.add(rightToLeftScrollSynchronizer::centerOtherView);

		restorePreferences(requireContext());
		return binding.getRoot();
	}

	private static DividerItemDecoration createDividerItemDecoration(Context context) {
		DividerItemDecoration divider = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
		divider.setDrawable(Objects.requireNonNull(AppCompatResources.getDrawable(context, android.R.drawable.divider_horizontal_dim_dark)));
		return divider;
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
	private static boolean move(View view, Path anImage, Path target) {
		try {
			try {
				Files.move(anImage, target);
			} catch (FileAlreadyExistsException fileAlreadyExistsException) {
				if (!anImage.equals(target) && !filesMismatch(anImage, target)) {
					Files.delete(anImage); //The image already exists at the target
				} else {
					throw fileAlreadyExistsException;
				}
			}
			MediaScannerConnection.scanFile(view.getContext(), new String[]{anImage.toAbsolutePath().toString()}, null /*mimeTypes*/, (s, uri) -> {
			});
			MediaScannerConnection.scanFile(view.getContext(), new String[]{target.toAbsolutePath().toString()}, null /*mimeTypes*/, (s, uri) -> {
			});
		} catch (IOException e) {
			Snackbar.make(view, "Failed to move " + anImage.getFileName() + " to " + target + ": " + e.getLocalizedMessage(), Snackbar.LENGTH_LONG)
					.setAction("Action", null).show();
			Log.e("Error", "Failed to move " + anImage.getFileName() + " to " + target, e);
			return false;
		}
		Log.i("Success", "Moved " + anImage.getFileName() + " to " + target);
		return true;
	}

	/**
	 * Similar to java.nio.file.Files.mismatch() - but this is not (yet) available
	 */
	public static boolean filesMismatch(Path path1, Path path2) throws IOException {
		if (Files.size(path1) != Files.size(path2)) {
			return true;
		}

		try (InputStream is1 = new BufferedInputStream(Files.newInputStream(path1));
			 InputStream is2 = new BufferedInputStream(Files.newInputStream(path2))) {
			int byte1, byte2;
			while ((byte1 = is1.read()) != -1 && (byte2 = is2.read()) != -1) {
				if (byte1 != byte2) {
					return true;
				}
			}
		}
		return false;
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