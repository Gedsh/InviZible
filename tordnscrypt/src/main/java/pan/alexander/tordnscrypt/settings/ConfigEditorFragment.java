package pan.alexander.tordnscrypt.settings;

/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

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

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.DialogSaveConfigChanges;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;
import pan.alexander.tordnscrypt.utils.filemanager.OnTextFileOperationsCompleteListener;

import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.readTextFile;

import javax.inject.Inject;

public class ConfigEditorFragment extends Fragment implements OnTextFileOperationsCompleteListener,
        OnBackPressListener {

    @Inject
    public Lazy<PathVars> pathVars;

    private String filePath;
    private String fileName;
    private EditText etConfigEditor;
    private String savedText;
    private String moduleName;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);

        if (this.getArguments() != null) {
            fileName = this.getArguments().getString("fileName");
        }

        if (getActivity() == null || fileName == null || fileName.isEmpty()) {
            return;
        }

        String appDataDir = pathVars.get().getAppDataDir();

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

        FileManager.setOnFileOperationCompleteListener(this);
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

        FileManager.readTextFile(getActivity(), filePath, fileName);

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        FileManager.deleteOnFileOperationCompleteListener(this);
    }

    @Override
    public void OnFileOperationComplete(FileOperationsVariants currentFileOperation, boolean fileOperationResult, String path, String tag, List<String> lines) {
        if (getActivity() == null) {
            return;
        }

        if (fileOperationResult && currentFileOperation == readTextFile) {

            final StringBuilder stringBuilder = new StringBuilder();

            for (String line : lines) {
                stringBuilder.append(line).append("\n");
            }

            savedText = stringBuilder.toString();

            if (getActivity() != null && etConfigEditor != null) {
                getActivity().runOnUiThread(() -> {
                    if (getActivity() != null) {
                        etConfigEditor.setText(savedText, TextView.BufferType.EDITABLE);
                    }
                });
            }
        }
    }

    public static void openEditorFragment(FragmentManager fragmentManager, String fileName) {
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

    @Override
    public boolean onBackPressed() {
        return showSaveChangesDialog();
    }

    private boolean showSaveChangesDialog() {
        String input = etConfigEditor.getText().toString();

        if (input.isEmpty()) {
            return false;
        }

        if (!input.equals(savedText)) {
            DialogFragment dialogFragment = DialogSaveConfigChanges.newInstance();

            Bundle bundle = new Bundle();
            bundle.putString("moduleName", moduleName);
            bundle.putString("filePath", filePath);
            bundle.putString("fileText", input);

            dialogFragment.setArguments(bundle);

            dialogFragment.show(getChildFragmentManager(), "DialogSaveConfigChanges");

            return true;
        }

        return false;
    }
}
