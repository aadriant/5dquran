package com.dquran.app5d;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AyahAdapter extends RecyclerView.Adapter<AyahAdapter.ViewHolder> {

    public interface OnAyahActionListener {
        void onAction(AyahModel ayah, String actionType);
    }

    private final Context context;
    private final List<AyahModel> ayahs;
    private final OnAyahActionListener listener;
    private final float arabicFontSize;

    // Karaoke state
    private int karaokeAyahIndex = -1;
    private int karaokeWordIndex = -1;

    public AyahAdapter(Context context, List<AyahModel> ayahs, float fontSize, OnAyahActionListener listener) {
        this.context = context;
        this.ayahs = ayahs;
        this.arabicFontSize = fontSize;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_ayah, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AyahModel a = ayahs.get(position);

        holder.tvNumber.setText(String.valueOf(a.getNumberInSurah()));
        holder.tvArabic.setTextSize(arabicFontSize);
        holder.tvArabic.setTypeface(PreferenceManager.getMushafTypeface(context));
        holder.tvArabic.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        holder.tvArabic.setTextDirection(View.TEXT_DIRECTION_RTL);
        holder.tvArabic.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        holder.tvArabic.setGravity(android.view.Gravity.RIGHT);

        if (position == karaokeAyahIndex && karaokeWordIndex >= 0) {
            applyKaraokeSpannable(holder.tvArabic, a.getArabic(), karaokeWordIndex);
        } else {
            holder.tvArabic.setText(a.getArabic());
            holder.tvArabic.setTextColor(context.getResources().getColor(R.color.gold_light));
        }

        if (PreferenceManager.isArabSaja(context)) {
            holder.tvIndo.setVisibility(View.GONE);
        } else {
            holder.tvIndo.setVisibility(View.VISIBLE);
            holder.tvIndo.setText(a.getIndo());
        }

        // 4. Pada halaman surat, jika ayat di klik, menuju tafsir
        holder.itemView.setOnClickListener(v -> listener.onAction(a, "pelajari"));
        holder.tvArabic.setOnClickListener(v -> listener.onAction(a, "pelajari"));
        holder.tvIndo.setOnClickListener(v -> listener.onAction(a, "pelajari"));

        holder.btnPlay.setOnClickListener(v -> listener.onAction(a, "dengar"));
        if (holder.btnKosakata != null) {
            holder.btnKosakata.setOnClickListener(v -> listener.onAction(a, "kosakata"));
        }
        holder.btnHafal.setOnClickListener(v -> listener.onAction(a, "hafal"));
        holder.btnTulis.setOnClickListener(v -> listener.onAction(a, "tulis"));
        holder.btnPelajari.setOnClickListener(v -> listener.onAction(a, "pelajari"));
    }

    @Override
    public int getItemCount() {
        return ayahs.size();
    }

    // ── KARAOKE HIGHLIGHT METHODS ──

    public void setKaraokeHighlight(RecyclerView rv, int ayahIndex, int wordIndex) {
        int oldAyahIndex = this.karaokeAyahIndex;
        this.karaokeAyahIndex = ayahIndex;
        this.karaokeWordIndex = wordIndex;

        // If active ayah changed, clear previous
        if (oldAyahIndex != -1 && oldAyahIndex != ayahIndex) {
            if (rv != null) {
                RecyclerView.ViewHolder oldVh = rv.findViewHolderForAdapterPosition(oldAyahIndex);
                if (oldVh instanceof ViewHolder && oldAyahIndex < ayahs.size()) {
                    ViewHolder h = (ViewHolder) oldVh;
                    h.tvArabic.setText(ayahs.get(oldAyahIndex).getArabic());
                    h.tvArabic.setTextColor(context.getResources().getColor(R.color.gold_light));
                    h.tvArabic.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                    h.tvArabic.setTextDirection(View.TEXT_DIRECTION_RTL);
                    h.tvArabic.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                    h.tvArabic.setGravity(android.view.Gravity.RIGHT);
                } else {
                    notifyItemChanged(oldAyahIndex);
                }
            } else {
                notifyItemChanged(oldAyahIndex);
            }
        }

        // Apply to current ayah ViewHolder without rebind
        if (rv != null && ayahIndex >= 0 && ayahIndex < ayahs.size()) {
            RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(ayahIndex);
            if (vh instanceof ViewHolder) {
                ViewHolder h = (ViewHolder) vh;
                applyKaraokeSpannable(h.tvArabic, ayahs.get(ayahIndex).getArabic(), wordIndex);
                return;
            }
        }
        notifyItemChanged(ayahIndex);
    }

    public void clearKaraokeHighlight(RecyclerView rv) {
        if (karaokeAyahIndex != -1) {
            int old = karaokeAyahIndex;
            karaokeAyahIndex = -1;
            karaokeWordIndex = -1;
            if (rv != null && old < ayahs.size()) {
                RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(old);
                if (vh instanceof ViewHolder) {
                    ViewHolder h = (ViewHolder) vh;
                    h.tvArabic.setText(ayahs.get(old).getArabic());
                    h.tvArabic.setTextColor(context.getResources().getColor(R.color.gold_light));
                    h.tvArabic.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                    h.tvArabic.setTextDirection(View.TEXT_DIRECTION_RTL);
                    h.tvArabic.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                    h.tvArabic.setGravity(android.view.Gravity.RIGHT);
                    return;
                }
            }
            notifyItemChanged(old);
        }
    }

    public static void applyKaraokeSpannable(TextView tv, String arabicText, int currentWordIndex) {
        if (tv == null || arabicText == null) return;
        tv.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        tv.setTextDirection(View.TEXT_DIRECTION_RTL);
        tv.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        tv.setGravity(android.view.Gravity.RIGHT);

        String[] words = arabicText.trim().split("\\s+");
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) ssb.append(" ");
            int start = ssb.length();
            ssb.append(words[i]);
            int end = ssb.length();

            if (i < currentWordIndex) {
                // Kata yang sudah dibaca: emas lembut
                ssb.setSpan(new ForegroundColorSpan(0xFFD4AF37), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (i == currentWordIndex) {
                // KATA YANG SEDANG DIBACA (KARAOKE HIGHLIGHT): Neon Cyan menyala + Background highlight
                ssb.setSpan(new ForegroundColorSpan(0xFF00FFD5), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new BackgroundColorSpan(0x4D00FFD5), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                // Kata berikutnya yang belum dibaca: agak redup
                ssb.setSpan(new ForegroundColorSpan(0x66D4AF37), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        tv.setText(ssb);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNumber, tvArabic, tvIndo;
        TextView btnPlay, btnKosakata, btnHafal, btnTulis, btnPelajari;

        ViewHolder(View itemView) {
            super(itemView);
            tvNumber = itemView.findViewById(R.id.tv_ayah_number);
            tvArabic = itemView.findViewById(R.id.tv_ayah_arabic);
            tvIndo = itemView.findViewById(R.id.tv_ayah_indo);

            btnPlay = itemView.findViewById(R.id.btn_ayah_play);
            btnKosakata = itemView.findViewById(R.id.btn_ayah_kosakata);
            btnHafal = itemView.findViewById(R.id.btn_ayah_hafal);
            btnTulis = itemView.findViewById(R.id.btn_ayah_tulis);
            btnPelajari = itemView.findViewById(R.id.btn_ayah_pelajari);
        }
    }
}
