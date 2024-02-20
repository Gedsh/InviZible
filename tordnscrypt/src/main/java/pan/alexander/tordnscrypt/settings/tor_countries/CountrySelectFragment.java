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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.settings.tor_countries;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.SettingsActivity;
import pan.alexander.tordnscrypt.settings.tor_preferences.PreferencesTorFragment;

public class CountrySelectFragment extends Fragment implements CompoundButton.OnCheckedChangeListener, SearchView.OnQueryTextListener {

    public static final int entryNodes = 100;
    public static final int excludeNodes = 200;
    public static final int exitNodes = 300;
    public static final int excludeExitNodes = 400;
    private int current_nodes_type = 0;
    private String countries = "";
    private RecyclerView.Adapter<CountriesAdapter.CountriesViewHolder> rvAdapter;
    private ArrayList<Countries> countriesListCurrent;
    private ArrayList<Countries> countriesListSaved;
    private PreferencesTorFragment preferencesTorFragment;

    public CountrySelectFragment() {

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        String[] countriesFullListTitles = getResources().getStringArray(R.array.pref_countries_titles);
        String[] countriesFullListValues = getResources().getStringArray(R.array.pref_countries_values);

        countriesListCurrent = new ArrayList<>();

        if (countriesFullListValues.length != countriesFullListTitles.length) {
            throw new IllegalStateException("Wrong Tor countries array");
        }
        for (int i = 0; i < countriesFullListValues.length; i++) {
            countriesListCurrent.add(new Countries(countriesFullListTitles[i], countriesFullListValues[i]));
        }

        if (getArguments() != null) {
            current_nodes_type = getArguments().getInt("nodes_type");
            countries = getArguments().getString("countries");
        }

        if (getActivity() != null && getActivity() instanceof SettingsActivity) {
            SettingsActivity settingsActivity = (SettingsActivity) getActivity();
            preferencesTorFragment = settingsActivity.preferencesTorFragment;
        }
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

        if (getActivity() == null) {
            return;
        }

        if (current_nodes_type == entryNodes) {
            getActivity().setTitle(R.string.pref_tor_entry_nodes);
        } else if (current_nodes_type == excludeNodes) {
            getActivity().setTitle(R.string.pref_tor_exclude_nodes);
        } else if (current_nodes_type == exitNodes) {
            getActivity().setTitle(R.string.pref_tor_exit_nodes);
        } else if (current_nodes_type == excludeExitNodes) {
            getActivity().setTitle(R.string.pref_tor_exclude_exit_nodes);
        }

        CheckBox chbTorCountriesSelectorAll = getActivity().findViewById(R.id.chbTorCountriesSelectorAll);
        chbTorCountriesSelectorAll.setOnCheckedChangeListener(this);

        ((SearchView) getActivity().findViewById(R.id.searhTorCountry)).setOnQueryTextListener(this);

        RecyclerView rvSelectCountries = getActivity().findViewById(R.id.rvSelectCountries);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
        rvSelectCountries.setLayoutManager(mLayoutManager);
        rvSelectCountries.requestFocus();
        rvAdapter = new CountriesAdapter();
        rvSelectCountries.setAdapter(rvAdapter);
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean active) {
        if (compoundButton.getId() == R.id.chbTorCountriesSelectorAll) {
            if (active) {
                ((CountriesAdapter) rvAdapter).checkAllCountries();
            } else {
                ((CountriesAdapter) rvAdapter).cleanAllCountries();
            }
            rvAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        return true;
    }

    @Override
    public boolean onQueryTextChange(String s) {
        if (s == null || s.isEmpty()) {
            if (countriesListSaved != null) {
                countriesListCurrent = countriesListSaved;
                countriesListSaved = null;
                rvAdapter.notifyDataSetChanged();
            }
            return true;
        }

        if (countriesListSaved == null) {
            countriesListSaved = new ArrayList<>(countriesListCurrent);
        }

        countriesListCurrent.clear();

        for (Countries country : countriesListSaved) {

            if (s.trim().matches("[A-Z]{2}")
                    && country.countryCode.contains(s.trim())) {

                countriesListCurrent.clear();
                countriesListCurrent.add(country);
                break;

            } else if (country.countryName.toLowerCase().contains(s.toLowerCase().trim())
                    || country.countryCode.toLowerCase().contains(s.toLowerCase().trim())) {
                countriesListCurrent.add(country);
            }
        }

        rvAdapter.notifyDataSetChanged();

        return true;
    }

    @Override
    public void onStop() {
        super.onStop();

        if (getActivity() == null || preferencesTorFragment == null) {
            return;
        }

        countries = ((CountriesAdapter) rvAdapter).getCheckedCountries();

        if (current_nodes_type == entryNodes) {
            preferencesTorFragment.entryNodes = countries;
        } else if (current_nodes_type == excludeNodes) {
            preferencesTorFragment.excludeNodes = countries;
        } else if (current_nodes_type == excludeExitNodes) {
            preferencesTorFragment.excludeExitNodes = countries;
        } else if (current_nodes_type == exitNodes) {
            preferencesTorFragment.exitNodes = countries;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        preferencesTorFragment = null;
        rvAdapter = null;
    }

    private class CountriesAdapter extends RecyclerView.Adapter<CountriesAdapter.CountriesViewHolder> {


        SelectedCountries selectedCountries = new SelectedCountries(countries, current_nodes_type, countriesListCurrent);
        LayoutInflater lInflater = (LayoutInflater) requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

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
            return countriesListCurrent.size();
        }

        private boolean isCountryInList(int position) {
            return selectedCountries.countriesList.contains(countriesListCurrent.get(position).countryCode);
        }

        private void setChecked(int position) {
            selectedCountries.countriesList.add(countriesListCurrent.get(position).countryCode);
        }

        private void removeCheck(String country) {
            selectedCountries.countriesList.remove(country);
        }

        private void checkAllCountries() {
            selectedCountries.countriesList.clear();
            for (Countries country : countriesListCurrent) {
                selectedCountries.countriesList.add(country.countryCode);
            }
        }

        private void cleanAllCountries() {
            selectedCountries.countriesList.clear();
        }

        private String getCheckedCountries() {

            if (selectedCountries.countriesList.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            for (String str : selectedCountries.countriesList) {
                sb.append("{").append(str).append("}").append(",");
            }
            return sb.toString().substring(0, sb.lastIndexOf(","));
        }

        private class CountriesViewHolder extends RecyclerView.ViewHolder {

            private final TextView tvCountry;
            private final CheckBox chbCountry;
            private final CardView cardCountry;
            private final LinearLayoutCompat llTorCountry;
            private CardView cardTorCountryFragment;

            private CountriesViewHolder(View itemView) {
                super(itemView);

                tvCountry = itemView.findViewById(R.id.tvCountry);
                chbCountry = itemView.findViewById(R.id.chbCountry);
                cardCountry = itemView.findViewById(R.id.cardCountry);
                cardCountry.setClickable(true);

                View.OnClickListener onClickListener = v -> {
                    if (!isCountryInList(getAdapterPosition())) {
                        setChecked(getAdapterPosition());
                        notifyItemChanged(getAdapterPosition());
                    } else if (isCountryInList(getAdapterPosition())) {
                        removeCheck(countriesListCurrent.get(getAdapterPosition()).countryCode);
                        notifyItemChanged(getAdapterPosition());
                    }
                };
                cardCountry.setOnClickListener(onClickListener);

                CompoundButton.OnCheckedChangeListener activeListener = (buttonView, isChecked) -> {

                    if (!isCountryInList(getAdapterPosition()) && isChecked) {
                        setChecked(getAdapterPosition());
                        notifyItemChanged(getAdapterPosition());
                    } else if (isCountryInList(getAdapterPosition()) && !isChecked) {
                        removeCheck(countriesListCurrent.get(getAdapterPosition()).countryCode);
                        notifyItemChanged(getAdapterPosition());
                    }
                };
                chbCountry.setOnCheckedChangeListener(activeListener);

                chbCountry.setFocusable(false);
                cardCountry.setFocusable(true);
                View.OnFocusChangeListener onFocusChangeListener = (v, hasFocus) -> {
                    if (hasFocus) {
                        ((CardView) v).setCardBackgroundColor(getResources().getColor(R.color.colorSelected));
                    } else {
                        if (getAdapterPosition() % 2 == 0) {
                            ((CardView) v).setCardBackgroundColor(getResources().getColor(R.color.colorSecond));
                        } else {
                            ((CardView) v).setCardBackgroundColor(getResources().getColor(R.color.colorFirst));
                        }
                    }

                };

                cardCountry.setOnFocusChangeListener(onFocusChangeListener);

                llTorCountry = itemView.findViewById(R.id.llTorCountry);

                if (getActivity() != null) {
                    cardTorCountryFragment = getActivity().findViewById(R.id.cardTorCountryFragment);
                }

            }

            private void bind(int position) {
                if (position == 0 && cardTorCountryFragment != null) {
                    llTorCountry.setPadding(0, cardTorCountryFragment.getHeight(), 0, 0);
                } else {
                    llTorCountry.setPadding(0, 0, 0, 0);
                }

                if (position % 2 == 0) {
                    cardCountry.setCardBackgroundColor(getResources().getColor(R.color.colorSecond));
                } else {
                    cardCountry.setCardBackgroundColor(getResources().getColor(R.color.colorFirst));
                }

                tvCountry.setText(countriesListCurrent.get(position).countryName);
                chbCountry.setChecked(isCountryInList(position));
            }


        }
    }
}
