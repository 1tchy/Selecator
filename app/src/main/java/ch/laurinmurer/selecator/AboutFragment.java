package ch.laurinmurer.selecator;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import ch.laurinmurer.selecator.databinding.FragmentAboutBinding;

public class AboutFragment extends Fragment {

	private FragmentAboutBinding binding;

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		binding = FragmentAboutBinding.inflate(inflater, container, false);
		requireActivity().addMenuProvider(new MenuProvider() {
			@Override
			public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
				menu.removeItem(R.id.about);
			}

			@Override
			public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
				return false; //report no menu selection handled
			}
		});
		binding.aboutText.setMovementMethod(LinkMovementMethod.getInstance());
		return binding.getRoot();
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		requireActivity().addMenuProvider(new MenuProvider() {
			@Override
			public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
				menuInflater.inflate(R.menu.menu_main, menu);
			}

			@Override
			public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
				return false; //report no menu selection handled
			}
		});
		binding = null;
	}
}