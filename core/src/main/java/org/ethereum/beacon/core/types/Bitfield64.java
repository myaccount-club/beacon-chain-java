package org.ethereum.beacon.core.types;

import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.util.uint.UInt64;

@SSZSerializable(serializeAs = UInt64.class)
public class Bitfield64 extends UInt64 {

  public static final Bitfield64 ZERO = new Bitfield64(UInt64.ZERO);

  public Bitfield64(UInt64 uint) {
    super(uint);
  }

  @Override
  public Bitfield64 shl(int number) {
    return new Bitfield64(super.shl(number));
  }

  @Override
  public Bitfield64 shr(int number) {
    return new Bitfield64(super.shr(number));
  }

  public Bitfield64 or(long uint) {
    return new Bitfield64(super.or(UInt64.valueOf(uint)));
  }

  @Override
  public String toString() {
    return "0b" + Long.toBinaryString(getValue());
  }
}
