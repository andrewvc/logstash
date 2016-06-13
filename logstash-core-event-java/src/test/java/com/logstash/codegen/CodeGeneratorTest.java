package com.logstash.codegen;

import com.logstash.Event;
import org.junit.Test;

import static org.junit.Assert.*;

import static net.javacrumbs.jsonunit.JsonAssert.assertJsonEquals;


public class CodeGeneratorTest {
//    public static class RecordingRunnable implements Runnable {
//        public boolean ran = false;
//
//        @Override
//        public void run() {
//            this.ran = true;
//        }
//    }
//
//    public CodeGenerator.Plugin runnableExpression(CodeGenerator cg) {
//        return cg.plugin(new RecordingRunnable());
//    }
//
//
//    @Test
//    public void testBasicPipeline() throws Exception {
//        Event e = new Event();
//        e.setField("[foo]", "bar");
//
//        CodeGenerator cg = new CodeGenerator();
//
//        CodeGenerator.Eq eq = cg.eq(cg.get("[foo]"), cg.objectValue("bar"));
//
//
//        CodeGenerator.Plugin rtrue = runnableExpression(cg);
//        CodeGenerator.Plugin rfalse = runnableExpression(cg);
//
//        cg.ifexpr(eq, rtrue, rfalse).executeMulti(e);
//
//        boolean trueRan = ((RecordingRunnable) rtrue.runnable).ran;
//        boolean falseRan = ((RecordingRunnable) rfalse.runnable).ran;
//
//        assertTrue(trueRan);
//        assertFalse(falseRan);
//    }
}
