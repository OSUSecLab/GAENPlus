/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.exposurenotification.ble.utils;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import androidx.core.content.ContextCompat;

import java.util.List;

/**
 * Utility class for bluetooth.
 */
@SuppressWarnings("MissingPermission")
public class BluetoothUtils {
    public static boolean isBluetoothProfileInUsing(List<Integer> profiles) {
        /**
         * FIXME: Get the device's Bluetooth Adapter from
         * {@link ContextCompat#getSystemService(Context, Class)} with the class
         * {@link BluetoothManager}
         */
        BluetoothAdapter adapter = null;
        if (adapter == null || !adapter.isEnabled()) {
            return false;
        }

        for (int profile : profiles) {
            int connectingState = adapter.getProfileConnectionState(profile);
            if (connectingState == BluetoothAdapter.STATE_CONNECTED
                    || connectingState == BluetoothAdapter.STATE_CONNECTING) {
                return true;
            }
        }
        return false;
    }

    private BluetoothUtils() {
    }
}