package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Multi-language localization support for tvOS Launcher:
 * English (en), Turkish (tr), Swedish (sv), Hindi/Indian (hi), Spanish (es), French (fr),
 * German (de), Italian (it), Russian (ru), Portuguese (pt), Arabic (ar),
 * Japanese (ja), and Chinese (zh).
 */
public final class I18n {
    public static final String LANG_EN = "en";
    public static final String LANG_TR = "tr";
    public static final String LANG_SV = "sv";
    public static final String LANG_HI = "hi";
    public static final String LANG_ES = "es";
    public static final String LANG_FR = "fr";
    public static final String LANG_DE = "de";
    public static final String LANG_IT = "it";
    public static final String LANG_RU = "ru";
    public static final String LANG_PT = "pt";
    public static final String LANG_AR = "ar";
    public static final String LANG_JA = "ja";
    public static final String LANG_ZH = "zh";

    public static final String[][] LANGUAGES = {
        {LANG_EN, "English", "Use tvOS Launcher in English"},
        {LANG_TR, "Türkçe", "tvOS Launcher'ı Türkçe kullanın"},
        {LANG_SV, "Svenska", "Använd tvOS Launcher på svenska"},
        {LANG_HI, "हिन्दी (Indian)", "tvOS Launcher का उपयोग हिन्दी में करें"},
        {LANG_ES, "Español", "Usar tvOS Launcher en español"},
        {LANG_FR, "Français", "Utiliser tvOS Launcher en français"},
        {LANG_DE, "Deutsch", "tvOS Launcher auf Deutsch verwenden"},
        {LANG_IT, "Italiano", "Usa tvOS Launcher in italiano"},
        {LANG_RU, "Русский", "Использовать tvOS Launcher на русском"},
        {LANG_PT, "Português", "Usar o tvOS Launcher em português"},
        {LANG_AR, "العربية", "استخدم tvOS Launcher باللغة العربية"},
        {LANG_JA, "日本語", "tvOS Launcher を日本語で使用する"},
        {LANG_ZH, "中文", "使用中文版 tvOS Launcher"}
    };

    private static final Set<String> SUPPORTED = new HashSet<>(Arrays.asList(
            LANG_EN, LANG_TR, LANG_SV, LANG_HI, LANG_ES, LANG_FR, LANG_DE,
            LANG_IT, LANG_RU, LANG_PT, LANG_AR, LANG_JA, LANG_ZH
    ));

    private static String cachedLang;
    private static final Map<String, String> TR_MAP = new HashMap<>();
    private static final Map<String, Map<String, String>> LANG_MAPS = new HashMap<>();

    static {
        // Turkish dictionary for I18n.t() keys
        TR_MAP.put("Look and scenes", "Görünüm ve sahneler");
        TR_MAP.put("Appearance and scenes", "Görünüm ve sahneler");
        TR_MAP.put("Weather and location", "Hava durumu ve konum");
        TR_MAP.put("Language", "Dil");
        TR_MAP.put("LOOK", "GÖRÜNÜM");
        TR_MAP.put("Night scenes", "Gece sahneleri");
        TR_MAP.put("A big glass clock, scenes taking turns", "Büyük cam saat, sırayla değişen sahneler");
        TR_MAP.put("Classic", "Klasik");
        TR_MAP.put("One photo and a grid of app banners", "Tek fotoğraf ve uygulama afişleri ızgarası");
        TR_MAP.put("SCENES", "SAHNELER");
        TR_MAP.put("Night", "Gece");
        TR_MAP.put("Golden hour", "Altın saat");
        TR_MAP.put("Waterline", "Su çizgisi");
        TR_MAP.put("Fog", "Sis");
        TR_MAP.put("Rainforest dawn", "Yağmur ormanı şafağı");
        TR_MAP.put("Custom", "Özel");
        TR_MAP.put("Clock position", "Saat konumu");
        TR_MAP.put("Left", "Sol");
        TR_MAP.put("Center", "Orta");
        TR_MAP.put("Right", "Sağ");
        TR_MAP.put("Change app icon", "Uygulama simgesini değiştir");
        TR_MAP.put("Reset to default icon", "Varsayılan simgeye sıfırla");
        TR_MAP.put("Updates", "Güncellemeler");
        TR_MAP.put("UPDATES", "GÜNCELLEMELER");
        TR_MAP.put("Automatic updates", "Otomatik güncellemeler");
        TR_MAP.put("Check for updates", "Güncellemeleri kontrol et");
        TR_MAP.put("Current version", "Mevcut sürüm");
        TR_MAP.put("GitHub release latest version", "GitHub son sürüm");
        TR_MAP.put("Each scene stays", "Her sahne süresi");
        TR_MAP.put("Clock", "Saat");
        TR_MAP.put("12 hour", "12 saat");
        TR_MAP.put("24 hour", "24 saat");
        TR_MAP.put("One scene has to stay on.", "En az bir sahne açık kalmalıdır.");
        TR_MAP.put("Keep watching", "İzlemeye devam et");
        TR_MAP.put("Continue Watching", "İzlemeye Devam Et");
        TR_MAP.put("More apps", "Diğer uygulamalar");
        TR_MAP.put("Fewer apps", "Daha az uygulama");
        TR_MAP.put("Settings", "Ayarlar");
        TR_MAP.put("Home settings", "Ana ekran ayarları");
        TR_MAP.put("Language, weather, scenes, apps and people", "Dil, hava durumu, sahneler, uygulamalar ve kişiler");
        TR_MAP.put("TV settings", "TV ayarları");
        TR_MAP.put("TV Settings", "TV Ayarları");
        TR_MAP.put("Wi-Fi, picture, sound and the rest of the TV", "Wi-Fi, görüntü, ses ve diğer TV ayarları");
        TR_MAP.put("Sleep timer", "Uyku zamanlayıcısı");
        TR_MAP.put("Sleep Timer", "Uyku Zamanlayıcısı");
        TR_MAP.put("Turn the TV off after", "TV'yi şu süreden sonra kapat:");
        TR_MAP.put("Off", "Kapalı");
        TR_MAP.put("Search", "Ara");
        TR_MAP.put("Refresh", "Yenile");
        TR_MAP.put("Clear", "Açık");
        TR_MAP.put("Sunny", "Güneşli");
        TR_MAP.put("Mostly Sunny", "Çoğunlukla Güneşli");
        TR_MAP.put("Mostly Clear", "Çoğunlukla Açık");
        TR_MAP.put("Partly Cloudy", "Parçalı Bulutlu");
        TR_MAP.put("Cloudy", "Bulutlu");
        TR_MAP.put("Foggy", "Sisli");
        TR_MAP.put("Drizzle", "Çiseleme");
        TR_MAP.put("Rain", "Yağmurlu");
        TR_MAP.put("Heavy Rain", "Şiddetli Yağmur");
        TR_MAP.put("Sleet", "Karla Karışık Yağmur");
        TR_MAP.put("Snow", "Karlı");
        TR_MAP.put("Heavy Snow", "Yoğun Karlı");
        TR_MAP.put("Showers", "Sağanak");
        TR_MAP.put("Thunderstorms", "Fırtına");

        String[] langs = {LANG_SV, LANG_HI, LANG_ES, LANG_FR, LANG_DE, LANG_IT, LANG_RU, LANG_PT, LANG_AR, LANG_JA, LANG_ZH};
        for (String l : langs) {
            LANG_MAPS.put(l, new HashMap<String, String>());
        }

        initTranslations();
        initSwedish();
    }

    private static void add(String en, String hi, String es, String fr, String de, String it, String ru, String pt, String ar, String ja, String zh) {
        LANG_MAPS.get(LANG_HI).put(en, hi);
        LANG_MAPS.get(LANG_ES).put(en, es);
        LANG_MAPS.get(LANG_FR).put(en, fr);
        LANG_MAPS.get(LANG_DE).put(en, de);
        LANG_MAPS.get(LANG_IT).put(en, it);
        LANG_MAPS.get(LANG_RU).put(en, ru);
        LANG_MAPS.get(LANG_PT).put(en, pt);
        LANG_MAPS.get(LANG_AR).put(en, ar);
        LANG_MAPS.get(LANG_JA).put(en, ja);
        LANG_MAPS.get(LANG_ZH).put(en, zh);
    }

    private static void initTranslations() {
        // Greetings
        add("Good morning", "सुप्रभात", "Buenos días", "Bonjour", "Guten Morgen", "Buongiorno", "Доброе утро", "Bom dia", "صباح الخير", "おはようございます", "早上好");
        add("Good afternoon", "नमस्कार", "Buenas tardes", "Bon après-midi", "Guten Tag", "Buon pomeriggio", "Добрый день", "Boa tarde", "مساء الخير", "こんにちは", "下午好");
        add("Good evening", "शुभ संध्या", "Buenas noches", "Bonsoir", "Guten Abend", "Buonasera", "Добрый вечер", "Boa noite", "مساء الخير", "こんばんは", "晚上好");
        add("Good night", "शुभ रात्रि", "Buenas noches", "Bonne nuit", "Gute Nacht", "Buonanotte", "Спокойной ночи", "Boa noite", "تصبح على خير", "おやすみなさい", "晚安");

        // Weather conditions
        add("Clear", "साफ़", "Despejado", "Dégagé", "Klar", "Sereno", "Ясно", "Limpo", "صافي", "快晴", "晴朗");
        add("Sunny", "धूप", "Soleado", "Ensoleillé", "Sonnig", "Soleggiato", "Солнечно", "Ensolarado", "مشمس", "晴れ", "晴");
        add("Mostly Sunny", "अधिकतर धूप", "Mayormente soleado", "Plutôt ensoleillé", "Meist sonnig", "Prevalentemente soleggiato", "Малооблачно", "Predominantemente ensolarado", "مشمس غالباً", "おおむね晴れ", "大部晴朗");
        add("Mostly Clear", "अधिकतर साफ़", "Mayormente despejado", "Plutôt dégagé", "Meist klar", "Prevalentemente sereno", "Почти ясно", "Predominantemente limpo", "صافي غالباً", "おおむね快晴", "大部晴朗");
        add("Partly Cloudy", "आंशिक बादल", "Parcialmente nublado", "Partiellement nuageux", "Teilweise bewölkt", "Parzialmente nuvoloso", "Переменная облачность", "Parcialmente nublado", "غائم جزئياً", "晴れ時々曇り", "多云");
        add("Cloudy", "बादल छाए रहेंगे", "Nublado", "Nuageux", "Bewölkt", "Nuvoloso", "Облачно", "Nublado", "غائم", "曇り", "阴");
        add("Foggy", "कोहरा", "Niebla", "Brouillard", "Neblig", "Nebbia", "Туман", "Nevoeiro", "ضبابي", "霧", "雾");
        add("Drizzle", "बूंदाबांदी", "Llovizna", "Bruine", "Nieselregen", "Pioggerella", "Морось", "Chuvisco", "رذاذ", "霧雨", "毛毛雨");
        add("Rain", "बारिश", "Lluvia", "Pluie", "Regen", "Pioggia", "Дождь", "Chuva", "مطر", "雨", "雨");
        add("Heavy Rain", "तेज़ बारिश", "Lluvia intensa", "Forte pluie", "Starkregen", "Pioggia forte", "Сильный дождь", "Chuva forte", "مطر غزير", "大雨", "大雨");
        add("Sleet", "ओले और बारिश", "Aguanieve", "Pluie verglaçante", "Schneeregen", "Nevischio", "Мокрый снег", "Granizo", "مطر متجمد", "みぞれ", "雨夹雪");
        add("Snow", "बर्फ़बारी", "Nieve", "Neige", "Schnee", "Neve", "Снег", "Neve", "ثلج", "雪", "雪");
        add("Heavy Snow", "भारी बर्फ़बारी", "Nieve intensa", "Forte neige", "Starker Schneefall", "Neve forte", "Сильный снег", "Neve forte", "ثلوج كثيفة", "大雪", "大雪");
        add("Showers", "बौछारें", "Chubascos", "Averses", "Regenschauer", "Rovesci", "Ливень", "Pancadas de chuva", "زخات مطر", "にわか雨", "阵雨");
        add("Thunderstorms", "आंधी-तूफ़ान", "Tormentas", "Orages", "Gewitter", "Temporali", "Гроза", "Trovoadas", "عواصف رعدية", "雷雨", "雷暴");

        // Sidebar & Main Sections
        add("Settings", "सेटिंग्स", "Ajustes", "Réglages", "Einstellungen", "Impostazioni", "Настройки", "Definições", "الإعدادات", "設定", "设置");
        add("Language", "भाषा", "Idioma", "Langue", "Sprache", "Lingua", "Язык", "Idioma", "اللغة", "言語", "语言");
        add("Location", "स्थान", "Ubicación", "Position", "Standort", "Posizione", "Местоположение", "Localização", "الموقع", "場所", "位置");
        add("Weather", "मौसम", "Clima", "Météo", "Wetter", "Meteo", "Погода", "Clima", "الطقس", "天気", "天气");
        add("Weather and location", "मौसम और स्थान", "Clima y ubicación", "Météo et position", "Wetter & Standort", "Meteo e posizione", "Погода и местоположение", "Clima e localização", "الطقس والموقع", "天気と場所", "天气与位置");
        add("Look and scenes", "रूप और दृश्य", "Apariencia y escenas", "Apparence et scènes", "Aussehen & Szenen", "Aspetto e scene", "Вид и сцены", "Aparência e cenas", "المظهر والمشاهد", "外観とシーン", "外观与场景");
        add("Appearance and scenes", "रूप और दृश्य", "Apariencia y escenas", "Apparence et scènes", "Aussehen & Szenen", "Aspetto e scene", "Вид и сцены", "Aparência e cenas", "المظهر والمشاهد", "外観とシーン", "外观与场景");
        add("Apps", "ऐप्स", "Aplicaciones", "Applications", "Apps", "App", "Приложения", "Aplicações", "التطبيقات", "アプリ", "应用");
        add("People", "लोग", "Personas", "Profils", "Personen", "Persone", "Профили", "Pessoas", "الأشخاص", "プロフィール", "用户");
        add("Button Mapper", "बटन मैपर", "Asignador de botones", "Boutons télécommande", "Tastenbelegung", "Mappatura tasti", "Кнопки пульта", "Mapeador de botões", "تعيين الأزرار", "ボタン割り当て", "按键映射");
        add("Button mapper", "बटन मैपर", "Asignador de botones", "Boutons télécommande", "Tastenbelegung", "Mappatura tasti", "Кнопки пульта", "Mapeador de botões", "تعيين الأزرار", "ボタン割り当て", "按键映射");
        add("Quick buttons", "त्वरित बटन", "Botones rápidos", "Boutons rapides", "Schnelltasten", "Pulsanti rapidi", "Быстрые кнопки", "Botões rápidos", "الأزرار السريعة", "クイックボタン", "快捷按钮");
        add("Home & System", "होम और सिस्टम", "Inicio y sistema", "Accueil et système", "Startseite & System", "Home e sistema", "Главный экран и система", "Início e sistema", "الشاشة الرئيسية والنظام", "ホームとシステム", "主页与系统");
        add("Advanced", "उन्नत", "Avanzado", "Avancé", "Erweitert", "Avanzate", "Дополнительно", "Avançado", "متقدم", "詳細設定", "高级");
        add("About", "जानकारी", "Acerca de", "À propos", "Info", "Informazioni", "О приложении", "Sobre", "حول", "情報", "关于");

        // Home screen & Scenes
        add("LOOK", "रूप", "APARIENCIA", "APPARENCE", "AUSSEHEN", "ASPETTO", "ВИД", "APARÊNCIA", "المظهر", "外観", "外观");
        add("Night scenes", "रात्रि दृश्य", "Escenas nocturnas", "Scènes nocturnes", "Nachtszenen", "Scene notturne", "Ночные сцены", "Cenas noturnas", "مشاهد ليلية", "ナイトシーン", "夜间场景");
        add("A big glass clock, scenes taking turns", "बड़ी कांच की घड़ी, बदलते हुए दृश्य", "Un gran reloj de cristal y escenas que rotan", "Une grande horloge en verre et des scènes qui défilent", "Große Glasuhr, wechselnde Szenen", "Un grande orologio di vetro e scene a rotazione", "Большие стеклянные часы и сменяющиеся сцены", "Um grande relógio de vidro e cenas alternadas", "ساعة زجاجية كبيرة ومشاهد متناوبة", "大きなガラス時計と切り替わるシーン", "大号玻璃时钟与轮换场景");
        add("Classic", "क्लासिक", "Clásico", "Classique", "Klassisch", "Classico", "Классический", "Clássico", "كلاسيكي", "クラシック", "经典");
        add("One photo and a grid of app banners", "एक फ़ोटो और ऐप बैनर का ग्रिड", "Una foto y una cuadrícula de aplicaciones", "Une photo et une grille d'applications", "Ein Foto und ein Raster aus App-Bannern", "Una foto e una griglia di app", "Одно фото и сетка баннеров приложений", "Uma foto e uma grelha de aplicações", "صورة واحدة وشبكة من التطبيقات", "1枚の写真とアプリのグリッド", "单张壁纸与应用网格");
        add("SCENES", "दृश्य", "ESCENAS", "SCÈNES", "SZENEN", "SCENE", "СЦЕНЫ", "CENAS", "المشاهد", "シーン", "场景");
        add("Night", "रात", "Noche", "Nuit", "Nacht", "Notte", "Ночь", "Noite", "ليل", "夜", "夜晚");
        add("Golden hour", "सुनहरा समय", "Hora dorada", "Heure dorée", "Goldene Stunde", "Ora d'oro", "Золотой час", "Hora dourada", "الساعة الذهبية", "ゴールデンアワー", "黄金时刻");
        add("Waterline", "जलरेखा", "Línea de agua", "Ligne d'eau", "Wasserlinie", "Linea d'acqua", "Водная гладь", "Linha de água", "خط الماء", "水際", "水岸线");
        add("Fog", "कोहरा", "Niebla", "Brouillard", "Nebel", "Nebbia", "Туман", "Nevoeiro", "ضباب", "霧", "晨雾");
        add("Rainforest dawn", "वर्षावन की सुबह", "Amanecer en la selva", "Aube tropicale", "Regenwald-Morgendämmerung", "Alba nella foresta", "Рассвет в тропиках", "Amanhecer na floresta", "فجر الغابة المطيرة", "熱帯雨林の夜明け", "雨林破晓");
        add("Each scene stays", "प्रत्येक दृश्य का समय", "Duración de cada escena", "Durée de chaque scène", "Dauer pro Szene", "Durata di ogni scena", "Длительность сцены", "Duração de cada cena", "مدة بقاء كل مشهد", "シーンの表示時間", "每个场景停留时间");
        add("Clock", "घड़ी", "Reloj", "Horloge", "Uhr", "Orologio", "Часы", "Relógio", "الساعة", "時計", "时钟");
        add("Clock style", "घड़ी की शैली", "Estilo de reloj", "Style d'horloge", "Uhrenstil", "Stile orologio", "Стиль часов", "Estilo do relógio", "نمط الساعة", "時計のスタイル", "时钟样式");
        add("iPhone Duo", "iPhone Duo", "iPhone Duo", "iPhone Duo", "iPhone Duo", "iPhone Duo", "iPhone Duo", "iPhone Duo", "iPhone Duo", "iPhone Duo", "iPhone Duo");
        add("Glass", "ग्लास", "Cristal", "Verre", "Glas", "Vetro", "Стекло", "Vidro", "زجاجي", "ガラス", "玻璃");
        add("12 hour", "12 घंटे", "12 horas", "12 heures", "12 Stunden", "12 ore", "12 часов", "12 horas", "12 ساعة", "12時間表示", "12 小时制");
        add("24 hour", "24 घंटे", "24 horas", "24 heures", "24 Stunden", "24 ore", "24 часа", "24 horas", "24 ساعة", "24時間表示", "24 小时制");
        add("One scene has to stay on.", "कम से कम एक दृश्य चालू रहना चाहिए।", "Al menos una escena debe permanecer activa.", "Au moins une scène doit rester activée.", "Mindestens eine Szene muss aktiviert bleiben.", "Almeno una scena deve rimanere attiva.", "Хотя бы одна сцена должна оставаться включенной.", "Pelo menos uma cena deve permanecer ativa.", "يجب أن يبقى مشهد واحد على الأقل قيد التشغيل.", "少なくとも1つのシーンを有効にする必要があります。", "至少需要保留一个开启的场景。");
        add("Keep watching", "देखना जारी रखें", "Seguir viendo", "Continuer à regarder", "Weiterschauen", "Continua a guardare", "Продолжить просмотр", "Continuar a ver", "متابعة المشاهدة", "視聴を続ける", "继续观看");
        add("Continue Watching", "देखना जारी रखें", "Seguir viendo", "Continuer à regarder", "Weiterschauen", "Continua a guardare", "Продолжить просмотр", "Continuar a ver", "متابعة المشاهدة", "視聴を続ける", "继续观看");
        add("More apps", "अधिक ऐप्स", "Más aplicaciones", "Plus d'applis", "Weitere Apps", "Altre app", "Все приложения", "Mais aplicações", "المزيد من التطبيقات", "すべてのアプリ", "更多应用");
        add("More Apps", "अधिक ऐप्स", "Más aplicaciones", "Plus d'applis", "Weitere Apps", "Altre app", "Все приложения", "Mais aplicações", "المزيد من التطبيقات", "すべてのアプリ", "更多应用");
        add("Inputs", "इनपुट", "Entradas", "Entrées", "Eingänge", "Ingressi", "Входы", "Entradas", "المداخل", "入力切替", "输入源");
        add("Presentation", "प्रेजेंटेशन", "Presentación", "Présentation", "Präsentation", "Presentazione", "Презентация", "Apresentação", "عرض تقديمي", "プレゼンテーション", "演示");
        add("Choose how you want your Home Screen to look.", "चुनें कि आप अपनी होम स्क्रीन को कैसा दिखाना चाहते हैं।", "Elige cómo quieres que se vea tu pantalla de inicio.", "Choisissez l'apparence de votre écran d'accueil.", "Wählen Sie aus, wie Ihr Startbildschirm aussehen soll.", "Scegli come vuoi che appaia la schermata Home.", "Выберите внешний вид главного экрана.", "Escolha como pretende que o ecrã inicial apareça.", "اختر كيف تريد أن تبدو الشاشة الرئيسية.", "ホーム画面の外観を選択してください。", "选择您希望主屏幕呈现的外观样式。");
        add("Choose Your Scenes", "अपने दृश्य चुनें", "Elige tus escenas", "Choisissez vos scènes", "Szenen auswählen", "Scegli le tue scene", "Выберите сцены", "Escolha as suas cenas", "اختر المشاهد", "シーンを選択", "选择场景");
        add("Choose which scenes take turns behind the glass clock.", "चुनें कि कांच की घड़ी के पीछे कौन से दृश्य बारी-बारी से बदलेंगे।", "Elige qué escenas se turnan detrás del reloj de cristal.", "Choisissez les scènes qui défilent derrière l'horloge en verre.", "Wählen Sie aus, welche Szenen sich hinter der Glasuhr abwechseln.", "Scegli quali scene si alternano dietro l'orologio di vetro.", "Выберите сцены, которые будут сменять друг друга за часами.", "Escolha as cenas que se alternam atrás do relógio de vidro.", "اختر المشاهد التي تتناوب خلف الساعة الزجاجية.", "ガラス時計の背景で切り替わるシーンを選択します。", "选择在玻璃时钟后轮换显示的场景。");
        add("Choose a Background", "एक पृष्ठभूमि चुनें", "Elige un fondo", "Choisissez un fond d'écran", "Hintergrund auswählen", "Scegli uno sfondo", "Выберите фон", "Escolha um fundo", "اختر خلفية", "背景を選択", "选择背景");
        add("Choose a photo or ambient colour theme behind your apps.", "अपने ऐप्स के पीछे एक फ़ोटो या परिवेश रंग थीम चुनें।", "Elige una foto o un tema de color ambiental detrás de tus aplicaciones.", "Choisissez une photo ou un thème coloré derrière vos applications.", "Wählen Sie ein Foto oder ein Farbthema hinter Ihren Apps.", "Scegli una foto o un tema colore dietro le tue app.", "Выберите фото или цветовую тему позади приложений.", "Escolha uma foto ou um tema de cor atrás das suas aplicações.", "اختر صورة أو سمة لونية خلف تطبيقاتك.", "アプリの背景に表示する写真またはカラーテーマを選択します。", "选择应用后方显示的照片或氛围色彩主题。");
        add("Fewer apps", "कम ऐप्स", "Menos aplicaciones", "Moins d'applis", "Weniger Apps", "Meno app", "Свернуть", "Menos aplicações", "تطبيقات أقل", "折りたたむ", "收起应用");
        add("Home settings", "होम सेटिंग्स", "Ajustes de inicio", "Réglages de l'accueil", "Startseiten-Einstellungen", "Impostazioni Home", "Настройки главного экрана", "Definições do ecrã inicial", "إعدادات الشاشة الرئيسية", "ホーム設定", "主页设置");
        add("Home Settings", "होम सेटिंग्स", "Ajustes de inicio", "Réglages de l'accueil", "Startseiten-Einstellungen", "Impostazioni Home", "Настройки главного экрана", "Definições do ecrã inicial", "إعدادات الشاشة الرئيسية", "ホーム設定", "主页设置");
        add("Language, weather, scenes, apps and people", "भाषा, मौसम, दृश्य, ऐप्स और लोग", "Idioma, clima, escenas, aplicaciones y perfiles", "Langue, météo, scènes, applications et profils", "Sprache, Wetter, Szenen, Apps und Personen", "Lingua, meteo, scene, app e profili", "Язык, погода, сцены, приложения и профили", "Idioma, clima, cenas, aplicações e pessoas", "اللغة والطقس والمشاهد والتطبيقات والأشخاص", "言語、天気、シーン、アプリ、プロフィール", "语言、天气、场景、应用与用户");
        add("TV settings", "टीवी सेटिंग्स", "Ajustes del TV", "Réglages du téléviseur", "TV-Einstellungen", "Impostazioni TV", "Настройки ТВ", "Definições da TV", "إعدادات التلفزيون", "テレビ設定", "电视设置");
        add("TV Settings", "टीवी सेटिंग्स", "Ajustes del TV", "Réglages du téléviseur", "TV-Einstellungen", "Impostazioni TV", "Настройки ТВ", "Definições da TV", "إعدادات التلفزيون", "テレビ設定", "电视设置");
        add("Wi-Fi, picture, sound and the rest of the TV", "वाई-फ़ाई, चित्र, ध्वनि और बाकी टीवी सेटिंग्स", "Wi-Fi, imagen, sonido y el resto del televisor", "Wi-Fi, image, son et autres réglages du téléviseur", "WLAN, Bild, Ton und weitere TV-Einstellungen", "Wi-Fi, immagine, audio e altre impostazioni della TV", "Wi-Fi, изображение, звук и другие настройки ТВ", "Wi-Fi, imagem, som e restantes definições da TV", "واي فاي والصورة والصوت وبقية إعدادات التلفزيون", "Wi-Fi、映像、音声、その他のテレビ設定", "Wi-Fi、画面、声音及其他电视设置");
        add("Sleep timer", "स्लीप टाइमर", "Temporizador de apagado", "Minuteur de veille", "Schlummerfunktion", "Timer di spegnimento", "Таймер сна", "Temporizador", "مؤقت النوم", "スリープタイマー", "睡眠定时器");
        add("Sleep Timer", "स्लीप टाइमर", "Temporizador de apagado", "Minuteur de veille", "Schlummerfunktion", "Timer di spegnimento", "Таймер сна", "Temporizador", "مؤقت النوم", "スリープタイマー", "睡眠定时器");
        add("Turn the TV off after", "इतने समय बाद टीवी बंद करें:", "Apagar el televisor después de", "Éteindre le téléviseur après", "TV ausschalten nach", "Spegni la TV dopo", "Выключить ТВ через", "Desligar a TV após", "إيقاف تشغيل التلفزيون بعد", "次の時間後にテレビをオフにする", "在此时间后关闭电视：");
        add("Off", "बंद", "Desactivado", "Désactivé", "Aus", "Disattivato", "Выкл.", "Desligado", "إيقاف", "オフ", "关闭");
        add("On", "चालू", "Activado", "Activé", "Ein", "Attivo", "Вкл.", "Ligado", "تشغيل", "オン", "开启");
        add("Search", "खोजें", "Buscar", "Rechercher", "Suche", "Cerca", "Поиск", "Pesquisar", "بحث", "検索", "搜索");
        add("Refresh", "रीफ़्रेश करें", "Actualizar", "Actualiser", "Aktualisieren", "Aggiorna", "Обновить", "Atualizar", "تحديث", "更新", "刷新");
        add("Cancel", "रद्द करें", "Cancelar", "Annuler", "Abbrechen", "Annulla", "Отмена", "Cancelar", "إلغاء", "キャンセル", "取消");
        add("Next", "अगला", "Siguiente", "Suivant", "Weiter", "Avanti", "Далее", "Seguinte", "التالي", "次へ", "下一步");
        add("Continue", "जारी रखें", "Continuar", "Continuer", "Weiter", "Continua", "Продолжить", "Continuar", "متابعة", "続ける", "继续");
        add("Get Started", "शुरू करें", "Empezar", "Commencer", "Los geht's", "Inizia", "Начать", "Começar", "البدء", "はじめる", "开始使用");
        add("Let’s set up tvOS Launcher.", "आइए tvOS Launcher सेट करें।", "Configuremos tvOS Launcher.", "Configurons tvOS Launcher.", "Richten wir tvOS Launcher ein.", "Configuriamo tvOS Launcher.", "Давайте настроим tvOS Launcher.", "Vamos configurar o tvOS Launcher.", "لنقم بإعداد tvOS Launcher.", "tvOS Launcher をセットアップしましょう。", "让我们开始设置 tvOS Launcher。");
        add("Choose a look", "एक रूप चुनें", "Elige una apariencia", "Choisissez une apparence", "Aussehen wählen", "Scegli un aspetto", "Выберите вид", "Escolha uma aparência", "اختر مظهراً", "外観を選択", "选择外观");
        add("Use My Location", "मेरा स्थान उपयोग करें", "Usar mi ubicación", "Utiliser ma position", "Meinen Standort verwenden", "Usa la mia posizione", "Использовать моё местоположение", "Usar a minha localização", "استخدام موقعي", "現在地を使用", "使用我的位置");
        add("Choose a City…", "शहर चुनें…", "Elegir una ciudad…", "Choisir une ville…", "Stadt auswählen…", "Scegli una città…", "Выбрать город…", "Escolher uma cidade…", "اختر مدينة…", "都市を選択…", "选择城市…");
        add("Search by name", "नाम से खोजें", "Buscar por nombre", "Rechercher par nom", "Nach Namen suchen", "Cerca per nome", "Поиск по названию", "Pesquisar por nome", "البحث بالاسم", "名前で検索", "按名称搜索");
        add("Don't Show Weather", "मौसम न दिखाएं", "No mostrar el clima", "Ne pas afficher la météo", "Wetter nicht anzeigen", "Non mostrare il meteo", "Не показывать погоду", "Não mostrar o clima", "عدم إظهار الطقس", "天気を表示しない", "不显示天气");
        add("Finish Setting Up", "सेटअप पूरा करें", "Finalizar configuración", "Terminer la configuration", "Einrichtung abschließen", "Completa la configurazione", "Завершение настройки", "Concluir configuração", "إنهاء الإعداد", "セットアップを完了", "完成设置");
        add("Easy Setup (Automatic via ADB)", "आसान सेटअप (ADB द्वारा स्वचालित)", "Configuración fácil (automática por ADB)", "Configuration facile (automatique via ADB)", "Einfache Einrichtung (Automatisch per ADB)", "Configurazione rapida (automatica via ADB)", "Простая настройка (автоматически через ADB)", "Configuração fácil (automática via ADB)", "إعداد سهل (تلقائي عبر ADB)", "かんたん設定（ADBで自動許可）", "简易设置（通过 ADB 自动授权）");
        add("Grants all required permissions automatically at once", "सभी आवश्यक अनुमतियां एक साथ स्वचालित रूप से देता है", "Concede todos los permisos necesarios automáticamente a la vez", "Accorde automatiquement toutes les autorisations requises", "Erteilt alle erforderlichen Berechtigungen automatisch auf einmal", "Concede tutte le autorizzazioni necessarie automaticamente", "Автоматически предоставляет все необходимые разрешения сразу", "Concede todas as permissões necessárias automaticamente de uma vez", "يمنح جميع الأذونات المطلوبة تلقائياً دفعة واحدة", "必要なすべての権限を一度に自動付与します", "一次性自动授予所有所需权限");
        add("Manual Setup", "मैन्युअल सेटअप", "Configuración manual", "Configuration manuelle", "Manuelle Einrichtung", "Configurazione manuale", "Ручная настройка", "Configuração manual", "إعداد يدوي", "手動設定", "手动设置");
        add("Grant permissions one by one manually", "अनुमतियां एक-एक करके मैन्युअल रूप से दें", "Conceder permisos uno por uno manualmente", "Accorder les autorisations une par une manuellement", "Berechtigungen einzeln manuell erteilen", "Concedi le autorizzazioni una per una manualmente", "Предоставить разрешения вручную по одному", "Conceder permissões uma a uma manualmente", "منح الأذونات واحداً تلو الآخر يدوياً", "権限を1つずつ手動で許可します", "逐个手动授予权限");
        add("Enable ADB Debugging", "ADB डीबगिंग सक्षम करें", "Activar depuración ADB", "Activer le débogage ADB", "ADB-Debugging aktivieren", "Attiva debug ADB", "Включите отладку ADB", "Ativar depuração ADB", "تفعيل تصحيح أخطاء ADB", "ADBデバッグを有効化", "启用 ADB 调试");
        add("Grant Permissions Now", "अभी अनुमतियां दें", "Conceder permisos ahora", "Accorder les autorisations maintenant", "Berechtigungen jetzt erteilen", "Concedi autorizzazioni ora", "Предоставить разрешения сейчас", "Conceder permissões agora", "منح الأذونات الآن", "今すぐ権限を付与", "立即授予权限");
        add("Switch to Manual Setup", "मैन्युअल सेटअप पर स्विच करें", "Cambiar a configuración manual", "Passer à la configuration manuelle", "Zur manuellen Einrichtung wechseln", "Passa alla configurazione manuale", "Перейти к ручной настройке", "Mudar para configuração manual", "التبديل إلى الإعداد اليدوي", "手動設定に切り替え", "切换到手动设置");
        add("Make tvOS Launcher the Home Screen", "tvOS Launcher को होम स्क्रीन बनाएं", "Hacer que tvOS Launcher sea la pantalla de inicio", "Définir tvOS Launcher comme écran d'accueil", "tvOS Launcher als Startbildschirm festlegen", "Imposta tvOS Launcher come schermata Home", "Сделать tvOS Launcher главным экраном", "Tornar o tvOS Launcher o ecrã inicial", "اجعل tvOS Launcher الشاشة الرئيسية", "tvOS Launcher をホーム画面にする", "将 tvOS Launcher 设为主屏幕");
        add("Button Mapper & Control Panel", "बटन मैपर और कंट्रोल पैनल", "Asignador de botones y Panel de control", "Boutons télécommande et Centre de contrôle", "Tastenbelegung & Kontrollzentrum", "Mappatura tasti e Pannello di controllo", "Кнопки пульта и Панель управления", "Mapeador de botões e Painel de controlo", "تعيين الأزرار ولوحة التحكم", "ボタン割り当てとコントロールパネル", "按键映射与控制中心");
        add("Active in Accessibility", "एक्सेसिबिलिटी में सक्रिय", "Activo en Accesibilidad", "Actif dans Accessibilité", "In Bedienungshilfen aktiv", "Attivo in Accessibilità", "Включено в Спец. возможностях", "Ativo na Acessibilidade", "نشط في تسهيلات الاستخدام", "ユーザー補助で有効", "已在无障碍中启用");
        add("Enable in Accessibility so remapped buttons work in all apps", "एक्सेसिबिलिटी में सक्षम करें ताकि रीमैप किए गए बटन सभी ऐप्स में काम करें", "Activar en Accesibilidad para que los botones funcionen en todas las apps", "Activer dans Accessibilité pour que les boutons fonctionnent partout", "In Bedienungshilfen aktivieren, damit Tasten in allen Apps funktionieren", "Attiva in Accessibilità per usare i tasti rimappati in tutte le app", "Включить в Спец. возможностях для работы кнопок во всех приложениях", "Ativar na Acessibilidade para funcionar em todas as aplicações", "تفعيل في تسهيلات الاستخدام لتعمل الأزرار في كل التطبيقات", "すべてのアプリでボタン割り当てを使うにはユーザー補助で有効化", "在无障碍中启用以在所有应用中使用映射按键");
        add("You're All Set", "सब तैयार है", "Todo listo", "Tout est prêt", "Alles bereit", "Tutto pronto", "Всё готово", "Tudo pronto", "أنت جاهز تماماً", "準備完了", "一切就绪");
        add("Go to Home Screen", "होम स्क्रीन पर जाएं", "Ir a la pantalla de inicio", "Aller à l'écran d'accueil", "Zum Startbildschirm", "Vai alla schermata Home", "Перейти на главный экран", "Ir para o ecrã inicial", "الانتقال إلى الشاشة الرئيسية", "ホーム画面へ", "前往主屏幕");
        add("Save", "सहेजें", "Guardar", "Enregistrer", "Speichern", "Salva", "Сохранить", "Guardar", "حفظ", "保存", "保存");
        add("Reset", "रीसेट करें", "Restablecer", "Réinitialiser", "Zurücksetzen", "Ripristina", "Сбросить", "Repor", "إعادة ضبط", "リセット", "重置");

        // Control Panel Overlay
        add("Control Panel", "कंट्रोल पैनल", "Panel de control", "Centre de contrôle", "Kontrollzentrum", "Pannello di controllo", "Панель управления", "Painel de controlo", "لوحة التحكم", "コントロールパネル", "控制中心");
        add("Control Panel (Overlay)", "कंट्रोल पैनल (ओवरले)", "Panel de control (superposición)", "Centre de contrôle (superposition)", "Kontrollzentrum (Overlay)", "Pannello di controllo (sovrapposizione)", "Панель управления (поверх окон)", "Painel de controlo (sobreposição)", "لوحة التحكم (طبقة علوية)", "コントロールパネル (オーバーレイ)", "控制中心（悬浮层）");
        add("Connected", "कनेक्टेड", "Conectado", "Connecté", "Verbunden", "Connesso", "Подключено", "Ligado", "متصل", "接続済み", "已连接");
        add("Settings ›", "सेटिंग्स ›", "Ajustes ›", "Réglages ›", "Einstellungen ›", "Impostazioni ›", "Настройки ›", "Definições ›", "الإعدادات ›", "設定 ›", "设置 ›");
        add("Accessories", "एक्सेसरीज़", "Accesorios", "Accessoires", "Zubehör", "Accessori", "Аксессуары", "Acessórios", "الملحقات", "アクセサリ", "配件");
        add("Memory", "मेमोरी", "Memoria", "Mémoire", "Speicher", "Memoria", "Память", "Memória", "الذاكرة", "メモリ", "内存");
        add("Clean", "साफ़ करें", "Limpiar", "Nettoyer", "Bereinigen", "Pulisci", "Очистить", "Limpar", "تنظيف", "クリーン", "清理");
        add("Cleaned · ", "साफ़ किया गया · ", "Limpiado · ", "Nettoyé · ", "Bereinigt · ", "Pulito · ", "Очищено · ", "Limpo · ", "تم التنظيف · ", "クリーン完了 · ", "已清理 · ");
        add(" free", " खाली", " libre", " libre", " frei", " liberi", " свободно", " livre", " متاح", " 空き", " 可用");
        add("Do Not Disturb", "डू नॉट डिस्टर्ब", "No molestar", "Ne pas déranger", "Nicht stören", "Non disturbare", "Не беспокоить", "Não incomodar", "عدم الإزعاج", "おやすみモード", "勿扰模式");
        add("Audio", "ऑडियो", "Audio", "Audio", "Audio", "Audio", "Звук", "Áudio", "الصوت", "オーディオ", "音频");
        add("Sound", "ध्वनि", "Sonido", "Son", "Ton", "Suono", "Звук", "Som", "الصوت", "サウンド", "声音");
        add("Output", "आउटपुट", "Salida", "Sortie", "Ausgabe", "Uscita", "Вывод", "Saída", "الإخراج", "出力", "输出");
        add("Ambient Mode", "एम्बिएंट मोड", "Modo ambiente", "Mode ambiant", "Ambient-Modus", "Modalità ambiente", "Спящий режим", "Modo ambiente", "الوضع المحيطي", "アンビエントモード", "环境模式");
        add("Screensaver", "स्क्रीनसेवर", "Salvapantallas", "Économiseur d'écran", "Bildschirmschoner", "Salvaschermo", "Заставка", "Proteção de ecrã", "شاشة التوقف", "スクリーンセーバー", "屏幕保护程序");
        add("Power Off", "पावर ऑफ़", "Apagar", "Éteindre", "Ausschalten", "Spegni", "Выключить", "Desligar", "إيقاف التشغيل", "電源オフ", "关机");
        add("Sleep", "स्लीप", "Suspender", "Veille", "Ruhezustand", "Sospensione", "Сон", "Suspender", "سكون", "スリープ", "休眠");
        add("Grant ›", "अनुमति दें ›", "Permitir ›", "Autoriser ›", "Erlauben ›", "Consenti ›", "Разрешить ›", "Permitir ›", "منح الإذن ›", "許可 ›", "授权 ›");
        add("No recent apps", "कोई हालिया ऐप नहीं", "Sin aplicaciones recientes", "Aucune application récente", "Keine letzten Apps", "Nessuna app recente", "Нет недавних приложений", "Sem aplicações recentes", "لا توجد تطبيقات حديثة", "最近のアプリはありません", "暂无最近使用的应用");

        // Settings hints & notes
        add("Up and down to move, OK when done", "स्थानांतरित करने के लिए ऊपर/नीचे, पूरा होने पर OK", "Arriba y abajo para mover, OK al terminar", "Haut et bas pour déplacer, OK pour valider", "Hoch/Runter zum Verschieben, OK wenn fertig", "Su e giù per spostare, OK per confermare", "Вверх и вниз для перемещения, OK для завершения", "Cima e baixo para mover, OK para concluir", "لأعلى ولأسفل للنقل، وموافق عند الانتهاء", "上下で移動、OKで完了", "上下移动位置，按 OK 完成");
        add("Up and down to browse, OK to change, Back to close", "ब्राउज़ करने के लिए ऊपर/नीचे, बदलने के लिए OK, बंद करने के लिए Back", "Arriba y abajo para explorar, OK para cambiar, Atrás para cerrar", "Haut et bas pour parcourir, OK pour modifier, Retour pour fermer", "Hoch/Runter zum Blättern, OK zum Ändern, Zurück zum Schließen", "Su e giù per sfogliare, OK per modificare, Indietro per chiudere", "Вверх/вниз для обзора, OK для изменения, Назад для закрытия", "Cima e baixo para navegar, OK para alterar, Voltar para fechar", "لأعلى ولأسفل للتصفح، موافق للتغيير، رجوع للإغلاق", "上下で選択、OKで変更、戻るで閉じる", "上下浏览，按 OK 更改，按返回关闭");
        add("OK to change, hold OK to move, Back to go back", "बदलने के लिए OK, हटाने के लिए OK दबाए रखें, वापस जाने के लिए Back", "OK para cambiar, mantén OK para mover, Atrás para volver", "OK pour modifier, maintenir OK pour déplacer, Retour pour revenir", "OK zum Ändern, OK halten zum Verschieben, Zurück", "OK per modificare, tieni premuto OK per spostare, Indietro per tornare", "OK — изменить, удерживайте OK — переместить, Назад — вернуться", "OK para alterar, manter OK para mover, Voltar para sair", "موافق للتغيير، مطولاً للنقل، رجوع للعودة", "OKで変更、OK長押しで移動、戻るで前の画面へ", "按 OK 更改，长按 OK 移动，按返回退出");
        add("OK to change, Back to go back", "बदलने के लिए OK, वापस जाने के लिए Back", "OK para cambiar, Atrás para volver", "OK pour modifier, Retour pour revenir", "OK zum Ändern, Zurück zum Zurückkehren", "OK per modificare, Indietro per tornare", "OK — изменить, Назад — вернуться", "OK para alterar, Voltar para sair", "موافق للتغيير، رجوع للعودة", "OKで変更、戻るで前の画面へ", "按 OK 更改，按返回退出");
        add("Changes the words on the home screen, date, weather and settings.", "होम स्क्रीन, तारीख, मौसम और सेटिंग्स की भाषा बदलता है।", "Cambia el idioma de la pantalla de inicio, la fecha, el clima y los ajustes.", "Change la langue de l'écran d'accueil, de la date, de la météo et des réglages.", "Ändert die Sprache auf dem Startbildschirm, bei Datum, Wetter und Einstellungen.", "Cambia la lingua della schermata Home, della data, del meteo e delle impostazioni.", "Изменяет язык главного экрана, даты, погоды и настроек.", "Altera o idioma do ecrã inicial, data, clima e definições.", "يغير اللغة في الشاشة الرئيسية والتاريخ والطقس والإعدادات.", "ホーム画面、日付、天気、設定の表示言語を変更します。", "更改主屏幕、日期、天气和设置中的显示语言。");

        // Location & Weather settings
        add("Current place", "वर्तमान स्थान", "Ubicación actual", "Lieu actuel", "Aktueller Ort", "Posizione attuale", "Текущее место", "Local atual", "الموقع الحالي", "現在の場所", "当前位置");
        add("Automatic (from internet)", "स्वचालित (इंटरनेट से)", "Automático (por internet)", "Automatique (via Internet)", "Automatisch (aus dem Internet)", "Automatica (da Internet)", "Автоматически (из интернета)", "Automático (via internet)", "تلقائي (عبر الإنترنت)", "自動（インターネットから）", "自动（通过网络）");
        add("Set manually", "मैन्युअल रूप से सेट", "Configurado manualmente", "Défini manuellement", "Manuell festgelegt", "Impostata manualmente", "Задано вручную", "Definido manualmente", "تم التعيين يدوياً", "手動設定", "手动设置");
        add("Detected automatically", "स्वचालित रूप से पहचाना गया", "Detectado automáticamente", "Détecté automatiquement", "Automatisch erkannt", "Rilevata automaticamente", "Определено автоматически", "Detetado automaticamente", "تم الاكتشاف تلقائياً", "自動検出", "自动检测");
        add("Search for a city or town…", "शहर या कस्बा खोजें…", "Buscar una ciudad o localidad…", "Rechercher une ville…", "Nach einer Stadt suchen…", "Cerca una città…", "Найти город или населенный пункт…", "Pesquisar uma cidade…", "ابحث عن مدينة أو بلدة…", "都市名を検索…", "搜索城市或地区…");
        add("Use automatic location", "स्वचालित स्थान का उपयोग करें", "Usar ubicación automática", "Utiliser la position automatique", "Automatischen Standort verwenden", "Usa posizione automatica", "Использовать автоопределение", "Usar localização automática", "استخدام الموقع التلقائي", "自動位置情報を使用", "使用自动定位");
        add("Used for the weather on the home screen and local headlines.", "होम स्क्रीन पर मौसम और स्थानीय समाचारों के लिए उपयोग किया जाता है।", "Se usa para el clima en la pantalla de inicio y las noticias locales.", "Utilisé pour la météo sur l'écran d'accueil et les actualités locales.", "Wird für das Wetter auf dem Startbildschirm und lokale Schlagzeilen verwendet.", "Usata per il meteo sulla schermata Home e le notizie locali.", "Используется для прогноза погоды на главном экране и местных новостей.", "Usado para o clima no ecrã inicial e notícias locais.", "يُستخدم للطقس على الشاشة الرئيسية والأخبار المحلية.", "ホーム画面の天気とローカルニュースに使用されます。", "用于主屏幕天气显示和本地资讯。");
        add("Weather on the home screen", "होम स्क्रीन पर मौसम", "Clima en la pantalla de inicio", "Météo sur l'écran d'accueil", "Wetter auf dem Startbildschirm", "Meteo sulla schermata Home", "Погода на главном экране", "Clima no ecrã inicial", "الطقس على الشاشة الرئيسية", "ホーム画面の天気", "在主屏幕显示天气");
        add("Temperature", "तापमान", "Temperatura", "Température", "Temperatur", "Temperatura", "Температура", "Temperatura", "درجة الحرارة", "温度単位", "温度单位");
        add("Update every", "अपडेट अंतराल", "Actualizar cada", "Actualiser toutes les", "Aktualisieren alle", "Aggiorna ogni", "Обновлять каждые", "Atualizar a cada", "تحديث كل", "更新間隔", "更新频率");
        add(" minutes", " मिनट", " minutos", " minutes", " Minuten", " minuti", " мин.", " minutos", " دقيقة", " 分", " 分钟");
        add("Weather source", "मौसम का स्रोत", "Fuente meteorológica", "Source météo", "Wetterquelle", "Fonte meteo", "Источник погоды", "Fonte meteorológica", "مصدر الطقس", "天気の情報源", "天气数据源");
        add("Refresh weather now", "अभी मौसम रीफ़्रेश करें", "Actualizar clima ahora", "Actualiser la météo maintenant", "Wetter jetzt aktualisieren", "Aggiorna meteo ora", "Обновить погоду сейчас", "Atualizar clima agora", "تحديث الطقس الآن", "今すぐ天気を更新", "立即刷新天气");
        add("Updating…", "अपडेट हो रहा है…", "Actualizando…", "Actualisation…", "Aktualisiere…", "Aggiornamento…", "Обновление…", "A atualizar…", "جارٍ التحديث…", "更新中…", "正在更新…");
        add("Updated", "अपडेट किया गया", "Actualizado", "Actualisé", "Aktualisiert", "Aggiornato", "Обновлено", "Atualizado", "تم التحديث", "更新完了", "已更新");

        // Classic wallpaper & Apps & Profiles
        add("WALLPAPER", "वॉलपेपर", "FONDO DE PANTALLA", "FOND D'ÉCRAN", "HINTERGRUND", "SFONDO", "ОБОИ", "PAPEL DE PAREDE", "خلفية الشاشة", "壁紙", "壁纸");
        add("Show in dock · press OK to hide", "डॉक में दिख रहा है · छिपाने के लिए OK दबाएं", "Visible en el dock · pulsa OK para ocultar", "Affiché dans le dock · OK pour masquer", "Im Dock sichtbar · OK zum Ausblenden", "Mostrata nel dock · premi OK per nascondere", "В доке · нажмите OK, чтобы скрыть", "Visível no dock · prima OK para ocultar", "ظاهر في الشريط · اضغط موافق للإخفاء", "ドックに表示中 · OKで非表示", "在程序坞中显示 · 按 OK 隐藏");
        add("Hidden from dock · press OK to show", "डॉक से छिपा हुआ · दिखाने के लिए OK दबाएं", "Oculto del dock · pulsa OK para mostrar", "Masqué du dock · OK pour afficher", "Im Dock ausgeblendet · OK zum Anzeigen", "Nascosta dal dock · premi OK per mostrare", "Скрыто из дока · нажмите OK, чтобы показать", "Oculto do dock · prima OK para mostrar", "مخفي من الشريط · اضغط موافق للإظهار", "ドックから非表示 · OKで表示", "已从程序坞隐藏 · 按 OK 显示");
        add("Press OK on an app to show or hide it from the home screen dock.", "होम स्क्रीन डॉक से किसी ऐप को दिखाने या छिपाने के लिए उस पर OK दबाएं।", "Pulsa OK en una aplicación para mostrarla u ocultarla del dock de inicio.", "Appuyez sur OK sur une application pour l'afficher ou la masquer du dock.", "Drücken Sie OK auf einer App, um sie im Dock ein- oder auszublenden.", "Premi OK su un'app per mostrarla o nasconderla dal dock della schermata Home.", "Нажмите OK на приложении, чтобы показать или скрыть его в доке главного экрана.", "Prima OK numa aplicação para a mostrar ou ocultar no dock do ecrã inicial.", "اضغط موافق على أي تطبيق لإظهاره أو إخفائه من شريط الشاشة الرئيسية.", "アプリでOKを押すと、ホーム画面のドックへの表示・非表示を切り替えられます。", "在应用上按 OK 可在主屏幕程序坞中显示或隐藏该应用。");
        add("Active", "सक्रिय", "Activo", "Actif", "Aktiv", "Attivo", "Активен", "Ativo", "نشط", "有効", "当前使用");
        add("Switch", "बदलें", "Cambiar", "Changer", "Wechseln", "Cambia", "Выбрать", "Mudar", "تبديل", "切り替え", "切换");
        add("+ Add a person", "+ व्यक्ति जोड़ें", "+ Añadir persona", "+ Ajouter un profil", "+ Person hinzufügen", "+ Aggiungi persona", "+ Добавить профиль", "+ Adicionar pessoa", "+ إضافة شخص", "+ プロフィールを追加", "+ 添加用户");
        add("Each person gets their own pinned apps and Continue Watching list.", "प्रत्येक व्यक्ति को अपने पिन किए गए ऐप्स और देखना जारी रखें सूची मिलती है।", "Cada persona tiene sus propias aplicaciones fijadas y su lista de Seguir viendo.", "Chaque profil dispose de ses propres applications épinglées et de sa liste Continuer à regarder.", "Jede Person erhält ihre eigenen angehefteten Apps und Weiterschauen-Liste.", "Ogni persona ha le proprie app fissate e la propria lista Continua a guardare.", "У каждого профиля свои закрепленные приложения и список «Продолжить просмотр».", "Cada pessoa tem as suas próprias aplicações afixadas e lista Continuar a ver.", "يحصل كل شخص على تطبيقاته المثبتة وقائمة متابعة المشاهدة الخاصة به.", "プロフィールごとにピン留めアプリや「視聴を続ける」リストを個別に管理できます。", "每位用户拥有独立的固定应用和“继续观看”列表。");
        add("Rename…", "नाम बदलें…", "Cambiar nombre…", "Renommer…", "Umbenennen…", "Rinomina…", "Переименовать…", "Mudar nome…", "إعادة تسمية…", "名前を変更…", "重命名…");
        add("Avatar color…", "अवतार का रंग…", "Color de avatar…", "Couleur de l'avatar…", "Avatar-Farbe…", "Colore avatar…", "Цвет аватара…", "Cor do avatar…", "لون الصورة الرمزية…", "アバターの色…", "头像颜色…");
        add("Delete profile", "प्रोफ़ाइल हटाएं", "Eliminar perfil", "Supprimer le profil", "Profil löschen", "Elimina profilo", "Удалить профиль", "Eliminar perfil", "حذف الملف الشخصي", "プロフィールを削除", "删除用户");
        add("Rename Profile", "प्रोफ़ाइल का नाम बदलें", "Renombrar perfil", "Renommer le profil", "Profil umbenennen", "Rinomina profilo", "Переименовать профиль", "Mudar nome do perfil", "إعادة تسمية الملف الشخصي", "プロフィールの名前変更", "重命名用户");
        add("Avatar Color", "अवतार का रंग", "Color de avatar", "Couleur de l'avatar", "Avatar-Farbe", "Colore avatar", "Цвет аватара", "Cor do avatar", "لون الصورة الرمزية", "アバターの色", "头像颜色");

        // Button Mapper & Recent Apps & Skip Setup
        add("Skip Setup", "सेटअप छोड़ें", "Omitir configuración", "Ignorer la configuration", "Einrichtung überspringen", "Salta configurazione", "Пропустить настройку", "Ignorar configuração", "تخطي الإعداد", "セットアップをスキップ", "跳过设置");
        add("Recent Apps", "हाल के ऐप्स", "Aplicaciones recientes", "Applications récentes", "Letzte Apps", "App recenti", "Недавние приложения", "Aplicações recentes", "التطبيقات الحديثة", "最近のアプリ", "最近使用的应用");
        add("Recent Apps Switcher", "हाल के ऐप्स स्विचर", "Selector de apps recientes", "Sélecteur d'applis récentes", "App-Umschalter (Letzte Apps)", "Selettore app recenti", "Переключатель недавних приложений", "Seletor de aplicações recentes", "مبدل التطبيقات الحديثة", "最近のアプリスイッチャー", "最近应用切换器");
        add("Recent Apps Switcher (Overlay)", "हाल के ऐप्स स्विचर (ओवरले)", "Selector de apps recientes (superposición)", "Sélecteur d'applis récentes (superposition)", "Letzte Apps-Umschalter (Overlay)", "Selettore app recenti (sovrapposizione)", "Недавние приложения (поверх окон)", "Seletor de aplicações recentes (sobreposição)", "مبدل التطبيقات الحديثة (طبقة علوية)", "最近のアプリスイッチャー (オーバーレイ)", "最近应用切换器（悬浮层）");
        add("No recent apps yet", "अभी कोई हालिया ऐप नहीं", "Aún no hay aplicaciones recientes", "Aucune application récente pour le moment", "Noch keine letzten Apps", "Ancora nessuna app recente", "Пока нет недавних приложений", "Ainda sem aplicações recentes", "لا توجد تطبيقات حديثة بعد", "最近のアプリはまだありません", "暂无最近使用的应用");
        add("Press ▲ on an app to close it", "बंद करने के लिए ऐप पर ▲ दबाएं", "Pulsa ▲ en una aplicación para cerrarla", "Appuyez sur ▲ sur une application pour la fermer", "Drücken Sie ▲ auf einer App, um sie zu schließen", "Premi ▲ su un'app per chiuderla", "Нажмите ▲ на приложении, чтобы закрыть его", "Prima ▲ numa aplicação para a fechar", "اضغط ▲ على أي تطبيق لإغلاقه", "アプリ上で ▲ を押して終了", "在应用上按 ▲ 将其关闭");
        add("Close All", "सभी बंद करें", "Cerrar todo", "Tout fermer", "Alle schließen", "Chiudi tutto", "Закрыть все", "Fechar tudo", "إغلاق الكل", "すべて閉じる", "全部关闭");
        add("Close", "बंद करें", "Cerrar", "Fermer", "Schließen", "Chiudi", "Закрыть", "Fechar", "إغلاق", "閉じる", "关闭");
        add("Done", "हो गया", "Listo", "Terminé", "Fertig", "Fatto", "Готово", "Concluído", "تم", "完了", "完成");
        add("None", "कोई नहीं", "Ninguno", "Aucun", "Keine", "Nessuno", "Нет", "Nenhum", "لا شيء", "なし", "无");
        add("None (Clear action)", "कोई नहीं (क्रिया हटाएं)", "Ninguno (borrar acción)", "Aucun (effacer l'action)", "Keine (Aktion löschen)", "Nessuno (rimuovi azione)", "Нет (очистить действие)", "Nenhum (limpar ação)", "لا شيء (مسح الإجراء)", "なし（動作をクリア）", "无（清除操作）");
        add("Single Tap (1x)", "सिंगल टैप (1x)", "Un toque (1x)", "Appui simple (1x)", "Einfach drücken (1x)", "Singolo tocco (1x)", "Одиночное нажатие (1x)", "Toque único (1x)", "ضغطة واحدة (1x)", "シングルタップ (1x)", "单击 (1x)");
        add("Double Tap (2x)", "डबल टैप (2x)", "Doble toque (2x)", "Double appui (2x)", "Doppelt drücken (2x)", "Doppio tocco (2x)", "Двойное нажатие (2x)", "Toque duplo (2x)", "ضغطتان (2x)", "ダブルタップ (2x)", "双击 (2x)");
        add("Triple Tap (3x)", "ट्रिपल टैप (3x)", "Triple toque (3x)", "Triple appui (3x)", "Dreifach drücken (3x)", "Triplo tocco (3x)", "Тройное нажатие (3x)", "Toque triplo (3x)", "ثلاث ضغطات (3x)", "トリプルタップ (3x)", "三击 (3x)");
        add("+ Add New Button to Remap…", "+ रीमैप करने के लिए नया बटन जोड़ें…", "+ Añadir nuevo botón para reasignar…", "+ Ajouter un bouton à réattribuer…", "+ Neue Taste zum Neubelegen hinzufügen…", "+ Aggiungi nuovo tasto da rimappare…", "+ Добавить кнопку для переназначения…", "+ Adicionar novo botão para mapear…", "+ إضافة زر جديد لإعادة التعيين…", "+ 新しいボタンを割り当てる…", "+ 添加要映射的新按键…");
        add("Assign Single Tap (1x), Double Tap (2x) & Triple Tap (3x) actions", "सिंगल (1x), डबल (2x) और ट्रिपल टैप (3x) क्रियाएं सेट करें", "Asignar acciones de un toque (1x), doble (2x) y triple (3x)", "Attribuer des actions d'appui simple (1x), double (2x) et triple (3x)", "Einfach- (1x), Doppel- (2x) und Dreifachdruck-Aktionen (3x) zuweisen", "Assegna azioni per singolo (1x), doppio (2x) e triplo tocco (3x)", "Назначить действия для одиночного (1x), двойного (2x) и тройного (3x) нажатия", "Atribuir ações de toque único (1x), duplo (2x) e triplo (3x)", "تعيين إجراءات الضغطة الواحدة (1x) والمزدوجة (2x) والثلاثية (3x)", "シングル(1x)、ダブル(2x)、トリプルタップ(3x)の動作を割り当て", "分配单击 (1x)、双击 (2x) 和三击 (3x) 操作");
        add("Map any button on your remote with Single Tap (1x), Double Tap (2x) and Triple Tap (3x) actions — open the Control Panel, Recent Apps Switcher, or any app.", "अपने रिमोट के किसी भी बटन पर सिंगल (1x), डबल (2x) और ट्रिपल टैप (3x) सेट करें — कंट्रोल पैनल, हाल के ऐप्स स्विचर या कोई भी ऐप खोलें।", "Asigna cualquier botón del mando con un toque (1x), doble (2x) y triple (3x) para abrir el Panel de control, el Selector de apps recientes o cualquier aplicación.", "Attribuez n'importe quel bouton avec un appui simple (1x), double (2x) ou triple (3x) pour ouvrir le Centre de contrôle, les Applications récentes ou une application.", "Belegen Sie jede Taste mit Einfach- (1x), Doppel- (2x) und Dreifachdruck (3x) — öffnen Sie das Kontrollzentrum, den App-Umschalter oder jede App.", "Mappa qualsiasi tasto con tocco singolo (1x), doppio (2x) e triplo (3x) per aprire il Pannello di controllo, le App recenti o qualsiasi app.", "Назначьте на любую кнопку пульта одиночное (1x), двойное (2x) и тройное (3x) нажатие — открывайте Панель управления, Недавние приложения или любое приложение.", "Mapeie qualquer botão do comando com toque único (1x), duplo (2x) e triplo (3x) para abrir o Painel de controlo, Aplicações recentes ou qualquer aplicação.", "عيّن أي زر في الريموت بضغطة واحدة (1x) أو مزدوجة (2x) أو ثلاثية (3x) لفتح لوحة التحكم أو مبدل التطبيقات الحديثة أو أي تطبيق.", "リモコンの任意のボタンにシングル(1x)・ダブル(2x)・トリプルタップ(3x)を割り当てて、コントロールパネルや最近のアプリスイッチャー、任意のアプリを開けます。", "为遥控器上的任意按键设置单击 (1x)、双击 (2x) 和三击 (3x) 操作 — 快速打开控制中心、最近应用切换器或任意应用。");
        add("ACCESSIBILITY SERVICE", "एक्सेसिबिलिटी सेवा", "SERVICIO DE ACCESIBILIDAD", "SERVICE D'ACCESSIBILITÉ", "BEDIENUNGSHILFEN-DIENST", "SERVIZIO DI ACCESSIBILITÀ", "СЛУЖБА СПЕЦИАЛЬНЫХ ВОЗМОЖНОСТЕЙ", "SERVIÇO DE ACESSIBILIDADE", "خدمة تسهيلات الاستخدام", "ユーザー補助サービス", "无障碍服务");
        add("Button Mapper service", "बटन मैपर सेवा", "Servicio de asignación de botones", "Service d'attribution des boutons", "Tastenbelegungs-Dienst", "Servizio mappatura tasti", "Служба переназначения кнопок", "Serviço de mapeamento de botões", "خدمة تعيين الأزرار", "ボタン割り当てサービス", "按键映射服务");
        add("Enable in Accessibility…", "एक्सेसिबिलिटी में सक्षम करें…", "Activar en Accesibilidad…", "Activer dans Accessibilité…", "In Bedienungshilfen aktivieren…", "Attiva in Accessibilità…", "Включить в Спец. возможностях…", "Ativar na Acessibilidade…", "تفعيل في تسهيلات الاستخدام…", "ユーザー補助で有効化…", "在无障碍设置中启用…");
        add("REMAPPED BUTTONS", "रीमैप किए गए बटन", "BOTONES REASIGNADOS", "BOUTONS RÉATTRIBUÉS", "NEU BELEGTE TASTEN", "TASTI RIMAPPATI", "ПЕРЕНАЗНАЧЕННЫЕ КНОПКИ", "BOTÕES ASSOCIADOS", "الأزرار المعاد تعيينها", "割り当て済みのボタン", "已映射的按键");
        add("+ Add new button to remap", "+ रीमैप करने के लिए नया बटन जोड़ें", "+ Añadir nuevo botón para reasignar", "+ Ajouter un bouton à réattribuer", "+ Neue Taste zum Neubelegen hinzufügen", "+ Aggiungi nuovo tasto da rimappare", "+ Добавить кнопку для переназначения", "+ Adicionar novo botão para mapear", "+ إضافة زر جديد لإعادة التعيين", "+ 新しいボタンを割り当てる", "+ 添加要映射的新按键");
        add("Listen for a remote key press", "रिमोट बटन दबाने की प्रतीक्षा करें", "Detectar pulsación de tecla del mando", "Appuyez sur une touche de la télécommande", "Auf Tastendruck der Fernbedienung warten", "Rileva la pressione di un tasto del telecomando", "Нажмите кнопку на пульте", "Detetar tecla do comando", "انتظار الضغط على زر في الريموت", "リモコンのボタン入力を待機", "检测遥控器按键输入");
        add("Add new button to remap", "रीमैप करने के लिए नया बटन जोड़ें", "Añadir nuevo botón para reasignar", "Ajouter un bouton à réattribuer", "Neue Taste zum Neubelegen hinzufügen", "Aggiungi nuovo tasto da rimappare", "Добавить кнопку", "Adicionar novo botão", "إضافة زر جديد لإعادة التعيين", "新しいボタンを割り当てる", "添加要映射的新按键");
        add("Press the button on your remote that you want to remap…\n\n(D-pad navigation, OK and Back are kept for normal navigation.)", "अपने रिमोट पर वह बटन दबाएं जिसे आप रीमैप करना चाहते हैं…\n\n(सामान्य नेविगेशन के लिए डी-पैड, OK और Back सुरक्षित हैं।)", "Pulsa el botón del mando que quieras reasignar…\n\n(La cruceta, OK y Atrás se mantienen para la navegación normal.)", "Appuyez sur le bouton de la télécommande à réattribuer…\n\n(Les flèches, OK et Retour restent réservés à la navigation.)", "Drücken Sie die Taste auf Ihrer Fernbedienung, die Sie neu belegen möchten…\n\n(Steuerkreuz, OK und Zurück bleiben für die normale Navigation erhalten.)", "Premi il tasto del telecomando che vuoi rimappare…\n\n(Le frecce direzionali, OK e Indietro restano per la navigazione normale.)", "Нажмите кнопку на пульте, которую хотите переназначить…\n\n(Стрелки, OK и Назад сохраняются для обычной навигации.)", "Prima o botão do comando que pretende mapear…\n\n(As setas, OK e Voltar são mantidos para a navegação normal.)", "اضغط على الزر الذي تريد إعادة تعيينه في جهاز التحكم…\n\n(تظل أزرار الاتجاهات وموافق ورجوع للتنقل العادي.)", "割り当てたいリモコンのボタンを押してください…\n\n（十字キー、決定、戻るボタンは通常操作のため維持されます）", "请按下遥控器上要重新映射的按键…\n\n（方向键、OK 键和返回键保留用于正常导航。）");
        add("Change what this button opens…", "बदलें कि यह बटन क्या खोलता है…", "Cambiar qué abre este botón…", "Modifier l'action de ce bouton…", "Ändern, was diese Taste öffnet…", "Cambia cosa apre questo tasto…", "Изменить действие кнопки…", "Alterar o que este botão abre…", "تغيير ما يفتحه هذا الزر…", "このボタンで開く項目を変更…", "更改此按键打开的内容…");
        add("Remove button remap", "बटन रीमैप हटाएं", "Eliminar asignación de botón", "Supprimer l'attribution", "Tastenbelegung entfernen", "Rimuovi mappatura tasto", "Удалить переназначение", "Remover mapeamento do botão", "إزالة تعيين الزر", "ボタンの割り当てを解除", "移除按键映射");
        add("Enable tvOS Launcher in Android TV Accessibility Settings so remapped remote buttons and the Control Panel overlay work over any app.", "Android TV एक्सेसिबिलिटी सेटिंग्स में tvOS Launcher सक्षम करें ताकि रीमैप किए गए बटन और कंट्रोल पैनल किसी भी ऐप के ऊपर काम कर सकें।", "Activa tvOS Launcher en los Ajustes de accesibilidad de Android TV para que los botones reasignados y el Panel de control funcionen sobre cualquier aplicación.", "Activez tvOS Launcher dans les paramètres d'accessibilité Android TV pour utiliser les boutons réattribués et le Centre de contrôle sur n'importe quelle application.", "Aktivieren Sie tvOS Launcher in den Android TV-Bedienungshilfen, damit neu belegte Tasten und das Kontrollzentrum über jeder App funktionieren.", "Attiva tvOS Launcher nelle Impostazioni di accessibilità di Android TV affinché i tasti rimappati e il Pannello di controllo funzionino su qualsiasi app.", "Включите tvOS Launcher в специальных возможностях Android TV, чтобы переназначенные кнопки и Панель управления работали поверх любых приложений.", "Ative o tvOS Launcher nas definições de acessibilidade da Android TV para que os botões mapeados e o Painel de controlo funcionem sobre qualquer aplicação.", "قم بتفعيل tvOS Launcher في إعدادات تسهيلات الاستخدام في Android TV ليعمل تعيين الأزرار ولوحة التحكم فوق أي تطبيق.", "Android TVのユーザー補助設定でtvOS Launcherを有効にすると、どのアプリの上でもボタン割り当てとコントロールパネルが機能します。", "请在 Android TV 无障碍设置中启用 tvOS Launcher，以便映射按键和控制中心可在任何应用上方正常使用。");

        // Home & System & About
        add("HOME & PERMISSIONS", "होम और अनुमतियां", "INICIO Y PERMISOS", "ACCUEIL ET AUTORISATIONS", "STARTSEITE & BERECHTIGUNGEN", "HOME E AUTORIZZAZIONI", "ГЛАВНЫЙ ЭКРАН И РАЗРЕШЕНИЯ", "INÍCIO E PERMISSÕES", "الشاشة الرئيسية والأذونات", "ホームと権限", "主页与权限");
        add("Default home screen", "डिफ़ॉल्ट होम स्क्रीन", "Pantalla de inicio predeterminada", "Écran d'accueil par défaut", "Standard-Startbildschirm", "Schermata Home predefinita", "Главный экран по умолчанию", "Ecrã inicial predefinido", "الشاشة الرئيسية الافتراضية", "デフォルトのホーム画面", "默认主屏幕");
        add("Set as default…", "डिफ़ॉल्ट के रूप में सेट करें…", "Establecer como predeterminado…", "Définir par défaut…", "Als Standard festlegen…", "Imposta come predefinita…", "Сделать по умолчанию…", "Definir como padrão…", "تعيين كافتراضي…", "デフォルトに設定…", "设为默认…");
        add("Power Off & Sleep Timer permission", "पावर ऑफ़ और स्लीप टाइमर अनुमति", "Permiso de apagado y temporizador", "Autorisation d'extinction et minuteur", "Berechtigung für Ausschalten & Schlummerfunktion", "Autorizzazione spegnimento e timer", "Разрешение на выключение и таймер сна", "Permissão de desligar e temporizador", "إذن إيقاف التشغيل ومؤقت النوم", "電源オフとスリープタイマーの権限", "关机与睡眠定时器权限");
        add("Continue Watching & DND permission", "देखना जारी रखें और DND अनुमति", "Permiso de Seguir viendo y No molestar", "Autorisation Continuer à regarder et Ne pas déranger", "Berechtigung für Weiterschauen & Nicht stören", "Autorizzazione Continua a guardare e Non disturbare", "Разрешение для «Продолжить просмотр» и «Не беспокоить»", "Permissão Continuar a ver e Não incomodar", "إذن متابعة المشاهدة وعدم الإزعاج", "「視聴を続ける」とおやすみモードの権限", "继续观看与勿扰模式权限");
        add("Allowed", "अनुमति है", "Permitido", "Autorisé", "Erlaubt", "Consentito", "Разрешено", "Permitido", "مسموح", "許可済み", "已允许");
        add("Allow…", "अनुमति दें…", "Permitir…", "Autoriser…", "Erlauben…", "Consenti…", "Разрешить…", "Permitir…", "سماح…", "許可する…", "去允许…");
        add("Not on this TV", "इस टीवी पर उपलब्ध नहीं", "No disponible en este TV", "Non disponible sur ce téléviseur", "Nicht auf diesem TV", "Non disponibile su questa TV", "Недоступно на этом ТВ", "Não disponível nesta TV", "غير متوفر على هذا التلفزيون", "このテレビでは利用不可", "此电视不支持");
        add("Navigation click sounds", "नेविगेशन क्लिक ध्वनि", "Sonidos de clic al navegar", "Sons de navigation", "Navigations-Klicktöne", "Suoni clic di navigazione", "Звуки щелчков навигации", "Sons de clique na navegação", "أصوات نقرات التنقل", "ナビゲーション効果音", "导航按键音");
        add("PRESENTATION &CLASSIC SHELF", "प्रेजेंटेशन और क्लासिक शेल्फ़", "PRESENTACIÓN Y ESTANTE CLÁSICO", "PRÉSENTATION ET ÉTAGÈRE CLASSIQUE", "PRÄSENTATION & KLASSISCHES REGAL", "PRESENTAZIONE E SCAFFALE CLASSICO", "ПРЕЗЕНТАЦИЯ И КЛАССИЧЕСКАЯ ПОЛКА", "APRESENTAÇÃO E PRATELEIRA CLÁSSICA", "العرض التقديمي والرف الكلاسيكي", "プレゼンテーションとクラシックシェルフ", "演示模式与经典顶栏");
        add("Presentation mode", "प्रेजेंटेशन मोड", "Modo presentación", "Mode présentation", "Präsentationsmodus", "Modalità presentazione", "Режим презентации", "Modo de apresentação", "وضع العرض التقديمي", "プレゼンテーションモード", "演示模式");
        add("Presentation address & QR code", "प्रेजेंटेशन पता और QR कोड", "Dirección de presentación y código QR", "Adresse de présentation et QR code", "Präsentationsadresse & QR-Code", "Indirizzo presentazione e codice QR", "Адрес презентации и QR-код", "Endereço de apresentação e código QR", "عنوان العرض التقديمي ورمز QR", "プレゼンテーションアドレスとQRコード", "演示地址与二维码");
        add("Turn on first", "पहले चालू करें", "Actívalo primero", "Activez d'abord", "Zuerst einschalten", "Attiva prima", "Сначала включите", "Ative primeiro", "قم بالتشغيل أولاً", "先にオンにしてください", "请先开启");
        add("Classic Top Shelf content", "क्लासिक टॉप शेल्फ़ सामग्री", "Contenido del estante superior clásico", "Contenu de l'étagère supérieure classique", "Inhalt des klassischen oberen Regals", "Contenuto scaffale superiore classico", "Содержимое классической верхней полки", "Conteúdo da prateleira superior clássica", "محتوى الرف العلوي الكلاسيكي", "クラシックトップシェルフの内容", "经典顶栏内容");
        add("Recommendations", "सिफ़ारिशें", "Recomendaciones", "Recommandations", "Empfehlungen", "Consigliati", "Рекомендации", "Recomendações", "التوصيات", "おすすめ", "推荐内容");
        add("News", "समाचार", "Noticias", "Actualités", "Nachrichten", "Notizie", "Новости", "Notícias", "الأخبار", "ニュース", "新闻资讯");
        add("Recommendations & News", "सिफ़ारिशें और समाचार", "Recomendaciones y noticias", "Recommandations et actualités", "Empfehlungen & Nachrichten", "Consigliati e notizie", "Рекомендации и новости", "Recomendações e notícias", "التوصيات والأخبار", "おすすめとニュース", "推荐与新闻");
        add("Run first-time setup again…", "प्रारंभिक सेटअप फिर से चलाएं…", "Ejecutar configuración inicial de nuevo…", "Relancer la configuration initiale…", "Ersteinrichtung erneut starten…", "Esegui di nuovo la configurazione iniziale…", "Запустить первоначальную настройку заново…", "Executar configuração inicial novamente…", "إعادة تشغيل الإعداد الأولي…", "初期設定をやり直す…", "重新运行初始设置…");
        add("Open Android TV Settings…", "Android TV सेटिंग्स खोलें…", "Abrir ajustes de Android TV…", "Ouvrir les paramètres Android TV…", "Android TV-Einstellungen öffnen…", "Apri impostazioni Android TV…", "Открыть настройки Android TV…", "Abrir definições da Android TV…", "فتح إعدادات Android TV…", "Android TVの設定を開く…", "打开 Android TV 系统设置…");
        add("Home screen", "होम स्क्रीन", "Pantalla de inicio", "Écran d'accueil", "Startbildschirm", "Schermata Home", "Главный экран", "Ecrã inicial", "الشاشة الرئيسية", "ホーム画面", "主屏幕");
        add("Developer", "डेवलपर", "Desarrollador", "Développeur", "Entwickler", "Sviluppatore", "Разработчик", "Programador", "المطور", "開発者", "开发者");
        add("Version", "संस्करण", "Versión", "Version", "Version", "Versione", "Версия", "Versão", "الإصدار", "バージョン", "版本");
        add("Settings kept", "सेटिंग्स सहेजी गईं", "Ajustes guardados", "Réglages conservés", "Einstellungen gespeichert", "Impostazioni salvate", "Хранение настроек", "Definições guardadas", "حفظ الإعدادات", "設定の保存先", "设置存储位置");
        add("ADB & PERMISSIONS", "ADB और अनुमतियां", "ADB Y PERMISOS", "ADB ET AUTORISATIONS", "ADB & BERECHTIGUNGEN", "ADB E AUTORIZZAZIONI", "ADB И РАЗРЕШЕНИЯ", "ADB E PERMISSÕES", "ADB والأذونات", "ADBと権限", "ADB 与权限");
        add("ADB connection status", "ADB कनेक्शन स्थिति", "Estado de conexión ADB", "État de la connexion ADB", "ADB-Verbindungsstatus", "Stato connessione ADB", "Статус подключения ADB", "Estado da ligação ADB", "حالة اتصال ADB", "ADB接続ステータス", "ADB 连接状态");
        add("Pair / Repair ADB connection", "ADB कनेक्शन पेयर / रिपेयर करें", "Vincular / Reparar conexión ADB", " Jumeler / Réparer la connexion ADB", "ADB-Verbindung koppeln / reparieren", "Associa / Ripara connessione ADB", "Подключить / Исправить ADB", "Emparelhar / Reparar ligação ADB", "إقران / إصلاح اتصال ADB", "ADB接続のペアリング / 修復", "配对 / 修复 ADB 连接");
        add("Check permissions", "अनुमतियां जांचें", "Comprobar permisos", "Vérifier les autorisations", "Berechtigungen prüfen", "Controlla autorizzazioni", "Проверить разрешения", "Verificar permissões", "التحقق من الأذونات", "権限を確認", "检查权限");
        add("Check Permissions", "अनुमतियां जांचें", "Comprobar permisos", "Vérifier les autorisations", "Berechtigungen prüfen", "Controlla autorizzazioni", "Проверить разрешения", "Verificar permissões", "التحقق من الأذونات", "権限を確認", "检查权限");
        add("Grant All via ADB", "ADB से सभी अनुमतियां दें", "Conceder todo por ADB", "Tout accorder via ADB", "Alle per ADB erteilen", "Concedi tutto via ADB", "Разрешить всё через ADB", "Conceder tudo via ADB", "منح الكل عبر ADB", "ADBですべて許可", "通过 ADB 授予全部权限");
        add("Repair", "रिपेयर करें", "Reparar", "Réparer", "Reparieren", "Ripara", "Исправить", "Reparar", "إصلاح", "修復", "修复");
        add("Pair now", "अभी पेयर करें", "Vincular ahora", "Jumeler maintenant", "Jetzt koppeln", "Associa ora", "Подключить", "Emparelhar agora", "إقران الآن", "今すぐペアリング", "立即配对");
        add("On this TV", "इस टीवी पर", "En este televisor", "Sur ce téléviseur", "Auf diesem TV", "Su questa TV", "На этом ТВ", "Nesta TV", "على هذا التلفزيون", "このテレビ内", "本机存储");
        add("Reset settings to defaults", "सेटिंग्स को डिफ़ॉल्ट पर रीसेट करें", "Restablecer ajustes predeterminados", "Réinitialiser les réglages par défaut", "Einstellungen auf Standard zurücksetzen", "Ripristina impostazioni predefinite", "Сбросить настройки по умолчанию", "Repor definições de origem", "إعادة ضبط الإعدادات إلى الافتراضية", "設定を初期状態にリセット", "恢复默认设置");
        add("Reset settings?", "सेटिंग्स रीसेट करें?", "¿Restablecer ajustes?", "Réinitialiser les réglages ?", "Einstellungen zurücksetzen?", "Ripristinare le impostazioni?", "Сбросить настройки?", "Repor definições?", "هل تريد إعادة ضبط الإعدادات؟", "設定をリセットしますか？", "要重置设置吗？");
        add("This resets look, scenes, clock and language settings back to defaults.", "यह रूप, दृश्य, घड़ी और भाषा सेटिंग्स को वापस डिफ़ॉल्ट पर रीसेट करता है।", "Esto restablecerá la apariencia, las escenas, el reloj y el idioma a los valores predeterminados.", "Cela réinitialise l'apparence, les scènes, l'horloge et la langue aux valeurs par défaut.", "Dadurch werden Aussehen, Szenen, Uhr und Sprache auf die Standardwerte zurückgesetzt.", "Questo ripristinerà aspetto, scene, orologio e lingua ai valori predefiniti.", "Вид, сцены, часы и язык будут сброшены к значениям по умолчанию.", "Isto repõe a aparência, cenas, relógio e idioma para os valores predefinidos.", "سيؤدي هذا إلى إعادة ضبط المظهر والمشاهد والساعة واللغة إلى الإعدادات الافتراضية.", "外観、シーン、時計、言語の設定をすべて初期状態に戻します。", "这将把外观、场景、时钟和语言设置恢复为默认值。");
        add("Settings reset to defaults.", "सेटिंग्स डिफ़ॉल्ट पर रीसेट हो गईं।", "Ajustes restablecidos a los valores predeterminados.", "Réglages réinitialisés par défaut.", "Einstellungen auf Standardwerte zurückgesetzt.", "Impostazioni ripristinate ai valori predefiniti.", "Настройки сброшены по умолчанию.", "Definições repostas para os valores predefinidos.", "تمت إعادة ضبط الإعدادات إلى الافتراضية.", "設定を初期状態にリセットしました。", "设置已恢复为默认值。");

        // Context Menu & Dock
        add("Open", "खोलें", "Abrir", "Ouvrir", "Öffnen", "Apri", "Открыть", "Abrir", "فتح", "開く", "打开");
        add("Rename", "नाम बदलें", "Renombrar", "Renommer", "Umbenennen", "Rinomina", "Переименовать", "Mudar nome", "إعادة تسمية", "名前を変更", "重命名");
        add("Rename App", "ऐप का नाम बदलें", "Renombrar aplicación", "Renommer l'application", "App umbenennen", "Rinomina applicazione", "Переименовать приложение", "Mudar nome da aplicação", "إعادة تسمية التطبيق", "アプリの名前を変更", "重命名应用");
        add("Move left in dock", "डॉक में बाएं ले जाएं", "Mover a la izquierda en el dock", "Déplacer à gauche dans le dock", "Im Dock nach links", "Sposta a sinistra nel dock", "Переместить влево в доке", "Mover para a esquerda no dock", "نقل لليسار في الشريط", "ドック内で左へ移動", "在程序坞中左移");
        add("Move right in dock", "डॉक में दाएं ले जाएं", "Mover a la derecha en el dock", "Déplacer à droite dans le dock", "Im Dock nach rechts", "Sposta a destra nel dock", "Переместить вправо в доке", "Mover para a direita no dock", "نقل لليمين في الشريط", "ドック内で右へ移動", "在程序坞中右移");
        add("Remove from dock", "डॉक से हटाएं", "Quitar del dock", "Retirer du dock", "Aus dem Dock entfernen", "Rimuovi dal dock", "Убрать из дока", "Remover do dock", "إزالة من الشريط", "ドックから削除", "从程序坞移除");
        add("Pin to Home", "होम पर पिन करें", "Fijar en inicio", "Épingler à l'accueil", "An Startseite anheften", "Fissa nella Home", "Закрепить на главном", "Afixar no início", "تثبيت في الشاشة الرئيسية", "ホームにピン留め", "固定到主页");
        add("Unpin from Home", "होम से अनपिन करें", "Desfijar de inicio", "Désépingler de l'accueil", "Von Startseite lösen", "Rimuovi dalla Home", "Открепить с главного", "Desafixar do início", "إلغاء التثبيت من الرئيسية", "ホームのピン留めを解除", "从主页取消固定");
        add("Move", "स्थानांतरित करें", "Mover", "Déplacer", "Verschieben", "Sposta", "Переместить", "Mover", "نقل", "移動", "移动");
        add("Hide from All Apps", "सभी ऐप्स से छिपाएं", "Ocultar de Todas las aplicaciones", "Masquer de toutes les applications", "Aus „Alle Apps“ ausblenden", "Nascondi da Tutte le app", "Скрыть из всех приложений", "Ocultar de Todas as aplicações", "إخفاء من كل التطبيقات", "すべてのアプリから隠す", "从所有应用中隐藏");
        add("App info", "ऐप जानकारी", "Información de la aplicación", "Infos sur l'application", "App-Info", "Info app", "О приложении", "Informações da aplicação", "معلومات التطبيق", "アプリ情報", "应用信息");
        add("Uninstall", "अनइंस्टॉल करें", "Desinstalar", "Désinstaller", "Deinstallieren", "Disinstalla", "Удалить", "Desinstalar", "إلغاء التثبيت", "アンインストール", "卸载");

        // Additional Settings Sections, Control Panel, Button Mapper, Advanced, Setup & CleanYourTV (Beta)
        add("LOCATION", "स्थान", "UBICACIÓN", "POSITION", "STANDORT", "POSIZIONE", "МЕСТОПОЛОЖЕНИЕ", "LOCALIZAÇÃO", "الموقع", "場所", "位置");
        add("WEATHER", "मौसम", "CLIMA", "MÉTÉO", "WETTER", "METEO", "ПОГОДА", "CLIMA", "الطقس", "天気", "天气");
        add("APPLICATIONS", "ऐप्स", "APLICACIONES", "APPLICATIONS", "ANWENDUNGEN", "APPLICAZIONI", "ПРИЛОЖЕНИЯ", "APLICAÇÕES", "التطبيقات", "アプリケーション", "应用程序");
        add("PROFILES", "प्रोफ़ाइल", "PERFILES", "PROFILS", "PROFILE", "PROFILI", "ПРОФИЛИ", "PERFIS", "الملفات الشخصية", "プロフィール", "用户配置");
        add("APPLICATION", "ऐप्लिकेशन", "APLICACIÓN", "APPLICATION", "ANWENDUNG", "APPLICAZIONE", "ПРИЛОЖЕНИЕ", "APLICAÇÃO", "التطبيق", "アプリケーション", "应用信息");
        add("RESET", "रीसेट", "RESTABLECER", "RÉINITIALISER", "ZURÜCKSETZEN", "RIPRISTINA", "СБРОС", "REPOR", "إعادة ضبط", "リセット", "重置");
        add("Add", "जोड़ें", "Añadir", "Ajouter", "Hinzufügen", "Aggiungi", "Добавить", "Adicionar", "إضافة", "追加", "添加");
        add("Name", "नाम", "Nombre", "Nom", "Name", "Nome", "Имя", "Nome", "الاسم", "名前", "名称");
        add("Add a person", "व्यक्ति जोड़ें", "Añadir persona", "Ajouter un profil", "Person hinzufügen", "Aggiungi persona", "Добавить профиль", "Adicionar pessoa", "إضافة شخص", "プロフィールを追加", "添加用户");
        add("UNIVERSAL SHORTCUT ACTIONS", "यूनिवर्सल शॉर्टकट क्रियाएं", "ACCIONES DE ACCESO DIRECTO", "ACTIONS DE RACCOURCI UNIVERSELLES", "UNIVERSELLE KURZBEFEHLE", "AZIONI SCORCIATOIA UNIVERSALI", "УНИВЕРСАЛЬНЫЕ ЯРЛЫКИ", "AÇÕES DE ATALHO UNIVERSAIS", "إجراءات الاختصار الشاملة", "ユニバーサルショートカット", "通用快捷操作");
        add("Inputs (Control Panel & Dock)", "इनपुट (कंट्रोल पैनल और डॉक)", "Entradas (Panel de control y Dock)", "Entrées (Centre de contrôle et Dock)", "Eingänge (Kontrollzentrum & Dock)", "Ingressi (Pannello di controllo e Dock)", "Входы (Панель управления и док)", "Entradas (Painel de controlo e Dock)", "المداخل (لوحة التحكم والشريط)", "入力切替（コントロールパネルとドック）", "输入源（控制中心与程序坞）");
        add("INPUTS APP ON DOCK", "डॉक पर इनपुट ऐप", "APP DE ENTRADAS EN EL DOCK", "APPLI ENTRÉES SUR LE DOCK", "EINGÄNGE-APP IM DOCK", "APP INGRESSI NEL DOCK", "ПРИЛОЖЕНИЕ ВХОДОВ В ДОКЕ", "APP ENTRADAS NO DOCK", "تطبيق المداخل على الشريط", "ドック上の入力アプリ", "程序坞上的输入源应用");
        add("Show Inputs app on Dock", "डॉक पर इनपुट ऐप दिखाएं", "Mostrar app de Entradas en el Dock", "Afficher l'appli Entrées sur le Dock", "Eingänge-App im Dock anzeigen", "Mostra app Ingressi nel Dock", "Показывать приложение «Входы» в доке", "Mostrar app Entradas no Dock", "إظهار تطبيق المداخل على الشريط", "ドックに入力アプリを表示", "在程序坞显示输入源应用");
        add("Reset Control Panel actions to default", "कंट्रोल पैनल क्रियाओं को डिफ़ॉल्ट पर रीसेट करें", "Restablecer acciones del Panel de control", "Réinitialiser les actions du Centre de contrôle", "Kontrollzentrum-Aktionen zurücksetzen", "Ripristina azioni Pannello di controllo", "Сбросить действия Панели управления", "Repor ações do Painel de controlo", "إعادة ضبط إجراءات لوحة التحكم", "コントロールパネルの動作を初期状態に戻す", "重置控制中心快捷操作");
        add("Automatic", "स्वचालित", "Automático", "Automatique", "Automatisch", "Automatico", "Автоматически", "Automático", "تلقائي", "自動", "自动");
        add("Each TV brand uses different package and activity names for Wi-Fi, Bluetooth, Sound, and Inputs. Choose an app and then pick the exact activity to launch from the Control Panel (and from the Inputs app on the dock).", "प्रत्येक टीवी ब्रांड वाई-फ़ाई, ब्लूटूथ, ध्वनि और इनपुट के लिए अलग-अलग पैकेज और गतिविधि नामों का उपयोग करता है। एक ऐप चुनें और फिर कंट्रोल पैनल से लॉन्च करने के लिए गतिविधि चुनें।", "Cada marca de TV usa diferentes paquetes y actividades para Wi-Fi, Bluetooth, Sonido y Entradas. Elige una aplicación y luego la actividad exacta para abrir desde el Panel de control.", "Chaque marque de téléviseur utilise des noms de paquets différents pour le Wi-Fi, le Bluetooth, le son et les entrées. Choisissez une application puis l'activité à ouvrir.", "Jede TV-Marke verwendet unterschiedliche Paketnamen für WLAN, Bluetooth, Ton und Eingänge. Wählen Sie eine App und dann die gewünschte Aktivität für das Kontrollzentrum.", "Ogni marca di TV utilizza pacchetti diversi per Wi-Fi, Bluetooth, Suono e Ingressi. Scegli un'app e poi l'attività esatta da avviare dal Pannello di controllo.", "Разные марки ТВ используют разные пакеты для Wi-Fi, Bluetooth, звука и входов. Выберите приложение и экран для запуска из Панели управления.", "Cada marca de TV usa pacotes diferentes para Wi-Fi, Bluetooth, Som e Entradas. Escolha uma aplicação e a atividade a abrir no Painel de controlo.", "تستخدم كل علامة تجارية للتلفزيون حزماً مختلفة لـ Wi-Fi والبلوتوث والصوت والمداخل. اختر تطبيقاً ثم النشاط المراد فتحه من لوحة التحكم.", "テレビメーカーによってWi-Fi、Bluetooth、サウンド、入力のパッケージ名が異なります。アプリと起動するアクティビティを選択してください。", "不同品牌的电视对 Wi-Fi、蓝牙、声音和输入源使用不同的包名与活动名。请选择应用并指定从控制中心启动的具体页面。");

        // Button Mapper extra options
        add("REMOTE FIXES", "रिमोट सुधार", "CORRECCIONES DEL MANDO", "CORRECTIFS TÉLÉCOMMANDE", "FERNBEDIENUNGS-FIXES", "CORREZIONI TELECOMANDO", "ИСПРАВЛЕНИЯ ПУЛЬТА", "CORREÇÕES DO COMANDO", "إصلاحات الريموت", "リモコンの修正", "遥控器修复");
        add("Long-press volume button fix", "वॉल्यूम बटन दबाए रखने का सुधार", "Corrección de mantener pulsado el volumen", "Correctif d'appui long sur le volume", "Korrektur für langes Drücken der Lautstärketaste", "Correzione pressione prolungata volume", "Исправление долгого нажатия громкости", "Correção de pressão longa no volume", "إصلاح الضغط المطول على زر الصوت", "音量ボタン長押しの修正", "长按音量键修复");
        add("Clear all button mappings", "सभी बटन मैपिंग साफ़ करें", "Borrar todas las asignaciones de botones", "Effacer toutes les attributions de boutons", "Alle Tastenbelegungen löschen", "Cancella tutte le mappature tasti", "Очистить все назначения кнопок", "Limpar todos os mapeamentos de botões", "مسح جميع تعيينات الأزرار", "すべてのボタン割り当てをクリア", "清除所有按键映射");
        add("Assign Single Tap (1x), Double Tap (2x) and Triple Tap (3x) actions for each remote button — such as opening the Control Panel overlay, the Recent Apps Switcher overlay, or any installed app.", "प्रत्येक रिमोट बटन के लिए सिंगल (1x), डबल (2x) और ट्रिपल टैप (3x) क्रियाएं सेट करें — जैसे कंट्रोल पैनल, हाल के ऐप्स स्विचर, या कोई भी इंस्टॉल किया गया ऐप खोलना।", "Asigna acciones de un toque (1x), doble (2x) y triple (3x) para cada botón del mando, como abrir el Panel de control, el Selector de apps recientes o cualquier aplicación.", "Attribuez des actions d'appui simple (1x), double (2x) et triple (3x) à chaque bouton pour ouvrir le Centre de contrôle, les Applications récentes ou n'importe quelle application.", "Weisen Sie jeder Taste Einfach- (1x), Doppel- (2x) und Dreifachdruck-Aktionen (3x) zu — z. B. Kontrollzentrum, App-Umschalter oder eine beliebige App.", "Assegna azioni per singolo (1x), doppio (2x) e triplo tocco (3x) a ogni tasto, come aprire il Pannello di controllo, le App recenti o qualsiasi app installata.", "Назначайте действия на одиночное (1x), двойное (2x) и тройное (3x) нажатие кнопок пульта — например, открытие Панели управления, Недавних приложений или любой программы.", "Atribua ações de toque único (1x), duplo (2x) e triplo (3x) para cada botão do comando — como abrir o Painel de controlo, Aplicações recentes ou qualquer aplicação.", "عيّن إجراءات الضغطة الواحدة (1x) والمزدوجة (2x) والثلاثية (3x) لكل زر في الريموت — مثل فتح لوحة التحكم أو مبدل التطبيقات الحديثة أو أي تطبيق.", "各リモコンボタンにシングル(1x)・ダブル(2x)・トリプルタップ(3x)の動作を割り当てて、コントロールパネルや最近のアプリ、任意のアプリを開けます。", "为每个遥控器按键分配单击 (1x)、双击 (2x) 和三击 (3x) 操作 — 例如打开控制中心、最近应用切换器或任意已安装应用。");

        // Advanced & Keep Alive
        add("KEEP ALIVE", "कीप अलाइव (स्वतः सुधार)", "MANTENER ACTIVO (KEEP ALIVE)", "MAINTIEN ACTIF (KEEP ALIVE)", "KEEP ALIVE (AUTO-REPARATUR)", "KEEP ALIVE (AUTORIPARAZIONE)", "АВТОВОССТАНОВЛЕНИЕ (KEEP ALIVE)", "MANTER ATIVO (KEEP ALIVE)", "البقاء نشطاً (إصلاح تلقائي)", "キープアライブ（自動修復）", "后台保活（自动修复权限）");
        add("Keep Alive (Auto-repair permissions)", "कीप अलाइव (अनुमतियां स्वतः सुधारें)", "Keep Alive (reparar permisos automáticamente)", "Keep Alive (réparer les autorisations auto.)", "Keep Alive (Berechtigungen automatisch reparieren)", "Keep Alive (ripara autorizzazioni automaticamente)", "Keep Alive (автовосстановление разрешений)", "Keep Alive (reparar permissões automaticamente)", "Keep Alive (إصلاح الأذونات تلقائياً)", "Keep Alive（権限の自動修復）", "Keep Alive（自动修复权限）");
        add("Paired & Ready", "पेयर और तैयार", "Vinculado y listo", "Jumelé et prêt", "Gekoppelt & bereit", "Associato e pronto", "Подключено и готово", "Emparelhado e pronto", "مقترن وجاهز", "ペアリング済み・準備完了", "已配对就绪");
        add("Port 5555 open (Tap to pair)", "पोर्ट 5555 खुला है (पेयर करने के लिए टैप करें)", "Puerto 5555 abierto (toca para vincular)", "Port 5555 ouvert (appuyez pour jumeler)", "Port 5555 offen (zum Koppeln tippen)", "Porta 5555 aperta (tocca per associare)", "Порт 5555 открыт (нажмите для привязки)", "Porta 5555 aberta (toque para emparelhar)", "المنفذ 5555 مفتوح (اضغط للإقران)", "ポート5555開放（タップしてペアリング）", "端口 5555 已开启（点击配对）");
        add("Not paired (ADB off)", "पेयर नहीं है (ADB बंद)", "No vinculado (ADB desactivado)", "Non jumelé (ADB désactivé)", "Nicht gekoppelt (ADB aus)", "Non associato (ADB disattivato)", "Не подключено (ADB выкл.)", "Não emparelhado (ADB desligado)", "غير مقترن (ADB متوقف)", "未ペアリング（ADBオフ）", "未配对（ADB 未开启）");
        add("active", "सक्रिय", "activos", "actifs", "aktiv", "attivi", "активно", "ativos", "نشط", "有効", "已启用");
        add("Some TVs disable Accessibility or other permissions automatically in the background. When Keep Alive is on, tvOS Launcher lightly checks permission status in the background without extra RAM or CPU load and automatically restores any disabled permissions via ADB.", "कुछ टीवी पृष्ठभूमि में एक्सेसिबिलिटी या अन्य अनुमतियों को स्वचालित रूप से अक्षम कर देते हैं। जब कीप अलाइव चालू होता है, तो tvOS Launcher बिना अतिरिक्त RAM या CPU लोड के ADB के माध्यम से बंद अनुमतियों को स्वचालित रूप से बहाल कर देता है।", "Algunos televisores desactivan la Accesibilidad u otros permisos en segundo plano. Cuando Keep Alive está activo, tvOS Launcher comprueba ligeramente los permisos sin carga extra de RAM o CPU y los restaura automáticamente por ADB.", "Certains téléviseurs désactivent l'accessibilité en arrière-plan. Lorsque Keep Alive est activé, tvOS Launcher vérifie légèrement l'état des autorisations sans surcharger la RAM ou le processeur et les restaure automatiquement via ADB.", "Einige Fernseher deaktivieren die Bedienungshilfen im Hintergrund von selbst. Wenn Keep Alive aktiviert ist, prüft tvOS Launcher ressourcenschonend im Hintergrund den Status und stellt deaktivierte Berechtigungen per ADB automatisch wieder her.", "Alcune TV disattivano l'Accessibilità in background. Quando Keep Alive è attivo, tvOS Launcher controlla lo stato delle autorizzazioni senza consumare RAM o CPU e le ripristina automaticamente via ADB.", "Некоторые ТВ сами отключают Спец. возможности в фоне. Когда функция Keep Alive включена, tvOS Launcher без нагрузки на память и процессор проверяет разрешения и автоматически восстанавливает их через ADB.", "Algumas TVs desativam a Acessibilidade automaticamente em segundo plano. Com o Keep Alive ativo, o tvOS Launcher verifica as permissões sem gastar RAM ou CPU e restaura-as automaticamente via ADB.", "تقوم بعض أجهزة التلفزيون بتعطيل تسهيلات الاستخدام تلقائياً في الخلفية. عند تفعيل Keep Alive، يتحقق المشغل بخفة دون استهلاك للذاكرة أو المعالج ويعيد تفعيل الأذونات تلقائياً عبر ADB.", "一部のテレビではバックグラウンドでユーザー補助などの権限が自動的に無効化されることがあります。Keep Aliveをオンにすると、RAMやCPUに負荷をかけずに権限を監視し、ADB経由で自動修復します。", "部分电视会在后台自动关闭无障碍等权限。开启 Keep Alive 后，tvOS Launcher 会在不增加内存或 CPU 负担的情况下轻量检测权限状态，并通过 ADB 自动恢复被关闭的权限。");

        // CleanYourTV (Beta)
        add("CLEANYOURTV (BETA)", "CLEANYOURTV (बीटा)", "CLEANYOURTV (BETA)", "CLEANYOURTV (BÊTA)", "CLEANYOURTV (BETA)", "CLEANYOURTV (BETA)", "CLEANYOURTV (БЕТА)", "CLEANYOURTV (BETA)", "CLEANYOURTV (تجريبي)", "CLEANYOURTV (ベータ)", "CLEANYOURTV（测试版）");
        add("Choose TV model", "टीवी मॉडल चुनें", "Elegir modelo de TV", "Choisir le modèle de TV", "TV-Modell auswählen", "Scegli modello TV", "Выбрать модель ТВ", "Escolher modelo de TV", "اختر طراز التلفزيون", "テレビのモデルを選択", "选择电视型号");
        add("Tap to detect / select…", "पहचानने / चुनने के लिए टैप करें…", "Toca para detectar / seleccionar…", "Appuyez pour détecter / choisir…", "Tippen zum Erkennen / Auswählen…", "Tocca per rilevare / selezionare…", "Нажмите для определения / выбора…", "Toque para detetar / selecionar…", "اضغط للاكتشاف / الاختيار…", "タップして検出 / 選択…", "点击自动识别 / 选择…");
        add("Debloat status", "डीब्लोट स्थिति", "Estado de limpieza (Debloat)", "État du nettoyage (Debloat)", "Debloat-Status", "Stato rimozione bloatware", "Статус очистки (Debloat)", "Estado de limpeza (Debloat)", "حالة إزالة البرامج الزائدة", "デブロート（不要アプリ削除）状態", "精简（Debloat）状态");
        add("Not done", "नहीं किया गया", "No realizado", "Non effectué", "Nicht durchgeführt", "Non eseguito", "Не выполнено", "Não concluído", "لم يتم", "未実行", "未执行");
        add("Not done (Select TV model first)", "नहीं किया गया (पहले टीवी मॉडल चुनें)", "No realizado (elige primero el modelo de TV)", "Non effectué (choisissez d'abord le modèle)", "Nicht durchgeführt (zuerst TV-Modell wählen)", "Non eseguito (scegli prima il modello TV)", "Не выполнено (сначала выберите модель ТВ)", "Não concluído (escolha o modelo primeiro)", "لم يتم (اختر طراز التلفزيون أولاً)", "未実行（先にテレビモデルを選択）", "未执行（请先选择电视型号）");
        add("Debloat TV…", "टीवी डीब्लोट शुरू करें…", "Limpiar TV (Debloat)…", "Nettoyer la TV (Debloat)…", "TV bereinigen (Debloat)…", "Pulisci TV (Debloat)…", "Очистить ТВ от лишнего ПО…", "Limpar TV (Debloat)…", "بدء تنظيف التلفزيون (Debloat)…", "テレビの不要アプリを無効化…", "开始精简电视应用…");
        add("Start ›", "शुरू करें ›", "Iniciar ›", "Démarrer ›", "Starten ›", "Avvia ›", "Начать ›", "Iniciar ›", "بدء ›", "開始 ›", "开始 ›");
        add("Disabled apps backup", "अक्षम ऐप्स बैकअप", "Copia de apps desactivadas", "Sauvegarde des applis désactivées", "Sicherung deaktivierter Apps", "Backup app disattivate", "Резервная копия отключенных приложений", "Cópia das apps desativadas", "نسخة التطبيقات المعطلة الاحتياطية", "無効化したアプリの記録", "已禁用应用备份记录");
        add("None saved", "कोई सहेजा नहीं गया", "Ninguna guardada", "Aucune enregistrée", "Keine gespeichert", "Nessuno salvato", "Нет записей", "Nenhum guardado", "لا يوجد محفوظات", "記録なし", "暂无记录");
        add("apps", "ऐप्स", "aplicaciones", "applications", "Apps", "app", "приложений", "aplicações", "تطبيقات", "アプリ", "个应用");
        add("packages", "पैकेज", "paquetes", "paquets", "Pakete", "pacchetti", "пакетов", "pacotes", "حزم", "パッケージ", "个包");
        add("Restore debloated apps…", "डीब्लोट किए गए ऐप्स बहाल करें…", "Restaurar aplicaciones desactivadas…", "Restaurer les applications désactivées…", "Deaktivierte Apps wiederherstellen…", "Ripristina app disattivate…", "Восстановить отключенные приложения…", "Restaurar aplicações desativadas…", "استعادة التطبيقات المعطلة…", "無効化したアプリを復元…", "恢复已精简的应用…");
        add("Restore ›", "बहाल करें ›", "Restaurar ›", "Restaurer ›", "Wiederherstellen ›", "Ripristina ›", "Восстановить ›", "Restaurar ›", "استعادة ›", "復元 ›", "恢复 ›");
        add("Ad-blocking Private DNS (AdGuard)", "विज्ञापन-अवरोधक प्राइवेट DNS (AdGuard)", "DNS privado con bloqueo de anuncios (AdGuard)", "DNS privé bloqueur de pubs (AdGuard)", "Werbeblocker Private-DNS (AdGuard)", "DNS privato blocca-pubblicità (AdGuard)", "Блокировка рекламы Private DNS (AdGuard)", "DNS privado com bloqueio de anúncios (AdGuard)", "نظام DNS خاص لحظر الإعلانات (AdGuard)", "広告ブロック Private DNS (AdGuard)", "去广告私人 DNS (AdGuard)");
        add("CleanYourTV (Beta) runs the CleanYourTV debloat profiles directly on your TV over Local ADB without needing a PC. Choose your TV model (or scan by installed system app packages), pick which bloatware and telemetry apps to disable, and restore them anytime with one tap.", "CleanYourTV (बीटा) बिना किसी पीसी के लोकल ADB के माध्यम से सीधे आपके टीवी पर CleanYourTV डीब्लोट प्रोफ़ाइल चलाता है। अपना टीवी मॉडल चुनें (या इंस्टॉल किए गए सिस्टम ऐप पैकेज द्वारा स्कैन करें), चुनें कि कौन से ब्लोटवेयर और टेलीमेट्री ऐप्स को अक्षम करना है, और उन्हें कभी भी एक टैप से बहाल करें।", "CleanYourTV (Beta) ejecuta los perfiles de limpieza de CleanYourTV directamente en tu televisor por ADB local sin necesitar un PC. Elige tu modelo de TV (o escanea por paquetes instalados), selecciona qué bloatware y telemetría desactivar y restáuralos cuando quieras con un toque.", "CleanYourTV (Bêta) exécute les profils CleanYourTV directement sur votre téléviseur via ADB local sans ordinateur. Choisissez votre modèle de TV (ou scannez les paquets système), sélectionnez les applications indésirables à désactiver et restaurez-les à tout moment en un clic.", "CleanYourTV (Beta) führt die CleanYourTV-Profile direkt auf Ihrem Fernseher über lokales ADB ohne PC aus. Wählen Sie Ihr TV-Modell (oder scannen Sie nach Systempaketen), wählen Sie aus, welche Bloatware- und Telemetrie-Apps deaktiviert werden sollen, und stellen Sie sie jederzeit wieder her.", "CleanYourTV (Beta) esegue i profili di pulizia CleanYourTV direttamente sulla TV tramite ADB locale senza bisogno di un PC. Scegli il modello della TV (o scansiona i pacchetti di sistema), seleziona quali bloatware disattivare e ripristinali in qualsiasi momento con un tocco.", "CleanYourTV (Бета) запускает профили очистки CleanYourTV прямо на вашем ТВ через локальный ADB без ПК. Выберите модель ТВ (или просканируйте системные пакеты), отметьте ненужные приложения и телеметрию для отключения и восстанавливайте их в одно касание.", "O CleanYourTV (Beta) executa os perfis de limpeza do CleanYourTV diretamente na sua TV via ADB local sem precisar de PC. Escolha o modelo da TV (ou procure pelos pacotes instalados), selecione as aplicações a desativar e restaure-as a qualquer momento com um toque.", "يشغّل CleanYourTV (تجريبي) ملفات تنظيف التلفزيون مباشرة عبر ADB المحلي دون الحاجة إلى كمبيوتر. اختر طراز التلفزيون (أو افحص حزم النظام المثبتة)، وحدد تطبيقات التتبع والبرامج الزائدة لتعطيلها، واستعدها في أي وقت بضغطة واحدة.", "CleanYourTV (ベータ) はPC不要でローカルADB経由で直接テレビの不要アプリ・テレメトリ無効化を実行します。テレビのモデルを選択（またはシステムアプリから一致検索）し、無効化するアプリを選んで、いつでもワンタップで復元できます。", "CleanYourTV（测试版）无需电脑即可通过本地 ADB 直接在电视上运行 CleanYourTV 精简配置。选择电视型号（或通过扫描已安装系统包名匹配），勾选要禁用的预装和遥测应用，并可随时一键恢复。");
        add("🔍 Search…", "🔍 खोजें…", "🔍 Buscar…", "🔍 Rechercher…", "🔍 Suchen…", "🔍 Cerca…", "🔍 Поиск…", "🔍 Pesquisar…", "🔍 بحث…", "🔍 検索…", "🔍 搜索…");
        add("Scan Apps (ADB)", "ऐप्स स्कैन करें (ADB)", "Escanear apps (ADB)", "Scanner les applis (ADB)", "Apps scannen (ADB)", "Scansiona app (ADB)", "Сканировать приложения (ADB)", "Procurar apps (ADB)", "فحص التطبيقات (ADB)", "アプリをスキャン (ADB)", "扫描系统应用 (ADB)");
        add("All 341 Models", "सभी 341 मॉडल", "Los 341 modelos", "Les 341 modèles", "Alle 341 Modelle", "Tutti i 341 modelli", "Все 341 модели", "Todos os 341 modelos", "جميع الطرازات الـ 341", "全341モデル", "全部 341 个型号");
        add("Disable Selected", "चयनित को अक्षम करें", "Desactivar seleccionados", "Désactiver la sélection", "Ausgewählte deaktivieren", "Disattiva selezionate", "Отключить выбранные", "Desativar selecionadas", "تعطيل المحدد", "選択した項目を無効化", "禁用所选应用");
        add("Select All / None", "सभी चुनें / हटाएं", "Seleccionar todo / nada", "Tout sélectionner / désélectionner", "Alle / Keine auswählen", "Seleziona tutto / nessuno", "Выбрать все / снять", "Selecionar tudo / nada", "تحديد الكل / إلغاء الكل", "すべて選択 / 解除", "全选 / 取消全选");
        add("Restore All", "सभी बहाल करें", "Restaurar todo", "Tout restaurer", "Alle wiederherstellen", "Ripristina tutto", "Восстановить все", "Restaurar tudo", "استعادة الكل", "すべて復元", "全部恢复");
        add(" [Optional]", " [वैकल्पिक]", " [Opcional]", " [Optionnel]", " [Optional]", " [Opzionale]", " [Необязательно]", " [Opcional]", " [اختياري]", " [任意]", " [可选]");
        add(" [Already disabled]", " [पहले से अक्षम]", " [Ya desactivado]", " [Déjà désactivé]", " [Bereits deaktiviert]", " [Già disattivata]", " [Уже отключено]", " [Já desativada]", " [معطل بالفعل]", " [無効化済み]", " [已禁用]");
        add(" [Enabled]", " [सक्षम]", " [Activado]", " [Activé]", " [Aktiviert]", " [Attiva]", " [Включено]", " [Ativa]", " [مفعل]", " [有効]", " [已启用]");
        add(" [Disabled]", " [अक्षम]", " [Desactivado]", " [Désactivé]", " [Deaktiviert]", " [Disattivata]", " [Отключено]", " [Desativada]", " [معطل]", " [無効]", " [已禁用]");
        add("✅ Re-added Wi-Fi (Continue)", "✅ वाई-फ़ाई फिर से जोड़ा (जारी रखें)", "✅ Wi-Fi reconectado (Continuar)", "✅ Wi-Fi rajouté (Continuer)", "✅ WLAN neu verbunden (Weiter)", "✅ Wi-Fi riaggiunto (Continua)", "✅ Wi-Fi переподключен (Далее)", "✅ Wi-Fi readicionado (Continuar)", "✅ تمت إعادة إضافة Wi-Fi (متابعة)", "✅ Wi-Fi再設定済み（続行）", "✅ 已重新连接 Wi-Fi（继续）");
        add("📶 Wi-Fi Settings…", "📶 वाई-फ़ाई सेटिंग्स…", "📶 Ajustes de Wi-Fi…", "📶 Paramètres Wi-Fi…", "📶 WLAN-Einstellungen…", "📶 Impostazioni Wi-Fi…", "📶 Настройки Wi-Fi…", "📶 Definições de Wi-Fi…", "📶 إعدادات Wi-Fi…", "📶 Wi-Fi設定…", "📶 Wi-Fi 设置…");
        add("🛡️ Skip setupwraith", "🛡️ setupwraith छोड़ें", "🛡️ Omitir setupwraith", "🛡️ Ignorer setupwraith", "🛡️ setupwraith überspringen", "🛡️ Salta setupwraith", "🛡️ Пропустить setupwraith", "🛡️ Ignorar setupwraith", "🛡️ تخطي setupwraith", "🛡️ setupwraithをスキップ", "🛡️ 跳过 setupwraith");
        add("Google TV Wi-Fi Warning", "Google TV वाई-फ़ाई चेतावनी", "Advertencia de Wi-Fi en Google TV", "Avertissement Wi-Fi Google TV", "Google TV WLAN-Warnung", "Avviso Wi-Fi Google TV", "Предупреждение Wi-Fi для Google TV", "Aviso de Wi-Fi do Google TV", "تحذير Wi-Fi لأجهزة Google TV", "Google TVのWi-Fiに関する注意", "Google TV Wi-Fi 警告");
        add("Debloat Complete", "डीब्लोट पूर्ण हुआ", "Limpieza completada", "Nettoyage terminé", "Debloat abgeschlossen", "Pulizia completata", "Очистка завершена", "Limpeza concluída", "اكتمل التنظيف", "無効化が完了しました", "精简完成");
        add("Restore Complete", "बहाली पूर्ण हुई", "Restauración completada", "Restauration terminée", "Wiederherstellung abgeschlossen", "Ripristino completato", "Восстановление завершено", "Restauração concluída", "اكتملت الاستعادة", "復元が完了しました", "恢复完成");
        add("Welcome", "स्वागत है", "Bienvenido", "Bienvenue", "Willkommen", "Benvenuto", "Добро пожаловать", "Bem-vindo", "مرحباً بك", "ようこそ", "欢迎");
        add("Select your language to begin", "शुरू करने के लिए अपनी भाषा चुनें", "Selecciona tu idioma para comenzar", "Sélectionnez votre langue pour commencer", "Wählen Sie Ihre Sprache, um zu beginnen", "Seleziona la tua lingua per iniziare", "Выберите язык для начала работы", "Selecione o seu idioma para começar", "اختر لغتك للبدء", "開始するには言語を選択してください", "请选择您的语言以开始使用");
        add("Automatic Setup", "स्वचालित सेटअप", "Configuración automática", "Configuration automatique", "Automatische Einrichtung", "Configurazione automatica", "Автоматическая настройка", "Configuração automática", "الإعداد التلقائي", "自動セットアップ", "自动设置");
        add("Choose how to grant permissions", "अनुमतियां देने का तरीका चुनें", "Elige cómo conceder los permisos", "Choisissez comment accorder les autorisations", "Wählen Sie, wie Berechtigungen erteilt werden sollen", "Scegli come concedere le autorizzazioni", "Выберите способ предоставления разрешений", "Escolha como conceder as permissões", "اختر كيفية منح الأذونات", "権限の付与方法を選択", "选择授予权限的方式");
        add("Manual Permissions", "मैन्युअल अनुमतियां", "Permisos manuales", "Autorisations manuelles", "Manuelle Berechtigungen", "Autorizzazioni manuali", "Ручные разрешения", "Permissões manuais", "الأذونات اليدوية", "手動での権限設定", "手动权限设置");
        add("Setup Complete", "सेटअप पूरा हुआ", "Configuración completa", "Configuration terminée", "Einrichtung abgeschlossen", "Configurazione completata", "Настройка завершена", "Configuração concluída", "اكتمل الإعداد", "セットアップ完了", "设置完成");
        add("Other apps", "अन्य ऐप्स", "Otras aplicaciones", "Autres applis", "Andere Apps", "Altre app", "Другие приложения", "Outras aplicações", "تطبيقات أخرى", "その他のアプリ", "其他应用");
        add("Other Apps", "अन्य ऐप्स", "Otras aplicaciones", "Autres applis", "Andere Apps", "Altre app", "Другие приложения", "Outras aplicações", "تطبيقات أخرى", "その他のアプリ", "其他应用");
        add("Move to Other Apps", "अन्य ऐप्स में ले जाएं", "Mover a Otras aplicaciones", "Déplacer vers Autres applis", "Zu Andere Apps verschieben", "Sposta in Altre app", "Переместить в Другие приложения", "Mover para Outras aplicações", "نقل إلى تطبيقات أخرى", "その他のアプリへ移動", "移至其他应用");
        add("Hidden apps", "छुपे हुए ऐप्स", "Aplicaciones ocultas", "Applications masquées", "Ausgeblendete Apps", "App nascoste", "Скрытые приложения", "Aplicações ocultas", "التطبيقات المخفية", "非表示のアプリ", "隐藏的应用");
        add("Back to Apps", "ऐप्स पर वापस जाएं", "Volver a Aplicaciones", "Retour aux Applications", "Zurück zu Apps", "Torna alle App", "Назад к Приложениям", "Voltar a Aplicações", "العودة إلى التطبيقات", "アプリ設定に戻る", "返回应用设置");
        add("OK to open  ·  Hold OK to restore  ·  Back to go back", "खोलने के लिए OK  ·  बहाल करने के लिए OK दबाए रखें  ·  वापस जाने के लिए Back", "OK para abrir  ·  Mantén OK para restaurar  ·  Atrás para volver", "OK pour ouvrir  ·  Maintenir OK pour restaurer  ·  Retour pour revenir", "OK zum Öffnen  ·  OK halten zum Wiederherstellen  ·  Zurück", "OK per aprire  ·  Tieni premuto OK per ripristinare  ·  Indietro", "OK — открыть  ·  Удерживайте OK — восстановить  ·  Назад — вернуться", "OK para abrir  ·  Manter OK para restaurar  ·  Voltar para sair", "موافق للفتح  ·  مطولاً للاستعادة  ·  رجوع للعودة", "OKで開く  ·  OK長押しで復元  ·  戻るで前の画面へ", "按 OK 打开  ·  长按 OK 恢复显示  ·  按返回退出");
        add("OK to open  ·  Hold OK to restore", "खोलने के लिए OK  ·  बहाल करने के लिए OK दबाए रखें", "OK para abrir  ·  Mantén OK para restaurar", "OK pour ouvrir  ·  Maintenir OK pour restaurer", "OK zum Öffnen  ·  OK halten zum Wiederherstellen", "OK per aprire  ·  Tieni premuto OK per ripristinare", "OK — открыть  ·  Удерживайте OK — восстановить", "OK para abrir  ·  Manter OK para restaurar", "موافق للفتح  ·  مطولاً للاستعادة", "OKで開く  ·  OK長押しで復元", "按 OK 打开  ·  长按 OK 恢复显示");
        add("No hidden apps. Hold OK on any app on the home screen dock and choose Hide.", "कोई छुपा हुआ ऐप नहीं है। होम स्क्रीन डॉक पर किसी भी ऐप पर OK दबाए रखें और छुपाएं चुनें।", "No hay aplicaciones ocultas. Mantén pulsado OK en cualquier app del dock y elige Ocultar.", "Aucune application masquée. Maintenez OK sur une application du dock et choisissez Masquer.", "Keine ausgeblendeten Apps. Halten Sie OK auf einer App im Dock gedrückt und wählen Sie Ausblenden.", "Nessuna app nascosta. Tieni premuto OK su un'app nel dock e scegli Nascondi.", "Нет скрытых приложений. Удерживайте OK на любом приложении в доке и выберите «Скрыть».", "Nenhuma aplicação oculta. Mantenha OK premido numa app na dock e escolha Ocultar.", "لا توجد تطبيقات مخفية. اضغط مطولاً على موافق فوق أي تطبيق في الشريط واختر إخفاء.", "非表示のアプリはありません。ホーム画面のドックでアプリをOK長押しして「非表示」を選択してください。", "暂无隐藏的应用。在主屏幕程序坞任意应用上长按 OK 并选择“隐藏”。");

        // Custom Wallpaper, Clock Position, App Icon Changer & Updates
        add("Custom", "कस्टम", "Personalizado", "Personnalisé", "Benutzerdefiniert", "Personalizzato", "Свой", "Personalizado", "مخصص", "カスタム", "自定义");
        add("Clock position", "घड़ी की स्थिति", "Posición del reloj", "Position de l'horloge", "Uhrposition", "Posizione orologio", "Позиция часов", "Posição do relógio", "موضع الساعة", "時計の位置", "时钟位置");
        add("Left", "बाएं", "Izquierda", "Gauche", "Links", "Sinistra", "Слеवा", "Esquerda", "يسار", "左", "居左");
        add("Center", "बीच में", "Centro", "Centre", "Mitte", "Centro", "По центру", "Centro", "وسط", "中央", "居中");
        add("Right", "दाएं", "Derecha", "Droite", "Rechts", "Destra", "Справа", "Direita", "يمين", "右", "居右");
        add("Clock font", "घड़ी का फ़ॉन्ट", "Fuente del reloj", "Police de l'horloge", "Uhr-Schriftart", "Carattere orologio", "Шрифт часов", "Tipo de letra do relógio", "خط الساعة", "時計のフォント", "时钟字体");
        add("CLASSIC BACKGROUND", "क्लासिक पृष्ठभूमि", "FONDO CLÁSICO", "ARRIÈRE-PLAN CLASSIQUE", "KLASSISCHER HINTERGRUND", "SFONDO CLASSICO", "КЛАССИЧЕСКИЙ ФОН", "FUNDO CLÁSSICO", "الخلفية الكلاسيكية", "クラシック背景", "经典背景");
        add("Ambient colour themes…", "परिवेश रंग थीम…", "Temas de color ambiental…", "Thèmes de couleur d'ambiance…", "Umgebungsfarbthemen…", "Temi colore ambientale…", "Цветовые темы…", "Temas de cor ambiente…", "سمات الألوان المحيطة…", "アンビエントカラーテーマ…", "氛围色彩主题…");
        add("Classic background", "क्लासिक पृष्ठभूमि", "Fondo clásico", "Arrière-plan classique", "Klassischer Hintergrund", "Sfondo classico", "Классический фон", "Fundo clássico", "الخلفية الكلاسيكية", "クラシック背景", "经典背景");
        add("Change app icon", "ऐप आइकन बदलें", "Cambiar icono de la app", "Changer l'icône de l'application", "App-Symbol ändern", "Cambia icona app", "Изменить значок приложения", "Alterar ícone da aplicação", "تغيير أيقونة التطبيق", "アプリアイコンを変更", "更改应用图标");
        add("Reset to default icon", "डिफ़ॉल्ट आइकन पर रीसेट करें", "Restablecer icono predeterminado", "Réinitialiser l'icône par défaut", "Auf Standardsymbol zurücksetzen", "Ripristina icona predefinita", "Сбросить значок по умолчанию", "Repor ícone predefinido", "إعادة تعيين الأيقونة الافتراضية", "デフォルトアイコンに戻す", "恢复默认图标");
        add("Choose image…", "चित्र चुनें…", "Elegir imagen…", "Choisir une image…", "Bild auswählen…", "Scegli immagine…", "Выбрать изображение…", "Escolher imagem…", "اختر صورة…", "画像を選択…", "选择图片…");
        add("Choose new image…", "नया चित्र चुनें…", "Elegir nueva imagen…", "Choisir une nouvelle image…", "Neues Bild auswählen…", "Scegli nuova immagine…", "Выбрать новое изображение…", "Escolher nova imagem…", "اختر صورة جديدة…", "新しい画像を選択…", "选择新图片…");
        add("Turn on Custom scene", "कस्टम दृश्य चालू करें", "Activar escena personalizada", "Activer la scène personnalisée", "Benutzerdefinierte Szene einschalten", "Attiva scena personalizzata", "Включить свою сцену", "Ativar cena personalizada", "تشغيل المشهد المخصص", "カスタムシーンをオンにする", "开启自定义场景");
        add("Turn off Custom scene", "कस्टम दृश्य बंद करें", "Desactivar escena personalizada", "Désactiver la scène personnalisée", "Benutzerdefinierte Szene ausschalten", "Disattiva scena personalizzata", "Выключить свою сцену", "Desativar cena personalizada", "إيقاف المشهد المخصص", "カスタムシーンをオフにする", "关闭自定义场景");
        add("Use custom background", "कस्टम पृष्ठभूमि का उपयोग करें", "Usar fondo personalizado", "Utiliser l'arrière-plan personnalisé", "Benutzerdefinierten Hintergrund verwenden", "Usa sfondo personalizzato", "Использовать свой фон", "Usar fundo personalizado", "استخدام الخلفية المخصصة", "カスタム背景を使用", "使用自定义背景");
        add("Updates", "अपडेट", "Actualizaciones", "Mises à jour", "Updates", "Aggiornamenti", "Обновления", "Atualizações", "التحديثات", "アップデート", "更新");
        add("UPDATES", "अपडेट", "ACTUALIZACIONES", "MISES À JOUR", "UPDATES", "AGGIORNAMENTI", "ОБНОВЛЕНИЯ", "ATUALIZAÇÕES", "التحديثات", "アップデート", "更新");
        add("Automatic updates", "स्वचालित अपडेट", "Actualizaciones automáticas", "Mises à jour automatiques", "Automatische Updates", "Aggiornamenti automatici", "Автоматические обновления", "Atualizações automáticas", "التحديثات التلقائية", "自動アップデート", "自动更新");
        add("Check for updates", "अपडेट की जांच करें", "Buscar actualizaciones", "Rechercher des mises à jour", "Nach Updates suchen", "Controlla aggiornamenti", "Проверить обновления", "Procurar atualizações", "التحقق من وجود تحديثات", "アップデートを確認", "检查更新");
        add("Check now ›", "अभी जांचें ›", "Comprobar ahora ›", "Vérifier maintenant ›", "Jetzt prüfen ›", "Controlla ora ›", "Проверить сейчас ›", "Verificar agora ›", "تحقق الآن ›", "今すぐ確認 ›", "立即检查 ›");
        add("VERSION INFO", "संस्करण जानकारी", "INFORMACIÓN DE VERSIÓN", "INFORMATIONS DE VERSION", "VERSIONSINFO", "INFO VERSIONE", "ИНФОРМАЦИЯ О ВЕРСИИ", "INFORMAÇÃO DA VERSÃO", "معلومات الإصدار", "バージョン情報", "版本信息");
        add("Current version", "वर्तमान संस्करण", "Versión actual", "Version actuelle", "Aktuelle Version", "Versione attuale", "Текущая версия", "Versão atual", "الإصدار الحالي", "現在のバージョン", "当前版本");
        add("GitHub release latest version", "GitHub नवीनतम संस्करण", "Última versión en GitHub", "Dernière version GitHub", "Neueste GitHub-Version", "Ultima versione GitHub", "Последняя версия на GitHub", "Última versão no GitHub", "أحدث إصدار على GitHub", "GitHub最新リリース", "GitHub 最新版本");
        add("Checking…", "जांच हो रही है…", "Comprobando…", "Vérification…", "Prüfe…", "Controllo in corso…", "Проверка…", "A verificar…", "جارٍ التحقق…", "確認中…", "正在检查…");
        add("Update Available", "अपडेट उपलब्ध है", "Actualización disponible", "Mise à jour disponible", "Update verfügbar", "Aggiornamento disponibile", "Доступно обновление", "Atualização disponível", "يتوفر تحديث", "アップデートがあります", "发现新版本");
        add("Download & Install", "डाउनलोड और इंस्टॉल करें", "Descargar e instalar", "Télécharger et installer", "Herunterladen & installieren", "Scarica e installa", "Скачать и установить", "Transferir e instalar", "تنزيل وتثبيت", "ダウンロードしてインストール", "下载并安装");
        add("Up to Date", "अप टू डेट", "Actualizado", "À jour", "Auf dem neuesten Stand", "Aggiornato", "Установлена последняя версия", "Atualizado", "محدّث لأحدث إصدار", "最新の状態です", "已是最新版本");
    }

    private static void sv(String en, String svVal) {
        LANG_MAPS.get(LANG_SV).put(en, svVal);
    }

    private static void initSwedish() {
        // Greetings
        sv("Good morning", "God morgon");
        sv("Good afternoon", "God eftermiddag");
        sv("Good evening", "God kväll");
        sv("Good night", "God natt");
        sv("Welcome", "Välkommen");

        // Weather conditions
        sv("Clear", "Klart");
        sv("Sunny", "Soligt");
        sv("Mostly Sunny", "Mestadels soligt");
        sv("Mostly Clear", "Mestadels klart");
        sv("Partly Cloudy", "Halvklart");
        sv("Cloudy", "Molnigt");
        sv("Foggy", "Dimmigt");
        sv("Drizzle", "Duggregn");
        sv("Rain", "Regn");
        sv("Heavy Rain", "Kraftigt regn");
        sv("Sleet", "Snöblandat regn");
        sv("Snow", "Snö");
        sv("Heavy Snow", "Kraftigt snöfall");
        sv("Showers", "Regnskurar");
        sv("Thunderstorms", "Åska");

        // Sidebar & Main Sections
        sv("Settings", "Inställningar");
        sv("Home settings", "Heminställningar");
        sv("Home Settings", "Heminställningar");
        sv("Language", "Språk");
        sv("Location", "Plats");
        sv("Weather", "Väder");
        sv("Weather and location", "Väder och plats");
        sv("Look and scenes", "Utseende och scener");
        sv("Appearance and scenes", "Utseende och scener");
        sv("Apps", "Appar");
        sv("People", "Personer");
        sv("Profiles", "Profiler");
        sv("Control Panel", "Kontrollpanel");
        sv("Control Panel (Overlay)", "Kontrollpanel (överlägg)");
        sv("Button Mapper", "Knappmappare");
        sv("Button mapper", "Knappmappare");
        sv("Quick buttons", "Snabbknappar");
        sv("Home & System", "Hem och system");
        sv("Advanced", "Avancerat");
        sv("Updates", "Uppdateringar");
        sv("UPDATES", "UPPDATERINGAR");
        sv("About", "Om");

        // Look & Scenes & Custom Wallpaper & Clock Position
        sv("LOOK", "UTSEENDE");
        sv("Night scenes", "Nattscener");
        sv("A big glass clock, scenes taking turns", "En stor glasklocka med scener som turas om");
        sv("Classic", "Klassisk");
        sv("One photo and a grid of app banners", "Ett foto och ett rutnät med appbanderoller");
        sv("SCENES", "SCENER");
        sv("Night", "Natt");
        sv("Golden hour", "Gyllene timmen");
        sv("Waterline", "Vattenlinje");
        sv("Fog", "Dimma");
        sv("Rainforest dawn", "Regnskogsgryning");
        sv("Custom", "Anpassad");
        sv("Clock position", "Klockposition");
        sv("Left", "Vänster");
        sv("Center", "Mitten");
        sv("Right", "Höger");
        sv("Each scene stays", "Varje scen visas i");
        sv(" seconds", " sekunder");
        sv("Clock", "Klocka");
        sv("Clock font", "Klocktypsnitt");
        sv("Clock style", "Klockstil");
        sv("Rounded", "Rundad");
        sv("Modern", "Modern");
        sv("Serif", "Serif");
        sv("Stencil", "Schablon");
        sv("12 hour", "12 timmar");
        sv("24 hour", "24 timmar");
        sv("One scene has to stay on.", "Minst en scen måste vara aktiverad.");
        sv("CLASSIC BACKGROUND", "KLASSISK BAKGRUND");
        sv("Classic background", "Klassisk bakgrund");
        sv("Ambient colour themes…", "Stämningsfulla färgteman…");
        sv("Turn on Custom scene", "Slå på anpassad scen");
        sv("Turn off Custom scene", "Stäng av anpassad scen");
        sv("Use custom background", "Använd anpassad bakgrund");
        sv("Choose image…", "Välj bild…");
        sv("Choose new image…", "Välj ny bild…");
        sv("Choose custom scene image", "Välj bild för anpassad scen");
        sv("Choose custom background image", "Välj bild för anpassad bakgrund");
        sv("Custom scene wallpaper updated.", "Anpassad scenbakgrund uppdaterad.");
        sv("Custom background updated.", "Anpassad bakgrund uppdaterad.");
        sv("Could not load selected image.", "Kunde inte läsa in den valda bilden.");

        // App Icon Changer & Dock Context Menu
        sv("Change app icon", "Ändra appikon");
        sv("Choose icon for ", "Välj ikon för ");
        sv("Choose new icon file…", "Välj ny ikonfil…");
        sv("Reset to default icon", "Återställ till standardikon");
        sv("App icon updated", "Appikon uppdaterad");
        sv("Default icon restored", "Standardikon återställd");
        sv("Could not decode image file", "Kunde inte läsa bildfilen");
        sv("System file picker…", "Systemets filväljare…");
        sv("Grant storage permission", "Ge lagringsbehörighet");
        sv("Storage folders", "Lagringsmappar");
        sv("⬅  Back to storage folders", "⬅  Tillbaka till lagringsmappar");
        sv("⬅  .. (Parent folder)", "⬅  .. (Överordnad mapp)");
        sv("Open", "Öppna");
        sv("Rename", "Byt namn");
        sv("Rename App", "Byt namn på app");
        sv("Move left in dock", "Flytta åt vänster i dockan");
        sv("Move right in dock", "Flytta åt höger i dockan");
        sv("Remove from dock", "Ta bort från dockan");
        sv("Pin to Home", "Fäst på hemskärmen");
        sv("Unpin from Home", "Lossa från hemskärmen");
        sv("Move", "Flytta");
        sv("Hide", "Dölj");
        sv("Hide from All Apps", "Dölj från Alla appar");
        sv("Move to Other Apps", "Flytta till Andra appar");
        sv("App info", "Appinfo");
        sv("Uninstall", "Avinstallera");

        // Updates section
        sv("Automatic updates", "Automatiska uppdateringar");
        sv("Automatic updates enabled.", "Automatiska uppdateringar aktiverade.");
        sv("Automatic updates disabled.", "Automatiska uppdateringar inaktiverade.");
        sv("Check for updates", "Sök efter uppdateringar");
        sv("Check now ›", "Sök nu ›");
        sv("VERSION INFO", "VERSIONSINFORMATION");
        sv("Current version", "Nuvarande version");
        sv("GitHub release latest version", "Senaste GitHub-version");
        sv("Checking…", "Söker…");
        sv("Checking for Updates", "Söker efter uppdateringar");
        sv("Checking GitHub releases for the latest version…", "Söker på GitHub efter den senaste versionen…");
        sv("Tap Check for updates", "Tryck på Sök efter uppdateringar");
        sv("Update Check Failed", "Uppdateringssökning misslyckades");
        sv("Could not reach GitHub Releases. Please check your internet connection.", "Kunde inte nå GitHub Releases. Kontrollera din internetanslutning.");
        sv("Update Available", "Uppdatering tillgänglig");
        sv("Current version: v", "Nuvarande version: v");
        sv("Latest GitHub version: v", "Senaste GitHub-version: v");
        sv("Download & Install", "Ladda ner och installera");
        sv("Up to Date", "Uppdaterad");
        sv("You are running the latest version.", "Du använder den senaste versionen.");
        sv("Updating tvOS Launcher", "Uppdaterar tvOS Launcher");
        sv("Downloading latest APK from GitHub…", "Laddar ner senaste APK från GitHub…");
        sv("Installing update…", "Installerar uppdatering…");
        sv("Update Failed", "Uppdatering misslyckades");
        sv("Checks GitHub Releases (techgenius1/tvOS-Launcher) for the latest APK and installs updates directly on your TV.", "Söker på GitHub Releases (techgenius1/tvOS-Launcher) efter den senaste APK-filen och installerar uppdateringar direkt på din TV.");

        // Home screen & Quick buttons & Setup
        sv("Keep watching", "Fortsätt titta");
        sv("Continue Watching", "Fortsätt titta");
        sv("More apps", "Fler appar");
        sv("More Apps", "Fler appar");
        sv("Fewer apps", "Färre appar");
        sv("Other apps", "Andra appar");
        sv("Other Apps", "Andra appar");
        sv("Hidden apps", "Dolda appar");
        sv("Back to Apps", "Tillbaka till Appar");
        sv("Inputs", "Ingångar");
        sv("Presentation", "Presentation");
        sv("TV settings", "TV-inställningar");
        sv("TV Settings", "TV-inställningar");
        sv("Language, weather, scenes, apps and people", "Språk, väder, scener, appar och personer");
        sv("Wi-Fi, picture, sound and the rest of the TV", "Wi-Fi, bild, ljud och resten av TV:n");
        sv("Sleep timer", "Insomningstimer");
        sv("Sleep Timer", "Insomningstimer");
        sv("Turn the TV off after", "Stäng av TV:n efter");
        sv("Off", "Av");
        sv("On", "På");
        sv("Search", "Sök");
        sv("Refresh", "Uppdatera");
        sv("Cancel", "Avbryt");
        sv("Next", "Nästa");
        sv("Continue", "Fortsätt");
        sv("Get Started", "Kom igång");
        sv("Skip Setup", "Hoppa över inställning");
        sv("Select your language to begin", "Välj ditt språk för att börja");
        sv("Let’s set up tvOS Launcher.", "Låt oss ställa in tvOS Launcher.");
        sv("Choose a look", "Välj ett utseende");
        sv("Choose how you want your Home Screen to look.", "Välj hur du vill att hemskärmen ska se ut.");
        sv("Choose Your Scenes", "Välj dina scener");
        sv("Choose which scenes take turns behind the glass clock.", "Välj vilka scener som turas om bakom glasklockan.");
        sv("Choose a Background", "Välj en bakgrund");
        sv("Choose a photo or ambient colour theme behind your apps.", "Välj ett foto eller ett färgtema bakom dina appar.");
        sv("Use My Location", "Använd min plats");
        sv("Choose a City…", "Välj en stad…");
        sv("Search by name", "Sök efter namn");
        sv("Don't Show Weather", "Visa inte väder");
        sv("Finish Setting Up", "Slutför inställning");
        sv("Automatic Setup", "Automatisk inställning");
        sv("Choose how to grant permissions", "Välj hur behörigheter ska ges");
        sv("Easy Setup (Automatic via ADB)", "Enkel inställning (automatiskt via ADB)");
        sv("Grants all required permissions automatically at once", "Ger alla nödvändiga behörigheter automatiskt på en gång");
        sv("Manual Setup", "Manuell inställning");
        sv("Manual Permissions", "Manuella behörigheter");
        sv("Grant permissions one by one manually", "Ge behörigheter en efter en manuellt");
        sv("Enable ADB Debugging", "Aktivera ADB-felsökning");
        sv("Grant Permissions Now", "Ge behörigheter nu");
        sv("Switch to Manual Setup", "Byt till manuell inställning");
        sv("Make tvOS Launcher the Home Screen", "Gör tvOS Launcher till hemskärm");
        sv("Button Mapper & Control Panel", "Knappmappare och kontrollpanel");
        sv("Active in Accessibility", "Aktiv i Tillgänglighet");
        sv("Enable in Accessibility so remapped buttons work in all apps", "Aktivera i Tillgänglighet så att omprogrammerade knappar fungerar i alla appar");
        sv("Setup Complete", "Inställning klar");
        sv("You're All Set", "Allt är klart");
        sv("Go to Home Screen", "Gå till hemskärmen");
        sv("Save", "Spara");
        sv("Reset", "Återställ");
        sv("Close", "Stäng");
        sv("Close All", "Stäng alla");
        sv("Done", "Klar");

        // Weather & Location page
        sv("WEATHER", "VÄDER");
        sv("LOCATION", "PLATS");
        sv("Weather on the home screen", "Väder på hemskärmen");
        sv("Temperature", "Temperatur");
        sv("Update every", "Uppdatera var");
        sv(" minutes", " minut");
        sv("Weather source", "Väderkälla");
        sv("Weather from", "Väder från");
        sv("Free forecast service, no account needed", "Gratis vädertjänst, inget konto behövs");
        sv("Current place", "Nuvarande plats");
        sv("Automatic (from internet)", "Automatisk (från internet)");
        sv("Set manually", "Angiven manuellt");
        sv("Detected automatically", "Identifierad automatiskt");
        sv("Search for a city or town…", "Sök efter en stad eller ort…");
        sv("Use automatic location", "Använd automatisk plats");
        sv("Used for the weather on the home screen and local headlines.", "Används för vädret på hemskärmen och lokala nyheter.");

        // Control Panel & Recent Apps & Button Mapper & Advanced & About
        sv("Connected", "Ansluten");
        sv("Settings ›", "Inställningar ›");
        sv("Accessories", "Tillbehör");
        sv("Memory", "Minne");
        sv("Clean", "Rensa");
        sv("Cleaned · ", "Rensat · ");
        sv(" free", " ledigt");
        sv("Do Not Disturb", "Stör ej");
        sv("Audio", "Ljud");
        sv("Sound", "Ljud");
        sv("Output", "Utgång");
        sv("Ambient Mode", "Viloläge");
        sv("Screensaver", "Skärmsläckare");
        sv("Power Off", "Stäng av");
        sv("Sleep", "Vila");
        sv("Grant ›", "Tillåt ›");
        sv("No recent apps", "Inga senaste appar");
        sv("Recent Apps", "Senaste appar");
        sv("Recent Apps Switcher", "Senaste appväxlare");
        sv("Recent Apps Switcher (Overlay)", "Senaste appväxlare (överlägg)");
        sv("No recent apps yet", "Inga senaste appar ännu");
        sv("Press ▲ on an app to close it", "Tryck ▲ på en app för att stänga den");
        sv("Up and down to move, OK when done", "Upp och ner för att flytta, OK när du är klar");
        sv("Up and down to browse, OK to change, Back to close", "Upp och ner för att bläddra, OK för att ändra, Tillbaka för att stänga");
        sv("OK to change, hold OK to move, Back to go back", "OK för att ändra, håll in OK för att flytta, Tillbaka för att gå tillbaka");
        sv("OK to change, Back to go back", "OK för att ändra, Tillbaka för att gå tillbaka");
        sv("OK to open  ·  Hold OK to restore  ·  Back to go back", "OK för att öppna  ·  Håll in OK för att återställa  ·  Tillbaka");
        sv("Changes the words on the home screen, date, weather and settings.", "Ändrar språket på hemskärmen, datum, väder och inställningar.");
        sv("Active", "Aktiv");
        sv("Switch", "Byt");
        sv("PROFILES", "PROFILER");
        sv("+ Add a person", "+ Lägg till en person");
        sv("Add a person", "Lägg till en person");
        sv("Each person gets their own pinned apps and Continue Watching list.", "Varje person får sina egna fästa appar och Fortsätt titta-lista.");
        sv("Rename…", "Byt namn…");
        sv("Avatar color…", "Avatarfärg…");
        sv("Delete profile", "Ta bort profil");
        sv("None", "Ingen");
        sv("None (Clear action)", "Ingen (rensa åtgärd)");
        sv("Single Tap (1x)", "Enkeltryck (1x)");
        sv("Double Tap (2x)", "Dubbeltryck (2x)");
        sv("Triple Tap (3x)", "Trippeltryck (3x)");
        sv("ACCESSIBILITY SERVICE", "TILLGÄNGLIGHETSTJÄNST");
        sv("Button Mapper service", "Knappmappartjänst");
        sv("Enable in Accessibility…", "Aktivera i Tillgänglighet…");
        sv("REMAPPED BUTTONS", "OMPROGRAMMERADE KNAPPAR");
        sv("+ Add new button to remap", "+ Lägg till ny knapp att mappa");
        sv("Listen for a remote key press", "Lyssna efter ett knapptryck på fjärrkontrollen");
        sv("REMOTE FIXES", "FJÄRRKONTROLLFIXAR");
        sv("Long-press volume button fix", "Fix för långt tryck på volymknappen");
        sv("Clear all button mappings", "Rensa alla knappmappningar");
        sv("ADB & PERMISSIONS", "ADB OCH BEHÖRIGHETER");
        sv("ADB connection status", "ADB-anslutningsstatus");
        sv("Pair / Repair ADB connection", "Parkoppla / reparera ADB-anslutning");
        sv("Check permissions", "Kontrollera behörigheter");
        sv("Check Permissions", "Kontrollera behörigheter");
        sv("Grant All via ADB", "Ge alla via ADB");
        sv("Repair", "Reparera");
        sv("Pair now", "Parkoppla nu");
        sv("Paired & Ready", "Parkopplad och redo");
        sv("Not paired (ADB off)", "Inte parkopplad (ADB av)");
        sv("active", "aktiva");
        sv("KEEP ALIVE", "KEEP ALIVE (AUTOMATISK REPARATION)");
        sv("Keep Alive (Auto-repair permissions)", "Keep Alive (reparera behörigheter automatiskt)");
        sv("CLEANYOURTV (BETA)", "CLEANYOURTV (BETA)");
        sv("Choose TV model", "Välj TV-modell");
        sv("Debloat status", "Debloat-status");
        sv("Debloat TV…", "Rensa TV (Debloat)…");
        sv("Start ›", "Starta ›");
        sv("Disabled apps backup", "Säkerhetskopia av inaktiverade appar");
        sv("Restore debloated apps…", "Återställ inaktiverade appar…");
        sv("Restore ›", "Återställ ›");
        sv("Ad-blocking Private DNS (AdGuard)", "Annonsblockerande privat DNS (AdGuard)");
        sv("APPLICATION", "APPLIKATION");
        sv("Home screen", "Hemskärm");
        sv("Developer", "Utvecklare");
        sv("Version", "Version");
        sv("Settings kept", "Inställningar sparas");
        sv("On this TV", "På denna TV");
        sv("RESET", "ÅTERSTÄLL");
        sv("Reset settings to defaults", "Återställ inställningar till standard");
        sv("Reset settings?", "Återställa inställningar?");
        sv("This resets look, scenes, clock and language settings back to defaults.", "Detta återställer utseende, scener, klocka och språk till standardvärden.");
        sv("Custom 2", "Anpassad 2");
        sv("Custom 3", "Anpassad 3");
        sv("Custom 4", "Anpassad 4");
        sv("Custom 5", "Anpassad 5");
        sv("App Icon Editor", "Appikonredigerare");
        sv("Preview", "Förhandsgranskning");
        sv("Background color", "Bakgrundsfärg");
        sv("Zoom", "Zoom");
        sv("Zoom in", "Zooma in");
        sv("Zoom out", "Zooma ut");
        sv("Move icon", "Flytta ikon");
        sv("Up", "Upp");
        sv("Down", "Ner");
        sv("Apply", "Tillämpa");
        sv("Auto", "Auto");
        sv("Dark", "Mörk");
        sv("Black", "Svart");
        sv("White", "Vit");
        sv("Gray", "Grå");
        sv("Red", "Röd");
        sv("Orange", "Orange");
        sv("Yellow", "Gul");
        sv("Green", "Grön");
        sv("Teal", "Turkos");
        sv("Blue", "Blå");
        sv("Indigo", "Indigo");
        sv("Purple", "Lila");
        sv("Pink", "Rosa");
    }

    private I18n() { }

    public static String detectSystemLang(Context c) {
        String raw = null;
        try {
            if (c != null && c.getResources() != null && c.getResources().getConfiguration() != null) {
                android.os.LocaleList locales = c.getResources().getConfiguration().getLocales();
                if (locales != null && !locales.isEmpty()) {
                    raw = locales.get(0).getLanguage();
                }
            }
        } catch (Throwable ignored) {
        }
        if (raw == null || raw.isEmpty()) {
            raw = Locale.getDefault().getLanguage();
        }
        if (raw == null || raw.isEmpty()) {
            return LANG_EN;
        }
        String code = raw.trim().toLowerCase(Locale.US);
        int sep = Math.max(code.indexOf('-'), code.indexOf('_'));
        if (sep > 0) {
            code = code.substring(0, sep);
        }
        return SUPPORTED.contains(code) ? code : LANG_EN;
    }

    public static void resetCache() {
        cachedLang = null;
    }

    public static String getLang(Context c) {
        if (cachedLang != null) return cachedLang;
        SharedPreferences p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String saved = p.getString("lang", null);
        if (saved == null || !SUPPORTED.contains(saved)) {
            saved = detectSystemLang(c);
            p.edit().putString("lang", saved).apply();
            if (!p.contains("clock_24h")) {
                Scenes.setClock24h(c, isDefault24hForLang(saved));
            }
        }
        cachedLang = saved;
        return cachedLang;
    }

    public static boolean isDefault24hForLang(String lang) {
        if (lang == null) return false;
        switch (lang) {
            case LANG_EN:
            case LANG_HI:
            case LANG_AR:
                return false;
            default:
                return true;
        }
    }

    public static void setLang(Context c, String lang) {
        String valid = (lang != null && SUPPORTED.contains(lang)) ? lang : LANG_EN;
        cachedLang = valid;
        c.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit().putString("lang", valid).apply();
        Scenes.setClock24h(c, isDefault24hForLang(valid));
    }

    public static boolean isTr(Context c) {
        return LANG_TR.equals(getLang(c));
    }

    public static Locale locale(Context c) {
        switch (getLang(c)) {
            case LANG_TR: return Locale.forLanguageTag("tr-TR");
            case LANG_SV: return Locale.forLanguageTag("sv-SE");
            case LANG_HI: return Locale.forLanguageTag("hi-IN");
            case LANG_ES: return Locale.forLanguageTag("es-ES");
            case LANG_FR: return Locale.forLanguageTag("fr-FR");
            case LANG_DE: return Locale.forLanguageTag("de-DE");
            case LANG_IT: return Locale.forLanguageTag("it-IT");
            case LANG_RU: return Locale.forLanguageTag("ru-RU");
            case LANG_PT: return Locale.forLanguageTag("pt-PT");
            case LANG_AR: return Locale.forLanguageTag("ar-SA");
            case LANG_JA: return Locale.forLanguageTag("ja-JP");
            case LANG_ZH: return Locale.forLanguageTag("zh-CN");
            default: return Locale.US;
        }
    }

    /** Returns localized string based on active language. */
    public static String s(Context c, String en, String tr) {
        if (en == null) return "";
        String lang = getLang(c);
        if (LANG_EN.equals(lang)) return en;
        if (LANG_TR.equals(lang)) return tr != null ? tr : en;
        return translateOther(lang, en);
    }

    /** Translates a key using the active language dictionary. */
    public static String t(Context c, String enKey) {
        if (enKey == null) return "";
        String lang = getLang(c);
        if (LANG_EN.equals(lang)) return enKey;
        if (LANG_TR.equals(lang)) {
            String v = TR_MAP.get(enKey);
            return v != null ? v : enKey;
        }
        return translateOther(lang, enKey);
    }

    private static String translateOther(String lang, String en) {
        Map<String, String> map = LANG_MAPS.get(lang);
        if (map != null) {
            String direct = map.get(en);
            if (direct != null) return direct;
        }
        // Dynamic pattern translations
        if (en.endsWith("% used · Recent Apps") && map != null) {
            String num = en.substring(0, en.length() - "% used · Recent Apps".length());
            return num + "% · " + map.getOrDefault("Recent Apps", "Recent Apps");
        }
        if (en.endsWith("% used · Clean") && map != null) {
            String num = en.substring(0, en.length() - "% used · Clean".length());
            return num + "% · " + map.getOrDefault("Clean", "Clean");
        }
        if (en.endsWith(" min left")) {
            String num = en.substring(0, en.length() - " min left".length());
            return num + " min";
        }
        if (en.startsWith("Move ") && map != null) {
            String rest = en.substring("Move ".length());
            if (!rest.equals("left in dock") && !rest.equals("right in dock")) {
                return map.getOrDefault("Move", "Move") + " " + rest;
            }
        }
        if (en.startsWith("Switch to ") && map != null) {
            String rest = en.substring("Switch to ".length());
            return map.getOrDefault("Switch", "Switch") + ": " + rest;
        }
        if (en.startsWith("Done (") && en.endsWith(" apps disabled)") && map != null) {
            String num = en.substring("Done (".length(), en.length() - " apps disabled)".length());
            return map.getOrDefault("Done", "Done") + " (" + num + " " + map.getOrDefault("apps", "apps") + ")";
        }
        if (en.startsWith("Not done (") && en.endsWith(" disabled)") && map != null) {
            String ratio = en.substring("Not done (".length(), en.length() - " disabled)".length());
            return map.getOrDefault("Not done", "Not done") + " (" + ratio + ")";
        }
        return en;
    }

    /** Formats the date line above the scene clock according to the active language. */
    public static String formatSceneDate(Context c, Date date) {
        String lang = getLang(c);
        Locale loc = locale(c);
        switch (lang) {
            case LANG_TR:
                return new SimpleDateFormat("d MMMM EEEE", loc).format(date);
            case LANG_JA:
            case LANG_ZH:
                return new SimpleDateFormat("M月d日 EEEE", loc).format(date);
            case LANG_DE:
                return new SimpleDateFormat("EEEE, d. MMMM", loc).format(date);
            case LANG_SV:
            case LANG_ES:
            case LANG_FR:
            case LANG_IT:
            case LANG_PT:
            case LANG_RU:
            case LANG_HI:
            case LANG_AR:
                return new SimpleDateFormat("EEEE d MMMM", loc).format(date);
            default:
                return new SimpleDateFormat("EEEE d MMMM", Locale.UK).format(date);
        }
    }

    /** Translates default profile names ("Person 1", "Default", "Kids") while preserving custom names. */
    public static String profileName(Context c, String name) {
        if (name == null) return "";
        if (isTr(c)) {
            if (name.equals("Default")) return "Varsayılan";
            if (name.startsWith("Person ")) return "Kişi " + name.substring("Person ".length());
            if (name.startsWith("Profile ")) return "Profile " + name.substring("Profile ".length());
            if (name.equals("Kids")) return "Çocuklar";
        } else {
            if (name.equals("Varsayılan")) return "Default";
            if (name.startsWith("Kişi ")) return "Person " + name.substring("Kişi ".length());
            if (name.startsWith("Profil ")) return "Profile " + name.substring("Profil ".length());
        }
        return name;
    }
}
