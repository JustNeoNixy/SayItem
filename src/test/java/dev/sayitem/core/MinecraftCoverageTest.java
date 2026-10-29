package dev.sayitem.core;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftCoverageTest {
    @TempDir Path directory;
    @Test void everyRealRegisteredItemHasANativeTranslationInEveryBuiltInPack() throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var packs = new PackRepository(directory);
        int checked = 0;
        for (var item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (id.equals("minecraft:air")) continue;
            for (String language : packs.ids()) assertTrue(packs.get(language).items().containsKey(id), language + " lacks " + id);
            checked++;
        }
        assertTrue(checked >= 1505);
    }
}
