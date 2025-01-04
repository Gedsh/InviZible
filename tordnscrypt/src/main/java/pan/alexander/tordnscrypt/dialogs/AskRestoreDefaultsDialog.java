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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.dialogs;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import javax.inject.Inject;

import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragmentViewModel;
import pan.alexander.tordnscrypt.utils.enums.ModuleName;

public class AskRestoreDefaultsDialog extends ExtendedDialogFragment {

    public static String MODULE_NAME_ARG = "pan.alexander.tordnscrypt.MODULE_NAME_ARG";

    private ModuleName module;

    @Inject
    public ViewModelProvider.Factory viewModelFactory;
    private TopFragmentViewModel topFragmentViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);

        topFragmentViewModel = new ViewModelProvider(
                requireParentFragment(),
                viewModelFactory
        ).get(TopFragmentViewModel.class);
    }

    @Override
    public AlertDialog.Builder assignBuilder() {
        if (getActivity() == null) {
            return null;
        }

        Bundle arguments = getArguments();
        if (arguments != null) {
            module = (ModuleName) arguments.getSerializable(MODULE_NAME_ARG);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(
                        String.format(
                                getString(R.string.ask_reset_settings_text),
                                module.getModuleName(),
                                module.getModuleName()
                        )
                )
                .setTitle(getString(R.string.reset_settings_title))
                .setPositiveButton(R.string.ask_reset_settings_btn, (dialog, which) -> {
                    if (topFragmentViewModel != null && module != null) {
                        topFragmentViewModel.resetModuleSettings(module);
                    }

                })
                .setNegativeButton(R.string.cancel, (dialog, id) -> dismiss());

        return builder;
    }

    public static DialogFragment getInstance(ModuleName name) {
        DialogFragment dialog = new AskRestoreDefaultsDialog();
        Bundle args = new Bundle();
        args.putSerializable(MODULE_NAME_ARG, name);
        dialog.setArguments(args);
        return dialog;
    }
}
