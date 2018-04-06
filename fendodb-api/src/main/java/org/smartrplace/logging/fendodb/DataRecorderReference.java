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

import org.smartrplace.logging.fendodb.permissions.FendoDbPermission;

public interface DataRecorderReference {
	
	Path getPath();
	
	FendoDbConfiguration getConfiguration();

	/**
	 * The data recorder should be {@link CloseableDataRecorder#close() closed} explicitly, 
	 * when it is not used any more.
	 * @return
	 * @throws SecurityException 
	 * 		if the caller does not have the {@link FendoDbPermission#READ read} permission 
	 * 		for the database.
	 * @throws IOException
	 */
	CloseableDataRecorder getDataRecorder() throws IOException;
	
	/**
	 * The data recorder should be {@link CloseableDataRecorder#close() closed} explicitly, 
	 * when it is not used any more.
	 * @param configuration
	 * @return
	 * @throws SecurityException 
	 * 		if the caller does not have the {@link FendoDbPermission#READ read} permission 
	 * 		for the database, or write access is requested, and the caller does not
	 * 		have {@link FendoDbPermission#WRITE write} permission.
	 * @throws IOException
	 */
	CloseableDataRecorder getDataRecorder(FendoDbConfiguration configuration) throws IOException;
	
}
