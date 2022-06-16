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

package com.google.samples.exposurenotification.nearby;

import androidx.annotation.IntDef;

import com.google.samples.exposurenotification.ExposureNotificationEnums;

/**
 * Infectiousness defined for an {@link ExposureWindow}.
 */
@IntDef({
        Infectiousness.NONE,
        Infectiousness.STANDARD,
        Infectiousness.HIGH,
})
public @interface Infectiousness {
    int NONE = ExposureNotificationEnums.Infectiousness.INFECTIOUSNESS_NONE_VALUE;
    int STANDARD = ExposureNotificationEnums.Infectiousness.INFECTIOUSNESS_STANDARD_VALUE;
    int HIGH = ExposureNotificationEnums.Infectiousness.INFECTIOUSNESS_HIGH_VALUE;
}