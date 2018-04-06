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
