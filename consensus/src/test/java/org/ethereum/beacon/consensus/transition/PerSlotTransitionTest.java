package org.ethereum.beacon.consensus.transition;

import java.util.List;
import java.util.Random;
import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.consensus.SpecHelpers;
import org.ethereum.beacon.consensus.TestUtils;
import org.ethereum.beacon.core.BeaconBlocks;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.pow.DepositContract.ChainStart;
import org.junit.Assert;
import org.junit.Test;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.uint.UInt64;

public class PerSlotTransitionTest {

  @Test
  public void test1() {
    Random rnd = new Random();
    Time genesisTime = Time.castFrom(UInt64.random(rnd));
    Eth1Data eth1Data = new Eth1Data(Hash32.random(rnd), Hash32.random(rnd));
    SpecConstants specConstants =
        new SpecConstants() {
          @Override
          public SlotNumber.EpochLength getSlotsPerEpoch() {
            return new SlotNumber.EpochLength(UInt64.valueOf(8));
          }
        };
    SpecHelpers specHelpers = SpecHelpers.createWithSSZHasher(specConstants);

    List<Deposit> deposits = TestUtils.getAnyDeposits(rnd, specHelpers, 8).getValue0();

    InitialStateTransition initialStateTransition =
        new InitialStateTransition(
            new ChainStart(genesisTime, eth1Data, deposits),
            specHelpers);

    BeaconStateEx initialState =
        initialStateTransition.apply(BeaconBlocks.createGenesis(specConstants));
    BeaconStateEx s1State = new PerSlotTransition(specHelpers).apply(initialState);
    BeaconStateEx s2State = new PerSlotTransition(specHelpers).apply(s1State);
    BeaconStateEx s3State = new PerSlotTransition(specHelpers).apply(s2State);

    Assert.assertEquals(specConstants.getGenesisSlot().plus(3), s3State.getSlot());
  }
}
