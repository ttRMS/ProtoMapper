package io.ttrms.protomapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class ProtoMapper {
    private final String VERSION;
    private static final int[] STATES_TO_CHECK = new int[]{0, 1, 66, 256, 500, 3690};
    private static final String MINECRAFT_VERSION_MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final String BURGER = "https://pokechu22.github.io/Burger/%s.json";
    private static final String VIAVER = "https://raw.githubusercontent.com/ViaVersion/ViaVersion/master/common/src/main/resources/assets/viaversion/data/mapping-%s.json";
    private static final String LANGUAGE_MAP = "language-%s.json";
    private static final String BLOCKSTATE_MAP = "blockstate-%s.json";
    private static final String ITEMS_MAP = "items-%s.json";

    public static void main(String[] args) throws IOException {
        // * Uncomment this line when you need to generate a new list (1/2)
        if (args.length > 0 && args[0].equals("generate")) {
            generate();
            return;
        }

        var pm = ProtoMapper.Version("1.12.2");

        // Check a few states
        System.out.println("Checking generated states:");
        for (int state : STATES_TO_CHECK)
            System.out.printf("State [%s] is: %s%n", state, pm.stateToBlock(state));

        System.out.printf(pm.getTranslationFormat("multiplayer.player.joined"), "tycrek");
        System.out.printf(pm.getTranslationFormat("chat.type.text"), "tycrek", "hello world");
    }

    public static ProtoMapper Version(String version) {
        return new ProtoMapper(version);
    }

    public String stateToBlock(int state) {
        try {
            return newGson()
                    .fromJson(readResourceAsJson(BLOCKSTATE_MAP), JsonObject.class)
                    .get(Integer.toString(state))
                    .getAsString();
        } catch (IOException ex) {
            ex.printStackTrace();
            return "air";
        }
    }

    public String getTranslationFormat(String key) {
        try {
            var keyParts = key.split(Pattern.quote("."), 2);
            return newGson()
                    .fromJson(readResourceAsJson(LANGUAGE_MAP), JsonObject.class)
                    .getAsJsonObject(keyParts[0])
                    .get(keyParts[1])
                    .getAsString();
        } catch (IOException ex) {
            ex.printStackTrace();
            return "";
        }
    }

    private static void generate() {
        val gson = newGson();

        // Get Minecraft version list
        var minecraft = downloadJson(gson, MINECRAFT_VERSION_MANIFEST, MinecraftManifest.class);

        var completedViaShortVersions = new ArrayList<String>();

        // Generate data for each version
        minecraft.versions
                .stream()
                .filter(version -> version.type.equals("release"))
                .map(MinecraftVersionInfo::getId)
                .forEach(version -> {
                    // Get Burger data
                    burger(gson, version);

                    // Remove patch from version
                    var viaVersion = version.split("\\.");
                    var viaVersionShort = viaVersion[0] + "." + viaVersion[1];
                    if (!completedViaShortVersions.contains(viaVersionShort)) {
                        viaver(gson, viaVersionShort);
                        completedViaShortVersions.add(viaVersionShort);
                    }
                });
    }

    private static void burger(Gson gson, String version) {
        // Try to read the file to check if it exits. NPE or IOE will be thrown if it doesn't
        try {
            readResourceAsJson(String.format(LANGUAGE_MAP, version));
        } catch (NullPointerException | IOException ignored) {
            try {
                // Attempt to download. This will throw a FileNotFoundException if it fails, but Java
                // is dumb, so we need to catch a regular Exception
                var burger = downloadJson(gson, String.format(BURGER, version), JsonArray.class).get(0).getAsJsonObject();

                // Write the file
                writeToResource(String.format(LANGUAGE_MAP, version), gson.toJson(burger.getAsJsonObject("language")));
            } catch (Exception ex) {
                System.err.printf("Could not find Burger data for version %s%n", version);
            }
        }
    }

    private static void viaver(Gson gson, String version) {
        // Try to read the files to check if they exist. NPE or IOE will be thrown if they don't
        boolean blocksExist = true, itemsExist = true;
        //#region boolean try/catch (unnecessarily thicc lines)
        try {
            readResourceAsJson(String.format(BLOCKSTATE_MAP, version));
        }
        catch (NullPointerException | IOException ignored) {
            blocksExist = false;
        }
        try {
            readResourceAsJson(String.format(ITEMS_MAP, version));
        } catch (NullPointerException | IOException ignored) {
            itemsExist = false;
        }
        //#endregion

        // If the files don't exist, download them
        JsonObject blocks, items;
        if (!blocksExist || !itemsExist) {
            try {
                var vv = downloadJson(gson, String.format(VIAVER, version), JsonObject.class);
                blocks = vv.getAsJsonObject("blocks");
                items = vv.getAsJsonObject("items");

                // Write the files
                if (!blocksExist) writeToResource(String.format(BLOCKSTATE_MAP, version), gson.toJson(blocks));
                if (!itemsExist) writeToResource(String.format(ITEMS_MAP, version), gson.toJson(items));
            } catch (Exception ex) {
                System.err.printf("Could not find Viaver data for version %s%n", version);
            }
        }
    }

    private static String readResourceAsJson(String resource) throws IOException {
        return new String(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)).readAllBytes());
    }

    @SneakyThrows
    private static <T> T downloadJson(Gson gson, String url, Class<T> clazz) {
        return gson.fromJson(new InputStreamReader(new URL(url).openConnection().getInputStream()), clazz);
    }

    private static void writeToResource(String filename, String json) {
        try (var out = new PrintWriter(String.format("src/main/resources/%s", filename))) {
            out.print(json);
            System.out.printf("Generated %s%n", filename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static Gson newGson() {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }
}
