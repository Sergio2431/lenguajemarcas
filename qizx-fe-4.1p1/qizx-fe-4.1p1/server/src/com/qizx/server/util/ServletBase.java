/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.util;

import com.xmlmind.multipartreq.MultipartConfig;
import com.xmlmind.multipartreq.MultipartRequest;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ServletBase extends HttpServlet
{
    private static final int MULTIPART_CORE_LIMIT = 200000; // threshold for tmp file use

    private static final String ANON_PREFIX = "anonymous_";
    
    protected String operationParameter = "op";
    protected HashMap<String, Request> handlerMap = new HashMap<String, Request>();

    protected int multipartMaxSize = -1; //21 * 1024*1024;
    protected String multipartTmpDir = "/tmp";

    private MultipartConfig multipartConfig;
    private boolean trace = false;

    public void setMultipartConfig(MultipartConfig config)
    {
        multipartConfig = config;
    }

    public MultipartConfig getMultipartConfig()
    {
        if(multipartConfig == null) {
            multipartConfig = new MultipartConfig(-1, multipartMaxSize,
                                                  MULTIPART_CORE_LIMIT, null);
        }
        return multipartConfig;
    }

    protected void doHead(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
    {
        Request handler = findHandler(req, resp);
        if (handler != null) {
            handler.setup();
            try {
                handler.handleHead();
            }
            catch(RequestException he) {
                handler.sendError(he);
            }
            finally {
                handler.cleanup();
                handler.multipartCleanup();
            }
        }
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
    {
        Request h = findHandler(req, resp);
        if (h != null) {
            h.setup();
            try {
                h.handleGet();
            }
            catch(RequestException he) {
                h.sendError(he);
            }
            // other exception handled by server
            finally {
                h.cleanup();
                h.multipartCleanup();
            }
        }
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
    {
        Request h = findHandler(req, resp);
        if (h != null) {
            h.setup();
            try {
                h.handlePost();
            }
            catch(RequestException he) {
                h.sendError(he);
            }
            finally {
                h.cleanup();
                h.multipartCleanup();
            }
        }
    }

    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
    {
        Request h = findHandler(req, resp);
        if (h != null) {
            h.setup();
            try {
                h.handlePut();
            }
            catch(RequestException he) {
                h.sendError(he);
            }
            finally {
                h.cleanup();
                h.multipartCleanup();
            }
        }
    }

    // -----------------------------------------------------------------------
    
    protected void addHandler(Request handler)
    {
        handlerMap.put(handler.getName(), handler);
    }
    
    protected void defaultHandler(String method, Request h)
    {
        handlerMap.put(ANON_PREFIX + method, h);
    }
    
    private Request findHandler(HttpServletRequest req, HttpServletResponse resp)
        throws IOException, ServletException
    {
        if(trace) {
            System.err.println("REQUEST "+req.getRequestURL());
            System.err.println("METHOD "+req.getMethod());

            for(Enumeration<?> en = req.getHeaderNames(); en.hasMoreElements(); ) {
                String name = (String) en.nextElement();
                System.err.println("header "+name+"="+req.getHeader(name));
            }            
            for(Enumeration<?> en = req.getParameterNames(); en.hasMoreElements(); ) {
                String name = (String) en.nextElement();
                System.err.println("param "+name+"="+req.getParameter(name));
            }            
        }
        
        String op = req.getParameter(operationParameter);

        // Multipart implem dependency:
        MultipartRequest multipart = null;
        if (MultipartRequest.isMultipartRequest(req)) {
            ServletContext context = getServletContext();
            multipart = new MultipartRequest(req, getMultipartConfig(), context);
         
            try {
                multipart.getParts();
            }
            catch (IllegalStateException e) {
                sendError(resp, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                               e.getMessage());
                return null;
            }
            op = multipart.getParameter(operationParameter);
        }
        
        if(op == null)
            op = ANON_PREFIX + req.getMethod();
        Request h = handlerMap.get(op);
        if(h == null) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                      "unknown request '" + op + "'");
            return null;
        }
        try {
            h = h.getClass().newInstance();
        }
        catch (InstantiationException e) {
            throw new ServletException(e);
        }
        catch (IllegalAccessException e) {
            throw new ServletException(e);
        }
        h.prepare(req, resp, this, multipart);
        return h;
    }
    
    protected void sendError(HttpServletResponse resp, int code, String message)
        throws IOException
    {
        resp.sendError(code, message);
    }
}

