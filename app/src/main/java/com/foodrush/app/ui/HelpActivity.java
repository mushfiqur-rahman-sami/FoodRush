package com.foodrush.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;

import com.foodrush.app.R;
import com.foodrush.app.databinding.ActivityHelpBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class HelpActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityHelpBinding binding = ActivityHelpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpTopBar(binding.helpBar, R.string.help_title);

        String[] questions = getResources().getStringArray(R.array.faq_questions);
        String[] answers = getResources().getStringArray(R.array.faq_answers);
        binding.helpFaq.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, questions));
        binding.helpFaq.setOnItemClickListener((parent, view, position, id) ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle(questions[position])
                        .setMessage(answers[position])
                        .setPositiveButton(R.string.ok, null)
                        .show());
    }

    /** Wired with android:onClick in activity_help.xml. */
    public void onEmailSupport(View view) {
        startImplicit(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + getString(R.string.support_email)))
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.support_subject)));
    }

    /** Wired with android:onClick in activity_help.xml. */
    public void onCallSupport(View view) {
        dial(getString(R.string.support_phone));
    }
}
