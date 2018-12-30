/**
 * ﻿Copyright 2018 Smartrplace UG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
