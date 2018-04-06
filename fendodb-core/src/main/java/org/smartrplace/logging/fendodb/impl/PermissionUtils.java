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

import org.smartrplace.logging.fendodb.permissions.FendoDbPermission;

class PermissionUtils {
	
	final static void checkWritePermission(final Path path) {
		checkPermission(path, FendoDbPermission.WRITE);
	}
	
	final static void checkReadPermission(final Path path) {
		checkPermission(path, FendoDbPermission.READ);
	}
	
	final static void checkPermission(final Path path, final String action) {
		final String pathStr = path.toString();
		System.getSecurityManager().checkPermission(new FendoDbPermission(pathStr, pathStr, action));
	}
	
	final static boolean hasAdminPermission(final Path path) {
		try {
			checkPermission(path, FendoDbPermission.ADMIN);
			return true;
		} catch (SecurityException e) {
			return false;
		}	
	}
	
	final static boolean mayWrite(final Path path) {
		try {
			checkPermission(path, FendoDbPermission.WRITE);
			return true;
		} catch (SecurityException e) {
			return false;
		}	
	}
	
	final static boolean mayRead(final Path path) {
		try {
			checkPermission(path, FendoDbPermission.READ);
			return true;
		} catch (SecurityException e) {
			return false;
		}
	}
	

}
