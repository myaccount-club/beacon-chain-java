package org.ethereum.beacon.ssz;

import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.SSZ;
import org.ethereum.beacon.crypto.Hashes;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import org.ethereum.beacon.ssz.fixtures.AttestationRecord;
import org.ethereum.beacon.ssz.fixtures.Bitfield;
import org.ethereum.beacon.ssz.fixtures.Sign;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import tech.pegasys.artemis.util.bytes.BytesValue;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.logging.Logger;

import static com.sun.org.apache.xerces.internal.impl.dv.util.HexBin.decode;
import static net.consensys.cava.bytes.Bytes.fromHexString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Tests of {@link SSZSerializer}
 */
public class SSZSerializerTest {
  private SSZSerializer sszSerializer;

  @Before
  public void setup() {
    sszSerializer = SSZSerializerBuilder.getBakedAnnotationBuilder().build();
  }

  private static byte[] DEFAULT_HASH = Hashes.keccak256(BytesValue.fromHexString("aa")).getArrayUnsafe();
  private static Sign.Signature DEFAULT_SIG = new Sign.Signature();
  static {
    SecureRandom random = new SecureRandom();
    byte[] r = new byte[20];
    random.nextBytes(r);
    DEFAULT_SIG.r = new BigInteger(1, r);
    byte[] s = new byte[20];
    random.nextBytes(s);
    DEFAULT_SIG.s = new BigInteger(1, s);
  }

  @Test
  public void bitfieldTest() {
    Bitfield expected = new Bitfield(
        decode("abcd")
    );

    byte[] encoded = sszSerializer.encode(expected);
    Bitfield constructed = (Bitfield) sszSerializer.decode(encoded, Bitfield.class);

    assertEquals(expected, constructed);
  }

  @Test
  public void SignatureTest() {
    Sign.Signature signature = new Sign.Signature();
    signature.r = new BigInteger("23452342342342342342342315643768758756967967");
    signature.s = new BigInteger("8713785871");

    byte[] encoded = sszSerializer.encode(signature);
    Sign.Signature constructed = (Sign.Signature) sszSerializer.decode(encoded, Sign.Signature.class);

    assertEquals(signature, constructed);
  }

  @Test
  public void simpleTest() {
    AttestationRecord expected = new AttestationRecord(
        12412L,
        123,
        Collections.emptyList(),
        DEFAULT_HASH,
        new Bitfield(decode("abcdef45")),
        12400L,
        DEFAULT_HASH,
        DEFAULT_SIG
    );

    byte[] encoded = sszSerializer.encode(expected);
    AttestationRecord constructed = (AttestationRecord) sszSerializer.decode(encoded, AttestationRecord.class);

    assertEquals(expected, constructed);
  }

  @Test
  public void explicitAnnotationsAndLoggerTest() {
    SSZSerializerBuilder builder = new SSZSerializerBuilder();
    builder.initWith(new SSZAnnotationSchemeBuilder().withLogger(Logger.getLogger("test")),
        new SSZCodecRoulette(), new SSZModelCreator().registerObjCreator(new ConstructorObjCreator()));
    builder.addPrimitivesCodecs();
    SSZSerializer serializer = builder.build();

    AttestationRecord expected = new AttestationRecord(
        12412L,
        123,
        Collections.emptyList(),
        DEFAULT_HASH,
        new Bitfield(decode("abcdef45")),
        12400L,
        DEFAULT_HASH,
        DEFAULT_SIG
    );

    byte[] encoded = serializer.encode(expected);
    AttestationRecord constructed = (AttestationRecord) serializer.decode(encoded, AttestationRecord.class);

    Assert.assertNotEquals(expected, constructed);

    assertEquals(expected.getShardId(), constructed.getShardId());
    assertEquals(expected.getObliqueParentHashes(), constructed.getObliqueParentHashes());
    Assert.assertArrayEquals(expected.getShardBlockHash(), constructed.getShardBlockHash());
    Assert.assertNull(constructed.getAggregateSig());
  }

  @Test
  public void nullableTest() {
    AttestationRecord expected1 = new AttestationRecord(
        12412L,
        123,
        Collections.emptyList(),
        DEFAULT_HASH,
        new Bitfield(decode("abcdef45")),
        12400L,
        DEFAULT_HASH,
        null
    );
    byte[] encoded1 = sszSerializer.encode(expected1);
    AttestationRecord actual1 = (AttestationRecord) sszSerializer.decode(encoded1, AttestationRecord.class);

    assertEquals(expected1, actual1);

    AttestationRecord expected2 = new AttestationRecord(
        12412L,
        123,
        Collections.emptyList(),
        DEFAULT_HASH,
        null,
        12400L,
        DEFAULT_HASH,
        DEFAULT_SIG
    );
    byte[] encoded2 = sszSerializer.encode(expected2);
    AttestationRecord actual2 = (AttestationRecord) sszSerializer.decode(encoded2, AttestationRecord.class);

    assertEquals(expected2, actual2);

    AttestationRecord expected3 = new AttestationRecord(
        12412L,
        123,
        Collections.emptyList(),
        DEFAULT_HASH,
        null,
        12400L,
        DEFAULT_HASH,
        null
    );
    byte[] encoded3 = sszSerializer.encode(expected3);
    AttestationRecord actual3 = (AttestationRecord) sszSerializer.decode(encoded3, AttestationRecord.class);

    assertEquals(expected3, actual3);
  }

  @Test(expected = NullPointerException.class)
  public void nullFixedSizeFieldTest() {
    AttestationRecord expected3 = new AttestationRecord(
        12412L,
        123,
        Collections.emptyList(),
        null,
        new Bitfield(decode("abcdef45")),
        12400L,
        null,
        DEFAULT_SIG
    );
    sszSerializer.encode(expected3);
  }

  @Test(expected = NullPointerException.class)
  public void nullListTest() {
    AttestationRecord expected4 = new AttestationRecord(
        12412L,
        123,
        null,
        DEFAULT_HASH,
        new Bitfield(decode("abcdef45")),
        12400L,
        DEFAULT_HASH,
        DEFAULT_SIG
    );
    sszSerializer.encode(expected4);
  }

  @SSZSerializable
  public static class SomeObject {
    private final String name;
    @org.ethereum.beacon.ssz.annotation.SSZ(type="uint8")
    private final int number;
    @org.ethereum.beacon.ssz.annotation.SSZ(type="uint256")
    private final BigInteger longNumber;

    public SomeObject(String name, int number, BigInteger longNumber) {
      this.name = name;
      this.number = number;
      this.longNumber = longNumber;
    }

    public String getName() {
      return name;
    }

    public int getNumber() {
      return number;
    }

    public BigInteger getLongNumber() {
      return longNumber;
    }
  }

  /**
   * Checks that we build objects with {@link SSZSerializer}
   * in the same way as Consensys's {@link SSZ}
   */
  @Test
  public void shouldWorkLikeCavaWithObjects() {
    Bytes bytes = fromHexString("0x00000003426F62040000000000000000000000000000000000000000000000000000011F71B70768");
    SomeObject readObject = SSZ.decode(bytes, r -> new SomeObject(r.readString(), r.readInt8(), r.readBigInteger(256)));

    assertEquals("Bob", readObject.name);
    assertEquals(4, readObject.number);
    assertEquals(BigInteger.valueOf(1234563434344L), readObject.longNumber);

    // Now try the same with new SSZSerializer
    SomeObject readObjectAuto = (SomeObject) sszSerializer.decode(bytes.toArrayUnsafe(), SomeObject.class);
    assertEquals("Bob", readObjectAuto.name);
    assertEquals(4, readObjectAuto.number);
    assertEquals(BigInteger.valueOf(1234563434344L), readObjectAuto.longNumber);
    // and finally check it backwards
    assertArrayEquals(bytes.toArrayUnsafe(), sszSerializer.encode(readObjectAuto));
  }
}