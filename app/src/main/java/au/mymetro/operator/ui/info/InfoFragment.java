package au.mymetro.operator.ui.info;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import au.mymetro.operator.R;
import au.mymetro.operator.databinding.FragmentInfoBinding;

// import dev.doubledot.doki.ui.DokiActivity;

public class InfoFragment extends Fragment {

    private FragmentInfoBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(false);

        binding = FragmentInfoBinding.inflate(inflater, container, false);

        updateAboutText();

        return binding.getRoot();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.info, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.info) {
            //DokiActivity.Companion.start(requireContext());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void updateAboutText() {
        String versionString = "";
        int versionCode = 0;
        try {
            PackageInfo info = requireActivity().getPackageManager().getPackageInfo(requireActivity().getPackageName(), 0);
            versionString = info.versionName;
            versionCode = info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        StringBuilder builder = new StringBuilder();
        // Version info
        builder.append("v")
                .append(versionString)
                .append(" (")
                .append(versionCode)
                .append(")\n\n");

        // Majority of content from string resource
        builder.append(getString(R.string.about_text));
        builder.append("\n\n");

        binding.aboutText.setText(builder.toString());
    }
}