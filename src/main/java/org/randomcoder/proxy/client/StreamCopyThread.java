package org.randomcoder.proxy.client;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Thread which copies data from an <code>InputStream</code> to an
 * <code>OutputStream</code>.
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
public class StreamCopyThread extends Thread {
  private static final Logger logger = Logger.getLogger(StreamCopyThread.class);

  private final InputStream input;
  private final OutputStream output;

  private long bytesCopied = 0;
  private IOException exception;
  private boolean success = false;

  /**
   * Creates a new stream copy thread.
   *
   * @param input  input stream to read
   * @param output output stream to write
   */
  public StreamCopyThread(InputStream input, OutputStream output) {
    logger.debug("Copy thread:");
    logger.debug("  FROM: " + input.getClass().getName());
    logger.debug("  TO: " + output.getClass().getName());

    this.input = input;
    this.output = output;
  }

  @Override public void run() {
    byte[] buf = new byte[32768];

    try {
      int c;
      do {
        c = input.read(buf, 0, 32768);
        if (c > 0) {
          output.write(buf, 0, c);
          output.flush();
          bytesCopied += c;
        }
      } while (c >= 0);
      success = true;
    } catch (IOException e) {
      exception = e;
      success = false;
    }
  }

  public boolean isSuccess() {
    return success;
  }

  public IOException getException() {
    return exception;
  }

  public long getBytesCopied() {
    return bytesCopied;
  }
}
