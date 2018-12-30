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

import java.security.AccessControlContext;
import java.security.Permission;

import org.osgi.framework.AdminPermission;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.smartrplace.logging.fendodb.accesscontrol.FendoDbAccessControl;

@Component(service=FendoDbAccessControl.class)
public class AccessControlManager implements FendoDbAccessControl {

	private Permission PERM;
	private final ThreadLocal<AccessControlContext> localContext = new ThreadLocal<AccessControlContext>();
	
	@Activate
	protected void activate(BundleContext ctx) {
		PERM = new AdminPermission(ctx.getBundle(), AdminPermission.EXECUTE);
	}
	
	@Override
	public void setAccessControlContext(AccessControlContext ctx) {
		final SecurityManager sman = System.getSecurityManager();
		if (sman != null)
			sman.checkPermission(PERM);
		localContext.set(ctx);
	}

	@Override
	public AccessControlContext getAccessControlContext() {
		return localContext.get();
	}
	
}
