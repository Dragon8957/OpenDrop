package com.openprop.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;

public class ThemeManager {
    public static class Theme {
        public final int id;
        public final String nameRu;
        public final String nameEn;
        public final int bgColor;
        public final int wallColor;
        public final int obsColor;
        public final int stripeColor;
        public final int spikeColor;
        public final int gridColor;
        public final int textColor;

        public Theme(int id, String nameRu, String nameEn, String bg, String wall, String obs, String stripe, String spike, String grid, String text) {
            this.id = id;
            this.nameRu = nameRu;
            this.nameEn = nameEn;
            this.bgColor = Color.parseColor(bg);
            this.wallColor = Color.parseColor(wall);
            this.obsColor = Color.parseColor(obs);
            this.stripeColor = Color.parseColor(stripe);
            this.spikeColor = Color.parseColor(spike);
            this.gridColor = Color.parseColor(grid);
            this.textColor = Color.parseColor(text);
        }

        public String getName() {
            return (Localization.lang == 1) ? nameEn : nameRu;
        }
    }

    public static class Skin {
        public final int id;
        public final String nameRu;
        public final String nameEn;

        public Skin(int id, String nameRu, String nameEn) {
            this.id = id;
            this.nameRu = nameRu;
            this.nameEn = nameEn;
        }

        public String getName() {
            return (Localization.lang == 1) ? nameEn : nameRu;
        }
    }

    private final SharedPreferences prefs;
    public final List<Theme> themes = new ArrayList<>();
    public final List<Skin> skins = new ArrayList<>();

    public int activeThemeId = 0;
    public int activeSkinId = 0;

    public ThemeManager(Context context) {
        prefs = context.getSharedPreferences("openprop_shop_v1", Context.MODE_PRIVATE);
        activeThemeId = prefs.getInt("active_theme", 0);
        activeSkinId = prefs.getInt("active_skin", 0);

        initThemes();
        initSkins();
    }

    private void initThemes() {
        themes.add(new Theme(0, "Индустриальный", "Industrial Core", "#16181D", "#0E0F12", "#232730", "#E5C07B", "#E06C75", "#323846", "#ABB2BF"));
        themes.add(new Theme(1, "Глубокий Космос", "Deep Cosmos", "#0A0D14", "#05070B", "#182030", "#61AFEF", "#C678DD", "#26354E", "#D1D5DB"));
        themes.add(new Theme(2, "Ватман и Чернила", "Blueprint & Ink", "#F4F0E8", "#DCD5C6", "#23272A", "#D9534F", "#C84630", "#C8BFB0", "#23272A"));
        themes.add(new Theme(3, "Магма и Лава", "Magma Chamber", "#1C0F0F", "#100808", "#331818", "#FF7B00", "#FF2A2A", "#592020", "#FFAA66"));
        themes.add(new Theme(4, "Изумрудный Грот", "Emerald Cavern", "#0B1711", "#050C08", "#142E21", "#98C379", "#E5C07B", "#1F4C37", "#A3E635"));
        themes.add(new Theme(5, "Кибер-Матрица", "Cyber Matrix", "#0E131B", "#070A0F", "#162030", "#00F0FF", "#FF0055", "#243954", "#E0F2FE"));
    }

    private void initSkins() {
        skins.add(new Skin(0, "Зонд Альфа", "Alpha Probe"));
        skins.add(new Skin(1, "Энерго-Сфера", "Energy Orb"));
        skins.add(new Skin(2, "Истребитель Стрела", "Arrow Jet"));
        skins.add(new Skin(3, "Ограненный Алмаз", "Faceted Diamond"));
        skins.add(new Skin(4, "Ядро Кометы", "Comet Core"));
    }

    public void selectTheme(int id) {
        activeThemeId = id;
        prefs.edit().putInt("active_theme", id).apply();
    }

    public void selectSkin(int id) {
        activeSkinId = id;
        prefs.edit().putInt("active_skin", id).apply();
    }

    public Theme getActiveTheme() {
        return getTheme(activeThemeId);
    }

    public Theme getTheme(int id) {
        for (Theme t : themes) if (t.id == id) return t;
        return themes.get(0);
    }

    public Skin getActiveSkin() {
        return getSkin(activeSkinId);
    }

    public Skin getSkin(int id) {
        for (Skin s : skins) if (s.id == id) return s;
        return skins.get(0);
    }
}
