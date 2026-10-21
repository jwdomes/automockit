package com.example.flowdocs.test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Path;

/**
 * Where an app's flow docs come from and go to. Paths are relative to the module directory, which is
 * Gradle's working directory for tests.
 *
 * @param basePackage      package scanned for steps, decisions and participants
 * @param entryParticipant participant that receives requests, used when a request is rejected before any step
 * @param docsDir          output directory for flow.mmd, README.md and flows/
 * @param scenariosDir     directory of scenario JSON files
 * @param upstreamLabel    label of the caller lifeline in scenario diagrams
 */
public record FlowDocsSettings(String basePackage, String entryParticipant, Path docsDir, Path scenariosDir,
                               String upstreamLabel) {

    public static FlowDocsSettings of(String basePackage, String entryParticipant) {
        return new FlowDocsSettings(basePackage, entryParticipant, Path.of("docs"),
                Path.of("src/test/resources/flow-scenarios"), "Upstream");
    }

    JavaClasses importClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
    }
}
