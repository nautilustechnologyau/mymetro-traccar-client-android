package au.mymetro.operator.ui.notifications;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.text.DateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;

import au.mymetro.operator.R;
import au.mymetro.operator.databinding.FragmentNotificationsBinding;

public class NotificationsFragment extends Fragment {
    private static final int LIMIT = 20;
    private static final LinkedList<String> messages = new LinkedList<>();
    private static final HashSet<ArrayAdapter<String>> adapters = new HashSet<>();

    private FragmentNotificationsBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);

        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, android.R.id.text1, messages);
        ListView listView = binding.list;
        listView.setAdapter(adapter);
        adapters.add(adapter);
        return root;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.status, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.clear) {
            clearMessages();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static void notifyAdapters() {
        for (ArrayAdapter<String> adapter : adapters) {
            adapter.notifyDataSetChanged();
        }
    }

    public static void addMessage(String originalMessage) {
        String message = originalMessage;
        DateFormat format = DateFormat.getTimeInstance(DateFormat.MEDIUM);
        message = format.format(new Date()) + " - " + message;
        messages.add(message);
        while (messages.size() > LIMIT) {
            messages.removeFirst();
        }
        notifyAdapters();
    }

    public static void clearMessages() {
        messages.clear();
        notifyAdapters();
    }
}