/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.util.accesscontrol;

import com.qizx.api.*;
import com.qizx.api.util.XMLSerializer;
import com.qizx.util.basic.PathUtil;
import com.qizx.xdm.DocumentParser;
import com.qizx.xdm.IQName;
import com.qizx.xquery.LibraryManagerImpl;

import org.xml.sax.InputSource;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

/**
 * AccessControl based on ACL (Access Control Lists).
 * <p>
 * Each member of a Library can bear a list of ACE (Access Control Entry) that
 * specifies a set of permissions for a User (or a group of users, or a role).
 * <p>
 * An ACE can be positive (grant) or negative (deny).
 * <p>
 * Permissions are inherited along the Collection tree: when permissions are
 * granted to users on a Collection, these permissions are also granted on all
 * the contained Library members. But some descendants can deny the permissions
 * through an ACE set on them.
 */
public class ACLAccessControl extends AccessControlBase
{
    public static final ACLAll ALL = new ACLAll();
    public static final String MIME_TYPE = "text/xml";
    
    // HACK: a special user which is always allowed to read and write
    // the storage of ACLs
    public static final String ADMIN = "acl-admin";

    private static final String ACL_DOC = AC_SECRETS + "/ACL.xml";

    private static final QName QN_OBJECT = IQName.get("member");
    private static final QName QN_PATH = IQName.get("path");
    private static final QName QN_GRANT = IQName.get("grant");
    private static final QName QN_DENY = IQName.get("deny");
    private static final QName QN_USER = IQName.get("user");
    private static final QName QN_GROUP = IQName.get("group");
    private static final QName QN_ROLE = IQName.get("role");
    private static final QName QN_PERMS = IQName.get("permissions");
    
    private static final HashMap<String,Permission> permissionTable =
        new HashMap<String, Permission>();
    
    static {
        for(Permission p : Permission.values()) {
            permissionTable.put(p.name, p);
        }
    }

    
    // maps path to ACL:
    private HashMap<String, ACL> acls;
    private LibraryManagerImpl libraryManager;
    private String libraryName;

    // must provide a default constructor
    public ACLAccessControl()
    {
        super(97);
        acls = new HashMap<String, ACL>();
            
        // initialize:
        // by default all users can do everything:
        addEntry(ACEType.GRANT, "/", ALL, Permission.ALL);
        // only admin can access ACLs:
        addEntry(ACEType.DENY, AC_SECRETS, ALL, Permission.ALL);
        addEntry(ACEType.GRANT, AC_SECRETS, ADMIN, Permission.ALL);
    }

    public void connectTo(LibraryManager libraryManager, String libraryName)
    {
        this.libraryManager = (LibraryManagerImpl) libraryManager;
        this.libraryName = libraryName;
        
        // try to load /access-control/ACL.xml
        boolean exists = false;
        if (libraryManager != null) {
            try {
                Library library = this.libraryManager.openLibrary(libraryName);
                Document aclStore = library.getDocument(ACL_DOC);
                if (aclStore != null) {
                    acls = load(aclStore.getDocumentNode());
                    exists = true;
                }
                library.close();
            }
            catch (DataModelException e) {
                this.libraryManager.getLog().error("ERROR loading ACLs: ", e);
            }
        }
        // save if new
        if(!exists && libraryManager != null) {
            saveToLibrary();
        }
    }
   
    /**
     * Converts a Permission name into a Permission descriptor.
     */
    public static Permission getPermission(String permName)
    {
        return permissionTable.get(permName);
    }

    // for tests
    private UserSet makeUserSet(String users)
    {
        if("*".equals(users))
            return ALL;
        if(users.startsWith("role:"))
            return new ACLRole(users.substring(5));
        return new ACLUser(users);
    }

    /**
     * Implementation.
     */
    public synchronized void addEntry(ACEType type, String path,
                                      String users, Permission perm)
    {
        addEntry(type, path, makeUserSet(users), perm);
    }
    
    /**
     * Implementation.
     */
    public synchronized void addEntry(ACEType type, String path,
                                      UserSet users, Permission perm)
    {
        ACL acl = findACEsForPath(path);
        if(acl == null) {
            acls.put(path, acl = new ACL(path));
        }
        acl.aces.add(new ACE(type, users, perm.mask));
        clearCache();
    }
    
    /**
     * Implementation.
     */
    public synchronized void clearEntries(ACEType type, String path)
    {
        acls.remove(path);
    }
    
    /**
     * Gets access-right data pertaining to a Library member.
     * @param path
     *        path of the Library member
     * @param inherited
     *        if false, returns access data pertaining only to the specified
     *        Library member. Otherwise, returns all access data inherited from
     *        parent Collections.
     * @return access data in string form. The actual format depends on the
     *         AccessControl implementation.
     */
    public String getAccessRights(String path, boolean inherited)
        throws LibraryException
    {
        path = PathUtil.normalizePath(path, true);
        StringWriter out = new StringWriter();
        XMLSerializer serial = new XMLSerializer(out);
        serial.setIndent(2);
        
        ACL acl = findACEsForPath(path);
        try {
            printHeader(serial);
            printACL(serial, acl, path);
            
            if(inherited) {
                int length = parentLength(path, path.length());
                for(; length > 0; ) {
                    acl = findACEsForPath(path.substring(0, length));
                    if(acl != null)
                        printACL(serial, acl, acl.path);
                    length = parentLength(path, length);
                }
            }
            serial.putElementEnd(QN_ACDATA);
            serial.flush();
        }
        catch (DataModelException e) {
            throw new LibraryException(e.getMessage(), e);
        }
        return out.toString();
    }

    /**
     * Defines or modifies access-rights. The data passed here will
     * <em>replace</em> existing data for the concerned Library members.
     * <p>There is no security check here, it must be done beforehand.
     * @param rights access-rights in string form.
     */
    public void setAccessRights(String rights)
        throws DataModelException
    {
        try {
            // parse ACLs and merge with ACLS stored here
            // An empty ACL is removed
            Node root = DocumentParser.parse(new InputSource(new StringReader(rights)));
            HashMap<String, ACL> table = load(root);
            for(ACL acl : table.values()) {
                if(acl.aces.size() == 0)
                    acls.remove(acl.path);
                else
                    acls.put(acl.path, acl); // replace
            }
        }
        catch (Exception e) {
            throw new DataModelException("ACL parse error: " + e.getMessage(), e);
        }
        clearCache();
        
        saveToLibrary();
    }

    private void saveToLibrary()
    {
        if(libraryManager == null || libraryName == null)
            return;
        try {
            Library library = libraryManager.openLibrary(libraryName);
            XMLPushStream out = library .beginImportDocument(ACL_DOC);
            save(out);
            library.endImportDocument();
            library.commit();
            library.close();
        }
        catch (DataModelException e) {
            libraryManager.getLog().error("ERROR saving ACLs: ", e);
        }
    }

    private HashMap<String, ACL> load(Node documentNode)
        throws DataModelException
    {
        if(documentNode == null)
            return null;
        Node top = documentNode.getFirstChild();
        if(top == null)
            return null;
        HashMap<String, ACL> table = new HashMap<String, ACL>();
        Node item = top.getFirstChild();
        for (; item != null; item = item.getNextSibling()) {
            QName name = item.getNodeName();
            if (name == null)
                continue;
            if (name == QN_USER) {
            }
            else if (name == QN_ROLE) {
            }
            else if (name == QN_OBJECT) {
                ACL acl = parseACL(item);
                table.put(acl.path, acl);
            }
            else
                throw new DataModelException("invalid top-level item " + name);
        }
        return table;
    }

    private ACL parseACL(Node item) throws DataModelException
    {
        Node pathAttr = item.getAttribute(QN_PATH);
        if (pathAttr == null)
            throw new DataModelException("missing attribute " + QN_PATH);
        
        String spath = PathUtil.normalizePath(pathAttr.getStringValue(), true);
        ACL acl = new ACL(spath);
        
        Node aceNode = item.getFirstChild();
        for( ; aceNode != null; aceNode = aceNode.getNextSibling()) {
            QName name = aceNode.getNodeName();
            if(name == null)
                continue;
            boolean grant = name == QN_GRANT;
            if(!grant && name != QN_DENY)
                throw new DataModelException("invalid ACE");
            Node permAttr = aceNode.getAttribute(QN_PERMS);
            if (permAttr == null)
                throw new DataModelException("missing attribute " + QN_PERMS);
            int perms = parsePermissions(permAttr.getStringValue());
            UserSet subjects = null;
            Node subj = aceNode.getAttribute(QN_USER);
            if (subj != null) {
                subjects = parseUserList(subj.getStringValue(), false);
            }
            else if ((subj = aceNode.getAttribute(QN_ROLE)) != null) {
                subjects = parseUserList(subj.getStringValue(), true);
            }
            else throw new DataModelException("missing user or role");
            ACE ace = new ACE(grant? ACEType.GRANT : ACEType.DENY, subjects, perms);
            acl.aces.add(ace);
        }
        return acl;
    }

    private int parsePermissions(String value) throws DataModelException
    {
        String[] perms = value.split("[ ,;]+");
        int mask = 0;
        for(String p : perms) {
            if(p.length() == 0)
                continue;
            Permission perm = getPermission(p);
            if(perm == null)
                throw new DataModelException("invalid permission " + p);
            mask |= perm.mask;
        }
        return mask;
    }

    private UserSet parseUserList(String value, boolean forRole)
    {
        String[] users = value.split("[ ,;]+");
        ACLGroup g = new ACLGroup("");
        for(String u : users) {
            if(u.equals("*"))
                g.add(ALL);
            else
                g.add(forRole? new ACLRole(u) : new ACLUser(u));
        }
        if(g.users.size() == 1)
            return g.users.get(0);
        return g;
    }

    private void save(XMLPushStream store) throws DataModelException
    {
        printHeader(store);
        ACL[] values = acls.values().toArray(new ACL[acls.size()]);
        Arrays.sort(values, new Comparator<ACL>() {
            public int compare(ACL a1, ACL a2) {
                return a1.path.compareTo(a2.path);
            }
        });
        for(ACL acl : values) {
            printACL(store, acl, acl.path);
        }
        store.putElementEnd(QN_ACDATA);
    }

    private void printHeader(XMLPushStream serial)
        throws DataModelException
    {
        serial.putElementStart(QN_ACDATA);
        serial.putAttribute(IQName.get("class"), getClass().getName(), null);
        serial.putText("\n");
    }

    /**
     * Simple format (non XML!). This is for use by an admin app.
     */
    private void printACL(XMLPushStream out, ACL acl, String origPath)
        throws DataModelException
    {
        out.putElementStart(QN_OBJECT);
        out.putAttribute(QN_PATH, origPath, null);
        if(acl != null) {
            for (ACE ace : acl.aces) {
                QName ruleType = (ace.type == ACEType.GRANT)? QN_GRANT : QN_DENY;
                out.putElementStart(ruleType);
                if(ace.users instanceof ACLUser) {
                    out.putAttribute(QN_USER, ace.users.name, null);
                }
                else if(ace.users instanceof ACLGroup) {
                    out.putAttribute(QN_GROUP, ace.users.name, null);
                }
                else if(ace.users instanceof ACLRole) {
                    out.putAttribute(QN_ROLE, ace.users.name, null);
                }
                else if(ace.users instanceof ACLAll) {
                    out.putAttribute(QN_USER, "*", null);
                }
                // permissions:
                int perms = ace.permissions;
                StringBuilder buf = new StringBuilder();
                if((perms & Permission.GET_CONTENT.mask) != 0)
                    buf.append(Permission.GET_CONTENT.name).append(' ');
                if((perms & Permission.SET_CONTENT.mask) != 0)
                    buf.append(Permission.SET_CONTENT.name).append(' ');
                if((perms & Permission.GET_PROPERTY.mask) != 0)
                    buf.append(Permission.GET_PROPERTY.name).append(' ');
                if((perms & Permission.SET_PROPERTY.mask) != 0)
                    buf.append(Permission.SET_PROPERTY.name).append(' ');
                out.putAttribute(QN_PERMS, buf.toString(), null);
                out.putElementEnd(ruleType);
            }
        }
        else out.putText(" ");
        out.putElementEnd(QN_OBJECT);
        out.putText("\n");
    }

    @Override
    protected void fetchPermission(LibraryMember member, com.qizx.api.User user,
                                   Permission perm, int slot)
    {
        String path = member.getPath();
        
        int length = path.length();
        ACE found = null;
        for(; length > 0; ) {
            ACL acl = findACEsForPath(path.substring(0, length));
            if(acl != null)
                for (ACE ace : acl.aces) {
                    if(ace.users.matches(user)
                       && (perm.mask & ace.permissions) != 0)
                        found = ace;
                }
            if(found != null)
                break;
            length = parentLength(path, length);
        }

        if(found != null) {
            if(found.type == ACEType.GRANT)
                cachePermissions(slot, member, user.getName(), found.permissions, perm.mask);
            else
                cachePermissions(slot, member, user.getName(), 0, perm.mask);
        }
    }

    private int parentLength(String path, int length)
    {
        if(length <= 1)
            return 0;       // root has no parent
        int nl = path.lastIndexOf('/', length - 1);
        return nl == 0? 1 : nl;
    }

    private ACL findACEsForPath(String path)
    {
        return acls.get(path);
    }

    protected static class ACL
    {
        String path;
        ArrayList<ACE> aces;
        
        public ACL(String path)
        {
            this.path = path;
            aces = new ArrayList<ACE>();
        }
        
        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder("ACL path=");
            buf.append(path);
            buf.append(' ');
            buf.append(aces);
            return buf.toString();
        }
    }
    
    public enum ACEType {
        GRANT,
        DENY
    }

    /**
     * AC Entry: GRANT or DENY Permissions on Object to Users.
     */
    protected static class ACE
    {
        ACEType type;
        UserSet users;
        int permissions;
        
        ACE(ACEType type, UserSet users, int permissions)
        {
            this.type = type;
            this.users = users;
            this.permissions = permissions;
        }

        public String toString() {
            return type + "(" + users + ", " + permissions + ")";
        }
    }


    /**
     * A set of users attached to an ACE (grant/deny).
     */
    static abstract class UserSet
    {
        protected String name;

        abstract boolean matches(com.qizx.api.User user);

        public String getName()
        {
            return name;
        }
    }

    /**
     * Extended User supporting Role checking.
     */
    public interface User extends com.qizx.api.User
    {
        /**
         * Returns the name of the User.
         * @return a String representing a unique name for the user
         */
        String getName();

        /**
         * Checks whether a User fulfils a Role.
         * If roles are not supported by an AccessControl implementation, 
         * can return any value.
         * @param roleName a name identifying a Role
         * @return true if the User fulfils the Role specified by roleName
         */
        boolean isInRole(String roleName);
    }

    /**
     * Internal representation of a User.
     */
    static class ACLUser extends UserSet
    {
        ACLUser(String name)
        {
            this.name = name;
        }

        boolean matches(com.qizx.api.User user)
        {
            return name.equals(user.getName());
        }

        public String toString() {
            return "User(" + name + ")";
        }
    }

    /**
     * Internal representation of a Group.
     */
    static class ACLGroup extends UserSet
    {
        private ArrayList<UserSet> users;
        
        ACLGroup(String name)
        {
            this.name = name;
        }
        
        public void add(UserSet user)
        {
            if(users == null)
                users = new ArrayList<UserSet>();
            users.add(user);
        }
        
        public boolean matches(com.qizx.api.User user)
        {
            if(users != null)
                for(UserSet u : users) {
                    if(u != null && u.matches(user))
                        return true;
                }
            return false;
        }

        public String toString() {
            return "Group(" + name + ", " + users + ")";
        }
    }

    /**
     * Representation of a Role.
     */
    static class ACLRole extends UserSet
    {
        ACLRole(String name)
        {
            this.name = name;
        }

        public String toString() {
            return "Role(" + name + ")";
        }

        boolean matches(com.qizx.api.User user)
        {
            return (user instanceof User) && ((User) user).isInRole(name);
        }
    }

    protected static class ACLAll extends UserSet
    {      
        public ACLAll() {
        }

        @Override
        boolean matches(com.qizx.api.User user) {
            return true;    // by definition
        }

        public String toString() {
            return "All";
        }
    }
}
