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

package pan.alexander.tordnscrypt.views;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

import pan.alexander.tordnscrypt.R;

/**
 * Custom preference for handling a switch with a clickable preference area as well
 */
public class SwitchPlusClickPreference extends SwitchPreferenceCompat {

    //
    // Public interface
    //

    /**
     * Sets listeners for the switch and the background container preference view cell
     *
     * @param listener A valid SwitchPlusClickListener
     */
    public void setSwitchClickListener(SwitchPlusClickListener listener) {
        this.listener = listener;
    }

    private SwitchPlusClickListener listener = null;

    /**
     * Interface gives callbacks in to both parts of the preference
     */
    public interface SwitchPlusClickListener {
        /**
         * Called when the switch is switched
         *
         * @param buttonView
         * @param isChecked
         */
        public void onCheckedChanged(SwitchCompat buttonView, boolean isChecked);

        /**
         * Called when the preference view is clicked
         *
         * @param view
         */
        public void onClick(View view);
    }

    public SwitchPlusClickPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SwitchPlusClickPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SwitchPlusClickPreference(Context context) {
        super(context);
    }


    //
    // Internal Functions
    //

    /**
     * Recursively go through view tree until we find an android.widget.Switch
     *
     * @param view Root view to start searching
     * @return A Switch class or null
     */
    private SwitchCompat findSwitchWidget(View view) {
        if (view instanceof SwitchCompat) {
            return (SwitchCompat) view;
        }
        if (view instanceof ViewGroup viewGroup) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child instanceof ViewGroup) {
                    SwitchCompat result = findSwitchWidget(child);
                    if (result != null) return result;
                }
                if (child instanceof SwitchCompat) {
                    return (SwitchCompat) child;
                }
            }
        }
        return null;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        SharedPreferences sharedPreferences = getSharedPreferences();
        if (sharedPreferences == null) {
            return;
        }

        final SwitchCompat switchView = findSwitchWidget(holder.itemView);
        if (switchView != null) {
            Drawable divider = ContextCompat.getDrawable(getContext(), R.drawable.switch_plus_click_divider);
            switchView.setBackground(divider);
            switchView.setOnClickListener(v -> {
                if (listener != null)
                    listener.onCheckedChanged((SwitchCompat) v, ((SwitchCompat) v).isChecked());
            });
            switchView.setChecked(sharedPreferences.getBoolean(getKey(), false));
            switchView.setFocusable(true);
            switchView.setEnabled(true);
            //Set the thumb drawable here if you need to. Seems like this code makes it not respect thumb_drawable in the xml.
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(v);
        });
    }
}
