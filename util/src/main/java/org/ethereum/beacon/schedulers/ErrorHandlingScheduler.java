package org.ethereum.beacon.schedulers;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class ErrorHandlingScheduler implements Scheduler {

  private final Scheduler delegate;
  private final Consumer<Throwable> errorHandler;

  public ErrorHandlingScheduler(Scheduler delegate,
      Consumer<Throwable> errorHandler) {
    this.delegate = delegate;
    this.errorHandler = errorHandler;
  }

  @Override
  public <T> CompletableFuture<T> execute(Callable<T> task) {
    return delegate.execute(task);
  }

  @Override
  public <T> CompletableFuture<T> executeWithDelay(Duration delay, Callable<T> task) {
    return delegate.executeWithDelay(delay, task);
  }

  @Override
  public CompletableFuture<Void> executeAtFixedRate(
      Duration initialDelay, Duration period,
      RunnableEx task) {
    return delegate.executeAtFixedRate(initialDelay, period, () -> runAndHandleError(task));
  }

  @Override
  public CompletableFuture<Void> execute(
      RunnableEx task) {
    return delegate.execute(() -> runAndHandleError(task));
  }

  @Override
  public CompletableFuture<Void> executeWithDelay(Duration delay,
      RunnableEx task) {
    return delegate.executeWithDelay(delay, () -> runAndHandleError(task));
  }

  private void runAndHandleError(RunnableEx runnable) throws Exception {
    try {
      runnable.run();
    } catch (Exception e) {
      errorHandler.accept(e);
      throw e;
    } catch (Throwable t) {
      errorHandler.accept(t);
      throw new ExecutionException(t);
    }
  }
}
