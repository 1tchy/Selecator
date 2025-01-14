package ch.laurinmurer.selecator.helper;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import androidx.core.content.ContextCompat;

import ch.laurinmurer.selecator.R;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static android.content.Context.ACTIVITY_SERVICE;
import static java.util.Objects.requireNonNull;

public class CachedBitmapLoader {
	private static final int MIN_FREE_HEAP_SPACE_MB = 10;
	private final AtomicReference<Path> basePath;
	private final int maxWidth;
	private final WeakHashMap<String, Optional<Bitmap>> cache = new WeakHashMap<>();
	private final ExecutorService cacheLoadExecutor;
	private final ActivityManager activityManager;
	private final Context context;

	public CachedBitmapLoader(AtomicReference<Path> basePath, int maxWidth, Context context) {
		this.basePath = basePath;
		this.maxWidth = maxWidth;
		this.context = context;
		this.activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
		this.cacheLoadExecutor = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable);
			thread.setDaemon(true);
			thread.setName(CachedBitmapLoader.class.getName() + "-for-" + this.basePath.get().getFileName());
			thread.setPriority(Thread.MIN_PRIORITY);
			return thread;
		});
	}

	public Optional<Bitmap> load(String filename) {
		return cache.computeIfAbsent(filename, f -> {
			if (FileSuffixHelper.hasAVideoSuffix(f)) {
				Bitmap bitmap = retrieveVideoFrameFromVideo(basePath.get().resolve(f).toString());
				return Optional.of(overlayDrawable(bitmap, requireNonNull(ContextCompat.getDrawable(context, R.drawable.play))));
			} else {
				return BitmapLoader.fromFile(basePath.get().resolve(f).toFile(), maxWidth);
			}
		});
	}

	public static Bitmap retrieveVideoFrameFromVideo(String videoPath) {
		try (MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever()) {
			mediaMetadataRetriever.setDataSource(videoPath);
			return mediaMetadataRetriever.getFrameAtTime();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static Bitmap overlayDrawable(Bitmap bitmap, Drawable squaredDrawable) {
		int drawableSize = Math.min(bitmap.getWidth(), bitmap.getHeight()) * 3 / 4;
		int left = (bitmap.getWidth() - drawableSize) / 2;
		int top = (bitmap.getHeight() - drawableSize) / 2;
		squaredDrawable.setBounds(left, top, drawableSize + left, drawableSize + top);

		Bitmap combinedBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
		Canvas canvas = new Canvas(combinedBitmap);

		canvas.drawBitmap(bitmap, 0, 0, null);
		squaredDrawable.draw(canvas);

		return combinedBitmap;
	}

	public void suggestCache(String filename) {
		cacheLoadExecutor.submit(() -> {
			if (isSystemLowOnMemory()) {
				Log.d(CachedBitmapLoader.class.getName(), "System is low on memory, not caching " + filename);
			} else if (getFreeHeapSpaceMB() <= MIN_FREE_HEAP_SPACE_MB) {
				Log.d(CachedBitmapLoader.class.getName(), "Heap is low on memory, not caching " + filename);
			} else {
				load(filename);
			}
		});
	}

	private boolean isSystemLowOnMemory() {
		ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
		activityManager.getMemoryInfo(memoryInfo);
		return memoryInfo.lowMemory;
	}

	private static long getFreeHeapSpaceMB() {
		final Runtime runtime = Runtime.getRuntime();
		final long usedMemInMB = runtime.totalMemory() - runtime.freeMemory();
		final long maxHeapSizeInMB = runtime.maxMemory();
		return (maxHeapSizeInMB - usedMemInMB) / 1024 / 1024;
	}

	public void suggestRemoveFromCache(String filename) {
		cache.remove(filename);
	}
}
