package com.example.flowdocs.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One scenario file: the request to send, the status the app must return, and the WireMock stubs that
 * stand in for downstream systems. The file name (without .json) is the scenario's name.
 */
public record Scenario(String description, String method, String path, JsonNode request, int expectedStatus,
                       List<JsonNode> stubs) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public Scenario {
        if (description == null || method == null || path == null || request == null || expectedStatus == 0) {
            throw new IllegalArgumentException(
                    "Scenario needs description, method, path, request and expectedStatus");
        }
        stubs = stubs == null ? List.of() : List.copyOf(stubs);
    }

    public static Scenario load(Path dir, String name) throws IOException {
        return JSON.readValue(Files.readString(dir.resolve(name + ".json")), Scenario.class);
    }

    /** Scenario names in the directory, sorted; for a @MethodSource. */
    public static Stream<String> names(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - ".json".length()))
                    .sorted().toList().stream();
        }
    }
}
