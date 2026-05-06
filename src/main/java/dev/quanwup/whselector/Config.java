package dev.quanwup.whselector;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class Config {
    public String jsonFile = "";

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("wh-selector.properties");
    }

    public static Config load() {
        Config c = new Config();
        Path p = path();
        if (Files.exists(p)) {
            try {
                Properties props = new Properties();
                try (var in = Files.newInputStream(p)) {
                    props.load(in);
                }
                c.jsonFile = props.getProperty("jsonFile", c.jsonFile);
            } catch (IOException e) {
                WhSelectorClient.LOGGER.warn("failed to load config: {}", e.getMessage());
            }
        }
        return c;
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty("jsonFile", jsonFile);
        try {
            Files.createDirectories(path().getParent());
            try (var out = Files.newOutputStream(path())) {
                props.store(out, "warehouse-selector config");
            }
        } catch (IOException e) {
            WhSelectorClient.LOGGER.warn("failed to save config: {}", e.getMessage());
        }
    }

    public boolean hasJsonFile() {
        return jsonFile != null && !jsonFile.isBlank();
    }
}
