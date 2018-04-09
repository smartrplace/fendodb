package org.smartrplace.logging.fendodb.impl;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class ReferenceCounter {

	private final AtomicInteger proxyCount = new AtomicInteger(0);
	private final AtomicBoolean closed;
	
	ReferenceCounter(AtomicBoolean closed) {
		this.closed = closed;
	}
	
	void referenceAdded() {
		if (!closed.get())
			proxyCount.incrementAndGet();
	}
	
	int referenceRemoved() {
		return proxyCount.decrementAndGet();
	}
	
	int getReferenceCount() {
		return proxyCount.get();
	}
	
}
