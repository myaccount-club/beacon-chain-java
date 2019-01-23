package org.ethereum.beacon.chain;

import org.ethereum.beacon.chain.storage.BeaconBlockStorage;
import org.ethereum.beacon.chain.storage.BeaconChainStorage;
import org.ethereum.beacon.chain.storage.BeaconTuple;
import org.ethereum.beacon.chain.storage.BeaconTupleStorage;
import org.ethereum.beacon.consensus.HeadFunction;
import org.ethereum.beacon.consensus.SpecHelpers;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.operations.Attestation;
import org.ethereum.beacon.core.state.ValidatorRecord;
import reactor.core.publisher.Flux;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes48;
import tech.pegasys.artemis.util.uint.UInt24;
import tech.pegasys.artemis.util.uint.UInt64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The beacon chain fork choice rule is a hybrid that combines justification and finality with
 * Latest Message Driven (LMD) Greediest Heaviest Observed SubTree (GHOST). For more info check <a
 * href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#beacon-chain-fork-choice-rule">Beacon
 * chain fork choice rule</a>
 */
public class LMDGhostHeadFunction implements HeadFunction {

  private final BeaconBlockStorage blockStorage;
  private final BeaconTupleStorage tupleStorage;
  private final SpecHelpers specHelpers;
  private final int CHILDREN_SEARCH_LIMIT = Integer.MAX_VALUE;
  private final Map<Bytes48, Attestation> attestationCache = new HashMap<>();
  private final Map<UInt64, Set<Bytes48>> validatorSlotCache = new HashMap<>();
  private BeaconState state;

  public LMDGhostHeadFunction(
      BeaconChain beaconChain, BeaconChainStorage chainStorage, SpecHelpers specHelpers) {
    this.tupleStorage = chainStorage.getBeaconTupleStorage();
    this.blockStorage = chainStorage.getBeaconBlockStorage();
    this.specHelpers = specHelpers;
    Optional<Hash32> justifiedHash = blockStorage.getSlotJustifiedBlock(blockStorage.getMaxSlot());
    Optional<BeaconTuple> justifiedTuple =
        justifiedHash
            .map(tupleStorage::get)
            .orElseThrow(() -> new RuntimeException("No justified head found"));
    this.state =
        justifiedTuple
            .map(BeaconTuple::getState)
            .orElseThrow(() -> new RuntimeException("No justified tuple found"));
    Flux.from(beaconChain.getBlockStatesStream())
        .doOnNext(beaconTuple -> state = beaconTuple.getState())
        .subscribe();
  }

  @Override
  public BeaconBlock getHead() {
    Optional<Hash32> justifiedHash = blockStorage.getSlotJustifiedBlock(blockStorage.getMaxSlot());
    Optional<BeaconTuple> justifiedTuple =
        justifiedHash
            .map(tupleStorage::get)
            .orElseThrow(() -> new RuntimeException("No justified head found"));
    Function<Hash32, List<BeaconBlock>> getChildrenBlocks =
        (hash) -> blockStorage.getChildren(hash, CHILDREN_SEARCH_LIMIT);
    BeaconBlock newHead =
        justifiedTuple
            .map(
                (BeaconTuple startBlock) ->
                    specHelpers.lmd_ghost(
                        startBlock.getBlock(),
                        startBlock.getState(),
                        blockStorage::get,
                        getChildrenBlocks,
                        this::get_latest_attestation))
            .orElseThrow(() -> new RuntimeException("No justified head found"));

    // Let justified_head be the descendant of finalized_head with the highest slot number that has
    // been justified for at least EPOCH_LENGTH slots. (A block B is justified if there is a
    // descendant of B in store the processing of which sets B as justified.)
    if (newHead
            .getSlot()
            .minus(justifiedTuple.get().getBlock().getSlot())
            .compareTo(specHelpers.getChainSpec().getEpochLength())
        >= 0) {
      blockStorage.addJustifiedHash(newHead.getHash());
      blockStorage.addFinalizedHash(justifiedHash.get());
    }

    return newHead;
  }

  /**
   * Let get_latest_attestation(store, validator) be the attestation with the highest slot number in
   * store from validator. If several such attestations exist, use the one the validator v observed
   * first.
   */
  private Attestation get_latest_attestation(ValidatorRecord validatorRecord) {
    return attestationCache.get(validatorRecord.getPubKey());
  }

  /**
   * This should be implemented via some kind of subscription we should be subscribed to all
   * verified attestations, even those not included in blocks yet
   */
  public void addAttestation(Attestation attestation) {
    List<UInt24> participants =
        specHelpers.get_attestation_participants(
            state, attestation.getData(), attestation.getParticipationBitfield());

    List<Bytes48> pubKeys = specHelpers.mapIndicesToPubKeys(state, participants);

    for (Bytes48 pubKey : pubKeys) {
      if (attestationCache.containsKey(pubKey)) {
        Attestation oldAttestation = attestationCache.get(pubKey);
        if (attestation.getData().getSlot().compareTo(oldAttestation.getData().getSlot()) > 0) {
          attestationCache.put(pubKey, attestation);
          validatorSlotCache.get(oldAttestation.getData().getSlot()).remove(pubKey);
          addToSlotCache(attestation.getData().getSlot(), pubKey);
        } else {
          // XXX: If several such attestations exist, use the one the validator v observed first
          // so no need to swap it
        }
      } else {
        attestationCache.put(pubKey, attestation);
        addToSlotCache(attestation.getData().getSlot(), pubKey);
      }
    }
  }

  private void addToSlotCache(UInt64 slot, Bytes48 pubKey) {
    if (validatorSlotCache.containsKey(slot)) {
      validatorSlotCache.get(slot).add(pubKey);
    } else {
      Set<Bytes48> pubKeysSet = new HashSet<>();
      pubKeysSet.add(pubKey);
      validatorSlotCache.put(slot, pubKeysSet);
    }
  }

  /** Purges all entries for slot and before */
  public void purgeAttestations(UInt64 slot) {
    for (Map.Entry<UInt64, Set<Bytes48>> entry : validatorSlotCache.entrySet()) {
      if (entry.getKey().compareTo(slot) <= 0) {
        entry.getValue().forEach(attestationCache::remove);
        validatorSlotCache.remove(entry.getKey());
      }
    }
  }
}
