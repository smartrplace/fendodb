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
