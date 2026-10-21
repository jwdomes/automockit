package com.example.flowdocs.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class FlowGraphTest {

    private static final String FIXTURE = "com.example.flowdocs.test.fixture";

    @Test
    void chainsConditionDiamondsAndLinksErrorHandlers() {
        JavaClasses classes = new ClassFileImporter().importPackages(FIXTURE);

        String mermaid = new FlowGraph(classes, FIXTURE, FlowParticipants.labels(classes)).mermaid();

        assertThat(mermaid).isEqualTo("""
                flowchart TD
                    subgraph APP["App"]
                        subgraph first["First?"]
                            first__not_empty{"not empty?"}
                            first__short{"short?"}
                        end
                        map_failure["Map failure"]
                        orchestrate["Orchestrate"]
                        receive["Receive"]
                        second{"Second?"}
                    end
                    subgraph BACKEND["Backend"]
                        call_one["Call one"]
                        call_two["Call two"]
                    end
                    call_one --> second
                    first__not_empty -->|no| second
                    first__not_empty -->|yes| first__short
                    first__short -->|no| second
                    first__short -->|yes| call_one
                    orchestrate --> first__not_empty
                    receive --> orchestrate
                    receive -.->|"IllegalStateException"| map_failure
                    second -->|yes| call_two
                """);
    }
}
