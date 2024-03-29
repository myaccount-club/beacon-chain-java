package org.ethereum.beacon.consensus.hasher;

import org.ethereum.beacon.crypto.Hashes;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.BytesValue;

/**
 * An interface of object hasher.
 *
 * @param <H> a hash type.
 * @see SSZObjectHasher
 */
public interface ObjectHasher<H extends BytesValue> {

  static ObjectHasher<Hash32> createSSZOverKeccak256() {
    return SSZObjectHasher.create(Hashes::keccak256);
  }

  /**
   * Calculates hash of given object.
   *
   * @param input an object of any type.
   * @return calculated hash.
   */
  H getHash(Object input);

  /**
   * Calculates hash of object truncated from current to `field` (not included).
   *
   * @param input an object of any type.
   * @param field this and all subsequent fields will not be included in hashed object
   * @return calculated hash.
   */
  H getHashTruncate(Object input, String field);
}
