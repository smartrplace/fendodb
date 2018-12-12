package org.smartrplace.logging.fendodb.grafana;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ogema.webadmin.AdminWebAccessManager.StaticRegistration;
import org.osgi.framework.Bundle;

@SuppressWarnings("serial")
class RemoteResourcesPage extends HttpServlet {
	
	private final AtomicReference<StaticRegistration> reg;
	private final String str; 
	
	public RemoteResourcesPage(String remote, Bundle bundle, AtomicReference<StaticRegistration> reg) throws IOException {
		if (remote.endsWith("/"))
			remote = remote.substring(0, remote.length()-1);
		this.reg = reg;
		final URL url = bundle.getResource("/webresources/index.html");
		if (url == null)
			throw new SecurityException("AdminPermission[this, RESOURCE] lacking to access file");
		final StringBuilder sb = new StringBuilder();
		try (final InputStream in = url.openStream();
				final BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			reader.lines()
				.forEach(sb::append);
		}
		this.str = sb.toString().replace("/org/ogema/tools/grafana-base", remote);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		final String otp = generateOtpHtml(reg.get(), req);
		final String html = str.replaceFirst("\\<head\\>", otp);
		resp.setCharacterEncoding("UTF-8");
		resp.setContentType("text/html");
		resp.getWriter().write(html);
		resp.setStatus(HttpServletResponse.SC_OK);
	}

	private static String generateOtpHtml(final StaticRegistration reg, final HttpServletRequest req) {
		final String[] otup = reg.generateOneTimePwd(req);
		final StringBuilder sb=  new StringBuilder();
		sb.append("<head><script>var otusr='")
			.append(otup[0])
			.append("';var otpwd='")
			.append(otup[1])
			.append("';</script>");
		return sb.toString();
	}
	
}

