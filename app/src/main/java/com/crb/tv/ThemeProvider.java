package com.crb.tv;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * ContentProvider untuk menerima push config.json dan wallpaper.jpg dari tools/theme.sh
 * melalui `content write --uri content://com.crb.tv.theme/config.json`.
 */
public class ThemeProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        if (getContext() == null) throw new FileNotFoundException("No context");
        String seg = uri.getLastPathSegment();
        if (!"config.json".equals(seg) && !"wallpaper.jpg".equals(seg)) {
            throw new SecurityException("Only config.json and wallpaper.jpg are allowed");
        }
        File target = new File(getContext().getFilesDir(), seg);
        int fileMode = ParcelFileDescriptor.MODE_READ_ONLY;
        if (mode.contains("w")) {
            fileMode = ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        }
        return ParcelFileDescriptor.open(target, fileMode);
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "application/octet-stream";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        if (getContext() == null) return 0;
        String seg = uri.getLastPathSegment();
        if ("config.json".equals(seg) || "wallpaper.jpg".equals(seg)) {
            File target = new File(getContext().getFilesDir(), seg);
            return target.delete() ? 1 : 0;
        }
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }
}
