package org.ethereum.beacon.consensus.verifier.block;

import static org.ethereum.beacon.consensus.verifier.VerificationResult.PASSED;
import static org.ethereum.beacon.consensus.verifier.VerificationResult.failedResult;

import org.ethereum.beacon.consensus.verifier.OperationVerifier;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.spec.SpecConstants;
import tech.pegasys.artemis.util.collections.ReadList;
import tech.pegasys.artemis.util.uint.UInt64;

/**
 * Verifies deposit list.
 *
 * @see Deposit
 */
public class DepositListVerifier extends OperationListVerifier<Deposit> {

  public DepositListVerifier(OperationVerifier<Deposit> operationVerifier, SpecConstants specConstants) {
    super(operationVerifier, block -> block.getBody().getDeposits(), specConstants.getMaxDeposits());

    addCustomVerifier(
        (deposits, state) -> {
          if (ReadList.sizeOf(deposits) > 0) {
            UInt64 expectedIndex = deposits.iterator().next().getIndex();

            if (!expectedIndex.equals(state.getDepositIndex())) {
              return failedResult(
                  "index of the first deposit is incorrect, expected %d but got %d",
                  state.getDepositIndex(), expectedIndex);
            }

            for (Deposit deposit : deposits) {
              if (!deposit.getIndex().equals(expectedIndex)) {
                return failedResult(
                    "inclusion order is broken, expected index %d but got %d",
                    expectedIndex, deposit.getIndex());
              }
              expectedIndex = expectedIndex.increment();
            }
          }

          return PASSED;
        });
  }

  @Override
  protected Class<Deposit> getType() {
    return Deposit.class;
  }
}
