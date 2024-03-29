package org.ethereum.beacon.consensus.hasher;

import org.ethereum.beacon.core.types.Hashable;
import org.ethereum.beacon.ssz.SSZHashSerializer;
import org.ethereum.beacon.ssz.SSZHashSerializers;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.BytesValue;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * An object hasher implementation using Tree Hash algorithm described in the spec.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/dev/specs/simple-serialize.md#tree-hash">Tree
 *     Hash</a> in the spec.
 */
public class SSZObjectHasher implements ObjectHasher<Hash32> {

  private static final int SSZ_SCHEMES_CACHE_CAPACITY = 128;
  private final SSZHashSerializer sszHashSerializer;

  SSZObjectHasher(SSZHashSerializer sszHashSerializer) {
    this.sszHashSerializer = sszHashSerializer;
  }

  public static SSZObjectHasher create(Function<BytesValue, Hash32> hashFunction) {
    SSZHashSerializer sszHashSerializer =
        SSZHashSerializers.createWithBeaconChainTypes(hashFunction, true, SSZ_SCHEMES_CACHE_CAPACITY);
    return new SSZObjectHasher(sszHashSerializer);
  }

  @Override
  public Hash32 getHash(Object input) {
    if (input instanceof Hashable<?>) {
      Optional<Hash32> hashOptional = ((Hashable<Hash32>) input).getHash();
      if (hashOptional.isPresent()) {
        return hashOptional.get();
      }
    }

    Hash32 hash = Hash32.wrap(Bytes32.wrap(sszHashSerializer.hash(input)));
    if (input instanceof Hashable<?>) {
      ((Hashable<Hash32>) input).setHash(hash);
    }

      return hash;
  }

  @Override
  public Hash32 getHashTruncate(Object input, String field) {
    if (input instanceof List) {
      throw new RuntimeException("Lists are not supported in truncated hash");
    } else {
      return Hash32.wrap(Bytes32.wrap(sszHashSerializer.hashTruncate(input, input.getClass(), field)));
    }
  }
}
