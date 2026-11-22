package com.sattune.service;

import com.sattune.model.AppData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Saves and loads AppData as a single JSON file in the OS-appropriate
 * application data directory.
 */
public class PersistenceService {

    private static final Logger LOG = Logger.getLogger(PersistenceService.class.getName());
    private static final String FILE_NAME = "library.json";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path dataFile;

    public PersistenceService() {
        this.dataFile = resolveDataFile();
    }

    private static Path resolveDataFile() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path dir;
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            dir = Paths.get(appData != null ? appData : System.getProperty("user.home"), "SatTune");
        } else if (os.contains("mac")) {
            dir = Paths.get(System.getProperty("user.home"), "Library", "Application Support", "SatTune");
        } else {
            String xdg = System.getenv("XDG_DATA_HOME");
            dir = Paths.get(xdg != null ? xdg : System.getProperty("user.home") + "/.local/share", "SatTune");
        }
        try { Files.createDirectories(dir); } catch (IOException e) { LOG.log(Level.WARNING, "Cannot create data dir", e); }
        return dir.resolve(FILE_NAME);
    }

    public Path getDataFilePath() { return dataFile; }

    /** Load persisted data, or return a fresh empty AppData on any error. */
    public AppData load() {
        if (!Files.exists(dataFile)) return new AppData();
        try (Reader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(dataFile.toFile()), StandardCharsets.UTF_8))) {
            AppData data = gson.fromJson(r, AppData.class);
            return data != null ? data : new AppData();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load library data, starting fresh", e);
            return new AppData();
        }
    }

    /** Persist data to disk asynchronously (fire-and-forget). */
    public void saveAsync(AppData data) {
        String json = gson.toJson(data);
        Thread t = new Thread(() -> {
            try (Writer w = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(dataFile.toFile()), StandardCharsets.UTF_8))) {
                w.write(json);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to save library data", e);
            }
        }, "sattune-save");
        t.setDaemon(true);
        t.start();
    }
}
