package dev.sayitem.core;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import java.nio.file.Path;
import java.util.TreeMap;

/** Build-time registry report; no world, server, or player data is created. */
public final class ItemCatalogExport {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var items = new TreeMap<String, String>();
        BuiltInRegistries.ITEM.forEach(item -> {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (!id.equals("minecraft:air")) items.put(id, item.getDescriptionId());
        });
        JsonFiles.write(Path.of(args[0]), items);
        System.out.println("Exported " + items.size() + " registered item translation keys");
    }
}
