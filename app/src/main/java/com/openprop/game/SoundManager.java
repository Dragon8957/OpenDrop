package com.openprop.game;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SoundManager {
    public volatile boolean enabled = true;
    public volatile boolean isInForeground = true;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final int sampleRate = 44100;
    private final Random random = new Random();
    private int comboCounter = 0;

    // Background Music Track
    private AudioTrack bgmTrack = null;
    private Thread bgmThread = null;
    private volatile boolean isBgmRunning = false;
    private int currentTrackIndex = 0;

    // Harmonious pentatonic scale frequencies (C5, D5, E5, G5, A5, C6, D6, E6)
    private final float[] pentatonicNotes = new float[]{
            523.25f, 587.33f, 659.25f, 783.99f, 880.00f, 1046.50f, 1174.66f, 1318.51f
    };

    public SoundManager() {
        startBackgroundMusic();
    }

    public void onPause() {
        isInForeground = false;
        if (bgmTrack != null) {
            try {
                if (bgmTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    bgmTrack.pause();
                }
            } catch (Exception ignored) {}
        }
    }

    public void onResume() {
        isInForeground = true;
        if (bgmTrack != null && enabled) {
            try {
                if (bgmTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                    bgmTrack.play();
                }
            } catch (Exception ignored) {}
        }
    }

    public void nextTrack() {
        currentTrackIndex = (currentTrackIndex + 1) % 3;
        stopBackgroundMusic();
        startBackgroundMusic();
    }

    public void startBackgroundMusic() {
        if (isBgmRunning) return;
        isBgmRunning = true;
        bgmThread = new Thread(new Runnable() {
            @Override
            public void run() {
                generateAndPlayBGM();
            }
        });
        bgmThread.setPriority(Thread.MIN_PRIORITY);
        bgmThread.start();
    }

    public void stopBackgroundMusic() {
        isBgmRunning = false;
        if (bgmTrack != null) {
            try {
                bgmTrack.stop();
                bgmTrack.release();
            } catch (Exception ignored) {}
            bgmTrack = null;
        }
    }

    public void onDestroy() {
        stopBackgroundMusic();
        executor.shutdownNow();
    }

    // Dynamic Multi-Track Procedural Synthwave BGM Generator
    private void generateAndPlayBGM() {
        final int bgmSampleRate = 22050;
        final float loopDuration = 12.0f; // 12-second rich seamless loop
        final int totalSamples = (int) (bgmSampleRate * loopDuration);
        short[] buffer = new short[totalSamples];

        if (currentTrackIndex == 0) {
            // Track 1: Cyberpunk Driving Synthwave (Em - C - G - D progression)
            float[][] chords = new float[][]{
                    {82.41f, 123.47f, 164.81f}, // Em
                    {65.41f, 130.81f, 164.81f}, // C
                    {98.00f, 123.47f, 146.83f}, // G
                    {73.42f, 110.00f, 146.83f}  // D
            };
            float[] leadNotes = new float[]{
                    329.63f, 392.00f, 493.88f, 587.33f, 493.88f, 392.00f,
                    261.63f, 329.63f, 392.00f, 523.25f, 392.00f, 329.63f,
                    392.00f, 493.88f, 587.33f, 783.99f, 587.33f, 493.88f,
                    293.66f, 369.99f, 440.00f, 587.33f, 440.00f, 369.99f
            };
            float chordLen = loopDuration / 4.0f;

            for (int i = 0; i < totalSamples; i++) {
                float t = (float) i / bgmSampleRate;
                int chordIdx = Math.min(3, (int) (t / chordLen));
                float chordT = t - chordIdx * chordLen;
                float[] curChord = chords[chordIdx];

                // 1. Driving Sawtooth/Square Bassline
                float bassFreq = curChord[0];
                float bassBeat = (t * 4.0f) % 1.0f;
                float bassEnv = (float) Math.exp(-bassBeat * 9.0);
                double bass = (Math.sin(2.0 * Math.PI * bassFreq * t) +
                        0.5 * Math.sin(2.0 * Math.PI * bassFreq * 2.0 * t)) * bassEnv * 0.28;

                // 2. Warm Atmosphere Chords
                double pad = 0;
                float padEnv = (float) Math.sin((chordT / chordLen) * Math.PI);
                for (float f : curChord) {
                    pad += Math.sin(2.0 * Math.PI * f * 2.0 * t) * 0.25;
                    pad += Math.sin(2.0 * Math.PI * (f * 2.006) * t) * 0.15;
                }
                pad *= padEnv * 0.16;

                // 3. Cyberpunk Lead Plucks
                int leadIdx = (int) (t * 3.0f) % leadNotes.length;
                float leadT = (t * 3.0f) - (int) (t * 3.0f);
                float leadEnv = (float) Math.exp(-leadT * 8.0);
                double lead = Math.sin(2.0 * Math.PI * leadNotes[leadIdx] * t) * leadEnv * 0.14;

                double mixed = (bass + pad + lead) * 0.72;
                buffer[i] = (short) (Math.max(-1.0, Math.min(1.0, mixed)) * 32767);
            }
        } else if (currentTrackIndex == 1) {
            // Track 2: Neon Chillwave (Am - F - C - G ambient dreamwave)
            float[][] chords = new float[][]{
                    {110.00f, 130.81f, 164.81f}, // Am
                    {87.31f, 110.00f, 130.81f},  // F
                    {130.81f, 164.81f, 196.00f}, // C
                    {98.00f, 123.47f, 146.83f}   // G
            };
            float[] arpNotes = new float[]{
                    440.00f, 523.25f, 659.25f, 783.99f, 659.25f, 523.25f,
                    349.23f, 440.00f, 523.25f, 659.25f, 523.25f, 440.00f,
                    523.25f, 659.25f, 783.99f, 987.77f, 783.99f, 659.25f,
                    392.00f, 493.88f, 587.33f, 783.99f, 587.33f, 493.88f
            };
            float chordLen = loopDuration / 4.0f;

            for (int i = 0; i < totalSamples; i++) {
                float t = (float) i / bgmSampleRate;
                int chordIdx = Math.min(3, (int) (t / chordLen));
                float chordT = t - chordIdx * chordLen;
                float[] curChord = chords[chordIdx];

                double pad = 0;
                float padEnv = (float) Math.sin((chordT / chordLen) * Math.PI);
                for (float f : curChord) {
                    pad += Math.sin(2.0 * Math.PI * f * t) * 0.45;
                    pad += Math.sin(2.0 * Math.PI * (f * 1.004) * t) * 0.25;
                    pad += Math.sin(2.0 * Math.PI * (f * 0.5) * t) * 0.35;
                }
                pad *= padEnv * 0.18;

                int arpIdx = (int) (t * 2.0f) % arpNotes.length;
                float arpT = (t * 2.0f) - (int) (t * 2.0f);
                float arpEnv = (float) Math.exp(-arpT * 7.0);
                double arp = Math.sin(2.0 * Math.PI * arpNotes[arpIdx] * t) * arpEnv * 0.12;

                float beatT = t % 1.0f;
                float pulseEnv = (float) Math.exp(-beatT * 14.0);
                double subPulse = Math.sin(2.0 * Math.PI * 55.0 * t) * pulseEnv * 0.15;

                double mixed = (pad + arp + subPulse) * 0.70;
                buffer[i] = (short) (Math.max(-1.0, Math.min(1.0, mixed)) * 32767);
            }
        } else {
            // Track 3: Deep Space Odyssey (Dm - Bb - F - C atmospheric dark synth)
            float[][] chords = new float[][]{
                    {73.42f, 110.00f, 146.83f}, // Dm
                    {58.27f, 116.54f, 146.83f}, // Bb
                    {87.31f, 130.81f, 174.61f}, // F
                    {65.41f, 130.81f, 164.81f}  // C
            };
            float chordLen = loopDuration / 4.0f;

            for (int i = 0; i < totalSamples; i++) {
                float t = (float) i / bgmSampleRate;
                int chordIdx = Math.min(3, (int) (t / chordLen));
                float chordT = t - chordIdx * chordLen;
                float[] curChord = chords[chordIdx];

                double drone = 0;
                for (float f : curChord) {
                    drone += Math.sin(2.0 * Math.PI * f * t) * 0.35;
                    drone += Math.sin(2.0 * Math.PI * (f * 1.002) * t) * 0.25;
                }
                drone *= 0.15;

                // Pulsing sub
                float subT = (t * 2.0f) % 1.0f;
                double sub = Math.sin(2.0 * Math.PI * 45.0 * t) * Math.exp(-subT * 6.0) * 0.22;

                // High shimmer
                float shimmerT = (t * 4.0f) % 1.0f;
                double shimmer = Math.sin(2.0 * Math.PI * (curChord[1] * 4.0) * t) * Math.exp(-shimmerT * 12.0) * 0.08;

                double mixed = (drone + sub + shimmer) * 0.72;
                buffer[i] = (short) (Math.max(-1.0, Math.min(1.0, mixed)) * 32767);
            }
        }

        try {
            bgmTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(bgmSampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(buffer.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();

            bgmTrack.write(buffer, 0, buffer.length);
            bgmTrack.setLoopPoints(0, totalSamples, -1);

            if (enabled && isInForeground) {
                bgmTrack.play();
            }

            while (isBgmRunning) {
                if (bgmTrack != null) {
                    boolean shouldPlay = enabled && isInForeground;
                    if (shouldPlay && bgmTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                        bgmTrack.play();
                    } else if (!shouldPlay && bgmTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        bgmTrack.pause();
                    }
                }
                Thread.sleep(150);
            }
        } catch (Exception ignored) {}
    }

    public void playGem(int combo) {
        if (!enabled || !isInForeground) return;
        final float freq = pentatonicNotes[(comboCounter++) % pentatonicNotes.length];
        executor.execute(new Runnable() {
            @Override
            public void run() {
                playMarimbaTone(freq, 0.16f, 0.36f);
            }
        });
    }

    public void playPowerup() {
        if (!enabled || !isInForeground) return;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                playChordArpeggio(new float[]{523.25f, 659.25f, 783.99f, 1046.50f}, 0.05f, 0.28f);
            }
        });
    }

    public void playShieldBreak() {
        if (!enabled || !isInForeground) return;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                playResonantPing(380f, 0.22f, 0.35f);
            }
        });
    }

    public void playGameOver() {
        if (!enabled || !isInForeground) return;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                playSoftSubBassDrop(140f, 35f, 0.40f, 0.42f);
            }
        });
    }

    public void playClick() {
        if (!enabled || !isInForeground) return;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                playSoftPop(780f, 0.024f, 0.22f);
            }
        });
    }

    private void playMarimbaTone(float baseFreq, float duration, float volume) {
        int numSamples = (int) (sampleRate * duration);
        short[] buffer = new short[numSamples];
        for (int i = 0; i < numSamples; i++) {
            float t = (float) i / sampleRate;
            float env = (float) Math.exp(-t * 18.0);
            double sample = (Math.sin(2.0 * Math.PI * baseFreq * t) +
                    0.35 * Math.sin(2.0 * Math.PI * (baseFreq * 2.0) * t)) * env * volume;
            buffer[i] = (short) (Math.max(-1.0, Math.min(1.0, sample)) * 32767);
        }
        playAudioBuffer(buffer);
    }

    private void playChordArpeggio(float[] freqs, float noteDur, float volume) {
        int totalSamples = (int) (sampleRate * (freqs.length * noteDur + 0.15f));
        short[] buffer = new short[totalSamples];
        for (int idx = 0; idx < freqs.length; idx++) {
            float f = freqs[idx];
            int startSample = (int) (idx * noteDur * sampleRate);
            for (int i = startSample; i < totalSamples; i++) {
                float t = (float) (i - startSample) / sampleRate;
                float env = (float) Math.exp(-t * 9.0);
                double sample = Math.sin(2.0 * Math.PI * f * t) * env * volume * 0.55;
                buffer[i] = (short) Math.max(-32768, Math.min(32767, buffer[i] + (int)(sample * 32767)));
            }
        }
        playAudioBuffer(buffer);
    }

    private void playResonantPing(float freq, float duration, float volume) {
        int numSamples = (int) (sampleRate * duration);
        short[] buffer = new short[numSamples];
        for (int i = 0; i < numSamples; i++) {
            float t = (float) i / sampleRate;
            float env = (float) Math.exp(-t * 12.0);
            double sample = (Math.sin(2.0 * Math.PI * freq * t) +
                    0.5 * Math.sin(2.0 * Math.PI * (freq * 1.5) * t) +
                    0.25 * Math.sin(2.0 * Math.PI * (freq * 3.0) * t)) * env * volume;
            buffer[i] = (short) (Math.max(-1.0, Math.min(1.0, sample)) * 32767);
        }
        playAudioBuffer(buffer);
    }

    private void playSoftSubBassDrop(float startFreq, float endFreq, float duration, float volume) {
        int numSamples = (int) (sampleRate * duration);
        short[] buffer = new short[numSamples];
        for (int i = 0; i < numSamples; i++) {
            float t = (float) i / sampleRate;
            float progress = t / duration;
            float f = startFreq + (endFreq - startFreq) * (progress * progress);
            float env = (float) Math.sin(progress * Math.PI);
            double sample = (Math.sin(2.0 * Math.PI * f * t) +
                    0.3 * Math.sin(2.0 * Math.PI * (f * 0.5) * t)) * env * volume;
            buffer[i] = (short) (Math.max(-1.0, Math.min(1.0, sample)) * 32767);
        }
        playAudioBuffer(buffer);
    }

    private void playSoftPop(float freq, float duration, float volume) {
        int numSamples = (int) (sampleRate * duration);
        short[] buffer = new short[numSamples];
        for (int i = 0; i < numSamples; i++) {
            float t = (float) i / sampleRate;
            float env = (float) Math.exp(-t * 100.0);
            double sample = Math.sin(2.0 * Math.PI * freq * t) * env * volume;
            buffer[i] = (short) (Math.max(-1.0, Math.min(1.0, sample)) * 32767);
        }
        playAudioBuffer(buffer);
    }

    private void playAudioBuffer(short[] buffer) {
        if (!isInForeground) return;
        AudioTrack track = null;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(buffer.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();

            track.write(buffer, 0, buffer.length);
            track.play();

            final AudioTrack finalTrack = track;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(600);
                        finalTrack.stop();
                        finalTrack.release();
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception e) {
            if (track != null) {
                try { track.release(); } catch (Exception ignored) {}
            }
        }
    }
}
