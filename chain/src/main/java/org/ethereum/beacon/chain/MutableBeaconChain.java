package org.ethereum.beacon.chain;

import org.ethereum.beacon.core.BeaconBlock;

public interface MutableBeaconChain extends BeaconChain {

  /**
   * Inserts new block into a chain.
   *
   * @param block a block.
   * @return whether a block was inserted or not.
   */
  boolean insert(BeaconBlock block);
}
