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

import java.io.IOException;
import java.nio.file.Files;
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

	InfoTask(FileObjectProxy proxy, boolean requiresFolderLock) {
		this(proxy, requiresFolderLock, LogLevel.INFO);
	}
	
	InfoTask(FileObjectProxy proxy, boolean requiresFolderLock, LogLevel level) {
		this.proxy = proxy;
		this.level = level;
		this.requiresFolderLock = requiresFolderLock;
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
			if (!wasRunning) {
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
			super(proxy, false, LogLevel.TRACE);
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
			super(proxy, true);
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
			super(proxy, true);
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
		private final long getDiskUsage(final Path folder) throws IOException {
			final AtomicLong length = new AtomicLong(0);
			recursive_size_walker(folder, length);
			return length.get();
		}

		private final void recursive_size_walker(final Path folder, final AtomicLong length) throws IOException {
			try (final Stream<Path> stream = Files.list(folder)) {
				stream.forEach(f -> {
					try {
						length.getAndAdd(Files.size(f));
						if (Files.isDirectory(f))
							recursive_size_walker(f, length);
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
			super(db.proxy, false);
			this.db = db;
		}
		
		@Override
		void runInternal() throws IOException {
			db.reloadDays();
		}
		
	}
	
	
}
