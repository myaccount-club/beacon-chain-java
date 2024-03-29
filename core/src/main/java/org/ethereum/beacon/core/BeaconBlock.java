package org.ethereum.beacon.core;

import com.google.common.base.Objects;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.ethereum.beacon.core.operations.Attestation;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.operations.VoluntaryExit;
import org.ethereum.beacon.core.operations.ProposerSlashing;
import org.ethereum.beacon.core.operations.slashing.AttesterSlashing;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.Hashable;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.ethereum.core.Hash32;

/**
 * Beacon chain block.
 *
 * <p>It consists of a header fields and {@link BeaconBlockBody}.
 *
 * @see BeaconBlockBody
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#beaconblock">BeaconBlock
 *     in the spec</a>
 */
@SSZSerializable
public class BeaconBlock implements Hashable<Hash32> {

  /** Number of a slot that block does belong to. */
  @SSZ private final SlotNumber slot;
  /** A hash of parent block. */
  @SSZ private final Hash32 parentRoot;
  /** A hash of the state that is created by applying a block to the previous state. */
  @SSZ private final Hash32 stateRoot;
  /** RANDAO signature submitted by proposer. */
  @SSZ private final BLSSignature randaoReveal;
  /** Eth1 data that is observed by proposer. */
  @SSZ private final Eth1Data eth1Data;
  /** Block body. */
  @SSZ private final BeaconBlockBody body;
  /** Proposer's signature. */
  @SSZ private final BLSSignature signature;

  private Hash32 hashCache = null;

  public BeaconBlock(
      SlotNumber slot,
      Hash32 parentRoot,
      Hash32 stateRoot,
      BLSSignature randaoReveal,
      Eth1Data eth1Data,
      BeaconBlockBody body,
      BLSSignature signature) {
    this.slot = slot;
    this.parentRoot = parentRoot;
    this.stateRoot = stateRoot;
    this.randaoReveal = randaoReveal;
    this.eth1Data = eth1Data;
    this.signature = signature;
    this.body = body;
  }

  @Override
  public Optional<Hash32> getHash() {
    return Optional.ofNullable(hashCache);
  }

  @Override
  public void setHash(Hash32 hash) {
    this.hashCache = hash;
  }

  public BeaconBlock withStateRoot(Hash32 stateRoot) {
    return new BeaconBlock(slot, parentRoot, stateRoot, randaoReveal, eth1Data, body, signature);
  }

  public SlotNumber getSlot() {
    return slot;
  }

  public Hash32 getParentRoot() {
    return parentRoot;
  }

  public Hash32 getStateRoot() {
    return stateRoot;
  }

  public BLSSignature getRandaoReveal() {
    return randaoReveal;
  }

  public Eth1Data getEth1Data() {
    return eth1Data;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  public BeaconBlockBody getBody() {
    return body;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BeaconBlock block = (BeaconBlock) o;
    return Objects.equal(slot, block.slot) &&
        Objects.equal(parentRoot, block.parentRoot) &&
        Objects.equal(stateRoot, block.stateRoot) &&
        Objects.equal(randaoReveal, block.randaoReveal) &&
        Objects.equal(eth1Data, block.eth1Data) &&
        Objects.equal(signature, block.signature) &&
        Objects.equal(body, block.body);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(slot, parentRoot, stateRoot, randaoReveal, eth1Data, signature, body);
  }

  @Override
  public String toString() {
    return toString(null, null, null);
  }

  public String toStringFull(@Nullable SpecConstants constants, @Nullable Time beaconStart,
      @Nullable Function<? super BeaconBlock, Hash32> hasher) {
    StringBuilder ret = new StringBuilder("Block["
        + toStringPriv(constants, beaconStart, hasher)
        + "]:\n");
    for (Attestation attestation : body.getAttestations()) {
      ret.append("  " + attestation.toString(constants, beaconStart) + "\n");
    }
    for (Deposit deposit : body.getDeposits()) {
      ret.append("  " + deposit.toString() + "\n");
    }
    for (VoluntaryExit voluntaryExit : body.getExits()) {
      ret.append("  " + voluntaryExit.toString(constants) + "\n");
    }
    for (ProposerSlashing proposerSlashing : body.getProposerSlashings()) {
      ret.append("  " + proposerSlashing.toString(constants, beaconStart) + "\n");
    }

    for (AttesterSlashing attesterSlashing : body.getAttesterSlashings()) {
      ret.append("  " + attesterSlashing.toString(constants, beaconStart) + "\n");
    }

    return ret.toString();
  }

  public String toString(@Nullable SpecConstants constants, @Nullable Time beaconStart,
      @Nullable Function<? super BeaconBlock, Hash32> hasher) {
    String ret = "Block[" + toStringPriv(constants, beaconStart, hasher);
    if (!body.getAttestations().isEmpty()) {
      ret += ", atts: [" + body.getAttestations().stream()
          .map(a -> a.toStringShort(constants))
          .collect(Collectors.joining(", ")) + "]";
    }
    if (!body.getDeposits().isEmpty()) {
      ret += ", depos: [" + body.getDeposits().stream()
          .map(a -> a.toString())
          .collect(Collectors.joining(", ")) + "]";
    }
    if (!body.getExits().isEmpty()) {
      ret += ", exits: [" + body.getExits().stream()
          .map(a -> a.toString(constants))
          .collect(Collectors.joining(", ")) + "]";
    }
    if (!body.getAttesterSlashings().isEmpty()) {
      ret += ", attSlash: [" + body.getAttesterSlashings().stream()
          .map(a -> a.toString(constants, beaconStart))
          .collect(Collectors.joining(", ")) + "]";
    }
    if (!body.getProposerSlashings().isEmpty()) {
      ret += ", propSlash: [" + body.getProposerSlashings().stream()
          .map(a -> a.toString(constants, beaconStart))
          .collect(Collectors.joining(", ")) + "]";
    }
    ret += "]";

    return ret;
  }

  private String toStringPriv(@Nullable SpecConstants constants, @Nullable Time beaconStart,
      @Nullable Function<? super BeaconBlock, Hash32> hasher) {
    return (hasher == null ? "?" : hasher.apply(this).toStringShort())
        + " <~ " + parentRoot.toStringShort()
        + ", @slot " + slot.toStringNumber(constants)
        + ", state=" + stateRoot.toStringShort()
        + ", randao=" + randaoReveal.toString()
        + ", " + eth1Data
        + ", sig=" + signature;
  }

  public static class Builder {
    private SlotNumber slot;
    private Hash32 parentRoot;
    private Hash32 stateRoot;
    private BLSSignature randaoReveal;
    private Eth1Data eth1Data;
    private BLSSignature signature;
    private BeaconBlockBody body;

    private Builder() {}

    public static Builder createEmpty() {
      return new Builder();
    }

    public static Builder fromBlock(BeaconBlock block) {
      Builder builder = new Builder();

      builder.slot = block.slot;
      builder.parentRoot = block.parentRoot;
      builder.stateRoot = block.stateRoot;
      builder.randaoReveal = block.randaoReveal;
      builder.eth1Data = block.eth1Data;
      builder.signature = block.signature;
      builder.body = block.body;

      return builder;
    }

    public Builder withSlot(SlotNumber slot) {
      this.slot = slot;
      return this;
    }

    public Builder withParentRoot(Hash32 parentRoot) {
      this.parentRoot = parentRoot;
      return this;
    }

    public Builder withStateRoot(Hash32 stateRoot) {
      this.stateRoot = stateRoot;
      return this;
    }

    public Builder withRandaoReveal(BLSSignature randaoReveal) {
      this.randaoReveal = randaoReveal;
      return this;
    }

    public Builder withEth1Data(Eth1Data eth1Data) {
      this.eth1Data = eth1Data;
      return this;
    }

    public Builder withSignature(BLSSignature signature) {
      this.signature = signature;
      return this;
    }

    public Builder withBody(BeaconBlockBody body) {
      this.body = body;
      return this;
    }

    public BeaconBlock build() {
      assert slot != null;
      assert parentRoot != null;
      assert stateRoot != null;
      assert randaoReveal != null;
      assert eth1Data != null;
      assert signature != null;
      assert body != null;

      return new BeaconBlock(
          slot, parentRoot, stateRoot, randaoReveal, eth1Data, body, signature);
    }
  }
}
