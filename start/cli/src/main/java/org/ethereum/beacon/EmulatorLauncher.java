package org.ethereum.beacon;

import org.ethereum.beacon.chain.storage.impl.MemBeaconChainStorageFactory;
import org.ethereum.beacon.consensus.SpecHelpers;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.spec.ChainSpec;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.crypto.BLS381;
import org.ethereum.beacon.emulator.config.main.MainConfig;
import org.ethereum.beacon.emulator.config.main.action.Action;
import org.ethereum.beacon.emulator.config.main.action.ActionEmulate;
import org.ethereum.beacon.pow.DepositContract;
import org.ethereum.beacon.schedulers.ControlledSchedulers;
import org.ethereum.beacon.schedulers.LoggerMDCExecutor;
import org.ethereum.beacon.schedulers.Schedulers;
import org.ethereum.beacon.util.EmulateUtils;
import org.ethereum.beacon.wire.LocalWireHub;
import org.ethereum.beacon.wire.WireApi;
import org.javatuples.Pair;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tech.pegasys.artemis.ethereum.core.Hash32;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class EmulatorLauncher implements Runnable {
  private final ActionEmulate emulateConfig;
  private final ChainSpec chainSpec;

  public EmulatorLauncher(MainConfig mainConfig, ChainSpec chainSpec) {
    this.chainSpec = chainSpec;
    List<Action> actions = mainConfig.getPlan().getValidator();
    Optional<ActionEmulate> actionEmulate =
        actions.stream()
            .filter(a -> a instanceof ActionEmulate)
            .map(a -> (ActionEmulate) a)
            .findFirst();
    if (!actionEmulate.isPresent()) {
      throw new RuntimeException("Emulate settings are not set");
    }
    this.emulateConfig = actionEmulate.get();
  }

  public void run() {
    int validatorCount = emulateConfig.getCount();

    Random rnd = new Random(1);
    Time genesisTime = Time.of(10 * 60);

    MDCControlledSchedulers controlledSchedulers = new MDCControlledSchedulers();
    controlledSchedulers.setCurrentTime(genesisTime.getMillis().getValue() + 1000);

    Eth1Data eth1Data = new Eth1Data(Hash32.random(rnd), Hash32.random(rnd));

    Pair<List<Deposit>, List<BLS381.KeyPair>> anyDeposits = EmulateUtils.getAnyDeposits(
        SpecHelpers.createWithSSZHasher(chainSpec, () -> 0L), validatorCount);
    List<Deposit> deposits = anyDeposits.getValue0();

    LocalWireHub localWireHub = new LocalWireHub(s -> {});
    DepositContract.ChainStart chainStart = new DepositContract.ChainStart(genesisTime, eth1Data, deposits);
    DepositContract depositContract = new SimpleDepositContract(chainStart);

    System.out.println("Creating peers...");
    for(int i = 0; i < validatorCount; i++) {
      ControlledSchedulers schedulers = controlledSchedulers.createNew();
      SpecHelpers specHelpers = SpecHelpers
          .createWithSSZHasher(chainSpec, schedulers::getCurrentTime);
      WireApi wireApi = localWireHub.createNewPeer("" + i);

      Launcher launcher = new Launcher(specHelpers, depositContract, anyDeposits.getValue1().get(i),
          wireApi, new MemBeaconChainStorageFactory(), schedulers);

      int finalI = i;
      Flux.from(launcher.slotTicker.getTickerStream())
          .subscribe(slot -> System.out.println("  #" + finalI + " Slot: " + slot.toString(chainSpec, genesisTime)));
      Flux.from(launcher.observableStateProcessor.getObservableStateStream())
          .subscribe(os -> {
            System.out.println("  #" + finalI + " New observable state: " + os.toString(specHelpers));
          });
      Flux.from(launcher.beaconChainValidator.getProposedBlocksStream())
          .subscribe(block ->System.out.println("#" + finalI + " !!! New block: " +
              block.toString(chainSpec, genesisTime, specHelpers::hash_tree_root)));
      Flux.from(launcher.beaconChainValidator.getAttestationsStream())
          .subscribe(attest ->System.out.println("#" + finalI + " !!! New attestation: " +
              attest.toString(chainSpec, genesisTime)));
      Flux.from(launcher.beaconChain.getBlockStatesStream())
          .subscribe(blockState ->System.out.println("  #" + finalI + " Block imported: " +
              blockState.getBlock().toString(chainSpec, genesisTime, specHelpers::hash_tree_root)));
    }
    System.out.println("Peers created");

    while (true) {
      System.out.println("===============================");
      controlledSchedulers.addTime(Duration.ofSeconds(10));
    }
  }

  private static class SimpleDepositContract implements DepositContract {
    private final ChainStart chainStart;

    public SimpleDepositContract(ChainStart chainStart) {
      this.chainStart = chainStart;
    }

    @Override
    public Publisher<ChainStart> getChainStartMono() {
      return Mono.just(chainStart);
    }

    @Override
    public Publisher<Deposit> getDepositStream() {
      return Mono.empty();
    }

    @Override
    public List<DepositInfo> peekDeposits(
        int maxCount, Eth1Data fromDepositExclusive, Eth1Data tillDepositInclusive) {
      return Collections.emptyList();
    }

    @Override
    public boolean hasDepositRoot(Hash32 blockHash, Hash32 depositRoot) {
      return true;
    }

    @Override
    public Optional<Eth1Data> getLatestEth1Data() {
      return Optional.of(chainStart.getEth1Data());
    }

    @Override
    public void setDistanceFromHead(long distanceFromHead) {}
  }


  static class MDCControlledSchedulers {
    public String mdcKey = "validatorIndex";
    private int counter = 0;
    private List<ControlledSchedulers> schedulersList = new ArrayList<>();
    private long currentTime;

    public ControlledSchedulers createNew() {
      LoggerMDCExecutor mdcExecutor = new LoggerMDCExecutor(mdcKey, "" + counter);
      counter++;
      ControlledSchedulers newSched = Schedulers.createControlled(() -> mdcExecutor);
      newSched.setCurrentTime(currentTime);
      schedulersList.add(newSched);
      return newSched;
    }

    public void setCurrentTime(long time) {
      currentTime = time;
      schedulersList.forEach(cs -> cs.setCurrentTime(time));
    }

    void addTime(Duration duration) {
      addTime(duration.toMillis());
    }

    void addTime(long millis) {
      setCurrentTime(currentTime + millis);
    }
  }
}
