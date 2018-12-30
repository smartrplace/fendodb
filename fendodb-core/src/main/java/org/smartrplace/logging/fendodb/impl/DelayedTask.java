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

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// wraps a task (Runnable) such that it can be executed only once at a time,
// and with a delay
// used for persistence tasks
class DelayedTask {

	private final long waitInterval;
	private final AtomicBoolean isRunning = new AtomicBoolean(false);
	private final Runnable internalTask;
	private final ScheduledExecutorService exec;
	private final AtomicBoolean isClosed = new AtomicBoolean(false);
	private volatile ScheduledFuture<?> future = null;
	
	DelayedTask(final Runnable task, final long waitInterval, ScheduledExecutorService exec) {
		Objects.requireNonNull(task);
		if (waitInterval <= 0)
			throw new IllegalArgumentException("Interval must be positive: " + waitInterval);
		this.exec = Objects.requireNonNull(exec);
		this.waitInterval = waitInterval;
		this.internalTask = new Runnable() {
			
			@Override
			public synchronized void run() {
				isRunning.set(false);
				task.run();  // TODO sometimes leads to failures if db is opened in read-only mode for a very short time
			}
		};
	}
	
	void schedule() {
		if (isRunning.getAndSet(true) || isClosed.get())
			return;
		this.future = exec.schedule(internalTask, waitInterval, TimeUnit.MILLISECONDS);
	}
	
	Future<?> close() {
		if (isClosed.getAndSet(true))
			return CompletableFuture.completedFuture(null);
		final ScheduledFuture<?> future= this.future;
		if (future != null)
			future.cancel(false);
		// execute once more, but immediately
		return exec.submit(internalTask);
	}
	
}
