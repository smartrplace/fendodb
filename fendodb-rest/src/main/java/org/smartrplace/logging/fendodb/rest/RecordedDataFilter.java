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
package org.smartrplace.logging.fendodb.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.Base64;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.ogema.accesscontrol.Constants;
import org.ogema.accesscontrol.PermissionManager;
import org.ogema.accesscontrol.UserRightsProxy;
import org.ogema.core.administration.AdminApplication;
import org.ogema.core.administration.AdministrationManager;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartrplace.logging.fendodb.permissions.FendoDbPermission;

// main part of this class is copied from the OGEMA rest project (class RestAccess)

@Component
@Property(name = HttpWhiteboardConstants.HTTP_WHITEBOARD_FILTER_PATTERN, value = RecordedDataServlet.ALIAS)
@Service(Filter.class)
public class RecordedDataFilter implements Filter {
	
	private static final Logger logger = LoggerFactory.getLogger(RecordedDataFilter.class);
    @Reference
    private PermissionManager permMan;
    @Reference
    private AdministrationManager adminMan;
	
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {}
	
	@Override
	public void destroy() {}

	@Override
	public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) 
			throws IOException, ServletException {
		if (!checkAccess((HttpServletRequest) request)) {
			((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		chain.doFilter(request, response);
	}

	boolean checkAccess(final HttpServletRequest req) {
		final String path = req.getParameter(Parameters.PARAM_DB);
		if (path == null || path.trim().isEmpty())
			return true; // FIXME depends on method
		final Path database = Paths.get(path).normalize();
		final HttpSession ses = req.getSession();
		
		/*
		 * Get The authentication information
		 */
		final String[] userPw = getUserAndPw(req);
		if (userPw == null)
			return false;
		if (!permMan.isSecure())
			return true;
		final String usr = userPw[0];
		final String pwd = userPw[1];
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
		final boolean result;
		if (check1TimePW(ses, usr, pwd)) {
			// Get the AccessControlContex of the involved app
			final AdminApplication aaa = adminMan.getAppById(usr);
			if (aaa == null) {
				result = false;
			} else {
				final ProtectionDomain pda = aaa.getID().getApplication().getClass().getProtectionDomain();
				result = pda.implies(perm);
			}
		}
		else if (checkM2MUserPW(usr, pwd)) {
			final UserRightsProxy urp = permMan.getAccessManager().getUrp(usr);
			if (urp == null)
				return false;
			result = urp.getClass().getProtectionDomain().implies(perm);
		} else 
			result = false;
		if (!result)
			logger.debug("REST access denied.");
		if (logger.isTraceEnabled()) {
			final StringBuilder msg = new StringBuilder();
			if (!result)
				msg.append("Failed access from ");
			else
				msg.append("REST access granted to ");
			msg.append(req.getRemoteAddr()).append(' ').append(", logged in user: ")
				.append(req.getRemoteUser()).append(", rest user: ").append(usr);
			logger.trace(msg.toString());
		}
		return result;
	}
	
	private static String[] getUserAndPw(final HttpServletRequest req) {
		final String basicAuth = req.getHeader("Authorization");
		if (basicAuth != null) {
			if (!basicAuth.startsWith("Basic "))
				return null;
			final String encoded = basicAuth.substring("Basic ".length());
			final String decoded = new String(Base64.getDecoder().decode(encoded.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
			final String[] userPw = decoded.split(":");
			if (userPw.length != 2)
				return null;
			return userPw;
		}
		final String usr = req.getParameter(Constants.OTUNAME);
		final String pwd = req.getParameter(Constants.OTPNAME);
		if (usr == null || pwd == null)
			return null;
		return new String[] {usr,pwd};
	}

	/*
	 * Access to the rest server could be performed by web resources including dynamic content or by any external entity
	 * that is registered as an authorized user. In case of a web resource one time password and user are checked to be
	 * known in the current session. In case of an external entity the AccessManager is asked for the authorization of
	 * the user. In both cases the requested URL has to contain the pair of parameter OTPNAME and OTUNAME.
	 */
	private boolean check1TimePW(HttpSession ses, String usr, String pwd) {
		/*
		 * If the app is already registered with this one time password the access is permitted.
		 */
		return permMan.getWebAccess().authenticate(ses, usr, pwd);
	}

	private final boolean checkM2MUserPW(String usr, String pwd) {
		/*
		 * Is there an user registered with the credentials attached to the request?
		 */
		return permMan.getAccessManager().authenticate(usr, pwd, false);

	}

}
