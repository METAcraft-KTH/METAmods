package se.datasektionen.mc.simplecustomfeatures;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import it.unimi.dsi.fastutil.bytes.ByteList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongList;

import java.nio.ByteBuffer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * {@link JavaOps} but it is better at handling number lists.
 */
public class LenientJavaOps implements DynamicOps<Object> {

	/**
	 * {@link JavaOps#INSTANCE} but it is better at handling number lists.
	 */
	public static final LenientJavaOps INSTANCE = new LenientJavaOps();

	private final JavaOps ops = JavaOps.INSTANCE;

	private LenientJavaOps() {

	}

	@Override
	public Object empty() {
		return ops.empty();
	}

	@Override
	public <U> U convertTo(DynamicOps<U> outOps, Object input) {
		return ops.convertTo(outOps, input);
	}

	@Override
	public DataResult<Number> getNumberValue(Object input) {
		return ops.getNumberValue(input);
	}

	@Override
	public Object createNumeric(Number i) {
		return ops.createNumeric(i);
	}

	@Override
	public DataResult<String> getStringValue(Object input) {
		return ops.getStringValue(input);
	}

	@Override
	public Object createString(String value) {
		return ops.createString(value);
	}

	@Override
	public DataResult<Object> mergeToList(Object list, Object value) {
		return ops.mergeToList(list, value);
	}

	@Override
	public DataResult<Object> mergeToMap(Object map, Object key, Object value) {
		return ops.mergeToMap(map, key, value);
	}

	@Override
	public DataResult<Stream<Pair<Object, Object>>> getMapValues(Object input) {
		return ops.getMapValues(input);
	}

	@Override
	public Object createMap(Stream<Pair<Object, Object>> map) {
		return ops.createMap(map);
	}

	@Override
	public DataResult<Stream<Object>> getStream(Object input) {
		return ops.getStream(input);
	}

	@Override
	public Object createList(Stream<Object> input) {
		return ops.createList(input);
	}

	@Override
	public Object remove(Object input, String key) {
		return ops.remove(input, key);
	}

	@Override
	public DataResult<IntStream> getIntStream(final Object input) {
		if (input instanceof final IntList value) {
			return DataResult.success(value.intStream());
		}
		return DynamicOps.super.getIntStream(input);
	}

	@Override
	public Object createIntList(final IntStream input) {
		return ops.createIntList(input);
	}

	@Override
	public DataResult<ByteBuffer> getByteBuffer(final Object input) {
		if (input instanceof final ByteList value) {
			return DataResult.success(ByteBuffer.wrap(value.toByteArray()));
		}
		return DynamicOps.super.getByteBuffer(input);
	}

	@Override
	public Object createByteList(final ByteBuffer input) {
		return ops.createByteList(input);
	}

	@Override
	public DataResult<LongStream> getLongStream(final Object input) {
		if (input instanceof final LongList value) {
			return DataResult.success(value.longStream());
		}
		return DynamicOps.super.getLongStream(input);
	}

	@Override
	public Object createLongList(final LongStream input) {
		return ops.createLongList(input);
	}

	@Override
	public DataResult<Boolean> getBooleanValue(final Object input) {
		if (input instanceof final Boolean value) {
			return ops.getBooleanValue(value);
		}
		return DynamicOps.super.getBooleanValue(input);
	}

	@Override
	public Object createBoolean(final boolean value) {
		return ops.createBoolean(value);
	}

	@Override
	public Object createByte(final byte value) {
		return ops.createByte(value);
	}

	@Override
	public Object createShort(final short value) {
		return ops.createShort(value);
	}

	@Override
	public Object createInt(final int value) {
		return ops.createInt(value);
	}

	@Override
	public Object createLong(final long value) {
		return ops.createLong(value);
	}

	@Override
	public Object createFloat(final float value) {
		return ops.createFloat(value);
	}

	@Override
	public Object createDouble(final double value) {
		return ops.createDouble(value);
	}

}
