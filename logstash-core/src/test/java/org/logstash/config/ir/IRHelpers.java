package org.logstash.config.ir;

import org.hamcrest.MatcherAssert;
import org.logstash.config.ir.graph.Edge;
import org.logstash.config.ir.graph.Graph;

import java.util.stream.Stream;

import static org.logstash.config.ir.DSL.*;
import static org.logstash.config.ir.PluginDefinition.Type.*;

/**
 * Created by andrewvc on 9/19/16.
 */
public class IRHelpers {
    public static void assertSyntaxEquals(ISourceComponent left, ISourceComponent right) {
        String message = String.format("Expected '%s' to equal '%s'", left, right);
        MatcherAssert.assertThat(message, left.sourceComponentEquals(right));
    }

    public static void assertGraphEquals(Graph left, Graph right) {
        String message = String.format("Expected \n'%s'\n to equal \n'%s'\n%s", left, right, left.diff(right));
        MatcherAssert.assertThat(message, left.sourceComponentEquals(right));
    }


    public static Pipeline samplePipeline() throws InvalidIRException {
        Graph inputSection = iComposeParallel(iPlugin(INPUT, "generator"), iPlugin(INPUT, "stdin")).toGraph();
        Graph filterSection = iIf(eEq(eEventValue("[foo]"), eEventValue("[bar]")),
                                    iPlugin(FILTER, "grok"),
                                    iPlugin(FILTER, "kv")).toGraph();
        Graph outputSection = iIf(eGt(eEventValue("[baz]"), eValue(1000)),
                                    iComposeParallel(
                                            iPlugin(OUTPUT, "s3"),
                                            iPlugin(OUTPUT, "elasticsearch")),
                                    iPlugin(OUTPUT, "stdout")).toGraph();

        return new Pipeline(inputSection, filterSection, outputSection);
    }
}
