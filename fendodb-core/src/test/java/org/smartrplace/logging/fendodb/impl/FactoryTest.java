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

import org.junit.After;
import org.junit.Before;
import org.smartrplace.logging.fendodb.FendoDbFactory;

/**
 * Collection of helper methods. All tests extend this class.
 */
public class FactoryTest extends SlotsDbTest {

	protected FendoDbFactory factory;

	@Before
	public void startFactory() {
		factory = createFactory();
	}

	@After
	public void closeFactory() {
		closeFactory(factory);
	}

	protected void restartFactory() throws InterruptedException {
		Thread.sleep(100); // wait for pending write operations
		closeFactory();
		startFactory();
	}

}
