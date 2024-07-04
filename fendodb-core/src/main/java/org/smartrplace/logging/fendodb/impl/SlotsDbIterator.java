/**
 * Copyright 2011-2018 Fraunhofer-Gesellschaft zur Förderung der angewandten Wissenschaften e.V.
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

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReadWriteLock;

import org.ogema.core.channelmanager.measurements.SampledValue;

/**
 * A fail-safe iterator that reads data file by file (which means day by day)
 */
class SlotsDbIterator implements Iterator<SampledValue> {

	private final SlotsDb recorder;
	private final long start;
	private final long end;
	private final ReadWriteLock lock;
	final String label;

	private volatile FileObjectList folder = null;
	private volatile List<SampledValue> folderValues = null;
	private int currentIdx = 0;
	private SampledValue current = null;
	private SampledValue next = null;

	SlotsDbIterator(String idEncoded, SlotsDb recorder, ReadWriteLock lock) {
		this(idEncoded, recorder, lock, Long.MIN_VALUE, Long.MAX_VALUE);
	}

	SlotsDbIterator(String idEncoded, SlotsDb recorder, ReadWriteLock lock, long start, long end) {
		this.recorder = recorder;
		this.lock = lock;
		this.start = start;
		this.end = end;
		this.label = idEncoded;
	}

	@Override
	public SampledValue next() {
		if (!hasNext())
			throw new NoSuchElementException();
		current = next;
		return next;
	}

	@Override
	public boolean hasNext() {
		if (nextIsNewer(current, next)) {
			return true;
		}
		else if (next == null && current != null) // reached the end
			return false;
		while(folderValues != null && currentIdx < folderValues.size()) {
			next = folderValues.get(currentIdx++);
			if (next.getTimestamp() > end) { // reached the end
				next = null;
				return false;
			}
			while (next.getTimestamp() < start) {
				current = next;
				if (currentIdx < folderValues.size()) {
					next = folderValues.get(currentIdx++);
				} else {
					return hasNext();
				}
			}
			return nextIsNewer(current, next) && !(next.getTimestamp() > end);
		}
		parseNextFile();
		if (folderValues == null) {
			next = null;
			return false;
		}
		return hasNext(); // parse next folder's list
	}


	private void parseNextFile() {
		final FileObjectList folder = this.folder;
		try {
			AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {

				@Override
				public Void run() throws Exception {
					final FileObjectList newfolder;
					final List<SampledValue> values;
					lock.readLock().lock();
					try {
						if (folder == null)
							newfolder = recorder.getProxy().getNextFolder(label, start, true);
						else
							newfolder = recorder.getProxy().getNextFolder(label, folder, false);
						if (newfolder != null)
							values = FileObjectProxy.readFolder(newfolder);
						else
							values = null;
					} finally {
						lock.readLock().unlock();
					}
					SlotsDbIterator.this.folder = newfolder;
					SlotsDbIterator.this.currentIdx = 0;
					SlotsDbIterator.this.folderValues = values;
					return null;
				}

			});
		} catch (PrivilegedActionException e) {
			throw new RuntimeException(e);
		}
	}

	private static boolean nextIsNewer(SampledValue current, SampledValue next) {
		if (current == null || next == null)
			return next != null;
		if (next == current)
			return false;
		return current.getTimestamp() < next.getTimestamp();
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("Logdata iterator does not support removal");
	};

}
