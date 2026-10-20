package com.example.router.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class Snapshots {

    /** With -PupdateFlowDocs, write the file; otherwise fail on any difference from the committed file. */
    static void verifyOrUpdate(Path path, String actual) throws IOException {
        if (Boolean.getBoolean("updateFlowDocs")) {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, actual);
            return;
        }
        String committed = Files.exists(path) ? Files.readString(path).replace("\r\n", "\n") : "";
        assertEquals(committed, actual,
                "Flow docs out of date: " + path + ". Run ./gradlew test -PupdateFlowDocs and commit docs/");
    }

    private Snapshots() {
    }
}
