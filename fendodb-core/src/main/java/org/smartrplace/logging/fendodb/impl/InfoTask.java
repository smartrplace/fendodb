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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.ogema.core.logging.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO tests for these functions
abstract class InfoTask extends TimerTask {
	
	private static final Logger logger = LoggerFactory.getLogger(InfoTask.class);
	private volatile boolean running = false;
	final FileObjectProxy proxy;
	private final LogLevel level;
	private final boolean requiresFolderLock;
	private final boolean runOnClose;

	InfoTask(FileObjectProxy proxy, boolean requiresFolderLock, boolean runOnClose) {
		this(proxy, requiresFolderLock, runOnClose, LogLevel.INFO);
	}
	
	InfoTask(FileObjectProxy proxy, boolean requiresFolderLock, boolean runOnClose, LogLevel level) {
		this.proxy = proxy;
		this.level = level;
		this.requiresFolderLock = requiresFolderLock;
		this.runOnClose = runOnClose;
	}
	
	final boolean isRunning() {
		return running;
	}
	
	@Override
	public final void run() {
		running = true;
		if (requiresFolderLock)
			proxy.folderLock.writeLock().lock();
		try {
			if (level == LogLevel.TRACE)
				logger.trace("Running info task {}",this);
			else if (level == LogLevel.DEBUG)
				logger.debug("Running info task {}",this);
			else if (level == LogLevel.INFO)
				logger.info("Running info task {}",this);
			if (requiresFolderLock)
				proxy.clearOpenFilesHashMap();
			runInternal();
		} catch (IOException e) {
			logger.error("Data operation of {} failed in IOException.",this,e);
		} finally {
			running = false;
			if (requiresFolderLock)
				proxy.folderLock.writeLock().unlock();
		}
	}
	
	void stopTask() {
		try {
			cancel();
			boolean wasRunning = waitForTask();
			if (!wasRunning && runOnClose) {
				run(); // execute task once more, so no data is lost
			}
		} catch (Exception e) {
			logger.error("Error stopping task and executing it",e);
		}
	}
	
	private boolean waitForTask() throws InterruptedException {
		if (!isRunning())
			return false;
		for (int i=0; i< 100; i++) {
			if (!isRunning())
				break;
			Thread.sleep(50);
		}
		return true;
	}
	
	abstract void runInternal() throws IOException;
	
	static void deleteRecursiveFolder(final Path folder) {
		if (Files.exists(folder)) {
			try {
				if (Files.isDirectory(folder)) {
					try (final Stream<Path> stream = Files.list(folder)) {
						stream.forEach(f -> deleteRecursiveFolder(f));
					}
				}
				Files.delete(folder);
			} catch (IOException e) {
				logger.error("Failed to delete file",e);
			}
		}
	}
	
	static class Flusher extends InfoTask {
		
		Flusher(FileObjectProxy proxy) {
			super(proxy, false, true, LogLevel.TRACE);
		}
		
		@Override
		void runInternal() throws IOException {
			Iterator<FileObjectList> itr = proxy.openFilesHM.values().iterator();
			while (itr.hasNext()) {
				itr.next().flush();
			}
			logger.trace("Data from {} folders flushed to disk.",proxy.openFilesHM.size());
		}

	}
	
	// FIXME take into account unit!
	static class DeleteJob extends InfoTask {
		
		DeleteJob(FileObjectProxy proxy) {
			super(proxy, true, false);
		}
		
		@Override
		void runInternal() throws IOException {
			deleteFoldersOlderThen(proxy.limit_folders);
		}

		@Override
		void stopTask() {
			try {
				cancel();
			} catch (Exception e) {
				logger.error("Error stopping task",e);
			}
		}
		
		private void deleteFoldersOlderThen(int limit_days) throws IOException {
			final long limit = proxy.getTime() - (86400000L * (limit_days+1));
			deleteFolders(limit, true);
		}
		
//		final void deleteFoldersNewerThan(final long t0) throws IOException {
//			final long start =  TimeUtils.getCurrentStart(t0, proxy.unit);
//			deleteFolders(start, false);
//		}
		
		final void deleteFolders(final long limit, final boolean olderOrNewer) throws IOException {
			proxy.reloadDays();
			final Iterator<Path> iterator = proxy.days.iterator();
			while (iterator.hasNext()) {
				final Path curElement = iterator.next();
				logger.trace("Deleting log data... checking: {}", curElement);
				try {
					final long folderTime = !proxy.useCompatibilityMode ? Long.parseLong(curElement.getFileName().toString()) :
						TimeUtils.parseCompatibilityFolderName(curElement.getFileName().toString());
					final boolean matches = olderOrNewer ? folderTime < limit : folderTime >= limit;
					if (matches) { // compare folder 's oldest value to limit
						logger.info("Folder: {} is " + (olderOrNewer ? "older" : "newer") 
								+ " than limit. Will be deleted.");
						deleteRecursiveFolder(curElement);
						if (Files.exists(curElement)) 
							logger.warn("Folder still exists after deletion attempt: {}", curElement);
					}
					else {
						if (olderOrNewer)
							break;
					}
						
				} catch (NumberFormatException e) {
					logger.error("Failed to parse folder name as long {}",curElement,e);
					continue;
				}
			}
			proxy.reloadDays();
		}
		
	}

	static class SizeWatcher extends InfoTask {

		SizeWatcher(FileObjectProxy proxy) {
			super(proxy, true, false);
		}

		@Override
		void runInternal() throws IOException {
			long size = getDiskUsage(proxy.rootNode);
			while ((size / 1000000 > proxy.limit_size) && (proxy.days.size() >= 2)) { // avoid deleting current folder
				if (logger.isInfoEnabled()) {
					logger.info("Exceeded Maximum Database Size: " + proxy.limit_size + " MB. Current size: " + (size / 1000000)
							+ " MB. Deleting: " + proxy.days.get(0));
				}
				deleteOldestFolder();
				size = getDiskUsage(proxy.rootNode);
			}
		}

		private void deleteOldestFolder() throws IOException {
			deleteRecursiveFolder(proxy.days.get(0));
			proxy.days.remove(0);
			proxy.clearOpenFilesHashMap();
		}

		/*
		 * recursive function to get the size of a folder. sums up all files. needs an initial LONG to store size to.
		 */
		private long getDiskUsage(final Path folder) throws IOException {
			final AtomicLong length = new AtomicLong(0);
			recursive_size_walker(folder, length);
			return length.get();
		}

		private void recursive_size_walker(final Path folder, final AtomicLong length) throws IOException {
			try (final Stream<Path> stream = Files.list(folder)) {
				stream.forEach(f -> {
					try {
						if (Files.isDirectory(f)) {
							recursive_size_walker(f, length);
                        }
                        length.getAndAdd(Files.size(f));
                    } catch (NoSuchFileException nsfe) {
                        logger.debug("file deleted: {}", nsfe.getMessage());
					} catch (IOException e) {
						logger.error("Failed to determine folder size",e);
					}
				});
			}
		}
		
	}
	
	static class DaysReloading extends InfoTask {

		private final SlotsDb db;
		
		DaysReloading(SlotsDb db) {
			// actually requires a folder lock, but this is obtained in the method Slotsdb#reloadDays.. and MUST NOT be acquired earlier,
			// otherwise the lock order policy would be violated
			super(db.proxy, false, false);
			this.db = db;
		}
		
		@Override
		void runInternal() throws IOException {
			db.reloadDays();
		}
		
	}
	
	
}
