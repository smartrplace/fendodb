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
package org.smartrplace.logging.fendodb.impl;

import java.nio.file.Path;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Collections;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ogema.core.channelmanager.measurements.FloatValue;
import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.recordeddata.RecordedDataStorage;
import org.smartrplace.logging.fendodb.CloseableDataRecorder;
import org.smartrplace.logging.fendodb.FendoDbConfiguration;
import org.smartrplace.logging.fendodb.FendoDbConfigurationBuilder;
import org.smartrplace.logging.fendodb.FendoDbFactory;
import org.smartrplace.logging.fendodb.FendoTimeSeries;
import org.smartrplace.logging.fendodb.permissions.FendoDbPermission;

public class SecurityTest extends SlotsDbTest {

	@Before
	public void setSecurityManager() {
		Policy.setPolicy(new SimplePolicy(null, null));
		System.setSecurityManager(new SecurityManager());
	}
	
	@After
	public void removeSecurityManager() {
		System.setSecurityManager(null);
	}
	
	private static class SimplePolicy extends Policy {
		
		private final Collection<Permission> allowedPermissions;
		private final Collection<Permission> deniedPermissions;
		
		SimplePolicy(Collection<Permission> allowedPermissions, Collection<Permission> deniedPermissions) {
			this.allowedPermissions = allowedPermissions != null ? allowedPermissions : Collections.emptyList();
			this.deniedPermissions = deniedPermissions != null ? deniedPermissions : Collections.emptyList(); 
		}
		
		@Override
		public boolean implies(ProtectionDomain domain, Permission permission) {
			for (Permission allowed: allowedPermissions) {
				if (allowed.implies(permission))
					return true;
			}
			for (Permission denied : deniedPermissions) {
				if (denied.implies(permission))
					return false;
			}
			return true;
		}
		
		@Override
		public void refresh() {}
		
//		 TODO required?
		@Override
		public PermissionCollection getPermissions(CodeSource codesource) {
			 Permissions p = new Permissions();
		     p.add(new AllPermission());  // enable everything
		     return p;
		}
		
	}

	private final static FendoDbPermission slotsAll = new FendoDbPermission("", "*", "*");
	private final static FendoDbPermission slotsWrite = new FendoDbPermission("", "*", FendoDbPermission.WRITE);
	
	private final static void denySinglePermission(final Permission p) {
		Policy.setPolicy(new SimplePolicy(null, Collections.singleton(p)));
	}
	
	@Test
	public void testDeny() throws Exception {
		denySinglePermission(slotsAll);
		final FendoDbFactory factory = createFactory();
		try (final CloseableDataRecorder slots = factory.getInstance(testPath)) {
			Assert.fail("SlotsDb instance should not have been accessible " + slots);
		} catch (SecurityException e) {
		} finally {
			closeFactory(factory);
		}
	}
	
	@Test
	public void readOnlyPermissionWorks() throws Exception {
		final Path path;
		final int nrConfigs = 3;
		final FendoDbFactory factory = createFactory();
		try (final CloseableDataRecorder slots = factory.getInstance(testPath)) {
			Assert.assertNotNull(slots);
			TestUtils.createAndFillRandomSlotsData(slots, nrConfigs, 4, 5 * ONE_DAY, ONE_DAY/10);
			path = slots.getPath();
			Assert.assertEquals(nrConfigs, slots.getAllRecordedDataStorageIDs().size());

		}
		denySinglePermission(slotsWrite);
		final FendoDbConfiguration readOnlyConfig = FendoDbConfigurationBuilder.getInstance()
				.setReadOnlyMode(true)
				.build();
		try (final CloseableDataRecorder slots = factory.getInstance(path, readOnlyConfig)) {
			Assert.assertNotNull("Failed to open existing SlotsDb instance",slots);
			Assert.assertEquals(nrConfigs, slots.getAllRecordedDataStorageIDs().size());
			Assert.assertEquals(nrConfigs, slots.getAllTimeSeries().size());
			try {
				final RecordedDataStorage storage = slots.createRecordedDataStorage("newteststorage", null);
				Assert.fail("Missing security exception; database opened in read only mode, but could create a new timeseries: "+storage);
			} catch (SecurityException expected) {}
			final RecordedDataStorage rds = slots.getRecordedDataStorage(slots.getAllRecordedDataStorageIDs().get(0));
			try {
				final SampledValue last = rds.getPreviousValue(Long.MAX_VALUE);
				Assert.assertNotNull(last);
				rds.insertValue(new SampledValue(new FloatValue(23), last.getTimestamp() + 1000, Quality.GOOD));
				Assert.fail("Missing security exception; database opened in read only mode, but could add values to timeseries");
			} catch (SecurityException expected) {}
		}
		final FendoDbConfiguration writeConfig = FendoDbConfigurationBuilder.getInstance(readOnlyConfig)
				.setReadOnlyMode(false)
				.build();
		try (final CloseableDataRecorder slots = factory.getInstance(path, writeConfig)) {
		 	Assert.fail("Database opened in write mode, despite missing permission");
		} catch (SecurityException expected) {}
		closeFactory(factory);
	}
	
	@Test
	public void readOnlyWorks2() throws Throwable {
		final int nrConfigs = 3;
		final String propKey = "test";
		final String propVal = "testVal";
		final String id;
		final FendoDbFactory factory = createFactory();
		try (final CloseableDataRecorder slots = factory.getInstance(testPath)) {
			Assert.assertNotNull(slots);
			TestUtils.createAndFillRandomSlotsData(slots, nrConfigs, 4, 5 * ONE_DAY, ONE_DAY/10);
			Assert.assertEquals(nrConfigs, slots.getAllRecordedDataStorageIDs().size());
			final FendoTimeSeries ts = slots.getAllTimeSeries().get(0);
			id = ts.getPath();
			ts.setProperty(propKey, propVal);
		}
		denySinglePermission(slotsWrite);
		try (final CloseableDataRecorder slots = factory.getInstance(testPath)) {
			Assert.assertNotNull(slots);
			Assert.assertEquals(nrConfigs, slots.getAllRecordedDataStorageIDs().size());
			final FendoTimeSeries ts = slots.getRecordedDataStorage(id);
			Assert.assertNotNull(ts);
			try {
				ts.setProperty(propKey, "something");
				Assert.fail("Succeeded to write a time series property despite missing write permission");
			} catch (SecurityException expected) {}
			Assert.assertEquals("Property value changed",propVal, ts.getFirstProperty(propKey));
		}
		closeFactory(factory);
	}
	
}
