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
