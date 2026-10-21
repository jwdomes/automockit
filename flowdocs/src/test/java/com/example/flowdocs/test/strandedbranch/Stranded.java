package com.example.flowdocs.test.strandedbranch;

import com.example.flowdocs.FlowBranch;
import com.example.flowdocs.FlowStep;

/** A @FlowBranch on a method that isn't a @FlowStep, which nothing would observe. */
public class Stranded {

    @FlowBranch(question = "Go?", participant = "APP", gates = "Go")
    public void decide(boolean go) {
        if (go) {
            go();
        }
    }

    @FlowStep(action = "Go", participant = "APP")
    public void go() {
    }
}
