/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.xqsp;

import com.qizx.api.*;
import com.qizx.api.util.XMLSerializer;
import com.qizx.server.util.QizxDriver;
import com.qizx.server.util.QizxRequestBase;
import com.qizx.server.util.RequestException;
import com.qizx.server.util.ServletBase;
import com.qizx.util.NamespaceContext;
import com.qizx.util.basic.FileUtil;
import com.qizx.util.basic.PathUtil;
import com.qizx.xquery.EvalContext;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;


/**
 *  XML Query Servlet: implements "XQuery Server Pages", a mechanism that
 *  uses XML Query as a template language for dynamic Web Applications.
 *  <p>Technically, this servlet executes a query and serializes the resulting
 *  tree to the servlet output stream, typically in HTML or XHTML.
 *  <p>Optionally, it can "pipe" the evaluated XML tree into a XSLT stylesheet,
 *  which in turn writes to the HTTP stream, thus providing the "View" 
 *  functionality of a MVC (Model, View, Controller) processing scheme.
 *  <p>The servlet manages a cache of XQuery pages, and reloads pages if
 *  needed.
 */
public class XQSPServlet extends ServletBase
{
    public static final String PARAMETER_NS = "com.qizx.server.xqs.parameter";
    public static final String XQSP_API_NS = "java:com.qizx.server.xqsp.XQSPServlet";

    private static final String XQSP_REQUEST_PROP = "xqsp-request";
    
    /** DIFFERENCES with xqs:
     *  - no list of services
     *  - richer API
     *  - option for XSLT output (later)
     */
    
    
    public void init()
        throws ServletException
    {
        super.init();
        
        defaultHandler("GET", new Request());
        defaultHandler("POST", new Request());
        
        // read configuration
        ServletConfig conf = getServletConfig();
    }

    public void destroy()
    {
        log("destroying servlet " + getClass());
        QizxDriver.terminate(getServletContext());
        log("servlet " + getClass() + " destroyed");
        super.destroy();
    }

    public static class Request extends QizxRequestBase
    {

        public Request()
        {
        }
        
        public String getName()
        {
            return "xqsp"; // whatever
        }

        public void handleGet()
            throws ServletException, IOException
        {
            handlePost();
        }

        public void handlePost()
            throws ServletException, IOException
        {
            // relative path of the stored XQ script:
            String queryPath =
                PathUtil.normalizePath(request.getPathInfo(), true);

            try {
                // Finds a compiled expression representing the stored query
                // Based on session pooling and caching of expr. for each session
                Expression expr = getScript(queryPath);
                if (expr == null) {
                    throw new RequestException(BAD_REQUEST,
                                               "unknown request " + queryPath);
                }
                expr.setProperty(XQSP_REQUEST_PROP, this);
                
                // look for global variables with NS matching 'req',
                // and check if there is a matching request parameter
                XQueryContext xctx = expr.getContext();
                for (QName varName : xctx.getVariableNames()) {
                    if (PARAMETER_NS.equals(varName.getNamespaceURI())) {
                        SequenceType type = xctx.getVariableType(varName);
                        //println(" param "+varName.getLocalPart()+" type=" + type);
                        String paramValue =
                            getParameter(varName.getLocalPart());
                        if (paramValue != null)
                            expr.bindVariable(varName, paramValue,
                                              type.getItemType());
                    }
                }

                // look for options in script: 
                String mimeType = null, format = "XML";
                XMLSerializer resout = new XMLSerializer(output, "UTF-8");
                for(QName name : xctx.getOptionNames()) {
                    //println("option "+name+" "+xctx.getOptionValue(name));
                    if(name.getNamespaceURI() == NamespaceContext.OUTPUT_NS) {
                        String value = xctx.getOptionValue(name);
                        String sname = name.getLocalPart();
                        if("content-type".equalsIgnoreCase(sname))
                            mimeType = value;
                        else {
                            if("method".equalsIgnoreCase(sname))
                                format = value;
                            resout.setOption(sname, value);
                        }
                    }
                }

                // mime-type:
                if (mimeType == null) {
                    if (format == null)
                        mimeType = "text/xml";
                    else if ("text".equalsIgnoreCase(format))
                        mimeType = "text/plain";
                    else
                        mimeType = "text/" + format.toLowerCase();
                }
                response.setContentType(mimeType);

                ItemSequence seq = expr.evaluate();
                for (; seq.moveToNextItem();) {
                    Item it = seq.getCurrentItem();
                    if (it.isNode())
                        resout.putNodeCopy(it.getNode(), 0);
                    else
                        println(it.getString());
                }

                resout.flush();
            }
            catch (RequestException e) {
                throw (e);
            }
            catch (Exception e) {
                throw new RequestException(e);
            }
        }

        /**
         * Cached access to a script by its path.
         * @param storedQuery relative path of the query
         */
        private Expression getScript(String storedQuery)
            throws Exception
        {
            QizxDriver driver = requireQizxDriver();
            Library lib = getStoredScriptSession(driver);
            File baseURI = new File(driver.getServicesRoot(), storedQuery);

            // beware: funky. setBaseURI needs a real good URI, but it's not checked
            lib.getContext().setBaseURI(FileUtil.fileToSystemId(baseURI));

            return driver.getStoredQuery(storedQuery, lib);
        }

        private Library getStoredScriptSession(QizxDriver driver)
            throws RequestException, DataModelException
        {
            Library lib = acquireLibSession(driver.getServicesDefaultLibrary());
            lib.getContext().declarePrefix("param", PARAMETER_NS);
            lib.getContext().declarePrefix("xqsp", XQSP_API_NS);
            return lib;
        }
    }

    // ==================== Web API ===========================================
   
    private static Request getRequest(EvalContext ctx)
    {
        return (Request) ctx.getProperty(XQSP_REQUEST_PROP);
    }
    
    public static Enumeration headerNames(EvalContext ctx)
    {
        Request req = getRequest(ctx);
        return req.getRequest().getHeaderNames();
    }
    
    public static String header(EvalContext ctx, String name)
    {
        Request req = getRequest(ctx);
        return req.getRequest().getHeader(name);
    }

    public static void setHeader(EvalContext ctx, String name, String value) // TODO
    {
        Request req = getRequest(ctx);
        req.getResponse().setHeader(name, value);
    }

    /// Request parameters:
    public static Enumeration parameterNames(EvalContext ctx)
    {
        Request req = getRequest(ctx);
        return req.getParameterNames();
    }
    
    /**
     * Returns the string value of a simple parameter. File parts of a 
     * multipart post request return a void value.
     * @param name
     * @return
     */
    public static String parameter(EvalContext ctx, String name)
    {
        return "parameter"; // TODO
    }

    public static void setParameter(EvalContext ctx, String name, String value) // TODO
    {
    }

    /// Cookies:

    /// Session attributes:

    public static void sessionGet(String name)
    {
    }
    
    public static void sessionSet(String name, String value)
    {
    }
    
    public static void sessionClose()
    {
    }
    
    /// Control
    public static void error(int code, String message)
    {
    }
    
    public static void forward(String pageURI)
    {
    }
    
    public static String userName()
    {
        return "JoeUser"; // TODO
    }

}
