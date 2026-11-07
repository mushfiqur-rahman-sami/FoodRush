package com.foodrush.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Shrinks a photo from the camera or gallery into small JPEG data URLs that fit in Firestore
 * documents (1 MB each). Firebase Storage needs the paid plan, so this keeps photo uploads free.
 */
public final class PhotoEncoder {

    public static final int FULL_SIDE = 800;
    public static final int THUMB_SIDE = 240;
    public static final int MEDIUM_SIDE = 480;
    /** Leaves room under Firestore's 1 MB document limit after base64 grows the bytes by a third. */
    private static final int MAX_FULL_CHARS = 650_000;

    public static final class Encoded {
        public final String full;
        /** About 50 KB: for wide pictures that many phones load, like restaurant covers. */
        public final String medium;
        public final String thumb;

        Encoded(String full, String medium, String thumb) {
            this.full = full;
            this.medium = medium;
            this.thumb = thumb;
        }
    }

    private PhotoEncoder() {
    }

    /** Blocking; call off the main thread. Returns null when the image cannot be read. */
    public static Encoded encode(Context context, Uri source) throws IOException {
        Bitmap bitmap = decode(context, source, FULL_SIDE * 2);
        if (bitmap == null) {
            return null;
        }
        bitmap = rotate(bitmap, orientation(context, source));
        Bitmap full = scale(bitmap, FULL_SIDE);
        Bitmap thumb = squareThumb(bitmap, THUMB_SIDE);
        String fullUrl = null;
        for (int quality = 80; quality >= 40; quality -= 10) {
            fullUrl = toDataUrl(full, quality);
            if (fullUrl.length() <= MAX_FULL_CHARS) {
                break;
            }
        }
        return new Encoded(fullUrl, toDataUrl(scale(bitmap, MEDIUM_SIDE), 70), toDataUrl(thumb, 70));
    }

    private static Bitmap decode(Context context, Uri source, int targetSide) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetSide) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            return BitmapFactory.decodeStream(in, null, options);
        }
    }

    private static int orientation(Context context, Uri source) {
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in == null) {
                return 0;
            }
            int value = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (value) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (IOException e) {
            return 0;
        }
    }

    private static Bitmap rotate(Bitmap bitmap, int degrees) {
        if (degrees == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private static Bitmap scale(Bitmap bitmap, int maxSide) {
        int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longest <= maxSide) {
            return bitmap;
        }
        float ratio = (float) maxSide / longest;
        return Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * ratio),
                Math.round(bitmap.getHeight() * ratio), true);
    }

    private static Bitmap squareThumb(Bitmap bitmap, int side) {
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap square = Bitmap.createBitmap(bitmap, (bitmap.getWidth() - size) / 2, (bitmap.getHeight() - size) / 2,
                size, size);
        return Bitmap.createScaledBitmap(square, side, side, true);
    }

    private static String toDataUrl(Bitmap bitmap, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }
}
