package com.openprop.game;

public class Localization {
    public static int lang = 1; // Default: 1 = English (EN), 0 = Russian (RU)

    public static String get(String key) {
        if (lang == 0) { // RU
            switch (key) {
                case "app_title": return "openprop";
                case "play": return "Играть";
                case "settings": return "Настройки";
                case "pause": return "Пауза";
                case "resume": return "Продолжить";
                case "restart": return "Начать заново";
                case "to_menu": return "В главное меню";
                case "game_over": return "Столкновение";
                case "best_record": return "Лучший рекорд";
                case "current_depth": return "Глубина";
                case "sound": return "Звук и музыка";
                case "vibration": return "Вибрация";
                case "language": return "Язык";
                case "on": return "Вкл";
                case "off": return "Выкл";
                case "controls": return "Управление";
                case "drag": return "Следование";
                case "zones": return "Края экрана";
                case "shake": return "Тряска";
                case "particles": return "Частицы";
                case "reset_score": return "Сбросить рекорд";
                case "save_close": return "Сохранить";
                case "shield": return "Щит";
                case "slowmo": return "Слоумо";
                case "magnet": return "Магнит";
                case "shield_broken": return "Щит сбит!";
                case "acceleration": return "Ускорение";
                case "developer": return "Разработчик:";
                case "about_desc_1": return "Бесконечный кибер-спуск:";
                case "about_desc_2": return "Уворачивайся от стен и ставь рекорды!";
                default: return key;
            }
        } else { // EN
            switch (key) {
                case "app_title": return "openprop";
                case "play": return "Play";
                case "settings": return "Settings";
                case "pause": return "Pause";
                case "resume": return "Resume";
                case "restart": return "Restart";
                case "to_menu": return "Main Menu";
                case "game_over": return "Game Over";
                case "best_record": return "Best Record";
                case "current_depth": return "Depth";
                case "sound": return "Sound & Music";
                case "vibration": return "Vibration";
                case "language": return "Language";
                case "on": return "On";
                case "off": return "Off";
                case "controls": return "Controls";
                case "drag": return "Follow";
                case "zones": return "Screen Sides";
                case "shake": return "Shake FX";
                case "particles": return "Particles";
                case "reset_score": return "Reset Best Score";
                case "save_close": return "Save & Exit";
                case "shield": return "Shield";
                case "slowmo": return "Slow-Mo";
                case "magnet": return "Magnet";
                case "shield_broken": return "Shield Broken!";
                case "acceleration": return "Acceleration";
                case "developer": return "Developer:";
                case "about_desc_1": return "Infinite cyber descent:";
                case "about_desc_2": return "Dodge hazards and reach deep depths!";
                default: return key;
            }
        }
    }
}
