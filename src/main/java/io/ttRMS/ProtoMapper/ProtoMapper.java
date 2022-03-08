package io.ttrms.protomapper;

import com.google.gson.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Objects;
import java.util.regex.Pattern;

public class ProtoMapper {
    private static final int[] STATES_TO_CHECK = new int[]{0, 1, 66, 256, 500, 3690};
    private static final String STATE_MAP = "mapping-1.12.2.json";
    private static final String TRANSLATE_MAP = "language-map-burger-1.12.2.json";
    private static final String BLOCK_REPORT = "generated/reports/blocks.json";
    private static final String REGISTRIES_REPORT = "generated/reports/registries.json";

    public static void main(String[] args) {
        // * Uncomment this line when you need to generate a new list (1/2)
        //generate();

        // Check a few states
        System.out.println("Checking generated states:");
        for (int state : STATES_TO_CHECK)
            System.out.printf("State [%s] is: %s%n", state, stateToBlock(state));

        System.out.printf(getTranslationFormat("multiplayer.player.joined"), "tycrek");
        System.out.printf(getTranslationFormat("chat.type.text"), "tycrek", "hello world");
    }

    public static String stateToBlock(int state) {
        try {
            return newGson()
                    .fromJson(readResourceAsJson(STATE_MAP), JsonObject.class)
                    .getAsJsonObject("blockstates")
                    .get(Integer.toString(state))
                    .getAsString();
        } catch (IOException ex) {
            ex.printStackTrace();
            return "air";
        }
    }

    public static String getTranslationFormat(String key) {
        try {
            var keyParts = key.split(Pattern.quote("."), 2);
            return newGson()
                    .fromJson(readResourceAsJson(TRANSLATE_MAP), JsonObject.class)
                    .getAsJsonObject("language")
                    .getAsJsonObject(keyParts[0])
                    .get(keyParts[1])
                    .getAsString()
                    .concat("%n");
        } catch (IOException ex) {
            ex.printStackTrace();
            return "";
        }
    }

    private static String readResourceAsJson(String resource) throws IOException {
        return new String(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)).readAllBytes());
    }

    // Rewritten from: https://gist.github.com/kennytv/1ee95cd3b8bb57dc8ee8cb71d5a4883e
    public static JsonObject generate() throws IOException {
        System.out.println("Generating reports...\n");

        // * Uncomment this line when you need to generate a new list (2/2)
        //net.minecraft.server.MinecraftServer.main(new String[]{"--reports"});

        // Create a Gson instance to use throughout generation
        var gson = newGson();

        // Read the reports into JSON objects
        var blockObject = gson.fromJson(new String(Files.readAllBytes(new File(BLOCK_REPORT).toPath())), JsonObject.class);
        var registriesObject = gson.fromJson(new String(Files.readAllBytes(new File(REGISTRIES_REPORT).toPath())), JsonObject.class);

        // Create all the JSON objects for the Minecraft data
        var mappings = new JsonObject();
        var blockStates = new JsonObject();
        var blocks = new JsonObject();
        var items = new JsonObject();
        var sounds = new JsonArray();

        // Add all sub-map objects to the primary map object
        mappings.add("blockstates", blockStates);
        mappings.add("blocks", blocks);
        mappings.add("items", items);
        mappings.add("sounds", sounds);

        // * Parse block/state information (1/3)
        String lastBlock = "";
        int id = 0;

        for (var blocksEntry : blockObject.entrySet()) {

            var block = blocksEntry.getValue().getAsJsonObject();
            var states = block.getAsJsonArray("states");

            for (var state : states) {
                var value = new StringBuilder(blocksEntry.getKey());

                if (!lastBlock.equals(blocksEntry.getKey())) {
                    lastBlock = blocksEntry.getKey();
                    blocks.add(Integer.toString(id++), new JsonPrimitive(lastBlock.replace("minecraft:", "")));
                }

                // If this block state has properties, add them to the String
                if (state.getAsJsonObject().has("properties")) {
                    value.append("[");

                    var properties = state.getAsJsonObject().getAsJsonObject("properties");
                    boolean first = true;

                    for (var propertyEntry : properties.entrySet()) {
                        if (first) first = false;
                        else value.append(',');

                        value.append(propertyEntry.getKey()).append('=').append(propertyEntry.getValue().getAsJsonPrimitive().getAsString());
                    }

                    value.append("]");
                }

                blockStates.add(state.getAsJsonObject().get("id").getAsString(), new JsonPrimitive(value.toString()));
            }
        }

        // * Parse ITEM information (2/3)
        for (var itemsEntry : registriesObject.getAsJsonObject("minecraft:item").getAsJsonObject("entries").entrySet())
            items.add(String.valueOf(itemsEntry.getValue().getAsJsonObject().getAsJsonPrimitive("protocol_id").getAsInt()), new JsonPrimitive(itemsEntry.getKey()));

        // * Parse SOUND information (3/3)
        int i = 0;
        for (var soundEntry : registriesObject.getAsJsonObject("minecraft:sound_event").getAsJsonObject("entries").entrySet()) {
            if (soundEntry.getValue().getAsJsonObject().getAsJsonPrimitive("protocol_id").getAsInt() != i)
                throw new IllegalStateException();

            sounds.add(new JsonPrimitive(soundEntry.getKey().replace("minecraft:", "")));
            i++;
        }

        try (var out = new PrintWriter(String.format("src/main/resources/%s", STATE_MAP))) {
            out.print(gson.toJson(mappings));
        }

        System.out.println("\n\n");

        return mappings;
    }

    private static Gson newGson() {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }
}
