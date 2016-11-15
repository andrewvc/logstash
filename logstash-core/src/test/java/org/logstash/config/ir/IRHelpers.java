package org.logstash.config.ir;

import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.hamcrest.MatcherAssert;
import org.logstash.config.ir.expression.BooleanExpression;
import org.logstash.config.ir.expression.ValueExpression;
import org.logstash.config.ir.expression.unary.Truthy;
import org.logstash.config.ir.graph.Edge;
import org.logstash.config.ir.graph.Graph;
import org.logstash.config.ir.graph.Vertex;

import java.util.HashMap;
import java.util.UUID;

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

    public static Vertex testVertex() {
        String id = UUID.randomUUID().toString();
        return new Vertex() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public String typeString() {
                return "testVertex";
            }

            @Override
            public boolean sourceComponentEquals(ISourceComponent sourceComponent) {
                return this.equals(sourceComponent);
            }
        };
    }

    public static Edge testEdge() throws InvalidIRException {
        Vertex v1 = testVertex();
        Vertex v2 = testVertex();
        return new Edge(v1, v2) {};
    }

    public static Edge testEdge(Vertex from, Vertex to) throws InvalidIRException {
        return new Edge(from, to) {};
    }

    public static BooleanExpression testExpression() throws InvalidIRException {
        return new Truthy(null, new ValueExpression(null, 1));
    }

    public static SourceMetadata testMetadata() {
        return new SourceMetadata("/fake/file", 1, 2, "<fakesource>");
    }

    public static PluginDefinition testPluginDefinition() {
        return new PluginDefinition(PluginDefinition.Type.FILTER, "testDefinition", new HashMap<String, Object>());
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
