package com.dquran.app5d;

import java.util.ArrayList;
import java.util.List;

public class RecitationEvaluator {

    public static class WordDiff {
        public final String targetWord;
        public final boolean isCorrect;
        public final boolean isMissing;

        public WordDiff(String targetWord, boolean isCorrect, boolean isMissing) {
            this.targetWord = targetWord;
            this.isCorrect = isCorrect;
            this.isMissing = isMissing;
        }
    }

    public static class EvaluationResult {
        public final int totalWords;
        public final int correctWords;
        public final int wrongWords;
        public final int missingWords;
        public final float accuracy;
        public final boolean isPerfect;
        public final List<WordDiff> diffs;
        public final String spokenText;

        public EvaluationResult(int totalWords, int correctWords, int wrongWords, int missingWords,
                                float accuracy, boolean isPerfect, List<WordDiff> diffs, String spokenText) {
            this.totalWords = totalWords;
            this.correctWords = correctWords;
            this.wrongWords = wrongWords;
            this.missingWords = missingWords;
            this.accuracy = accuracy;
            this.isPerfect = isPerfect;
            this.diffs = diffs;
            this.spokenText = spokenText;
        }
    }

    public static String normAr(String text) {
        if (text == null) return "";
        // Remove harakat / Quranic diacritics
        String s = text.replaceAll("[\u064B-\u065F\u0670\u06D6-\u06ED\u0640]", "");
        // Normalize alef variants → ا
        s = s.replaceAll("[\u0623\u0625\u0622\u0671]", "\u0627");
        // ى , ئ → ي
        s = s.replaceAll("[\u0649\u0626]", "\u064A");
        // ة → ه
        s = s.replace('\u0629', '\u0647');
        // ؤ → و
        s = s.replace('\u0624', '\u0648');
        // Keep only Arabic letters + whitespace
        s = s.replaceAll("[^\u0621-\u064A\\s]", "");
        return s.trim();
    }

    public static int levenshtein(String a, String b) {
        int la = a.length(), lb = b.length();
        int[][] dp = new int[la + 1][lb + 1];
        for (int i = 0; i <= la; i++) dp[i][0] = i;
        for (int j = 0; j <= lb; j++) dp[0][j] = j;
        for (int i = 1; i <= la; i++) {
            for (int j = 1; j <= lb; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }
        return dp[la][lb];
    }

    public static float similarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) return 1f;
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1f;
        return 1f - (float) levenshtein(a, b) / maxLen;
    }

    public static EvaluationResult evaluate(String targetArabic, String spokenArabic) {
        if (targetArabic == null) targetArabic = "";
        if (spokenArabic == null) spokenArabic = "";

        String[] rawTargetWords = targetArabic.trim().split("\\s+");
        String[] targetNorm = new String[rawTargetWords.length];
        for (int i = 0; i < rawTargetWords.length; i++) {
            targetNorm[i] = normAr(rawTargetWords[i]);
        }

        String[] rawSpokenWords = spokenArabic.trim().isEmpty() ? new String[0] : spokenArabic.trim().split("\\s+");
        String[] spokenNorm = new String[rawSpokenWords.length];
        for (int j = 0; j < rawSpokenWords.length; j++) {
            spokenNorm[j] = normAr(rawSpokenWords[j]);
        }

        boolean[] spokenUsed = new boolean[spokenNorm.length];
        boolean[] targetMatched = new boolean[targetNorm.length];
        int correctCount = 0;

        int searchWindow = 4;
        for (int i = 0; i < targetNorm.length; i++) {
            String tw = targetNorm[i];
            if (tw.isEmpty()) {
                targetMatched[i] = true;
                continue;
            }
            int startJ = Math.max(0, i - searchWindow);
            int endJ = Math.min(spokenNorm.length, i + searchWindow + 1);
            int bestJ = -1;
            float bestSim = 0f;

            for (int j = startJ; j < endJ; j++) {
                if (!spokenUsed[j]) {
                    float sim = similarity(tw, spokenNorm[j]);
                    if (sim > bestSim && sim >= 0.72f) {
                        bestSim = sim;
                        bestJ = j;
                    }
                }
            }

            if (bestJ != -1) {
                targetMatched[i] = true;
                spokenUsed[bestJ] = true;
                correctCount++;
            }
        }

        int missingCount = 0;
        List<WordDiff> diffs = new ArrayList<>();
        for (int i = 0; i < targetNorm.length; i++) {
            if (targetMatched[i]) {
                diffs.add(new WordDiff(rawTargetWords[i], true, false));
            } else {
                missingCount++;
                diffs.add(new WordDiff(rawTargetWords[i], false, true));
            }
        }

        int wrongCount = 0;
        for (boolean used : spokenUsed) {
            if (!used) wrongCount++;
        }

        int total = rawTargetWords.length;
        float accuracy = total > 0 ? ((float) correctCount / total * 100f) : 0f;
        boolean isPerfect = (wrongCount == 0 && missingCount == 0);

        return new EvaluationResult(total, correctCount, wrongCount, missingCount, accuracy, isPerfect, diffs, spokenArabic);
    }

    public static int findSpokenWordIndex(String targetArabic, String spokenArabic, int previousMatchedIndex) {
        if (targetArabic == null || spokenArabic == null) return -1;
        String[] rawTargetWords = targetArabic.trim().split("\\s+");
        if (rawTargetWords.length == 0) return -1;

        String[] targetNorm = new String[rawTargetWords.length];
        for (int i = 0; i < rawTargetWords.length; i++) {
            targetNorm[i] = normAr(rawTargetWords[i]);
        }

        String[] rawSpokenWords = spokenArabic.trim().isEmpty() ? new String[0] : spokenArabic.trim().split("\\s+");
        if (rawSpokenWords.length == 0) return -1;

        // Ambil token kata spoken terakhir yang diucapkan
        String lastSpoken = normAr(rawSpokenWords[rawSpokenWords.length - 1]);
        if (lastSpoken.isEmpty() && rawSpokenWords.length > 1) {
            lastSpoken = normAr(rawSpokenWords[rawSpokenWords.length - 2]);
        }
        if (lastSpoken.isEmpty()) return -1;

        // 1. Cari di sekitar previousMatchedIndex ke depan
        int startSearch = Math.max(0, previousMatchedIndex);
        int endSearch = Math.min(targetNorm.length, startSearch + 5);

        int bestIndex = -1;
        float bestSim = 0f;

        for (int i = startSearch; i < endSearch; i++) {
            float sim = similarity(targetNorm[i], lastSpoken);
            if (sim > bestSim && sim >= 0.62f) {
                bestSim = sim;
                bestIndex = i;
            }
        }

        // 2. Jika tidak ditemukan, cari di seluruh kata target
        if (bestIndex == -1) {
            for (int i = 0; i < targetNorm.length; i++) {
                float sim = similarity(targetNorm[i], lastSpoken);
                if (sim > bestSim && sim >= 0.68f) {
                    bestSim = sim;
                    bestIndex = i;
                }
            }
        }

        return bestIndex;
    }
}
