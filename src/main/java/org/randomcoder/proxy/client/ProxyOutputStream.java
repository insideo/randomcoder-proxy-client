package org.randomcoder.proxy.client;

import org.apache.commons.codec.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.log4j.Logger;
import org.randomcoder.proxy.client.config.ProxyConfigurationListener;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;

/**
 * <code>OutputStream</code> implementation which wraps a remote proxied
 * connection.
 *
 * <pre>
 * Copyright (c) 2007, Craig Condit. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS &quot;AS IS&quot;
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * </pre>
 */
public class ProxyOutputStream extends OutputStream {
  private static final Logger logger =
      Logger.getLogger(ProxyOutputStream.class);

  private static final long PING_FREQUENCY = 30000L;

  private final HttpClient client;
  private final HttpHost httpHost;
  private final HttpClientContext localContext;
  private final String proxyUrl;
  private final String connectionId;
  private final PingThread pingThread;
  private final ProxyConfigurationListener listener;

  /**
   * Creates a new output stream connected to a remote HTTP proxy.
   *
   * @param client       HTTP client to use for connections
   * @param httpHost     HTTP host to connect to
   * @param localContext local HTTP context
   * @param proxyUrl     proxy URL
   * @param connectionId connection id
   * @param listener     proxy configuration listener
   * @throws IOException if an error occurs while establishing communications
   */
  public ProxyOutputStream(HttpClient client, HttpHost httpHost,
      HttpClientContext localContext, String proxyUrl, String connectionId,
      ProxyConfigurationListener listener) throws IOException {
    logger.debug("Creating proxy output stream");

    this.client = client;
    this.httpHost = httpHost;
    this.localContext = localContext;
    this.proxyUrl = proxyUrl;
    this.connectionId = connectionId;
    this.listener = listener;

    // ping to verify connection
    ping();

    // create a ping thread to keep remote connection alive
    pingThread = new PingThread(this, PING_FREQUENCY);
    pingThread.start();

    logger.debug("Proxy output stream created");
  }

  @Override public void write(byte[] b, int off, int len) throws IOException {
    logger.debug("write(byte[],int,int)");

    HttpPost post = null;
    try {
      logger.debug("PROXY OUTPUT: Writing " + len + " bytes");

      post = new HttpPost(
          proxyUrl + "/send?id=" + URLEncoder.encode(connectionId, "UTF-8"));
      post.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build());
      post.setProtocolVersion(new ProtocolVersion("http", 1, 1));
      post.setHeader("User-Agent", "Randomcoder-Proxy 1.0-SNAPSHOT");

      byte[] output = new byte[len];
      System.arraycopy(b, off, output, 0, len);
      post.setEntity(new ByteArrayEntity(output));

      HttpResponse httpResponse = client.execute(httpHost, post, localContext);

      int status = httpResponse.getStatusLine().getStatusCode();

      logger.debug("PROXY OUTPUT: Got result: " + status);

      if (status == HttpStatus.SC_OK) {
        String response = IOUtils
            .toString(httpResponse.getEntity().getContent(), Charsets.UTF_8);
        logger.debug(response);

        if (listener != null)
          listener.dataSent(null, len);

        return;
      }
    } finally {
      try {
        if (post != null)
          post.releaseConnection();
      } catch (Throwable ignored) {
      }
    }
  }

  @Override public void write(byte[] b) throws IOException {
    logger.debug("write(byte[])");

    write(b, 0, b.length);
  }

  @Override public void write(int b) throws IOException {
    logger.debug("write(int)");
    byte[] buf = new byte[1];
    buf[0] = (byte) (b & 0xff);

    write(buf, 0, 1);
  }

  @Override public void close() throws IOException {
    pingThread.shutdown();
    try {
      pingThread.join();
    } catch (InterruptedException ignored) {
    }
  }

  @Override public void flush() throws IOException {
    // nothing to do
  }

  /**
   * Sends a keep-alive packet to the remote stream.
   *
   * @throws IOException if the remote stream is closed or an I/O error occurs
   */
  public void ping() throws IOException {
    logger.debug("Starting ping");

    HttpGet get = null;
    try {
      get = new HttpGet(
          proxyUrl + "/ping?id=" + URLEncoder.encode(connectionId, "UTF-8"));
      get.setConfig(RequestConfig.custom().setRedirectsEnabled(false)
          .setSocketTimeout(30000).build());
      get.setProtocolVersion(new ProtocolVersion("http", 1, 1));
      get.setHeader("User-Agent", "Randomcoder-Proxy 1.0-SNAPSHOT");

      HttpResponse httpResponse = client.execute(httpHost, get, localContext);
      int status = httpResponse.getStatusLine().getStatusCode();

      if (status == HttpStatus.SC_OK) {
        String response = IOUtils
            .toString(httpResponse.getEntity().getContent(), Charsets.UTF_8)
            .trim();
        if ("ACTIVE".equals(response)) {
          logger.debug("Ping returned successfully");
          return;
        }

        throw new IOException(
            "Unknown response received from remote proxy: " + response);
      }

      if (status == HttpStatus.SC_NOT_FOUND) {
        String response = IOUtils
            .toString(httpResponse.getEntity().getContent(), Charsets.UTF_8);
        throw new IOException(response);
      }

      throw new IOException(
          "Unknown status received from remote proxy: " + status);
    } finally {
      try {
        if (get != null)
          get.releaseConnection();
      } catch (Throwable ignored) {
      }
    }
  }

  private static final class PingThread extends Thread {
    private final long frequency;
    private final ProxyOutputStream parent;

    private boolean shutdown = false;

    /**
     * Creates a new ping thread.
     *
     * @param parent    proxy output stream
     * @param frequency frequency in milliseconds of pings
     */
    public PingThread(ProxyOutputStream parent, long frequency) {
      super("ProxyOutputStream keep-alive");
      this.parent = parent;
      this.frequency = frequency;
    }

    @Override public void run() {
      while (!shutdown) {
        try {
          try {
            Thread.sleep(frequency);
          } catch (InterruptedException ignored) {
          }

          if (shutdown)
            break;

          parent.ping();
        } catch (Throwable t) {
          // failsafe to prevent thread death
          t.printStackTrace();
        }
      }
    }

    /**
     * Requests that this thread shut down.
     */
    public void shutdown() {
      shutdown = true;
      interrupt();
    }
  }
}
