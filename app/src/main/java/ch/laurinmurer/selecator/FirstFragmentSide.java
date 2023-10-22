package ch.laurinmurer.selecator;

import android.util.Log;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.widget.AppCompatImageView;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class FirstFragmentSide {

	/**
	 * @noinspection unused, FieldCanBeLocal - useful for debugging
	 */
	private final String side;
	private static final Set<String> SUPPORTED_FILE_SUFFIXES = Set.of("bmp", "gif", "jpg", "jpeg", "png", "webp", "heic", "heif");
	private final TextView pathLabel;
	private final View.OnTouchListener swipeListener;
	private final Runnable afterPathSet;
	private final AtomicBoolean canFilesNowBeLoaded;
	private final ExecutorService imageLoaderExecutor = Executors.newSingleThreadExecutor();
	private final SelecatorRecyclerViewAdapter recyclerViewAdapter;
	private final AtomicReference<Path> path;

	public FirstFragmentSide(String side, TextView pathLabel, View.OnTouchListener swipeListener, Runnable afterPathSet, AtomicBoolean canFilesNowBeLoaded, AtomicReference<Path> pathReferenceHolder, SelecatorRecyclerViewAdapter recyclerViewAdapter) {
		this.side = side;
		this.pathLabel = pathLabel;
		this.swipeListener = swipeListener;
		this.afterPathSet = afterPathSet;
		this.canFilesNowBeLoaded = canFilesNowBeLoaded;
		this.path = pathReferenceHolder;
		this.recyclerViewAdapter = recyclerViewAdapter;
	}

	public boolean hasValidDirectorySelected() {
		return getPath() != null && getPath().toFile().isDirectory();
	}

	public void onDestroyView() {
		imageLoaderExecutor.shutdown();
	}

	public SelecatorRecyclerViewAdapter.Data getImageDataForView(AppCompatImageView imageView) {
		return recyclerViewAdapter.getCurrentBinding(imageView);
	}

	public Instant getLastModifiedForImage(AppCompatImageView imageView) {
		return Instant.ofEpochMilli(getImageDataForView(imageView).lastModified());
	}

	public Path getPath() {
		return path.get();
	}

	public void setPathVariables(File newPath) {
		if (!newPath.toPath().equals(path.get())) {
			pathLabel.setText(newPath.getName());
			path.set(newPath.toPath());
			afterPathSet.run();
			if (canFilesNowBeLoaded.get()) {
				loadFilesInNewThread();
			}
		}
	}

	public void loadFilesInNewThread() {
		Path path = this.path.get();
		if (path != null && path.toFile().isDirectory()) {
			imageLoaderExecutor.submit(() -> {
				try {
					File[] filesInPath = listImagesOnDisk();
					removeImagesNoMoreOnDisk(filesInPath);
					loadImages(filesInPath);
				} catch (RuntimeException e) {
					Log.e("Exception", e.getLocalizedMessage(), e);
					throw e;
				}
			});
		}
	}

	private File[] listImagesOnDisk() {
		File[] filesInPath = path.get().toFile().listFiles();
		if (filesInPath == null) {
			filesInPath = new File[0];
		}
		return filesInPath;
	}

	private void removeImagesNoMoreOnDisk(File[] filesInPath) {
		Set<String> filesOnDisk = Arrays.stream(filesInPath)
				.map(File::getName)
				.collect(Collectors.toSet());
		for (int index = recyclerViewAdapter.getItemCount() - 1; index >= 0; index--) {
			SelecatorRecyclerViewAdapter.Data data = recyclerViewAdapter.getData(index);
			if (!filesOnDisk.contains(data.imageFileName())) {
				recyclerViewAdapter.removeData(data);
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

	public SelecatorRecyclerViewAdapter.Data loadImage(View.OnTouchListener swipeListener, File anImage) {
		SelecatorRecyclerViewAdapter.Data data = new SelecatorRecyclerViewAdapter.Data(anImage, swipeListener);
		recyclerViewAdapter.addData(data);
		return data;
	}

	public void removeImage(SelecatorRecyclerViewAdapter.Data anImage) {
		recyclerViewAdapter.removeData(anImage);
	}
}