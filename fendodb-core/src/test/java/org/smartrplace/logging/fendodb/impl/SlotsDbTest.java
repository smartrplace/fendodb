/**
 * Copyright 2011-2018 Fraunhofer-Gesellschaft zur Förderung der angewandten Wissenschaften e.V.
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
