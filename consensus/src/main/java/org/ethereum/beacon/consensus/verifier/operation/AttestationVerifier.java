package org.ethereum.beacon.consensus.verifier.operation;

import static org.ethereum.beacon.consensus.verifier.VerificationResult.PASSED;
import static org.ethereum.beacon.consensus.verifier.VerificationResult.failedResult;
import static org.ethereum.beacon.core.spec.SignatureDomains.ATTESTATION;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.ethereum.beacon.consensus.SpecHelpers;
import org.ethereum.beacon.consensus.verifier.OperationVerifier;
import org.ethereum.beacon.consensus.verifier.VerificationResult;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.operations.Attestation;
import org.ethereum.beacon.core.operations.attestation.AttestationData;
import org.ethereum.beacon.core.operations.attestation.AttestationDataAndCustodyBit;
import org.ethereum.beacon.core.operations.attestation.Crosslink;
import org.ethereum.beacon.core.state.ShardCommittee;
import org.ethereum.beacon.core.types.BLSPubkey;
import org.ethereum.beacon.core.types.ValidatorIndex;
import org.ethereum.beacon.crypto.BLS381.PublicKey;
import tech.pegasys.artemis.ethereum.core.Hash32;

/**
 * Verifies {@link Attestation} beacon chain operation.
 *
 * @see Attestation
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#attestations-1">Attesations</a>
 *     in the spec.
 */
public class AttestationVerifier implements OperationVerifier<Attestation> {

  private SpecHelpers spec;

  public AttestationVerifier(SpecHelpers spec) {
    this.spec = spec;
  }

  @Override
  public VerificationResult verify(Attestation attestation, BeaconState state) {
    AttestationData data = attestation.getData();

    if (attestation.getData().getSlot().less(spec.getConstants().getGenesisSlot())) {
      return failedResult("Attestation slot %s is less than GENESIS_SLOT %s",
          attestation.getData().getSlot(), spec.getConstants().getGenesisSlot());
    }

    spec.checkShardRange(data.getShard());

    // Verify that attestation.data.slot <= state.slot - MIN_ATTESTATION_INCLUSION_DELAY
    //    < attestation.data.slot + SLOTS_PER_EPOCH
    if (!(data.getSlot()
            .lessEqual(state.getSlot().minus(spec.getConstants().getMinAttestationInclusionDelay()))
        && state
            .getSlot()
            .minus(spec.getConstants().getMinAttestationInclusionDelay())
            .less(data.getSlot().plus(spec.getConstants().getSlotsPerEpoch())))) {

      return failedResult(
          "MIN_ATTESTATION_INCLUSION_DELAY violated, inclusion slot starts from %s but got %s",
          data.getSlot().plus(spec.getConstants().getMinAttestationInclusionDelay()), state.getSlot());
    }

    // Verify that attestation.data.justified_epoch is equal to
    // state.justified_epoch
    // if slot_to_epoch(attestation.data.slot + 1) >= get_current_epoch(state)
    // else state.previous_justified_epoch.
    if (!data.getJustifiedEpoch().equals(
        spec.slot_to_epoch(data.getSlot().increment()).greaterEqual(spec.get_current_epoch(state)) ?
        state.getJustifiedEpoch() : state.getPreviousJustifiedEpoch())) {
      return failedResult(
          "Attestation.data.justified_epoch is invalid");
    }

    // Verify that attestation.data.justified_block_root is equal to
    // get_block_root(state, get_epoch_start_slot(attestation.data.justified_epoch))
    Hash32 blockRootAtJustifiedSlot = spec.get_block_root(state,
        spec.get_epoch_start_slot(data.getJustifiedEpoch()));
    if (!data.getJustifiedBlockRoot().equals(blockRootAtJustifiedSlot)) {
      return failedResult(
          "attestation_data.justified_block_root must be equal to block_root at state.justified_slot, "
              + "justified_block_root=%s, block_root=%s",
          data.getJustifiedBlockRoot(), blockRootAtJustifiedSlot);
    }

    // Verify that either
    //   (i) state.latest_crosslinks[attestation.data.shard] == attestation.data.latest_crosslink or
    //   (ii) state.latest_crosslinks[attestation.data.shard] ==
    //        Crosslink(crosslink_data_root=attestation.data.crosslink_data_root, epoch=slot_to_epoch(attestation.data.slot)).
    Crosslink latestCrosslink =
        state.getLatestCrosslinks().get(data.getShard());
    if (!data.getLatestCrosslink().equals(latestCrosslink)
        && !latestCrosslink.equals(new Crosslink(spec.slot_to_epoch(attestation.getData().getSlot()),
            attestation.getData().getCrosslinkDataRoot()))) {
      return failedResult("attestation.data.latest_crosslink is incorrect");
    }

    // Verify bitfields and aggregate signature:

    //  assert attestation.custody_bitfield == b'\x00' * len(attestation.custody_bitfield)  # [TO BE REMOVED IN PHASE 1]
    if (!attestation.getCustodyBitfield().isZero()) {
      return failedResult("attestation.custody_bitfield != ZERO");
    }
    //  assert attestation.aggregation_bitfield != b'\x00' * len(attestation.aggregation_bitfield)
    if (attestation.getAggregationBitfield().isZero()) {
      return failedResult("attestation.aggregation_bitfield == ZERO");
    }

    //  crosslink_committee = [
    //      committee for committee, shard in get_crosslink_committees_at_slot(state, attestation.data.slot)
    //      if shard == attestation.data.shard
    //  ][0]
    Optional<ShardCommittee> crosslink_committee_opt = spec
        .get_crosslink_committees_at_slot(state, data.getSlot()).stream()
        .filter(c -> c.getShard().equals(data.getShard()))
        .findFirst();
    if (!crosslink_committee_opt.isPresent()) {
      return failedResult("crosslink_committee not found");
    }
    List<ValidatorIndex> crosslink_committee = crosslink_committee_opt.get().getCommittee();

    //  for i in range(len(crosslink_committee)):
    //      if get_bitfield_bit(attestation.aggregation_bitfield, i) == 0b0:
    //          assert get_bitfield_bit(attestation.custody_bitfield, i) == 0b0
    for (int i = 0; i < crosslink_committee.size(); i++) {
      if (attestation.getAggregationBitfield().getBit(i) == false) {
        if (attestation.getCustodyBitfield().getBit(i) != false) {
          return failedResult("aggregation_bitfield and custody_bitfield doesn't match");
        }
      }
    }

    //  participants = get_attestation_participants(state, attestation.data, attestation.aggregation_bitfield)
    List<ValidatorIndex> participants =
        spec.get_attestation_participants(state, data, attestation.getAggregationBitfield());

    //  custody_bit_1_participants = get_attestation_participants(state, attestation.data, attestation.custody_bitfield)
    List<ValidatorIndex> custody_bit_1_participants =
        spec.get_attestation_participants(state, data, attestation.getCustodyBitfield());
    //  custody_bit_0_participants = [i in participants for i not in custody_bit_1_participants]
    List<ValidatorIndex> custody_bit_0_participants = participants.stream()
        .filter(i -> !custody_bit_1_participants.contains(i)).collect(Collectors.toList());

    //  assert bls_verify_multiple(
    //      pubkeys=[
    //          bls_aggregate_pubkeys([state.validator_registry[i].pubkey for i in custody_bit_0_participants]),
    //          bls_aggregate_pubkeys([state.validator_registry[i].pubkey for i in custody_bit_1_participants]),
    //      ],
    //      messages=[
    //          hash_tree_root(AttestationDataAndCustodyBit(data=attestation.data, custody_bit=0b0)),
    //          hash_tree_root(AttestationDataAndCustodyBit(data=attestation.data, custody_bit=0b1)),
    //      ],
    //      signature=attestation.aggregate_signature,
    //      domain=get_domain(state.fork, slot_to_epoch(attestation.data.slot), DOMAIN_ATTESTATION),
    //  )
    List<BLSPubkey> pubKeys1 = spec.mapIndicesToPubKeys(state, custody_bit_0_participants);
    PublicKey groupPublicKey1 = spec.bls_aggregate_pubkeys(pubKeys1);
    List<BLSPubkey> pubKeys2 = spec.mapIndicesToPubKeys(state, custody_bit_1_participants);
    PublicKey groupPublicKey2 = spec.bls_aggregate_pubkeys(pubKeys2);
    if (!spec.bls_verify_multiple(
        Arrays.asList(groupPublicKey1, groupPublicKey2),
        Arrays.asList(
          spec.hash_tree_root(new AttestationDataAndCustodyBit(data, false)),
          spec.hash_tree_root(new AttestationDataAndCustodyBit(data, true))),
        attestation.getAggregateSignature(),
        spec.get_domain(state.getForkData(), spec.slot_to_epoch(data.getSlot()), ATTESTATION))) {
      return failedResult("failed to verify aggregated signature");
    }

    if (!Hash32.ZERO.equals(data.getCrosslinkDataRoot())) {
      return failedResult(
          "attestation_data.crosslink_data_root must be equal to zero hash, phase 0 check");
    }

    return PASSED;
  }
}
