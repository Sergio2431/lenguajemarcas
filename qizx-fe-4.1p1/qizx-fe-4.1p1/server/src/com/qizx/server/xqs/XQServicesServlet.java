/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.xqs;

import com.qizx.api.*;
import com.qizx.api.util.XMLSerializer;
import com.qizx.server.util.QizxDriver;
import com.qizx.server.util.QizxRequestBase;
import com.qizx.server.util.RequestException;
import com.qizx.server.util.ServletBase;
import com.qizx.util.NamespaceContext;
import com.qizx.util.basic.FileUtil;
import com.qizx.util.basic.PathUtil;
import com.qizx.xdm.DocumentParser;
import com.qizx.xdm.IQName;

import com.xmlmind.multipartreq.Part;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;


/**
 *  Servlet implementing "XQuery Services".
 */
public class XQServicesServlet extends ServletBase
{
    public static final String PARAMETER_NS = "com.qizx.server.xqs.parameter";
    
    private static final IQName QN_SERVICE = IQName.get("service");
    private static final IQName QN_SERVICES = IQName.get("services");
    private static final IQName QN_NAME = IQName.get("name");
    private static final IQName QN_PACKAGE = IQName.get("package");
    private static final IQName QN_OUTPUT_OPTION = IQName.get("output-option");
    private static final IQName QN_PARAM = IQName.get("parameter");
    private static final IQName QN_TYPE = IQName.get("type");
    private static final IQName QN_RESULT_TYPE = IQName.get("result-type");
    private static final IQName QN_DOCUMENTATION = IQName.get("documentation");
    
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
            return "xqs"; // whatever
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
                if(expr == null)
                {
                    Map<String, Expression> services = listQueries(queryPath);
                    if(services == null)
                        throw new RequestException(BAD_REQUEST,
                                                   "unknown request " + queryPath);
                    printServiceList(services, queryPath);
                    return;
                }
                
                // look for global variables with NS matching 'req',
                // and check if there is a matching request parameter
                XQueryContext xctx = expr.getContext();
                for(QName varName : xctx.getVariableNames()) {
                    if(PARAMETER_NS.equals(varName.getNamespaceURI()))
                    {
                        SequenceType type = xctx.getVariableType(varName);
                        bindParameter(expr, varName, type.getItemType());
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
                if(mimeType == null) {
                    if(format == null)
                        mimeType = "text/xml";
                    else if("text".equalsIgnoreCase(format))
                        mimeType = "text/plain";
                    else
                        mimeType = "text/" + format.toLowerCase();
                }
                response.setContentType(mimeType);
                
                ItemSequence seq = expr.evaluate();
                for( ; seq.moveToNextItem(); )
                {
                    Item it = seq.getCurrentItem();
                    if(it.isNode())
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

        private void bindParameter(Expression expr,
                                   QName name, ItemType type)
            throws QizxException, IOException, ServletException, SAXException
        {
            String pname = name.getLocalPart();
            
            if(type.getNodeKind() != ItemType.ATOMIC_TYPE) {
                InputSource data = openXMLSource(pname);
                if(data == null)
                    return;
                Node node = DocumentParser.parse(data);
                if(type.getNodeKind() == Node.ELEMENT)   // must be document
                    node = node.getFirstChild(); // fails if leading PI comment
                expr.bindVariable(name, node);
                return;
            }
            Part part = getPart(pname);
            if(part != null) {
                if("hexBinary".equals(type.getShortName())
                     || "base64Binary".equals(type.getShortName())) {
                    InputStream in = part.getInputStream();
                    byte[] data = FileUtil.loadBytes(in);
                    in.close();
                    expr.bindVariable(name, data, type);
                }
                else if(!"anyType".equals(type.getShortName())) {
                    String charset = getTextPartCharset(part);
                    
                    InputStream in = part.getInputStream();
                    String value = FileUtil.loadString(in, charset);
                    in.close();
                    expr.bindVariable(name, value, type);
                }
                // else do nothing: 
                // TODO lazy access through specialized XQ ext functions
                return;
            }
            
            String value = getParameter(pname);            
            if(value != null)
                expr.bindVariable(name, value, type);
        }

        private String getTextPartCharset(Part part)
        {
            String contentType = part.getContentType();
            String PLAIN_TEXT_TYPE = "text/plain; charset=";
            if(contentType.startsWith(PLAIN_TEXT_TYPE))
                return contentType.substring(PLAIN_TEXT_TYPE.length());
            return "UTF-8";
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

        private Map<String, Expression> listQueries(String path)
            throws Exception
        {
            QizxDriver driver = requireQizxDriver();
            Library lib = getStoredScriptSession(driver);
            return driver.listStoredQueries(path, lib);
        }

        private Library getStoredScriptSession(QizxDriver driver)
            throws RequestException, DataModelException
        {
            Library lib = acquireLibSession(driver.getServicesDefaultLibrary());
            lib.getContext().declarePrefix("param", PARAMETER_NS);
            return lib;
        }

        private void printServiceList(Map<String, Expression> services,
                                      String queryPath)
            throws DataModelException
        {
            response.setContentType(MIME_XML);

            XMLSerializer out = new XMLSerializer(output, "UTF-8");
            out.putElementStart(QN_SERVICES);
            out.putAttribute(QN_PACKAGE, queryPath, null);

            for(String name : services.keySet()) {
                Expression expr = services.get(name);
                
                out.putElementStart(QN_SERVICE);
                out.putAttribute(QN_NAME, name, null);
                out.putAttribute(QN_RESULT_TYPE, expr.getStaticType().toString(), null);
                
                XQueryContext xctx = expr.getContext();
                for(QName varName : xctx.getVariableNames()) {
                    if(PARAMETER_NS.equals(varName.getNamespaceURI())) {
                        SequenceType type = xctx.getVariableType(varName);
                        out.putElementStart(QN_PARAM);
                        out.putAttribute(QN_NAME, varName.getLocalPart(), null);
                        out.putAttribute(QN_TYPE, type.toString(), null);
                        
                        out.putElementEnd(QN_PARAM);                        
                    }
                }
                
                // look for options in script: 
                for(QName oname : xctx.getOptionNames()) {
                    //println("option "+name+" "+xctx.getOptionValue(name));
                    if(oname.getNamespaceURI() == NamespaceContext.OUTPUT_NS) {
                        out.putElementStart(QN_OUTPUT_OPTION);
                        out.putAttribute(QN_NAME, oname.getLocalPart(), null);
                        out.putText(xctx.getOptionValue(oname));
                        out.putElementEnd(QN_OUTPUT_OPTION);
                    }
                }
                
                String src = expr.getSource();
                int xqdoc = src.indexOf("(:~");
                if(xqdoc >= 0) {
                    xqdoc += 3;
                    int docEnd = src.indexOf(":)", xqdoc + 3);
                    if(docEnd > 0) {
                        out.putElementStart(QN_DOCUMENTATION);
                        out.putText(src.substring(xqdoc, docEnd));
                        out.putElementEnd(QN_DOCUMENTATION);
                    }
                }
                
                out.putElementEnd(QN_SERVICE);
            }
            out.putElementEnd(QN_SERVICES);
            out.flush();
        }
    }
}
