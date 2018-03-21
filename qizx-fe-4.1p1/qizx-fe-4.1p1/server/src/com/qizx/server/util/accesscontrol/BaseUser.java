/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.util.accesscontrol;

import com.qizx.api.AccessControl;
import com.qizx.api.User;

/**
 * Base implementation of {@link User}. Can be used with any {@link AccessControl}
 * implementation. 
 */
public class BaseUser
    implements User
{
    protected String name;

    public BaseUser(String name) {
        this.name = name;
    }
    
    public String getName()
    {
        return name;
    }
}
