package com.kazisayed.deliverit3d;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * High-Fidelity Real-Time Procedural Audio Synthesizer & Sound System for Night Rider.
 * Features:
 * - Dynamic Multi-Cylinder Motorcycle Engine Simulator (RPM curves, 4-gear transmission, intake growl, exhaust harmonics)
 * - Vehicle Profiles: High-rev Superbike, 2-Stroke Scooter whine, and Mechanical Bicycle cadence & freehub pawl ticking
 * - Road Texture Rumble & Aerodynamic Helmet Wind Rush scaling with speed
 * - Wet Road Tire Spray & Skid Friction Screech on hard braking
 * - Multi-Layered Rain Environment & Deep Rolling City Thunder
 * - Ambient Cyberpunk Synthwave Soundtrack (Evolving 4-chord analog synth pad & pulsing sub-bass)
 * - Rich Layered Arcade Cues: Delivery Chime, Coin Pickup, Dual Automotive Horn, Multi-Stage Crash Impact, UI Clicks
 * - Stereo Master Limiter with soft-knee saturation (zero clipping/distortion)
 */
public final class GameAudio implements AutoCloseable {
    private static final int RATE = 48000;
    private static final int FRAMES = 512;

    private final AudioManager manager;
    private final AudioTrack track;
    private final Thread thread;

    // Concurrency queues
    private final ConcurrentLinkedQueue<Voice> queue = new ConcurrentLinkedQueue<>();
    private final List<Voice> voices = new ArrayList<>(32);

    // Operational flags & volume controls
    private volatile boolean alive = true;
    private volatile boolean active = false;
    private volatile float master = 0.75f;
    private volatile float effects = 0.80f;
    private volatile float ambience = 0.50f;

    // Live driving telemetry
    private volatile float speed = 0f;
    private volatile float throttle = 0f;
    private volatile boolean braking = false;
    private volatile float rain = 0f;
    private volatile String vehicleId = "bike";
    private volatile boolean inGame = false;

    // DSP Engine States
    private long sampleIndex = 0;
    private int noisePrng = 83719;
    private double pinkNoiseFilter = 0;
    private double subRumbleFilter = 0;
    private double windFilter = 0;

    // Engine Simulation States
    private double currentRpm = 1100.0;
    private double enginePhase = 0;
    private double bikePawlPhase = 0;
    private double bikePedalPhase = 0;

    // Ambient Synth Music States
    private int chordIndex = 0;
    private double chordTimer = 0;
    private double synthPhase1 = 0;
    private double synthPhase2 = 0;
    private double bassPhase = 0;

    // Voice Definition
    private static final int KIND_TONE = 0;
    private static final int KIND_BELL = 1;
    private static final int KIND_NOISE = 2;
    private static final int KIND_HORN = 3;
    private static final int KIND_IMPACT = 4;
    private static final int KIND_SWEEP = 5;
    private static final int KIND_UI = 6;

    private static class Voice {
        double frequency;
        double endFrequency;
        double duration;
        double remaining;
        double gain;
        double phase;
        int kind;
        float pan; // -1 (left) .. +1 (right)

        Voice(double freq, double seconds, double gain, int kind) {
            this(freq, freq, seconds, gain, kind, 0f);
        }

        Voice(double freq, double seconds, double gain, int kind, float pan) {
            this(freq, freq, seconds, gain, kind, pan);
        }

        Voice(double freq, double endFreq, double seconds, double gain, int kind, float pan) {
            this.frequency = freq;
            this.endFrequency = endFreq;
            this.duration = Math.max(0.001, seconds);
            this.remaining = this.duration;
            this.gain = gain;
            this.kind = kind;
            this.pan = pan;
            this.phase = 0;
        }
    }

    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        if (change <= 0) active = false;
    };

    public GameAudio(Context context) {
        manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        int bufSize = Math.max(minBuf, FRAMES * 4 * 4);

        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufSize)
                .build();

        thread = new Thread(this::mixLoop, "NightRiderAudio");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.setDaemon(true);
        thread.start();
    }

    public void volumes(float m, float e, float a) {
        master = Math.max(0f, Math.min(1f, m));
        effects = Math.max(0f, Math.min(1f, e));
        ambience = Math.max(0f, Math.min(1f, a));
    }

    /** Legacy telemetry signature for backwards compatibility. */
    public void state(float speed, float rain, float traffic) {
        state(speed, speed > 0.5f ? 0.7f : 0f, false, rain, "bike", speed > 0.01f);
    }

    /** Full telemetry driving update. */
    public void state(float speed, float throttle, boolean braking, float rain, String vehicleId, boolean inGame) {
        this.speed = Math.abs(speed);
        this.throttle = Math.max(0f, Math.min(1f, throttle));
        this.braking = braking;
        this.rain = Math.max(0f, Math.min(1f, rain));
        this.vehicleId = vehicleId != null ? vehicleId : "bike";
        this.inGame = inGame;
    }

    public void resume() {
        if (manager != null) {
            int res = manager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) active = true;
        } else {
            active = true;
        }
    }

    public void pause() {
        active = false;
        queue.clear();
        if (manager != null) manager.abandonAudioFocus(focusListener);
    }

    /** Trigger high-polish procedural audio cues. */
    public void cue(String name) {
        if (name == null) return;
        switch (name) {
            case "delivery" -> {
                // Sparkling crystalline delivery arpeggio (E5 -> G#5 -> B5 -> E6)
                queue.add(new Voice(659.25, 0.26, 0.22, KIND_BELL, -0.2f));
                queue.add(new Voice(830.61, 0.32, 0.24, KIND_BELL, 0.0f));
                queue.add(new Voice(987.77, 0.38, 0.25, KIND_BELL, 0.2f));
                queue.add(new Voice(1318.51, 0.65, 0.28, KIND_BELL, 0.3f));
            }
            case "coin" -> {
                // Classic crisp dual arcade pickup chime (B5 -> E6)
                queue.add(new Voice(987.77, 0.08, 0.18, KIND_BELL, 0.1f));
                queue.add(new Voice(1318.51, 0.28, 0.22, KIND_BELL, 0.2f));
            }
            case "horn" -> {
                // Authentic dual-tone vehicle horn (F4 349Hz + A4 440Hz)
                queue.add(new Voice(349.23, 0.36, 0.28, KIND_HORN, -0.1f));
                queue.add(new Voice(440.00, 0.36, 0.24, KIND_HORN, 0.1f));
            }
            case "crash" -> {
                // Multi-stage collision: transient metal crunch + body crunch + sub-bass shockwave
                queue.add(new Voice(1800, 400, 0.14, 0.38, KIND_IMPACT, 0.0f));
                queue.add(new Voice(240, 60, 0.35, 0.42, KIND_NOISE, 0.0f));
                queue.add(new Voice(65, 32, 0.65, 0.50, KIND_SWEEP, 0.0f));
            }
            case "thunder" -> {
                // Rolling cityscape thunder rumble
                queue.add(new Voice(36, 26, 3.2, 0.38, KIND_NOISE, -0.2f));
                queue.add(new Voice(54, 38, 2.4, 0.26, KIND_NOISE, 0.3f));
            }
            case "finish" -> {
                // Triumphant victory fanfare (C5 -> E5 -> G5 -> C6)
                queue.add(new Voice(523.25, 0.70, 0.22, KIND_BELL, -0.3f));
                queue.add(new Voice(659.25, 0.85, 0.24, KIND_BELL, -0.1f));
                queue.add(new Voice(783.99, 1.05, 0.25, KIND_BELL, 0.1f));
                queue.add(new Voice(1046.50, 1.40, 0.28, KIND_BELL, 0.3f));
            }
            case "checkpoint" -> {
                // Energetic checkpoint chime
                queue.add(new Voice(587.33, 0.14, 0.20, KIND_BELL, -0.1f));
                queue.add(new Voice(880.00, 0.30, 0.22, KIND_BELL, 0.1f));
            }
            case "combo" -> {
                // Rising combo bonus chime
                queue.add(new Voice(740, 1100, 0.22, 0.22, KIND_SWEEP, 0.2f));
            }
            case "brake", "skid" -> {
                // Hard tire skid screech
                queue.add(new Voice(880, 720, 0.28, 0.22, KIND_SWEEP, 0.0f));
            }
            case "ui_click" -> {
                // Modern tactile mechanical micro-click
                queue.add(new Voice(1200, 400, 0.024, 0.16, KIND_UI, 0.0f));
            }
            case "ui_toggle" -> {
                // Crisp 2-tone toggle blip
                queue.add(new Voice(920, 1180, 0.045, 0.14, KIND_UI, 0.0f));
            }
            default -> queue.add(new Voice(440, 0.08, 0.10, KIND_BELL, 0f));
        }
    }

    private void mixLoop() {
        short[] pcm = new short[FRAMES * 2];
        final double dt = 1.0 / RATE;
        track.play();

        while (alive) {
            // Dequeue incoming voices
            Voice incoming;
            while ((incoming = queue.poll()) != null) {
                if (voices.size() < 32) voices.add(incoming);
            }

            final float curSpeed = speed;
            final float curThrottle = throttle;
            final boolean curBraking = braking;
            final float curRain = rain;
            final boolean curInGame = inGame;
            final String curVehicle = vehicleId;

            // Target engine RPM computation
            double targetRpm = 1100.0;
            if (curInGame) {
                if ("bike".equals(curVehicle)) {
                    targetRpm = 80.0 + curSpeed * 12.0; // Cadence RPM
                } else if ("scooter".equals(curVehicle)) {
                    targetRpm = 2200.0 + curSpeed * 220.0 + curThrottle * 2800.0;
                } else {
                    // Motorcycle / Superbike: 4-gear virtual transmission
                    double gearRatio = curSpeed < 5.5f ? 2.7 : (curSpeed < 11.5f ? 1.85 : (curSpeed < 17.5f ? 1.32 : 1.0));
                    targetRpm = 1200.0 + (curSpeed * 210.0 * gearRatio) + (curThrottle * 2600.0);
                    if (targetRpm > 9800.0) targetRpm = 9800.0;
                }
            }

            // Synthesize block of stereo PCM frames
            for (int i = 0; i < FRAMES; i++) {
                // 1. Generate White / Pink Noise
                noisePrng ^= (noisePrng << 13);
                noisePrng ^= (noisePrng >>> 17);
                noisePrng ^= (noisePrng << 5);
                double white = (noisePrng & 0x7FFFFFFF) / 2147483647.0 * 2.0 - 1.0;
                pinkNoiseFilter += (white - pinkNoiseFilter) * 0.045;
                subRumbleFilter += (pinkNoiseFilter - subRumbleFilter) * 0.018;

                // 2. Synthesize Vehicle Engine Audio
                double engineL = 0;
                double engineR = 0;

                if (curInGame) {
                    if ("bike".equals(curVehicle)) {
                        // Bicycle: chain whoosh when pedaling + freehub ticking when coasting
                        if (curSpeed > 0.4f) {
                            if (curThrottle > 0.08f) {
                                // Pedaling cadence whoosh (sine envelope around 1.8 Hz)
                                bikePedalPhase += 2.0 * Math.PI * 2.2 * dt;
                                double whooshEnv = Math.max(0, Math.sin(bikePedalPhase));
                                double chain = (white * 0.02 + pinkNoiseFilter * 0.035) * whooshEnv * Math.min(1.0, curSpeed / 6.0);
                                engineL += chain * 0.5;
                                engineR += chain * 0.5;
                            } else {
                                // Coasting freehub pawl clicking (rapid micro-ticks)
                                bikePawlPhase += (curSpeed * 4.5);
                                if (bikePawlPhase > 1.0) {
                                    bikePawlPhase -= Math.floor(bikePawlPhase);
                                    double click = (white > 0 ? 0.06 : -0.06) * Math.min(1.0, curSpeed / 8.0);
                                    engineL += click;
                                    engineR += click;
                                }
                            }
                        }
                    } else if ("scooter".equals(curVehicle)) {
                        // Scooter: high-pitch 2-stroke whine
                        currentRpm += (targetRpm - currentRpm) * 0.0004;
                        double f0 = currentRpm / 30.0;
                        enginePhase += 2.0 * Math.PI * f0 * dt;
                        if (enginePhase > 2.0 * Math.PI) enginePhase -= 2.0 * Math.PI;

                        double wave = Math.sin(enginePhase) + 0.6 * Math.sin(enginePhase * 2) + 0.35 * Math.sin(enginePhase * 3);
                        double raspy = Math.tanh(wave * 1.5) * (0.07 + curThrottle * 0.05);
                        engineL += raspy;
                        engineR += raspy;
                    } else {
                        // Motorcycle / Superbike: rich multi-harmonic 4-stroke thrum & exhaust growl
                        currentRpm += (targetRpm - currentRpm) * (curThrottle > 0.05 ? 0.0007 : 0.0003);
                        double f0 = currentRpm / 30.0; // Firing frequency
                        enginePhase += 2.0 * Math.PI * f0 * dt;
                        if (enginePhase > 2.0 * Math.PI) enginePhase -= 2.0 * Math.PI;

                        double p = enginePhase;
                        double pulse = Math.sin(p)
                                + 0.55 * Math.sin(p * 2.0)
                                + 0.32 * Math.sin(p * 3.0)
                                + 0.18 * Math.sin(p * 4.0)
                                + 0.08 * Math.sin(p * 6.0);

                        // Throaty exhaust saturation + intake growl
                        double growl = Math.tanh(pulse * (1.6 + curThrottle * 0.9));
                        double vol = (0.055 + Math.min(0.12, (currentRpm - 1100.0) / 7000.0 * 0.12)) * (0.75 + curThrottle * 0.45);

                        // Subtle cylinder valve chatter on exhaust stroke
                        double chatter = (white > 0.75 ? 0.015 : 0) * Math.min(1.0, currentRpm / 5000.0);

                        double exhaust = (growl * vol) + chatter;
                        engineL += exhaust * 0.95;
                        engineR += exhaust * 1.05;
                    }
                }

                // 3. Road Texture & Wind Rush Audio
                double roadRumble = subRumbleFilter * Math.min(1.0, curSpeed / 16.0) * 0.08;
                double windIntensity = Math.max(0.0, (curSpeed - 5.5) / 20.0);
                windFilter += (pinkNoiseFilter * windIntensity - windFilter) * 0.02;
                double windRush = windFilter * 0.11;

                // Wet Road Tire Spray
                double wetSpray = (Math.abs(white) * 0.035 + pinkNoiseFilter * 0.025) * curRain * Math.min(1.0, curSpeed / 10.0);

                // Hard Braking Tire Skid
                double brakeSqueal = 0;
                if (curBraking && curSpeed > 2.5f) {
                    double skidFreq = 780.0 + Math.sin(sampleIndex * 0.04) * 80.0;
                    double skidWave = Math.sin(sampleIndex * 2.0 * Math.PI * skidFreq * dt);
                    brakeSqueal = (skidWave * 0.08 + white * 0.04) * Math.min(1.0, curSpeed / 8.0);
                }

                // 4. Rain & Weather Ambience
                double rainShower = (Math.abs(white) * 0.028 + pinkNoiseFilter * 0.055) * curRain;

                // 5. Cyberpunk Synthwave Ambient Soundtrack
                chordTimer += dt;
                if (chordTimer >= 5.0) {
                    chordTimer -= 5.0;
                    chordIndex = (chordIndex + 1) % 4;
                }

                // Evolving 4-chord progression: Am7 -> Fmaj7 -> Cadd9 -> Em7
                double rootFreq;
                double thirdFreq;
                double fifthFreq;
                switch (chordIndex) {
                    case 0 -> { rootFreq = 110.0; thirdFreq = 130.81; fifthFreq = 164.81; } // Am
                    case 1 -> { rootFreq = 87.31; thirdFreq = 110.0;  fifthFreq = 130.81; } // F
                    case 2 -> { rootFreq = 65.41; thirdFreq = 98.00;  fifthFreq = 130.81; } // C
                    default ->{ rootFreq = 82.41; thirdFreq = 123.47; fifthFreq = 164.81; } // Em
                }

                synthPhase1 += 2.0 * Math.PI * thirdFreq * dt;
                synthPhase2 += 2.0 * Math.PI * (fifthFreq * 1.003) * dt; // Subtle chorus detune
                bassPhase += 2.0 * Math.PI * (rootFreq * 0.5) * dt;

                double slowLfo = Math.sin(sampleIndex * 2.0 * Math.PI * 0.08 * dt);
                double padWave = (Math.sin(synthPhase1) + Math.sin(synthPhase2)) * (0.016 + slowLfo * 0.005);

                // 8th-note pulsing analog bassline
                double bassEnv = Math.max(0, Math.sin(sampleIndex * 2.0 * Math.PI * 2.4 * dt));
                double bassWave = (Math.sin(bassPhase) + 0.3 * Math.sin(bassPhase * 2.0)) * bassEnv * 0.022;
                double synthScore = (padWave + bassWave) * (curInGame ? 0.65 : 1.0);

                // Combine Ambience & Vehicle Core
                double leftTotal = (engineL + roadRumble + windRush + wetSpray + brakeSqueal) * effects
                        + (rainShower + synthScore) * ambience;
                double rightTotal = (engineR + roadRumble + windRush + wetSpray + brakeSqueal) * effects
                        + (rainShower + synthScore) * ambience;

                // 6. Synthesize Active Voice SFX (Chimes, Horn, Crash, UI)
                for (int v = voices.size() - 1; v >= 0; v--) {
                    Voice voice = voices.get(v);
                    double progress = 1.0 - (voice.remaining / voice.duration);
                    voice.remaining -= dt;

                    // Compute dynamic frequency for sweeps
                    double curFreq = voice.frequency + (voice.endFrequency - voice.frequency) * progress;
                    voice.phase += 2.0 * Math.PI * curFreq * dt;

                    double voiceSignal = 0;
                    double env;

                    switch (voice.kind) {
                        case KIND_BELL -> {
                            // Crystalline bell envelope with exponential ring
                            env = Math.min(1.0, progress * 40.0) * Math.exp(-progress * 4.8);
                            double harm = Math.sin(voice.phase)
                                    + 0.45 * Math.sin(voice.phase * 2.76)
                                    + 0.22 * Math.sin(voice.phase * 5.4);
                            voiceSignal = harm * env * voice.gain;
                        }
                        case KIND_HORN -> {
                            // Automotive horn with brass harmonics and vibrato
                            double vib = Math.sin(sampleIndex * 2.0 * Math.PI * 5.5 * dt) * 0.012;
                            double hPhase = voice.phase * (1.0 + vib);
                            env = Math.min(1.0, progress * 25.0) * Math.min(1.0, (1.0 - progress) * 20.0);
                            double hornWave = Math.sin(hPhase)
                                    + 0.5 * Math.sin(hPhase * 3.0)
                                    + 0.25 * Math.sin(hPhase * 5.0);
                            voiceSignal = hornWave * env * voice.gain;
                        }
                        case KIND_IMPACT -> {
                            // High-frequency crash crunch & shock transient
                            env = Math.exp(-progress * 9.0);
                            voiceSignal = (white * 0.6 + Math.sin(voice.phase) * 0.4) * env * voice.gain;
                        }
                        case KIND_SWEEP -> {
                            // Smooth frequency dive/rise
                            env = Math.min(1.0, progress * 30.0) * Math.exp(-progress * 3.5);
                            voiceSignal = Math.sin(voice.phase) * env * voice.gain;
                        }
                        case KIND_NOISE -> {
                            // Filtered noise rumble (thunder / crash boom)
                            env = Math.exp(-progress * 2.2);
                            voiceSignal = pinkNoiseFilter * env * voice.gain;
                        }
                        case KIND_UI -> {
                            // Ultra-short crisp tactile micro-click
                            env = Math.sin(progress * Math.PI);
                            voiceSignal = Math.sin(voice.phase) * env * voice.gain;
                        }
                        default -> {
                            env = Math.min(1.0, (1.0 - progress) * 8.0);
                            voiceSignal = Math.sin(voice.phase) * env * voice.gain;
                        }
                    }

                    // Apply stereo pan
                    double panL = Math.cos((voice.pan + 1.0) * Math.PI * 0.25);
                    double panR = Math.sin((voice.pan + 1.0) * Math.PI * 0.25);
                    leftTotal += voiceSignal * panL * effects;
                    rightTotal += voiceSignal * panR * effects;

                    if (voice.remaining <= 0) voices.remove(v);
                }

                // 7. Master Saturation Limiter (Prevents Digital Clipping)
                double finalL = active ? Math.tanh(leftTotal * master * 1.35) : 0;
                double finalR = active ? Math.tanh(rightTotal * master * 1.35) : 0;

                pcm[i * 2] = (short) (finalL * 28500);
                pcm[i * 2 + 1] = (short) (finalR * 28500);
                sampleIndex++;
            }

            // Write PCM block to AudioTrack
            int written = 0;
            while (alive && written < pcm.length) {
                int res = track.write(pcm, written, pcm.length - written, AudioTrack.WRITE_BLOCKING);
                if (res < 0) {
                    alive = false;
                    break;
                }
                written += res;
            }
        }

        try {
            track.stop();
            track.release();
        } catch (Exception ignored) {}
    }

    @Override
    public void close() {
        alive = false;
        if (manager != null) manager.abandonAudioFocus(focusListener);
        try {
            thread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
