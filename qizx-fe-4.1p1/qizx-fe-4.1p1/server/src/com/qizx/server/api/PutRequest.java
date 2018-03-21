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
import com.qizx.api.Document;
import com.qizx.api.Library;
import com.qizx.api.QName;
import com.qizx.server.util.QizxRequestBase;
import com.qizx.server.util.RequestException;
import com.qizx.util.basic.PathUtil;
import com.qizx.xdm.IQName;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.IOException;

public class PutRequest extends QizxRequestBase
{
    private static final int LOCK_TIME_OUT = 5000;

    public String getName()
    {
        return "put";
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
            
            log("========== starting import ");

            // lock an enclosing collection? TODO broken
            if(lock != null) {
                if(lib.lockCollection(lock, LOCK_TIME_OUT) == null)
                    throw new RequestException("XMLData",
                                              "cannot lock collection " + lock);
            }

            response.setContentType(MIME_PLAIN_TEXT);
            StringBuilder status = new StringBuilder();
            
            String curDataParam = "data";
            InputSource data = openXMLSource(curDataParam);
            
            int rank = 2, errorCount = 0;
            for( ; ; ++rank)
            {
                if(data == null)
                    throw new RequestException(BAD_REQUEST, "no XML data for parameter " + curDataParam);
                path = PathUtil.normalizePath(path, true);

                try {
                    log("import XML " + path + " from " + curDataParam);
                    Document doc = lib.importDocument(path, data);
                }
                catch (DataModelException e) {
                    Exception ex = e;
                    if (e.getCause() instanceof SAXException)
                        ex = (SAXException) e.getCause();
                    
                    status.append(ex.getClass().getSimpleName()).append('\t');
                    status.append(path).append('\t');
                    if(ex instanceof SAXParseException) {
                        SAXParseException sax = (SAXParseException) ex;
                        status.append("[line " + sax.getLineNumber() + "] ");
                    }
                    status.append(ex.getMessage()).append('\n');
                    
                    ++ errorCount;
                }
//                finally {
//                    if(currentPart != null)
//                        currentPart.delete();
//                }
                // more?
                path = getParameter("path" + rank);
                if(path == null)
                    break;
                curDataParam = "data" + rank;
                data = openXMLSource(curDataParam);
            }
            
            lib.commit();
            
            log("import of " + (rank - 1 - errorCount) + " documents, "
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
