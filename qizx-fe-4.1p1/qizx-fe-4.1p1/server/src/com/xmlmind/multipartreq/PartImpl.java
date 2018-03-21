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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.File;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

/*package*/ final class PartImpl implements Part {
    public final String defaultCharset;
    public final long maxFileSize;
    public final int fileSizeThreshold;
    public final File uploadDir;

    private String name;
    private String contentType;
    private String[] headerNames;
    private String[][] headerValues;
    /**
     * filename==null means: not a file (whether stored on disk or not).
     * filename=="" generally means: it's an empty file field. It may also
     * mean: unknown filename. 
     * Check size to make a difference between the two cases.
     */
    private String filename;

    private long size;
    private OutputStream stream;
    private byte[] bytes;
    private File file;

    private static final byte[] NO_BYTES = new byte[0];
    private static final String[] NO_STRINGS = new String[0];
    private static final String[][] NO_STRING_LISTS = new String[0][];

    // -----------------------------------------------------------------------

    public PartImpl(List headers, String defaultCharset,
                    long maxFileSize, int fileSizeThreshold, File uploadDir) 
        throws IOException {
        this.defaultCharset = defaultCharset;
        this.maxFileSize = maxFileSize;
        this.fileSizeThreshold = fileSizeThreshold;
        this.uploadDir = uploadDir;

        name = null;
        // Default Content-Type.
        contentType = "text/plain; charset=" + defaultCharset;
        headerNames = NO_STRINGS;
        headerValues = NO_STRING_LISTS;
        filename = null;

        parseHeaders(headers);

        size = 0;
        bytes = NO_BYTES;
        file = null;
        stream = null;
    }

    private void parseHeaders(List headers) 
        throws IOException {
        String disposition = null;
        String transferEncoding = null;

        // Compile headers ---

        HashMap map = new HashMap();

        int headerCount = headers.size();
        for (int i = 0; i < headerCount; ++i) {
            String header = (String) headers.get(i);

            String[] split = HeaderUtil.splitHeader(header);
            if (split == null) {
                throw new IOException("\"" + header + "\", malformed header");
            }

            String headerName = split[0]; // Always lower-case.
            String headerValue = split[1];

            if ("content-disposition".equals(headerName)) {
                disposition = headerValue;
            } else if ("content-type".equals(headerName)) {
                contentType = headerValue;
            } else if ("content-transfer-encoding".equals(headerName)) {
                transferEncoding = headerValue;
            }

            String[] headerValues = (String[]) map.get(headerName);
            if (headerValues == null) {
                headerValues = new String[] { headerValue };
            } else {
                int count = headerValues.length;
                String[] headerValues2 = new String[count+1];
                System.arraycopy(headerValues, 0, headerValues2, 0, count);
                headerValues2[count] = headerValue;
                headerValues = headerValues2;
            }

            map.put(headerName, headerValues);
        }

        headerCount = map.size();
        headerNames = new String[headerCount];
        headerValues = new String[headerCount][];
        headerCount = 0;

        Iterator iter = map.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry e = (Map.Entry) iter.next();

            headerNames[headerCount] = (String) e.getKey();
            headerValues[headerCount] = (String[]) e.getValue();
            ++headerCount;
        }

        // Parse Content-Disposition ---

        if (disposition == null) {
            throw new IOException("missing \"content-disposition\" header");
        }

        String[] parsed = HeaderUtil.splitHeaderValue(disposition);
        if (parsed == null) {
            throw new IOException("\"" + disposition + 
                                  "\", malformed header value");
        }

        if (!parsed[0].toLowerCase().equals("form-data")) {
            throw new IOException("expected \"form-data\", found \"" +
                                  parsed[0] + "\"");
        }

        for (int i = 1; i < parsed.length; i += 2) {
            String param = parsed[i]; // Always lower-case.

            if ("name".equals(param)) {
                name = parsed[i+1].trim();
                if (name.length() == 0) {
                    name = null;
                }
            } else if ("filename".equals(param)) {
                filename = parsed[i+1].trim();
                if (filename.length() == 0) {
                    filename = null;
                }
            }
        }

        if (name == null) {
            throw new IOException("\"" + disposition + 
                                  "\", is missing a \"name\" parameter");
        }

        if (filename != null) {
            int pos1 = filename.lastIndexOf('\\');
            int pos2 = filename.lastIndexOf('/');
            int pos = Math.max(pos1, pos2);
            if (pos >= 0) {
                filename = filename.substring(pos+1);
                if (filename.length() == 0) {
                    filename = null;
                }
            }
        }

        if (filename == null &&
            !contentType.toLowerCase().startsWith("text/plain")) {
            // This is used to make a difference between a file field and
            // other fields.
            filename = "";
        }

        // Parse Content-Transfer-Encoding ---

        if (transferEncoding != null) {
            transferEncoding = transferEncoding.toLowerCase();
            if (!"binary".equals(transferEncoding) &&
                !"8bit".equals(transferEncoding) &&
                !"7bit".equals(transferEncoding)) {
                throw new IOException(
                    "\"" + transferEncoding + 
                    "\", unsupported value for \"Content-Transfer-Encoding\"");
            }
        }
    }

    public String asParameterValue() 
        throws UnsupportedEncodingException {
        if (filename != null) {
            return filename; // May be the empty string.
        } else {
            String encoding = 
                HeaderUtil.getParameter(contentType, "charset", defaultCharset);
            return new String(bytes, encoding);
        }
    }

    public void append(byte[] inBytes, int inByteCount) 
        throws IllegalStateException, IOException {
        if (inByteCount <= 0) {
            return;
        }

        if (maxFileSize > 0 && size + inByteCount > maxFileSize) {
            throw new IllegalStateException("the size of part \"" + name + 
                                            "\" exceeds limit " + maxFileSize);
        }

        if (filename != null && 
            size + inByteCount > fileSizeThreshold) {
            if (file == null) {
                String baseName = filename;
                if (filename.length() == 0) {
                    // This may happen with a semi-dumb browser.
                    baseName = name;
                } 
                baseName = toSimpleBaseName(baseName);

                file = File.createTempFile(baseName, ".tmp", uploadDir);

                byte[] appended = null;
                if (stream != null) {
                    stream.close();
                    appended = ((ByteArrayOutputStream) stream).toByteArray();
                    stream = null;
                }

                stream = new FileOutputStream(file);

                if (appended != null) {
                    stream.write(appended);
                }
            }
        } else {
            if (stream == null) {
                stream = new ByteArrayOutputStream();
            }
        }

        stream.write(inBytes, 0, inByteCount);
        size += inByteCount;
    }

    private static String toSimpleBaseName(String baseName) {
        StringBuffer buffer = new StringBuffer();

        int length = baseName.length();
        for (int i = 0; i < length; ++i) {
            char c = baseName.charAt(i);

            if (!Character.isLetterOrDigit(c) && c != '-') {
                // Also replace '.' by '_'.
                c = '_';
            }
            buffer.append(c);
        }

        return buffer.toString();
    }

    public void finish() 
        throws IOException {
        if (stream != null) {
            stream.flush();
            stream.close();

            if (file == null) {
                bytes = ((ByteArrayOutputStream) stream).toByteArray();
            } else {
                bytes = null;
            }

            stream = null; // No longer needed.
        }
    }

    public void abort() {
        if (stream != null) {
            try { stream.close(); } catch (IOException ignored) {}
            stream = null;
        }

        if (file != null) {
            file.delete();
            file = null;
        }

        size = 0;
        bytes = NO_BYTES;
    }

    // -----------------------------------------------------------------------
    // Part
    // -----------------------------------------------------------------------

    public String getName() {
        return name;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

    public String[] getHeaderNames() {
        return headerNames;
    }

    public String getHeader(String name) {
        String[] values = getHeaders(name);
        return (values != null && values.length > 0)? values[0] : null;
    }

    public String[] getHeaders(String name) {
        name = name.toLowerCase();

        int headerCount = headerNames.length;
        for (int i = 0; i < headerCount; ++i) {
            if (headerNames[i].equals(name)) {
                return headerValues[i];
            }
        }
        return NO_STRINGS;
    }

    public InputStream getInputStream()
        throws IOException {
        if (bytes != null) {
            return new ByteArrayInputStream(bytes);
        } else {
            if (file.isFile()) {
                return new FileInputStream(file);
            } else {
                throw new IOException("write() has been used: part \"" + name + 
                                      "\" is no longer stored in file \"" + 
                                      file + "\"");
            }
        }
    }

    public void write(String dstFileName)
        throws IOException {
        File dstFile = new File(dstFileName);
        if (!dstFile.isAbsolute()) {
            dstFile = new File(uploadDir, dstFileName);
        }
        dstFile = dstFile.getAbsoluteFile();

        File parentFile = dstFile.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            if (!parentFile.mkdirs()) {
                throw new IOException("cannot create directory \"" + 
                                      parentFile + "\"");
            }
        }

        boolean renamed = false;

        if (file != null) {
            if (!file.isFile()) {
                throw new IOException("write() has been used: part \"" + name + 
                                      "\" is no longer stored in file \"" + 
                                      file + "\"");
            }

            // renameTo() returns false when dstFile in on a NFS partition
            // different from file's.
            renamed = file.renameTo(dstFile);
        }

        if (!renamed) {
            doWrite(dstFile);
        }
    }

    private void doWrite(File dstFile)
        throws IOException {
        InputStream in = getInputStream();
        try {
            copyFile(in, dstFile);
        } finally {
            in.close();
        }
    }

    private static final void copyFile(InputStream src, File dstFile)
        throws IOException {
        FileOutputStream dst = new FileOutputStream(dstFile);
        try {
            copyFile(src, dst);
        } finally {
            dst.close();
        }
    }

    private static final void copyFile(InputStream in, OutputStream out)
        throws IOException {
        byte[] buffer = new byte[65535];
        int count;

        while ((count = in.read(buffer)) != -1) {
            out.write(buffer, 0, count);
        }

        out.flush();
    }

    public void delete()
        throws IOException
    {
        if (file != null && file.isFile()) {
            if (!file.delete()) {
                throw new IOException("cannot delete part \"" + name + 
                                      "\" stored in file \"" + file + "\"");
            }
        }
    }
}
