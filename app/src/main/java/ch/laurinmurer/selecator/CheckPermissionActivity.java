package ch.laurinmurer.selecator;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import ch.laurinmurer.selecator.databinding.ActivityCheckPermissionBinding;

public class CheckPermissionActivity extends AppCompatActivity {

	private ActivityResultLauncher<Intent> permissionRequestedResultLauncher;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (shouldNavigateToMainActivity()) {
			navigateToMainActivity();
		} else {
			ch.laurinmurer.selecator.databinding.ActivityCheckPermissionBinding binding = ActivityCheckPermissionBinding.inflate(getLayoutInflater());
			permissionRequestedResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> checkCorrectActivity());
			binding.openPermissonButton.setOnClickListener(v ->
					permissionRequestedResultLauncher.launch(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
			);
			setContentView(binding.getRoot());
		}
	}

	private void checkCorrectActivity() {
		if (shouldNavigateToMainActivity()) {
			navigateToMainActivity();
		}
	}

	private static boolean shouldNavigateToMainActivity() {
		return Environment.isExternalStorageManager();
	}

	private void navigateToMainActivity() {
		startActivity(new Intent(this, MainActivity.class));
		finish();
	}
}