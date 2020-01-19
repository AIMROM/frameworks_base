/*
 *  Copyright (C) 2018 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.android.systemui.aim.header;

import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.Log;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;

import com.android.systemui.R;

public class FileHeaderProvider implements
        StatusBarHeaderMachine.IStatusBarHeaderProvider {

    public static final String TAG = "FileHeaderProvider";
    private static final boolean DEBUG = false;
    private static final String HEADER_FILE_NAME = "custom_file_header_image";

    private Context mContext;
    private Drawable mImage;

    public FileHeaderProvider(Context context) {
        mContext = context;
    }

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public void settingsChanged(Uri uri) {
        final boolean customHeader = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_CUSTOM_HEADER, 0,
                UserHandle.USER_CURRENT) == 1;

        if (uri != null && uri.equals(Settings.System.getUriFor(
                Settings.System.STATUS_BAR_FILE_HEADER_IMAGE))) {
            String imageUri = Settings.System.getStringForUser(mContext.getContentResolver(),
                    Settings.System.STATUS_BAR_FILE_HEADER_IMAGE,
                    UserHandle.USER_CURRENT);
            if (imageUri != null) {
                saveHeaderImage(Uri.parse(imageUri));
            }
        }
        if (customHeader) {
            loadHeaderImage();
        }
    }

    @Override
    public void enableProvider() {
        settingsChanged(null);
    }

    @Override
    public void disableProvider() {
    }

    private void saveHeaderImage(Uri imageUri) {
        if (DEBUG) Log.i(TAG, "Save header image " + " " + imageUri);
        try {
            final InputStream imageStream = mContext.getContentResolver().openInputStream(imageUri);
            File file = new File(mContext.getFilesDir(), HEADER_FILE_NAME);
            if (file.exists()) {
                file.delete();
            }
            FileOutputStream output = new FileOutputStream(file);
            byte[] buffer = new byte[8 * 1024];
            int read;

            while ((read = imageStream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            if (DEBUG) Log.i(TAG, "Saved header image " + " " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Save header image failed " + " " + imageUri);
        }
    }

    private void loadHeaderImage() {
        mImage = null;
        File file = new File(mContext.getFilesDir(), HEADER_FILE_NAME);
        if (file.exists()) {
            if (DEBUG) Log.i(TAG, "Load header image");
            final Bitmap image = BitmapFactory.decodeFile(file.getAbsolutePath());
            mImage = new BitmapDrawable(mContext.getResources(), resizeMaxDeviceSize(mContext, image));
        }
    }

    @Override
    public Drawable getCurrent(final Calendar now) {
        return mImage;
    }

    public static Bitmap resizeMaxDeviceSize(Context context, Drawable image) {
        Bitmap i2b = ((BitmapDrawable) image).getBitmap();
        return resizeMaxDeviceSize(context, i2b);
    }

    public static Bitmap resizeMaxDeviceSize(Context context, Bitmap image) {
        Bitmap imageToBitmap;
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = context.getSystemService(WindowManager.class);
        wm.getDefaultDisplay().getRealMetrics(metrics);
        int maxHeight = metrics.heightPixels;
        int maxWidth = metrics.widthPixels;
        try {
            imageToBitmap = RGB565toARGB888(image);
            if (maxHeight > 0 && maxWidth > 0) {
                int width = imageToBitmap.getWidth();
                int height = imageToBitmap.getHeight();
                float ratioBitmap = (float) width / (float) height;
                float ratioMax = (float) maxWidth / (float) maxHeight;

                int finalWidth = maxWidth;
                int finalHeight = maxHeight;
                if (ratioMax > ratioBitmap) {
                    finalWidth = (int) ((float)maxHeight * ratioBitmap);
                } else {
                    finalHeight = (int) ((float)maxWidth / ratioBitmap);
                }
                imageToBitmap = Bitmap.createScaledBitmap(imageToBitmap, finalWidth, finalHeight, true);
                return imageToBitmap;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return image;
    }

    private static Bitmap RGB565toARGB888(Bitmap img) throws Exception {
        int numPixels = img.getWidth() * img.getHeight();
        int[] pixels = new int[numPixels];
        //Get JPEG pixels.  Each int is the color values for one pixel.
        img.getPixels(pixels, 0, img.getWidth(), 0, 0, img.getWidth(), img.getHeight());
        //Create a Bitmap of the appropriate format.
        Bitmap result = Bitmap.createBitmap(img.getWidth(), img.getHeight(), Bitmap.Config.ARGB_8888);
        //Set RGB pixels.
        result.setPixels(pixels, 0, result.getWidth(), 0, 0, result.getWidth(), result.getHeight());
        return result;
    }
}
