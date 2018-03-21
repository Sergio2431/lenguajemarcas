/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.api;

import com.qizx.api.DataModelException;
import com.qizx.api.LibraryMember;
import com.qizx.api.Node;
import com.qizx.api.QName;
import com.qizx.api.util.XMLSerializer;
import com.qizx.server.util.QizxDriver;
import com.qizx.server.util.QizxRequestBase;
import com.qizx.server.util.Request;
import com.qizx.server.util.ServletBase;
import com.qizx.xdm.IQName;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

public class RESTAPIServlet extends ServletBase
{
    // QNames for generated XML data:
    public static final QName NM_PROPERTIES = IQName.get("properties");
    public static final QName NM_PROPERTY = IQName.get("property");
    public static final QName NM_PATH = IQName.get("path");
    public static final QName NM_NAME = IQName.get("name");
    public static final QName NM_TYPE = IQName.get("type");
    public static final QName NM_ITEM = IQName.get("item");
    
    public void init()
        throws ServletException
    {
        super.init();
        
        // read configuration
        QizxDriver.initialize(this);
        
        
        // ----- general requests:

        addHandler(new ListLibRequest());
        
        addHandler(new EvalRequest());      // q/o
        addHandler(new MkColRequest());
        addHandler(new PutRequest());
        addHandler(new PutNonXMLRequest());

        addHandler(new GetRequest());
        defaultHandler("GET", new GetRequest());

        addHandler(new MoveRequest());
        addHandler(new CopyRequest());
        addHandler(new DeleteRequest());
        
        addHandler(new GetPropRequest());
        addHandler(new SetPropRequest());
        addHandler(new QueryPropRequest());

        addHandler(new InfoRequest());      // q/o
        
        // ----- admin requests:
        
        addHandler(new ServerControlRequest());      // q/o
        
        addHandler(new MkLibRequest());
        addHandler(new DelLibRequest());
       
        addHandler(new GetIndexingRequest());
        addHandler(new SetIndexingRequest());
        addHandler(new ReindexRequest());
        addHandler(new OptimizeRequest());
        addHandler(new BackupRequest());
        addHandler(new ProgressRequest());
        
        addHandler(new GetAclRequest());
        addHandler(new SetAclRequest());
    }

    public void destroy()
    {
        log("destroying servlet " + getClass());
        QizxDriver.terminate(getServletContext());
        log("servlet " + getClass() + " destroyed");
        super.destroy();
    }
    
    
    protected void sendError(HttpServletResponse resp, int code, String message)
        throws IOException
    {
        resp.setContentType(QizxRequestBase.MIME_QIZX_ERROR);
        String scode = code == HttpServletResponse.SC_BAD_REQUEST? 
                Request.BAD_REQUEST : Request.SERVER;
        resp.getOutputStream().println(scode + ": " + message);
    }

    // utility for getprop, queryprop and put
    protected static void putProperties(LibraryMember member, XMLSerializer out,
                                        HashSet<String> propNames)
        throws DataModelException
    {
        out.putElementStart(NM_PROPERTIES);
        out.putAttribute(NM_PATH, member.getPath(), null);
        String[] names = member.getPropertyNames();
        for (int i = 0; i < names.length; i++) {
            if(propNames != null && !propNames.contains(names[i]))
                continue;
            Object value = member.getProperty(names[i]);
            if(value == null)   // strange thing
                continue;
            out.putElementStart(NM_PROPERTY);
            out.putAttribute(NM_NAME, names[i], null);
            String type = null;
            if(value instanceof Node) {
                Node node = (Node) value;
                out.putAttribute(NM_TYPE, node.getNodeKind() + "()", null);
                out.putNodeCopy(node, 0);   // strange if node is attribute
            }
            else {
                if(value instanceof Date) {
                    value = new com.qizx.api.util.time.DateTime((Date) value, 0);
                    type = "dateTime";
                }
                else if(value instanceof Double)
                    type = "double";
                else if(value instanceof Long)
                    type = "integer";
                else if(value instanceof Boolean)
                    type = "boolean";
                if(type != null)
                    out.putAttribute(NM_TYPE, type, null);
    
                out.putText(value.toString());
            }
            out.putElementEnd(NM_PROPERTY);
        }
        out.putElementEnd(NM_PROPERTIES);
    }
}
