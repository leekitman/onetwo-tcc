package org.onetwo.tcc.hystrix;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.onetwo.tcc.core.internal.TransactionResourceHolder;
import org.onetwo.tcc.core.util.TCCInvokeContext;

import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableLifecycle;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.strategy.properties.HystrixProperty;

/**
 * example: RequestContextConcurrencyStrategy
 * @author wayshall
 * <br/>
 */
public class TCCInvokeContextConcurrencyStrategy extends HystrixConcurrencyStrategy {

	private HystrixConcurrencyStrategy existingConcurrencyStrategy;

	public TCCInvokeContextConcurrencyStrategy() {
		this(HystrixPlugins.getInstance().getConcurrencyStrategy());
	}
	
	public TCCInvokeContextConcurrencyStrategy(HystrixConcurrencyStrategy existingConcurrencyStrategy) {
		if (getClass().isInstance(existingConcurrencyStrategy)) {
			System.out.println("Welcome to singleton hell...");
			return;
		}
		
		this.existingConcurrencyStrategy = existingConcurrencyStrategy;
		
		HystrixEventNotifier eventNotifier = HystrixPlugins.getInstance()
				.getEventNotifier();
		HystrixMetricsPublisher metricsPublisher = HystrixPlugins.getInstance()
				.getMetricsPublisher();
		HystrixPropertiesStrategy propertiesStrategy = HystrixPlugins.getInstance()
				.getPropertiesStrategy();
		HystrixCommandExecutionHook commandExecutionHook = HystrixPlugins.getInstance()
				.getCommandExecutionHook();

		HystrixPlugins.reset();

		// Registers existing plugins excepts the Concurrent Strategy plugin.
		HystrixPlugins.getInstance().registerConcurrencyStrategy(this);
		HystrixPlugins.getInstance().registerEventNotifier(eventNotifier);
		HystrixPlugins.getInstance().registerMetricsPublisher(metricsPublisher);
		HystrixPlugins.getInstance().registerPropertiesStrategy(propertiesStrategy);
		HystrixPlugins.getInstance().registerCommandExecutionHook(commandExecutionHook);
	}
	
	@Override
	public BlockingQueue<Runnable> getBlockingQueue(int maxQueueSize) {
		return existingConcurrencyStrategy != null
				? existingConcurrencyStrategy.getBlockingQueue(maxQueueSize)
				: super.getBlockingQueue(maxQueueSize);
	}

	@Override
	public <T> HystrixRequestVariable<T> getRequestVariable(
			HystrixRequestVariableLifecycle<T> rv) {
		return existingConcurrencyStrategy != null
				? existingConcurrencyStrategy.getRequestVariable(rv)
				: super.getRequestVariable(rv);
	}

	@Override
	public ThreadPoolExecutor getThreadPool(HystrixThreadPoolKey threadPoolKey,
			HystrixProperty<Integer> corePoolSize,
			HystrixProperty<Integer> maximumPoolSize,
			HystrixProperty<Integer> keepAliveTime, TimeUnit unit,
			BlockingQueue<Runnable> workQueue) {
		return existingConcurrencyStrategy != null
				? existingConcurrencyStrategy.getThreadPool(threadPoolKey, corePoolSize,
						maximumPoolSize, keepAliveTime, unit, workQueue)
				: super.getThreadPool(threadPoolKey, corePoolSize, maximumPoolSize,
						keepAliveTime, unit, workQueue);
	}

	@Override
	public ThreadPoolExecutor getThreadPool(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolProperties threadPoolProperties) {
		return existingConcurrencyStrategy != null
				? existingConcurrencyStrategy.getThreadPool(threadPoolKey, threadPoolProperties)
				: super.getThreadPool(threadPoolKey, threadPoolProperties);
	}

	@Override
	public <T> Callable<T> wrapCallable(Callable<T> callable) {
		return existingConcurrencyStrategy != null
				? existingConcurrencyStrategy
						.wrapCallable(createDelegatingContextCallable(callable))
				: super.wrapCallable(createDelegatingContextCallable(callable));
	}
	
	protected <V> Callable<V> createDelegatingContextCallable(Callable<V> callable) {
		return new TCCInvokeContextCallable<>(callable);
	}
	
	class TCCInvokeContextCallable<T> implements Callable<T> {
		private Callable<T> delegate;
//		private RequestAttributes requestAttributes;
		private TransactionResourceHolder resourceHolder;
		
		public TCCInvokeContextCallable(Callable<T> callable) {
			this.delegate = callable;
//			this.requestAttributes = RequestContextHolder.getRequestAttributes();
			this.resourceHolder = TCCInvokeContext.get();
		}

		@Override
		public T call() throws Exception {
			TransactionResourceHolder existResourceHolder = TCCInvokeContext.get();
			
/*			RequestAttributes existRequestAttributes = RequestContextHolder.getRequestAttributes();
			boolean needToSetContext = requestAttributes!=null && !requestAttributes.equals(existRequestAttributes);*/
			try {
				TCCInvokeContext.set(resourceHolder);
				/*if (needToSetContext) {
					RequestContextHolder.setRequestAttributes(requestAttributes);
				}*/
				return this.delegate.call();
			} finally {
				/*if (needToSetContext){
					RequestContextHolder.setRequestAttributes(existRequestAttributes);
				}*/
				TCCInvokeContext.set(existResourceHolder);
			}
		}
		
	}
}
