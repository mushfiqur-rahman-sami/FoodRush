package com.foodrush.app.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.foodrush.app.R;

/**
 * Loads dish and restaurant pictures. An image is one of: "res:dish_kacchi" (a photo bundled with the
 * app), a "data:image/jpeg;base64,..." URL (a photo a restaurant uploaded), or a web URL.
 */
public final class Images {

    private Images() {
    }

    /** Food photos as round plates, the design's look. */
    public static void plate(ImageView view, String image) {
        request(view, image).placeholder(R.drawable.ic_food_placeholder).circleCrop().into(view);
    }

    /** Wide covers and photo previews, filling the view. */
    public static void cover(ImageView view, String image) {
        request(view, image).placeholder(R.drawable.ic_food_placeholder).centerCrop().into(view);
    }

    private static RequestBuilder<android.graphics.drawable.Drawable> request(ImageView view, String image) {
        if (image != null && image.startsWith("res:")) {
            int id = resource(view.getContext(), image.substring(4));
            if (id != 0) {
                return Glide.with(view).load(id);
            }
        }
        return Glide.with(view).load(image);
    }

    /** Bundled photos are looked up by name because their names are stored as data, not code. */
    @SuppressLint("DiscouragedApi")
    private static int resource(Context context, String name) {
        return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
    }
}
