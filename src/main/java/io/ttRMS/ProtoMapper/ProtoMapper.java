package io.ttRMS.ProtoMapper;

import com.google.gson.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;

public class ProtoMapper {
    private static final int[] STATES_TO_CHECK = new int[]{0, 1, 66, 256, 500, 3690};


    public static void main(String[] args) throws IOException {
        //generate();
        System.out.println("Checking generated states:");
        for (int state : STATES_TO_CHECK)
            System.out.printf("State [%s] is: %s%n", state, stateToBlock(state));
    }

    private static Gson newGson() {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    public static String stateToBlock(int state) throws IOException {
        return newGson()
                .fromJson(new String(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResourceAsStream("mapping-1.17.1.json")).readAllBytes()), JsonObject.class)
                .getAsJsonObject("blockstates")
                .get(Integer.toString(state))
                .getAsString();
    }

    public static JsonObject generate() throws IOException {
        System.out.println("Generating reports...\n");

        // Call the vanilla server jar
        net.minecraft.data.Main.main(new String[]{"--reports"});

        String content = new String(Files.readAllBytes(new File("generated/reports/blocks.json").toPath()));

        var gson = newGson();
        JsonObject object = gson.fromJson(content, JsonObject.class);

        final JsonObject viaMappings = new JsonObject();
        final JsonObject blockstates = new JsonObject();
        final JsonObject blocks = new JsonObject();
        viaMappings.add("blockstates", blockstates);
        viaMappings.add("blocks", blocks);

        String lastBlock = "";
        int id = 0;
        for (final Map.Entry<String, JsonElement> blocksEntry : object.entrySet()) {
            final JsonObject block = blocksEntry.getValue().getAsJsonObject();
            final JsonArray states = block.getAsJsonArray("states");
            for (final JsonElement state : states) {
                final StringBuilder value = new StringBuilder(blocksEntry.getKey());
                if (!lastBlock.equals(blocksEntry.getKey())) {
                    lastBlock = blocksEntry.getKey();
                    blocks.add(Integer.toString(id++), new JsonPrimitive(lastBlock.replace("minecraft:", "")));
                }
                if (state.getAsJsonObject().has("properties")) {
                    value.append("[");
                    final JsonObject properties = state.getAsJsonObject().getAsJsonObject("properties");
                    boolean first = true;
                    for (final Map.Entry<String, JsonElement> propertyEntry : properties.entrySet()) {
                        if (first) {
                            first = false;
                        } else {
                            value.append(',');
                        }
                        value.append(propertyEntry.getKey()).append('=').append(propertyEntry.getValue().getAsJsonPrimitive().getAsString());
                    }
                    value.append("]");
                }
                blockstates.add(state.getAsJsonObject().get("id").getAsString(), new JsonPrimitive(value.toString()));
            }
        }

        content = new String(Files.readAllBytes(new File("generated/reports/registries.json").toPath()));
        object = gson.fromJson(content, JsonObject.class);

        final JsonObject items = new JsonObject();
        viaMappings.add("items", items);

        for (final Map.Entry<String, JsonElement> itemsEntry : object.getAsJsonObject("minecraft:item").getAsJsonObject("entries").entrySet()) {
            items.add(String.valueOf(itemsEntry.getValue().getAsJsonObject().getAsJsonPrimitive("protocol_id").getAsInt()), new JsonPrimitive(itemsEntry.getKey()));
        }

        final JsonArray sounds = new JsonArray();
        viaMappings.add("sounds", sounds);

        int i = 0;
        for (final Map.Entry<String, JsonElement> soundEntry : object.getAsJsonObject("minecraft:sound_event").getAsJsonObject("entries").entrySet()) {
            if (soundEntry.getValue().getAsJsonObject().getAsJsonPrimitive("protocol_id").getAsInt() != i) {
                throw new IllegalStateException();
            }
            sounds.add(new JsonPrimitive(soundEntry.getKey().replace("minecraft:", "")));
            i++;
        }

        try (final PrintWriter out = new PrintWriter("src/main/resources/mapping-1.17.1.json")) {
            out.print(gson.toJson(viaMappings));
        }

        System.out.println("\n\n");

        return viaMappings;
    }
}
