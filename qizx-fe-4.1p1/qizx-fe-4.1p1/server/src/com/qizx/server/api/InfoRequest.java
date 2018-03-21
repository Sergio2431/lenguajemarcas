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
import com.qizx.api.Product;
import com.qizx.api.QName;
import com.qizx.api.QizxException;
import com.qizx.api.util.XMLSerializer;
import com.qizx.server.util.RequestException;
import com.qizx.server.util.QizxDriver;
import com.qizx.server.util.QizxRequestBase;
import com.qizx.xdm.IQName;

import java.io.IOException;

/**
 * Get information about the server as a list of properties.
 */
public class InfoRequest extends QizxRequestBase
{
    QName prop, name;

    public String getName()
    {
        return "info";
    }

    public void handlePost()
        throws RequestException, IOException
    {
        handleGet();
    }
    
    public void handleGet()
        throws RequestException, IOException
    {
        try {
            QizxDriver driver = requireQizxDriver();
            QName wrapper = IQName.get("info");
            prop = IQName.get("property");
            name = IQName.get("name");
            
            response.setContentType(MIME_XML);
            XMLSerializer out = new XMLSerializer(output, "UTF-8");
            out.putDocumentStart();
            out.putElementStart(wrapper);
            
            putProp(out, "server-name", driver.getName());
            putProp(out, ("product-name"), Product.PRODUCT_NAME);
            putProp(out, ("product-version"), Product.FULL_VERSION);
            putProp(out, ("product-vendor"), Product.VENDOR);
            putProp(out, ("xquery-version"), Product.XQUERY_VERSION);
            
            putProp(out, ("java-version"), System.getProperty("java.version"));
            putProp(out, ("java-vm-name"), System.getProperty("java.vm.name"));
            putProp(out, ("java-vm-version"), System.getProperty("java.vm.version"));
            putProp(out, ("java-vm-vendor"), System.getProperty("java.vendor"));
            
            putProp(out, ("OS-name"), System.getProperty("os.name"));
            putProp(out, ("OS-version"), System.getProperty("os.version"));
            
            putProp(out, ("architecture"), System.getProperty("os.arch"));
            putProp(out, ("processors"), 
                         "" + Runtime.getRuntime().availableProcessors());

            out.putElementEnd(wrapper);
            out.putDocumentEnd();
            out.flush();
        }
        catch (QizxException e) {
            throw new RequestException(e);
        }
    }

    private void putProp(XMLSerializer out, String propName, String value)
        throws DataModelException
    {
        out.putElementStart(prop);
        out.putAttribute(name, propName, null);
        out.putText(value);
        out.putElementEnd(prop);
    }
}
