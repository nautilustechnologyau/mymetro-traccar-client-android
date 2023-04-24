package au.mymetro.operator.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import au.mymetro.operator.MainFragment;
import au.mymetro.operator.R;
import au.mymetro.operator.databinding.FragmentSettingsBinding;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;

    /*@Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }*/

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // return super.onCreateView(inflater, container, savedInstanceState);
        //SettingsViewModel dashboardViewModel =
        //        new ViewModelProvider(this).get(SettingsViewModel.class);

        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        //final TextView textView = binding.textDashboard;
        //dashboardViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.preferences_placeholder, new MainFragment())
                .commit();
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}