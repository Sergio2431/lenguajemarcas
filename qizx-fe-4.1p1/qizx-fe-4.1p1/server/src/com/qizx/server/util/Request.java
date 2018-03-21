/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.util;

import com.qizx.util.basic.FileUtil;

import com.xmlmind.multipartreq.MultipartRequest;
import com.xmlmind.multipartreq.Part;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Abstract request handler for a servlet service.
 * <p>
 * A handler is instantiated for each HTTP request and wraps both request and
 * response (Command Design Pattern, for the pedantic).
 */
public abstract class Request
{
    public static final String BAD_REQUEST = "BadRequest";
    public static final String SERVER = "Server";
    public static final String NOT_FOUND = "NotFound";

    public static final String MIME_DATA = "application/data";
    public static final String MIME_PLAIN_TEXT = "text/plain";
    public static final String MIME_XML = "text/xml";


    protected String opName;
    protected ServletBase servlet;

    protected HttpServletRequest request;
    protected MultipartRequest multipart;
    protected HttpServletResponse response;
    protected ServletOutputStream output;

    public abstract String getName();
    
    public void handleHead()
        throws ServletException, IOException
    {
        throw new RequestException(BAD_REQUEST, "HEAD not supported for " + getName());
    }

    public void handleGet()
        throws ServletException, IOException
    {
        throw new RequestException(BAD_REQUEST, "GET not supported for " + getName());
    }
    
    public void handlePost()
        throws ServletException, IOException
    {
        throw new RequestException(BAD_REQUEST, "POST not supported for " + getName());
    }
    
    public void handlePut()
        throws ServletException, IOException
    {
        throw new RequestException(BAD_REQUEST, "PUT not supported for " + getName());
    }
   
    // ------------- utilities for implementations ----------------------------
    
    public void prepare(HttpServletRequest request, HttpServletResponse response,
                        ServletBase servlet, MultipartRequest multipart)
        throws IOException
    {
        this.servlet = servlet;
        this.request = request;
        this.response = response;
        this.multipart = multipart;
        this.output = response.getOutputStream();
    }
    
    public abstract void setup();

    public abstract void cleanup();

    protected void multipartCleanup()
        throws IOException, ServletException
    {
        if (multipart == null)
            return;
 
        Part[] parts;
        try {
            parts = multipart.getParts();
            for (int i = 0; i < parts.length; ++i) {
                try {
                    parts[i].delete();
                }
                catch (Exception e) {
                    String reason = e.getClass().getName();
                    if (e.getMessage() != null) {
                        reason += ": " + e.getMessage();
                    }
                    servlet.getServletContext().log("cannot delete a part of a"
                                                    + " \"multipart/form-data\" request: "
                                                    + reason, e);
                }
            }
        }
        catch (IllegalStateException e) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                               e.getMessage());
            return;
        }
    }

    protected void log(String msg)
    {
        servlet.getServletContext().log(msg);
    }

    /**
     * Sends en error response.
     * Generic handling: redefined in Qizx-specific handlers
     */
    protected void sendError(RequestException he) throws IOException
    {
        if(!response.isCommitted())
            response.reset();
        response.setContentType("text/error");
        println(he.code + ": " + he.getMessage());
    }

    protected void requiredParam(String name)
        throws RequestException
    {
        throw new RequestException(BAD_REQUEST, "required parameter '" + name +"'");
    }

    protected void print(String string) throws IOException
    {
        output.print(string);
    }

    protected void println(String string) throws IOException
    {
        output.println(string);
    }

    public HttpServletResponse getResponse()
    {
        return response;
    }

    public HttpServletRequest getRequest()
    {
        return request;
    }

    public String getUserName()
    {
        Principal user = request.getUserPrincipal();
        String userName = (user == null)? null : user.getName();
        return userName;
    }

    public Enumeration getParameterNames()
    {
        if(multipart != null) {
            return multipart.getParameterNames(); // already merged
        }
        return request.getParameterNames();
    }

    /**
     * Gets a simple parameter either from URL or from multipart data.
     * Does not work with file parts.
     * @param name
     */
    public String getParameter(String name)
    {
        return getParameter(name, null);
    }
   
    /**
     * Gets a simple parameter either from URL or from multipart data.
     * Does not work with file parts.
     */
    public String getParameter(String name, String defaultValue)
    {
        String parameter = request.getParameter(name);
        if(parameter == null && multipart != null)
            parameter = multipart.getParameter(name);
        return (parameter == null || parameter.length() == 0)?
                    defaultValue : parameter;
    }
   
    public boolean getBooleanParameter(String name, boolean defaultValue)
    {
        String v = getParameter(name);
        if(v == null)
            return defaultValue;
        v = v.trim();
        return "true".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v);
    }

    public int getIntParameter(String name, int defaultValue)
        throws RequestException
    {
        String v = getParameter(name);
        if(v == null)
            return defaultValue;
        v = v.trim();
        if(v.length() == 0)
            return defaultValue;
        try {
            return Integer.parseInt(v);
        }
        catch (NumberFormatException e) {
            throw new RequestException(BAD_REQUEST, 
                        "invalid integer value of parameter " + name +": " + v);
        }
    }

    public boolean isMultipart()
    {
        return multipart != null;
    }
    
    protected Part getPart(String name)
        throws IOException, ServletException
    {
        return multipart == null? null : multipart.getPart(name);
    }

    protected InputStream getPartAsStream(String name)
        throws IOException, ServletException
    {
        Part p = getPart(name);
        if (p == null)
            return null;
        return p.getInputStream();
    }

    protected String getPartAsString(String name)
        throws IOException, ServletException
    {
        Part p = getPart(name);
        if (p == null)
            return null;
        InputStream stream = p.getInputStream();
        String result = FileUtil.loadString(stream, p.getHeader("encoding"));
        stream.close();
        return result;
    }

    protected Object getContextObject(String key)
    {
        return servlet.getServletContext().getAttribute(key);
    }
}

//        Enumeration params = getParameterNames();
//        for (; params.hasMoreElements(); ) {
//            String name = (String) params.nextElement();
//            println("param " + name + " = " + getParameter(name));

