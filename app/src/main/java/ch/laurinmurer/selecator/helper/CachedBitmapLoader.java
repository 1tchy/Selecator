package ch.laurinmurer.selecator.helper;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.nio.file.Path;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static android.content.Context.ACTIVITY_SERVICE;

public class CachedBitmapLoader {
	private static final int MIN_FREE_HEAP_SPACE_MB = 10;
	private final AtomicReference<Path> basePath;
	private final int maxWidth;
	private final WeakHashMap<String, Bitmap> cache = new WeakHashMap<>();
	private final ExecutorService cacheLoadExecutor;
	private final ActivityManager activityManager;

	public CachedBitmapLoader(AtomicReference<Path> basePath, int maxWidth, Context context) {
		this.basePath = basePath;
		this.maxWidth = maxWidth;
		this.activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
		this.cacheLoadExecutor = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable);
			thread.setDaemon(true);
			thread.setName(CachedBitmapLoader.class.getName() + "-for-" + this.basePath.get().getFileName());
			thread.setPriority(Thread.MIN_PRIORITY);
			return thread;
		});
	}

	public Bitmap load(String filename) {
		return cache.computeIfAbsent(filename, f -> BitmapLoader.fromFile(basePath.get().resolve(filename).toFile(), maxWidth));
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
