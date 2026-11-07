package com.foodrush.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.StringRes;

import com.foodrush.app.databinding.ActivityWebPageBinding;

/** WebView for the bundled Terms and Privacy pages. JavaScript stays off: they are plain HTML. */
public class WebPageActivity extends BaseActivity {

    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_ASSET = "asset";

    public static void open(Context context, @StringRes int title, String assetName) {
        context.startActivity(new Intent(context, WebPageActivity.class)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ASSET, assetName));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityWebPageBinding binding = ActivityWebPageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpTopBar(binding.webBar, getIntent().getIntExtra(EXTRA_TITLE, 0));
        binding.webView.loadUrl("file:///android_asset/" + getIntent().getStringExtra(EXTRA_ASSET));
    }
}
