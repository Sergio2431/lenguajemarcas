/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */

package com.qizx.server.api;

import com.qizx.api.*;
import com.qizx.api.util.XMLSerializer;
import com.qizx.server.util.QizxRequestBase;
import com.qizx.server.util.RequestException;
import com.qizx.xquery.ExpressionImpl;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;

/**
 * Execute a XQuery script.
 */
public class EvalRequest extends QizxRequestBase
{
    private static final String ITEMS_FORMAT = "items";
    private static final String HTML_FMT = "html";
    private static final String XHTML_FMT = "xhtml";

    public String getName()
    {
        return "eval";
    }

    public void handleGet()
        throws ServletException, IOException
    {
        handlePost();
    }

    public void handlePost()
        throws ServletException, IOException
    {
        String libName = getLibraryParam();
        //String path = getPathParam();
        String queryParam = getParameter("query"); 
        if(queryParam == null) { // in a part?
            queryParam = getPartAsString("query");
            if(queryParam == null)
                requiredParam("query");
        }
        String format = getParameter("format");
        String encoding = getParameter("encoding", "UTF-8");
        int maxTime = getIntParameter("maxtime", -1);
        int count = getIntParameter("count", -1);
        int first = getIntParameter("first", 0);
        log("count "+count+" first "+first);
        
        boolean wrapped = ITEMS_FORMAT.equals(format);
//        if(count < 0 && !wrapped)
//            count = 1;
        
        try {
            XQuerySession lib = acquireLibSession(libName);
            
            Expression expr = lib.compileExpression(queryParam);
            
            XMLSerializer serial = new XMLSerializer(output, encoding);
            QName RESULTS = lib.getQName("items");

            if(HTML_FMT.equalsIgnoreCase(format)) {
                serial.setOption(XMLSerializer.METHOD, "html");
                response.setContentType("text/html");
            }
            else if(XHTML_FMT.equalsIgnoreCase(format)) {
                serial.setOption(XMLSerializer.METHOD, "xhtml");
                response.setContentType("text/xhtml+xml");
            }
            else {
                response.setContentType(MIME_XML);
            }

            if(maxTime > 0)
                expr.setTimeOut(maxTime);
            else {
                int timeout = getDriver().evalTimeout;
                if(timeout > 0)
                    expr.setTimeOut(timeout);
            }
            /////((ExpressionImpl) expr).setCompilationTrace(new PrintWriter(System.err, true));

            ItemSequence items = expr.evaluate();
            int itemCnt = 0;

            items.moveTo(first);
            
            if (wrapped) {
                serial.putDocumentStart();
                serial.putElementStart(RESULTS);
                serial.putAttribute(lib.getQName("total-count"),
                                    Long.toString(items.countItems()), null);
            }
            for(; (count < 0 || itemCnt < count) && items.moveToNextItem(); ++itemCnt)
            {
                if (wrapped) {
                    serial.putElementStart(RESTAPIServlet.NM_ITEM);
                    serial.putAttribute(RESTAPIServlet.NM_TYPE,
                                        items.getType().toString(), null);
                }
                
                if (items.isNode()) {
                    items.export(serial);
                }
                else {
                    if (itemCnt > 0 && !wrapped)
                        serial.putText(" "); // some space
                    serial.putAtomText(items.getString());
                }
                if (wrapped)
                    serial.putElementEnd(RESTAPIServlet.NM_ITEM);
            }
            if (wrapped) {
                serial.putElementEnd(RESULTS);
                serial.putDocumentEnd();
            }

            serial.flush();
        }
        catch (CompilationException e) {
            throw new RequestException(e);
        }
        catch (EvaluationException e) {
            if(e.getErrorCode() == EvaluationException.TIME_LIMIT)
                throw new RequestException(TIMEOUT, e);
            throw new RequestException(e);
        }
        catch (QizxException e) {
            throw new RequestException(e);
        }
    }
}
