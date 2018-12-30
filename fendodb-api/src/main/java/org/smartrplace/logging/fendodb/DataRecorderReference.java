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
