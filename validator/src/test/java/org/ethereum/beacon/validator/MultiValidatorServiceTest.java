package org.ethereum.beacon.validator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

import java.util.Collections;
import java.util.Random;
import org.ethereum.beacon.chain.observer.ObservableBeaconState;
import org.ethereum.beacon.chain.util.ObservableBeaconStateTestUtil;
import org.ethereum.beacon.consensus.SpecHelpers;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.state.ValidatorRecord;
import org.ethereum.beacon.core.types.BLSPubkey;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.ValidatorIndex;
import org.ethereum.beacon.validator.crypto.BLS381Credentials;
import org.ethereum.beacon.validator.crypto.MessageSigner;
import org.ethereum.beacon.validator.util.MessageSignerTestUtil;
import org.ethereum.beacon.validator.util.ValidatorServiceTestUtil;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes48;
import tech.pegasys.artemis.util.collections.ReadList;
import tech.pegasys.artemis.util.collections.WriteList;

public class MultiValidatorServiceTest {

  @Test
  public void recentStateIsKept() {
    Random random = new Random();
    SpecHelpers specHelpers = Mockito.spy(SpecHelpers.createWithSSZHasher(SpecConstants.DEFAULT));

    MultiValidatorService validator =
        ValidatorServiceTestUtil.mockBeaconChainValidator(random, specHelpers);
    Mockito.doReturn(true).when(specHelpers).is_current_slot(any(), anyLong());
    Mockito.doNothing().when(validator).runTasks(any());

    SlotNumber currentSlot = SlotNumber.of(Math.abs(random.nextLong()) % 10 + 10);
    ObservableBeaconState currentSlotState =
        ObservableBeaconStateTestUtil.createInitialState(random, specHelpers, currentSlot);

    // state was kept
    validator.onNewState(currentSlotState);
    Assert.assertEquals(currentSlotState, validator.getRecentState());

    ObservableBeaconState updatedState =
        ObservableBeaconStateTestUtil.createInitialState(random, specHelpers, currentSlot);

    // state was updated
    validator.onNewState(updatedState);
    Assert.assertEquals(updatedState, validator.getRecentState());
  }

  @Test
  public void outboundRecentStateIsIgnored() {
    Random random = new Random();
    SpecHelpers specHelpers = Mockito.spy(SpecHelpers.createWithSSZHasher(SpecConstants.DEFAULT));

    MultiValidatorService validator =
        ValidatorServiceTestUtil.mockBeaconChainValidator(random, specHelpers);
    Mockito.doNothing().when(validator).runTasks(any());

    ObservableBeaconState outdatedState =
        ObservableBeaconStateTestUtil.createInitialState(random, specHelpers, SlotNumber.ZERO);

    SlotNumber currentSlot = SlotNumber.of(Math.abs(random.nextLong()) % 10 + 10);
    ObservableBeaconState currentSlotState =
        ObservableBeaconStateTestUtil.createInitialState(random, specHelpers, currentSlot);

    Mockito.doReturn(true)
        .when(specHelpers)
        .is_current_slot(eq(currentSlotState.getLatestSlotState()), anyLong());

    // state wasn't kept
    validator.onNewState(outdatedState);
    Assert.assertNull(validator.getRecentState());

    // state was kept
    validator.onNewState(currentSlotState);
    Assert.assertEquals(currentSlotState, validator.getRecentState());

    // state wasn't updated
    validator.onNewState(outdatedState);
    Assert.assertEquals(currentSlotState, validator.getRecentState());
  }

  @Test
  public void initService() {
    Random random = new Random();
    SpecHelpers specHelpers = Mockito.spy(SpecHelpers.createWithSSZHasher(SpecConstants.DEFAULT));

    BLSPubkey pubkey = BLSPubkey.wrap(Bytes48.random(random));
    MessageSigner<BLSSignature> signer = MessageSignerTestUtil.createBLSSigner();
    BLS381Credentials blsCredentials = new BLS381Credentials(pubkey, signer);
    MultiValidatorService validator =
        ValidatorServiceTestUtil.mockBeaconChainValidator(random, specHelpers, blsCredentials);

    ValidatorIndex validatorIndex = ValidatorIndex.of(Math.abs(random.nextInt()) % 10 + 10);

    ObservableBeaconState outdatedState =
        ObservableBeaconStateTestUtil.createInitialState(random, specHelpers, SlotNumber.ZERO);
    validator.onNewState(outdatedState);
    Assert.assertNull(validator.getRecentState());

    Mockito.verify(validator, Mockito.never()).runTasks(any());

    SlotNumber currentSlot = SlotNumber.of(Math.abs(random.nextLong()) % 10 + 10);
    ObservableBeaconState currentSlotState =
        ObservableBeaconStateTestUtil.createInitialState(random, specHelpers, currentSlot);

    ReadList<ValidatorIndex, ValidatorRecord> validatorRegistry =
        createRegistry(random, validatorIndex, pubkey);
    Mockito.doReturn(validatorRegistry)
        .when(currentSlotState.getLatestSlotState())
        .getValidatorRegistry();

    Mockito.doReturn(true).when(specHelpers).is_current_slot(any(), anyLong());
    Mockito.doReturn(validatorIndex).when(specHelpers).get_validator_index_by_pubkey(any(), any());
    Mockito.doNothing().when(validator).runTasks(any());

    validator.onNewState(currentSlotState);

    // validatorIndex is set
    Assert.assertEquals(Collections.singleton(validatorIndex), validator.getValidatorIndices());
  }

  @Test
  public void runValidatorTasks() {
    Random random = new Random();
    SpecHelpers specHelpers = Mockito.spy(SpecHelpers.createWithSSZHasher(SpecConstants.DEFAULT));

    BLSPubkey pubkey = BLSPubkey.wrap(Bytes48.random(random));
    MessageSigner<BLSSignature> signer = MessageSignerTestUtil.createBLSSigner();
    BLS381Credentials blsCredentials = new BLS381Credentials(pubkey, signer);
    MultiValidatorService validator =
        ValidatorServiceTestUtil.mockBeaconChainValidator(random, specHelpers, blsCredentials);

    ValidatorIndex validatorIndex = ValidatorIndex.of(Math.abs(random.nextInt()) % 10 + 10);

    SlotNumber currentSlot = SlotNumber.of(Math.abs(random.nextLong()) % 10 + 10);
    ObservableBeaconState initialState =
        ObservableBeaconStateTestUtil.createInitialState(random, specHelpers, currentSlot);
    ObservableBeaconState updatedState =
        ObservableBeaconStateTestUtil.createInitialState(
            random, specHelpers, currentSlot.increment());
    ObservableBeaconState sameSlotState =
        ObservableBeaconStateTestUtil.createInitialState(
            random, specHelpers, currentSlot.increment());
    ObservableBeaconState nextSlotState =
        ObservableBeaconStateTestUtil.createInitialState(
            random, specHelpers, currentSlot.increment().increment());

    ReadList<ValidatorIndex, ValidatorRecord> validatorRegistry =
        createRegistry(random, validatorIndex, pubkey);

    Mockito.doReturn(validatorRegistry)
        .when(initialState.getLatestSlotState())
        .getValidatorRegistry();

    Mockito.doReturn(validatorRegistry)
        .when(updatedState.getLatestSlotState())
        .getValidatorRegistry();

    Mockito.doReturn(validatorRegistry)
        .when(sameSlotState.getLatestSlotState())
        .getValidatorRegistry();

    Mockito.doReturn(validatorRegistry)
        .when(nextSlotState.getLatestSlotState())
        .getValidatorRegistry();

    Mockito.doReturn(true).when(specHelpers).is_current_slot(any(), anyLong());
    Mockito.doReturn(validatorIndex).when(specHelpers).get_validator_index_by_pubkey(any(), any());
    Mockito.doNothing().when(validator).runTasks(any());

    validator.onNewState(initialState);
    Assert.assertEquals(Collections.singleton(validatorIndex), validator.getValidatorIndices());

    // runTasks was called on a new state
    validator.onNewState(updatedState);
    Mockito.verify(validator, Mockito.times(1)).runTasks(updatedState);
    Mockito.verify(validator, Mockito.times(2)).runTasks(any());

    // runTasks was not called for a state belonging to the same slot
    validator.onNewState(sameSlotState);
    Mockito.verify(validator, Mockito.times(2)).runTasks(any());

    // runTasks was called again when a state for a new slot came
    validator.onNewState(nextSlotState);
    Mockito.verify(validator, Mockito.times(3)).runTasks(any());
  }

  private ReadList<ValidatorIndex, ValidatorRecord> createRegistry(
      Random random, ValidatorIndex validatorIndex, BLSPubkey pubkey) {
    WriteList<ValidatorIndex, ValidatorRecord> validatorRegistry =
        WriteList.create(ValidatorIndex::of);
    validatorRegistry.addAll(
        Collections.nCopies(
            validatorIndex.getIntValue(),
            new ValidatorRecord(
                BLSPubkey.wrap(Bytes48.random(random)),
                Hash32.ZERO,
                EpochNumber.ZERO,
                EpochNumber.ZERO,
                EpochNumber.ZERO,
                Boolean.FALSE,
                Boolean.FALSE)));
    validatorRegistry.add(
        new ValidatorRecord(
            pubkey,
            Hash32.ZERO,
            EpochNumber.ZERO,
            EpochNumber.ZERO,
            EpochNumber.ZERO,
            Boolean.FALSE,
            Boolean.FALSE));

    return validatorRegistry;
  }
}
