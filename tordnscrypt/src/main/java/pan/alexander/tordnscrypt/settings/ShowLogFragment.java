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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/


import android.app.Activity;
import android.os.Bundle;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

/**
 * A simple {@link Fragment} subclass.
 */
public class ShowLogFragment extends Fragment implements View.OnClickListener, SwipeRefreshLayout.OnRefreshListener {

    private final static int MAX_LINES_QUANTITY = 1000;
    private String file_path;
    private TextView tvLogFile;
    private SwipeRefreshLayout swipeRefreshDNSQueries;


    public ShowLogFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        if (getArguments() != null) {
            file_path = getArguments().getString("path");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_show_log, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (getActivity() == null) {
            return;
        }

        tvLogFile = getActivity().findViewById(R.id.tvLogFile);

        FloatingActionButton floatingBtnClearLog = getActivity().findViewById(R.id.floatingBtnClearLog);
        floatingBtnClearLog.setAlpha(0.8f);
        floatingBtnClearLog.setOnClickListener(this);
        floatingBtnClearLog.requestFocus();

        swipeRefreshDNSQueries = getActivity().findViewById(R.id.swipeRefreshDNSQueries);
        swipeRefreshDNSQueries.setOnRefreshListener(this);

        if (file_path.contains("query.log")) {
            getActivity().setTitle(R.string.title_dnscrypt_query_log);
        } else if (file_path.contains("nx.log")) {
            getActivity().setTitle(R.string.title_dnscrypt_nx_log);
        }

        refreshDNSQueries(file_path);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.floatingBtnClearLog) {
            FileOperations.writeToTextFile(getActivity(), file_path, Collections.singletonList(""), "ignored");
            tvLogFile.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            tvLogFile.setText(R.string.dnscrypt_empty_log);
        }

    }

    @Override
    public void onRefresh() {
        refreshDNSQueries(file_path);
        swipeRefreshDNSQueries.setRefreshing(false);
    }

    private void refreshDNSQueries(final String path) {

        try {

            List<String> lines = new ArrayList<>();
            String line;

            try (FileReader reader = new FileReader(path);
                 BufferedReader bufferedReader = new BufferedReader(reader)) {

                while ((line = bufferedReader.readLine()) != null) {
                    lines.add(line);
                }

            }

            final String tvLogFileText = shortenToLongFile(path, lines);

            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    if (tvLogFileText.isEmpty()) {
                        if (tvLogFile.getTextAlignment() != View.TEXT_ALIGNMENT_CENTER) {
                            tvLogFile.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                        }
                        tvLogFile.setText(R.string.dnscrypt_empty_log);
                    } else {
                        if (tvLogFile.getTextAlignment() != View.TEXT_ALIGNMENT_TEXT_START) {
                            tvLogFile.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                        }
                        if (!tvLogFile.getText().toString().trim().equals(tvLogFileText)) {
                            tvLogFile.setText(tvLogFileText);
                        }
                    }

                });
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "DNSCrypt startReadAndRefreshLogs fault " + e.getMessage() + e.getCause());
        }
    }

    private String shortenToLongFile(String path, List<String> lines) {

        StringBuilder stringBuilder = new StringBuilder();

        int i = 0;
        if (lines.size() > MAX_LINES_QUANTITY) {
            i = lines.size() - MAX_LINES_QUANTITY;
        }
        for (; i < lines.size(); i++) {
            stringBuilder.append(lines.get(i)).append(System.lineSeparator());
        }
        String tvLogFileText = stringBuilder.toString().trim();

        if (lines.size() > MAX_LINES_QUANTITY * 2) {
            try(FileWriter fileWriter = new FileWriter(path)) {
                fileWriter.write(tvLogFileText);
            } catch (IOException e) {
                Log.e(LOG_TAG, "DNSCrypt ShowLogTimer shorten file fault " + e.getMessage() + e.getCause());
            }
        }
        return tvLogFileText;
    }

}
