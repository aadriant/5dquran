package com.dquran.app5d;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SurahAdapter extends RecyclerView.Adapter<SurahAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(QuranItem item);
    }

    private final Context context;
    private final List<QuranItem> fullList = new ArrayList<>();
    private final List<QuranItem> displayList = new ArrayList<>();
    private final OnItemClickListener clickListener;

    public SurahAdapter(Context context, List<QuranItem> items, OnItemClickListener listener) {
        this.context = context;
        this.clickListener = listener;
        setItems(items);
    }

    public void setItems(List<QuranItem> items) {
        this.fullList.clear();
        if (items != null) {
            this.fullList.addAll(items);
        }
        this.displayList.clear();
        this.displayList.addAll(this.fullList);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        displayList.clear();
        String q = query != null ? query.toLowerCase().trim() : "";

        if (q.isEmpty()) {
            displayList.addAll(fullList);
        } else {
            for (QuranItem item : fullList) {
                boolean matchTitle = item.getTitle() != null && item.getTitle().toLowerCase().contains(q);
                boolean matchSubtitle = item.getSubtitle() != null && item.getSubtitle().toLowerCase().contains(q);
                boolean matchTag = item.getTag() != null && item.getTag().toLowerCase().contains(q);
                boolean matchNumber = String.valueOf(item.getNumber()).equals(q)
                        || ("juz " + item.getNumber()).equalsIgnoreCase(q)
                        || ("halaman " + item.getNumber()).equalsIgnoreCase(q)
                        || ("hal " + item.getNumber()).equalsIgnoreCase(q)
                        || ("wahyu " + item.getNumber()).equalsIgnoreCase(q);
                boolean matchArabic = item.getArabic() != null && item.getArabic().contains(q);
                boolean matchSurahRef = false;
                if (item.getSurahRef() != null) {
                    matchSurahRef = item.getSurahRef().getEnglishName().toLowerCase().contains(q)
                            || item.getSurahRef().getIndonesianName().toLowerCase().contains(q)
                            || String.valueOf(item.getSurahRef().getNumber()).equals(q);
                }

                if (matchTitle || matchSubtitle || matchTag || matchNumber || matchArabic || matchSurahRef) {
                    displayList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    public int getDisplayCount() {
        return displayList.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_surah, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        QuranItem item = displayList.get(position);

        holder.tvNumber.setText(String.valueOf(item.getNumber()));
        holder.tvName.setText(item.getTitle());
        holder.tvTranslation.setText(item.getSubtitle());
        holder.tvType.setText(item.getTag());
        holder.tvArabic.setText(item.getArabic());
        holder.tvArabic.setTypeface(PreferenceManager.getMushafTypeface(context));

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNumber, tvName, tvTranslation, tvType, tvArabic;

        ViewHolder(View itemView) {
            super(itemView);
            tvNumber = itemView.findViewById(R.id.tv_surah_number);
            tvName = itemView.findViewById(R.id.tv_surah_name);
            tvTranslation = itemView.findViewById(R.id.tv_surah_translation);
            tvType = itemView.findViewById(R.id.tv_surah_type);
            tvArabic = itemView.findViewById(R.id.tv_surah_arabic);
        }
    }
}
