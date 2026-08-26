package com.veryrandomcreator.renthelp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class InspectionsAdapter extends RecyclerView.Adapter<InspectionsAdapter.InspectionViewHolder> {
    public interface OnItemClickListener {
        void onItemClick(Inspection item);
    }

    private final List<Inspection> inspections;
    private final OnItemClickListener listener;

    public InspectionsAdapter(List<Inspection> inspections, OnItemClickListener listener) {
        this.inspections = inspections;
        this.listener = listener;
    }

    @NonNull
    @Override
    public InspectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inspection, parent, false);
        return new InspectionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull InspectionViewHolder holder, int position) {
        Inspection item = inspections.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return inspections == null ? 0 : inspections.size();
    }

    static class InspectionViewHolder extends RecyclerView.ViewHolder {
        private final TextView labelTextView;
        private final TextView descriptionTextView;

        public InspectionViewHolder(@NonNull View itemView) {
            super(itemView);
            labelTextView = itemView.findViewById(R.id.text_view_inspection_label);
            descriptionTextView = itemView.findViewById(R.id.text_view_inspection_description);
        }

        public void bind(Inspection item, OnItemClickListener listener) {
            labelTextView.setText(item.getLabel());
            descriptionTextView.setText(item.getDescription());
            
            itemView.setAlpha(item.isReadOnly() ? 0.5f : 1.0f);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }
    }
}
