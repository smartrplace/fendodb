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

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class representing a folder in a SlotsDatabase.<br>
 * <br>
 * ./rootnode/20110129/ID1/1298734198000.opm <br>
 * /1298734598000.opm <br>
 * /ID2/ <br>
 * /20110130/ID1/ <br>
 * /ID2/ <br>
 * <br>
 * Usually there is only 1 File in a Folder/FileObjectList<br>
 * But there might be more then 1 file in terms of reconfiguration.<br>
 * <br>
 * 
 */
public final class FileObjectList {

	private final static Logger LOGGER = LoggerFactory.getLogger(FileObjectList.class);
	// synchronized by read-write lock of the respective SlotsDbStorage
	// adding a file requires a write lock, reading requires a read lock
	private List<FileObject> files;
	private final String foldername;
	private final String dayFolderName;
	private long firstTS;
	private int size;
	private final boolean useCompatibilityMode;
	
	
	private Path basePath;
	FileObjectList(Path basePath, String foldername, String dayFolderName, FendoCache cache, String encodedId, boolean useCompatibilityMode) throws IOException {
		// File folder = new File(foldername);
		this.foldername = foldername;
		this.dayFolderName = dayFolderName;
		this.useCompatibilityMode = useCompatibilityMode;
		this.basePath = basePath;
		reLoadFolder(cache, encodedId);
	}
	
	/**
	 * Creates a FileObjectList<br>
	 * and creates a FileObject for every File
	 * 
	 * @param foldername
	 * @throws IOException
	 */
	FileObjectList(String foldername, String dayFolderName, FendoCache cache, String encodedId, boolean useCompatibilityMode) throws IOException {
		// File folder = new File(foldername);
		this.foldername = foldername;
		this.dayFolderName = dayFolderName;
		this.useCompatibilityMode = useCompatibilityMode;
		reLoadFolder(cache, encodedId);
	}
	
	public String getFolderName() {
		return foldername;
	}
	
	private Path getBasePath() {
		return basePath != null ? basePath : //Path.of(foldername);
				FileSystems.getDefault().getPath(foldername);
	}

	/**
	 * Reloads the List; requires folder read lock (see FileObjectProxy)
	 * 
	 * @throws IOException
	 */
	final void reLoadFolder(final FendoCache cache, final String encodedId) throws IOException {
		Path folder = getBasePath();
		files = new ArrayList<>(1);
		if (Files.isDirectory(folder)) {
			// stream obtained with Files#list *must* be closed or leaks file handle (to directory)
			try ( Stream<Path> s = Files.list(folder)) {
				for (Path file : s.collect(Collectors.toList())) {
					final String filename = file.getFileName().toString();
					if (filename.endsWith(SlotsDb.FILE_EXTENSION)) {
						if (Files.size(file) >= 16) {
							files.add(FileObject.getFileObject(file, cache.getCache(encodedId, filename)));
						} else { // corrupted or empty
							Files.delete(file);
						}
					}
				}
			}
			if (files.size() > 1) {
				sortList(files);
			}
		}
		size = files.size();		
		LOGGER.trace("reloadFolder: {}, {}", folder, size);
		/*
		 * set first Timestamp for this FileObjectList if there are no files -> first TS = TS@ 00:00:00 o'clock.
		 */
		if (size == 0) {
			firstTS = !useCompatibilityMode ? Long.parseLong(dayFolderName)
					: TimeUtils.parseCompatibilityFolderName(dayFolderName);
		}
		else {
			firstTS = files.get(0).getStartTimeStamp();
		}
		//System.out.printf("%s reloadFolder(%s) = %s%n", Thread.currentThread().getName(), folder, files);
	}

	/*
	 * bubble sort to sort files in directory. usually there is only 1 file, might be 2... will also work for more. but
	 * not very fast.
	 */
	private static final void sortList(final List<FileObject> toSort) {
		int j = 0;
		FileObject tmp;
		boolean switched = true;
		while (switched) {
			switched = false;
			j++;
			for (int i = 0; i < toSort.size() - j; i++) {
				if (toSort.get(i).getStartTimeStamp() > toSort.get(i + 1).getStartTimeStamp()) {
					tmp = toSort.get(i);
					toSort.set(i, toSort.get(i + 1));
					toSort.set(i + 1, tmp);
					switched = true;
				}
			}
		}
	}

	/**
	 * Returns the last created FileObject
	 */
	public FileObject getCurrentFileObject() {
		return get(size - 1);
	}

	/**
	 * Returns the File Object at any position in list.
	 * 
	 * @param position
	 */
	public FileObject get(int position) {
		return files.get(position);
	}

	/**
	 * Returns the size (Number of Files in this Folder/FileObjectList)
	 */
	public int size() {
		return size;
	}

	/**
	 * Closes all files in this List. This will also cause DataOutputStreams to be flushed.
	 * 
	 * @throws IOException
	 */
	public void closeAllFiles() throws IOException {
		for (FileObject f : files) {
			f.close();
		}
	}

	/**
	 * Returns a FileObject in this List for a certain Timestamp. If there is no FileObject containing this Value, null
	 * will be returned.
	 * 
	 * @param timestamp
	 */
	public FileObject getFileObjectForTimestamp(long timestamp) {
		if (files.size() > 1) {
			for (FileObject f : files) {
				if (f.getStartTimeStamp() <= timestamp && f.getTimestampForLatestValue() >= timestamp) {
					// File
					// found!
					return f;
				}
			}
		}
		else if (files.size() == 1) {
			if (files.get(0).getStartTimeStamp() <= timestamp && files.get(0).getTimestampForLatestValue() >= timestamp) {
				// contains
				// this
				// TS
				return files.get(0);
			}
		}
		return null;
	}

	/**
	 * Returns All FileObject in this List, which contain Data starting at given timestamp.
	 * 
	 * @param timestamp
	 */
	public List<FileObject> getFileObjectsStartingAt(long timestamp) {
		List<FileObject> toReturn = new ArrayList<>(1);
		for (int i = 0; i < files.size(); i++) {
			FileObject fo = files.get(i);
			if (fo.getTimestampForLatestValue() >= timestamp) {
				toReturn.add(fo);
			}
		}
		return toReturn;
	}

	/**
	 * Returns all FileObjects in this List.
	 */
	public List<FileObject> getAllFileObjects() {
		return files;
	}

	/**
	 * Returns all FileObjects which contain Data before ending at given timestamp.
	 * 
	 * @param timestamp
	 */
	public List<FileObject> getFileObjectsUntil(long timestamp) {
		List<FileObject> toReturn = new Vector<FileObject>(1);
		for (int i = 0; i < files.size(); i++) {
			if (files.get(i).getStartTimeStamp() <= timestamp) {
				toReturn.add(files.get(i));
			}
		}
		return toReturn;
	}

	/**
	 * Returns all FileObjects which contain Data from start to end timestamps
	 * 
	 * @param start
	 * @param end
	 */
	public List<FileObject> getFileObjectsFromTo(long start, long end) {
		List<FileObject> toReturn = new Vector<FileObject>(1);
		if (files.size() > 1) {
			for (int i = 0; i < files.size(); i++) {
				FileObject fo = files.get(i);
				if ((fo.getStartTimeStamp() <= start && fo.getTimestampForLatestValue() >= start)
						|| (fo.getStartTimeStamp() <= end && fo.getTimestampForLatestValue() >= end)
						|| (fo.getStartTimeStamp() >= start && fo.getTimestampForLatestValue() <= end)) {
					// needed files.
					toReturn.add(fo);
				}
			}
		}
		else if (files.size() == 1) {
			FileObject fo = files.get(0);
			if (fo.getStartTimeStamp() <= end && fo.getTimestampForLatestValue() >= start) {
				// contains
				// this
				// TS
				toReturn.add(fo);
			}
		}
		return toReturn;
	}

	/**
	 * Returns first recorded timestamp of oldest FileObject in this list. If List is empty, this timestamp will be set
	 * to 00:00:00 o'clock
	 */
	public long getFirstTS() {
		return firstTS;
	}

	/**
	 * Flushes all FileObjects in this list.
	 * 
	 * @throws IOException
	 */
	public void flush() throws IOException {
		for (FileObject f : files) {
			f.flush();
		}
	}
	
	@Override
	public String toString() {
		return "FileObjectList '" + foldername + "': " + files;
	}
}
