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
package org.smartrplace.logging.fendodb.wrapper;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.ogema.core.administration.FrameworkClock;
import org.ogema.core.administration.FrameworkClock.ClockChangeListener;
import org.ogema.core.administration.FrameworkClock.ClockChangedEvent;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.recordeddata.DataRecorder;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.LoggerFactory;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.DataRecorderReference;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbConfigurationBuilder;
import org.smartrplace.logging.fendodb.FendoDbFactory;

@Component(immediate=true)
public class CoreSlotsRegistration implements FendoDbFactory.SlotsDbListener, ClockChangeListener {

	@Reference
	private FendoDbFactory slotsFactory;
	@Reference
	private FrameworkClock clock;
	private ServiceRegistration<DataRecorder> sreg;
	private volatile boolean active = false;
	private CloseableDataRecorder slots;
	private Path dbFolder;
	private BundleContext ctx;
	
	
	@Activate
	protected synchronized void activate(final BundleContext ctx) throws IOException {
		this.ctx = ctx;
		final String folder0 = ctx.getProperty("org.ogema.recordeddata.slotsdb.dbfolder");
		final String folder = folder0 != null ? folder0 : "./data/slotsdb";
		final String compatMode0 = ctx.getProperty("org.ogema.recordeddata.slotsdb.compatibility_mode");
		final int maxOpenFolders = getIntValue(ctx, "org.ogema.recordeddata.slotsdb.max_open_folders", 512, 8);
		final long flushperiodMs = getLongValue(ctx, "org.ogema.recordeddata.slotsdb.flushperiod_ms", 10000, 0);
		final int limitDays = getIntValue(ctx, "org.ogema.recordeddata.slotsdb.limit_days", 14, 0);
		final int limitSizeMb = getIntValue(ctx, "org.ogema.recordeddata.slotsdb.limit_size", 100, 0);
		final long scanningItv = getLongValue(ctx, "org.ogema.recordeddata.slotsdb.scanning_interval", 24 * 60 * 60 * 1000, 5 * 60 * 1000);
		
		final boolean compatMode = compatMode0 != null ? Boolean.parseBoolean(compatMode0) : false;
		FendoDbConfigurationBuilder configBuilder = FendoDbConfigurationBuilder.getInstance()
				.setUseCompatibilityMode(compatMode)
				.setMaxOpenFolders(maxOpenFolders)
				.setFlushPeriod(flushperiodMs)
				.setDataLifetimeInDays(limitDays)
				.setMaxDatabaseSize(limitSizeMb)
				.setDataExpirationCheckInterval(scanningItv)
				.setReloadDaysInterval(0);

		String timeUnitName = ctx.getProperty("org.ogema.recordeddata.slotsdb.timeperiod");
		if (timeUnitName != null) {
			final ChronoUnit unit = ChronoUnit.valueOf(timeUnitName);
			configBuilder.setTemporalUnit(unit);
		}
		
		final FendoDbConfiguration config = configBuilder.build();
		final CloseableDataRecorder slotsInstance = slotsFactory.getInstance(Paths.get(folder), config);
		this.sreg = ctx.registerService(DataRecorder.class, slotsInstance, null);
		this.slots = slotsInstance;
		this.active = true;
		this.dbFolder = slotsInstance.getPath();
		slotsFactory.addDatabaseListener(this);
		
		clock.addClockChangeListener(this);
	}
	
	@Deactivate
	protected synchronized void deactivate() {
		try {
			clock.removeClockChangeListener(this);
		} catch (Exception ignore) { /* you never know... */}
		this.active = false;
		final ServiceRegistration<DataRecorder> registration = this.sreg;
		final CloseableDataRecorder slotsInstance = this.slots;
		this.sreg = null;
		this.slots = null;
		this.active = false;
		this.ctx = null;
		this.dbFolder = null;
		try {
			slotsFactory.removeDatabaseListener(this);
		} catch (Exception ignore) {}
		if (registration != null) {
			try {
				registration.unregister();
			} catch (Exception ignore) {}
		}
		if (slotsInstance != null) {
			try {
				slotsInstance.close();
			} catch (Exception ignore) {}
		}
	}
	
	@Override
	public synchronized void databaseClosed(DataRecorderReference ref) {
		if (active && ref.getPath().equals(slots.getPath())) {
			this.slots = null;
			final ServiceRegistration<DataRecorder> sreg = this.sreg;
			this.sreg = null;
			if (sreg != null) {
				try {
					sreg.unregister();
				} catch (Exception ignore) {}
			}
		}
	}
	
	@Override
	public synchronized void databaseStarted(DataRecorderReference ref) {
		if (active && slots == null && dbFolder.equals(ref.getPath())) {
			try {
				this.slots = ref.getDataRecorder();
			} catch (IOException e) {
				LoggerFactory.getLogger(CoreSlotsRegistration.class).error("",e);
				return;
			}
			this.sreg = ctx.registerService(DataRecorder.class, slots,	null);
		}
		
	}
	
	@Override
	public void clockChanged(ClockChangedEvent e) {
		final CloseableDataRecorder slots = this.slots;
		if (!active || slots == null || !slots.isActive())
			return;
		final long t = clock.getExecutionTime();
		final Optional<SampledValue> lastTime = slots.getAllTimeSeries().stream()
				.map(ts -> ts.getPreviousValue(Long.MAX_VALUE))
				.filter(sv -> sv != null)
				.sorted((sv0,sv1) -> -Long.compare(sv0.getTimestamp(), sv1.getTimestamp()))
				.findFirst();
		if (!lastTime.isPresent())
			return;
		final long lastTimestamp = lastTime.get().getTimestamp();
		if (lastTimestamp <= t)
			return;
		if (deleteDataFrom(t)) {
			LoggerFactory.getLogger(CoreSlotsRegistration.class).info("Framework time changed, which led to "
					+ "deletion of log data. New timestamp: {}",t);
		}
	}
	
	private boolean deleteDataFrom(final long t0) {
		return AccessController.doPrivileged(new PrivilegedAction<Boolean>() {

			@Override
			public Boolean run() {
				try {
					return slots.deleteDataAfter(Instant.ofEpochMilli(t0));
				} catch (IOException e) {
					LoggerFactory.getLogger(CoreSlotsRegistration.class).error("Failed to delete future log data",e);
					return false;
				}
			}
		});
	}
	
	private static int getIntValue(final BundleContext ctx, final String property, final int defaultVal, final int minValue) {
		final String val = ctx.getProperty(property);
		if (val != null) {
			try {
				final int value = Integer.parseInt(val);
				if (value >= minValue)
					return value;
			} catch (NumberFormatException ok) {}
		}
		return defaultVal;
	}
	
	private static long getLongValue(final BundleContext ctx, final String property, final long defaultVal, final long minValue) {
		final String val = ctx.getProperty(property);
		if (val != null) {
			try {
				final long value = Long.parseLong(val);
				if (value >= minValue)
					return value;
			} catch (NumberFormatException ok) {}
		}
		return defaultVal;
	}
	
}
