package dev.sayitem.core;

import com.ibm.icu.text.Transliterator;
import java.nio.file.*;
import java.util.*;

/** Build-time script variants only; the mod gains no ICU dependency on the server. */
public final class GenerateOrthographicAliases {
    public static void main(String[] args) throws Exception {
        var traditional = Transliterator.getInstance("Simplified-Traditional");
        for (String language : List.of("zh", "el")) {
            Path file = Path.of("src/main/resources/languages/" + language + ".json");
            var original = JsonFiles.GSON.fromJson(Files.readString(file), LanguagePack.class);
            var items = new TreeMap<String, LanguagePack.Entry>(); int count = 0;
            for (var item : original.items().entrySet()) {
                var entry = item.getValue(); String variant = language.equals("zh") ? traditional.transliterate(entry.text())
                    : entry.text().replace("αβγ", "αυγ").replace("Αβγ", "Αυγ");
                var aliases = new LinkedHashSet<>(entry.aliases());
                if (!variant.equals(entry.text()) && aliases.size() < 32 && aliases.add(variant)) count++;
                items.put(item.getKey(), new LanguagePack.Entry(entry.text(), entry.reading(), List.copyOf(aliases)));
            }
            var generated = new LinkedHashMap<String, Object>();
            generated.put("schemaVersion", 1); generated.put("id", original.id()); generated.put("name", original.name());
            generated.put("whisperLanguage", original.whisperLanguage()); generated.put("items", items);
            JsonFiles.write(file, generated);
            System.out.println(language + ": " + count + " orthographic alternatives added");
        }
    }
}
