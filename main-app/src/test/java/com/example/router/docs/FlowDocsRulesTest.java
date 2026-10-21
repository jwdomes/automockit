package com.example.router.docs;

import com.example.flowdocs.test.FlowDocsRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

@AnalyzeClasses(packages = "com.example.router", importOptions = ImportOption.DoNotIncludeTests.class)
class FlowDocsRulesTest {

    @ArchTest
    static final ArchTests flowDocs = ArchTests.in(FlowDocsRules.class);
}
