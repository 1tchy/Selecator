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
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SortedList;
import androidx.recyclerview.widget.SortedListAdapterCallback;
import ch.laurinmurer.selecator.helper.BitmapLoader;
import com.github.chrisbanes.photoview.PhotoView;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class SelecatorRecyclerViewAdapter extends RecyclerView.Adapter<SelecatorRecyclerViewAdapter.SelecatorViewHolder> {

	private final SortedList<Data> dataSet = new SortedList<>(Data.class, Data.createSorter(this));
	private final Map<AppCompatImageView, Data> currentImageBindings = Collections.synchronizedMap(new WeakHashMap<>());

	private final Context context;
	private final RecyclerView recyclerView;
	private final View.OnTouchListener swipeListener;
	private final Consumer<Runnable> onUiThreadRunner;
	private final AtomicReference<Path> path;

	public SelecatorRecyclerViewAdapter(Context context, RecyclerView recyclerView, View.OnTouchListener swipeListener, Consumer<Runnable> onUiThreadRunner, AtomicReference<Path> path) {
		this.context = context;
		this.recyclerView = recyclerView;
		this.swipeListener = swipeListener;
		this.onUiThreadRunner = onUiThreadRunner;
		this.path = path;
	}

	@NonNull
	@Override
	public SelecatorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		AppCompatImageView imageView = new AppCompatImageView(context);
		imageView.setAdjustViewBounds(true);
		imageView.setScaleType(ImageView.ScaleType.FIT_XY);
		imageView.setBackgroundColor(Color.RED);
		imageView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return new SelecatorViewHolder(imageView);
	}

	@Override
	@SuppressLint("ClickableViewAccessibility")
	public void onBindViewHolder(@NonNull SelecatorViewHolder holder, int position) {
		Data data = dataSet.get(position);
		AppCompatImageView imageView = holder.getImageView();
		currentImageBindings.put(imageView, data);
		imageView.setImageBitmap(BitmapLoader.fromFile(path.get().resolve(data.imageFileName()).toFile(), getFilesMaxWidth()));
		imageView.setOnClickListener(v -> showImageFullscreen(Uri.fromFile(path.get().resolve(data.imageFileName()).toFile())));
		imageView.setOnTouchListener(swipeListener);
	}

	@Override
	public int getItemCount() {
		return dataSet.size();
	}

	public Data getCurrentBinding(AppCompatImageView imageView) {
		return currentImageBindings.get(imageView);
	}

	public void addData(Data data) {
		onUiThreadRunner.accept(() -> dataSet.add(data));
	}

	public Data getData(int index) {
		return dataSet.get(index);
	}

	public void removeData(Data data) {
		onUiThreadRunner.accept(() -> dataSet.remove(data));
	}

	public void scrollTo(Data data) {
		onUiThreadRunner.accept(() -> {
			int index = dataSet.indexOf(data);
			if (index >= 0) {
				recyclerView.scrollToPosition(index);
			}
		});
	}

	private int getFilesMaxWidth() {
		int sleepTime = 1;
		do {
			int width = recyclerView.getWidth();
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

	public static class SelecatorViewHolder extends RecyclerView.ViewHolder {

		private final AppCompatImageView imageView;

		public SelecatorViewHolder(@NonNull AppCompatImageView imageView) {
			super(imageView);
			this.imageView = imageView;
		}

		public AppCompatImageView getImageView() {
			return imageView;
		}
	}

	public record Data(String imageFileName, long lastModified) {
		public Data(File imageFile) {
			this(imageFile.getName(), imageFile.lastModified());
		}

		public Instant lastModifiedInstant() {
			return Instant.ofEpochMilli(lastModified());
		}

		public static SortedListAdapterCallback<Data> createSorter(SelecatorRecyclerViewAdapter adapter) {
			return new SortedListAdapterCallback<>(adapter) {
				@Override
				public int compare(Data o1, Data o2) {
					return -Long.compare(o1.lastModified(), o2.lastModified());
				}

				@Override
				public boolean areContentsTheSame(Data oldItem, Data newItem) {
					return oldItem.imageFileName().equals(newItem.imageFileName());
				}

				@Override
				public boolean areItemsTheSame(Data item1, Data item2) {
					return item1.imageFileName().equals(item2.imageFileName());
				}
			};
		}
	}
}
