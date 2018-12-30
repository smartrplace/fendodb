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
package org.smartrplace.logging.fendodb.tagging.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.apache.felix.service.command.Descriptor;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.tagging.api.TaggingUtils;

public class GogoCommands {

	private final SlotsDbDataTagger app;

	GogoCommands(SlotsDbDataTagger app) {
		this.app = app;
	}

	@Descriptor("Apply standard tags to log data")
	public void tagLogData() throws IOException {
		app.tagLogData();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Descriptor("Tag FendoDb data which is not log data corresponding to OGEMA resources on this instance")
	public boolean tagFendoDbData(@Descriptor("Path to database root") final String path) throws IOException {
		final Path path2 = Paths.get(path);
		if (!Files.exists(path2)) {
			System.out.println("Path " + path + " does not exist");
			return false;
		}
		try (final CloseableDataRecorder recorder = app.factory.getInstance(path2)) {
			if (recorder.isEmpty())
				return false;
			recorder.getAllTimeSeries().forEach(ts -> {
				final Map<String, List<String>> map = TaggingUtils.getResourceTags(ts.getPath());
				ts.setProperties((Map) map);
			});
			return true;
		}
	}

}
