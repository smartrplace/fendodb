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
package org.smartrplace.logging.fendodb.impl;

import java.nio.file.Path;
import java.security.AccessControlContext;

import org.smartrplace.logging.fendodb.accesscontrol.FendoDbAccessControl;
import org.smartrplace.logging.fendodb.permissions.FendoDbPermission;

class PermissionUtils {
	
	final static void checkWritePermission(final Path path, final FendoDbAccessControl accessManager) {
		checkPermission(path, FendoDbPermission.WRITE, accessManager);
	}
	
	final static void checkReadPermission(final Path path, final FendoDbAccessControl accessManager) {
		checkPermission(path, FendoDbPermission.READ, accessManager);
	}
	
	/**
	 * @param path
	 * @param action
	 * @param accessManager
	 * @throws SecurityException
	 */
	final static void checkPermission(final Path path, final String action, final FendoDbAccessControl accessManager) {
		final String pathStr = path.toString();
		final AccessControlContext ctx = accessManager == null ? null : accessManager.getAccessControlContext();
		final FendoDbPermission perm = new FendoDbPermission(pathStr, pathStr, action);
		if (ctx == null)
			System.getSecurityManager().checkPermission(perm);
		else
			ctx.checkPermission(perm);
	}
	
	final static boolean hasAdminPermission(final Path path, final FendoDbAccessControl accessManager) {
		try {
			checkPermission(path, FendoDbPermission.ADMIN, accessManager);
			return true;
		} catch (SecurityException e) {
			return false;
		}	
	}
	
	final static boolean mayWrite(final Path path, final FendoDbAccessControl accessManager) {
		try {
			checkPermission(path, FendoDbPermission.WRITE, accessManager);
			return true;
		} catch (SecurityException e) {
			return false;
		}	
	}
	
	final static boolean mayRead(final Path path, final FendoDbAccessControl accessManager) {
		try {
			checkPermission(path, FendoDbPermission.READ, accessManager);
			return true;
		} catch (SecurityException e) {
			return false;
		}
	}
	

}
