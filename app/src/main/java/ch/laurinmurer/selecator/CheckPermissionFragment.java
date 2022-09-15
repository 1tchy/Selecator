package ch.laurinmurer.selecator;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import ch.laurinmurer.selecator.databinding.FragmentCheckPermissionBinding;

public class CheckPermissionFragment extends Fragment {

	private FragmentCheckPermissionBinding binding;
	private ActivityResultLauncher<Intent> permissionRequestedResultLauncher;

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		binding = FragmentCheckPermissionBinding.inflate(inflater, container, false);
		permissionRequestedResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> checkPermissionGranted());
		binding.openPermissonButton.setOnClickListener(v ->
				permissionRequestedResultLauncher.launch(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
		);
		checkPermissionGranted();
		return binding.getRoot();

	}

	private void checkPermissionGranted() {
		if (Environment.isExternalStorageManager()) {
			NavHostFragment.findNavController(CheckPermissionFragment.this).navigate(R.id.action_SecondFragment_to_FirstFragment);
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		binding = null;
		permissionRequestedResultLauncher = null;
	}
}