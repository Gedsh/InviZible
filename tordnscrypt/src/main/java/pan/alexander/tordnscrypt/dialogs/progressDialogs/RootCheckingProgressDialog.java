package pan.alexander.tordnscrypt.dialogs.progressDialogs;

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

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.Context;
import android.support.v7.app.AlertDialog;
import android.widget.ProgressBar;

import pan.alexander.tordnscrypt.R;

public class RootCheckingProgressDialog {
    public static AlertDialog.Builder getBuilder(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CustomDialogTheme);
        builder.setTitle(R.string.root);
        builder.setMessage(R.string.root_available);
        builder.setIcon(R.drawable.ic_visibility_off_black_24dp);

        ProgressBar progressBar = new ProgressBar(context,null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setBackgroundResource(R.drawable.background_10dp_padding);
        progressBar.setIndeterminate(true);
        builder.setView(progressBar);
        builder.setCancelable(false);
        return builder;
    }
}
