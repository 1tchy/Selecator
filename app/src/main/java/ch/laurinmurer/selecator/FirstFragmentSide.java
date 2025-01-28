package ch.laurinmurer.selecator;

import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageView;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import ch.laurinmurer.selecator.helper.FileSuffixHelper;

public class FirstFragmentSide {

	/**
	 * @noinspection unused, FieldCanBeLocal - useful for debugging
	 */
	private final String side;
	private final TextView pathLabel;
	private final Runnable afterPathSet;
	private final AtomicBoolean canFilesNowBeLoaded;
	private final ExecutorService imageLoaderExecutor = Executors.newCachedThreadPool();
	private final SelecatorRecyclerViewAdapter recyclerViewAdapter;
	private final AtomicReference<Path> path;
	private final AtomicInteger loadedImageCount = new AtomicInteger();

	public FirstFragmentSide(String side, TextView pathLabel, Runnable afterPathSet, AtomicBoolean canFilesNowBeLoaded, AtomicReference<Path> pathReferenceHolder, SelecatorRecyclerViewAdapter recyclerViewAdapter) {
		this.side = side;
		this.pathLabel = pathLabel;
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

	public Instant getTimestampForImage(AppCompatImageView imageView) {
		return Instant.ofEpochMilli(getImageDataForView(imageView).timestamp());
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
					long time1 = System.currentTimeMillis();
					File[] filesInPath = listImagesOnDisk();
					long time2 = System.currentTimeMillis();
					Log.i("Performance", "Listing files took " + (time2 - time1) + "ms");
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

	/**
	 * @noinspection SimplifyStreamApiCallChains: not supported by current API level
	 */
	private void loadImages(File[] filesInPath) {
		long time1 = System.currentTimeMillis();
		List<FileToLoad> filesToLoad = Arrays.stream(filesInPath)
				.parallel()
				.filter(File::isFile)
				.filter(f -> !f.getName().startsWith("."))
				.filter(f -> FileSuffixHelper.hasASupportedSuffix(f.getName()))
				.map(imageFile -> new FileToLoad(imageFile, imageFile.lastModified()))
				.collect(Collectors.toUnmodifiableList()).stream()
				.sorted(Comparator.comparingLong(FileToLoad::lastModified).reversed())
				.collect(Collectors.toUnmodifiableList());
		long time2 = System.currentTimeMillis();
		Log.i("Performance", "Checking and sorting files took " + (time2 - time1) + "ms");
		filesToLoad.stream()
				.map(FileToLoad::file)
				.forEach(this::loadImage);
		long time3 = System.currentTimeMillis();
		Log.i("Performance", "Loading all images took " + (time3 - time2) + "ms on thread:" + Thread.currentThread());
	}

	public SelecatorRecyclerViewAdapter.Data loadImage(File anImage) {
		SelecatorRecyclerViewAdapter.Data data = new SelecatorRecyclerViewAdapter.Data(anImage);
		recyclerViewAdapter.addData(data);
		int firstImpressionImageCountEstimate = 10;
		if (loadedImageCount.incrementAndGet() == firstImpressionImageCountEstimate) {
			Log.i("Performance", "Already loaded the first " + firstImpressionImageCountEstimate + " images");
			try {
				Thread.sleep(100);
			} catch (InterruptedException ignored) {
			}
		}
		return data;
	}

	public void removeImage(SelecatorRecyclerViewAdapter.Data anImage) {
		recyclerViewAdapter.removeData(anImage);
	}

	private record FileToLoad(File file, long lastModified) {
	}
}