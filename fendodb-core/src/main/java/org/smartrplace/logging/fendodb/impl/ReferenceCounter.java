package org.smartrplace.logging.fendodb.impl;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class ReferenceCounter {

	private final AtomicInteger proxyCount = new AtomicInteger(0);
	private final AtomicBoolean closed;
	private final Callable<Void> closedCallable;
	
	ReferenceCounter(AtomicBoolean closed, Callable<Void> closedCallable) {
		this.closed = closed;
		this.closedCallable = closedCallable;
	}
	
	void referenceAdded() {
		if (!closed.get())
			proxyCount.incrementAndGet();
	}
	
	void referenceRemoved() {
		if (!closed.get() && proxyCount.decrementAndGet() <= 0 && closedCallable != null) {
			try {
				closedCallable.call();
			} catch (Exception e) {
				FileObjectProxy.logger.warn("Failed to execute closed callable",e);
			}
		}
	}
	
	int getReferenceCount() {
		return proxyCount.get();
	}
	
}
