package pan.alexander.tordnscrypt.settings.tor_countries;

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

import java.util.ArrayList;

import static pan.alexander.tordnscrypt.settings.tor_countries.CountrySelectFragment.entryNodes;

class SelectedCountries {
    ArrayList<String> countriesList = new ArrayList<>();

    SelectedCountries(String countries, int current_nodes_type, ArrayList<Countries> countriesListCurrent) {
        if (!countries.isEmpty()) {
            String[] arr = countries.split(",");
            for (String str : arr) {
                this.countriesList.add(str.trim().replaceAll("[^A-Z]+", ""));
            }
        } else if (current_nodes_type == entryNodes) {
            for (Countries country : countriesListCurrent) {
                this.countriesList.add(country.countryCode);
            }
        }
    }
}
