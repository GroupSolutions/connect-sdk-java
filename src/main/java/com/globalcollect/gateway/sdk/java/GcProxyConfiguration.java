package com.globalcollect.gateway.sdk.java;

import java.net.URI;

/**
 * HTTP proxy configuration.
 */
public class GcProxyConfiguration {

	private String scheme;

	private String host;

	private int port;

	private String username;

	private String password;

	public GcProxyConfiguration(String host, int port) {
		this("http", host, port);
	}

	public GcProxyConfiguration(String host, int port, String username, String password) {
		this("http", host, port, username, password);
	}

	public GcProxyConfiguration(String scheme, String host, int port) {
		this(scheme, host, port, null, null);
	}

	public GcProxyConfiguration(String scheme, String host, int port, String username, String password) {
		if (scheme == null || scheme.trim().isEmpty()) {
			throw new IllegalArgumentException("scheme is required");
		}
		if (host == null || host.trim().isEmpty()) {
			throw new IllegalArgumentException("host is required");
		}
		if (port <= 0 || port > 65535) {
			throw new IllegalArgumentException("port is invalid");
		}
		this.scheme = scheme;
		this.host = host;
		this.port = port;
		this.username = username;
		this.password = password;
	}

	public GcProxyConfiguration(URI address) {
		this(address, null, null);
	}

	public GcProxyConfiguration(URI address, String username, String password) {
		this(address.getScheme(), address.getHost(), getPort(address), username, password);
	}

	private static int getPort(URI address) {
		int port = address.getPort();
		if (port != -1) {
			return port;
		} else if ("http".equalsIgnoreCase(address.getScheme())) {
			return 80;
		} else if ("https".equalsIgnoreCase(address.getScheme())) {
			return 443;
		} else {
			throw new IllegalArgumentException("unsupported scheme: " + address.getScheme());
		}
	}

	public String getScheme() {
		return scheme;
	}
	public void setScheme(String scheme) {
		this.scheme = scheme;
	}
	public GcProxyConfiguration withScheme(String scheme) {
		this.scheme = scheme;
		return this;
	}

	public String getHost() {
		return host;
	}
	public void setHost(String host) {
		this.host = host;
	}
	public GcProxyConfiguration withHost(String host) {
		this.host = host;
		return this;
	}

	public int getPort() {
		return port;
	}
	public void setPort(int port) {
		this.port = port;
	}
	public GcProxyConfiguration withPort(int port) {
		this.port = port;
		return this;
	}

	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public GcProxyConfiguration withUsername(String username) {
		this.username = username;
		return this;
	}

	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public GcProxyConfiguration withPassword(String password) {
		this.password = password;
		return this;
	}
}
