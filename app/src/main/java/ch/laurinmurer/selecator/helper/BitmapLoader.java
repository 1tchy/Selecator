package ch.laurinmurer.selecator.helper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class BitmapLoader {

	private BitmapLoader() {
	}

	public static Optional<Bitmap> fromFile(File image, int maxWidth) {
		BitmapFactory.Options bounds = loadBounds(image);
		int sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxWidth, 1);

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inSampleSize = sampleSize;
		Bitmap bitmapPhoto = BitmapFactory.decodeFile(image.getPath(), options);
		if (bitmapPhoto == null) {
			return Optional.empty();
		}

		int orientation = loadExifOrientation(image);
		Matrix matrix = new Matrix();

		Bitmap bitmap;
		if ((orientation == ExifInterface.ORIENTATION_ROTATE_180)) {
			matrix.postRotate(180);
			bitmap = Bitmap.createBitmap(bitmapPhoto, 0, 0,
					bitmapPhoto.getWidth(), bitmapPhoto.getHeight(), matrix,
					true);

		} else if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
			matrix.postRotate(90);
			bitmap = Bitmap.createBitmap(bitmapPhoto, 0, 0,
					bitmapPhoto.getWidth(), bitmapPhoto.getHeight(), matrix,
					true);

		} else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
			matrix.postRotate(270);
			bitmap = Bitmap.createBitmap(bitmapPhoto, 0, 0,
					bitmapPhoto.getWidth(), bitmapPhoto.getHeight(), matrix,
					true);

		} else {
			matrix.postRotate(0);
			bitmap = Bitmap.createBitmap(bitmapPhoto, 0, 0,
					bitmapPhoto.getWidth(), bitmapPhoto.getHeight(), null,
					true);

		}

		return Optional.of(bitmap);
	}

	private static int loadExifOrientation(File image) {
		ExifInterface exif;
		try {
			exif = new ExifInterface(image);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
	}

	private static BitmapFactory.Options loadBounds(File image) {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(image.getPath(), options);
		return options;
	}

	public static int calculateInSampleSize(int rawWidth, int rawHeight, int reqWidth, int reqHeight) {
		int inSampleSize = 1;
		final int halfHeight = rawHeight / 2;
		final int halfWidth = rawWidth / 2;

		// Calculate the largest inSampleSize value that is a power of 2 and keeps both
		// height and width larger than the requested height and width.
		while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
			inSampleSize *= 2;
		}

		return inSampleSize;
	}
}
