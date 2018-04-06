/**
 * This file is part of OGEMA.
 *
 * OGEMA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3
 * as published by the Free Software Foundation.
 *
 * OGEMA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OGEMA. If not, see <http://www.gnu.org/licenses/>.
 */
package org.smartrplace.logging.fendodb.impl;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.osgi.framework.BundleContext;
import org.smartrplace.logging.fendodb.FendoDbFactory;
import org.smartrplace.logging.fendodb.impl.SlotsDb;
import org.smartrplace.logging.fendodb.impl.SlotsDbFactoryImpl;

/**
 * Collection of helper methods. All tests extend this class.
 */
public class SlotsDbTest {

	static final Path testPath = Paths.get(SlotsDb.DB_TEST_ROOT_FOLDER);
	// the basic unit according to which SlotsDb organises its file storage
	public static final long ONE_DAY = 24 * 3600 * 1000;

	public static SlotsDbFactoryImpl createFactory() {
		final SlotsDbFactoryImpl factory = new SlotsDbFactoryImpl();
		try {
			final Method m  = SlotsDbFactoryImpl.class.getDeclaredMethod("activate", BundleContext.class);
			m.setAccessible(true);
			m.invoke(factory, (BundleContext) null);
			return factory;
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

	public static void closeFactory(FendoDbFactory factory) {
		Objects.requireNonNull(factory);
		try {
			final Method m  = SlotsDbFactoryImpl.class.getDeclaredMethod("deactivate");
			m.setAccessible(true);
			m.invoke(factory);
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

	@Before
	public void deleteTestFiles() {
		deleteTree(new File(SlotsDb.DB_TEST_ROOT_FOLDER));
	}

	@After
	public void deleteTestFiles2() {
		deleteTestFiles();
	}

	/**
	 * @param path
	 *            Deletes all files and folders under under the specified path.
	 */
	static void deleteTree(final File path) {

		if (path.exists()) {
			if (path.isDirectory()) {
				for (File file : path.listFiles()) {
					deleteTree(file);
				}
			}
			if (!path.delete()) {
				System.out.println(path + " could not be deleted!");
			}
		}
		Assert.assertFalse("Deleted file still exists: " + path,path.exists());
	}

}
