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
import com.qizx.api.LibraryMember;
import com.qizx.api.QName;
import com.qizx.api.User;
import com.qizx.xdm.IQName;

/**
 * Base implementation of AccessControl usable for custom implementations.
 * <p>
 * This implementation offers a cache to speed-up access to permissions from a
 * Library member. 
 * <p>
 * Permissions are a mask of basic permissions CONTENT_READ, CONTENT_CHANGE,
 * PROPERTY_READ, PROPERTY_CHANGE corresponding to the methods of
 * AccessControl. More permissions can be added within a total limit of 32.
 * <p>
 * A concrete subclass needs to implement the method computePermissions() which 
 * return the permission mask each time a member is not found in the cache.
 */
public abstract class AccessControlBase
    implements AccessControl
{
    /**
     * Path of collection reserved for storage of AC data.
     */
    protected static final String AC_SECRETS = "/access-control";

    protected static final QName QN_ACDATA = IQName.get("accesscontrol");

    public enum Permission
    {
        GET_CONTENT("GetContent", 1),
        SET_CONTENT("SetContent", 2),
        GET_PROPERTY("GetProperty", 4),
        SET_PROPERTY("SetProperty", 8),
        CONTENT("Content",       GET_CONTENT.mask | SET_CONTENT.mask),
        PROPERTIES("Properties", GET_PROPERTY.mask | SET_PROPERTY.mask),
        READ("Read",   GET_CONTENT.mask | GET_PROPERTY.mask),
        WRITE("Write", SET_CONTENT.mask | SET_PROPERTY.mask),
        ALL("All",     CONTENT.mask | PROPERTIES.mask);
        
        String name;
        int    mask;        // bit mask for fast test
        
        Permission(String name, int mask) {
            this.name = name;
            this.mask = mask;
        }
    }
    

    // permission cache, associated with each instance
    private int cacheSize;
    private LibraryMember[] cachedMember;
    private String[] cachedUser;
    private int[] cachedPerm;
    private int[] cachedPermMask;   // mask of known permissions (0 if unknown)
    private boolean trace = !true;

    /**
     * Default constructor.
     */
    protected AccessControlBase(int cacheSize)
    {
        this.cacheSize = cacheSize;
        cachedMember = new LibraryMember[cacheSize];
        cachedUser = new String[cacheSize];
        cachedPerm = new int[cacheSize];
        cachedPermMask = new int[cacheSize];
    }

    /**
     * Returns the current permission cache size.
     * @return the current permission cache size.
     */
    protected int getCacheSize()
    {
        return cacheSize;
    }

    public boolean mayReadContent(User user, LibraryMember member)
    {
        return hasPermission(user, member, Permission.GET_CONTENT);
    }

    public boolean mayChangeContent(User user, LibraryMember member)
    {
        return hasPermission(user, member, Permission.SET_CONTENT);
    }

    public boolean mayReadProperty(User user,
                                   LibraryMember member, String propertyName)
    {
        return hasPermission(user, member, Permission.GET_PROPERTY);
    }

    public boolean mayChangeProperty(User user,
                                     LibraryMember member, String propertyName)
    {
        return hasPermission(user, member, Permission.SET_PROPERTY);
    }


    /**
     * Cached method to check a permission for a given user. First looks up
     * in the cache and if not found calls the fetchPermission() method.
     * @param user the user associated with the XML Library session that owns
     *        this access control. It should never change.
     * @param member concerned object of the Library
     * @param permission which permission is looked for. The AC implementation
     *        can choose to fetch permissions all at once or selectively.
     * @return true if user has permission
     */
    synchronized 
     protected boolean hasPermission(User user, LibraryMember member,
                                     Permission permission)
    {
        String userName = user.getName();
        int slot = hashMember(member, userName);
        if(trace)
            System.err.println("has perm "+permission.mask+" on "+member+" user "+user+" -> slot "+slot + " mask "+cachedPermMask[slot]+" perms "+cachedPerm[slot]);
        if( !slotMatch(slot, member, userName) ||
            (cachedPermMask[slot] & permission.mask) == 0)
        {
            fetchPermission(member, user, permission, slot);
        }
        return userName.equals(cachedUser[slot]) &&
               (cachedPerm[slot] & permission.mask) != 0;
    }

    private boolean slotMatch(int slot, LibraryMember member, String userId)
    {
        return member.equals(cachedMember[slot]) &&
               userId.equals(cachedUser[slot]);
    }

    /**
     * This method has be implemented by a concrete subclass: it is called when
     * a library member is not found in cache.
     * <p>
     * The implementation has to look for the permission on the member, and to
     * store the definition mask and the permissions into the cache. It might
     * fetch all permissions for the member or only a part, but at least the
     * desired permission.
     * @param member concerned object of the Library
     * @param user user concerned by the access control
     * @param permission bit mask of permission initially searched for
     * @param slot caches slot where to store fetched permissions and mask
     */
    protected abstract void fetchPermission(LibraryMember member, User user,
                                            Permission permission, int slot);


    /**
     * Cache permissions for a Library member.
     * @param perms OR of permissions
     * @param mask of defined permissions
     */
    protected void cachePermissions(int slot, LibraryMember member, String userId,
                                    int perms, int mask)
    {
        cachedMember[slot] = member;
        cachedUser[slot] = userId;
        cachedPermMask[slot] |= mask;
        cachedPerm[slot] = (cachedPerm[slot] &~ mask) | (perms & mask);
        if(trace)
            System.err.println("cache perm "+perms+" "+ mask+" on "+member+" for "+userId+" -> slot "+slot);
    }
    
    /**
     * Erases permission from cache to force recomputation.
     * @param member library member of interest
     */
    protected void clearPermissions(LibraryMember member, User user)
    {
        String name = user.getName();
        int slot = hashMember(member, name);
        if(slotMatch(slot, member, name))
            cachedMember[slot] = null;
        if(trace)
            System.err.println("clear perm on "+member+" for "+user+" -> slot "+slot);
    }
    
    protected void clearCache()
    {
        for (int i = 0; i < cachedMember.length; i++) {
            cachedMember[i] = null;
            cachedPermMask[i] = 0;
            cachedPerm[i] = 0;
        }
    }
    
    private int hashMember(LibraryMember member, String user)
    {
        int h = (member.hashCode() ^ user.hashCode()) % cacheSize;
        if (h < 0)
            h += cacheSize;
        
        return h;
    }
}
