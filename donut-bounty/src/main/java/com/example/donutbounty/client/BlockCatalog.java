package com.example.donutbounty.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Every registered block (except air) with its display name and an icon (empty for blocks that have no item form). */
public final class BlockCatalog {

    public record Entry(Block block, Identifier id, String name, String searchKey, ItemStack icon) {}

    private static List<Entry> all;

    private BlockCatalog() {}

    public static synchronized List<Entry> all() {
        if (all == null) {
            List<Entry> list = new ArrayList<>();
            for (Block block : Registries.BLOCK) {
                if (block.getDefaultState().isAir()) continue;
                Identifier id = Registries.BLOCK.getId(block);
                String name = block.getName().getString();
                Item item = block.asItem();
                ItemStack icon = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
                String key = (name + " " + id.getPath() + " " + id.getNamespace()).toLowerCase(Locale.ROOT);
                list.add(new Entry(block, id, name, key, icon));
            }
            list.sort(Comparator.comparing((Entry e) -> e.name().toLowerCase(Locale.ROOT)));
            all = Collections.unmodifiableList(list);
        }
        return all;
    }

    /** Every whitespace-separated word must appear in the block's name or id. */
    public static List<Entry> filter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return all();
        String[] tokens = q.split("\\s+");
        List<Entry> out = new ArrayList<>();
        outer:
        for (Entry e : all()) {
            for (String t : tokens) {
                if (!e.searchKey().contains(t)) continue outer;
            }
            out.add(e);
        }
        return out;
    }

    public static Optional<Entry> byId(String id) {
        Identifier parsed = Identifier.tryParse(id == null ? "" : id);
        if (parsed == null) return Optional.empty();
        for (Entry e : all()) {
            if (e.id().equals(parsed)) return Optional.of(e);
        }
        return Optional.empty();
    }
}
