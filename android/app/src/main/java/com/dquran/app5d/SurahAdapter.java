package com.dquran.app5d;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SurahAdapter extends RecyclerView.Adapter<SurahAdapter.ViewHolder> {

    public interface OnSurahActionListener {
        void onAction(SurahModel surah, String actionType);
    }

    private final Context context;
    private final List<SurahModel> fullList;
    private final List<SurahModel> displayList;
    private final OnSurahActionListener actionListener;

    public SurahAdapter(Context context, List<SurahModel> surahs, OnSurahActionListener listener) {
        this.context = context;
        this.fullList = new ArrayList<>(surahs);
        this.displayList = new ArrayList<>(surahs);
        this.actionListener = listener;
    }

    public void filter(String query, String typeFilter) {
        displayList.clear();
        String q = query.toLowerCase().trim();

        for (SurahModel s : fullList) {
            boolean matchType = true;
            if ("makkiyah".equalsIgnoreCase(typeFilter)) {
                matchType = "Makkiyah".equalsIgnoreCase(s.getRevelationType()) || "Meccan".equalsIgnoreCase(s.getRevelationType());
            } else if ("madaniyah".equalsIgnoreCase(typeFilter)) {
                matchType = "Madaniyah".equalsIgnoreCase(s.getRevelationType()) || "Medinan".equalsIgnoreCase(s.getRevelationType());
            }

            if (!matchType) continue;

            if (q.isEmpty()) {
                displayList.add(s);
            } else {
                boolean matchName = s.getEnglishName().toLowerCase().contains(q);
                boolean matchIndo = s.getIndonesianName().toLowerCase().contains(q);
                boolean matchNumber = String.valueOf(s.getNumber()).equals(q);
                boolean matchArabic = s.getName().contains(q);

                if (matchName || matchIndo || matchNumber || matchArabic) {
                    displayList.add(s);
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
        SurahModel s = displayList.get(position);

        holder.tvNumber.setText(String.valueOf(s.getNumber()));
        holder.tvName.setText(s.getEnglishName());
        holder.tvTranslation.setText(s.getIndonesianName() + " · " + s.getNumberOfAyahs() + " Ayat");
        holder.tvType.setText(s.getRevelationType());
        holder.tvArabic.setText(QuranRepository.cleanArabicSurahName(s.getName()));
        holder.tvArabic.setTypeface(PreferenceManager.getMushafTypeface(context));

        holder.itemView.setOnClickListener(v -> actionListener.onAction(s, "baca"));
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
