package au.mymetro.operator.ui.settings;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.google.firebase.analytics.FirebaseAnalytics;

import au.mymetro.operator.ApiKeyCheckerTask;
import au.mymetro.operator.MainFragment;
import au.mymetro.operator.R;
import au.mymetro.operator.databinding.FragmentSettingsBinding;
import au.mymetro.operator.oba.io.elements.ObaRegion;
import au.mymetro.operator.oba.ui.NavHelp;
import au.mymetro.operator.oba.util.UIUtils;
import au.mymetro.operator.ui.home.HomeViewModel;

public class SettingsFragment extends Fragment implements ApiKeyCheckerTask.ApiKeyCheckerTaskListener {

    private FragmentSettingsBinding binding;
    //private SharedPreferences sharedPreferences;
    //private AlarmManager alarmManager;
    //private PendingIntent alarmIntent;
    //private Boolean requestingPermissions = false;
    //private Boolean mAutoSelectInitialValue = true;
    //private FirebaseAnalytics mFirebaseAnalytics;
    private MainFragment mPreferenceFragment;
    private HomeViewModel homeViewModel;

    /*@Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }*/

    /*@Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }*/

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // return super.onCreateView(inflater, container, savedInstanceState);
        //SettingsViewModel dashboardViewModel =
        //        new ViewModelProvider(this).get(SettingsViewModel.class);


        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        homeViewModel.getRegionChanged().observe(requireActivity(), this::onRegionChanged);
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        //final TextView textView = binding.textDashboard;
        //dashboardViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        mPreferenceFragment = new MainFragment();
        //mPreferenceFragment.setRegionPreferenceChangeListener(this);
        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.preferences_placeholder, mPreferenceFragment)
                .commit();
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public void onRegionChanged(ObaRegion region) {
        homeViewModel.setStop(null);
        homeViewModel.setArrivalInfo(null);
        homeViewModel.setTripId(null);
        UIUtils.showObaApiKeyInputDialog(requireActivity(), this);
    }

    @Override
    public void onApiCheckerTaskComplete(@Nullable Boolean valid) {
        if (Boolean.TRUE != valid) {
            UIUtils.popupSnackbarForApiKey(requireActivity(), this);
            // UIUtils.showObaApiKeyInputDialog(activity, this)
            NavHelp.goHome(requireActivity());
        }
    }
}