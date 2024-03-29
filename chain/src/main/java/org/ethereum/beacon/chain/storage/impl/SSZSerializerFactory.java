package org.ethereum.beacon.chain.storage.impl;

import java.util.function.Function;
import org.ethereum.beacon.ssz.Serializer;
import tech.pegasys.artemis.util.bytes.BytesValue;

public class SSZSerializerFactory implements SerializerFactory {

  private final Serializer serializer;

  public SSZSerializerFactory(Serializer serializer) {
    this.serializer = serializer;
  }

  @Override
  public <T> Function<BytesValue, T> getDeserializer(Class<T> objectClass) {
    return bytes -> serializer.decode(bytes, objectClass);
  }

  @Override
  public <T> Function<T, BytesValue> getSerializer(Class<T> objectClass) {
    return serializer::encode2;
  }
}
