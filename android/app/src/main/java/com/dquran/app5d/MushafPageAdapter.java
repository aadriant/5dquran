package com.dquran.app5d;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private boolean isWordByWordEnabled = false;
    private int karaokeAyahNumber = -1;
    private int karaokeWordIndex = -1;
    private final java.util.Set<Integer> karaokeErrorWordIndices = new java.util.HashSet<>();

    public MushafPageAdapter(Context context, SurahModel surah, List<MushafPageModel> pages, OnPageAyahListener listener) {
        this.context = context;
        this.surah = surah;
        this.pages = pages;
        this.listener = listener;
        this.isWordByWordEnabled = PreferenceManager.isWordByWord(context);
    }

    public void setWordByWordEnabled(boolean enabled) {
        this.isWordByWordEnabled = enabled;
        notifyDataSetChanged();
    }

    public boolean isWordByWordEnabled() {
        return isWordByWordEnabled;
    }

    public void setActiveAyah(int ayahNumber, boolean masked) {
        int oldAyah = this.activeAyahNumber;
        this.activeAyahNumber = ayahNumber;
        this.isAyahMasked = masked;
        if (oldAyah != ayahNumber) {
            int oldPage = findPositionForAyah(oldAyah);
            int newPage = findPositionForAyah(ayahNumber);
            if (oldPage == newPage && oldPage >= 0) {
                notifyItemChanged(newPage);
            } else {
                if (oldPage >= 0) notifyItemChanged(oldPage);
                if (newPage >= 0) notifyItemChanged(newPage);
            }
        }
    }

    public void scrollToAyah(RecyclerView rv, int ayahNumber) {
        scrollToAyah(rv, ayahNumber, false);
    }

    public void scrollToAyah(RecyclerView rv, int ayahNumber, boolean immediate) {
        if (rv == null || ayahNumber <= 0) return;
        int pagePos = findPositionForAyah(ayahNumber);
        if (pagePos < 0 || pagePos >= pages.size()) return;

        LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
        if (lm == null) return;

        if (rv.getHeight() <= 0) {
            rv.post(() -> scrollToAyah(rv, ayahNumber, immediate));
            return;
        }

        int rvHeight = rv.getHeight();
        int targetCenterY = rvHeight / 2;

        RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(pagePos);
        if (vh instanceof PageViewHolder) {
            PageViewHolder pvh = (PageViewHolder) vh;
            int ayahOffsetInPage = pvh.getAyahVerticalOffset(ayahNumber, isWordByWordEnabled);
            int currentItemTop = pvh.itemView.getTop();
            int currentAyahScreenTop = currentItemTop + ayahOffsetInPage;

            if (!immediate && currentAyahScreenTop >= rvHeight * 0.40f && currentAyahScreenTop <= rvHeight * 0.50f) {
                // Posisi ayat sudah nyaman di tengah layar, tidak perlu geser layar
                return;
            }

            int targetScrollY = currentAyahScreenTop - targetCenterY;
            if (immediate) {
                rv.scrollBy(0, targetScrollY);
            } else {
                rv.smoothScrollBy(0, targetScrollY);
            }
        } else {
            // Halaman target belum terlihat di viewport, posisikan halaman terlebih dahulu
            lm.scrollToPositionWithOffset(pagePos, 0);
            rv.postDelayed(() -> {
                RecyclerView.ViewHolder newVh = rv.findViewHolderForAdapterPosition(pagePos);
                if (newVh instanceof PageViewHolder) {
                    PageViewHolder pvh = (PageViewHolder) newVh;
                    int ayahOffsetInPage = pvh.getAyahVerticalOffset(ayahNumber, isWordByWordEnabled);
                    int currentItemTop = pvh.itemView.getTop();
                    int targetScrollY = currentItemTop + ayahOffsetInPage - targetCenterY;
                    if (immediate) {
                        rv.scrollBy(0, targetScrollY);
                    } else {
                        rv.smoothScrollBy(0, targetScrollY);
                    }
                }
            }, 80);
        }
    }

    public int getActiveAyahNumber() {
        return activeAyahNumber;
    }

    private void safeNotifyItemChanged(RecyclerView rv, int position) {
        if (position < 0 || position >= pages.size()) return;
        if (rv != null && rv.isComputingLayout()) {
            rv.post(() -> {
                try {
                    notifyItemChanged(position);
                } catch (Exception ignored) {}
            });
        } else {
            try {
                notifyItemChanged(position);
            } catch (Exception ignored) {}
        }
    }

    private long lastWordScrollTime = 0;

    public void scrollToActiveWord(RecyclerView rv, int ayahNumber, int wordIndex) {
        if (rv == null || ayahNumber <= 0 || wordIndex < 0) return;
        int pagePos = findPositionForAyah(ayahNumber);
        if (pagePos < 0 || pagePos >= pages.size()) return;

        RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(pagePos);
        if (!(vh instanceof PageViewHolder)) {
            LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
            if (lm != null) {
                lm.scrollToPositionWithOffset(pagePos, 0);
                rv.post(() -> scrollToActiveWord(rv, ayahNumber, wordIndex));
            }
            return;
        }

        PageViewHolder pvh = (PageViewHolder) vh;
        int wordYInPage = isWordByWordEnabled
                ? pvh.getWordVerticalOffsetWbw(ayahNumber, wordIndex)
                : pvh.getWordVerticalOffset(ayahNumber, wordIndex);

        int currentItemTop = pvh.itemView.getTop();
        int wordScreenY = currentItemTop + wordYInPage;

        int rvHeight = rv.getHeight();
        if (rvHeight <= 0) return;

        // Target: Letakkan kata tepat di tengah layar (~44% tinggi viewport, di atas footer murojaah)
        int targetCenterY = (int) (rvHeight * 0.44f);
        int scrollDelta = wordScreenY - targetCenterY;

        // Toleransi nyaman: jika kata berada di rentang tengah layar (36% - 52% tinggi layar),
        // tidak perlu scroll agar layar tenang saat membaca kata-kata pada baris yang sama.
        int minComfortY = (int) (rvHeight * 0.36f);
        int maxComfortY = (int) (rvHeight * 0.52f);

        if (wordScreenY >= minComfortY && wordScreenY <= maxComfortY) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastWordScrollTime < 250) {
            return;
        }
        lastWordScrollTime = now;

        rv.smoothScrollBy(0, scrollDelta);
    }

    public void setKaraokeHighlight(RecyclerView rv, int ayahNumber, int wordIndex) {
        setKaraokeHighlight(rv, ayahNumber, wordIndex, null);
    }

    public void setKaraokeHighlight(RecyclerView rv, int ayahNumber, int wordIndex, java.util.Set<Integer> errorWordIndices) {
        try {
            int oldAyahNum = this.karaokeAyahNumber;
            this.karaokeAyahNumber = ayahNumber;
            this.karaokeWordIndex = wordIndex;
            this.karaokeErrorWordIndices.clear();
            if (errorWordIndices != null) {
                this.karaokeErrorWordIndices.addAll(errorWordIndices);
            }

            if (oldAyahNum != -1 && oldAyahNum != ayahNumber && rv != null) {
                int oldPagePos = findPositionForAyah(oldAyahNum);
                RecyclerView.ViewHolder oldVh = rv.findViewHolderForAdapterPosition(oldPagePos);
                if (oldVh instanceof PageViewHolder) {
                    PageViewHolder pvh = (PageViewHolder) oldVh;
                    if (pvh.layoutPageWbwContainer != null) {
                        FlowLayout targetFlow = pvh.layoutPageWbwContainer.findViewWithTag("flow_ayah_" + oldAyahNum);
                        if (targetFlow != null) {
                            AyahAdapter.clearWbwHighlight(targetFlow, context);
                        }
                    }
                }
            }

            if (rv != null && ayahNumber > 0) {
                int pagePos = findPositionForAyah(ayahNumber);
                RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(pagePos);
                if (vh instanceof PageViewHolder) {
                    PageViewHolder pvh = (PageViewHolder) vh;
                    if (isWordByWordEnabled && pvh.layoutPageWbwContainer != null) {
                        FlowLayout targetFlow = pvh.layoutPageWbwContainer.findViewWithTag("flow_ayah_" + ayahNumber);
                        if (targetFlow != null) {
                            AyahAdapter.applyWbwHighlight(targetFlow, wordIndex, this.karaokeErrorWordIndices);
                        }
                    } else if (!isWordByWordEnabled) {
                        safeNotifyItemChanged(rv, pagePos);
                    }
                } else if (!isWordByWordEnabled) {
                    safeNotifyItemChanged(rv, pagePos);
                }

                // Letakkan kata yang sedang dibaca tepat di tengah layar saat murojaah suara user
                if (wordIndex >= 0) {
                    scrollToActiveWord(rv, ayahNumber, wordIndex);
                }
            }
        } catch (Exception ignored) {}
    }

    public void clearKaraokeHighlight(RecyclerView rv) {
        try {
            this.karaokeErrorWordIndices.clear();
            if (karaokeAyahNumber != -1) {
                int oldNum = karaokeAyahNumber;
                karaokeAyahNumber = -1;
                karaokeWordIndex = -1;
                if (rv != null) {
                    int pagePos = findPositionForAyah(oldNum);
                    RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(pagePos);
                    if (vh instanceof PageViewHolder) {
                        PageViewHolder pvh = (PageViewHolder) vh;
                        if (isWordByWordEnabled && pvh.layoutPageWbwContainer != null) {
                            FlowLayout targetFlow = pvh.layoutPageWbwContainer.findViewWithTag("flow_ayah_" + oldNum);
                            if (targetFlow != null) {
                                AyahAdapter.clearWbwHighlight(targetFlow, context);
                            }
                        } else if (!isWordByWordEnabled) {
                            safeNotifyItemChanged(rv, pagePos);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
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

        // 3. Quran Text Display (Word-by-Word or Continuous Quran Text)
        if (isWordByWordEnabled) {
            holder.tvPageArabicContent.setVisibility(View.GONE);
            if (holder.layoutPageWbwContainer != null) {
                holder.layoutPageWbwContainer.setVisibility(View.VISIBLE);
                renderPageWordByWord(holder.layoutPageWbwContainer, page);
            }
        } else {
            holder.tvPageArabicContent.setVisibility(View.VISIBLE);
            if (holder.layoutPageWbwContainer != null) {
                holder.layoutPageWbwContainer.setVisibility(View.GONE);
                holder.layoutPageWbwContainer.removeAllViews();
            }

            Typeface mushafTf = PreferenceManager.getMushafTypeface(context);
            holder.tvPageArabicContent.setTypeface(mushafTf);
            holder.tvPageArabicContent.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            holder.tvPageArabicContent.setTextDirection(View.TEXT_DIRECTION_RTL);
            holder.tvPageArabicContent.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            holder.tvPageArabicContent.setGravity(android.view.Gravity.RIGHT);

            float arabicFontSize = PreferenceManager.getArabicFontSize(context);
            if (arabicFontSize > 0) {
                holder.tvPageArabicContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(18f, arabicFontSize - 1f));
            }

            int goldCircle = context.getResources().getColor(R.color.gold_primary);
            int goldText = context.getResources().getColor(R.color.gold_light);

            SpannableStringBuilder ssb = new SpannableStringBuilder();
            holder.ayahCharOffsets.clear();

            for (AyahModel ayah : page.getAyahs()) {
                final AyahModel curAyah = ayah;
                int start = ssb.length();
                holder.ayahCharOffsets.put(ayah.getNumberInSurah(), start);

                boolean isCurrentActive = (ayah.getNumberInSurah() == activeAyahNumber);

                if (isCurrentActive && isAyahMasked) {
                    // Hidden/masked text placeholder for memory test
                    String maskText = "  [ ... Ayat " + ayah.getNumberInSurah() + " disembunyikan untuk menguji hafalan ... ]  ";
                    ssb.append(maskText);
                    int end = ssb.length();
                    ssb.setSpan(new ForegroundColorSpan(0xFF64748B), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.setSpan(new BackgroundColorSpan(0x22F0C060), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    String arText = ayah.getArabic();
                    ssb.append(arText);
                    int end = ssb.length();

                    if (ayah.getNumberInSurah() == karaokeAyahNumber && (karaokeWordIndex >= 0 || !karaokeErrorWordIndices.isEmpty())) {
                        // Highlight words with karaoke cyan and error red
                        int curToken = 0;
                        int idx = 0;
                        while (idx < arText.length()) {
                            while (idx < arText.length() && Character.isWhitespace(arText.charAt(idx))) idx++;
                            if (idx >= arText.length()) break;
                            int tStart = idx;
                            while (idx < arText.length() && !Character.isWhitespace(arText.charAt(idx))) idx++;
                            int tEnd = idx;

                            int spanS = Math.max(start, Math.min(start + tStart, end));
                            int spanE = Math.max(spanS, Math.min(start + tEnd, end));

                            if (spanE > spanS) {
                                boolean isErr = karaokeErrorWordIndices.contains(curToken);
                                if (isErr) {
                                    // WARNA MERAH UNTUK KATA YANG DILEWATI ATAU SALAH
                                    ssb.setSpan(new BackgroundColorSpan(0x4DEF4444), spanS, spanE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    ssb.setSpan(new ForegroundColorSpan(0xFFFF3333), spanS, spanE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    ssb.setSpan(new StyleSpan(Typeface.BOLD), spanS, spanE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                } else if (curToken == karaokeWordIndex) {
                                    // KATA YANG SEDANG DIBACA: NEON CYAN
                                    ssb.setSpan(new BackgroundColorSpan(0x6600FFD5), spanS, spanE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    ssb.setSpan(new ForegroundColorSpan(0xFF00FFD5), spanS, spanE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    ssb.setSpan(new StyleSpan(Typeface.BOLD), spanS, spanE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                } else if (curToken < karaokeWordIndex) {
                                    // KATA SEBELUMNYA: GOLD LEMBUT
                                    ssb.setSpan(new ForegroundColorSpan(0xFFD4AF37), spanS, spanE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                            }
                            curToken++;
                        }
                    } else if (isCurrentActive) {
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
    }

    private void renderPageWordByWord(LinearLayout container, MushafPageModel page) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        Typeface mushafTf = PreferenceManager.getMushafTypeface(context);
        float fontSize = PreferenceManager.getArabicFontSize(context);

        int surahNum = (surah != null) ? surah.getNumber() : 1;

        for (AyahModel ayah : page.getAyahs()) {
            final AyahModel curAyah = ayah;
            View ayahView = inflater.inflate(R.layout.item_mushaf_wbw_ayah, container, false);
            ayahView.setTag("ayah_row_" + ayah.getNumberInSurah());
            TextView tvNum = ayahView.findViewById(R.id.tv_mushaf_wbw_num);
            TextView tvMore = ayahView.findViewById(R.id.tv_mushaf_wbw_more);
            FlowLayout flowWords = ayahView.findViewById(R.id.flow_mushaf_wbw_words);
            flowWords.setTag("flow_ayah_" + ayah.getNumberInSurah());

            tvNum.setText(String.valueOf(ayah.getNumberInSurah()));

            boolean isCurrentActive = (ayah.getNumberInSurah() == activeAyahNumber);
            if (isCurrentActive) {
                ayahView.setBackgroundColor(0x2200FFD5);
            } else {
                ayahView.setBackground(null);
            }

            tvMore.setOnClickListener(v -> {
                if (listener != null) listener.onAyahClick(curAyah);
            });
            ayahView.setOnClickListener(v -> {
                if (listener != null) listener.onAyahClick(curAyah);
            });

            if (isCurrentActive && isAyahMasked) {
                TextView tvMask = new TextView(context);
                tvMask.setText("  [ ... Ayat " + ayah.getNumberInSurah() + " disembunyikan untuk menguji hafalan ... ]  ");
                tvMask.setTextColor(0xFF64748B);
                tvMask.setBackgroundColor(0x22F0C060);
                tvMask.setPadding(16, 16, 16, 16);
                flowWords.addView(tvMask);
            } else {
                int aNum = ayah.getNumberInSurah();
                List<WordModel> words = QuranRepository.getWordByWord(context, surahNum, aNum);

                if (words != null && !words.isEmpty()) {
                    for (WordModel w : words) {
                        View wordV = inflater.inflate(R.layout.item_wbw_reference, flowWords, false);
                        TextView tvAr = wordV.findViewById(R.id.tv_wbw_arabic);
                        TextView tvTr = wordV.findViewById(R.id.tv_wbw_meaning);

                        tvAr.setText(w.getArabic());
                        if (mushafTf != null) tvAr.setTypeface(mushafTf);
                        if (fontSize > 0) tvAr.setTextSize(fontSize);

                        String tr = w.getTranslation();
                        tvTr.setText((tr == null || tr.trim().isEmpty()) ? "-" : tr.trim());

                        wordV.setOnClickListener(clickV -> {
                            if (listener != null) listener.onAyahClick(curAyah);
                        });
                        flowWords.addView(wordV);
                    }
                } else {
                    String[] tokens = ayah.getArabic().trim().split("\\s+");
                    for (int i = 0; i < tokens.length; i++) {
                        String token = tokens[i].trim();
                        if (token.isEmpty()) continue;
                        View wordV = inflater.inflate(R.layout.item_wbw_reference, flowWords, false);
                        TextView tvAr = wordV.findViewById(R.id.tv_wbw_arabic);
                        TextView tvTr = wordV.findViewById(R.id.tv_wbw_meaning);
                        tvAr.setText(token);
                        if (mushafTf != null) tvAr.setTypeface(mushafTf);
                        if (fontSize > 0) tvAr.setTextSize(fontSize);
                        tvTr.setText("Kata " + (i + 1));
                        wordV.setOnClickListener(clickV -> {
                            if (listener != null) listener.onAyahClick(curAyah);
                        });
                        flowWords.addView(wordV);
                    }
                }
            }

            if (ayah.getNumberInSurah() == karaokeAyahNumber && (karaokeWordIndex >= 0 || !karaokeErrorWordIndices.isEmpty())) {
                AyahAdapter.applyWbwHighlight(flowWords, karaokeWordIndex, karaokeErrorWordIndices);
            }

            container.addView(ayahView);
        }
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
        LinearLayout layoutPageWbwContainer;
        final Map<Integer, Integer> ayahCharOffsets = new HashMap<>();

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
            layoutPageWbwContainer = itemView.findViewById(R.id.layout_page_wbw_container);
        }

        public int getViewTopRelativeToItem(View target) {
            if (target == null) return 0;
            int top = 0;
            View v = target;
            while (v != null && v != itemView) {
                top += v.getTop();
                if (v.getParent() instanceof View) {
                    v = (View) v.getParent();
                } else {
                    break;
                }
            }
            return top;
        }

        public int getAyahVerticalOffset(int ayahNumber, boolean isWbw) {
            if (isWbw && layoutPageWbwContainer != null) {
                View ayahRow = layoutPageWbwContainer.findViewWithTag("ayah_row_" + ayahNumber);
                if (ayahRow != null) {
                    int rowTop = getViewTopRelativeToItem(ayahRow);
                    int rowHeight = ayahRow.getHeight();
                    return rowTop + (rowHeight > 0 ? rowHeight / 2 : 0);
                }
            } else if (!isWbw && tvPageArabicContent != null) {
                Layout layout = tvPageArabicContent.getLayout();
                Integer charOffset = ayahCharOffsets.get(ayahNumber);
                if (layout != null && charOffset != null) {
                    try {
                        int contentTop = getViewTopRelativeToItem(tvPageArabicContent);
                        int line = layout.getLineForOffset(charOffset);
                        int lineMid = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2;
                        return contentTop + lineMid;
                    } catch (Exception ignored) {}
                }
            }
            return 0;
        }

        public int getWordVerticalOffset(int ayahNumber, int wordIndex) {
            if (tvPageArabicContent != null) {
                Layout layout = tvPageArabicContent.getLayout();
                Integer startChar = ayahCharOffsets.get(ayahNumber);
                CharSequence fullText = tvPageArabicContent.getText();
                if (layout != null && startChar != null && fullText != null && startChar >= 0 && startChar < fullText.length()) {
                    try {
                        if (wordIndex <= 0) {
                            int line = layout.getLineForOffset(startChar);
                            int lineCenter = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2;
                            return tvPageArabicContent.getTop() + lineCenter;
                        }

                        int curToken = 0;
                        int idx = startChar;
                        int wStart = -1;
                        int textLen = fullText.length();

                        while (idx < textLen) {
                            while (idx < textLen && Character.isWhitespace(fullText.charAt(idx))) idx++;
                            if (idx >= textLen) break;
                            int tStart = idx;
                            while (idx < textLen && !Character.isWhitespace(fullText.charAt(idx))) idx++;
                            if (curToken == wordIndex) {
                                wStart = tStart;
                                break;
                            }
                            curToken++;
                        }

                        if (wStart >= 0 && wStart < layout.getText().length()) {
                            int line = layout.getLineForOffset(wStart);
                            int lineCenter = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2;
                            return tvPageArabicContent.getTop() + lineCenter;
                        }
                    } catch (Exception ignored) {}
                }
            }
            return getAyahVerticalOffset(ayahNumber, false);
        }

        public int getWordVerticalOffsetWbw(int ayahNumber, int wordIndex) {
            if (layoutPageWbwContainer != null) {
                try {
                    View ayahRow = layoutPageWbwContainer.findViewWithTag("ayah_row_" + ayahNumber);
                    if (ayahRow != null) {
                        int rowTop = layoutPageWbwContainer.getTop() + ayahRow.getTop();
                        FlowLayout flowWords = ayahRow.findViewWithTag("flow_ayah_" + ayahNumber);
                        if (flowWords != null) {
                            int childCount = flowWords.getChildCount();
                            if (childCount > 0) {
                                int targetIndex = Math.min(Math.max(0, wordIndex), childCount - 1);
                                View wordChild = flowWords.getChildAt(targetIndex);
                                if (wordChild != null) {
                                    int wordMidY = wordChild.getTop() + (wordChild.getHeight() / 2);
                                    return rowTop + flowWords.getTop() + wordMidY;
                                }
                            }
                            return rowTop + flowWords.getTop();
                        }
                        return rowTop;
                    }
                } catch (Exception ignored) {}
            }
            return getAyahVerticalOffset(ayahNumber, true);
        }

        public int findAyahAtViewportY(RecyclerView rv, List<AyahModel> pageAyahs, boolean isWbw) {
            if (pageAyahs == null || pageAyahs.isEmpty() || rv == null) return -1;
            int cardTop = itemView.getTop();
            int rvHeight = rv.getHeight();
            int targetY = rvHeight > 0 ? (int) (rvHeight * 0.38f) : 0;

            int bestAyah = pageAyahs.get(0).getNumberInSurah();
            for (AyahModel a : pageAyahs) {
                int ayahOffset = getAyahVerticalOffset(a.getNumberInSurah(), isWbw);
                int screenY = cardTop + ayahOffset;
                if (screenY <= targetY) {
                    bestAyah = a.getNumberInSurah();
                } else {
                    if (bestAyah == pageAyahs.get(0).getNumberInSurah() && screenY < rvHeight) {
                        bestAyah = a.getNumberInSurah();
                    }
                    break;
                }
            }
            return bestAyah;
        }
    }
}
