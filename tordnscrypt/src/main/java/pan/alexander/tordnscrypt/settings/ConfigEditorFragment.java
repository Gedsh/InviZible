package pan.alexander.tordnscrypt.settings;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import java.util.List;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.DialogSaveConfigChanges;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;
import pan.alexander.tordnscrypt.utils.file_operations.OnTextFileOperationsCompleteListener;

import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.readTextFile;

public class ConfigEditorFragment extends Fragment implements OnTextFileOperationsCompleteListener {

    private String filePath;
    private String fileName;
    private EditText etConfigEditor;
    private String savedText;
    private String moduleName;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (this.getArguments() != null) {
            fileName = this.getArguments().getString("fileName");
        }

        if (getActivity() == null || fileName == null || fileName.isEmpty()) {
            return;
        }

        PathVars pathVars = PathVars.getInstance(getActivity());
        String appDataDir = pathVars.getAppDataDir();

        switch (fileName) {
            case "dnscrypt-proxy.toml":
                filePath = appDataDir + "/app_data/dnscrypt-proxy/dnscrypt-proxy.toml";
                moduleName = "DNSCrypt";
                break;
            case "tor.conf":
                filePath = appDataDir + "/app_data/tor/tor.conf";
                moduleName = "Tor";
                break;
            case "i2pd.conf":
                filePath = appDataDir + "/app_data/i2pd/i2pd.conf";
                moduleName = "ITPD";
                break;
            case "tunnels.conf":
                filePath = appDataDir + "/app_data/i2pd/tunnels.conf";
                moduleName = "ITPD";
                break;
        }

        FileOperations.setOnFileOperationCompleteListener(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_config_editor, container, false);

        etConfigEditor = view.findViewById(R.id.etConfigEditor);
        etConfigEditor.setBackgroundColor(Color.TRANSPARENT);

        if (getActivity() != null && fileName != null) {
            getActivity().setTitle(fileName);
        }

        FileOperations.readTextFile(getActivity(), filePath, fileName);

        return view;
    }


    @Override
    public void onStop() {

        super.onStop();

        String input = etConfigEditor.getText().toString();

        if (input.isEmpty()) {
            return;
        }

        if (!input.equals(savedText) && getFragmentManager() != null) {
            DialogFragment dialogFragment = DialogSaveConfigChanges.newInstance();

            Bundle bundle = new Bundle();
            bundle.putString("moduleName", moduleName);
            bundle.putString("filePath", filePath);
            bundle.putString("fileText", input);

            dialogFragment.setArguments(bundle);

            dialogFragment.show(getFragmentManager(), "DialogSaveConfigChanges");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        FileOperations.deleteOnFileOperationCompleteListener();
    }

    @Override
    public void OnFileOperationComplete(FileOperationsVariants currentFileOperation, boolean fileOperationResult, String path, String tag, List<String> lines) {
        if (getActivity() == null) {
            return;
        }

        if (fileOperationResult && currentFileOperation == readTextFile) {

            final StringBuilder stringBuilder = new StringBuilder();

            for (String line : lines) {
                stringBuilder.append(line).append(System.lineSeparator());
            }

            savedText = stringBuilder.toString();

            if (getActivity() != null && etConfigEditor != null) {
                getActivity().runOnUiThread(() -> {
                    if (getActivity() != null) {
                        etConfigEditor.setText(stringBuilder, TextView.BufferType.EDITABLE);
                    }
                });
            }
        }
    }

    static void openEditorFragment(FragmentManager fragmentManager, String fileName) {
        Bundle bundle = new Bundle();
        bundle.putString("fileName", fileName);
        ConfigEditorFragment configEditorFragment = new ConfigEditorFragment();
        configEditorFragment.setArguments(bundle);
        if (fragmentManager != null) {
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            fragmentTransaction.replace(android.R.id.content, configEditorFragment);
            fragmentTransaction.addToBackStack("configEditorFragmentTag");
            fragmentTransaction.commit();
        }
    }
}
