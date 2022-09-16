package ch.laurinmurer.selecator;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.appcompat.widget.AppCompatImageView;
import ch.laurinmurer.selecator.helper.BitmapLoader;
import ch.laurinmurer.selecator.helper.ZoomInZoomOut;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public class FirstFragmentSide {

	private static final Set<String> SUPPORTED_FILE_SUFFIXES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"bmp", "gif", "jpg", "jpeg", "png", "webp", "heic", "heif"
	)));
	private final TextView pathLabel;
	private final ViewGroup imagesViewGroup;
	private final View.OnTouchListener swipeListener;
	private final Runnable afterPathSet;
	private final Context context;
	private final Map<AppCompatImageView, ImageMetadata> imageMetadata = new ConcurrentHashMap<>();
	private final Consumer<Runnable> onUiThreadRunner;
	private final ExecutorService imageLoaderExecutor = Executors.newSingleThreadExecutor();
	private Path path;

	public FirstFragmentSide(TextView pathLabel, ViewGroup imagesViewGroup, View.OnTouchListener swipeListener, Runnable afterPathSet, Context context, Consumer<Runnable> onUiThreadRunner) {
		this.pathLabel = pathLabel;
		this.imagesViewGroup = imagesViewGroup;
		this.swipeListener = swipeListener;
		this.afterPathSet = afterPathSet;
		this.context = context;
		this.onUiThreadRunner = onUiThreadRunner;
	}

	public boolean hasValidDirectorySelected() {
		return getPath() != null && getPath().toFile().isDirectory();
	}

	public void onDestroyView() {
		imageLoaderExecutor.shutdown();
	}

	public File getFileForImage(AppCompatImageView imageView) {
		return requireNonNull(imageMetadata.get(imageView)).getFile();
	}

	public Path getPath() {
		return path;
	}

	public void setPathVariables(File newPath) {
		pathLabel.setText(newPath.getName());
		path = newPath.toPath();
		afterPathSet.run();
	}

	public void loadFiles() {
		if (path != null && path.toFile().isDirectory()) {
			imageLoaderExecutor.submit(() -> loadImages(path.toFile().listFiles(), swipeListener));
		}
	}

	public int getFilesMaxWidth() {
		int sleepTime = 1;
		do {
			int width = imagesViewGroup.getWidth();
			if (width > 0) {
				return width;
			} else {
				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
					return width;
				}
				sleepTime *= 2;
			}
		} while (sleepTime < 4096);//total 4s
		return 512;//pixel
	}

	private void loadImages(File[] filesInPath, View.OnTouchListener swipeListener) {
		Arrays.stream(filesInPath == null ? new File[0] : filesInPath)
				.filter(File::isFile)
				.filter(f -> !f.getName().startsWith("."))
				.filter(f -> hasASupportedSuffix(f.getName()))
				.sorted(Comparator.comparingLong(File::lastModified).reversed())
				.forEach(anImage -> loadImage(swipeListener, anImage));
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
	public void loadImage(View.OnTouchListener swipeListener, File anImage) {
		if (!alreadyContainsImage(anImage)) {
			AppCompatImageView newImage = new AppCompatImageView(context);
			newImage.setImageBitmap(BitmapLoader.fromFile(anImage, getFilesMaxWidth()));
			newImage.setAdjustViewBounds(true);
			newImage.setScaleType(ImageView.ScaleType.FIT_XY);
			newImage.setOnClickListener(v -> showImage(Uri.fromFile(anImage)));
			newImage.setOnTouchListener(swipeListener);
			imageMetadata.put(newImage, new ImageMetadata(anImage, anImage.lastModified()));
			onUiThreadRunner.accept(() -> imagesViewGroup.addView(newImage, calculateNewIndex(newImage)));
		}
	}

	private boolean alreadyContainsImage(File image) {
		for (int index = 0; index < imagesViewGroup.getChildCount(); index++) {
			AppCompatImageView childAt = (AppCompatImageView) imagesViewGroup.getChildAt(index);
			if (childAt.getHeight() > 0 && Objects.requireNonNull(imageMetadata.get(childAt)).getFile().getAbsolutePath().equals(image.getAbsolutePath())) {
				return true;
			}
		}
		return false;
	}

	private void showImage(Uri image) {
		Dialog builder = new Dialog(context, android.R.style.Theme_Light);
		builder.requestWindowFeature(Window.FEATURE_NO_TITLE);
		builder.getWindow().setBackgroundDrawable(new ColorDrawable(Color.argb(200, 0, 0, 0)));
		builder.setOnDismissListener(dialogInterface -> {
		});

		ImageView imageView = new ImageView(context);
		imageView.setImageURI(image);
		imageView.setOnClickListener(v -> builder.dismiss());
		imageView.setOnTouchListener(new ZoomInZoomOut());
		builder.addContentView(imageView, new RelativeLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		builder.show();
	}

	private int calculateNewIndex(AppCompatImageView newImage) {
		long lastModifiedOfNewImage = Objects.requireNonNull(imageMetadata.get(newImage)).getLastModified();
		for (int index = 0; index < imagesViewGroup.getChildCount(); index++) {
			AppCompatImageView childAt = (AppCompatImageView) imagesViewGroup.getChildAt(index);
			long lastModifiedOfChildAt = Objects.requireNonNull(imageMetadata.get(childAt)).getLastModified();
			if (lastModifiedOfChildAt < lastModifiedOfNewImage) {
				return index;
			}
		}
		return imagesViewGroup.getChildCount();
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