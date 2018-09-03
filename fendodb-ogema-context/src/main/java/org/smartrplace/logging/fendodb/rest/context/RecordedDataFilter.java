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
package org.smartrplace.logging.fendodb.rest.context;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessControlContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ogema.accesscontrol.PermissionManager;
import org.ogema.accesscontrol.RestAccess;
import org.osgi.service.component.ComponentServiceObjects;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.context.ServletContextHelper;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;
import org.smartrplace.logging.fendodb.permissions.FendoDbPermission;
import org.smartrplace.logging.fendodb.rest.RecordedDataServlet;

@Component(
		service=ServletContextHelper.class,
		property = {
				HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME + "=" + RecordedDataServlet.CONTEXT,
				HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_PATH + "=/rest/fendodb"
		}
)
public class RecordedDataFilter extends ServletContextHelper {
	
	@Reference
	private ComponentServiceObjects<PermissionManager> permManService;
    @Reference
    private ComponentServiceObjects<RestAccess> restAccessService;
	
    @Override
    public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response) throws IOException {
    	final RestAccess restAcc = restAccessService.getService();
    	try {
    		final AccessControlContext ctx = restAcc.getAccessContext(request, response);
    		if (ctx == null) {
    			request.removeAttribute(REMOTE_USER);
    			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    			return false;
    		}
    		if (!checkAccess(request, ctx) || !setUsername(request)) {
    			request.removeAttribute(REMOTE_USER);
    			response.sendError(HttpServletResponse.SC_FORBIDDEN);
    			return false;
    		}
    		return true;
    	} catch (ServletException e) {
    		request.removeAttribute(REMOTE_USER);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return false;
		} finally {
    		restAccessService.ungetService(restAcc);
    	}
    }
    
    private boolean setUsername(final HttpServletRequest request) {
    	final PermissionManager permMan = permManService.getService();
    	try {
    		final String user = permMan.getAccessManager().getCurrentUser();
    		if (user == null)
    			return false;
    		request.setAttribute(REMOTE_USER, user);
    		return true;
    	} finally {
    		permManService.ungetService(permMan);
    	}
    }

	private boolean checkAccess(final HttpServletRequest req, final AccessControlContext ctx) {
		final String path = req.getParameter("db"); // Parameters.PARAM_DB
		if (path == null) {// listing all databases... 
			if (!"GET".equalsIgnoreCase(req.getMethod()))
				return false;
			final PermissionManager permMan = permManService.getService();
			try {
				permMan.setAccessContext(ctx); // FIXME that does not work... need a different approach to limit the dbs to be shown
				return true;
			} finally {
				permManService.ungetService(permMan);
			}
		}
		final Path database = Paths.get(path).normalize();
		/*
		 * Get The authentication information
		 */
		final String method = req.getMethod();
		final String action;
		// TODO more fine-grained
		switch (method) {
		case "DELETE":
		case "PUT":
			action = "admin";
			break;
		case "POST":
			action = "write";
			break;
		default:
			action = "read";
		}
		final FendoDbPermission perm = new FendoDbPermission("perm", database.toString().replace('\\', '/'), action);
		try {
			ctx.checkPermission(perm);
			return true;
		} catch (SecurityException e) {
			return false;
		}
	}

}
