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

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.utils.FileOperations;

/**
 * A simple {@link Fragment} subclass.
 */
public class ShowLogFragment extends android.app.Fragment implements View.OnClickListener {

    ArrayList<String> log_file;
    String file_path;
    TextView tvLogFile;
    String appDataDir;
    String busyboxPath;


    public ShowLogFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        log_file = getArguments().getStringArrayList("log_file");
        file_path = getArguments().getString("path");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_show_log, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();

        PathVars pathVars = new PathVars(getActivity());
        appDataDir = pathVars.appDataDir;
        busyboxPath = pathVars.busyboxPath;

        tvLogFile = getActivity().findViewById(R.id.tvLogFile);
        getActivity().findViewById(R.id.btnDeleteLog).setOnClickListener(this);

        if(file_path.contains("query.log")){
            getActivity().setTitle(R.string.title_dnscrypt_query_log);
        } else if(file_path.contains("nx.log")){
            getActivity().setTitle(R.string.title_dnscrypt_nx_log);
        }

        if (log_file.size() > 1){
            StringBuilder sb = new StringBuilder();
            for (int i=0;i<log_file.size();i++){
                sb.append(log_file.get(i)).append((char)10);
            }
            tvLogFile.setText(sb);
        } else {
            tvLogFile.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            tvLogFile.setText(R.string.dnscrypt_empty_log);
        }

    }

    @Override
    public void onClick(View v) {
        List<String> emptyString = new LinkedList<>();
        emptyString.add("");

        if(v.getId()==R.id.btnDeleteLog){
            FileOperations.writeToTextFile(getActivity(),file_path,emptyString,"ignored");
            tvLogFile.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            tvLogFile.setText(R.string.dnscrypt_empty_log);
        }

    }
}
