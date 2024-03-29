package org.ethereum.beacon.ssz.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SSZ model field annotation. Used at model field definition.
 *
 * <p>Clarifies SSZ encoding/decoding details
 *
 * <p>Required if {@link org.ethereum.beacon.ssz.SSZAnnotationSchemeBuilder}
 * `explicitFieldAnnotation` is set to <b>true</b>, otherwise it's optional.
 */
@Documented
@Target(ElementType.FIELD)
@Inherited
@Retention(RetentionPolicy.RUNTIME)
public @interface SSZ {

  // Handy type shortcuts
  String UInt16 = "uint16";
  String UInt24 = "uint24";
  String UInt32 = "uint32";
  String UInt64 = "uint64";
  String UInt256 = "uint256";
  String UInt384 = "uint384";
  String UInt512 = "uint512";
  String Bytes = "bytes";
  String Hash = "hash";
  String Hash32 = "hash32";
  String Hash48 = "hash48";
  String Bool = "bool";
  String Address = "address";
  String String = "string";

  /**
   * Specifies type and size (for fixed sizes). If custom type is specified, it overrides default
   * type mapped from Java class.
   *
   * <p>Type should be one of: "uint", "bytes", "hash", "boolean", "address", "string", "container".
   *
   * <p>Size could be omitted if it's not fixed. Otherwise for non-byte types size should be
   * multiplier of 8 as size is in bits. For byte types ("bytes", "hash", "address", "string") size
   * is provided in bytes. Size is required for "uint" type.
   *
   * <p>Numeric types and other fixed size types doesn't support null values. On attempt to encode
   * such value {@link NullPointerException} will be thrown.
   *
   * <p>For List declare type of values, which list holds, if type of this values could not be
   * mapped automatically.
   *
   * <p>Types and default mapping:
   *
   * <ul>
   *   <li>"uint" - unsigned integer. short.class is mapped to "uint16", int.class is mapped to
   *       "uint32", long.class is mapped to "uint64" by default, BigInteger is mapped to "uint512"
   *   <li>"bytes" - bytes data. byte[].class is mapped to "bytes"
   *   <li>"hash" - same as bytes, but purposed to use to store hash. no types are mapped to "hash"
   *       by default
   *   <li>"address" - bytes with size of 20, standard address size. no types are mapped to
   *       "address" by default
   *   <li>"bool" - bool type. boolean.class is mapped to "bool"
   *   <li>"string" - string, text type. String.class is mapped to "string"
   *   <li>"container" - type designed to store another model inside. Any class which has no default
   *       mapping will be handled as Container and should be SSZ-serializable.
   * </ul>
   *
   * <p>Examples: "bytes", "hash32"
   */
  String type() default "";

  /**
   * If true, non-standard field is not handled like container, instead its type is passed through
   * and reconstructed
   *
   * <p>So if you need to have, for example, some field to be stored as "hash32" in SSZ but you
   * don't want to use byte[] for it in Java representation, you need some class to handle it, this
   * parameter when set to <b>true</b> marks that it's such kind of field
   */
  boolean skipContainer() default false;
}
