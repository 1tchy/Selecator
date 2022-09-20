package ch.laurinmurer.selecator;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.appcompat.widget.AppCompatImageView;
import ch.laurinmurer.selecator.helper.BitmapLoader;
import com.github.chrisbanes.photoview.PhotoView;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class FirstFragmentSide {

	private static final Set<String> SUPPORTED_FILE_SUFFIXES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"bmp", "gif", "jpg", "jpeg", "png", "webp", "heic", "heif"
	)));
	private final TextView pathLabel;
	private final ViewGroup imagesViewGroup;
	private final List<AppCompatImageView> synchronousImagesViewGroupList = Collections.synchronizedList(new ArrayList<>());
	private final Map<AppCompatImageView, ImageMetadata> imageMetadata = new ConcurrentHashMap<>();
	private final View.OnTouchListener swipeListener;
	private final Runnable afterPathSet;
	private final Context context;
	private final Consumer<Runnable> onUiThreadRunner;
	private final AtomicBoolean canFilesNowBeLoaded;
	private final ExecutorService imageLoaderExecutor = Executors.newSingleThreadExecutor();
	private Path path;

	public FirstFragmentSide(TextView pathLabel, ViewGroup imagesViewGroup, View.OnTouchListener swipeListener, Runnable afterPathSet, Context context, Consumer<Runnable> onUiThreadRunner, AtomicBoolean canFilesNowBeLoaded) {
		this.pathLabel = pathLabel;
		this.imagesViewGroup = imagesViewGroup;
		this.swipeListener = swipeListener;
		this.afterPathSet = afterPathSet;
		this.context = context;
		this.onUiThreadRunner = onUiThreadRunner;
		this.canFilesNowBeLoaded = canFilesNowBeLoaded;
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
		if (!newPath.toPath().equals(path)) {
			pathLabel.setText(newPath.getName());
			path = newPath.toPath();
			afterPathSet.run();
			if (canFilesNowBeLoaded.get()) {
				loadFilesInNewThread();
			}
		}
	}

	public void loadFilesInNewThread() {
		if (path != null && path.toFile().isDirectory()) {
			imageLoaderExecutor.submit(() -> logExceptions(() -> {
				File[] filesInPath = listImagesOnDisk();
				removeImagesNoMoreOnDisk(filesInPath);
				loadImages(filesInPath);
			}));
		}
	}

	private File[] listImagesOnDisk() {
		File[] filesInPath = path.toFile().listFiles();
		if (filesInPath == null) {
			filesInPath = new File[0];
		}
		return filesInPath;
	}

	private void removeImagesNoMoreOnDisk(File[] filesInPath) {
		Set<String> filesOnDisk = Arrays.stream(filesInPath)
				.map(File::getAbsolutePath)
				.collect(Collectors.toSet());
		for (int index = synchronousImagesViewGroupList.size() - 1; index >= 0; index--) {
			AppCompatImageView imageView = synchronousImagesViewGroupList.get(index);
			if (!filesOnDisk.contains(requireNonNull(imageMetadata.get(imageView)).getFile().getAbsolutePath())) {
				synchronousImagesViewGroupList.remove(index);
				onUiThreadRunner.accept(() -> imagesViewGroup.removeView(imageView));
			}
		}
	}

	private void loadImages(File[] filesInPath) {
		Arrays.stream(filesInPath)
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
			newImage.setOnClickListener(v -> showImageFullscreen(Uri.fromFile(anImage)));
			newImage.setOnTouchListener(swipeListener);
			imageMetadata.put(newImage, new ImageMetadata(anImage, anImage.lastModified()));
			onUiThreadRunner.accept(() -> addImageToView(newImage));
		}
	}

	private void addImageToView(AppCompatImageView newImage) {
		int index = calculateNewIndex(newImage);
		synchronousImagesViewGroupList.add(index, newImage);
		imagesViewGroup.addView(newImage, index);
	}

	private boolean alreadyContainsImage(File image) {
		for (AppCompatImageView aChild : new ArrayList<>(synchronousImagesViewGroupList)) {
			if (aChild.getHeight() > 0 && Objects.requireNonNull(imageMetadata.get(aChild)).getFile().getAbsolutePath().equals(image.getAbsolutePath())) {
				return true;
			}
		}
		return false;
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

	private void showImageFullscreen(Uri image) {
		Dialog builder = new Dialog(context, android.R.style.Theme_Light);
		builder.requestWindowFeature(Window.FEATURE_NO_TITLE);
		builder.getWindow().setBackgroundDrawable(new ColorDrawable(Color.argb(200, 0, 0, 0)));
		builder.setOnDismissListener(dialogInterface -> {
		});

		PhotoView imageView = new PhotoView(context);
		imageView.setImageURI(image);
		imageView.setOnClickListener(v -> builder.dismiss());
		builder.addContentView(imageView, new RelativeLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		builder.show();
	}

	private int calculateNewIndex(AppCompatImageView newImage) {
		long lastModifiedOfNewImage = Objects.requireNonNull(imageMetadata.get(newImage)).getLastModified();
		for (int index = 0; index < synchronousImagesViewGroupList.size(); index++) {
			AppCompatImageView childAt = synchronousImagesViewGroupList.get(index);
			long lastModifiedOfChildAt = Objects.requireNonNull(imageMetadata.get(childAt)).getLastModified();
			if (lastModifiedOfChildAt < lastModifiedOfNewImage) {
				return index;
			}
		}
		return synchronousImagesViewGroupList.size();
	}

	private static void logExceptions(Runnable runnable) {
		try {
			runnable.run();
		} catch (RuntimeException e) {
			Log.e("Exception", e.getLocalizedMessage(), e);
			throw e;
		}
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