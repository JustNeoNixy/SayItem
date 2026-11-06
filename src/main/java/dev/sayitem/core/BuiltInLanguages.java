package dev.sayitem.core;

import java.util.Map;

public final class BuiltInLanguages {
    private BuiltInLanguages() {}
    public static final String RANDOM = "random";
    public static final Map<String, String> LOCALES = Map.ofEntries(
        Map.entry("en", "en_us"), Map.entry("ja", "ja_jp"), Map.entry("es", "es_es"),
        Map.entry("fr", "fr_fr"), Map.entry("de", "de_de"), Map.entry("pl", "pl_pl"),
        Map.entry("bg", "bg_bg"), Map.entry("ru", "ru_ru"), Map.entry("it", "it_it"),
        Map.entry("pt", "pt_pt"), Map.entry("nl", "nl_nl"), Map.entry("sv", "sv_se"),
        Map.entry("da", "da_dk"), Map.entry("no", "no_no"), Map.entry("fi", "fi_fi"),
        Map.entry("cs", "cs_cz"), Map.entry("sk", "sk_sk"), Map.entry("uk", "uk_ua"),
        Map.entry("el", "el_gr"), Map.entry("tr", "tr_tr"), Map.entry("hu", "hu_hu"),
        Map.entry("ro", "ro_ro"), Map.entry("ko", "ko_kr"), Map.entry("zh", "zh_cn"),
        Map.entry("id", "id_id"), Map.entry("vi", "vi_vn"));
    public static boolean validChoice(String language) {
        return language != null && (language.equals(RANDOM) || language.matches("[a-z]{2,3}(-[a-z0-9]+)?"));
    }
}
