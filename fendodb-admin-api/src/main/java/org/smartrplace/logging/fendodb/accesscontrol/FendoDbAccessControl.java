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
package org.smartrplace.logging.fendodb.accesscontrol;

import java.security.AccessControlContext;

/**
 * Optional administrative service
 */
public interface FendoDbAccessControl {

	/**
	 * @param ctx
	 * @throws SecurityException if the caller does not have org.osgi.framework.AdminPermission 
	 * for the fendoDb core bundle with action "execute".
	 */
	void setAccessControlContext(AccessControlContext ctx);
	AccessControlContext getAccessControlContext();
	
}
