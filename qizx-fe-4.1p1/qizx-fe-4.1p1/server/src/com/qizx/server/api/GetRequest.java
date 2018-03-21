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
import com.qizx.util.basic.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;

public class GetRequest extends QizxRequestBase
{

    public String getName()
    {
        return "get";
    }

    public void handlePost()
        throws RequestException, IOException
    {
        handleGet();
    }

    public void handleGet()
        throws RequestException, IOException
    {
        String libName = getLibraryParam();
        String path = getPathParam();
        String opts = getParameter("options");
        String[] options = (opts == null)? null : opts.split("[\n\r\t;,]+");
        
        try {
            Library lib = acquireLibSession(libName);         
            LibraryMember member = requireMember(lib, path);

            if(member.isCollection())
            {
                response.setContentType(MIME_PLAIN_TEXT);
                Collection col = (Collection) member;
                LibraryMemberIterator iter = col.getChildren();
                ArrayList<String> res = new ArrayList<String>();
                for( ; iter.moveToNextMember(); ) {
                    member = iter.getCurrentMember();
                    if(member.isCollection())
                        res.add(member.getPath() + "/");
                    else
                        res.add(member.getPath());
                }
                Collections.sort(res);
                for (String mpath : res) {
                    println(mpath);
                }
            }
            else if(member instanceof Document) {
                response.setContentType(MIME_XML);
                Document doc = (Document) member;
                XMLSerializer serial = new XMLSerializer(output, "UTF-8");
                if(options != null)
                    for (int i = 0; i < options.length; i++) {
                        String op = options[i];
                        int eq = op.indexOf('=');
                        String optName = (eq < 0)? op : op.substring(0, eq);
                        String optValue = (eq < 0)? "true" : op.substring(eq + 1);
                        try {
                            serial.setOption(optName, optValue);
                        }
                        catch (DataModelException ignored) { ; }
                    }
                serial.putNodeCopy(doc.getDocumentNode(), 0);
                serial.flush();
            }
            else if(member instanceof NonXMLDocument) {
                String mimeType = (String) member.getProperty(MIME_PROPERTY);
                response.setContentType(mimeType != null? mimeType : MIME_DATA);
                NonXMLDocument nonx = (NonXMLDocument) member;
                InputStream export = nonx.open();
                FileUtil.copy(export, output, null);
                export.close();
            }
        }
        catch (DataModelException e) {
            throw new RequestException(e);
        }

    }
}
