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
