package com.example.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    public interface CategoryListener {
        void onCategoryClick(String category);
    }

    private final List<String> categories = new ArrayList<>();
    private final CategoryListener listener;
    private String selectedCategory = "All";

    public CategoryAdapter(CategoryListener listener) {
        this.listener = listener;
    }

    public void setCategories(List<String> list) {
        categories.clear();
        categories.add("All");
        if (list != null) {
            for (String c : list) {
                if (c != null && !c.trim().isEmpty() && !c.equalsIgnoreCase("Default") && !categories.contains(c)) {
                    categories.add(c);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void setSelectedCategory(String category) {
        this.selectedCategory = category != null ? category : "All";
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(com.example.R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        String cat = categories.get(position);
        holder.bind(cat);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView pillRoot;
        private final TextView txtCategoryName;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            pillRoot = itemView.findViewById(com.example.R.id.pill_root);
            txtCategoryName = itemView.findViewById(com.example.R.id.txt_category_name);
        }

        public void bind(String category) {
            txtCategoryName.setText(category);
            boolean isSelected = category.equalsIgnoreCase(selectedCategory);

            if (isSelected) {
                pillRoot.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), com.example.R.color.selected_card_background));
                txtCategoryName.setTextColor(ContextCompat.getColor(itemView.getContext(), com.example.R.color.accent));
            } else {
                pillRoot.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), com.example.R.color.search_background));
                txtCategoryName.setTextColor(ContextCompat.getColor(itemView.getContext(), com.example.R.color.text_secondary));
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCategoryClick(category);
                }
            });
        }
    }
}
