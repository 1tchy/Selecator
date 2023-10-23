package ch.laurinmurer.selecator.helper;

import androidx.annotation.Nullable;

import java.util.Set;

public class FileSuffixHelper {
	private static final Set<String> SUPPORTED_IMAGE_FILE_SUFFIXES = Set.of("bmp", "gif", "jpg", "jpeg", "png", "webp", "heic", "heif");
	private static final Set<String> SUPPORTED_VIDEO_FILE_SUFFIXES = Set.of("3gp", "mkv", "mp4", "ts", "webm");

	public static boolean hasASupportedSuffix(String fileName) {
		String suffix = getSuffix(fileName);
		return SUPPORTED_IMAGE_FILE_SUFFIXES.contains(suffix) || SUPPORTED_VIDEO_FILE_SUFFIXES.contains(suffix);
	}

	public static boolean hasAVideoSuffix(String fileName) {
		return SUPPORTED_VIDEO_FILE_SUFFIXES.contains(getSuffix(fileName));
	}

	@Nullable
	private static String getSuffix(String fileName) {
		int lastIndexOf = fileName.lastIndexOf(".");
		if (lastIndexOf > 0) {
			String suffix = fileName.substring(lastIndexOf + 1);
			return suffix.toLowerCase();
		}
		return null;
	}
}
