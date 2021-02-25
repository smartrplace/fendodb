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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

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

	// synchronized by read-write lock of the respective SlotsDbStorage
	// adding a file requires a write lock, reading requires a read lock
	private List<FileObject> files;
	private String foldername;
	private long firstTS;
	private int size;
	private final boolean useCompatibilityMode;
	
	/**
	 * Creates a FileObjectList<br>
	 * and creates a FileObject for every File
	 * 
	 * @param foldername
	 * @throws IOException
	 */
	public FileObjectList(String foldername, FendoCache cache, String encodedId, boolean useCompatibilityMode) throws IOException {
		// File folder = new File(foldername);
		this.foldername = foldername;
		this.useCompatibilityMode = useCompatibilityMode;
		reLoadFolder(cache, encodedId);
	}
	
	public String getFolderName() {
		return foldername;
	}

	/**
	 * Reloads the List; requires folder read lock (see FileObjectProxy)
	 * 
	 * @throws IOException
	 */
	final void reLoadFolder(final FendoCache cache, final String encodedId) throws IOException {

		File folder = new File(foldername);

		files = new ArrayList<>(1);
		if (folder.isDirectory()) {
			for (File file : folder.listFiles()) {
				if (file.length() >= 16) { // otherwise is corrupted or empty
					// file.
					final String filename = file.getName();
					String[] split = filename.split("\\.");
					if (("." + split[split.length - 1]).equals(SlotsDb.FILE_EXTENSION)) {
						files.add(FileObject.getFileObject(file, cache.getCache(encodedId, filename)));
					}
				}
				else {
					file.delete();
				}
			}
			if (files.size() > 1) {
				sortList(files);
			}
		}

		size = files.size();

		/*
		 * set first Timestamp for this FileObjectList if there are no files -> first TS = TS@ 00:00:00 o'clock.
		 */
		if (size == 0) {
			firstTS = !useCompatibilityMode ? Long.parseLong(folder.getParentFile().getName())
					: TimeUtils.parseCompatibilityFolderName(folder.getParentFile().getName());
//			
//			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
//			try {
//				sdf.parse(folder.getParentFile().getName());
//			} catch (ParseException e) {
//				throw new IOException("Unable to parse Timestamp from folder: " + folder.getParentFile().getName()
//						+ ". Expected Folder in yyyyMMdd Format!");
//			}
//			firstTS = sdf.getCalendar().getTimeInMillis();
		}
		else {
			firstTS = files.get(0).getStartTimeStamp();
		}
		folder = null;
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
		return "FileObjectList: " + foldername;
	}
}
