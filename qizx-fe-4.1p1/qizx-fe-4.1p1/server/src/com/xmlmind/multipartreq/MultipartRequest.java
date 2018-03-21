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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * Wraps around an actual <tt>HttpServletRequest</tt> and allows to use
 * {@link #getPart} and {@link #getParts}, as specified by the Servlet 3.0 
 * standard, to access the parts of a <tt>multipart/form-data</tt> request.
 */
public final class MultipartRequest extends HttpServletRequestWrapper {
    /**
     * The version of this tool.
     */
    public static final String VERSION = "1.0.0_01";

    /**
     * The default MultipartConfig.
     */
    public static final MultipartConfig DEFAULT_MULTIPART_CONFIG = 
        new MultipartConfig(20*1024*1024, 10*1024*1024, 65536, null);

    /**
     * The ServletContext passed to the constructor.
     */
    public final ServletContext servletContext;

    /**
     * The MultipartConfig passed to the constructor.
     */
    public final MultipartConfig multipartConfig;

    /**
     * <code>true</code> if this request is a <tt>multipart/form-data</tt> 
     * request; <code>false</code> otherwise.
     */
    public final boolean isMultipartRequest;

    /**
     * Maps a parameter name to its values, an array of strings.
     */
    private Hashtable parameterMap;

    /**
     * The list of parts.
     */
    private PartImpl[] parts;

    // ------------------------------------------------------------------------

    /**
     * Constructs a MultipartRequest wrapping itself around specified request.
     */
    public MultipartRequest(HttpServletRequest request, 
                            MultipartConfig multipartConfig,
                            ServletContext servletContext) {
        super(request);

        if (multipartConfig == null) {
            multipartConfig = DEFAULT_MULTIPART_CONFIG;
        }
        this.multipartConfig = multipartConfig;
        this.servletContext = servletContext;

        isMultipartRequest = isMultipartRequest(request);
    }

    /**
     * Returns <code>true</code> if specified request is a 
     * <tt>multipart/form-data</tt> request. 
     * Returns <code>false</code> otherwise.
     */
    public static boolean isMultipartRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return (contentType != null && 
                contentType.toLowerCase().startsWith("multipart/form-data"));
    }

    /**
     * {@inheritDoc}
     * <p>Works fine whether this request is a <tt>multipart/form-data</tt> 
     * request or not.
     * <p><b>IMPORTANT</b> this method will throw 
     * an {@link IllegalStateException} if any of the limits specified 
     * in {@link MultipartConfig} is exceeded.
     *
     * @see #getParameter
     */
    public Enumeration getParameterNames() {
        if (!isMultipartRequest) {
            return super.getParameterNames();
        }

        if (parameterMap == null) {
            parseParts();
        }
        return parameterMap.keys();
    }

    /**
     * {@inheritDoc}
     * <p>Works normally when this request is <em>not</em> 
     * a <tt>multipart/form-data</tt> request.
     * <p>When this request is actually a <tt>multipart/form-data</tt> request:
     * <ul>
     * <li>If the corresponding part does not represent a file field, 
     * the returned value is the text contained in the field.
     * <li>If the corresponding part represents a file field, 
     * the returned value is the <tt>filename</tt> parameter (always reduced 
     * to a basename) of the <tt>Content-Disposition</tt> header if any and 
     * the empty string otherwise.
     * <p>Note that this case is not clearly specified in the Servlet 3.0 spec.
     * </ul>
     * <p><b>IMPORTANT</b> this method will throw 
     * an {@link IllegalStateException} if any of the limits specified 
     * in {@link MultipartConfig} is exceeded.
     */
    public String getParameter(String name) {
        if (!isMultipartRequest) {
            return super.getParameter(name);
        }

        if (parameterMap == null) {
            parseParts();
        }
        String[] values = (String[]) parameterMap.get(name);
        return (values == null)? null : values[0];
    }

    /**
     * {@inheritDoc}
     * <p>Works fine whether this request is a <tt>multipart/form-data</tt> 
     * request or not.
     * <p><b>IMPORTANT</b> this method will throw 
     * an {@link IllegalStateException} if any of the limits specified 
     * in {@link MultipartConfig} is exceeded.
     *
     * @see #getParameter
     */
    public String[] getParameterValues(String name) {
        if (!isMultipartRequest) {
            return super.getParameterValues(name);
        }

        if (parameterMap == null) {
            parseParts();
        }
        return (String[]) parameterMap.get(name);
    }

    /**
     * {@inheritDoc}
     * <p>Works fine whether this request is a <tt>multipart/form-data</tt> 
     * request or not.
     * <p><b>IMPORTANT</b> this method will throw 
     * an {@link IllegalStateException} if any of the limits specified 
     * in {@link MultipartConfig} is exceeded.
     *
     * @see #getParameter
     */
    public Map getParameterMap() {
        if (!isMultipartRequest) {
            return super.getParameterMap();
        }

        if (parameterMap == null) {
            parseParts();
        }
        return parameterMap;
    }

    private void parseParts() {
        try {
            doParseParts();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            StringBuffer msg = new StringBuffer("cannot parse parts: ");
            msg.append(e.getClass().getName());
            if (e.getMessage() != null) {
                msg.append(": ");
                msg.append(e.getMessage());
            }

            if (servletContext != null) {
                servletContext.log(msg.toString(), e);
            } else {
                System.err.println(msg.toString());
            }

            parameterMap = new Hashtable();
            parts = new PartImpl[0];
        }
    }

    /**
     * Returns the Part with the given name. 
     *
     * @param name the name of the requested Part 
     * @return the Part with the given name, or <code>null</code> 
     * if this request is of type <tt>multipart/form-data</tt>, 
     * but does not contain the requested  Part 
     * @exception IOException if an I/O error occurred during the retrieval 
     * of the Part components of this request 
     * @exception ServletException if this request is not of type 
     * <tt>multipart/form-data</tt> 
     * @exception IllegalStateException if the request body is larger 
     * than <tt>maxRequestSize</tt>, or any Part in the request is larger than 
     * <tt>maxFileSize</tt>
     * @see MultipartConfig
     */
    public Part getPart(String name)
        throws IOException, ServletException {
        if (parts == null) {
            doParseParts();
        }

        int partCount = parts.length;
        for (int i = 0; i < partCount; ++i) {
            if (parts[i].getName().equals(name)) {
                return parts[i];
            }
        }
        return null;
    }

    /**
     * Returns all the Part components of this request, provided that it is of
     * type <tt>multipart/form-data</tt>.
     * <p>
     * If this request is of type <tt>multipart/form-data</tt>, but does not
     * contain any Part components, the returned array will be empty.
     * @return a possibly empty array of the Part components of this request
     * @exception IOException if an I/O error occurred during the retrieval of
     *            the Part components of this request
     * @exception ServletException if this request is not of type
     *            <tt>multipart/form-data</tt>
     * @exception IllegalStateException if the request body is larger than
     *            <tt>maxRequestSize</tt>, or any Part in the request is larger
     *            than <tt>maxFileSize</tt>
     * @see MultipartConfig
     */
    public Part[] getParts()
        throws IOException, ServletException
    {
        if (parts == null) {
            doParseParts();
        }

        return parts;
    }

    private void doParseParts()
        throws IOException, ServletException
    {
        if (!isMultipartRequest) {
            throw new ServletException("Not a \"multipart/form-data\" request");
        }

        Hashtable paramMap = new Hashtable();
        ArrayList partList = new ArrayList();
        MultipartParser parser =
            new MultipartParser((HttpServletRequest) getRequest(),
                                multipartConfig, paramMap, partList);
        parser.parse();

        parameterMap = paramMap;
        parts = new PartImpl[partList.size()];
        partList.toArray(parts);
    }
}
