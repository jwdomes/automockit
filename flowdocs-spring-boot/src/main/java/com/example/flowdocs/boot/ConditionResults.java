package com.example.flowdocs.boot;

import java.util.List;

/** Each condition of a decision and whether it held; stored in the observation context. */
public record ConditionResults(List<ConditionResult> results) {

    public record ConditionResult(String label, boolean held) {
    }
}
