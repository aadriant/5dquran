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
    private final int surahNumber;
    private final List<AyahModel> ayahs;
    private final OnAyahActionListener listener;
    private final float arabicFontSize;
    private boolean isWordByWordEnabled = false;

    // Karaoke state
    private int karaokeAyahIndex = -1;
    private int karaokeWordIndex = -1;

    public AyahAdapter(Context context, int surahNumber, List<AyahModel> ayahs, float fontSize, OnAyahActionListener listener) {
        this.context = context;
        this.surahNumber = surahNumber;
        this.ayahs = ayahs;
        this.arabicFontSize = fontSize;
        this.listener = listener;
        this.isWordByWordEnabled = PreferenceManager.isWordByWord(context);
    }

    public AyahAdapter(Context context, List<AyahModel> ayahs, float fontSize, OnAyahActionListener listener) {
        this(context, 1, ayahs, fontSize, listener);
    }

    public void setWordByWordEnabled(boolean enabled) {
        this.isWordByWordEnabled = enabled;
        notifyDataSetChanged();
    }

    public boolean isWordByWordEnabled() {
        return isWordByWordEnabled;
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

        if (isWordByWordEnabled) {
            holder.tvArabic.setVisibility(View.GONE);
            if (holder.flowWbw != null) {
                holder.flowWbw.setVisibility(View.VISIBLE);
                renderWordByWord(holder.flowWbw, a);
                if (position == karaokeAyahIndex && karaokeWordIndex >= 0) {
                    applyWbwHighlight(holder.flowWbw, karaokeWordIndex);
                }
            }
        } else {
            holder.tvArabic.setVisibility(View.VISIBLE);
            if (holder.flowWbw != null) {
                holder.flowWbw.setVisibility(View.GONE);
                holder.flowWbw.removeAllViews();
            }

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
        holder.btnHafal.setOnClickListener(v -> listener.onAction(a, "hafal"));
        holder.btnTulis.setOnClickListener(v -> listener.onAction(a, "tulis"));
        holder.btnPelajari.setOnClickListener(v -> listener.onAction(a, "pelajari"));
    }

    private void renderWordByWord(FlowLayout flow, AyahModel a) {
        flow.removeAllViews();
        int aNum = a.getNumberInSurah();
        List<WordModel> words = QuranRepository.getWordByWord(context, surahNumber, aNum);
        LayoutInflater inflater = LayoutInflater.from(context);
        Typeface tf = PreferenceManager.getMushafTypeface(context);

        if (words != null && !words.isEmpty()) {
            for (WordModel w : words) {
                View v = inflater.inflate(R.layout.item_wbw_reference, flow, false);
                TextView tvAr = v.findViewById(R.id.tv_wbw_arabic);
                TextView tvTr = v.findViewById(R.id.tv_wbw_meaning);

                tvAr.setText(w.getArabic());
                if (tf != null) tvAr.setTypeface(tf);
                if (arabicFontSize > 0) tvAr.setTextSize(arabicFontSize);

                String tr = w.getTranslation();
                tvTr.setText((tr == null || tr.trim().isEmpty()) ? "-" : tr.trim());

                v.setOnClickListener(clickV -> {
                    if (listener != null) listener.onAction(a, "pelajari");
                });
                flow.addView(v);
            }
        } else {
            // Fallback token split
            String[] tokens = a.getArabic().trim().split("\\s+");
            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i].trim();
                if (token.isEmpty()) continue;
                View v = inflater.inflate(R.layout.item_wbw_reference, flow, false);
                TextView tvAr = v.findViewById(R.id.tv_wbw_arabic);
                TextView tvTr = v.findViewById(R.id.tv_wbw_meaning);
                tvAr.setText(token);
                if (tf != null) tvAr.setTypeface(tf);
                if (arabicFontSize > 0) tvAr.setTextSize(arabicFontSize);
                tvTr.setText("Kata " + (i + 1));
                v.setOnClickListener(clickV -> {
                    if (listener != null) listener.onAction(a, "pelajari");
                });
                flow.addView(v);
            }
        }
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
                    if (isWordByWordEnabled) {
                        clearWbwHighlight(h.flowWbw, context);
                    } else {
                        h.tvArabic.setText(ayahs.get(oldAyahIndex).getArabic());
                        h.tvArabic.setTextColor(context.getResources().getColor(R.color.gold_light));
                        h.tvArabic.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                        h.tvArabic.setTextDirection(View.TEXT_DIRECTION_RTL);
                        h.tvArabic.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                        h.tvArabic.setGravity(android.view.Gravity.RIGHT);
                    }
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
                if (isWordByWordEnabled) {
                    applyWbwHighlight(h.flowWbw, wordIndex);
                } else {
                    applyKaraokeSpannable(h.tvArabic, ayahs.get(ayahIndex).getArabic(), wordIndex);
                }
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
                    if (isWordByWordEnabled) {
                        clearWbwHighlight(h.flowWbw, context);
                    } else {
                        h.tvArabic.setText(ayahs.get(old).getArabic());
                        h.tvArabic.setTextColor(context.getResources().getColor(R.color.gold_light));
                        h.tvArabic.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                        h.tvArabic.setTextDirection(View.TEXT_DIRECTION_RTL);
                        h.tvArabic.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                        h.tvArabic.setGravity(android.view.Gravity.RIGHT);
                    }
                    return;
                }
            }
            notifyItemChanged(old);
        }
    }

    public static void applyWbwHighlight(FlowLayout flow, int currentWordIndex) {
        if (flow == null) return;
        int count = flow.getChildCount();
        for (int i = 0; i < count; i++) {
            View wordV = flow.getChildAt(i);
            TextView tvAr = wordV.findViewById(R.id.tv_wbw_arabic);
            TextView tvTr = wordV.findViewById(R.id.tv_wbw_meaning);

            if (i == currentWordIndex) {
                wordV.setBackgroundResource(R.drawable.bg_word_karaoke_active);
                if (tvAr != null) {
                    tvAr.setTextColor(0xFF00FFD5);
                    tvAr.setTypeface(null, Typeface.BOLD);
                }
                if (tvTr != null) {
                    tvTr.setTextColor(0xFFFFFFFF);
                    tvTr.setTypeface(null, Typeface.BOLD);
                }
            } else if (i < currentWordIndex) {
                wordV.setBackground(null);
                if (tvAr != null) {
                    tvAr.setTextColor(0xFFD4AF37); // warm gold
                    tvAr.setTypeface(null, Typeface.NORMAL);
                }
                if (tvTr != null) {
                    tvTr.setTextColor(0xFF94A3B8);
                    tvTr.setTypeface(null, Typeface.NORMAL);
                }
            } else {
                wordV.setBackground(null);
                if (tvAr != null) {
                    tvAr.setTextColor(0x88D4AF37); // dimmed gold
                    tvAr.setTypeface(null, Typeface.NORMAL);
                }
                if (tvTr != null) {
                    tvTr.setTextColor(0x5594A3B8); // dimmed meaning
                    tvTr.setTypeface(null, Typeface.NORMAL);
                }
            }
        }
    }

    public static void clearWbwHighlight(FlowLayout flow, Context context) {
        if (flow == null) return;
        int count = flow.getChildCount();
        int normalGold = context != null ? context.getResources().getColor(R.color.gold_light) : 0xFFFDE68A;
        for (int i = 0; i < count; i++) {
            View wordV = flow.getChildAt(i);
            wordV.setBackground(null);
            TextView tvAr = wordV.findViewById(R.id.tv_wbw_arabic);
            TextView tvTr = wordV.findViewById(R.id.tv_wbw_meaning);
            if (tvAr != null) {
                tvAr.setTextColor(normalGold);
                tvAr.setTypeface(null, Typeface.NORMAL);
            }
            if (tvTr != null) {
                tvTr.setTextColor(0xFF94A3B8);
                tvTr.setTypeface(null, Typeface.NORMAL);
            }
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
        FlowLayout flowWbw;
        TextView btnPlay, btnHafal, btnTulis, btnPelajari;

        ViewHolder(View itemView) {
            super(itemView);
            tvNumber = itemView.findViewById(R.id.tv_ayah_number);
            tvArabic = itemView.findViewById(R.id.tv_ayah_arabic);
            flowWbw  = itemView.findViewById(R.id.flow_ayah_wbw);
            tvIndo   = itemView.findViewById(R.id.tv_ayah_indo);

            btnPlay = itemView.findViewById(R.id.btn_ayah_play);
            btnHafal = itemView.findViewById(R.id.btn_ayah_hafal);
            btnTulis = itemView.findViewById(R.id.btn_ayah_tulis);
            btnPelajari = itemView.findViewById(R.id.btn_ayah_pelajari);
        }
    }
}
