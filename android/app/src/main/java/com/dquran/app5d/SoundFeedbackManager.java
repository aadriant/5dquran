package com.dquran.app5d;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

/**
 * SoundFeedbackManager
 * Mengatur audio feedback untuk Murojaah sesuai spesifikasi:
 * - Start mendengarkan / start: nada naik lembut 2-tone
 * - Stop / pause: nada turun penutup 2-tone
 * - Salah / terlewati: nada peringatan frekuensi rendah (alert buzz)
 * - Selain event di atas (misal kata benar atau restart internal), tidak ada suara.
 */
public class SoundFeedbackManager {

    private static final int SAMPLE_RATE = 44100;
    private static long lastErrorSoundTime = 0;

    /**
     * Suara saat start mendengarkan / start murojaah (nada naik lembut C5 -> E5)
     */
    public static void playStartSound() {
        playTones(new int[]{523, 659}, new int[]{80, 110}, 0.75f, false);
    }

    /**
     * Suara saat stop / pause murojaah (nada turun penutup E5 -> A4)
     */
    public static void playStopSound() {
        playTones(new int[]{659, 440}, new int[]{70, 110}, 0.65f, false);
    }

    /**
     * Suara berbeda tanda salah atau terlewati (nada peringatan rendah 210Hz + harmoni)
     */
    public static void playWrongSound() {
        long now = System.currentTimeMillis();
        if (now - lastErrorSoundTime < 600) return; // Debounce agar tidak bertumpukan
        lastErrorSoundTime = now;

        playTones(new int[]{210, 0, 180}, new int[]{100, 25, 140}, 0.90f, true);
    }

    private static void playTones(int[] frequencies, int[] durationsMs, float volume, boolean isError) {
        new Thread(() -> {
            try {
                int totalSamples = 0;
                for (int d : durationsMs) {
                    totalSamples += (SAMPLE_RATE * d) / 1000;
                }
                short[] buffer = new short[totalSamples];
                int offset = 0;

                for (int i = 0; i < frequencies.length; i++) {
                    int freq = frequencies[i];
                    int segSamples = (SAMPLE_RATE * durationsMs[i]) / 1000;
                    for (int s = 0; s < segSamples; s++) {
                        if (freq <= 0) {
                            buffer[offset++] = 0;
                        } else {
                            double t = (double) s / SAMPLE_RATE;
                            // Linear attack and decay envelope to eliminate clicking/popping noise
                            double envelope = 1.0;
                            double attack = Math.min(segSamples * 0.15, SAMPLE_RATE * 0.015);
                            double decay = Math.min(segSamples * 0.35, SAMPLE_RATE * 0.05);
                            if (s < attack) {
                                envelope = (double) s / attack;
                            } else if (s > segSamples - decay) {
                                envelope = (double) (segSamples - s) / decay;
                            }

                            double wave;
                            if (isError) {
                                // Rich alert waveform (fundamental + harmonic)
                                wave = 0.65 * Math.sin(2 * Math.PI * freq * t) + 0.35 * Math.sin(4 * Math.PI * freq * t);
                            } else {
                                // Pure pleasant sine tone
                                wave = Math.sin(2 * Math.PI * freq * t);
                            }
                            buffer[offset++] = (short) (wave * envelope * volume * 32767);
                        }
                    }
                }

                AudioTrack track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(buffer.length * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();

                track.write(buffer, 0, buffer.length);
                track.play();

                long durationMs = (totalSamples * 1000L / SAMPLE_RATE) + 120;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        track.stop();
                        track.release();
                    } catch (Exception ignored) {}
                }, durationMs);

            } catch (Throwable t) {
                t.printStackTrace();
            }
        }).start();
    }
}
