/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
/*
 * Copyright (c) 2009-2010 Pixware SARL. 
 *
 * Author: Hussein Shafie
 *
 * This file is part of the XMLmind MultipartRequest project.
 * For conditions of distribution and use, see the accompanying legal.txt file.
 */
package com.xmlmind.multipartreq;

import java.io.File;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * Parses a <tt>multipart/form-data</tt> requests.
 */
/*package*/ final class MultipartParser {
    private final HttpServletRequest req;
    private final MultipartConfig conf;
    private final Map params;
    private final List parts;

    private final File uploadDir;        
    private final ArrayList headers;
    private final String defaultCharset;
    private final String delimiter;
    private final byte[] endOfPart;
    private final byte[] byteBuffer;
    private final PushbackInputStream in;

    // The spec says 70 chars. We use 256 bytes.
    private static final int LINE_MAX_BYTES = 256;

    // -----------------------------------------------------------------------

    public MultipartParser(HttpServletRequest req, MultipartConfig conf,
                           Map params, List parts) 
        throws IOException {
        String contentType = req.getContentType();
        if (contentType == null ||
            !contentType.toLowerCase().startsWith("multipart/form-data")) {
            // Should not happen: we have already tested that in
            // MultipartRequest.
            throw new IllegalArgumentException(
                "Not a \"multipart/form-data\" request");
        }

        this.req = req;
        this.conf = conf;
        this.params = params;
        this.parts = parts;

        String uploadDirName = 
            ((conf.location == null || conf.location.length() == 0)? 
             "." : conf.location);
        uploadDir = (new File(uploadDirName)).getCanonicalFile();

        headers = new ArrayList();

        String boundary = 
            HeaderUtil.getParameter(contentType, "boundary", null);
        if (boundary == null) {
            throw new IOException("Content-Type \"" + contentType + 
                                  "\" should have a \"boundary\" parameter");
        }
        delimiter = "--" + boundary;
        
        String enc = req.getCharacterEncoding();
        defaultCharset = (enc == null)? "ISO-8859-1" : enc;

        endOfPart = ("\r\n" + delimiter).getBytes("US-ASCII");

        // byteBuffer must be larger than endOfPart.length. 
        // With 65536 bytes, this should always be the case.
        byteBuffer = new byte[Math.max(65536, endOfPart.length)];

        in = new PushbackInputStream(req.getInputStream(), byteBuffer.length);
    }

    public void parse() 
        throws IllegalStateException, IOException {
        if (conf.maxRequestSize > 0) {
            int contentLength = req.getContentLength();
            if (contentLength > conf.maxRequestSize) {
                throw new IllegalStateException(
                    "request size " + contentLength + " exceeds limit of " + 
                    conf.maxRequestSize + " bytes");
            }
            // Note that contentLength may be negative or null.
        }

        // Skip preamble if any ---

        for (;;) {
            String line = readLine();
            if (line == null) {
                throw new IOException("Unexpected end of stream");
            } 

            if (line.equals(delimiter)) {
                // Reached first part.
                break;
            }
        }

        // Read parts ---

        try {
            readParts();
        } catch (IllegalStateException e) {
            abortParts();

            throw e;
        } catch (IOException e) {
            abortParts();

            throw e;
        }
    }

    private String readLine() 
        throws IOException {
        int prevB = -1;

        for (int i = 0; i < LINE_MAX_BYTES; ++i) {
            int b = in.read();
            if (b < 0) {
                // Reached end of stream.
                return null;
            }

            if (b == '\n' && prevB == '\r') {
                if (i <= 1) {
                    return "";
                } else {
                    String line = new String(byteBuffer, 0, i-1, "US-ASCII");
                    return HeaderUtil.decodeWords(line);
                }
            }

            byteBuffer[i] = (byte) b;
            prevB = b;
        }

        // No end of line within reach 
        return null;
    }

    private void readParts() 
        throws IllegalStateException, IOException {
        for (;;) {
            readPart();

            String line = readLine();
            if (line == null) {
                throw new IOException("Unexpected end of stream");
            } 

            if ("--".equals(line)) {
                // Found close-delimiter (=delimiter+"--"): done.
                // This skips epilogue if any.
                break;
            } 

            if (line.length() > 0) {
                throw new IOException("\"" + (delimiter+line) + 
                                      "\", malformed delimiter");
            }
            // Otherwise, normal delimiter: proceed.
        }
    }

    private void abortParts() {
        int partCount = parts.size();
        for (int i = 0; i < partCount; ++i) {
            PartImpl part = (PartImpl) parts.get(i);

            part.abort();
        }
    }

    private final void readPart()
        throws IllegalStateException, IOException {
        // Collect headers ---

        headers.clear();
        String header = null;

        for (;;) {
            String line = readLine();
            if (line == null) {
                throw new IOException("Unexpected end of stream");
            } 

            if (line.length() == 0) {
                // End of headers.
                break;
            }

            if (Character.isWhitespace(line.charAt(0))) {
                if (header == null) {
                    throw new IOException("\"" + line + "\" malformed header");
                }

                // This line is a continuation. See "folding" in RFC822.
                header = header + line;
            } else {
                if (header != null) {
                    headers.add(header);
                }
                header = line;
            }
        }

        if (header != null) {
            headers.add(header);
        }

        // Create part ---

        PartImpl part = new PartImpl(headers, defaultCharset, conf.maxFileSize, 
                                     conf.fileSizeThreshold, uploadDir);
        parts.add(part);

        // Collect bytes ---

        int byteBufferSize = byteBuffer.length;
        int endOfPartLength = endOfPart.length;

        for (;;) {
            int byteCount = in.read(byteBuffer, 0, byteBufferSize);
            if (byteCount < 0) {
                throw new IOException("Unexpected end of stream");
            }

            if (byteCount > 0) {
                int endOfPartIndex = findEndOfPart(endOfPart, 
                                                   byteBuffer, byteCount);
                if (endOfPartIndex < 0) {
                    part.append(byteBuffer, byteCount);
                } else {
                    if (endOfPartIndex + endOfPartLength <= byteCount) {
                        part.append(byteBuffer, endOfPartIndex);

                        int unreadFirst = endOfPartIndex + endOfPartLength;
                        int unreadCount = byteCount - unreadFirst;
                        in.unread(byteBuffer, unreadFirst, unreadCount);

                        // Done with this part.
                        break;
                    } 

                    // Found a partial end of part. (May be not an end of part
                    // at all!) Refill buffer and retry from this partial end
                    // of part.

                    part.append(byteBuffer, endOfPartIndex);

                    int unreadFirst = endOfPartIndex;
                    int unreadCount = byteCount - unreadFirst;
                    in.unread(byteBuffer, unreadFirst, unreadCount);

                    // Proceed.
                }
            }
        }

        // Add part to the results ---

        part.finish();
        addPart(params, part);
    }

    private static int findEndOfPart(final byte[] endOfPart, 
                                     final byte[] bytes, final int byteCount) {
        final int endOfPartSize = endOfPart.length;
        int endOfPartIndex = 0;
        int i = 0;

        for (; i < byteCount; ++i) {
            if (bytes[i] == endOfPart[endOfPartIndex]) {
                ++endOfPartIndex;
                if (endOfPartIndex == endOfPartSize) {
                    // Found end of part.
                    ++i;
                    break;
                }
            } else {
                endOfPartIndex = (bytes[i] == endOfPart[0])? 1 : 0;
            }
        }

        return (endOfPartIndex == 0)? -1 : (i - endOfPartIndex);
    }

    private static void addPart(Map params, PartImpl part) 
        throws IOException {
        String name = part.getName();
        String value = part.asParameterValue();

        String[] values = (String[]) params.get(name);
        if (values == null) {
            values = new String[] { value };
        } else {
            int count = values.length;
            String[] values2 = new String[count+1];
            System.arraycopy(values, 0, values2, 0, count);
            values2[count] = value;
            values = values2;
        }

        params.put(name, values);
    }
}
