/*
  Copyright 2017 Esri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.​

  For additional information, contact:
  Environmental Systems Research Institute, Inc.
  Attn: Contracts Dept
  380 New York Street
  Redlands, California, USA 92373

  email: contracts@esri.com
*/

package com.esri.geoevent.transport.httpInboundEx;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.http.HttpRequest;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicNameValuePair;

import com.esri.ges.core.component.ComponentException;
import com.esri.ges.framework.i18n.BundleLogger;
import com.esri.ges.framework.i18n.BundleLoggerFactory;
import com.esri.ges.transport.TransportContext;
import com.esri.ges.transport.TransportDefinition;
import com.esri.ges.transport.http.HttpInboundTransport;
import com.esri.ges.transport.http.HttpTransportContext;
import com.esri.ges.transport.http.HttpTransportService;

public class HttpInboundExTransport extends HttpInboundTransport
{
	private static final BundleLogger	LOGGER	= BundleLoggerFactory.getLogger(HttpInboundExTransport.class);

	private String						headerParams;
	private String						username;
	private String						password;

	public HttpInboundExTransport(TransportDefinition definition) throws ComponentException
	{
		super(definition);
	}

	@Override
	public synchronized void start()
	{
		super.start();
	}

	@Override
	public synchronized void stop()
	{
		super.stop();
	}

	@Override
	public synchronized void setup()
	{
		super.setup();
		headerParams = getProperty("header").getValueAsString();
		username = getProperty(HttpTransportService.USERNAME_PROPERTY).getValueAsString();
		password = getProperty(HttpTransportService.PASSWORD_PROPERTY).getValueAsString();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void beforeConnect(TransportContext context)
	{
		if (!(context instanceof HttpTransportContext))
			return;

		HttpRequest request = ((HttpTransportContext) context).getHttpRequest();
		String decryptedPwd = "";

		if (username != null && !username.isEmpty())
		{
			if (password.length() > 0)
			{
				try
				{
					decryptedPwd = cryptoService.decrypt(password);
				}
				catch (Exception error)
				{
					LOGGER.error("PASSWORD_DECRYPT_ERROR");
				}
			}

			String auth = username + ":" + decryptedPwd;
			String basic_auth = new String(Base64.encodeBase64((auth).getBytes()));
			request.addHeader("Authorization", "Basic " + basic_auth);
		}

		ArrayList<NameValuePair> headerParameters;
		headerParameters = new ArrayList<NameValuePair>();

		try
		{
			Map<String, String> paramMap = parseParameters(headerParams);
			Iterator<Entry<String, String>> it = paramMap.entrySet().iterator();
			while (it.hasNext())
			{
				Map.Entry pairs = (Map.Entry) it.next();
				headerParameters.add(new BasicNameValuePair((String) pairs.getKey(), (String) pairs.getValue()));
				((HttpGet) request).setHeader((String) pairs.getKey(), (String) pairs.getValue());
				it.remove();
			}
		}
		catch (Exception error)
		{
			LOGGER.error(error.getMessage(), error);
		}
	}

	private Map<String, String> parseParameters(String params) throws UnsupportedEncodingException
	{
		Map<String, String> headerMap = new LinkedHashMap<String, String>();
		String[] pairs = params.split(",");

		for (String pair : pairs)
		{
			int idx = pair.indexOf(":");
			if (idx > 0)
				headerMap.put(StringUtils.newStringUtf8(pair.substring(0, idx).getBytes("UTF-8")), StringUtils.newStringUtf8(pair.substring(idx + 1).getBytes("UTF-8")));
		}
		return headerMap;
	}
}
