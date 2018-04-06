/**
 * Copyright 2018 Smartrplace UG
 *
 * FendoDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FendoDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
