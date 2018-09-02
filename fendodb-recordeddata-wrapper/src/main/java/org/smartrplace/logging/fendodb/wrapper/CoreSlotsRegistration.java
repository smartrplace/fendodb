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
package org.smartrplace.logging.fendodb.wrapper;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.Instant;
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
		final FendoDbConfiguration config = FendoDbConfigurationBuilder.getInstance()
				.setUseCompatibilityMode(compatMode)
				.setMaxOpenFolders(maxOpenFolders)
				.setFlushPeriod(flushperiodMs)
				.setDataLifetimeInDays(limitDays)
				.setMaxDatabaseSize(limitSizeMb)
				.setDataExpirationCheckInterval(scanningItv)
				.build();
		final CloseableDataRecorder slots = slotsFactory.getInstance(Paths.get(folder), config);
		this.sreg = ctx.registerService(DataRecorder.class, slots, null);
		this.slots = slots;
		this.active = true;
		this.dbFolder = slots.getPath();
		slotsFactory.addDatabaseListener(this);
		
		clock.addClockChangeListener(this);
	}
	
	@Deactivate
	protected synchronized void deactivate() {
		try {
			clock.removeClockChangeListener(this);
		} catch (Exception ignore) { /* you never know... */}
		this.active = false;
		final ServiceRegistration<DataRecorder> sreg = this.sreg;
		final CloseableDataRecorder slots = this.slots;
		this.sreg = null;
		this.slots = null;
		this.active = false;
		this.ctx = null;
		this.dbFolder = null;
		try {
			slotsFactory.removeDatabaseListener(this);
		} catch (Exception ignore) {}
		if (sreg != null) {
			try {
				sreg.unregister();
			} catch (Exception ignore) {}
		}
		if (slots != null) {
			try {
				slots.close();
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
	
	private final boolean deleteDataFrom(final long t0) {
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
	
	private final static int getIntValue(final BundleContext ctx, final String property, final int defaultVal, final int minValue) {
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
	
	private final static long getLongValue(final BundleContext ctx, final String property, final long defaultVal, final long minValue) {
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
