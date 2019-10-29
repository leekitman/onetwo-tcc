package org.onetwo.tcc.core.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.onetwo.common.interceptor.SimpleInterceptorChain;
import org.onetwo.common.interceptor.SimpleInterceptorManager;
import org.onetwo.common.utils.LangUtils;
import org.onetwo.common.utils.StringUtils;
import org.onetwo.dbm.id.SnowflakeIdGenerator;
import org.onetwo.tcc.boot.TCCProperties;
import org.onetwo.tcc.core.annotation.TCCTransactional;
import org.onetwo.tcc.core.exception.TCCErrors;
import org.onetwo.tcc.core.exception.TCCException;
import org.onetwo.tcc.core.exception.TCCRemoteException;
import org.onetwo.tcc.core.spi.TCCTXContextLookupService;
import org.onetwo.tcc.core.spi.TCCTXContextLookupService.TXContext;
import org.onetwo.tcc.core.spi.TXInterceptor;
import org.onetwo.tcc.core.spi.TXLogRepository;
import org.onetwo.tcc.core.util.TCCTransactionType;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.Getter;
import lombok.Setter;

/**
 * @author weishao zeng
 * <br/>
 */
@Aspect
public class TransactionAspect {
//	private static final NamedThreadLocal<TransactionContext> CURRENT_CONTEXTS = new NamedThreadLocal<>("dtx-transaction");

	public static TransactionResourceHolder getCurrent(TransactionAspect key) {
		return (TransactionResourceHolder)TransactionSynchronizationManager.getResource(key);
	}
	
	private TCCTXContextLookupService txContextLookupService;
	@Getter
	private TXLogRepository txLogRepository;
	@Value(TCCProperties.SERVICE_ID)
	private String serviceId;
	
    private SnowflakeIdGenerator idGenerator = new SnowflakeIdGenerator(31);
	
    @Setter
	private List<String> remoteExceptions = new ArrayList<String>();
    private SimpleInterceptorManager<TXInterceptor> interceptorManager;

	public TransactionAspect(SimpleInterceptorManager<TXInterceptor> interceptorManager, 
			TCCTXContextLookupService txContextLookupService,
			TXLogRepository txLogRepository) {
		super();
		this.interceptorManager = interceptorManager;
		this.txContextLookupService = txContextLookupService;
		this.txLogRepository = txLogRepository;
	}
	

	@Around("@annotation(org.onetwo.tcc.core.annotation.TCCTransactional)")
//	@Pointcut("@annotation(org.onetwo.tcc.annotation.TCCTransactional)")
	public Object startTransaction(ProceedingJoinPoint pjp) throws Throwable {
		MethodSignature ms = (MethodSignature)pjp.getSignature();
//		TransactionContext ctx = CURRENT_CONTEXTS.get();
		TransactionResourceHolder resource = (TransactionResourceHolder)TransactionSynchronizationManager.getResource(this);
		TCCTransactional tccTransaction = null;
		if (resource==null) {
			Optional<TXContext> parentContext = txContextLookupService.findCurrent();
			resource = new TransactionResourceHolder(this);
			tccTransaction = AnnotationUtils.findAnnotation(ms.getMethod(), TCCTransactional.class);
			resource.setTryMethod(ms.getMethod());
			resource.setConfirmMethod(tccTransaction.confirmMethod());
			resource.setCancelMethod(tccTransaction.cancelMethod());
			resource.setTarget(pjp.getTarget());
			resource.setMethodArgs(pjp.getArgs());
			Class<?> targetClass = AopUtils.getTargetClass(pjp.getTarget());
			resource.setTargetClass(targetClass);
			resource.setSynchronizedWithTransaction(true);
			resource.setServiceId(serviceId);
			if (parentContext.isPresent()) {
				resource.setTransactionType(TCCTransactionType.BRANCH);
//				resource.setGid(ctx.get().getGid());
//				resource.setParentId(ctx.get().getParentId());
				resource.setParentContext(parentContext.get());
				resource.setCurrentTxid("B" + nextId());
			} else {
				resource.setTransactionType(TCCTransactionType.GLOBAL);
				resource.setCurrentTxid("G" + nextId());
//				resource.setGid("G" + nextId());
			}
			resource.check();
			
			if (StringUtils.isBlank(resource.getConfirmMethod())) {
				throw new TCCException("the confirmMethod of @" + TCCTransactional.class.getSimpleName() + " can not be blank!");
			}
			if (StringUtils.isBlank(resource.getCancelMethod())) {
				throw new TCCException("the cancelMethod of @" + TCCTransactional.class.getSimpleName() + " can not be blank!");
			}
			
			TransactionSynchronizationManager.bindResource(this, resource);
			TCCTransactionSynchronization synchronization = new TCCTransactionSynchronization(resource);
			TransactionSynchronizationManager.registerSynchronization(synchronization);

			resource.createTxLog();
		} else {
			throw new TCCException(TCCErrors.ERR_ONLYONE_TCC_TRANSACTIONAL);
		}
		
		Object result = null;;
		try {
			SimpleInterceptorChain<TXInterceptor> interceptorChain = new SimpleInterceptorChain<>(pjp.getTarget(), 
																								ms.getMethod(), 
																								pjp.getArgs(), 
																								interceptorManager.getInterceptors(), 
																								() -> {
																									return pjp.proceed();
																								});
			result = interceptorChain.invoke();
		} catch (Throwable e) {
			handleException(e);
		}
		
		return result;
	}
	
	protected void handleException(Throwable e) throws Throwable {
		if (isRemoteError(e)) {
			throw new TCCRemoteException(TCCErrors.ERR_REMOTE, e);
		} else {
			throw e;
		}
	}
	
	/***
	 * 是否远程调用异常
	 * @author weishao zeng
	 * @param t
	 * @return
	 */
	protected boolean isRemoteError(Throwable t) {
		if (t instanceof IOException) {
			return true;
		}
		Throwable rootCause = LangUtils.getFinalCauseException(t);
		if (rootCause instanceof IOException) {
			return true;
		}
		if (remoteExceptions.contains(t.getClass().getName())) {
			return true;
		}
		return false;
	}
	
	protected String nextId() {
		return String.valueOf(idGenerator.nextId());
	}
	
}

