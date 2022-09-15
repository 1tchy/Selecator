package ch.laurinmurer.selecator;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.*;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import ch.laurinmurer.selecator.databinding.FragmentFirstBinding;
import ch.laurinmurer.selecator.helper.BitmapLoader;
import ch.laurinmurer.selecator.helper.SwipeListener;
import ch.laurinmurer.selecator.helper.ZoomInZoomOut;
import com.google.android.material.snackbar.Snackbar;
import com.obsez.android.lib.filechooser.ChooserDialog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public class FirstFragment extends Fragment {

	private static final String PREFS_NAME = "Selecator.FirstFragment";
	private static final Set<String> SUPPORTED_FILE_SUFFIXES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"bmp", "gif", "jpg", "jpeg", "png", "webp", "heic", "heif"
	)));
	private FragmentFirstBinding binding;
	private Path fromPath;
	private Path toPath;
	private long time = System.currentTimeMillis();
	private final Map<AppCompatImageView, ImageMetadata> imageMetadata = new ConcurrentHashMap<>();
	private View.OnTouchListener leftToRightSwipeListener;
	private View.OnTouchListener rightToLeftSwipeListener;
	private ExecutorService fromImageLoaderExecutor;
	private ExecutorService toImageLoaderExecutor;

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		fromImageLoaderExecutor = Executors.newSingleThreadExecutor();
		toImageLoaderExecutor = Executors.newSingleThreadExecutor();
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
		leftToRightSwipeListener = new SwipeListener(true, binding.fromScrollView, v -> {
			File anImage = requireNonNull(imageMetadata.get((AppCompatImageView) v)).getFile();
			Path target = toPath.resolve(anImage.getName());
			boolean moveSuccessful = move(v, anImage, target);
			if (moveSuccessful) {
				loadImage(requireContext(),
						rightToLeftSwipeListener,
						getToFilesMaxWidth(),
						() -> binding.toScrollViewLayout,
						target.toFile());
			}
			return moveSuccessful;
		});
		rightToLeftSwipeListener = new SwipeListener(false, binding.toScrollView, v -> {
			File anImage = requireNonNull(imageMetadata.get((AppCompatImageView) v)).getFile();
			Path target = fromPath.resolve(anImage.getName());
			boolean moveSuccessful = move(v, anImage, target);
			if (moveSuccessful) {
				loadImage(requireContext(),
						leftToRightSwipeListener,
						getFromFilesMaxWidth(),
						() -> binding.fromScrollViewLayout,
						target.toFile());
			}
			return moveSuccessful;
		});
		checkPermissionMissing();
		restorePreferences(requireContext());
		return binding.getRoot();
	}

	private int calculateNewIndex(ViewGroup layout, AppCompatImageView newImage) {
		long lastModifiedOfNewImage = requireNonNull(imageMetadata.get(newImage)).getLastModified();
		for (int index = 0; index < layout.getChildCount(); index++) {
			AppCompatImageView childAt = (AppCompatImageView) layout.getChildAt(index);
			long lastModifiedOfChildAt = requireNonNull(imageMetadata.get(childAt)).getLastModified();
			if (lastModifiedOfChildAt < lastModifiedOfNewImage) {
				return index;
			}
		}
		return layout.getChildCount();
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			public void onGlobalLayout() {
				view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
				//After the view is fully loaded
				loadAllFiles(requireContext());
			}
		});
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		fromImageLoaderExecutor.shutdown();
		fromImageLoaderExecutor = null;
		toImageLoaderExecutor.shutdown();
		toImageLoaderExecutor = null;
		binding = null;
	}

	private void restorePreferences(Context context) {
		SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
		String fromPathFromPreferences = settings.getString("fromPath", null);
		String toPathFromPreferences = settings.getString("toPath", null);
		if (fromPathFromPreferences != null) {
			setFromPathVariables(Paths.get(fromPathFromPreferences).toFile());
		}
		if (toPathFromPreferences != null) {
			setToPathVariables(Paths.get(toPathFromPreferences).toFile());
		}
	}

	private void savePreferences(Context context) {
		SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		if (fromPath != null) {
			editor.putString("fromPath", fromPath.toString());
		}
		if (toPath != null) {
			editor.putString("toPath", toPath.toString());
		}
		editor.apply();
	}

	private void checkPermissionMissing() {
		if (!Environment.isExternalStorageManager()) {
			NavHostFragment.findNavController(FirstFragment.this).navigate(R.id.action_FirstFragment_to_CheckPermissionFragment);
		}
	}

	private void setFromPath(File path, Context context) {
		setFromPathVariables(path);
		savePreferences(context);
		loadFromFiles(context);
	}

	private void setFromPathVariables(File path) {
		binding.fromPath.setText(path.getName());
		fromPath = path.toPath();
		checkIntroductionStillNeeded();
	}

	private void setToPath(File path, Context context) {
		setToPathVariables(path);
		savePreferences(context);
		loadToFiles(context);
	}

	private void setToPathVariables(File path) {
		binding.toPath.setText(path.getName());
		toPath = path.toPath();
		checkIntroductionStillNeeded();
	}

	private void checkIntroductionStillNeeded() {
		if (fromPath != null && fromPath.toFile().isDirectory() && toPath != null && toPath.toFile().isDirectory()) {
			binding.introductionExplanationLabel.setVisibility(View.GONE);
		}
	}

	private void loadAllFiles(Context context) {
		loadFromFiles(context);
		loadToFiles(context);
	}

	private void loadFromFiles(Context context) {
		if (fromPath != null && fromPath.toFile().isDirectory()) {
			fromImageLoaderExecutor.submit(() -> loadImages(context,
					fromPath.toFile().listFiles(),
					leftToRightSwipeListener,
					getFromFilesMaxWidth(),
					() -> binding.fromScrollViewLayout));
		}
	}

	private static int getFromFilesMaxWidth() {
		return Resources.getSystem().getDisplayMetrics().widthPixels * 3 / 4;
	}

	private void loadToFiles(Context context) {
		if (toPath != null && toPath.toFile().isDirectory()) {
			toImageLoaderExecutor.submit(() -> loadImages(context,
					toPath.toFile().listFiles(),
					rightToLeftSwipeListener,
					getToFilesMaxWidth(),
					() -> binding.toScrollViewLayout));
		}
	}

	private static int getToFilesMaxWidth() {
		return Resources.getSystem().getDisplayMetrics().widthPixels / 4;
	}

	private void loadImages(Context context, File[] filesInPath, View.OnTouchListener swipeListener, int maxWidth, Supplier<ViewGroup> viewSupplierToAddImageTo) {
		performanceLog("Point 0");
		Arrays.stream(filesInPath == null ? new File[0] : filesInPath)
				.filter(File::isFile)
				.filter(f -> !f.getName().startsWith("."))
				.filter(f -> hasASupportedSuffix(f.getName()))
				.sorted(Comparator.comparingLong(File::lastModified).reversed())
				.forEach(anImage -> loadImage(context, swipeListener, maxWidth, viewSupplierToAddImageTo, anImage));
		performanceLog("Point 2");
	}

	private static boolean hasASupportedSuffix(String fileName) {
		int lastIndexOf = fileName.lastIndexOf(".");
		if (lastIndexOf > 0) {
			String suffix = fileName.substring(lastIndexOf + 1);
			return SUPPORTED_FILE_SUFFIXES.contains(suffix.toLowerCase());
		}
		return false;
	}

	@SuppressLint("ClickableViewAccessibility")
	private void loadImage(Context context, View.OnTouchListener swipeListener, int maxWidth, Supplier<ViewGroup> viewSupplierToAddImageTo, File anImage) {
		ViewGroup viewToAddImageTo = viewSupplierToAddImageTo.get();
		if (!alreadyContainsImage(viewToAddImageTo, anImage)) {
			performanceLog("Loading image: " + anImage.getName());
			AppCompatImageView newImage = new AppCompatImageView(context);
			newImage.setImageBitmap(BitmapLoader.fromFile(anImage, maxWidth));
			newImage.setAdjustViewBounds(true);
			newImage.setScaleType(ImageView.ScaleType.FIT_XY);
			newImage.setOnClickListener(v -> {
				Log.i("Clicky", "Clicked on " + imageMetadata.get((AppCompatImageView) v));
				showImage(Uri.fromFile(anImage));
			});
			newImage.setOnTouchListener(swipeListener);
			imageMetadata.put(newImage, new ImageMetadata(anImage, anImage.lastModified()));
			requireActivity().runOnUiThread(() -> viewToAddImageTo.addView(newImage, calculateNewIndex(viewToAddImageTo, newImage)));
		}
	}

	private boolean alreadyContainsImage(ViewGroup view, File image) {
		for (int index = 0; index < view.getChildCount(); index++) {
			AppCompatImageView childAt = (AppCompatImageView) view.getChildAt(index);
			if (childAt.getHeight() > 0 && requireNonNull(imageMetadata.get(childAt)).getFile().getAbsolutePath().equals(image.getAbsolutePath())) {
				return true;
			}
		}
		return false;
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

	@Override
	public void onResume() {
		super.onResume();
		loadAllFiles(requireContext());
		Log.d("Debug", "onResume() called! :-)");
	}

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

	public void showImage(Uri image) {
		Dialog builder = new Dialog(requireContext(), android.R.style.Theme_Light);
		builder.requestWindowFeature(Window.FEATURE_NO_TITLE);
		builder.getWindow().setBackgroundDrawable(new ColorDrawable(Color.argb(200, 0, 0, 0)));
		builder.setOnDismissListener(dialogInterface -> {
		});

		ImageView imageView = new ImageView(requireContext());
		imageView.setImageURI(image);
		imageView.setOnClickListener(v -> builder.dismiss());
		imageView.setOnTouchListener(new ZoomInZoomOut());
		builder.addContentView(imageView, new RelativeLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		builder.show();
	}

	private void performanceLog(String msg) {
		Log.d("Performance", msg + ": " + (System.currentTimeMillis() - time));
		time = System.currentTimeMillis();
	}

	private static class ImageMetadata {
		private final File file;
		private final long lastModified;

		private ImageMetadata(File file, long lastModified) {
			this.file = file;
			this.lastModified = lastModified;
		}

		public File getFile() {
			return file;
		}

		public long getLastModified() {
			return lastModified;
		}
	}
}