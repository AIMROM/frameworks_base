/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.service.autofill;

import android.os.Bundle;
import android.os.RemoteCallback;
import android.view.autofill.AutofillValue;
import java.util.List;

/**
 * Service used to calculate match scores for Autofill Field Classification.
 *
 * @hide
 */
oneway interface IAutofillFieldClassificationService {
    void getAvailableAlgorithms(in RemoteCallback callback);
    void getDefaultAlgorithm(in RemoteCallback callback);
    void getScores(in RemoteCallback callback, String algorithmName, in Bundle algorithmArgs,
                  in List<AutofillValue> actualValues, in String[] userDataValues);
}
