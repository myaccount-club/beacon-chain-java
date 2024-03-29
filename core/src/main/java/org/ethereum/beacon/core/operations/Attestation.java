package org.ethereum.beacon.core.operations;

import com.google.common.base.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.ethereum.beacon.core.BeaconBlockBody;
import org.ethereum.beacon.core.operations.attestation.AttestationData;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.Bitfield;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;

/**
 * Attests on a block linked to particular slot in particular shard.
 *
 * @see BeaconBlockBody
 * @see AttestationData
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#attestation">Attestation
 *     in the spec</a>
 */
@SSZSerializable
public class Attestation {

  /** Attestation data object. */
  @SSZ private final AttestationData data;
  /** A bitfield where each bit corresponds to a validator attested to the {@link #data}. */
  @SSZ private final Bitfield aggregationBitfield;
  /** Proof of custody bitfield. */
  @SSZ private final Bitfield custodyBitfield;
  /** A product of aggregation of signatures from different validators to {@link #data}. */
  @SSZ private final BLSSignature aggregateSignature;

  public Attestation(
      AttestationData data,
      Bitfield aggregationBitfield,
      Bitfield custodyBitfield,
      BLSSignature aggregateSignature) {
    this.data = data;
    this.aggregationBitfield = aggregationBitfield;
    this.custodyBitfield = custodyBitfield;
    this.aggregateSignature = aggregateSignature;
  }

  public AttestationData getData() {
    return data;
  }

  public Bitfield getAggregationBitfield() {
    return aggregationBitfield;
  }

  public Bitfield getCustodyBitfield() {
    return custodyBitfield;
  }

  public BLSSignature getAggregateSignature() {
    return aggregateSignature;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Attestation that = (Attestation) o;
    return Objects.equal(data, that.data)
        && Objects.equal(aggregationBitfield, that.aggregationBitfield)
        && Objects.equal(custodyBitfield, that.custodyBitfield)
        && Objects.equal(aggregateSignature, that.aggregateSignature);
  }

  @Override
  public int hashCode() {
    int result = data.hashCode();
    result = 31 * result + aggregationBitfield.hashCode();
    result = 31 * result + custodyBitfield.hashCode();
    result = 31 * result + aggregateSignature.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return toString(null, null);
  }

  private String getSignerIndices() {
    return aggregationBitfield.getBits().stream().map(i -> "" + i).collect(Collectors.joining("+"));
  }

  public String toString(@Nullable SpecConstants spec,@Nullable Time beaconStart) {
    return "Attestation["
        + data.toString(spec, beaconStart)
        + ", attesters=" + getSignerIndices()
        + ", cusodyBits=" + custodyBitfield
        + ", sig=" + aggregateSignature
        + "]";
  }

  public String toStringShort(@Nullable SpecConstants spec) {
    return getData().getSlot().toStringNumber(spec) + "/"
        + getData().getShard().toString(spec) + "/"
        + getData().getBeaconBlockRoot().toStringShort() + "/"
        + getSignerIndices();
  }
}
