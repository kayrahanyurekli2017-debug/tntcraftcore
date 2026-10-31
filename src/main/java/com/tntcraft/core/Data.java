package com.tntcraft.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.neoforged.fml.loading.FMLPaths;

/** Tum veriler config/tntcraft/data.json dosyasinda tutulur. */
public final class Data {
    public static class Home {
        public String dim;
        public double x, y, z;
        public float yaw, pitch;
    }

    public static class PlayerData {
        public String name = "?";
        public long balance;
        public long lastDaily;
        public Map<String, Home> homes = new LinkedHashMap<>();
    }

    public static class Settings {
        public String currency = "TNT";
        public long startBalance = 100;
        public int maxHomes = 3;
        public long dailyReward = 50;
        /** /eco komutunu kullanabilecek oyuncu adlari. Konsol her zaman kullanabilir. */
        public List<String> admins = new ArrayList<>();
        public Map<String, Long> sellPrices = new LinkedHashMap<>();
    }

    public static class Root {
        public Settings settings = new Settings();
        public Map<String, PlayerData> players = new LinkedHashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Root root = new Root();

    private Data() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("tntcraft").resolve("data.json");
    }

    private static void defaults() {
        Map<String, Long> p = root.settings.sellPrices;
        if (!p.isEmpty()) return;
        p.put("minecraft:coal", 1L);
        p.put("minecraft:copper_ingot", 2L);
        p.put("minecraft:redstone", 1L);
        p.put("minecraft:lapis_lazuli", 2L);
        p.put("minecraft:gunpowder", 4L);
        p.put("minecraft:iron_ingot", 5L);
        p.put("minecraft:gold_ingot", 8L);
        p.put("minecraft:quartz", 2L);
        p.put("minecraft:emerald", 30L);
        p.put("minecraft:diamond", 50L);
        p.put("minecraft:tnt", 20L);
        p.put("minecraft:netherite_ingot", 400L);
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Root r = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Root.class);
                if (r != null) root = r;
            }
        } catch (Exception e) {
            TNTCraftCore.LOGGER.error("data.json okunamadi, bos veriyle devam ediliyor", e);
            root = new Root();
        }
        if (root.settings == null) root.settings = new Settings();
        if (root.players == null) root.players = new LinkedHashMap<>();
        defaults();
        save();
    }

    public static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            TNTCraftCore.LOGGER.error("data.json yazilamadi", e);
        }
    }

    public static Settings settings() {
        return root.settings;
    }

    public static Map<String, PlayerData> players() {
        return root.players;
    }

    public static PlayerData player(UUID id, String name) {
        PlayerData d = root.players.get(id.toString());
        if (d == null) {
            d = new PlayerData();
            d.balance = root.settings.startBalance;
            root.players.put(id.toString(), d);
        }
        d.name = name;
        return d;
    }
}
