package com.foodrush.app.ui;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.foodrush.app.R;
import com.foodrush.app.data.FirebaseSync;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityProfileBinding;
import com.foodrush.app.util.AppExecutors;
import com.foodrush.app.util.Bangladesh;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** The Activity itself is the click listener here, the other style in the click-handling resource. */
public class ProfileActivity extends BaseActivity implements View.OnClickListener {

    private ActivityProfileBinding binding;
    private Session session;
    private Uri cameraUri;

    private final ActivityResultLauncher<Uri> camera = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), saved -> {
                if (Boolean.TRUE.equals(saved)) {
                    storePhoto(cameraUri);
                }
            });

    private final ActivityResultLauncher<Intent> gallery = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null) {
                    storePhoto(data.getData());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpTopBar(binding.profileBar, R.string.profile_title);
        session = new Session(this);

        binding.profileEmailLabel.setText(session.email());
        binding.profileName.setText(session.name());
        binding.profilePhone.setText(session.phone());
        binding.profileAddress.infoIcon.setImageResource(R.drawable.ic_pin);
        binding.profileAddress.getRoot().setVisibility(session.isCustomer() ? View.VISIBLE : View.GONE);

        binding.profileChangePhoto.setOnClickListener(this);
        binding.profilePhoto.setOnClickListener(this);
        binding.profileSave.setOnClickListener(this);
        binding.profileAddress.getRoot().setOnClickListener(this);
        renderPhoto();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.profileNameLabel.setText(session.name());
        binding.profileAddress.infoText.setText(session.addressLabel());
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.profile_save) {
            save();
        } else if (id == R.id.profile_change_photo || id == R.id.profile_photo) {
            choosePhotoSource();
        } else {
            startActivity(new Intent(this, AddressPickerActivity.class));
        }
    }

    private void save() {
        String name = String.valueOf(binding.profileName.getText()).trim();
        String phone = Bangladesh.normalizePhone(String.valueOf(binding.profilePhone.getText()));
        boolean nameOk = !name.isEmpty();
        boolean phoneOk = phone != null;
        binding.profileNameLayout.setError(nameOk ? null : getString(R.string.error_name));
        binding.profilePhoneLayout.setError(phoneOk ? null : getString(R.string.error_phone));
        if (!nameOk || !phoneOk) {
            return;
        }
        session.updateProfile(name, phone);
        binding.profilePhone.setText(phone);
        binding.profileNameLabel.setText(name);
        FirebaseSync.saveUser(this, session.uid(), name, session.email(), phone, session.role(), session.restaurantId());
        showMessage(getString(R.string.profile_saved));
    }

    private void choosePhotoSource() {
        String[] options = {getString(R.string.photo_camera), getString(R.string.photo_gallery)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.change_photo)
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        withPermission(Manifest.permission.CAMERA, R.string.permission_camera, this::openCamera);
                    } else {
                        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
                        withPermission(permission, R.string.permission_media, () -> gallery.launch(
                                new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)));
                    }
                })
                .show();
    }

    private void openCamera() {
        File dir = new File(getCacheDir(), "camera");
        if (!dir.exists() && !dir.mkdirs()) {
            showMessage(getString(R.string.no_app_for_action));
            return;
        }
        cameraUri = FileProvider.getUriForFile(this, "com.foodrush.app.files", new File(dir, "capture.jpg"));
        camera.launch(cameraUri);
    }

    /** Copies the image into private storage through the content resolver, off the main thread. */
    private void storePhoto(Uri source) {
        AppExecutors.load(() -> {
            File target = new File(getFilesDir(), "profile_" + System.currentTimeMillis() + ".jpg");
            try (InputStream in = getContentResolver().openInputStream(source);
                 OutputStream out = new FileOutputStream(target)) {
                if (in == null) {
                    return null;
                }
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return target;
            } catch (IOException e) {
                return null;
            }
        }, (File target) -> {
            if (target != null) {
                String old = session.photoPath();
                session.setPhotoPath(target.getPath());
                if (old != null) {
                    AppExecutors.io(() -> new File(old).delete());
                }
                renderPhoto();
            }
        });
    }

    private void renderPhoto() {
        String path = session.photoPath();
        if (path != null) {
            binding.profilePhoto.setPadding(0, 0, 0, 0);
            Glide.with(this).load(new File(path)).signature(new ObjectKey(path)).circleCrop().into(binding.profilePhoto);
        }
    }
}
