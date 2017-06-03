/**
 * Copyright (c) 2017, Paranoid Android
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
package com.android.server.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;

public abstract class ServiceHelper {

    private static final String gmsPackage = "com.google.android.gms";
    private static int uid = -1;

    /**
     * Check whether GMS is installed
     * @hide
     */
    public static boolean isGMSInstalled(final Context context) {
        if (uid == -1) {
            PackageManager pm = context.getPackageManager();
            try {
                uid = pm.getPackageUidAsUser(gmsPackage,
                        PackageManager.MATCH_SYSTEM_ONLY, UserHandle.USER_SYSTEM);
            } catch (PackageManager.NameNotFoundException ignore) {
                uid = -2;
            }
        }
        return uid > -1;
    }

}
