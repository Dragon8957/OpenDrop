package com.openprop.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private Thread gameThread;
    private volatile boolean isRunning = false;
    private final SurfaceHolder holder;
    private final Random random = new Random();

    // Managers
    private final SoundManager sounds = new SoundManager();
    private final VibrationManager haptics;
    private final SharedPreferences prefs;

    // Paints
    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pWall = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pObs = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStripe = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSpike = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pShield = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTextBold = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCard = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pOverlay = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pVectorIcon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pIconStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAura = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Item Detail Paints
    private final Paint pItemFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pItemStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pItemGlow = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Screen
    private int screenW = 1080, screenH = 2400;
    private float dpr = 2.5f;
    private float wallMargin;

    // Author Avatar
    private Bitmap authorAvatarBitmap = null;

    // Animation Clock
    private float globalAnimTime = 0f;

    // Ghost Trails for Slow-Mo
    private static class Ghost {
        float x, y, tilt, alpha;
    }
    private final List<Ghost> ghostTrails = new ArrayList<>();

    // Settings Parameters
    private boolean screenShakeEnabled = true;
    private boolean particlesEnabled = true;
    private boolean speedAccelerationEnabled = true;
    private int controlMode = 0; // 0=Drag Follow, 1=Left/Right Zones
    private float sensitivity = 1.0f; // 1.0f, 1.5f, 2.0f

    // Screen Shake
    private float shakeTimer = 0f;
    private float shakeAmount = 0f;

    // States
    public enum State { MENU, PLAYING, PAUSED, SETTINGS, GAMEOVER }
    private State gameState = State.MENU;
    private State prevSettingsState = State.MENU;

    // Button Animation System
    private int pressedBtnId = -1;
    private float[] btnScales = new float[]{1f, 1f, 1f, 1f, 1f};
    private int pendingActionId = -1;
    private float pendingActionTimer = 0f;

    // High Score
    private int bestDepth = 0;

    // Game data
    private float worldY = 0f;
    private int depth = 0;
    private float nextSpawnY = 400f;

    // Player Capsule
    private float px = 0, py = 0;
    private float pRadius = 18f;
    private float pVx = 0;
    private float targetTouchX = 0;
    private boolean isTouching = false;
    private int zoneTouchDir = 0;
    private boolean shield = false;
    private float slowMoTimer = 0f;
    private float playerTilt = 0f;

    // AI Bot Autopilot (ONLY active on State.MENU)
    private float botTargetX = 0;
    private float botDecisionTimer = 0;

    // Ambient Dust Particles
    private static class Dust {
        float x, y, speed, size, alpha;
    }
    private final List<Dust> dustParticles = new ArrayList<>();

    // Thruster & Impact Particles
    private static class Particle {
        float x, y, vx, vy, life, maxLife, radius;
        int color;
    }
    private final List<Particle> particles = new ArrayList<>();

    // Floating Text
    private static class FloatingText {
        float x, y, life;
        String text;
        int color;
    }
    private final List<FloatingText> floatTexts = new ArrayList<>();

    // Obstacles
    private static class Obstacle {
        int type; // 0=wall, 1=moving_wall, 2=spike
        float x, y, w, h;
        float vx, minX, maxX;
    }
    private final List<Obstacle> obstacles = new ArrayList<>();

    // Pickups
    private static class Pickup {
        int type; // 0=diamond, 1=shield, 2=hourglass, 3=magnet
        float x, y, radius, animPhase;
    }
    private final List<Pickup> pickups = new ArrayList<>();

    public GameView(Context context) {
        super(context);
        holder = getHolder();
        holder.addCallback(this);

        haptics = new VibrationManager(context);
        prefs = context.getSharedPreferences("openprop_settings_v1", Context.MODE_PRIVATE);
        bestDepth = prefs.getInt("best_depth", 0);

        Localization.lang = prefs.getInt("app_lang", 1);
        sounds.enabled = prefs.getBoolean("snd_enabled", true);
        haptics.enabled = prefs.getBoolean("vib_enabled", true);
        screenShakeEnabled = prefs.getBoolean("opt_shake", true);
        particlesEnabled = prefs.getBoolean("opt_particles", true);
        speedAccelerationEnabled = prefs.getBoolean("opt_accel", true);
        controlMode = prefs.getInt("opt_control", 0);
        sensitivity = prefs.getFloat("opt_sens", 1.0f);

        try {
            int avatarResId = getResources().getIdentifier("author_avatar", "drawable", context.getPackageName());
            if (avatarResId != 0) {
                authorAvatarBitmap = BitmapFactory.decodeResource(getResources(), avatarResId);
            }
        } catch (Throwable ignored) {}

        initPaints(context);
    }

    private void initPaints(Context context) {
        Typeface customFont = null;
        try {
            int fontResId = getResources().getIdentifier("game_font", "font", context.getPackageName());
            if (fontResId != 0) {
                customFont = getResources().getFont(fontResId);
            }
        } catch (Throwable ignored) {}

        if (customFont == null) {
            try {
                customFont = Typeface.createFromAsset(context.getAssets(), "fonts/game_font.ttf");
            } catch (Throwable ignored) {}
        }

        if (customFont == null) {
            customFont = Typeface.create("casual", Typeface.BOLD);
        }

        pTitle.setTypeface(customFont);
        pTitle.setLetterSpacing(0.04f);
        pTitle.setShadowLayer(16f, 0, 0, Color.parseColor("#9000F0FF"));

        pTextBold.setTypeface(customFont);
        pTextBold.setLetterSpacing(0.02f);

        pText.setTypeface(customFont);
        pText.setLetterSpacing(0.01f);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(3f);

        pCard.setStyle(Paint.Style.FILL);
        pBtn.setStyle(Paint.Style.FILL);

        pBtnBorder.setStyle(Paint.Style.STROKE);
        pBtnBorder.setStrokeWidth(1.5f);
        pBtnBorder.setColor(Color.parseColor("#40FFFFFF"));

        pVectorIcon.setStyle(Paint.Style.FILL_AND_STROKE);
        pVectorIcon.setStrokeWidth(2f);

        pIconStroke.setStyle(Paint.Style.STROKE);
        pIconStroke.setStrokeWidth(2.5f);
        pIconStroke.setColor(Color.WHITE);

        pItemStroke.setStyle(Paint.Style.STROKE);
        pItemStroke.setStrokeCap(Paint.Cap.ROUND);
        pItemStroke.setStrokeJoin(Paint.Join.ROUND);

        pOverlay.setColor(Color.parseColor("#B8080A10"));
    }

    public void onPause() {
        sounds.onPause();
    }

    public void onResume() {
        sounds.onResume();
    }

    public void onDestroy() {
        sounds.onDestroy();
    }

    private void saveSettings() {
        prefs.edit()
                .putInt("app_lang", Localization.lang)
                .putBoolean("snd_enabled", sounds.enabled)
                .putBoolean("vib_enabled", haptics.enabled)
                .putBoolean("opt_shake", screenShakeEnabled)
                .putBoolean("opt_particles", particlesEnabled)
                .putBoolean("opt_accel", speedAccelerationEnabled)
                .putInt("opt_control", controlMode)
                .putFloat("opt_sens", sensitivity)
                .apply();
    }

    private void initDust() {
        dustParticles.clear();
        if (!particlesEnabled) return;
        for (int i = 0; i < 40; i++) {
            Dust d = new Dust();
            d.x = wallMargin + random.nextFloat() * (screenW - wallMargin * 2f);
            d.y = random.nextFloat() * screenH;
            d.speed = (120f + random.nextFloat() * 220f) * dpr;
            d.size = (1.5f + random.nextFloat() * 2.5f) * dpr;
            d.alpha = 0.25f + random.nextFloat() * 0.55f;
            dustParticles.add(d);
        }
    }

    private void startNewGame() {
        gameState = State.PLAYING;
        worldY = 0f;
        depth = 0;
        nextSpawnY = 400f * dpr;

        px = screenW / 2f;
        py = 240f * dpr;
        targetTouchX = px;
        pVx = 0;
        shield = false;
        slowMoTimer = 0;
        playerTilt = 0f;

        obstacles.clear();
        pickups.clear();
        particles.clear();
        floatTexts.clear();
        ghostTrails.clear();

        for (int i = 0; i < 7; i++) {
            spawnObstacleSegment();
        }

        haptics.pulse();
        sounds.playPowerup();
        sounds.nextTrack(); // Switch to fresh cyber synth track on each run!
    }

    private void spawnObstacleSegment() {
        float y = nextSpawnY;
        float spacing = Math.max(175f * dpr, 265f * dpr - Math.min(75f * dpr, depth * 0.35f));
        nextSpawnY += spacing;

        float availableW = screenW - wallMargin * 2f;
        int pattern = random.nextInt(4);

        if (pattern == 0) {
            float gapW = Math.max(88f * dpr, 132f * dpr - Math.min(40f * dpr, depth * 0.2f));
            float gapX = wallMargin + random.nextFloat() * (availableW - gapW);

            if (gapX > wallMargin) {
                Obstacle o = new Obstacle();
                o.type = 0; o.x = wallMargin; o.y = y; o.w = gapX - wallMargin; o.h = 24f * dpr;
                obstacles.add(o);
            }
            if (gapX + gapW < screenW - wallMargin) {
                Obstacle o = new Obstacle();
                o.type = 0; o.x = gapX + gapW; o.y = y; o.w = (screenW - wallMargin) - (gapX + gapW); o.h = 24f * dpr;
                obstacles.add(o);
            }
        } else if (pattern == 1) {
            float gapW = 86f * dpr;
            float islandW = 65f * dpr;
            float total = gapW * 2f + islandW;
            if (availableW > total) {
                float startX = wallMargin + (availableW - total) / 2f;
                Obstacle o1 = new Obstacle(); o1.type = 0; o1.x = wallMargin; o1.y = y; o1.w = startX - wallMargin; o1.h = 24f * dpr;
                Obstacle o2 = new Obstacle(); o2.type = 0; o2.x = startX + gapW; o2.y = y; o2.w = islandW; o2.h = 24f * dpr;
                Obstacle o3 = new Obstacle(); o3.type = 0; o3.x = startX + gapW + islandW + gapW; o3.y = y; o3.w = (screenW - wallMargin) - o3.x; o3.h = 24f * dpr;
                obstacles.add(o1); obstacles.add(o2); obstacles.add(o3);
            }
        } else if (pattern == 2) {
            float platW = 95f * dpr;
            Obstacle o = new Obstacle();
            o.type = 1; o.x = wallMargin + 25f * dpr; o.y = y; o.w = platW; o.h = 24f * dpr;
            o.vx = (130f + Math.min(70f, depth * 0.15f)) * dpr;
            o.minX = wallMargin + 10f * dpr; o.maxX = screenW - wallMargin - platW - 10f * dpr;
            obstacles.add(o);
        } else if (pattern == 3) {
            float safeW = 95f * dpr;
            float safeX = wallMargin + random.nextFloat() * (availableW - safeW);
            if (safeX > wallMargin) {
                Obstacle o = new Obstacle(); o.type = 2; o.x = wallMargin; o.y = y; o.w = safeX - wallMargin; o.h = 24f * dpr;
                obstacles.add(o);
            }
            if (safeX + safeW < screenW - wallMargin) {
                Obstacle o = new Obstacle(); o.type = 2; o.x = safeX + safeW; o.y = y; o.w = (screenW - wallMargin) - (safeX + safeW); o.h = 24f * dpr;
                obstacles.add(o);
            }
        }

        float r = random.nextFloat();
        float py = y - spacing / 2f;
        float px = wallMargin + 35f * dpr + random.nextFloat() * (availableW - 70f * dpr);
        if (r < 0.45f) {
            Pickup p = new Pickup(); p.type = 0; p.x = px; p.y = py; p.radius = 17f * dpr; p.animPhase = random.nextFloat() * 6f;
            pickups.add(p);
        } else if (r < 0.58f) {
            Pickup p = new Pickup(); p.type = 1; p.x = px; p.y = py; p.radius = 18f * dpr; p.animPhase = 0;
            pickups.add(p);
        } else if (r < 0.70f) {
            Pickup p = new Pickup(); p.type = 2; p.x = px; p.y = py; p.radius = 18f * dpr; p.animPhase = 0;
            pickups.add(p);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        isRunning = true;
        gameThread = new Thread(this);
        gameThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        screenW = width;
        screenH = height;
        dpr = getResources().getDisplayMetrics().density;
        wallMargin = 22f * dpr;
        pRadius = 17f * dpr;
        initDust();

        if (gameState == State.MENU && px == 0) {
            px = screenW / 2f;
            py = 240f * dpr;
            botTargetX = px;
            for (int i = 0; i < 7; i++) spawnObstacleSegment();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isRunning = false;
        try {
            if (gameThread != null) gameThread.join();
        } catch (InterruptedException ignored) {}
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        while (isRunning) {
            try {
                long now = System.nanoTime();
                float dt = Math.min(0.05f, (now - lastTime) / 1_000_000_000f);
                lastTime = now;

                update(dt);

                Canvas canvas = holder.lockCanvas();
                if (canvas != null) {
                    try {
                        render(canvas);
                    } finally {
                        holder.unlockCanvasAndPost(canvas);
                    }
                }
            } catch (Exception ignored) {}

            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {}
        }
    }

    private void update(float dt) {
        globalAnimTime += dt;

        for (int i = 0; i < btnScales.length; i++) {
            float target = (pressedBtnId == i) ? 0.93f : 1.0f;
            btnScales[i] += (target - btnScales[i]) * 18f * dt;
        }

        if (pendingActionTimer > 0) {
            pendingActionTimer -= dt;
            if (pendingActionTimer <= 0) {
                executeAction(pendingActionId);
                pendingActionId = -1;
            }
        }

        if (gameState != State.PLAYING && gameState != State.MENU) {
            return;
        }

        boolean isMenuBot = (gameState == State.MENU);

        float baseFall = 260f * dpr;
        if (speedAccelerationEnabled) {
            baseFall += Math.min(280f * dpr, depth * 0.85f * dpr);
        }

        if (slowMoTimer > 0) {
            slowMoTimer -= dt;
            baseFall *= 0.5f;

            if (random.nextFloat() < 0.45f) {
                Ghost g = new Ghost();
                g.x = px; g.y = py; g.tilt = playerTilt; g.alpha = 0.5f;
                ghostTrails.add(g);
            }
        }

        worldY += baseFall * dt;
        depth = (int) (worldY / (32f * dpr));

        if (shakeTimer > 0) shakeTimer -= dt;

        if (isMenuBot) {
            botDecisionTimer -= dt;
            if (botDecisionTimer <= 0) {
                botDecisionTimer = 0.15f;
                updateBotAI();
            }
            float dx = botTargetX - px;
            pVx = dx * 10f;
        } else {
            if (controlMode == 0) {
                if (isTouching) {
                    float dx = targetTouchX - px;
                    pVx = dx * 16f * sensitivity;
                } else {
                    pVx = 0;
                }
            } else {
                if (isTouching) {
                    pVx = zoneTouchDir * 420f * dpr * sensitivity;
                } else {
                    pVx = 0;
                }
            }
        }

        px += pVx * dt;
        playerTilt += ((pVx * 0.05f) - playerTilt) * 14f * dt;

        float minX = wallMargin + pRadius;
        float maxX = screenW - wallMargin - pRadius;
        if (px < minX) px = minX;
        if (px > maxX) px = maxX;

        for (int i = ghostTrails.size() - 1; i >= 0; i--) {
            Ghost g = ghostTrails.get(i);
            g.alpha -= dt * 2.5f;
            g.y -= (baseFall * 0.2f) * dt;
            if (g.alpha <= 0) ghostTrails.remove(i);
        }

        if (particlesEnabled) {
            for (Dust d : dustParticles) {
                d.y -= (d.speed + baseFall * 0.35f) * dt;
                if (d.y < 0) {
                    d.y = screenH + 10f * dpr;
                    d.x = wallMargin + random.nextFloat() * (screenW - wallMargin * 2f);
                }
            }

            for (int i = 0; i < 2; i++) {
                Particle p = new Particle();
                float offset = (i == 0 ? -6f : 6f) * dpr;
                p.x = px + offset + (random.nextFloat() - 0.5f) * 4f * dpr;
                p.y = py - pRadius * 0.8f;
                p.vx = (random.nextFloat() - 0.5f) * 20f * dpr;
                p.vy = -(140f + random.nextFloat() * 120f) * dpr;
                p.radius = (3.5f + random.nextFloat() * 3.5f) * dpr;
                p.life = 0.24f; p.maxLife = 0.24f;
                p.color = (slowMoTimer > 0 ? Color.parseColor("#F59E0B") : Color.parseColor("#00F0FF"));
                particles.add(p);
            }
        }

        while (nextSpawnY - worldY < screenH + 450f * dpr) {
            spawnObstacleSegment();
        }

        for (int i = obstacles.size() - 1; i >= 0; i--) {
            Obstacle o = obstacles.get(i);
            float screenY = o.y - worldY + py;

            if (o.type == 1) {
                o.x += o.vx * dt;
                if (o.x <= o.minX || o.x >= o.maxX) o.vx = -o.vx;
            }

            if (checkCollision(o, screenY)) {
                if (isMenuBot) {
                    obstacles.remove(i);
                    continue;
                }

                if (shield) {
                    shield = false;
                    sounds.playShieldBreak();
                    haptics.heavy();
                    triggerScreenShake(0.25f, 16f * dpr);
                    FloatingText ft = new FloatingText(); ft.x = px; ft.y = py; ft.text = Localization.get("shield_broken"); ft.color = Color.parseColor("#FFD60A"); ft.life = 0.6f;
                    floatTexts.add(ft);
                    obstacles.remove(i);
                    continue;
                } else {
                    onGameOver();
                    return;
                }
            }

            if (screenY < -120f * dpr) obstacles.remove(i);
        }

        // Pickups
        for (int i = pickups.size() - 1; i >= 0; i--) {
            Pickup p = pickups.get(i);
            float screenY = p.y - worldY + py;
            p.animPhase += dt * 4f;

            if (Math.hypot(px - p.x, py - screenY) < pRadius + p.radius) {
                collectPickup(p, isMenuBot);
                pickups.remove(i);
                continue;
            }

            if (screenY < -120f * dpr) pickups.remove(i);
        }

        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle pt = particles.get(i);
            pt.x += pt.vx * dt; pt.y += pt.vy * dt;
            pt.life -= dt;
            if (pt.life <= 0) particles.remove(i);
        }

        for (int i = floatTexts.size() - 1; i >= 0; i--) {
            FloatingText ft = floatTexts.get(i);
            ft.y -= 35f * dpr * dt;
            ft.life -= dt;
            if (ft.life <= 0) floatTexts.remove(i);
        }
    }

    private void updateBotAI() {
        Obstacle nearest = null;
        float nearestDist = Float.MAX_VALUE;
        for (Obstacle o : obstacles) {
            float sy = o.y - worldY + py;
            if (sy > py - 10f * dpr && sy < py + 350f * dpr) {
                if (sy - py < nearestDist) {
                    nearestDist = sy - py;
                    nearest = o;
                }
            }
        }

        if (nearest != null) {
            float obsCenterX = nearest.x + nearest.w / 2f;
            if (obsCenterX < screenW / 2f) {
                botTargetX = Math.min(screenW - wallMargin - pRadius * 2f, nearest.x + nearest.w + 40f * dpr);
            } else {
                botTargetX = Math.max(wallMargin + pRadius * 2f, nearest.x - 40f * dpr);
            }
        } else {
            Pickup bestPickup = null;
            float pDist = Float.MAX_VALUE;
            for (Pickup p : pickups) {
                float sy = p.y - worldY + py;
                if (sy > py && sy < py + 250f * dpr) {
                    if (sy - py < pDist) {
                        pDist = sy - py;
                        bestPickup = p;
                    }
                }
            }
            if (bestPickup != null) {
                botTargetX = bestPickup.x;
            }
        }
    }

    private boolean checkCollision(Obstacle o, float screenY) {
        float cx = Math.max(o.x, Math.min(px, o.x + o.w));
        float cy = Math.max(screenY, Math.min(py, screenY + o.h));
        float distSq = (px - cx) * (px - cx) + (py - cy) * (py - cy);
        return distSq < (pRadius * 0.9f * pRadius * 0.9f);
    }

    private void collectPickup(Pickup p, boolean isBot) {
        if (p.type == 0) {
            if (!isBot) {
                sounds.playGem(1);
                haptics.tick();
                FloatingText ft = new FloatingText(); ft.x = p.x; ft.y = py; ft.text = "+100"; ft.color = Color.parseColor("#00F0FF"); ft.life = 0.5f;
                floatTexts.add(ft);
            }
        } else if (p.type == 1) {
            shield = true;
            if (!isBot) {
                sounds.playPowerup();
                haptics.pulse();
                FloatingText ft = new FloatingText(); ft.x = p.x; ft.y = py; ft.text = Localization.get("shield"); ft.color = Color.parseColor("#34C759"); ft.life = 0.6f;
                floatTexts.add(ft);
            }
        } else if (p.type == 2) {
            slowMoTimer = 5.0f;
            if (!isBot) {
                sounds.playPowerup();
                haptics.pulse();
                FloatingText ft = new FloatingText(); ft.x = p.x; ft.y = py; ft.text = Localization.get("slowmo"); ft.color = Color.parseColor("#FF9500"); ft.life = 0.6f;
                floatTexts.add(ft);
            }
        }

        if (particlesEnabled) {
            for (int i = 0; i < 10; i++) {
                Particle pt = new Particle();
                pt.x = p.x; pt.y = py;
                float ang = random.nextFloat() * (float) Math.PI * 2f;
                float spd = (60f + random.nextFloat() * 100f) * dpr;
                pt.vx = (float) Math.cos(ang) * spd;
                pt.vy = (float) Math.sin(ang) * spd;
                pt.radius = (2.5f + random.nextFloat() * 2.5f) * dpr;
                pt.color = (p.type == 0) ? Color.parseColor("#00F0FF") : (p.type == 1 ? Color.parseColor("#34C759") : Color.parseColor("#FF9500"));
                pt.life = 0.35f; pt.maxLife = 0.35f;
                particles.add(pt);
            }
        }
    }

    private void triggerScreenShake(float duration, float intensity) {
        if (!screenShakeEnabled) return;
        shakeTimer = duration;
        shakeAmount = intensity;
    }

    private void onGameOver() {
        gameState = State.GAMEOVER;
        sounds.playGameOver();
        haptics.heavy();
        triggerScreenShake(0.35f, 22f * dpr);

        if (depth > bestDepth) {
            bestDepth = depth;
            prefs.edit().putInt("best_depth", bestDepth).apply();
        }
    }

    // Canvas Rendering
    private void render(Canvas c) {
        int bgColor = Color.parseColor("#0A0E17");
        int gridColor = Color.parseColor("#141E2E");

        c.save();
        if (shakeTimer > 0 && screenShakeEnabled) {
            float ox = (random.nextFloat() - 0.5f) * shakeAmount;
            float oy = (random.nextFloat() - 0.5f) * shakeAmount;
            c.translate(ox, oy);
        }

        // 1. Background Grid
        pBg.setColor(bgColor);
        c.drawRect(0, 0, screenW, screenH, pBg);

        float gridGap = 60f * dpr;
        float gridOff = -(worldY * 0.4f % gridGap);
        pStroke.setColor(gridColor);
        pStroke.setStrokeWidth(1.5f);
        for (float y = gridOff; y < screenH; y += gridGap) {
            c.drawLine(wallMargin, y, screenW - wallMargin, y, pStroke);
        }

        // 2. Dust Particles
        if (particlesEnabled) {
            pObs.setColor(Color.parseColor("#00F0FF"));
            for (Dust d : dustParticles) {
                pObs.setAlpha((int) (255 * d.alpha * 0.35f));
                c.drawCircle(d.x, d.y, d.size, pObs);
            }
            pObs.setAlpha(255);
        }

        // 3. ULTRA-DETAILED CYBER TECH SIDE WALLS
        renderDetailedSideWalls(c);

        // 4. HIGH-TECH OBSTACLES (Metallic Platforms & Plasma Spikes)
        for (Obstacle o : obstacles) {
            float sy = o.y - worldY + py;

            if (o.type == 0 || o.type == 1) {
                renderDetailedPlatform(c, o, sy);
            } else if (o.type == 2) {
                renderDetailedSpikes(c, o, sy);
            }
        }

        // 5. Realistic Recognizable Pickups
        for (Pickup p : pickups) {
            float sy = p.y - worldY + py + (float) Math.sin(p.animPhase) * 5f * dpr;
            c.save();
            c.translate(p.x, sy);

            if (p.type == 0) {
                renderFacetedDiamond(c, p.radius);
            } else if (p.type == 1) {
                renderRealisticShield(c, p.radius);
            } else if (p.type == 2) {
                renderRealisticHourglass(c, p.radius);
            }
            c.restore();
        }

        // 6. Particles
        for (Particle pt : particles) {
            pObs.setColor(pt.color);
            pObs.setAlpha((int) (255 * (pt.life / pt.maxLife)));
            c.drawCircle(pt.x, pt.y, pt.radius, pObs);
        }
        pObs.setAlpha(255);

        // 7. Player Sci-Fi Capsule Probe
        renderSciFiCapsule(c);

        // 8. Floating Texts
        pTextBold.setTextSize(17f * dpr);
        pTextBold.setTextAlign(Paint.Align.CENTER);
        for (FloatingText ft : floatTexts) {
            pTextBold.setColor(ft.color);
            c.drawText(ft.text, ft.x, ft.y, pTextBold);
        }

        c.restore();

        // 9. Overlay Screens
        if (gameState == State.PLAYING) renderInGameHUD(c);
        else if (gameState == State.MENU) renderMainMenu(c);
        else if (gameState == State.PAUSED) renderPauseMenu(c);
        else if (gameState == State.SETTINGS) renderSettingsMenu(c);
        else if (gameState == State.GAMEOVER) renderGameOverMenu(c);
    }

    // 🧱 PRO-TECH CYBER WALLS WITH PANELS, NEON TUBES, AND RIVETS
    private void renderDetailedSideWalls(Canvas c) {
        // Left & Right Gunmetal Base
        pWall.setColor(Color.parseColor("#0D131F"));
        c.drawRect(0, 0, wallMargin, screenH, pWall);
        c.drawRect(screenW - wallMargin, 0, screenW, screenH, pWall);

        // Inner Tech Panel Plates
        pWall.setColor(Color.parseColor("#152033"));
        c.drawRect(2f * dpr, 0, wallMargin - 4f * dpr, screenH, pWall);
        c.drawRect(screenW - wallMargin + 4f * dpr, 0, screenW - 2f * dpr, screenH, pWall);

        // Repeating Wall Plate Seams & Bolts
        float seamGap = 120f * dpr;
        float seamOff = -(worldY * 0.6f % seamGap);
        pStroke.setColor(Color.parseColor("#0A0E17"));
        pStroke.setStrokeWidth(3f * dpr);

        pItemFill.setColor(Color.parseColor("#F59E0B")); // Amber rivet bolt
        for (float y = seamOff; y < screenH + seamGap; y += seamGap) {
            c.drawLine(0, y, wallMargin, y, pStroke);
            c.drawLine(screenW - wallMargin, y, screenW, y, pStroke);

            // Tech Rivet Bolts
            c.drawCircle(8f * dpr, y - 8f * dpr, 2f * dpr, pItemFill);
            c.drawCircle(screenW - 8f * dpr, y - 8f * dpr, 2f * dpr, pItemFill);
        }

        // Pulsating Neon Energy Conduits
        float neonPulse = 0.7f + 0.3f * (float) Math.sin(globalAnimTime * 4f);
        pItemGlow.setColor(Color.parseColor("#00F0FF"));
        pItemGlow.setAlpha((int) (255 * neonPulse));
        pItemGlow.setStrokeWidth(2.5f * dpr);
        pItemGlow.setStyle(Paint.Style.STROKE);
        c.drawLine(wallMargin - 1f * dpr, 0, wallMargin - 1f * dpr, screenH, pItemGlow);
        c.drawLine(screenW - wallMargin + 1f * dpr, 0, screenW - wallMargin + 1f * dpr, screenH, pItemGlow);

        // Outer Glow line
        pItemGlow.setColor(Color.parseColor("#38BDF8"));
        pItemGlow.setAlpha((int) (120 * neonPulse));
        pItemGlow.setStrokeWidth(5f * dpr);
        c.drawLine(wallMargin - 1f * dpr, 0, wallMargin - 1f * dpr, screenH, pItemGlow);
        c.drawLine(screenW - wallMargin + 1f * dpr, 0, screenW - wallMargin + 1f * dpr, screenH, pItemGlow);
    }

    // 🚧 DETAILED SHADED METALLIC PLATFORMS WITH BEVEL & WARNING BEACONS
    private void renderDetailedPlatform(Canvas c, Obstacle o, float sy) {
        RectF r = new RectF(o.x, sy, o.x + o.w, sy + o.h);

        // Dark Titanium Plate Body
        pObs.setColor(Color.parseColor("#151D2A"));
        c.drawRoundRect(r, 6f * dpr, 6f * dpr, pObs);

        // Hazard Inlay Stripes
        c.save();
        c.clipRect(r);
        pStripe.setColor(Color.parseColor("#EF4444"));
        pStripe.setStrokeWidth(10f * dpr);
        float step = 20f * dpr;
        for (float sx = o.x - o.h * 1.5f; sx < o.x + o.w + o.h * 1.5f; sx += step) {
            c.drawLine(sx, sy + o.h, sx + o.h * 1.1f, sy, pStripe);
        }
        c.restore();

        // 3D Top Specular Bevel
        pStroke.setColor(Color.parseColor("#64748B"));
        pStroke.setStrokeWidth(2.5f * dpr);
        c.drawLine(o.x + 4f * dpr, sy + 1.5f * dpr, o.x + o.w - 4f * dpr, sy + 1.5f * dpr, pStroke);

        // Bottom Dark Shading
        pStroke.setColor(Color.parseColor("#090D14"));
        pStroke.setStrokeWidth(3f * dpr);
        c.drawLine(o.x + 4f * dpr, sy + o.h - 1.5f * dpr, o.x + o.w - 4f * dpr, sy + o.h - 1.5f * dpr, pStroke);

        // Frame Border
        pStroke.setColor(Color.parseColor("#334155"));
        pStroke.setStrokeWidth(1.8f * dpr);
        c.drawRoundRect(r, 6f * dpr, 6f * dpr, pStroke);

        // Pulsing Warning Red Beacon LEDs at platform edges
        float ledPulse = 0.5f + 0.5f * (float) Math.sin(globalAnimTime * 8f);
        pItemFill.setColor(Color.parseColor("#EF4444"));
        pItemFill.setAlpha((int) (255 * ledPulse));
        c.drawCircle(o.x + 8f * dpr, sy + o.h / 2f, 3.5f * dpr, pItemFill);
        c.drawCircle(o.x + o.w - 8f * dpr, sy + o.h / 2f, 3.5f * dpr, pItemFill);
        pItemFill.setAlpha(255);
    }

    // ⚡ DETAILED CRYSTALLINE PLASMA SPIKES WITH GLOWING TIPS
    private void renderDetailedSpikes(Canvas c, Obstacle o, float sy) {
        // Metallic Base Rail
        pObs.setColor(Color.parseColor("#1E293B"));
        c.drawRect(o.x, sy + o.h - 5f * dpr, o.x + o.w, sy + o.h, pObs);

        float spikeW = 20f * dpr;
        int count = (int) (o.w / spikeW);

        for (int i = 0; i < count; i++) {
            float sx = o.x + i * spikeW;
            float midX = sx + spikeW / 2f;
            float apexY = sy;
            float baseY = sy + o.h - 4f * dpr;

            // Shaded Left Facet
            Path leftFacet = new Path();
            leftFacet.moveTo(sx, baseY);
            leftFacet.lineTo(midX, apexY);
            leftFacet.lineTo(midX, baseY);
            leftFacet.close();
            pItemFill.setColor(Color.parseColor("#DC2626")); // Darker Crimson
            c.drawPath(leftFacet, pItemFill);

            // Shaded Right Facet
            Path rightFacet = new Path();
            rightFacet.moveTo(midX, baseY);
            rightFacet.lineTo(midX, apexY);
            rightFacet.lineTo(sx + spikeW, baseY);
            rightFacet.close();
            pItemFill.setColor(Color.parseColor("#F87171")); // Lighter Coral
            c.drawPath(rightFacet, pItemFill);

            // Center Energy Spine
            pItemStroke.setColor(Color.WHITE);
            pItemStroke.setStrokeWidth(1.5f * dpr);
            c.drawLine(midX, baseY, midX, apexY, pItemStroke);

            // Glowing Plasma Tip Apex Flare
            float flare = 0.7f + 0.3f * (float) Math.sin(globalAnimTime * 10f + i);
            pItemGlow.setColor(Color.parseColor("#FFD60A"));
            pItemGlow.setAlpha((int) (255 * flare));
            c.drawCircle(midX, apexY, 2.8f * dpr, pItemGlow);
        }
    }

    // 💎 REALISTIC 3D FACETED BRILLIANT DIAMOND
    private void renderFacetedDiamond(Canvas c, float r) {
        pItemGlow.setColor(Color.parseColor("#00F0FF"));
        pItemGlow.setAlpha(65);
        c.drawCircle(0, 0, r * 1.35f, pItemGlow);

        float topW = r * 0.85f;
        float midW = r * 1.30f;
        float topY = -r * 0.95f;
        float midY = -r * 0.25f;
        float botY = r * 1.15f;

        Path table = new Path();
        table.moveTo(-topW, topY);
        table.lineTo(topW, topY);
        table.lineTo(topW * 0.6f, midY);
        table.lineTo(-topW * 0.6f, midY);
        table.close();
        pItemFill.setColor(Color.parseColor("#E0F2FE"));
        c.drawPath(table, pItemFill);

        Path crownL = new Path();
        crownL.moveTo(-topW, topY);
        crownL.lineTo(-topW * 0.6f, midY);
        crownL.lineTo(-midW, midY);
        crownL.close();
        pItemFill.setColor(Color.parseColor("#7DD3FC"));
        c.drawPath(crownL, pItemFill);

        Path crownR = new Path();
        crownR.moveTo(topW, topY);
        crownR.lineTo(midW, midY);
        crownR.lineTo(topW * 0.6f, midY);
        crownR.close();
        pItemFill.setColor(Color.parseColor("#BAE6FD"));
        c.drawPath(crownR, pItemFill);

        Path pavCenter = new Path();
        pavCenter.moveTo(-topW * 0.6f, midY);
        pavCenter.lineTo(topW * 0.6f, midY);
        pavCenter.lineTo(0, botY);
        pavCenter.close();
        pItemFill.setColor(Color.parseColor("#0284C7"));
        c.drawPath(pavCenter, pItemFill);

        Path pavL = new Path();
        pavL.moveTo(-midW, midY);
        pavL.lineTo(-topW * 0.6f, midY);
        pavL.lineTo(0, botY);
        pavL.close();
        pItemFill.setColor(Color.parseColor("#0369A1"));
        c.drawPath(pavL, pItemFill);

        Path pavR = new Path();
        pavR.moveTo(midW, midY);
        pavR.lineTo(0, botY);
        pavR.lineTo(topW * 0.6f, midY);
        pavR.close();
        pItemFill.setColor(Color.parseColor("#38BDF8"));
        c.drawPath(pavR, pItemFill);

        pItemStroke.setColor(Color.WHITE);
        pItemStroke.setStrokeWidth(1.2f * dpr);
        c.drawPath(table, pItemStroke);
        c.drawPath(crownL, pItemStroke);
        c.drawPath(crownR, pItemStroke);
        c.drawPath(pavCenter, pItemStroke);
        c.drawPath(pavL, pItemStroke);
        c.drawPath(pavR, pItemStroke);

        float glintX = -topW * 0.4f;
        float glintY = topY + 2f * dpr;
        pItemStroke.setColor(Color.WHITE);
        pItemStroke.setStrokeWidth(1.8f * dpr);
        c.drawLine(glintX - 5f * dpr, glintY, glintX + 5f * dpr, glintY, pItemStroke);
        c.drawLine(glintX, glintY - 5f * dpr, glintX, glintY + 5f * dpr, pItemStroke);
    }

    // 🛡️ REALISTIC HEATER SHIELD
    private void renderRealisticShield(Canvas c, float r) {
        pItemGlow.setColor(Color.parseColor("#10B981"));
        pItemGlow.setAlpha(65);
        c.drawCircle(0, 0, r * 1.35f, pItemGlow);

        float w = r * 1.05f;
        float topY = -r * 1.05f;
        float midY = -r * 0.1f;
        float botY = r * 1.15f;

        Path outerShield = new Path();
        outerShield.moveTo(-w, topY);
        outerShield.quadTo(0, topY - 3f * dpr, w, topY);
        outerShield.quadTo(w, midY, 0, botY);
        outerShield.quadTo(-w, midY, -w, topY);
        outerShield.close();

        pItemFill.setColor(Color.parseColor("#F59E0B"));
        c.drawPath(outerShield, pItemFill);

        float inW = w * 0.82f;
        float inTopY = topY + 3f * dpr;
        float inMidY = midY - 2f * dpr;
        float inBotY = botY - 4f * dpr;

        Path innerShield = new Path();
        innerShield.moveTo(-inW, inTopY);
        innerShield.quadTo(0, inTopY - 2f * dpr, inW, inTopY);
        innerShield.quadTo(inW, inMidY, 0, inBotY);
        innerShield.quadTo(-inW, inMidY, -inW, inTopY);
        innerShield.close();

        pItemFill.setColor(Color.parseColor("#059669"));
        c.drawPath(innerShield, pItemFill);

        c.save();
        c.clipPath(innerShield);
        pItemFill.setColor(Color.parseColor("#34D399"));
        c.drawRect(-inW, inTopY, 0, inBotY, pItemFill);
        c.restore();

        pItemFill.setColor(Color.WHITE);
        Path crest = new Path();
        crest.moveTo(-2.5f * dpr, -r * 0.55f);
        crest.lineTo(2.5f * dpr, -r * 0.55f);
        crest.lineTo(2.5f * dpr, r * 0.45f);
        crest.lineTo(-2.5f * dpr, r * 0.45f);
        crest.close();
        c.drawPath(crest, pItemFill);

        Path crestCross = new Path();
        crestCross.moveTo(-r * 0.45f, -r * 0.25f);
        crestCross.lineTo(r * 0.45f, -r * 0.25f);
        crestCross.lineTo(r * 0.45f, -r * 0.05f);
        crestCross.lineTo(-r * 0.45f, -r * 0.05f);
        crestCross.close();
        c.drawPath(crestCross, pItemFill);

        pItemStroke.setColor(Color.parseColor("#78350F"));
        pItemStroke.setStrokeWidth(1.5f * dpr);
        c.drawPath(outerShield, pItemStroke);
    }

    // ⏳ REALISTIC 3D GOLDEN HOURGLASS
    private void renderRealisticHourglass(Canvas c, float r) {
        pItemGlow.setColor(Color.parseColor("#F59E0B"));
        pItemGlow.setAlpha(65);
        c.drawCircle(0, 0, r * 1.35f, pItemGlow);

        float topY = -r * 1.15f;
        float botY = r * 1.15f;
        float w = r * 0.95f;

        pItemFill.setColor(Color.parseColor("#D97706"));
        c.drawRoundRect(-w * 1.1f, topY, w * 1.1f, topY + 5f * dpr, 2f * dpr, 2f * dpr, pItemFill);
        c.drawRoundRect(-w * 1.1f, botY - 5f * dpr, w * 1.1f, botY, 2f * dpr, 2f * dpr, pItemFill);

        Path glass = new Path();
        glass.moveTo(-w * 0.85f, topY + 5f * dpr);
        glass.lineTo(w * 0.85f, topY + 5f * dpr);
        glass.lineTo(2.5f * dpr, 0);
        glass.lineTo(w * 0.85f, botY - 5f * dpr);
        glass.lineTo(-w * 0.85f, botY - 5f * dpr);
        glass.lineTo(-2.5f * dpr, 0);
        glass.close();

        pItemFill.setColor(Color.parseColor("#26384D"));
        c.drawPath(glass, pItemFill);

        Path topSand = new Path();
        topSand.moveTo(-w * 0.65f, topY + 8f * dpr);
        topSand.lineTo(w * 0.65f, topY + 8f * dpr);
        topSand.lineTo(1.5f * dpr, -1f * dpr);
        topSand.lineTo(-1.5f * dpr, -1f * dpr);
        topSand.close();
        pItemFill.setColor(Color.parseColor("#FBBF24"));
        c.drawPath(topSand, pItemFill);

        pItemStroke.setColor(Color.parseColor("#FDE047"));
        pItemStroke.setStrokeWidth(2f * dpr);
        c.drawLine(0, -1f * dpr, 0, r * 0.55f, pItemStroke);

        Path botSand = new Path();
        botSand.moveTo(-w * 0.75f, botY - 5f * dpr);
        botSand.lineTo(w * 0.75f, botY - 5f * dpr);
        botSand.lineTo(0, r * 0.35f);
        botSand.close();
        pItemFill.setColor(Color.parseColor("#F59E0B"));
        c.drawPath(botSand, pItemFill);

        pItemStroke.setColor(Color.WHITE);
        pItemStroke.setStrokeWidth(1.5f * dpr);
        c.drawPath(glass, pItemStroke);

        pItemStroke.setColor(Color.parseColor("#F59E0B"));
        pItemStroke.setStrokeWidth(2.5f * dpr);
        c.drawLine(-w * 0.95f, topY + 4f * dpr, -w * 0.95f, botY - 4f * dpr, pItemStroke);
        c.drawLine(w * 0.95f, topY + 4f * dpr, w * 0.95f, botY - 4f * dpr, pItemStroke);
    }

    // High-Tech Animated Sci-Fi Capsule Probe
    private void renderSciFiCapsule(Canvas c) {
        for (Ghost g : ghostTrails) {
            c.save();
            c.translate(g.x, g.y);
            c.rotate(g.tilt);
            pAura.setColor(Color.parseColor("#F59E0B"));
            pAura.setAlpha((int) (255 * g.alpha * 0.4f));
            c.drawCircle(0, 0, pRadius * 1.1f, pAura);
            c.restore();
        }

        if (shield) {
            float rot = globalAnimTime * 90f;
            c.save();
            c.translate(px, py);
            c.rotate(rot);

            pShield.setColor(Color.parseColor("#10B981"));
            pShield.setStyle(Paint.Style.STROKE);
            pShield.setStrokeWidth(3f * dpr);
            c.drawCircle(0, 0, pRadius * 1.55f, pShield);

            pShield.setStyle(Paint.Style.FILL);
            c.drawCircle(pRadius * 1.55f, 0, 3.5f * dpr, pShield);
            c.drawCircle(-pRadius * 1.55f, 0, 3.5f * dpr, pShield);

            pShield.setAlpha(40);
            c.drawCircle(0, 0, pRadius * 1.55f, pShield);
            pShield.setAlpha(255);
            c.restore();
        }

        c.save();
        c.translate(px, py);
        c.rotate(playerTilt);

        // Aerodynamic Wings
        Path wingL = new Path();
        wingL.moveTo(-pRadius * 0.5f, -pRadius * 0.2f);
        wingL.lineTo(-pRadius * 1.35f, -pRadius * 0.8f);
        wingL.lineTo(-pRadius * 0.9f, pRadius * 0.2f);
        wingL.close();
        pObs.setColor(Color.parseColor("#1E293B"));
        c.drawPath(wingL, pObs);

        Path wingR = new Path();
        wingR.moveTo(pRadius * 0.5f, -pRadius * 0.2f);
        wingR.lineTo(pRadius * 1.35f, -pRadius * 0.8f);
        wingR.lineTo(pRadius * 0.9f, pRadius * 0.2f);
        wingR.close();
        c.drawPath(wingR, pObs);

        pStroke.setColor(Color.parseColor("#00F0FF"));
        pStroke.setStrokeWidth(1.5f * dpr);
        c.drawPath(wingL, pStroke);
        c.drawPath(wingR, pStroke);

        // Main Capsule Body
        RectF body = new RectF(-pRadius * 0.75f, -pRadius * 1.1f, pRadius * 0.75f, pRadius * 1.1f);
        pObs.setColor(Color.parseColor("#F8FAFC"));
        c.drawRoundRect(body, pRadius * 0.75f, pRadius * 0.75f, pObs);

        pStroke.setColor(Color.parseColor("#0F172A"));
        pStroke.setStrokeWidth(2.5f * dpr);
        c.drawRoundRect(body, pRadius * 0.75f, pRadius * 0.75f, pStroke);

        // Ion Cockpit Eye
        float pulse = 0.8f + 0.2f * (float) Math.sin(globalAnimTime * 8f);
        pObs.setColor(Color.parseColor("#00F0FF"));
        c.drawCircle(0, pRadius * 0.15f, pRadius * 0.42f * pulse, pObs);

        pObs.setColor(Color.WHITE);
        c.drawCircle(1.5f * dpr, pRadius * 0.08f, 2.5f * dpr, pObs);

        c.restore();
    }

    private void renderInGameHUD(Canvas c) {
        float topPad = 48f * dpr;

        // Depth Badge
        pCard.setColor(Color.parseColor("#E6111827"));
        RectF depthBadge = new RectF(wallMargin + 6f * dpr, topPad, wallMargin + 140f * dpr, topPad + 44f * dpr);
        c.drawRoundRect(depthBadge, 12f * dpr, 12f * dpr, pCard);
        pTextBold.setColor(Color.WHITE);
        pTextBold.setTextSize(20f * dpr);
        pTextBold.setTextAlign(Paint.Align.LEFT);
        c.drawText(depth + " M", wallMargin + 18f * dpr, topPad + 30f * dpr, pTextBold);

        // Pause Button
        RectF pauseBtn = new RectF(screenW - wallMargin - 46f * dpr, topPad, screenW - wallMargin - 6f * dpr, topPad + 44f * dpr);
        c.drawRoundRect(pauseBtn, 12f * dpr, 12f * dpr, pCard);
        pVectorIcon.setColor(Color.WHITE);
        c.drawRect(pauseBtn.centerX() - 6f * dpr, topPad + 13f * dpr, pauseBtn.centerX() - 2f * dpr, topPad + 31f * dpr, pVectorIcon);
        c.drawRect(pauseBtn.centerX() + 2f * dpr, topPad + 13f * dpr, pauseBtn.centerX() + 6f * dpr, topPad + 31f * dpr, pVectorIcon);

        // Active Buffs
        float by = topPad + 64f * dpr;
        pTextBold.setTextSize(14f * dpr);
        pTextBold.setTextAlign(Paint.Align.LEFT);
        if (shield) {
            pTextBold.setColor(Color.parseColor("#10B981"));
            c.drawText("[ " + Localization.get("shield") + " ]", wallMargin + 10f * dpr, by, pTextBold);
        }
        if (slowMoTimer > 0) {
            pTextBold.setColor(Color.parseColor("#F59E0B"));
            c.drawText("[ " + Localization.get("slowmo") + ": " + (int)Math.ceil(slowMoTimer) + "s ]", wallMargin + 85f * dpr, by, pTextBold);
        }
    }

    // Animated Translucent Button Renderer
    private void drawModernButton(Canvas c, RectF r, String text, String iconType, int btnIdx) {
        c.save();
        float scale = (btnIdx >= 0 && btnIdx < btnScales.length) ? btnScales[btnIdx] : 1.0f;
        if (scale != 1.0f) {
            c.scale(scale, scale, r.centerX(), r.centerY());
        }

        pBtn.setColor((pressedBtnId == btnIdx) ? Color.parseColor("#EE2E3642") : Color.parseColor("#CC1E232B"));
        c.drawRoundRect(r, 14f * dpr, 14f * dpr, pBtn);
        c.drawRoundRect(r, 14f * dpr, 14f * dpr, pBtnBorder);

        float iconCenterX = r.left + 32f * dpr;
        float iconCenterY = r.centerY();

        if ("play".equals(iconType)) {
            Path p = new Path();
            p.moveTo(iconCenterX - 6f * dpr, iconCenterY - 9f * dpr);
            p.lineTo(iconCenterX + 9f * dpr, iconCenterY);
            p.lineTo(iconCenterX - 6f * dpr, iconCenterY + 9f * dpr);
            p.close();
            c.drawPath(p, pIconStroke);
        } else if ("speed".equals(iconType)) {
            Path p = new Path();
            p.moveTo(iconCenterX + 2f * dpr, iconCenterY - 10f * dpr);
            p.lineTo(iconCenterX - 7f * dpr, iconCenterY + 1f * dpr);
            p.lineTo(iconCenterX - 1f * dpr, iconCenterY + 1f * dpr);
            p.lineTo(iconCenterX - 3f * dpr, iconCenterY + 10f * dpr);
            p.lineTo(iconCenterX + 7f * dpr, iconCenterY - 1f * dpr);
            p.lineTo(iconCenterX + 1f * dpr, iconCenterY - 1f * dpr);
            p.close();
            pVectorIcon.setColor(speedAccelerationEnabled ? Color.parseColor("#F59E0B") : Color.parseColor("#94A3B8"));
            c.drawPath(p, pVectorIcon);
        } else if ("settings".equals(iconType)) {
            c.drawCircle(iconCenterX, iconCenterY, 6f * dpr, pIconStroke);
            for (int a = 0; a < 6; a++) {
                double rad = Math.toRadians(a * 60);
                float x1 = iconCenterX + (float)Math.cos(rad) * 6f * dpr;
                float y1 = iconCenterY + (float)Math.sin(rad) * 6f * dpr;
                float x2 = iconCenterX + (float)Math.cos(rad) * 9f * dpr;
                float y2 = iconCenterY + (float)Math.sin(rad) * 9f * dpr;
                c.drawLine(x1, y1, x2, y2, pIconStroke);
            }
        } else if ("restart".equals(iconType)) {
            RectF arc = new RectF(iconCenterX - 7f * dpr, iconCenterY - 7f * dpr, iconCenterX + 7f * dpr, iconCenterY + 7f * dpr);
            c.drawArc(arc, 45, 270, false, pIconStroke);
            c.drawLine(iconCenterX + 4f * dpr, iconCenterY - 9f * dpr, iconCenterX + 8f * dpr, iconCenterY - 5f * dpr, pIconStroke);
            c.drawLine(iconCenterX + 4f * dpr, iconCenterY - 1f * dpr, iconCenterX + 8f * dpr, iconCenterY - 5f * dpr, pIconStroke);
        } else if ("exit".equals(iconType)) {
            c.drawLine(iconCenterX - 7f * dpr, iconCenterY - 8f * dpr, iconCenterX - 7f * dpr, iconCenterY + 8f * dpr, pIconStroke);
            c.drawLine(iconCenterX - 7f * dpr, iconCenterY - 8f * dpr, iconCenterX + 2f * dpr, iconCenterY - 8f * dpr, pIconStroke);
            c.drawLine(iconCenterX - 7f * dpr, iconCenterY + 8f * dpr, iconCenterX + 2f * dpr, iconCenterY + 8f * dpr, pIconStroke);
            c.drawLine(iconCenterX - 2f * dpr, iconCenterY, iconCenterX + 8f * dpr, iconCenterY, pIconStroke);
            c.drawLine(iconCenterX + 4f * dpr, iconCenterY - 4f * dpr, iconCenterX + 8f * dpr, iconCenterY, pIconStroke);
            c.drawLine(iconCenterX + 4f * dpr, iconCenterY + 4f * dpr, iconCenterX + 8f * dpr, iconCenterY, pIconStroke);
        }

        pTextBold.setColor(Color.WHITE);
        pTextBold.setTextSize(18f * dpr);
        pTextBold.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, r.centerX() + 8f * dpr, r.centerY() + 7f * dpr, pTextBold);

        c.restore();
    }

    private void renderMainMenu(Canvas c) {
        c.drawRect(0, 0, screenW, screenH, pOverlay);

        float topY = screenH * 0.22f;

        pTitle.setColor(Color.WHITE);
        pTitle.setTextSize(44f * dpr);
        pTitle.setTextAlign(Paint.Align.CENTER);
        c.drawText(Localization.get("app_title"), screenW / 2f, topY, pTitle);

        pText.setColor(Color.parseColor("#94A3B8"));
        pText.setTextSize(17f * dpr);
        c.drawText(Localization.get("best_record") + ": " + bestDepth + " M", screenW / 2f, topY + 34f * dpr, pText);

        float btnW = screenW * 0.84f;
        float btnH = 56f * dpr;
        float startX = (screenW - btnW) / 2f;
        float startY = topY + 75f * dpr;
        float gap = 16f * dpr;

        // 1. PLAY
        RectF playBtn = new RectF(startX, startY, startX + btnW, startY + btnH);
        drawModernButton(c, playBtn, Localization.get("play"), "play", 0);

        // 2. SPEED ACCELERATION TOGGLE
        String accelStr = Localization.get("acceleration") + ": " + (speedAccelerationEnabled ? Localization.get("on") : Localization.get("off"));
        RectF accelBtn = new RectF(startX, startY + (btnH + gap), startX + btnW, startY + (btnH + gap) + btnH);
        drawModernButton(c, accelBtn, accelStr, "speed", 1);

        // 3. SETTINGS
        RectF setBtn = new RectF(startX, startY + (btnH + gap) * 2f, startX + btnW, startY + (btnH + gap) * 2f + btnH);
        drawModernButton(c, setBtn, Localization.get("settings"), "settings", 2);

        pText.setColor(Color.parseColor("#64748B"));
        pText.setTextSize(14f * dpr);
        String sStatus = Localization.get("sound") + ": " + (sounds.enabled ? Localization.get("on") : Localization.get("off"));
        String vStatus = Localization.get("vibration") + ": " + (haptics.enabled ? Localization.get("on") : Localization.get("off"));
        c.drawText(sStatus + "  |  " + vStatus, screenW / 2f, startY + (btnH + gap) * 3f + 20f * dpr, pText);
    }

    private void renderPauseMenu(Canvas c) {
        c.drawRect(0, 0, screenW, screenH, pOverlay);

        float topY = screenH * 0.24f;

        pTitle.setColor(Color.WHITE);
        pTitle.setTextSize(38f * dpr);
        pTitle.setTextAlign(Paint.Align.CENTER);
        c.drawText(Localization.get("pause"), screenW / 2f, topY, pTitle);

        pText.setColor(Color.parseColor("#94A3B8"));
        pText.setTextSize(17f * dpr);
        c.drawText(Localization.get("current_depth") + ": " + depth + " M", screenW / 2f, topY + 30f * dpr, pText);

        float btnW = screenW * 0.84f;
        float btnH = 54f * dpr;
        float startX = (screenW - btnW) / 2f;
        float startY = topY + 68f * dpr;
        float gap = 16f * dpr;

        // Resume
        RectF resBtn = new RectF(startX, startY, startX + btnW, startY + btnH);
        drawModernButton(c, resBtn, Localization.get("resume"), "play", 0);

        // Settings
        RectF setBtn = new RectF(startX, startY + (btnH + gap), startX + btnW, startY + (btnH + gap) + btnH);
        drawModernButton(c, setBtn, Localization.get("settings"), "settings", 1);

        // Restart
        RectF rstBtn = new RectF(startX, startY + (btnH + gap) * 2f, startX + btnW, startY + (btnH + gap) * 2f + btnH);
        drawModernButton(c, rstBtn, Localization.get("restart"), "restart", 2);

        // Main Menu
        RectF menuBtn = new RectF(startX, startY + (btnH + gap) * 3f, startX + btnW, startY + (btnH + gap) * 3f + btnH);
        drawModernButton(c, menuBtn, Localization.get("to_menu"), "exit", 3);
    }

    private void renderGameOverMenu(Canvas c) {
        c.drawRect(0, 0, screenW, screenH, pOverlay);

        float topY = screenH * 0.22f;

        pTitle.setColor(Color.parseColor("#EF4444"));
        pTitle.setTextSize(40f * dpr);
        pTitle.setTextAlign(Paint.Align.CENTER);
        c.drawText(Localization.get("game_over"), screenW / 2f, topY, pTitle);

        pText.setColor(Color.WHITE);
        pText.setTextSize(19f * dpr);
        c.drawText(Localization.get("current_depth") + ": " + depth + " M", screenW / 2f, topY + 36f * dpr, pText);

        pTextBold.setColor(Color.parseColor("#10B981"));
        pTextBold.setTextSize(19f * dpr);
        c.drawText(Localization.get("best_record") + ": " + bestDepth + " M", screenW / 2f, topY + 66f * dpr, pTextBold);

        float btnW = screenW * 0.84f;
        float btnH = 54f * dpr;
        float startX = (screenW - btnW) / 2f;
        float startY = topY + 108f * dpr;
        float gap = 16f * dpr;

        // Restart
        RectF rstBtn = new RectF(startX, startY, startX + btnW, startY + btnH);
        drawModernButton(c, rstBtn, Localization.get("restart"), "restart", 0);

        // Main menu
        RectF menuBtn = new RectF(startX, startY + (btnH + gap), startX + btnW, startY + (btnH + gap) + btnH);
        drawModernButton(c, menuBtn, Localization.get("to_menu"), "exit", 1);
    }

    private void renderSettingsMenu(Canvas c) {
        c.drawRect(0, 0, screenW, screenH, pOverlay);

        float cardW = screenW * 0.92f;
        float cardH = screenH * 0.92f;
        float cx = (screenW - cardW) / 2f;
        float cy = (screenH - cardH) / 2f;

        RectF card = new RectF(cx, cy, cx + cardW, cy + cardH);
        pCard.setColor(Color.parseColor("#E610141D"));
        c.drawRoundRect(card, 20f * dpr, 20f * dpr, pCard);
        c.drawRoundRect(card, 20f * dpr, 20f * dpr, pBtnBorder);

        pTextBold.setColor(Color.WHITE);
        pTextBold.setTextSize(24f * dpr);
        pTextBold.setTextAlign(Paint.Align.CENTER);
        c.drawText(Localization.get("settings"), screenW / 2f, cy + 34f * dpr, pTextBold);

        float sy = cy + 46f * dpr;
        float rowH = 38f * dpr;
        float halfW = (cardW - 32f * dpr) / 2f;

        // 1. Language
        pTextBold.setTextSize(13.5f * dpr);
        pTextBold.setTextAlign(Paint.Align.LEFT);
        pTextBold.setColor(Color.parseColor("#9CA3AF"));
        c.drawText(Localization.get("language") + ":", cx + 14f * dpr, sy + 13f * dpr, pTextBold);

        RectF langRu = new RectF(cx + 14f * dpr, sy + 18f * dpr, cx + 14f * dpr + halfW, sy + 18f * dpr + 30f * dpr);
        RectF langEn = new RectF(cx + 18f * dpr + halfW, sy + 18f * dpr, cx + cardW - 14f * dpr, sy + 18f * dpr + 30f * dpr);
        pBtn.setColor((Localization.lang == 0) ? Color.parseColor("#0284C7") : Color.parseColor("#1F2530"));
        c.drawRoundRect(langRu, 8f * dpr, 8f * dpr, pBtn);
        pTextBold.setColor(Color.WHITE);
        pTextBold.setTextSize(13.5f * dpr);
        pTextBold.setTextAlign(Paint.Align.CENTER);
        c.drawText("Русский (RU)", langRu.centerX(), langRu.centerY() + 4f * dpr, pTextBold);

        pBtn.setColor((Localization.lang == 1) ? Color.parseColor("#0284C7") : Color.parseColor("#1F2530"));
        c.drawRoundRect(langEn, 8f * dpr, 8f * dpr, pBtn);
        c.drawText("English (EN)", langEn.centerX(), langEn.centerY() + 4f * dpr, pTextBold);

        // 2. Sound
        sy += rowH + 14f * dpr;
        pTextBold.setTextAlign(Paint.Align.LEFT);
        pTextBold.setColor(Color.parseColor("#9CA3AF"));
        c.drawText(Localization.get("sound") + ":", cx + 14f * dpr, sy + 13f * dpr, pTextBold);

        RectF sndOn = new RectF(cx + 14f * dpr, sy + 18f * dpr, cx + 14f * dpr + halfW, sy + 18f * dpr + 30f * dpr);
        RectF sndOff = new RectF(cx + 18f * dpr + halfW, sy + 18f * dpr, cx + cardW - 14f * dpr, sy + 18f * dpr + 30f * dpr);
        pBtn.setColor(sounds.enabled ? Color.parseColor("#10B981") : Color.parseColor("#1F2530"));
        c.drawRoundRect(sndOn, 8f * dpr, 8f * dpr, pBtn);
        pTextBold.setColor(Color.WHITE);
        pTextBold.setTextAlign(Paint.Align.CENTER);
        c.drawText(Localization.get("on"), sndOn.centerX(), sndOn.centerY() + 4f * dpr, pTextBold);

        pBtn.setColor(!sounds.enabled ? Color.parseColor("#DC2626") : Color.parseColor("#1F2530"));
        c.drawRoundRect(sndOff, 8f * dpr, 8f * dpr, pBtn);
        c.drawText(Localization.get("off"), sndOff.centerX(), sndOff.centerY() + 4f * dpr, pTextBold);

        // 3. Vibration
        sy += rowH + 14f * dpr;
        pTextBold.setTextAlign(Paint.Align.LEFT);
        pTextBold.setColor(Color.parseColor("#9CA3AF"));
        c.drawText(Localization.get("vibration") + ":", cx + 14f * dpr, sy + 13f * dpr, pTextBold);

        RectF vibOn = new RectF(cx + 14f * dpr, sy + 18f * dpr, cx + 14f * dpr + halfW, sy + 18f * dpr + 30f * dpr);
        RectF vibOff = new RectF(cx + 18f * dpr + halfW, sy + 18f * dpr, cx + cardW - 14f * dpr, sy + 18f * dpr + 30f * dpr);
        pBtn.setColor(haptics.enabled ? Color.parseColor("#10B981") : Color.parseColor("#1F2530"));
        c.drawRoundRect(vibOn, 8f * dpr, 8f * dpr, pBtn);
        pTextBold.setColor(Color.WHITE);
        pTextBold.setTextAlign(Paint.Align.CENTER);
        c.drawText(Localization.get("on"), vibOn.centerX(), vibOn.centerY() + 4f * dpr, pTextBold);

        pBtn.setColor(!haptics.enabled ? Color.parseColor("#DC2626") : Color.parseColor("#1F2530"));
        c.drawRoundRect(vibOff, 8f * dpr, 8f * dpr, pBtn);
        c.drawText(Localization.get("off"), vibOff.centerX(), vibOff.centerY() + 4f * dpr, pTextBold);

        // 4. Control Mode
        sy += rowH + 14f * dpr;
        pTextBold.setTextAlign(Paint.Align.LEFT);
        pTextBold.setColor(Color.parseColor("#9CA3AF"));
        c.drawText(Localization.get("controls") + ":", cx + 14f * dpr, sy + 13f * dpr, pTextBold);

        RectF ctrl1 = new RectF(cx + 14f * dpr, sy + 18f * dpr, cx + 14f * dpr + halfW, sy + 18f * dpr + 30f * dpr);
        RectF ctrl2 = new RectF(cx + 18f * dpr + halfW, sy + 18f * dpr, cx + cardW - 14f * dpr, sy + 18f * dpr + 30f * dpr);
        pBtn.setColor((controlMode == 0) ? Color.parseColor("#0284C7") : Color.parseColor("#1F2530"));
        c.drawRoundRect(ctrl1, 8f * dpr, 8f * dpr, pBtn);
        pTextBold.setColor(Color.WHITE);
        pTextBold.setTextSize(13.5f * dpr);
        pTextBold.setTextAlign(Paint.Align.CENTER);
        c.drawText(Localization.get("drag"), ctrl1.centerX(), ctrl1.centerY() + 4f * dpr, pTextBold);

        pBtn.setColor((controlMode == 1) ? Color.parseColor("#0284C7") : Color.parseColor("#1F2530"));
        c.drawRoundRect(ctrl2, 8f * dpr, 8f * dpr, pBtn);
        c.drawText(Localization.get("zones"), ctrl2.centerX(), ctrl2.centerY() + 4f * dpr, pTextBold);

        // 5. FX Toggles
        sy += rowH + 14f * dpr;
        pTextBold.setTextAlign(Paint.Align.LEFT);
        pTextBold.setColor(Color.parseColor("#9CA3AF"));
        c.drawText(Localization.get("particles") + " / " + Localization.get("shake") + ":", cx + 14f * dpr, sy + 13f * dpr, pTextBold);

        RectF fxShake = new RectF(cx + 14f * dpr, sy + 18f * dpr, cx + 14f * dpr + halfW, sy + 18f * dpr + 30f * dpr);
        RectF fxPart = new RectF(cx + 18f * dpr + halfW, sy + 18f * dpr, cx + cardW - 14f * dpr, sy + 18f * dpr + 30f * dpr);
        pBtn.setColor(screenShakeEnabled ? Color.parseColor("#10B981") : Color.parseColor("#374151"));
        c.drawRoundRect(fxShake, 8f * dpr, 8f * dpr, pBtn);
        pTextBold.setColor(Color.WHITE);
        pTextBold.setTextSize(12.5f * dpr);
        pTextBold.setTextAlign(Paint.Align.CENTER);
        c.drawText(Localization.get("shake") + ": " + (screenShakeEnabled ? Localization.get("on") : Localization.get("off")), fxShake.centerX(), fxShake.centerY() + 4f * dpr, pTextBold);

        pBtn.setColor(particlesEnabled ? Color.parseColor("#10B981") : Color.parseColor("#374151"));
        c.drawRoundRect(fxPart, 8f * dpr, 8f * dpr, pBtn);
        c.drawText(Localization.get("particles") + ": " + (particlesEnabled ? Localization.get("on") : Localization.get("off")), fxPart.centerX(), fxPart.centerY() + 4f * dpr, pTextBold);

        // 6. Reset Score
        sy += rowH + 12f * dpr;
        RectF rstScoreBtn = new RectF(cx + 14f * dpr, sy + 14f * dpr, cx + cardW - 14f * dpr, sy + 44f * dpr);
        pBtn.setColor(Color.parseColor("#26181B"));
        c.drawRoundRect(rstScoreBtn, 8f * dpr, 8f * dpr, pBtn);
        pTextBold.setColor(Color.parseColor("#F87171"));
        pTextBold.setTextSize(13.5f * dpr);
        c.drawText(Localization.get("reset_score") + " (" + bestDepth + " M)", screenW / 2f, rstScoreBtn.centerY() + 4f * dpr, pTextBold);

        // 7. DEVELOPER & ABOUT SECTION WITH REAL AVATAR AND LABELED SOCIAL LINKS
        sy += 48f * dpr;
        float aboutH = 124f * dpr;
        RectF aboutBox = new RectF(cx + 14f * dpr, sy, cx + cardW - 14f * dpr, sy + aboutH);
        pCard.setColor(Color.parseColor("#CC18202E"));
        c.drawRoundRect(aboutBox, 14f * dpr, 14f * dpr, pCard);
        pBtnBorder.setColor(Color.parseColor("#3300F0FF"));
        c.drawRoundRect(aboutBox, 14f * dpr, 14f * dpr, pBtnBorder);

        // User Avatar on Left
        float avatarSize = 40f * dpr;
        float avX = aboutBox.left + 12f * dpr;
        float avY = aboutBox.top + 10f * dpr;
        RectF avRect = new RectF(avX, avY, avX + avatarSize, avY + avatarSize);

        if (authorAvatarBitmap != null) {
            c.save();
            Path avClip = new Path();
            avClip.addRoundRect(avRect, 10f * dpr, 10f * dpr, Path.Direction.CW);
            c.clipPath(avClip);
            c.drawBitmap(authorAvatarBitmap, null, avRect, null);
            c.restore();

            pStroke.setColor(Color.parseColor("#F59E0B"));
            pStroke.setStrokeWidth(1.8f * dpr);
            c.drawRoundRect(avRect, 10f * dpr, 10f * dpr, pStroke);
        }

        // About Text next to avatar (Title + 2 lines of description)
        float textStartX = avX + avatarSize + 10f * dpr;
        pTextBold.setTextAlign(Paint.Align.LEFT);
        pTextBold.setColor(Color.WHITE);
        pTextBold.setTextSize(15.5f * dpr);
        c.drawText("openprop", textStartX, avY + 12f * dpr, pTextBold);

        pText.setTextAlign(Paint.Align.LEFT);
        pText.setColor(Color.parseColor("#94A3B8"));
        pText.setTextSize(11f * dpr);
        c.drawText(Localization.get("about_desc_1"), textStartX, avY + 25f * dpr, pText);
        c.drawText(Localization.get("about_desc_2"), textStartX, avY + 37f * dpr, pText);

        // Developer Section Header Title (With proper spacing below avatar)
        float devHeaderY = aboutBox.top + 68f * dpr;
        pTextBold.setColor(Color.parseColor("#38BDF8"));
        pTextBold.setTextSize(11.5f * dpr);
        pTextBold.setTextAlign(Paint.Align.LEFT);
        c.drawText(Localization.get("developer"), aboutBox.left + 12f * dpr, devHeaderY, pTextBold);

        // Social Links: Telegram & GitHub Badges
        float badgeY = devHeaderY + 6f * dpr;
        float badgeW = (aboutBox.width() - 24f * dpr) / 2f;

        // 1. Telegram Badge
        RectF tgBadge = new RectF(aboutBox.left + 10f * dpr, badgeY, aboutBox.left + 10f * dpr + badgeW, badgeY + 34f * dpr);
        pBtn.setColor(Color.parseColor("#1D2B3A"));
        c.drawRoundRect(tgBadge, 8f * dpr, 8f * dpr, pBtn);

        float tgCx = tgBadge.left + 14f * dpr;
        float tgCy = tgBadge.centerY();
        pItemFill.setColor(Color.parseColor("#2AABEE"));
        c.drawCircle(tgCx, tgCy, 9f * dpr, pItemFill);

        Path tgPlane = new Path();
        tgPlane.moveTo(tgCx + 4.5f * dpr, tgCy - 4f * dpr);
        tgPlane.lineTo(tgCx - 4f * dpr, tgCy - 1f * dpr);
        tgPlane.lineTo(tgCx - 1f * dpr, tgCy + 1.2f * dpr);
        tgPlane.lineTo(tgCx - 1f * dpr, tgCy + 4f * dpr);
        tgPlane.lineTo(tgCx + 1f * dpr, tgCy + 2f * dpr);
        tgPlane.lineTo(tgCx + 4f * dpr, tgCy + 3.2f * dpr);
        tgPlane.close();
        pItemFill.setColor(Color.WHITE);
        c.drawPath(tgPlane, pItemFill);

        pTextBold.setColor(Color.WHITE);
        pTextBold.setTextSize(11f * dpr);
        pTextBold.setTextAlign(Paint.Align.LEFT);
        c.drawText("@mubava", tgBadge.left + 26f * dpr, tgBadge.top + 14f * dpr, pTextBold);

        pText.setColor(Color.parseColor("#38BDF8"));
        pText.setTextSize(9f * dpr);
        c.drawText("Telegram", tgBadge.left + 26f * dpr, tgBadge.top + 26f * dpr, pText);

        // 2. GitHub Badge
        RectF ghBadge = new RectF(aboutBox.left + 14f * dpr + badgeW, badgeY, aboutBox.right - 10f * dpr, badgeY + 34f * dpr);
        pBtn.setColor(Color.parseColor("#1D2B3A"));
        c.drawRoundRect(ghBadge, 8f * dpr, 8f * dpr, pBtn);

        float ghCx = ghBadge.left + 14f * dpr;
        float ghCy = ghBadge.centerY();
        pItemFill.setColor(Color.WHITE);

        Path ghOctocat = new Path();
        ghOctocat.addCircle(ghCx, ghCy, 7.5f * dpr, Path.Direction.CW);
        ghOctocat.moveTo(ghCx - 4.5f * dpr, ghCy - 5f * dpr);
        ghOctocat.lineTo(ghCx - 6f * dpr, ghCy - 8.5f * dpr);
        ghOctocat.lineTo(ghCx - 2.5f * dpr, ghCy - 7f * dpr);
        ghOctocat.close();
        ghOctocat.moveTo(ghCx + 4.5f * dpr, ghCy - 5f * dpr);
        ghOctocat.lineTo(ghCx + 6f * dpr, ghCy - 8.5f * dpr);
        ghOctocat.lineTo(ghCx + 2.5f * dpr, ghCy - 7f * dpr);
        ghOctocat.close();
        c.drawPath(ghOctocat, pItemFill);

        pItemFill.setColor(Color.parseColor("#1D2B3A"));
        Path ghFace = new Path();
        ghFace.addOval(new RectF(ghCx - 4.5f * dpr, ghCy - 3f * dpr, ghCx + 4.5f * dpr, ghCy + 4f * dpr), Path.Direction.CW);
        c.drawPath(ghFace, pItemFill);

        pItemFill.setColor(Color.WHITE);
        c.drawCircle(ghCx, ghCy + 3f * dpr, 1.8f * dpr, pItemFill);

        pTextBold.setColor(Color.WHITE);
        pTextBold.setTextSize(11f * dpr);
        pTextBold.setTextAlign(Paint.Align.LEFT);
        c.drawText("dragon8957", ghBadge.left + 26f * dpr, ghBadge.top + 14f * dpr, pTextBold);

        pText.setColor(Color.parseColor("#94A3B8"));
        pText.setTextSize(9f * dpr);
        c.drawText("GitHub", ghBadge.left + 26f * dpr, ghBadge.top + 26f * dpr, pText);

        // Save & Back Button
        RectF backBtn = new RectF(cx + 14f * dpr, cy + cardH - 42f * dpr, cx + cardW - 14f * dpr, cy + cardH - 8f * dpr);
        pBtn.setColor(Color.parseColor("#059669"));
        c.drawRoundRect(backBtn, 12f * dpr, 12f * dpr, pBtn);
        pTextBold.setColor(Color.WHITE);
        pTextBold.setTextSize(16f * dpr);
        pTextBold.setTextAlign(Paint.Align.CENTER);
        c.drawText(Localization.get("save_close"), screenW / 2f, backBtn.centerY() + 5f * dpr, pTextBold);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float tx = event.getX();
        float ty = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (gameState == State.MENU) {
                    checkMenuBtnDown(tx, ty);
                    return true;
                } else if (gameState == State.PAUSED) {
                    checkPauseBtnDown(tx, ty);
                    return true;
                } else if (gameState == State.SETTINGS) {
                    handleSettingsTouch(tx, ty);
                    return true;
                } else if (gameState == State.GAMEOVER) {
                    checkGameOverBtnDown(tx, ty);
                    return true;
                } else if (gameState == State.PLAYING) {
                    float topPad = 48f * dpr;
                    if (tx >= screenW - wallMargin - 55f * dpr && ty <= topPad + 50f * dpr) {
                        gameState = State.PAUSED;
                        sounds.playClick();
                        haptics.click();
                        return true;
                    }
                    isTouching = true;
                    targetTouchX = tx;
                    zoneTouchDir = (tx < screenW / 2f) ? -1 : 1;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (gameState == State.PLAYING && isTouching) {
                    targetTouchX = tx;
                    zoneTouchDir = (tx < screenW / 2f) ? -1 : 1;
                }
                break;

            case MotionEvent.ACTION_UP:
                if (gameState == State.MENU) {
                    handleMenuBtnUp(tx, ty);
                    pressedBtnId = -1;
                    return true;
                } else if (gameState == State.PAUSED) {
                    handlePauseBtnUp(tx, ty);
                    pressedBtnId = -1;
                    return true;
                } else if (gameState == State.GAMEOVER) {
                    handleGameOverBtnUp(tx, ty);
                    pressedBtnId = -1;
                    return true;
                }
                isTouching = false;
                pressedBtnId = -1;
                break;

            case MotionEvent.ACTION_CANCEL:
                isTouching = false;
                pressedBtnId = -1;
                break;
        }
        return true;
    }

    private void checkMenuBtnDown(float tx, float ty) {
        float topY = screenH * 0.22f;
        float btnW = screenW * 0.84f;
        float btnH = 56f * dpr;
        float startX = (screenW - btnW) / 2f;
        float startY = topY + 75f * dpr;
        float gap = 16f * dpr;

        for (int i = 0; i < 3; i++) {
            RectF r = new RectF(startX, startY + (btnH + gap) * i, startX + btnW, startY + (btnH + gap) * i + btnH);
            if (r.contains(tx, ty)) {
                pressedBtnId = i;
                haptics.click();
                sounds.playClick();
                return;
            }
        }
        pressedBtnId = -1;
    }

    private void handleMenuBtnUp(float tx, float ty) {
        float topY = screenH * 0.22f;
        float btnW = screenW * 0.84f;
        float btnH = 56f * dpr;
        float startX = (screenW - btnW) / 2f;
        float startY = topY + 75f * dpr;
        float gap = 16f * dpr;

        if (pressedBtnId == 0) {
            RectF r = new RectF(startX, startY, startX + btnW, startY + btnH);
            if (r.contains(tx, ty)) queueAction(10, 0.08f);
        } else if (pressedBtnId == 1) {
            RectF r = new RectF(startX, startY + (btnH + gap), startX + btnW, startY + (btnH + gap) + btnH);
            if (r.contains(tx, ty)) {
                speedAccelerationEnabled = !speedAccelerationEnabled;
                saveSettings();
                haptics.pulse();
                sounds.playClick();
            }
        } else if (pressedBtnId == 2) {
            RectF r = new RectF(startX, startY + (btnH + gap) * 2f, startX + btnW, startY + (btnH + gap) * 2f + btnH);
            if (r.contains(tx, ty)) queueAction(12, 0.08f);
        }
    }

    private void checkPauseBtnDown(float tx, float ty) {
        float topY = screenH * 0.24f;
        float btnW = screenW * 0.84f;
        float btnH = 54f * dpr;
        float startX = (screenW - btnW) / 2f;
        float startY = topY + 68f * dpr;
        float gap = 16f * dpr;

        for (int i = 0; i < 4; i++) {
            RectF r = new RectF(startX, startY + (btnH + gap) * i, startX + btnW, startY + (btnH + gap) * i + btnH);
            if (r.contains(tx, ty)) {
                pressedBtnId = i;
                haptics.click();
                sounds.playClick();
                return;
            }
        }
        pressedBtnId = -1;
    }

    private void handlePauseBtnUp(float tx, float ty) {
        float topY = screenH * 0.24f;
        float btnW = screenW * 0.84f;
        float btnH = 54f * dpr;
        float startX = (screenW - btnW) / 2f;
        float startY = topY + 68f * dpr;
        float gap = 16f * dpr;

        if (pressedBtnId == 0) {
            RectF r = new RectF(startX, startY, startX + btnW, startY + btnH);
            if (r.contains(tx, ty)) queueAction(20, 0.08f);
        } else if (pressedBtnId == 1) {
            RectF r = new RectF(startX, startY + (btnH + gap), startX + btnW, startY + (btnH + gap) + btnH);
            if (r.contains(tx, ty)) queueAction(21, 0.08f);
        } else if (pressedBtnId == 2) {
            RectF r = new RectF(startX, startY + (btnH + gap) * 2f, startX + btnW, startY + (btnH + gap) * 2f + btnH);
            if (r.contains(tx, ty)) queueAction(22, 0.08f);
        } else if (pressedBtnId == 3) {
            RectF r = new RectF(startX, startY + (btnH + gap) * 3f, startX + btnW, startY + (btnH + gap) * 3f + btnH);
            if (r.contains(tx, ty)) queueAction(23, 0.08f);
        }
    }

    private void checkGameOverBtnDown(float tx, float ty) {
        float topY = screenH * 0.22f;
        float btnW = screenW * 0.84f;
        float btnH = 54f * dpr;
        float startX = (screenW - btnW) / 2f;
        float startY = topY + 108f * dpr;
        float gap = 16f * dpr;

        for (int i = 0; i < 2; i++) {
            RectF r = new RectF(startX, startY + (btnH + gap) * i, startX + btnW, startY + (btnH + gap) * i + btnH);
            if (r.contains(tx, ty)) {
                pressedBtnId = i;
                haptics.click();
                sounds.playClick();
                return;
            }
        }
        pressedBtnId = -1;
    }

    private void handleGameOverBtnUp(float tx, float ty) {
        float topY = screenH * 0.22f;
        float btnW = screenW * 0.84f;
        float btnH = 54f * dpr;
        float startX = (screenW - btnW) / 2f;
        float startY = topY + 108f * dpr;
        float gap = 16f * dpr;

        if (pressedBtnId == 0) {
            RectF r = new RectF(startX, startY, startX + btnW, startY + btnH);
            if (r.contains(tx, ty)) queueAction(30, 0.08f);
        } else if (pressedBtnId == 1) {
            RectF r = new RectF(startX, startY + (btnH + gap), startX + btnW, startY + (btnH + gap) + btnH);
            if (r.contains(tx, ty)) queueAction(32, 0.08f);
        }
    }

    private void queueAction(int actionId, float delay) {
        pendingActionId = actionId;
        pendingActionTimer = delay;
    }

    private void executeAction(int actionId) {
        switch (actionId) {
            case 10:
                startNewGame();
                break;
            case 12:
                prevSettingsState = State.MENU;
                gameState = State.SETTINGS;
                break;
            case 20:
                gameState = State.PLAYING;
                break;
            case 21:
                prevSettingsState = State.PAUSED;
                gameState = State.SETTINGS;
                break;
            case 22:
                startNewGame();
                break;
            case 23:
                gameState = State.MENU;
                break;
            case 30:
                startNewGame();
                break;
            case 32:
                gameState = State.MENU;
                break;
        }
    }

    private void handleSettingsTouch(float tx, float ty) {
        float cardW = screenW * 0.92f;
        float cardH = screenH * 0.92f;
        float cx = (screenW - cardW) / 2f;
        float cy = (screenH - cardH) / 2f;

        float sy = cy + 46f * dpr;
        float rowH = 38f * dpr;
        float halfW = (cardW - 32f * dpr) / 2f;

        // 1. Language
        RectF langRu = new RectF(cx + 14f * dpr, sy + 18f * dpr, cx + 14f * dpr + halfW, sy + 18f * dpr + 30f * dpr);
        RectF langEn = new RectF(cx + 18f * dpr + halfW, sy + 18f * dpr, cx + cardW - 14f * dpr, sy + 18f * dpr + 30f * dpr);
        if (langRu.contains(tx, ty)) {
            Localization.lang = 0; sounds.playClick(); haptics.click(); return;
        }
        if (langEn.contains(tx, ty)) {
            Localization.lang = 1; sounds.playClick(); haptics.click(); return;
        }

        // 2. Sound
        sy += rowH + 14f * dpr;
        RectF sndOn = new RectF(cx + 14f * dpr, sy + 18f * dpr, cx + 14f * dpr + halfW, sy + 18f * dpr + 30f * dpr);
        RectF sndOff = new RectF(cx + 18f * dpr + halfW, sy + 18f * dpr, cx + cardW - 14f * dpr, sy + 18f * dpr + 30f * dpr);
        if (sndOn.contains(tx, ty)) {
            sounds.enabled = true; sounds.playClick(); haptics.click(); return;
        }
        if (sndOff.contains(tx, ty)) {
            sounds.enabled = false; haptics.click(); return;
        }

        // 3. Vibration
        sy += rowH + 14f * dpr;
        RectF vibOn = new RectF(cx + 14f * dpr, sy + 18f * dpr, cx + 14f * dpr + halfW, sy + 18f * dpr + 30f * dpr);
        RectF vibOff = new RectF(cx + 18f * dpr + halfW, sy + 18f * dpr, cx + cardW - 14f * dpr, sy + 18f * dpr + 30f * dpr);
        if (vibOn.contains(tx, ty)) {
            haptics.enabled = true; haptics.pulse(); sounds.playClick(); return;
        }
        if (vibOff.contains(tx, ty)) {
            haptics.enabled = false; sounds.playClick(); return;
        }

        // 4. Control Mode
        sy += rowH + 14f * dpr;
        RectF ctrl1 = new RectF(cx + 14f * dpr, sy + 18f * dpr, cx + 14f * dpr + halfW, sy + 18f * dpr + 30f * dpr);
        RectF ctrl2 = new RectF(cx + 18f * dpr + halfW, sy + 18f * dpr, cx + cardW - 14f * dpr, sy + 18f * dpr + 30f * dpr);
        if (ctrl1.contains(tx, ty)) {
            controlMode = 0; sounds.playClick(); haptics.click(); return;
        }
        if (ctrl2.contains(tx, ty)) {
            controlMode = 1; sounds.playClick(); haptics.click(); return;
        }

        // 5. FX Toggles
        sy += rowH + 14f * dpr;
        RectF fxShake = new RectF(cx + 14f * dpr, sy + 18f * dpr, cx + 14f * dpr + halfW, sy + 18f * dpr + 30f * dpr);
        RectF fxPart = new RectF(cx + 18f * dpr + halfW, sy + 18f * dpr, cx + cardW - 14f * dpr, sy + 18f * dpr + 30f * dpr);
        if (fxShake.contains(tx, ty)) {
            screenShakeEnabled = !screenShakeEnabled;
            sounds.playClick(); haptics.click();
            return;
        }
        if (fxPart.contains(tx, ty)) {
            particlesEnabled = !particlesEnabled;
            initDust();
            sounds.playClick(); haptics.click();
            return;
        }

        // 6. Reset Score
        sy += rowH + 12f * dpr;
        RectF rstScoreBtn = new RectF(cx + 14f * dpr, sy + 14f * dpr, cx + cardW - 14f * dpr, sy + 44f * dpr);
        if (rstScoreBtn.contains(tx, ty)) {
            bestDepth = 0;
            prefs.edit().putInt("best_depth", 0).apply();
            sounds.playShieldBreak();
            haptics.heavy();
            return;
        }

        // Back / Save
        RectF backBtn = new RectF(cx + 14f * dpr, cy + cardH - 46f * dpr, cx + cardW - 14f * dpr, cy + cardH - 10f * dpr);
        if (backBtn.contains(tx, ty)) {
            saveSettings();
            sounds.playClick();
            haptics.click();
            gameState = prevSettingsState;
            return;
        }
    }

    public boolean onBackPressed() {
        if (gameState == State.PLAYING) {
            gameState = State.PAUSED;
            return true;
        } else if (gameState == State.SETTINGS) {
            saveSettings();
            gameState = prevSettingsState;
            return true;
        } else if (gameState == State.PAUSED || gameState == State.GAMEOVER) {
            gameState = State.MENU;
            return true;
        }
        return false;
    }
}
