package org.ethereum.beacon.core.types;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.util.uint.UInt64;

/** Time in seconds. */
@SSZSerializable(serializeAs = UInt64.class)
public class Time extends UInt64 implements SafeComparable<Time> {
  private static final SimpleDateFormat SHORT_TIME_FORMAT =
      new SimpleDateFormat("MM/dd/yy HH:mm:ss");

  public static final Time ZERO = of(0);

  public static Time of(long seconds) {
    return new Time(UInt64.valueOf(seconds));
  }

  public static Time castFrom(UInt64 time) {
    return new Time(time);
  }

  public Time(UInt64 uint) {
    super(uint);
  }

  public Time plus(Time addend) {
    return new Time(super.plus(addend));
  }

  public Time minus(Time subtrahend) {
    return new Time(super.minus(subtrahend));
  }

  @Override
  public Time times(UInt64 unsignedMultiplier) {
    return new Time(super.times(unsignedMultiplier));
  }

  public Time times(int times) {
    return new Time(super.times(times));
  }

  @Override
  public Time dividedBy(UInt64 divisor) {
    return new Time(super.dividedBy(divisor));
  }

  @Override
  public Time dividedBy(long divisor) {
    return new Time(super.dividedBy(divisor));
  }

  public Millis getMillis() {
    return Millis.of(getValue() * 1000);
  }

  @Override
  public String toString() {
    return SHORT_TIME_FORMAT.format(new Date(getMillis().getValue()));
  }
}
