package pan.alexander.tordnscrypt;
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

import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;

public class CountrySelectFragment extends Fragment implements View.OnClickListener {

    public static final int entryNodes =100;
    public static final int excludeNodes =200;
    public static final int exitNodes =300;
    public static final int excludeExitNodes =400;
    int current_nodes_type = 0;
    String countries = "";
    String[] countriesFullListTitles;
    String[] countriesFullListValues;
    RecyclerView.Adapter rvAdapter;
    RecyclerView rvSelectCountries;

    public CountrySelectFragment() {

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        countriesFullListTitles = getResources().getStringArray(R.array.pref_countries_titles);
        countriesFullListValues = getResources().getStringArray(R.array.pref_countries_values);
        current_nodes_type = getArguments().getInt("nodes_type");
        countries = getArguments().getString("countries");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_country_select, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (current_nodes_type==entryNodes){
            getActivity().setTitle(R.string.pref_tor_entry_nodes);
        } else if (current_nodes_type==excludeNodes) {
            getActivity().setTitle(R.string.pref_tor_exclude_nodes);
        } else if (current_nodes_type==exitNodes) {
            getActivity().setTitle(R.string.pref_tor_exit_nodes);
        } else if (current_nodes_type==excludeExitNodes) {
            getActivity().setTitle(R.string.pref_tor_exclude_exit_nodes);
        }

        Button btnSelectAll = getActivity().findViewById(R.id.btnSelectAll);
        btnSelectAll.setOnClickListener(this);
        Button btnUnselectAll = getActivity().findViewById(R.id.btnUnselectAll);
        btnUnselectAll.setOnClickListener(this);

        rvSelectCountries = getActivity().findViewById(R.id.rvSelectCountries);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
        rvSelectCountries.setLayoutManager(mLayoutManager);
        rvAdapter = new CountriesAdapter();
        rvSelectCountries.setAdapter(rvAdapter);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btnSelectAll:
                ((CountriesAdapter)rvAdapter).checkAllCountries();
                rvAdapter.notifyDataSetChanged();
                break;
            case R.id.btnUnselectAll:
                ((CountriesAdapter)rvAdapter).cleanAllCountries();
                rvAdapter.notifyDataSetChanged();
                break;
        }

    }

    @Override
    public void onStop() {
        super.onStop();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        SharedPreferences.Editor editor = sp.edit();
        countries = ((CountriesAdapter)rvAdapter).getCheckedCountries();

        if (current_nodes_type==entryNodes){
            editor.putString("EntryNodesCountries",countries);
            editor.apply();
        } else if (current_nodes_type==excludeNodes) {
            editor.putString("ExcludeNodesCountries",countries);
            editor.apply();
        } else if (current_nodes_type==excludeExitNodes) {
            editor.putString("ExcludeExitNodesCountries",countries);
            editor.apply();
        } else if (current_nodes_type==exitNodes) {
            editor.putString("ExitNodesCountries",countries);
            editor.apply();
        }
    }

    public class SelectedCountries {
        ArrayList<String> countriesList = new ArrayList<>();

        SelectedCountries(String countries){
            if (!countries.isEmpty()){
                String[] arr = countries.split(",");
                for (String str : arr) {
                    this.countriesList.add(str.trim().replaceAll("[^A-Z]+", ""));
                }
                //Toast.makeText(getActivity(),this.countriesList.toString(),Toast.LENGTH_LONG).show();
            } else if(current_nodes_type==entryNodes){
                this.countriesList = new ArrayList<>(Arrays.asList(countriesFullListValues));
            }
        }
    }

    public class CountriesAdapter extends RecyclerView.Adapter<CountriesAdapter.CountriesViewHolder> {


        SelectedCountries selectedCountries = new SelectedCountries(countries);
        LayoutInflater lInflater = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        @NonNull
        @Override
        public CountriesAdapter.CountriesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = lInflater.inflate(R.layout.item_country, parent, false);
            return new CountriesAdapter.CountriesViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CountriesViewHolder holder, int position) {
            holder.bind(position);
        }


        @Override
        public int getItemCount() {
            return countriesFullListValues.length;
        }

        boolean isCountryInList(int position) {
            return selectedCountries.countriesList.contains(countriesFullListValues[position]);
        }

        void setChecked(int position) {
            selectedCountries.countriesList.add(countriesFullListValues[position]);
        }

        void removeCheck(String country){
            selectedCountries.countriesList.remove(country);
        }

        void checkAllCountries(){
            selectedCountries.countriesList = new ArrayList<>(Arrays.asList(countriesFullListValues));
        }

        void cleanAllCountries(){
            selectedCountries.countriesList.clear();
        }

        String getCheckedCountries() {
            if (selectedCountries.countriesList.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (String str:selectedCountries.countriesList){
                sb.append("{").append(str).append("}").append(",");
            }
            return sb.toString().substring(0,sb.lastIndexOf(","));
        }

        class CountriesViewHolder extends RecyclerView.ViewHolder {

            TextView tvCountry;
            CheckBox chbCountry;
            ConstraintLayout clCountry;

            CountriesViewHolder(View itemView) {
                super(itemView);

                tvCountry = itemView.findViewById(R.id.tvCountry);
                chbCountry = itemView.findViewById(R.id.chbCountry);
                clCountry = itemView.findViewById(R.id.clCountry);
                clCountry.addStatesFromChildren();
                clCountry.shouldDelayChildPressedState();
                clCountry.setClickable(true);
                clCountry.setOnClickListener(onClickListener);
                chbCountry.setOnCheckedChangeListener(activeListener);
                chbCountry.setFocusable(false);
                clCountry.setFocusable(true);
                clCountry.setOnFocusChangeListener(onFocusChangeListener);
            }

            void bind(int position){
                if (position%2==0) {
                    clCountry.setBackgroundColor(getResources().getColor(R.color.colorSecond));
                } else {
                    clCountry.setBackgroundColor(getResources().getColor(R.color.colorFirst));
                }

                tvCountry.setText(countriesFullListTitles[position]);
                chbCountry.setChecked(isCountryInList(position));
            }

            CompoundButton.OnCheckedChangeListener activeListener = new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                    if (!isCountryInList(getAdapterPosition())&& isChecked){
                        setChecked(getAdapterPosition());
                        notifyItemChanged(getAdapterPosition());
                    } else if (isCountryInList(getAdapterPosition())&& !isChecked) {
                        removeCheck(countriesFullListValues[getAdapterPosition()]);
                        notifyItemChanged(getAdapterPosition());
                    }
                }
            };

            View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isCountryInList(getAdapterPosition())){
                        setChecked(getAdapterPosition());
                        notifyItemChanged(getAdapterPosition());
                    } else if (isCountryInList(getAdapterPosition())) {
                        removeCheck(countriesFullListValues[getAdapterPosition()]);
                        notifyItemChanged(getAdapterPosition());
                    }
                }
            };

            View.OnFocusChangeListener onFocusChangeListener = new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        v.setBackgroundColor(getResources().getColor(R.color.colorSelected));
                    } else {
                        if (getAdapterPosition()%2==0) {
                            v.setBackgroundColor(getResources().getColor(R.color.colorSecond));
                        } else {
                            v.setBackgroundColor(getResources().getColor(R.color.colorFirst));
                        }
                    }

                }
            };


        }
    }
}
