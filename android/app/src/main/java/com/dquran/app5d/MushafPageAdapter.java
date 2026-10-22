package com.dquran.app5d;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MushafPageAdapter extends RecyclerView.Adapter<MushafPageAdapter.PageViewHolder> {

    public interface OnPageAyahListener {
        void onAyahClick(AyahModel ayah);
    }

    private final Context context;
    private final SurahModel surah;
    private final List<MushafPageModel> pages;
    private final OnPageAyahListener listener;

    private int activeAyahNumber = -1;
    private boolean isAyahMasked = false;

    public MushafPageAdapter(Context context, SurahModel surah, List<MushafPageModel> pages, OnPageAyahListener listener) {
        this.context = context;
        this.surah = surah;
        this.pages = pages;
        this.listener = listener;
    }

    public void setActiveAyah(int ayahNumber, boolean masked) {
        this.activeAyahNumber = ayahNumber;
        this.isAyahMasked = masked;
        notifyDataSetChanged();
    }

    public int getActiveAyahNumber() {
        return activeAyahNumber;
    }

    public int findPositionForAyah(int ayahNumber) {
        for (int i = 0; i < pages.size(); i++) {
            for (AyahModel a : pages.get(i).getAyahs()) {
                if (a.getNumberInSurah() == ayahNumber) {
                    return i;
                }
            }
        }
        return 0;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_mushaf_page, parent, false);
        return new PageViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        MushafPageModel page = pages.get(position);

        // 1. Page Banner
        String bannerText = "Halaman " + page.getPageNumber() + " · Ruku " + page.getRukuNumber();
        holder.tvPageBadge.setText(bannerText);

        // 2. Surah Header
        if (page.isStartOfSurah() && surah != null) {
            holder.layoutSurahHeader.setVisibility(View.VISIBLE);
            holder.tvSurahNumBadge.setText(String.valueOf(surah.getNumber()));
            holder.tvSurahPageName.setText(surah.getEnglishName());
            holder.tvSurahPageTrans.setText(surah.getIndonesianName() + " · " + surah.getNumberOfAyahs() + " Ayat");

            if (surah.getNumber() == 9) { // At-Taubah has no Bismillah
                holder.tvPageBismillah.setVisibility(View.GONE);
            } else {
                holder.tvPageBismillah.setVisibility(View.VISIBLE);
                holder.tvPageBismillah.setTypeface(PreferenceManager.getMushafTypeface(context));
            }
        } else {
            holder.layoutSurahHeader.setVisibility(View.GONE);
        }

        // 3. Continuous Quran Text
        Typeface mushafTf = PreferenceManager.getMushafTypeface(context);
        holder.tvPageArabicContent.setTypeface(mushafTf);
        holder.tvPageArabicContent.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        holder.tvPageArabicContent.setTextDirection(View.TEXT_DIRECTION_RTL);
        holder.tvPageArabicContent.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        holder.tvPageArabicContent.setGravity(android.view.Gravity.RIGHT);

        int goldCircle = context.getResources().getColor(R.color.gold_primary);
        int goldText = context.getResources().getColor(R.color.gold_light);

        SpannableStringBuilder ssb = new SpannableStringBuilder();

        for (AyahModel ayah : page.getAyahs()) {
            final AyahModel curAyah = ayah;
            int start = ssb.length();

            boolean isCurrentActive = (ayah.getNumberInSurah() == activeAyahNumber);

            if (isCurrentActive && isAyahMasked) {
                // Hidden/masked text placeholder for memory test
                String maskText = "  [ ... Ayat " + ayah.getNumberInSurah() + " disembunyikan untuk menguji hafalan ... ]  ";
                ssb.append(maskText);
                int end = ssb.length();
                ssb.setSpan(new ForegroundColorSpan(0xFF64748B), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new BackgroundColorSpan(0x22F0C060), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                ssb.append(ayah.getArabic());
                int end = ssb.length();

                if (isCurrentActive) {
                    // Highlight active ayah in gold/cyan tint
                    ssb.setSpan(new BackgroundColorSpan(0x3300FFD5), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.setSpan(new ForegroundColorSpan(0xFF00FFD5), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            int ayahEnd = ssb.length();

            // ClickableSpan for each Ayah
            ssb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    if (listener != null) {
                        listener.onAyahClick(curAyah);
                    }
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    ds.setUnderlineText(false);
                }
            }, start, ayahEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            // Inline Ayah End Circle Badge
            ssb.append(" ");
            int badgeStart = ssb.length();
            ssb.append(String.valueOf(ayah.getNumberInSurah()));
            int badgeEnd = ssb.length();
            ssb.setSpan(new AyahNumberSpan(ayah.getNumberInSurah(), goldCircle, goldText), badgeStart, badgeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append(" ");
        }

        holder.tvPageArabicContent.setText(ssb);
        holder.tvPageArabicContent.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {
        View layoutPageBanner;
        TextView tvPageBadge;
        View layoutSurahHeader;
        TextView tvSurahNumBadge, tvSurahPageName, tvSurahPageTrans, tvPageBismillah;
        TextView tvPageArabicContent;

        PageViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutPageBanner = itemView.findViewById(R.id.layout_page_banner);
            tvPageBadge = itemView.findViewById(R.id.tv_page_badge);
            layoutSurahHeader = itemView.findViewById(R.id.layout_surah_header);
            tvSurahNumBadge = itemView.findViewById(R.id.tv_surah_num_badge);
            tvSurahPageName = itemView.findViewById(R.id.tv_surah_page_name);
            tvSurahPageTrans = itemView.findViewById(R.id.tv_surah_page_trans);
            tvPageBismillah = itemView.findViewById(R.id.tv_page_bismillah);
            tvPageArabicContent = itemView.findViewById(R.id.tv_page_arabic_content);
        }
    }
}
