package ch.laurinmurer.selecator;

import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.navigation.fragment.NavHostFragment;
import ch.laurinmurer.selecator.databinding.FragmentSplashBinding;
import ch.laurinmurer.selecator.helper.FragmentWithoutActionBar;

public class SplashFragment extends FragmentWithoutActionBar {

	private FragmentSplashBinding binding;

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		binding = FragmentSplashBinding.inflate(inflater, container, false);
		binding.getRoot().postDelayed(this::checkPermissionGranted, 700);
		return binding.getRoot();
	}

	private void checkPermissionGranted() {
		if (Environment.isExternalStorageManager()) {
			NavHostFragment.findNavController(this).navigate(R.id.action_splashFragment_to_FirstFragment);
		} else {
			NavHostFragment.findNavController(this).navigate(R.id.action_splashFragment_to_CheckPermissionFragment);
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		binding = null;
	}
}