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

package pan.alexander.tordnscrypt.settings.itpd_settings;

import androidx.annotation.NonNull;

import java.util.Objects;

public class ItpdSubscriptionRecycleItem {

    private String text;

    public ItpdSubscriptionRecycleItem(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItpdSubscriptionRecycleItem that = (ItpdSubscriptionRecycleItem) o;
        return text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(text);
    }

    @NonNull
    @Override
    public String toString() {
        return "ItpdSubscriptionRecycleItem{" +
                "text='" + text + '\'' +
                '}';
    }
}
