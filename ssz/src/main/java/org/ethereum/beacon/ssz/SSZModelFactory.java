package org.ethereum.beacon.ssz;

import org.javatuples.Pair;
import java.util.List;

/** Creates instance of SSZ model class */
public interface SSZModelFactory {

  /** Registers object creator which will be used for ssz model instantiation */
  SSZModelFactory registerObjCreator(ObjectCreator objectCreator);

  /**
   * Creates instance of SSZ model class using field -> value data
   *
   * @param clazz SSZ model class
   * @param fieldValuePairs Field -> value info
   * @return created instance
   */
  <C> C create(
      Class<? extends C> clazz, List<Pair<SSZSchemeBuilder.SSZScheme.SSZField, Object>> fieldValuePairs);
}
