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
package org.smartrplace.logging.fendodb;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Get or create SlotsDb instances. SlotsDb is a time series database, used in particular
 * for storing OGEMA log data.   
 */
public interface FendoDbFactory {

	/**
	 * Creates a new slotsDb instance, or returns the existing one for the specified folder.
	 * The default configuration is used (see {@link FendoDbConfiguration}), i.e. data is never 
	 * cleaned up.
	 * The returned instance should be {@link CloseableDataRecorder#close() closed} explicitly, 
	 * when it is not used any more.
	 * 
	 * @param baseFolder
	 * 		The base folder for the database. If the folder does not exist, it is created. 
	 * 		If it exists and has the structure of a SlotsDb database, the corresponding 
	 * 		database instance is returned. If it exists but does not conform to the SlotsDb
	 * 		format, an IllegalArgumentException is thrown.
	 * @return
	 * 		The requested SlotsDb instance. May return null, if the database is currently being updated. 
	 * @throws IOException
	 * 		If baseFolder is not accessible.
	 * @throws NullPointerException
	 * 		If baseFolder is null.
	 * @throws SecurityException 
	 *  	If access is not allowed
	 */
	CloseableDataRecorder getInstance(Path baseFolder) throws IOException;
	
	/**
	 * Creates a new slotsDb instance, or returns the existing one for the specified folder.
	 * The instance should be {@link CloseableDataRecorder#close() closed} explicitly, 
	 * when it is not used any more.
	 * 
	 * @param baseFolder
	 * 		The base folder for the database. If the folder does not exist, it is created. 
	 * 		If it exists and has the structure of a SlotsDb database, the corresponding 
	 * 		database instance is returned. If it exists but does not conform to the SlotsDb
	 * 		format, an IllegalArgumentException is thrown.
	 * @param configuration
	 * 		Global configuration settings for this database instance. This is partly ignored if
	 * 		the database instance for this folder already exists.
	 * @return
	 * 		The requested SlotsDb instance. May return null, if the database is currently being updated. 
	 * @throws IOException
	 * 		If baseFolder is not accessible.
	 * @throws NullPointerException
	 * 		If baseFolder is null.
 	 * @throws SecurityException 
	 *  	If access is not allowed
	 */
	CloseableDataRecorder getInstance(Path baseFolder, FendoDbConfiguration configuration) throws IOException;
	
	/**
	 * Returns an existing database for the specified folder.
	 * The instance should be {@link CloseableDataRecorder#close() closed} explicitly, 
	 * when it is not used any more.
	 * 
	 * @param baseFolder
	 * 		The base folder for the database. If the folder does not exist, null is returned.
	 * @return
	 * @throws IOException
	 * 		If baseFolder is not accessible.
	 * @throws NullPointerException
	 * 		If baseFolder is null.
 	 * @throws SecurityException 
	 *  	If access is not allowed
	 */
	// TODO configuration options? like read-only mode?
	CloseableDataRecorder getExistingInstance(Path baseFolder) throws IOException;

	/**
	 * Get all SlotsDb instances
	 * @return
	 */
	Map<Path, DataRecorderReference> getAllInstances();
	
	boolean databaseExists(final Path path);
	
	/**
	 * The listener will be informed about all existing and all newly added database instances
	 * @param listener
	 */
	void addDatabaseListener(SlotsDbListener listener);
	void removeDatabaseListener(SlotsDbListener listener);
	
	interface SlotsDbListener {
		
		void databaseStarted(DataRecorderReference db);
		void databaseClosed(DataRecorderReference db);
		
	}
	

}
