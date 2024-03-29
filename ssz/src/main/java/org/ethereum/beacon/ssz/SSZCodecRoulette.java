package org.ethereum.beacon.ssz;

import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.BytesSSZReaderProxy;
import net.consensys.cava.ssz.SSZException;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import org.ethereum.beacon.ssz.type.SSZCodec;
import org.ethereum.beacon.ssz.type.SubclassCodec;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.ethereum.beacon.ssz.SSZSerializer.LENGTH_PREFIX_BYTE_SIZE;

/**
 * Implementation of {@link SSZCodec} which handles unknown classes recursively, passing it to input
 * {@link BytesSerializer} instance, and prioritizes codec supported class over supported type.
 *
 * <p>So, if handled field class has only one codec registered for, it will be used, even if field
 * have text marking that matches two codecs. But if several codecs are registered for one class and
 * it has {@link org.ethereum.beacon.ssz.SSZSchemeBuilder.SSZScheme.SSZField#extraType} marking, it
 * will be handled by codec supporting this class and type.
 */
public class SSZCodecRoulette implements SSZCodecResolver {
  private Map<Class, List<CodecEntry>> registeredClassHandlers = new HashMap<>();

  public Consumer<Triplet<Object, OutputStream, BytesSerializer>> resolveEncodeFunction(
      SSZSchemeBuilder.SSZScheme.SSZField field) {
    SSZCodec encoder = resolveCodec(field);

    if (field.multipleType.equals(SSZSchemeBuilder.SSZScheme.MultipleType.NONE)) {
      if (encoder != null) {
        return objects -> {
          Object value = objects.getValue0();
          OutputStream res = objects.getValue1();
          encoder.encode(value, field, res);
        };
      } else {
        return objects -> {
          Object value = objects.getValue0();
          OutputStream res = objects.getValue1();
          BytesSerializer sszSerializer = objects.getValue2();
          encodeContainer(value, field, res, sszSerializer);
        };
      }
    } else if (field.multipleType.equals(SSZSchemeBuilder.SSZScheme.MultipleType.LIST)) {
      if (encoder != null) {
        return objects -> {
          Object value = objects.getValue0();
          OutputStream res = objects.getValue1();
          encoder.encodeList((List<Object>) value, field, res);
        };
      } else {
        return objects -> {
          Object value = objects.getValue0();
          OutputStream res = objects.getValue1();
          BytesSerializer sszSerializer = objects.getValue2();
          encodeContainerList((List<Object>) value, field, res, sszSerializer);
        };
      }
    } else if (field.multipleType.equals(SSZSchemeBuilder.SSZScheme.MultipleType.ARRAY)) {
      if (encoder != null) {
        return objects -> {
          Object value = objects.getValue0();
          OutputStream res = objects.getValue1();
          encoder.encodeArray((Object[]) value, field, res);
        };
      } else {
        return objects -> {
          Object value = objects.getValue0();
          OutputStream res = objects.getValue1();
          BytesSerializer sszSerializer = objects.getValue2();
          encodeContainerList(Arrays.asList((Object[]) value), field, res, sszSerializer);
        };
      }
    }

    throw new SSZSchemeException(
        String.format("Function not resolved for encoding field %s", field));
  }

  private void encodeContainer(
      Object value,
      SSZSchemeBuilder.SSZScheme.SSZField field,
      OutputStream result,
      BytesSerializer sszSerializer) {
    byte[] data = sszSerializer.encode(value, field.type);

    if (!field.notAContainer) {
      try {
        // Prepend data with its length
        result.write(net.consensys.cava.ssz.SSZ.encodeInt32(data.length).toArrayUnsafe());
      } catch (IOException e) {
        throw new SSZException("Failed to write data length to stream", e);
      }
    }

    try {
      result.write(data);
    } catch (IOException e) {
      String error =
          String.format("Failed to write container from field \"%s\" to stream", field.name);
      throw new SSZException(error, e);
    }
  }

  private void encodeContainerList(
      List<Object> value,
      SSZSchemeBuilder.SSZScheme.SSZField field,
      OutputStream result,
      BytesSerializer sszSerializer) {
    try {
      Bytes[] data = packContainerList(value, field, sszSerializer);
      result.write(net.consensys.cava.ssz.SSZ.encodeBytesList(data).toArrayUnsafe());
    } catch (IOException ex) {
      String error = String.format("Failed to write data from field \"%s\" to stream", field.name);
      throw new SSZException(error, ex);
    }
  }

  private Bytes[] packContainerList(
      List<Object> values, SSZSchemeBuilder.SSZScheme.SSZField field, BytesSerializer sszSerializer) {
    Bytes[] res = new Bytes[values.size()];
    for (int i = 0; i < values.size(); ++i) {
      byte[] data = sszSerializer.encode(values.get(i), field.type);
      Bytes curValue;
      if (field.notAContainer) {
        curValue = Bytes.of(data).slice(4);
      } else {
        curValue = Bytes.of(data);
      }
      res[i] = curValue;
    }

    return res;
  }

  public Function<Pair<BytesSSZReaderProxy, BytesSerializer>, Object> resolveDecodeFunction(
      SSZSchemeBuilder.SSZScheme.SSZField field) {
    SSZCodec decoder = resolveCodec(field);
    if (field.multipleType.equals(SSZSchemeBuilder.SSZScheme.MultipleType.NONE)) {
      if (decoder != null) {
        return objects -> {
          BytesSSZReaderProxy reader = objects.getValue0();
          return decoder.decode(field, reader);
        };
      } else {
        return objects -> {
          BytesSSZReaderProxy reader = objects.getValue0();
          BytesSerializer sszSerializer = objects.getValue1();
          return decodeContainer(field, reader, sszSerializer);
        };
      }
    } else if (field.multipleType.equals(SSZSchemeBuilder.SSZScheme.MultipleType.LIST)) {
      if (decoder != null) {
        return objects -> {
          BytesSSZReaderProxy reader = objects.getValue0();
          return decoder.decodeList(field, reader);
        };
      } else {
        return objects -> {
          BytesSSZReaderProxy reader = objects.getValue0();
          BytesSerializer sszSerializer = objects.getValue1();
          return decodeContainerList(field, reader, sszSerializer);
        };
      }
    } else if (field.multipleType.equals(SSZSchemeBuilder.SSZScheme.MultipleType.ARRAY)) {
      return objects -> {
        Object[] uncastedResult;
        BytesSSZReaderProxy reader = objects.getValue0();
        BytesSerializer sszSerializer = objects.getValue1();
        if (decoder != null) {
          uncastedResult = decoder.decodeArray(field, reader);
        } else {
          List<Object> list = decodeContainerList(field, reader, sszSerializer);
          uncastedResult = list.toArray();
        }
        Object[] res = (Object[]) Array.newInstance(field.type, uncastedResult.length);
        System.arraycopy(uncastedResult, 0, res, 0, uncastedResult.length);

        return res;
      };
    }

    throw new SSZSchemeException(
        String.format("Function not resolved for decoding field %s", field));
  }

  private Object decodeContainer(
      SSZSchemeBuilder.SSZScheme.SSZField field,
      BytesSSZReaderProxy reader,
      BytesSerializer sszSerializer) {
    return decodeContainerImpl(field, reader, sszSerializer).getValue0();
  }

  private Pair<Object, Integer> decodeContainerImpl(
      SSZSchemeBuilder.SSZScheme.SSZField field,
      BytesSSZReaderProxy reader,
      BytesSerializer sszSerializer) {
    Bytes data = reader.readBytes();
    int dataSize = data.size();

    if (field.notAContainer) {
      Bytes lengthPrefix = net.consensys.cava.ssz.SSZ.encodeUInt32(dataSize);
      byte[] container = Bytes.concatenate(lengthPrefix, data).toArrayUnsafe();
      return new Pair<>(sszSerializer.decode(container, field.type), dataSize);
    } else {
      return new Pair<>(
          sszSerializer.decode(data.toArrayUnsafe(), field.type),
          dataSize + LENGTH_PREFIX_BYTE_SIZE);
    }
  }

  private List<Object> decodeContainerList(
      SSZSchemeBuilder.SSZScheme.SSZField field,
      BytesSSZReaderProxy reader,
      BytesSerializer sszSerializer) {
    int remainingData = reader.readInt32();
    List<Object> res = new ArrayList<>();
    while (remainingData > 0) {
      Pair<Object, Integer> decodeRes = decodeContainerImpl(field, reader, sszSerializer);
      res.add(decodeRes.getValue0());
      remainingData -= decodeRes.getValue1();
    }
    return res;
  }

  private SSZCodec resolveCodec(SSZSchemeBuilder.SSZScheme.SSZField field) {
    Class<?> type = field.type;
    boolean subclassCodec = false;
    if (!SubclassCodec.getSerializableClass(type).equals(type)) {
      type = SubclassCodec.getSerializableClass(type);
      subclassCodec = true;
    }

    SSZCodec codec = null;
    if (registeredClassHandlers.containsKey(type)) {
      List<CodecEntry> codecs = registeredClassHandlers.get(type);
      if (field.extraType == null || field.extraType.isEmpty()) {
        codec = codecs.get(0).codec;
      } else {
        for (CodecEntry codecEntry : codecs) {
          if (codecEntry.types.contains(field.extraType)) {
            codec = codecEntry.codec;
            break;
          }
        }
      }
    }

    if (codec != null && subclassCodec) {
      codec = new SubclassCodec(codec);
    }

    return codec;
  }

  /**
   * Registers codecs to be used for
   *
   * @param classes Classes, resolving is performed with class at first
   * @param types Text type, one class could be interpreted to several types. Several codecs could
   *     handle one class. Empty/null type is occupied by first class codec. Type is looked up in
   *     codecs one by one.
   * @param codec Codec able to encode/decode of specific class/types
   */
  public void registerCodec(Set<Class> classes, Set<String> types, SSZCodec codec) {
    for (Class clazz : classes) {
      if (registeredClassHandlers.get(clazz) != null) {
        registeredClassHandlers.get(clazz).add(new CodecEntry(codec, types));
      } else {
        registeredClassHandlers.put(
            clazz, new ArrayList<>(Collections.singletonList(new CodecEntry(codec, types))));
      }
    }
  }

  class CodecEntry {
    SSZCodec codec;
    Set<String> types;

    public CodecEntry(SSZCodec codec, Set<String> types) {
      this.codec = codec;
      this.types = types;
    }
  }
}
