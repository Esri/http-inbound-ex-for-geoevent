/*
  Copyright 1995-2014 Esri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

  For additional information, contact:
  Environmental Systems Research Institute, Inc.
  Attn: Contracts Dept
  380 New York Street
  Redlands, California, USA 92373

  email: contracts@esri.com
*/

package com.esri.geoevent.transport.httpInboundEx;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.esri.ges.core.component.ComponentException;
import com.esri.ges.transport.TransportContext;
import com.esri.ges.transport.TransportDefinition;
import com.esri.ges.transport.http.HttpInboundTransport;
import com.esri.ges.transport.http.HttpTransportContext;
import com.esri.ges.transport.http.HttpTransportService;

public class HttpInboundExTransport extends HttpInboundTransport
{
  static final private Log log = LogFactory.getLog(HttpInboundExTransport.class);
  private String headerParams;
  private String username;
  private String password;
  
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
  
  @Override
  public void beforeConnect(TransportContext context)
  {
    if(! (context instanceof HttpTransportContext))
      return;
    
    HttpRequest request = ((HttpTransportContext)context).getHttpRequest();
    String decryptedPwd = "";

    if( username != null && !username.isEmpty() )
    {
      if(password.length()>0)
      {
        try
        {
          decryptedPwd = cryptoService.decrypt(password);
        }
        catch (Exception e)
        {
          log.error("Failed to decrypt password.");
        }
      }
      
      String auth = username+":"+decryptedPwd;
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
          Map.Entry pairs = (Map.Entry)it.next();
          headerParameters.add(new BasicNameValuePair((String)pairs.getKey(), (String)pairs.getValue()));
          ((HttpGet)request).setHeader((String)pairs.getKey(), (String)pairs.getValue());            
          it.remove();
      }        
    }
    catch (UnsupportedEncodingException e)
    {
      log.error(e);
    }
    catch (Exception e)
    {
      log.error(e);
    }
  }
  
  private Map<String, String> parseParameters(String params) throws UnsupportedEncodingException
  {
    Map<String, String> query_pairs = new LinkedHashMap<String, String>();
    String[] pairs = params.split(",");
    for (String pair : pairs) {
        int idx = pair.indexOf(":");
        if(idx>0)
          query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
    }
    return query_pairs;
  }
  
  @Override
  public void onReceive(TransportContext context)
  {
    System.out.println("received at: " + new Date());
    super.onReceive(context);
    
    /*
    if (!(context instanceof HttpTransportContext))
      return;

    HttpResponse response = ((HttpTransportContext) context).getHttpResponse();
    HttpEntity entity = ( response != null ) ? response.getEntity() : null;

    if (entity != null)
    {
      log.debug("Got response from http request.");
    }

    StatusLine statusLine = response.getStatusLine();
    if (statusLine.getStatusCode() != HttpStatus.SC_OK)
    {
      String message = ((HttpTransportContext) context).getHttpRequest().getRequestLine().getUri()
          + " :  Request failed(" + statusLine.toString() + ")";
      log.error(message);
      return;
    }

    if (true)
    {
      BufferedReader rd=null;
      InputStreamReader isrd=null;
      try
      {
        String charset = "UTF-8";
        isrd=new InputStreamReader(new StreamingGZIPInputStream(entity.getContent()), charset);
        rd = new BufferedReader(isrd);
        String line;
        try
        {
          while ((line = rd.readLine()) != null)
          {
            //if (longPollingThread.running != true)
            //{
            //  break;
            //}
            byte[] bytes = line.getBytes();
            ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length);
            byteBuffer.put(bytes);
            byteBuffer.flip();
            byteListener.receive(byteBuffer, "");

            //log.debug(line);
            System.out.println(line);
          }
        }
        finally
        {
          close(rd);
          close(isrd);
        }
      } catch (IllegalStateException e)
      {
        log.error(e.getMessage(),e);
      } catch (IOException e)
      {
        log.error(e.getMessage(),e);
      }
      
      return;
    }

    byte[] output = null;

    try
    {
      if (entity != null)
      {
        if (entity.getContentEncoding() != null)
        {
          if (entity.getContentEncoding().getValue().equals("gzip"))
          {
            output = unpackRaw(EntityUtils.toByteArray(entity));
          } else
          {
            output = EntityUtils.toByteArray(entity);
          }
        } else
        {
          output = EntityUtils.toByteArray(entity);
        }
      }
    } catch (IOException e)
    {
      String message = ((HttpTransportContext) context).getHttpRequest().getRequestLine().getUri()
          + " :  Request failed(Error parsing response.)";
      log.error(message, e);
      return;
    }

    /*
    Header[] s = response.getHeaders("last-modified");
    Date responseLastModified = null;
    if (s.length > 0)
    {
      if (s[0] != null && !s[0].getValue().isEmpty())
      {
        responseLastModified = dateFormatter.toDate(s[0].getValue());
      }
    }

    if ( !blockBasedOnLastModified(responseLastModified) )
    {
      if (output != null)
      {
        ByteBuffer byteBuffer = ByteBuffer.allocate(output.length);
        byteBuffer.put(output);
        byteBuffer.flip();
        byteListener.receive(byteBuffer, "");
      }

      if (responseLastModified != null && honorLastModified)
      {
        lastModified = responseLastModified;
      }
    }
    */
    
  }
  
  private void close(Closeable isrd)
  {
    if(isrd != null)
    {
      try
      {
        isrd.close();
      }
      catch(Exception e)
      {
        //ignore
      }
    }
  }
  
  private byte[] unpackRaw(byte[] b) throws IOException
  {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ByteArrayInputStream bais = new ByteArrayInputStream(b);

    GZIPInputStream zis = new GZIPInputStream(bais);
    byte[] tmpBuffer = new byte[256];
    int n;
    while ((n = zis.read(tmpBuffer)) >= 0)
      baos.write(tmpBuffer, 0, n);
    zis.close();

    return baos.toByteArray();
  }
}
