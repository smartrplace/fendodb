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
package org.smartrplace.logging.fendodb.visualisation;

import java.io.IOException;

import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbConfigurationBuilder;

import de.iwes.widgets.api.widgets.sessionmanagement.OgemaHttpRequest;

class Utils {
	
	final static CloseableDataRecorder getDataRecorder(final FendoSelector slotsSelector, final OgemaHttpRequest req, final boolean readOrWrite) 
			throws IOException {
		final DataRecorderReference ref = slotsSelector.getSelectedItem(req);
		if (ref == null)
			return null;
		final FendoDbConfiguration cfg = FendoDbConfigurationBuilder.getInstance(ref.getConfiguration())
				.setReadOnlyMode(readOrWrite)
				.build();
		return ref.getDataRecorder(cfg);
	}

}
