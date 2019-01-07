package org.ethereum.beacon.chain.storage.impl;

import org.ethereum.beacon.chain.storage.BeaconBlockStorage;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.db.source.DataSource;
import org.ethereum.beacon.db.source.HoleyList;
import tech.pegasys.artemis.ethereum.core.Hash32;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.singletonList;

public class BeaconBlockStorageImpl implements BeaconBlockStorage {

  public static class SlotBlocks {
    public static final int NO_CANONICAL = -1;

    private final List<Hash32> blockHashes;
    // -1: no canonical block
    private final int canonicalIndex;

    SlotBlocks(List<Hash32> blockHashes, int canonicalIndex) {
      this.blockHashes = blockHashes;
      this.canonicalIndex = canonicalIndex;
    }

    public List<Hash32> getBlockHashes() {
      return blockHashes;
    }

    public int getCanonicalIndex() {
      return canonicalIndex;
    }

    public Optional<Hash32> getCanonicalHash() {
      return canonicalIndex == NO_CANONICAL ? Optional.empty() :
          Optional.of(getBlockHashes().get(getCanonicalIndex()));
    }

    public SlotBlocks addBlock(Hash32 newBlock) {
      ArrayList<Hash32> blocks = new ArrayList<>(getBlockHashes());
      blocks.add(newBlock);
      return new SlotBlocks(blocks, canonicalIndex);
    }

    public SlotBlocks setCanonicalHash(Hash32 newCanonicalHash) {
      int idx = blockHashes.indexOf(newCanonicalHash);
      return setCanonicalIndex(idx < 0 ? NO_CANONICAL : idx);
    }

    public SlotBlocks setCanonicalIndex(int newCanonicalIdx) {
      return newCanonicalIdx == getCanonicalIndex() ? this :
          new SlotBlocks(getBlockHashes(), newCanonicalIdx);
    }

    @Override
    public String toString() {
      return "SlotBlocks{" +
          "blockHashes=" + blockHashes +
          ", canonicalIndex=" + canonicalIndex +
          '}';
    }
  }

  private final DataSource<Hash32, BeaconBlock> rawBlocks;
  private final HoleyList<SlotBlocks> blockIndex;

  public BeaconBlockStorageImpl(DataSource<Hash32, BeaconBlock> rawBlocks,
                                HoleyList<SlotBlocks> blockIndex) {
    this.rawBlocks = rawBlocks;
    this.blockIndex = blockIndex;
  }

  @Override
  public Hash32 getCanonicalHead() {
    for (long i = getMaxSlot(); i >= 0; i--) {
      Optional<Hash32> canonicalHash = blockIndex.get(i)
          .flatMap(SlotBlocks::getCanonicalHash);
      if (canonicalHash.isPresent()) {
        return canonicalHash.get();
      }
    }
    throw new IllegalStateException("At least genesis head should exist.");
  }

  @Override
  public void reorgTo(Hash32 newCanonical) {
    for (long slot = getMaxSlot(); slot >= 0; slot--) {
      Optional<SlotBlocks> slotBlocks = blockIndex.get(slot);
      if (slotBlocks.isPresent()) {
        Optional<Hash32> curCanonical = slotBlocks.get().getCanonicalHash();
        if (curCanonical.isPresent() && newCanonical.equals(curCanonical.get())) {
          break;
        }
        SlotBlocks slotBlocksNew = slotBlocks.get().setCanonicalHash(newCanonical);
        blockIndex.put(slot, slotBlocksNew);
        Optional<Hash32> curCanonicalNew = slotBlocksNew.getCanonicalHash();
        if (curCanonicalNew.isPresent() && newCanonical.equals(curCanonicalNew.get())) {
          BeaconBlock block = get(newCanonical)
              .orElseThrow(() -> new RuntimeException("Can't reorg to missing block"));
          newCanonical = block.getParentRoot();
        }
      }
    }
  }

  @Override
  public long getMaxSlot() {
    return blockIndex.size() - 1;
  }

  @Override
  public List<Hash32> getSlotBlocks(long slot) {
    return blockIndex.get(slot)
        .map(SlotBlocks::getBlockHashes)
        .orElse(Collections.emptyList());
  }

  @Override
  public Optional<Hash32> getSlotCanonicalBlock(long slot) {
    return blockIndex.get(slot).flatMap(SlotBlocks::getCanonicalHash);
  }

  @Override
  public Optional<BeaconBlock> get(@Nonnull Hash32 key) {
    return rawBlocks.get(key);
  }

  @Override
  public void put(@Nonnull Hash32 key, @Nonnull BeaconBlock value) {
    boolean genesisInit = isEmpty();
    rawBlocks.put(key, value);
    blockIndex.update(value.getSlot().getValue(),
        blocks -> blocks.addBlock(value.getHash()),
        new SlotBlocks(singletonList(value.getHash()), 0));
    if (genesisInit) {
      reorgTo(key);
    }
  }

  @Override
  public void remove(@Nonnull Hash32 key) {
    Optional<BeaconBlock> block = rawBlocks.get(key);
    if (block.isPresent()) {
      rawBlocks.remove(key);
      SlotBlocks slotBlocks = blockIndex.get(block.get().getSlot().getValue()).get();
      int idx = 0;
      for (; idx < slotBlocks.getBlockHashes().size(); idx++) {
        if (slotBlocks.getBlockHashes().get(idx).equals(key)) {
          break;
        }
      }
      if (slotBlocks.getCanonicalIndex() == idx) {
        throw new RuntimeException("Attempt to remove canonical block: " + block.get().getSlot() + ": " + key);
      }
      int newIdx = slotBlocks.getCanonicalIndex() - (slotBlocks.getCanonicalIndex() < idx ? 0 : 1);
      ArrayList<Hash32> newBlocks = new ArrayList<>(slotBlocks.getBlockHashes());
      newBlocks.remove(idx);
      blockIndex.put(block.get().getSlot().getValue(), new SlotBlocks(newBlocks, newIdx));
    }
  }

  @Override
  public void flush() {
    // nothing to be done here. No cached data in this implementation
  }
}
