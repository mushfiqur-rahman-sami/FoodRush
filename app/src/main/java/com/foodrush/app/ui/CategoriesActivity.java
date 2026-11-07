package com.foodrush.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.foodrush.app.R;
import com.foodrush.app.adapter.CategoryGridAdapter;
import com.foodrush.app.databinding.ActivityCategoriesBinding;
import com.foodrush.app.util.States;

import java.util.ArrayList;
import java.util.List;

/** GridView of every category near the customer. Returns the chosen one as an activity result. */
public class CategoriesActivity extends BaseActivity {

    public static final String EXTRA_CATEGORY = "category";
    private static final String EXTRA_PAIRS = "pairs";

    private final CategoryGridAdapter adapter = new CategoryGridAdapter();

    /** @param namesAndImages name, image, name, image, ... */
    public static Intent intent(Context context, ArrayList<String> namesAndImages) {
        return new Intent(context, CategoriesActivity.class).putStringArrayListExtra(EXTRA_PAIRS, namesAndImages);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityCategoriesBinding binding = ActivityCategoriesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpTopBar(binding.categoriesBar, R.string.categories_title);

        binding.categoriesGrid.setAdapter(adapter);
        binding.categoriesGrid.setOnItemClickListener((parent, view, position, id) -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_CATEGORY, adapter.getItem(position).name));
            finish();
        });
        List<String> pairs = getIntent().getStringArrayListExtra(EXTRA_PAIRS);
        List<CategoryGridAdapter.Category> categories = new ArrayList<>();
        for (int i = 0; pairs != null && i + 1 < pairs.size(); i += 2) {
            categories.add(new CategoryGridAdapter.Category(pairs.get(i), pairs.get(i + 1)));
        }
        if (categories.isEmpty()) {
            States.empty(binding.categoriesState, R.string.no_dishes);
        } else {
            States.hide(binding.categoriesState);
            adapter.submit(categories);
        }
    }
}
