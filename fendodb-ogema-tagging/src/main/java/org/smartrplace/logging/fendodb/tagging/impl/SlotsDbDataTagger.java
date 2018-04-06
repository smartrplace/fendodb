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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.ogema.core.application.Application;
import org.ogema.core.application.ApplicationManager;
import org.ogema.core.model.Resource;
import org.ogema.core.resourcemanager.ResourceAccess;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoDbFactory;
import org.smartrplace.logging.fendodb.tagging.api.TaggingUtils;

@Service(Application.class)
@Component
public class SlotsDbDataTagger implements Application, FendoDbFactory.SlotsDbListener {

	private volatile ApplicationManager appManager;
	private volatile BundleContext ctx;
	private ServiceRegistration<?> shellCommands;
	private AutoCloseable continuousTagger;

	@Reference FendoDbFactory factory;

	@Override
	public void start(ApplicationManager appManager) {
		this.appManager = appManager;
		this.ctx = appManager.getAppID().getBundle().getBundleContext();
		final Hashtable<String, Object> props = new Hashtable<String, Object>();
		props.put("osgi.command.scope", "fendodb");
		props.put("osgi.command.function", new String[] {
			"tagLogData", "tagFendoDbdata"
		});
		this.shellCommands = ctx.registerService(GogoCommands.class, new GogoCommands(this), props);
		String blockTagging = null;
		try {
			blockTagging = ctx.getProperty("org.smartrplace.logging.fendodb.blocklogdatatagging");
		} catch (SecurityException ignore) {}
		final boolean block = Boolean.parseBoolean(blockTagging);
		if (!block) {
			try {
				factory.addDatabaseListener(this);
				final CloseableDataRecorder recorder = getLogdataRecorder(ctx, factory);
				if (recorder != null) // if it did not start yet, we will be informed about it later on
					continuousTagger = new ContinuousTagger(recorder, appManager.getResourceAccess());
			} catch (Exception e) {
				appManager.getLogger().warn("Failed to register continuous log data tagger",e);
			}
		}

	}

	@Override
	public void stop(AppStopReason reason) {
		final ServiceRegistration<?> sreg = this.shellCommands;
		this.shellCommands = null;
		final AutoCloseable tagger = this.continuousTagger;
		this.continuousTagger = null;
		if (tagger != null) {
			try {
				tagger.close();
			} catch (Exception ignore) {}
		}
		if (sreg != null) {
			try {
				sreg.unregister();
			} catch (Exception ignore) {}
		}
		this.appManager = null;
		this.ctx = null;
		try {
			factory.removeDatabaseListener(this);
		} catch (Exception ignore) {}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	boolean tagLogData() throws IOException {
		try (final CloseableDataRecorder recorder = getLogdataRecorder(ctx, factory)) {
			if (recorder == null) {
				appManager.getLogger().info("Failed to tag log data; FendoDb instance not found.");
				return false;
			}
			final ResourceAccess ra = appManager.getResourceAccess();
			recorder.getAllTimeSeries().forEach(ts -> {
				final Resource r = ra.getResource(ts.getPath());
				if (r == null)
					return;
				final Map<String, List<String>> tags = TaggingUtils.getResourceTags(r);
				ts.setProperties((Map) tags);
			});
			return true;
		}
	}

	private final static String getLogdataRecorderPath(final BundleContext ctx, final FendoDbFactory factory) {
		String path0 = AccessController.doPrivileged(new PrivilegedAction<String>() {

			@Override
			public String run() {
				return ctx.getProperty("org.ogema.recordeddata.slotsdb.dbfolder");
			}
		});
		if (path0 == null)
			path0 = "data/slotsdb";
		return path0;
	}

	private final static CloseableDataRecorder getLogdataRecorder(final BundleContext ctx, final FendoDbFactory factory) throws IOException {
		return factory.getExistingInstance(Paths.get(getLogdataRecorderPath(ctx, factory)));
	}

	@Override
	public void databaseStarted(DataRecorderReference db) {
		final Path path = Paths.get(getLogdataRecorderPath(ctx, factory));
		if (!path.equals(db.getPath()))
			return;
		try (CloseableDataRecorder recorder = db.getDataRecorder()) {
			continuousTagger = new ContinuousTagger(recorder, appManager.getResourceAccess());
		} catch (Exception e) {
			appManager.getLogger().warn("Failed to register continuous log data tagger",e);
		}
	}

	@Override
	public void databaseClosed(DataRecorderReference db) {
		final ContinuousTagger tagger = (ContinuousTagger) this.continuousTagger;
		if (tagger == null || !tagger.path.equals(db.getPath()))
			return;
		this.continuousTagger = null; // no need to close it when the db has been closed already; just avoid holding a reference to it
	}

}
