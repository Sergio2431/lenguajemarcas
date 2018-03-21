/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.util;

import com.qizx.api.QizxException;

import javax.servlet.ServletException;

public class RequestException extends ServletException
{
    public String code;
    
    public RequestException(String code, String message)
    {
        super(message);
        this.code = code;
    }

    public RequestException(Exception cause)
    {
        super(cause);
        this.code = code;
    }

    public RequestException(String code, Exception cause)
    {
        super(cause);
        this.code = code;
    }
}
