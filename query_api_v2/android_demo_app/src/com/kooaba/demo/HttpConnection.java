/*
Copyright (c) 2012, kooaba AG
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

  * Redistributions of source code must retain the above copyright notice, this
    list of conditions and the following disclaimer.
  * Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.
  * Neither the name of the kooaba AG nor the names of its contributors may be
    used to endorse or promote products derived from this software without
    specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.kooaba.demo;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeaderValueParser;
import org.apache.http.message.HeaderValueParser;
import org.apache.http.message.ParserCursor;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.CharArrayBuffer;

import base64.Base64;


public class HttpConnection {

	protected static final int SUCCESS_CODE_FIRST = 200;
	protected static final int SUCCESS_CODE_LAST  = 299;
	protected static final DefaultHttpClient httpclient = new DefaultHttpClient();
	protected String host;
	protected int port;
	protected HttpUriRequest operationInFlight;

	public enum AuthMethod {NONE, BASIC, KWS};
	protected AuthMethod authMethod;

	// password resp. secret key
	protected String secret;

	// login resp. accessKey
	protected String access;
	
	// response attributes
	protected int      responseStatus;
	protected Header[] responseHeaders;

	public HttpConnection(String host, int port) {
		this.host = host;
		this.port = port;
		authMethod=AuthMethod.NONE;
	}

	public HttpConnection(String host, int port, String access, String secret, AuthMethod method)
	{
		this(host, port);
		this.secret=secret;
		this.access=access;
		authMethod=method;
	}
	
	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}
	
	public int getResponseStatus() {
		return responseStatus;
	}
	
	/** Return first occurrence of given header.
	 * 
	 * @param name  Name of the header. 
	 * 
	 * @return The first occurrence of the header with given name or null if no such header. 
	 */
	public String getFirstHeader(String name) {
		for (Header h : responseHeaders) {
			if (h.getName().equalsIgnoreCase(name)) {
				return h.getValue();
			}
		}
		return null;
	}

	public InputStream get(String remotePath) throws IOException, NoSuchAlgorithmException {
		return get(host, port, remotePath, new Properties());
	}
	
	public InputStream get(String remotePath, Properties params) throws IOException, NoSuchAlgorithmException {
		return get(host, port, remotePath, params);
	}

	public InputStream get(String host, int port, String remotePath) throws IOException, NoSuchAlgorithmException {
		return get(host, port, remotePath, new Properties());
	}
	
	public InputStream get(String host, int port, String remotePath, Properties params) throws IOException, NoSuchAlgorithmException {
		InputStream inputStream = null;
		try {
			// TODO: replace by a library function if exists
			StringBuilder queryBuilder = new StringBuilder();
			String queryParamSeparator = "";
			for (Enumeration<Object> param = params.keys(); param.hasMoreElements();) {
				String parameterName 	= (String)param.nextElement();
				String parameterValue 	= params.getProperty(parameterName);
				queryBuilder.append(queryParamSeparator).append(parameterName).append("=").append(parameterValue);
				queryParamSeparator = "&";
			}
			String query = queryBuilder.toString();
			URI uri = new URI("http", "", host, port, remotePath, query, "");
			HttpGet httpGet = new HttpGet(uri);
			operationInFlight = httpGet;
			addAuthHeaders(httpGet);
			HttpResponse response = httpclient.execute(httpGet);
			responseStatus = response.getStatusLine().getStatusCode();
			inputStream = extractBodyStream(response);
			if ((responseStatus < SUCCESS_CODE_FIRST) || (responseStatus > SUCCESS_CODE_LAST)) {
				String errorMessage = "Http error to: " + host + ":" + port + " . Http status: " + response.getStatusLine() +"; message: " + IOUtils.toString(inputStream);
				if (responseStatus == HttpStatus.SC_NOT_FOUND) {
					throw new FileNotFoundException(errorMessage);
				}
				throw new IOException(errorMessage);
			}
			responseHeaders = response.getAllHeaders();
			operationInFlight = null;
		} catch (URISyntaxException e) {
			operationInFlight = null;
			throw new IOException("URISyntaxException occured: " + e.toString());
		}
		return inputStream;
	}

	public InputStream post(HttpEntity httpEntity, String remotePath) throws IOException, NoSuchAlgorithmException {
		InputStream inputStream = null;
		try {
			URI uri = new URI("http", "", host, port, remotePath, "", "");
			HttpPost httpPost = new HttpPost(uri);
			operationInFlight = httpPost;
			// TODO: check if can send empty content (e.g. RecognitionServer does not work if empty)
			if (httpEntity.getContentLength() > 0) {
				httpPost.setEntity(httpEntity);
			}
			addAuthHeaders(httpPost);
			httpPost.addHeader("Accept", "application/json");
			httpPost.addHeader("Accept-Encoding", "gzip;q=1.0, identity; q=0.5, *;q=0");
			HttpResponse response = httpclient.execute(httpPost);
			responseStatus = response.getStatusLine().getStatusCode();
			inputStream = extractBodyStream(response);
			if ((responseStatus < SUCCESS_CODE_FIRST) || (responseStatus > SUCCESS_CODE_LAST)) {
				String errorMessage = "Http error to: " + host + ":" + port + " . Http status: " + response.getStatusLine() +"; message: " + IOUtils.toString(inputStream);
				if (responseStatus == HttpStatus.SC_NOT_FOUND) {
					throw new FileNotFoundException(errorMessage);
				}
				throw new IOException(errorMessage);
			}
			responseHeaders = response.getAllHeaders();
			operationInFlight = null;
		} catch (URISyntaxException e) {
			operationInFlight = null;
			throw new IOException("URISyntaxException occured: " + e.toString());
		}
		return inputStream;
	}

	public InputStream put(HttpEntity httpEntity, String remotePath) throws IOException, NoSuchAlgorithmException {
		InputStream inputStream = null;
		try {
			URI uri = new URI("http", "", host, port, remotePath, "", ""); 
			HttpPut httpPut = new HttpPut(uri);
			operationInFlight = httpPut;
			HttpParams httpParameters = httpPut.getParams();
			
			// Do not wait for OK for sending data
			httpParameters.setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, false);
			
			// Set timeout in milliseconds until a connection is established.
			int timeoutConnection = 150;
			HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);

			if (httpEntity.getContentLength() > 0) {
				httpPut.setEntity(httpEntity);
			}
			addAuthHeaders(httpPut);
			HttpResponse response = httpclient.execute(httpPut);
			responseStatus = response.getStatusLine().getStatusCode();
			inputStream = extractBodyStream(response);
			if ((responseStatus < SUCCESS_CODE_FIRST) || (responseStatus > SUCCESS_CODE_LAST)) {
				String errorMessage = "Http error to: " + host + ":" + port + " . Http status: " + response.getStatusLine() +"; message: " + IOUtils.toString(inputStream);
				if (responseStatus == HttpStatus.SC_NOT_FOUND) {
					throw new FileNotFoundException(errorMessage);
				}
				throw new IOException(errorMessage);
			}
			responseHeaders = response.getAllHeaders();
			operationInFlight = null;
		} catch (URISyntaxException e) {
			operationInFlight = null;
			throw new IOException("URISyntaxException occured: " + e.toString());
		}
		return inputStream;
	}

	public void abort() {
		if (operationInFlight != null) {
			try {
				operationInFlight.abort();
			} catch(UnsupportedOperationException e) {
			}
			operationInFlight = null;
		}
	}

	public void close() {
		abort();
	}


	protected void addAuthHeaders(HttpRequestBase meth) throws IOException, NoSuchAlgorithmException {
		if(authMethod==AuthMethod.KWS)
		{
			meth.addHeader("Date", DateUtils.formatDate(new Date()));
			meth.addHeader("Authorization", "KWS " + access + ":" + kwsSignature(meth));
		}
		else if(authMethod==AuthMethod.BASIC) {
			meth.addHeader(BasicScheme.authenticate(new UsernamePasswordCredentials(access, secret), "UTF-8", false));
		}
	}	

	/**
	 * Calculates the MD5 digest of the request body for POST or PUT methods
	 * @param httpMethod
	 * @return String MD5 digest as a hex string
	 * @throws IOException
	 * @throws NoSuchAlgorithmException 
	 */
	private static String contentMD5(HttpEntityEnclosingRequestBase httpMethod) throws IOException, NoSuchAlgorithmException {
		final String toHex = "0123456789abcdef";
		ByteArrayOutputStream requestOutputStream = new ByteArrayOutputStream();
		httpMethod.getEntity().writeTo(requestOutputStream);
		MessageDigest digest = MessageDigest.getInstance("MD5");
		digest.update(requestOutputStream.toByteArray());
		byte[] raw = digest.digest();
		StringBuilder hash = new StringBuilder();
		for (byte b : raw) {
			int i = b;
			if (i < 0) {
				i += 256; 
			}
			int high = i/16;
			hash.append(toHex.charAt(high));
			int low = i & 15;
			hash.append(toHex.charAt(low));
		}
		return hash.toString();
	}

	private static String contentMD5(HttpRequestBase method) throws IOException, NoSuchAlgorithmException {
		if(!(method instanceof HttpEntityEnclosingRequestBase)) {
			return "";
		}
		return contentMD5((HttpEntityEnclosingRequestBase)method);
	}


	/**
	 * Calculates the KWS signature of a HTTP request (POST or PUT)
	 * @param httpMethod
	 * @return String Signature
	 * @throws IOException
	 * @throws NoSuchAlgorithmException 
	 */
	//public static String kwsSignature(HttpEntityEnclosingRequestBase httpMethod) throws IOException {
	public String kwsSignature(HttpRequestBase httpMethod) throws IOException, NoSuchAlgorithmException {
		String method = httpMethod.getMethod();
		String hexDigest=contentMD5(httpMethod);

		CharArrayBuffer buf = new CharArrayBuffer(64);

		if(httpMethod instanceof HttpEntityEnclosingRequestBase) {
			buf.append(((HttpEntityEnclosingRequestBase)httpMethod).getEntity().getContentType().getValue());
		}
		HeaderValueParser parser = new BasicHeaderValueParser();
		ParserCursor cursor = new ParserCursor(0, buf.length());
		String contentType = parser.parseNameValuePair(buf, cursor).toString();
		String dateValue = httpMethod.getFirstHeader("Date").getValue();
		String requestPath = httpMethod.getURI().getPath();
		String signatureInput = method + "\n" + hexDigest + "\n" + contentType + "\n" + dateValue + "\n" + requestPath;

		String digestInput = secret + "\n\n" + signatureInput;
		MessageDigest digest = MessageDigest.getInstance("SHA1");
		digest.update(digestInput.getBytes("ISO-8859-1"));
		byte[] digestBytes = digest.digest();
		String encoded = Base64.encodeBytes(digestBytes);
		return encoded;
	}
	
	/** Extracts response body as a stream, handling compression.
	 * 
	 * @param response  HTTP response object
	 * @return Raw body as a stream or null if there is no body.
	 * @throws IOException 
	 * @throws IllegalStateException 
	 */
	protected InputStream extractBodyStream(HttpResponse response) throws IllegalStateException, IOException {
		if ((response == null) || (response.getEntity() == null)) {
			return null;
		}
		InputStream primaryStream = response.getEntity().getContent();
		Header encoding = response.getFirstHeader("Content-Encoding");
		if (encoding == null) {
			return primaryStream;
		}
		if (encoding.getValue().equals("gzip")) {
			return new GZIPInputStream(new BufferedInputStream(primaryStream));
		} else if (encoding.getValue().equals("identity")) {
			return primaryStream;
		} else {
			throw new IOException("Unsupported encoding '"+encoding.getValue()+"'.");
		}
	}

}
