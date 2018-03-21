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
import com.qizx.api.Library;
import com.qizx.api.NonXMLDocument;
import com.qizx.api.QName;
import com.qizx.server.util.QizxRequestBase;
import com.qizx.server.util.RequestException;
import com.qizx.util.basic.PathUtil;
import com.qizx.xdm.IQName;

import com.xmlmind.multipartreq.Part;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;

public class PutNonXMLRequest extends QizxRequestBase
{
    private static final int LOCK_TIME_OUT = 5000;

    public String getName()
    {
        return "putnonxml";
    }

    static QName NM_PUT = IQName.get("put");
    static QName NM_ERROR = IQName.get("error");
    static QName NM_PATH = IQName.get("path");
    static QName NM_TYPE = IQName.get("type");
    
    public void handlePost()
        throws RequestException, IOException
    {
        String path = getPathParam();
        if(path == null)
            throw new RequestException(BAD_REQUEST, "at least one document required");

        String libName = getParameter("library");
        String lock = getParameter("lock");
        
        try {
            Library lib = acquireLibSession(libName);
            
            log("========== starting non-XML import ");

            // lock an enclosing collection? TODO broken
            if(lock != null) {
                if(lib.lockCollection(lock, LOCK_TIME_OUT) == null)
                    throw new RequestException("XMLData",
                                              "cannot lock collection " + lock);
            }

            response.setContentType(MIME_PLAIN_TEXT);
            StringBuilder status = new StringBuilder();
            
            String curDataParam = "data";
            
            int rank = 2, errorCount = 0;
            for( ; ; ++rank)
            {
                path = PathUtil.normalizePath(path, true);
                
                try {
                    log("import non-XML " + path + " rank " + rank);
                    Part content = getPart(curDataParam);
                    if(content == null)
                        throw new RequestException(BAD_REQUEST,
                                       "no data for parameter " + curDataParam);
                    long size = content.getSize();
                    InputStream stream = content.getInputStream();
                    NonXMLDocument doc =
                        lib.importNonXMLDocument(path, false, stream);
                    stream.close();
                    String mimeType = content.getContentType();
                    if(mimeType != null)
                        doc.setProperty(MIME_PROPERTY, mimeType);
                    doc.setIntegerProperty("size", size);
                }
                catch (ServletException e) {
                    ++ errorCount;
                    status.append(e.getClass().getSimpleName()).append('\t');
                    status.append(path).append('\t');
                    status.append(e.getMessage()).append('\n');
                }

                // more?
                path = getParameter("path" + rank);
                if(path == null)
                    break;
                curDataParam = "data" + rank;
            }
            
            lib.commit();
            
            log("import of " + (rank - 1 - errorCount) + " non-XML documents, "
                + errorCount + " error(s)");
            
            status.append("IMPORT ERRORS ").append(errorCount);
            println(status.toString());
        }
        catch (IOException e) {
            log("put: IO error " + e);
            throw new RequestException(SERVER, e);
        }
        catch (DataModelException e) {
            log("put: dm error " + e);
            throw new RequestException(e);
        }
    }
}
