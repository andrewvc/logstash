package com.logstash.codegen;

import com.logstash.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by andrewvc on 6/10/16.
 */
public class CodeGenerator extends ClassLoader {
    public MultiExpression multiExpression(List<IExpression> expressions) {
        return new MultiExpression(expressions);
    }

    public class MultiExpression implements IExpression {
        private final List<IExpression> expressions;

        public MultiExpression(List<IExpression> expressions) {
            this.expressions = expressions;
        }

        @Override
        public List<Event> executeMulti(List<Event> events) throws Exception {
            List<Event> out = new ArrayList<>(1);
            for (Event event : events) {
                out.addAll(execute(event));
            }
            return out;
        }

        @Override
        public List<Event> execute(Event event) throws Exception {
            List<Event> out = new ArrayList<>(1);
            for (IExpression expression : expressions) {
                out.addAll(expression.execute(event));
            }
            return out;
        }

        public String toString() {
            StringBuilder s = new StringBuilder();
            s.append("(DO ");
            for (IExpression e : expressions) {
                s.append(e);
                s.append(" ");
            }
            s.append(")");

            return s.toString();
        }
    }

    public ObjectValue objectValue(Object v) {
        return new ObjectValue(v);
    }

    public class ObjectValue extends AbstractValueExpression {
        public final Object value;

        public ObjectValue(Object value) {
            this.value = value;
        }

        @Override
        public Object get(Event e) {
            return this.value;
        }

        public boolean equals(Object o) {
            if (o == null) {
                return this.value == null;
            }

            if (o instanceof ObjectValue) {
                return o.equals(this.value);
            } else {
                return this.value.equals(o);
            }
        }

        public String toString() {
            return "ObjectValue(" + this.value + ")";
        }
    }

    public Get get(String field) {
        return new Get(field);
    }

    public class Get extends AbstractValueExpression {
        public final String field;

        public Get(String field) {
            this.field = field;
        }

        @Override
        public Object get(Event e) {
            return e.getField(this.field);
        }

        public String toString() {
            return "(GET " +  this.field + ")";
        }
    }

    public Eq eq(IValueExpression ival, IValueExpression rval) {
        return new Eq(ival, rval);
    }

    public class Eq implements IBooleanExpression {
        public final IValueExpression lval;
        public final IValueExpression rval;

        public Eq(IValueExpression lval, IValueExpression rval) {
            this.lval = lval;
            this.rval = rval;
        }

        @Override
        public boolean execute(Event e) {
            Object lv = lval.get(e);
            Object rv = rval.get(e);

            if (lv == null) {
                // Return true if both are null, otherwise nothing
                return rv == null;
            } else {
                return lv.equals(rv);
            }
        }

        public String toString() {
            return "(== " + lval.toString() + " " + rval.toString() + ")";
        }
    }

    public ValToBool valToBool(IValueExpression ive) {
        return new ValToBool(ive);
    }

    public class ValToBool implements IBooleanExpression {
        private final IValueExpression valueExpression;

        public ValToBool(IValueExpression valueExpression) {
            this.valueExpression = valueExpression;
        }

        @Override
        public boolean execute(Event e) {
            Object value = valueExpression.get(e);

            if (value == null) return false;

            if (value instanceof Boolean) {
                return (Boolean) value;
            }

            return true;
        }

        public String toString() {
            return "(bool " + valueExpression + ")";
        }
    }

    public Noop noop() {
        return new Noop();
    }

    public class Noop implements IExpression {
        @Override
        public List<Event> executeMulti(List<Event> events) throws Exception {
            return events;
        }

        @Override
        public List<Event> execute(Event event) throws Exception {
            ArrayList<Event> a = new ArrayList<>();
            a.add(event);
            return a;
        }

        public String toString() {
            return "(NOOP)";
        }
    }

    public If ifexpr (IBooleanExpression condition, IExpression ifTrue, IExpression ifFalse) {
        return new If(condition, ifTrue, ifFalse);
    }

    public class If implements IExpression {
        public final IBooleanExpression condition;
        public final IExpression ifTrue;
        public final IExpression ifFalse;

        public If(IBooleanExpression condition, IExpression ifTrue, IExpression ifFalse) {
            this.condition = condition;
            this.ifTrue = ifTrue;
            this.ifFalse = ifFalse;
        }

        public String toString() {
            return "(IF " + condition.toString() + " " + ifTrue.toString() + " " + ifFalse.toString() + ")";
        }

        @Override
        public List<Event> executeMulti(List<Event> events) throws Exception {
            List<Event> out = new ArrayList<Event>();
            for (Event e : events) {
                if (condition.execute(e)) {
                    out.addAll(ifTrue.execute(e));
                } else {
                    out.addAll(ifFalse.execute(e));
                }
            }
            return out;
        }

        @Override
        public List<Event> execute(Event event) throws Exception {
            List<Event> out = new ArrayList<Event>(1);
            if (condition.execute(event)) {
                out.addAll(ifTrue.execute(event));
            } else {
                out.addAll(ifFalse.execute(event));
            }
            return out;
        }
    }

    public CodeGenerator() {

    }

    public void run() {

    }

    public static void main(String[] args) {
        (new CodeGenerator()).run();
    }


}
