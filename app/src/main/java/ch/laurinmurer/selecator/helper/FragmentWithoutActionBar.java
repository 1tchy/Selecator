package ch.laurinmurer.selecator.helper;

import android.util.Log;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

public class FragmentWithoutActionBar extends Fragment {

	@Override
	public void onStart() {
		super.onStart();
		hideActionBar();
	}

	@Override
	public void onStop() {
		super.onStop();
		showActionBar();
	}

	@Override
	public void onResume() {
		super.onResume();
		hideActionBar();
	}

	protected void showActionBar() {
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.show();
		}
	}

	protected void hideActionBar() {
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			Log.d("Debug", "hiding actionbar");
			actionBar.hide();
		}
	}

	@Nullable
	private ActionBar getSupportActionBar() {
		if (getActivity() instanceof AppCompatActivity) {
			AppCompatActivity activity = (AppCompatActivity) getActivity();
			return activity.getSupportActionBar();
		}
		return null;
	}
}
