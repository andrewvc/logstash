package org.logstash;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyBignum;
import org.jruby.RubyBoolean;
import org.jruby.RubyFixnum;
import org.jruby.RubyFloat;
import org.jruby.RubyHash;
import org.jruby.RubyNil;
import org.jruby.RubyString;
import org.jruby.RubySymbol;
import org.jruby.ext.bigdecimal.RubyBigDecimal;
import org.jruby.runtime.builtin.IRubyObject;
import org.logstash.ext.JrubyTimestampExtLibrary;

public final class Rubyfier {

    private static final Transformer IDENTITY = (runtime, input) -> (IRubyObject) input;

    private static final Transformer FLOAT_CONVERTER =
        (runtime, input) -> runtime.newFloat(((Number) input).doubleValue());

    private static final Transformer LONG_CONVERTER =
        (runtime, input) -> runtime.newFixnum(((Number) input).longValue());

    private static final Map<Class<?>, Transformer> CONVERTER_MAP = initConverters();

    private static final Map<Class<?>, Transformer> CLONER_MAP = initCloners();


    /**
     * Rubyfier.deep() is called by JrubyEventExtLibrary RubyEvent ruby_get_field,
     * ruby_remove, ruby_to_hash and ruby_to_hash_with_metadata.
     * When any value is added to the Event it should pass through Valuefier.convert.
     * Rubyfier.deep is the mechanism to pluck the Ruby value from a BiValue or convert a
     * ConvertedList and ConvertedMap back to RubyArray or RubyHash.
     * However, IRubyObjects and the RUby runtime do not belong in ConvertedMap or ConvertedList
     * so they are unconverted here.
     */
    private Rubyfier() {
    }

    public static IRubyObject deep(final Ruby runtime, final Object input) {
        if (input == null) {
            return runtime.getNil();
        }
        final Class<?> cls = input.getClass();
        final Transformer converter = CONVERTER_MAP.get(cls);
        if (converter != null) {
            return converter.convert(runtime, input);
        }
        return fallbackTransform(runtime, input, cls, CONVERTER_MAP);
    }

    public static IRubyObject deepClone(final Ruby runtime, final Object input) {
        if (input == null) {
            return runtime.getNil();
        }

        final Class<?> cls = input.getClass();
        final Transformer cloner = CLONER_MAP.get(cls);
        if (cloner != null) {
            return cloner.convert(runtime, input);
        }
        return fallbackTransform(runtime, input, cls, CLONER_MAP);
    }

    private static RubyArray deepList(final Ruby runtime, final Collection<?> list) {
        return deepListTransform(runtime, list, Rubyfier::deep);
    }

    private static RubyArray deepListClone(final Ruby runtime, final Collection<?> list) {
        return deepListTransform(runtime, list, Rubyfier::deepClone);
    }

    private static RubyArray deepListTransform(final Ruby runtime, final Collection<?> list, BiFunction<Ruby, Object, IRubyObject> fn) {
        final int length = list.size();
        final RubyArray array = runtime.newArray(length);
        for (final Object item : list) {
            array.add(fn.apply(runtime, item));
        }
        return array;
    }

    private static RubyHash deepMap(final Ruby runtime, final Map<?, ?> map) {
        return deepMapTransform(runtime, map, Rubyfier::deep);
    }

    /**
     * Returns a deep copy of event data
     * @param runtime
     * @param map
     * @return a new RubyHash of the data
     */
    private static RubyHash deepMapClone(final Ruby runtime, final Map<?, ?> map) {
        return deepMapTransform(runtime, map, Rubyfier::deepClone);
    }

    /**
     * Transforms every key of the map into a RubyHash, applying the given fn to each value
     * @param runtime
     * @param map
     * @param fn
     * @return RubyHash representing the value
     */
    private static RubyHash deepMapTransform(final Ruby runtime, final Map<?, ?> map, BiFunction<Ruby, Object, IRubyObject> fn) {
        final RubyHash hash = RubyHash.newHash(runtime);
        // Note: RubyHash.put calls JavaUtil.convertJavaToUsableRubyObject on keys and values
        map.forEach((key, value) -> hash.put(key, fn.apply(runtime, value)));
        return hash;
    }

    private static Map<Class<?>, Transformer> initConverters() {
        final Map<Class<?>, Transformer> converters =
            new ConcurrentHashMap<>(50, 0.2F, 1);
        converters.put(RubyString.class, IDENTITY);
        converters.put(RubyNil.class, IDENTITY);
        converters.put(RubySymbol.class, IDENTITY);
        converters.put(RubyBignum.class, IDENTITY);
        converters.put(RubyBigDecimal.class, IDENTITY);
        converters.put(RubyFloat.class, IDENTITY);
        converters.put(RubyFixnum.class, IDENTITY);
        converters.put(RubyBoolean.class, IDENTITY);
        converters.put(JrubyTimestampExtLibrary.RubyTimestamp.class, IDENTITY);
        converters.put(String.class, (runtime, input) -> runtime.newString((String) input));
        converters.put(Double.class, FLOAT_CONVERTER);
        converters.put(Float.class, FLOAT_CONVERTER);
        converters.put(Integer.class, LONG_CONVERTER);
        converters.put(
            BigInteger.class, (runtime, value) -> RubyBignum.newBignum(runtime, (BigInteger) value)
        );
        converters.put(
            BigDecimal.class, (runtime, value) -> new RubyBigDecimal(runtime, (BigDecimal) value)
        );
        converters.put(Long.class, LONG_CONVERTER);
        converters.put(Boolean.class, (runtime, input) -> runtime.newBoolean((Boolean) input));
        converters.put(Map.class, (runtime, input) -> deepMap(runtime, (Map<?, ?>) input));
        converters.put(
            Collection.class, (runtime, input) -> deepList(runtime, (Collection<?>) input)
        );
        converters.put(
            Timestamp.class,
            (runtime, input) -> JrubyTimestampExtLibrary.RubyTimestamp.newRubyTimestamp(
                runtime, (Timestamp) input
            )
        );
        return converters;
    }

    private static Map<Class<?>, Transformer> initCloners() {
        final Map<Class<?>, Rubyfier.Transformer> cloners =
            new ConcurrentHashMap<>(50, 0.2F, 1);
        cloners.put(RubyString.class, (ruby,s) -> ((RubyString) s).rbClone());
        cloners.put(RubyNil.class, IDENTITY);
        cloners.put(RubySymbol.class, IDENTITY);
        cloners.put(RubyBignum.class, IDENTITY);
        cloners.put(RubyBigDecimal.class, IDENTITY);
        cloners.put(RubyFloat.class, IDENTITY);
        cloners.put(RubyFixnum.class, IDENTITY);
        cloners.put(RubyBoolean.class, IDENTITY);
        cloners.put(JrubyTimestampExtLibrary.RubyTimestamp.class, IDENTITY);
        cloners.put(String.class, (runtime, input) -> runtime.newString((String) input));
        cloners.put(Double.class, FLOAT_CONVERTER);
        cloners.put(Float.class, FLOAT_CONVERTER);
        cloners.put(Integer.class, LONG_CONVERTER);
        cloners.put(
            BigInteger.class, (runtime, value) -> RubyBignum.newBignum(runtime, (BigInteger) value)
        );
        cloners.put(
            BigDecimal.class, (runtime, value) -> new RubyBigDecimal(runtime, (BigDecimal) value)
        );
        cloners.put(Long.class, LONG_CONVERTER);
        cloners.put(Boolean.class, (runtime, input) -> runtime.newBoolean((Boolean) input));
        cloners.put(Map.class, (runtime, input) -> deepMapClone(runtime, (Map<?, ?>) input));
        cloners.put(
            Collection.class, (runtime, input) -> deepListClone(runtime, (Collection<?>) input)
        );
        cloners.put(
            Timestamp.class,
            (runtime, input) -> JrubyTimestampExtLibrary.RubyTimestamp.newRubyTimestamp(
                runtime, (Timestamp) input
            )
        );
        return cloners;
    }


    /**
     * Same principle as {@link Valuefier#fallbackConvert(Object, Class)}.
     */
    private static IRubyObject fallbackTransform(final Ruby runtime, final Object o,
        final Class<?> cls, Map<Class<?>, Transformer> transformMap) {
        for (final Map.Entry<Class<?>, Transformer> entry : transformMap.entrySet()) {
            if (entry.getKey().isAssignableFrom(cls)) {
                final Transformer found = entry.getValue();
                transformMap.put(cls, found);
                return found.convert(runtime, o);
            }
        }
        throw new MissingConverterException(cls);
    }

    // Interface for either transforming or cloning a ruby object
    private interface Transformer {
        IRubyObject convert(Ruby runtime, Object input);
    }
}
