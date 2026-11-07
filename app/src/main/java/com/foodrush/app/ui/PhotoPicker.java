package com.foodrush.app.ui;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;

import com.foodrush.app.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.function.Consumer;

/**
 * "Take a photo or choose one" for any screen. Create it as a field of the activity, so its result
 * launchers are registered before the activity starts.
 */
public final class PhotoPicker {

    private final BaseActivity activity;
    private final Consumer<Uri> onPicked;
    private final ActivityResultLauncher<Uri> camera;
    private final ActivityResultLauncher<Intent> gallery;
    private Uri cameraUri;

    public PhotoPicker(BaseActivity activity, Consumer<Uri> onPicked) {
        this.activity = activity;
        this.onPicked = onPicked;
        camera = activity.registerForActivityResult(new ActivityResultContracts.TakePicture(), saved -> {
            if (Boolean.TRUE.equals(saved) && cameraUri != null) {
                onPicked.accept(cameraUri);
            }
        });
        gallery = activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Intent data = result.getData();
            if (result.getResultCode() == BaseActivity.RESULT_OK && data != null && data.getData() != null) {
                onPicked.accept(data.getData());
            }
        });
    }

    public void choose() {
        String[] options = {activity.getString(R.string.photo_camera), activity.getString(R.string.photo_gallery)};
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.change_photo)
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        activity.withPermission(Manifest.permission.CAMERA, R.string.permission_camera, this::openCamera);
                    } else {
                        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
                        activity.withPermission(permission, R.string.permission_media, () -> gallery.launch(
                                new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)));
                    }
                })
                .show();
    }

    private void openCamera() {
        File dir = new File(activity.getCacheDir(), "camera");
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        cameraUri = FileProvider.getUriForFile(activity, "com.foodrush.app.files",
                new File(dir, "capture_" + System.currentTimeMillis() + ".jpg"));
        camera.launch(cameraUri);
    }
}
