/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.util;

import com.qizx.api.*;
import com.qizx.api.fulltext.FullTextFactory;
import com.qizx.api.util.DefaultModuleResolver;
import com.qizx.server.api.BackupRequest;
import com.qizx.server.util.accesscontrol.ACLAccessControl;
import com.qizx.server.util.accesscontrol.BaseUser;
import com.qizx.util.basic.FileUtil;
import com.qizx.xdm.DocumentPool;

import com.xmlmind.multipartreq.MultipartConfig;

import org.apache.xml.resolver.CatalogManager;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.Templates;

/**
 * Manages access to a Qizx Library engine on behalf of a Web App. A single
 * instance is attached to a Web App context.
 * <p>
 * Services provided:
 * <ul>
 * <li>Load configuration
 * <li>Start and stop Qizx engine
 * <li>Open XML Library sessions.
 * </ul>
 */
public class QizxDriver
{
    private static final int MB = 1048576;
    
    // key used to attach this object to a web app context:
    static final String WAPP_KEY = "Qizx_driver";
    // Location of the configuration .properties file inside server root
    private static final String QIZX_ROOT_PARAMETER = "qizx-server-root";
    // Location of the configuration .properties file inside server root
    private static final String QIZX_CONFIG = "qizx-server.conf";

    // Config: application name
    static final String CF_SERVER_NAME = "server_name";
    
    // Config: limit on size of POST request (specially put request)
    static final String CF_POST_LIMIT = "post_limit";

    // Config: required user role/name for administration operations. 
    // No security if null.
    static final String CF_ADMIN_ROLE = "admin_role";
    static final String CF_ADMIN_USER = "admin_user";
    
    // Config: absolute path to the directory of a Qizx Library Group.
    static final String CF_LIBRARY_GROUP = "library_group";

    // Config: memory size in Mb used for the Library Group.
    static final String CF_LIBRARY_MEMORY = "xlib_memory";

    // Config: memory size in Mb used for the document cache.
    static final String CF_DOC_POOL_MEMORY = "doc_pool_memory";

   // Config: Access Control class used (no AC if not defined). 
    // Default is ACLAccessControl.
    static final String CF_ACCESS_CONTROL = "access_control";

    // Config: FulltextFactory implementation
    static final String CF_FULLTEXT_FACTORY = "fulltext_factory";
    
    // Config: XML catalog properties
    static final String CF_CATALOGS_PREFER = "catalogs_prefer";
    static final String CF_CATALOGS_VERBOSITY = "catalogs_verbosity";
    static final String CF_CATALOGS = "catalogs";

    // Config: root directory for XQuery modules
    static final String CF_MODULE_DIR = "module_dir";

    // Config: root directory for XQuery Services (stored queries)
    static final String CF_SERVICES_DIR = "services_dir";
    // Config: default XML Library used by XQuery services
    //   Note: need a mechanism for finer specification (per package)
    static final String CF_SERVICES_LIB = "services_library";

    // Config: 
    static final String CF_JAVA_CLASSES = "allowed_java_classes";
    // Config: 
    static final String CF_EVAL_TIME_OUT = "eval_time_out";


    // -----------------------------------------------------------------------
    
    private ServletContext context;

    // root directory of a server
    private File serverRootDir;

    // Qizx configuration loaded from specific .properties file:
    private Properties configuration;
    
    private MultipartConfig multipartConfig;
    private long multipartMaxSize = -1;

    // abs path of the Qizx Library group:
    private File libGroupDir;
    // Qizx engine:
    private LibraryManagerFactory lmFactory;
    private volatile LibraryManager libManager;
    
    // list of XML Library names (to resolve void name)
    private volatile String[] libNames;
    private HashMap<String,AccessControl> acMap;
    
    // Long actions (backup etc) in progress:
    protected ArrayList<LongAction> actions = new ArrayList<LongAction>();

    private String adminRoleName;

    private CatalogManager catManager;

    private String[] allowedClasses;    // Java binding
    public int evalTimeout;
    
    private File servicesRoot;
    private String servicesDefaultLibrary;

    private String[] adminUsers;

    

    public QizxDriver(ServletContext webApp, File serverRootPath)
    {
        this.context = webApp;
        this.serverRootDir = serverRootPath;
        multipartConfig = new MultipartConfig(-1, multipartMaxSize, 200000, null);
    }

    /**
     * Ensures that a LibraryAccess is attached to the Web App.
     */
    public static QizxDriver initialize(ServletBase servlet)
    {
        ServletContext webApp = servlet.getServletContext();
        
        synchronized (webApp)
        {
            QizxDriver driver = (QizxDriver) webApp.getAttribute(WAPP_KEY);
            if(driver != null)
                return driver;
            
            ServletConfig config = servlet.getServletConfig();

            // Configuration file:
            String configLoc = config.getInitParameter(QIZX_ROOT_PARAMETER);
            if(configLoc == null || configLoc.length() == 0) {
                webApp.log("FATAL: location of server root not defined, " +
                           "servlet init parameter " + QIZX_ROOT_PARAMETER);
                return null;
            }
            File rootDir = new File(configLoc);
            try {
                if(!rootDir.isDirectory()) {
                    webApp.log("FATAL: server root is not a directory: " + rootDir);
                    return null;
                }
                if(!rootDir.canRead() || !rootDir.canWrite()) {
                    webApp.log("FATAL: insufficient access rights on server root directory: " + rootDir);
                    return null;
                }
            }
            catch(java.security.AccessControlException e) {
                webApp.log("FATAL: " + e + " while initializing Qizx configuration");
                webApp.log("   *** This can be due to the security policy of the servlet container.");
                return null;
            }

            driver = new QizxDriver(webApp, rootDir);
            driver.loadConfiguration();
            
            servlet.setMultipartConfig(driver.multipartConfig);
            
            // start service:
            try {
                driver.start();
            }
            catch (QizxException e) {
                webApp.log("ERROR: XML Library init error", e);
                return null;
            }
        
            webApp.setAttribute(WAPP_KEY, driver);
            return driver;
        }
    }

    // On servlet destroy:
    public static void terminate(ServletContext webApp)
    {
        synchronized (webApp)
        {
            QizxDriver driver = (QizxDriver) webApp.getAttribute(WAPP_KEY);
            if(driver == null)
                return;
            webApp.setAttribute(WAPP_KEY, null);
            try {
                driver.stop();
            }
            catch (DataModelException e) {
                webApp.log("ERROR closing Qizx driver " + e);
            }
        }
    }
    
    // restart after config reload
    public void reload(ServletBase servlet)
        throws QizxException
    {
        //terminate(context);
        stop();
        
        loadConfiguration();
        
        try {
            start();
        }
        catch (QizxException e) {
            context.log("ERROR: XML Library init error", e);
            return;
        }
    
        context.setAttribute(WAPP_KEY, this);
    }
    
    // start Qizx engine:
    public synchronized void start()
        throws QizxException
    {
        if(libManager != null)
            return;
        
        context.log("starting Qizx engine on group " + libGroupDir);
        libManager = lmFactory.openLibraryGroup(libGroupDir);
        DocumentPool docPool = libManager.getTransientDocumentCache();
       
        // Qizx engine configuration
        try {
            String acClass = getProperty(CF_ACCESS_CONTROL, null);
            if(acClass != null) {
                context.log(" AC: " + acClass);
                acMap = new HashMap<String, AccessControl>();
                String[] lnames = libManager.listLibraries();
                for(String name : lnames) {
                    AccessControl acctrl = (AccessControl)
                                 instantiateClass(acClass, AccessControl.class);
                    acMap.put(name, acctrl);
                    if(acctrl instanceof ACLAccessControl) {
                        // loads ACL from library itself:
                        ((ACLAccessControl) acctrl).connectTo(libManager, name);
                    }
                }
            }
        }
        catch (Exception e) {
            context.log("ERROR in AccessControl instantiation: " + e 
                        + ", parameter " + CF_ACCESS_CONTROL, e);
        }
        
        try {
            FullTextFactory ftf = (FullTextFactory) instantiateClass(
                                  configuration.getProperty(CF_FULLTEXT_FACTORY),
                                  FullTextFactory.class);
            if(ftf != null) {
                context.log(" FT factory: " + ftf.getClass().getCanonicalName());
                libManager.setFullTextFactory(ftf);
            }
        }
        catch (Exception e) {
            context.log("ERROR in FullTextFactory instantiation: " + e 
                        + ", parameter " + CF_FULLTEXT_FACTORY, e);
        }
        
        initCatalogs();
        
        long memSize = getIntProperty(CF_LIBRARY_MEMORY, -1);
        if(memSize > 0 && memSize < 32) // unit = Mb
            memSize = 32;
        if (memSize > 1048576)
            memSize /= 1048576; // wrongly given in bytes
        libManager.setMemoryLimit(memSize * 1048576);
        

        if(configuration.getProperty(CF_DOC_POOL_MEMORY) != null) {
            long poolSize = getIntProperty(CF_DOC_POOL_MEMORY, -1);
            if(poolSize < 1) // unit = Mb
                poolSize = 1;
            if (poolSize > 1048576)
                poolSize /= 1048576; // wrongly given in bytes
           docPool.setCacheSize(poolSize * 1048576);
        }
        docPool.setLocalCatalogManager(catManager);
        
        String javaClasses = configuration.getProperty(CF_JAVA_CLASSES);
        if(javaClasses != null) {
            allowedClasses = javaClasses.split("[ ,;]+");
        }
        
        evalTimeout = (int) getIntProperty(CF_EVAL_TIME_OUT, -1);
        
        // modules
        File modules = getFileProperty(CF_MODULE_DIR);
        if(modules != null) {
            if(!modules.isDirectory()) {
                context.log("ERROR: module_dir is not a directory: " + modules);
            }
            else {
                DefaultModuleResolver mr = new DefaultModuleResolver(FileUtil.fileToURL(modules));
                libManager.setModuleResolver(mr);
            }
        }
        
        servicesRoot = getFileProperty(CF_SERVICES_DIR);
        servicesDefaultLibrary = getProperty(CF_SERVICES_LIB, null);
        
        context.log("Qizx server started");
        
        changedLibraryList();
    }
    
    public synchronized void stop()
        throws DataModelException
    {
        if(libManager == null)
            return;
        context.log("stopping Qizx engine... ");
        boolean graceful = libManager.closeAllLibraries(0);
        changedLibraryList();
        libManager = null;
        context.log("Qizx engine stopped " + (graceful? "gracefully" : "with rollbacks"));
    }

    public synchronized boolean isRunning()
    {
        return libManager != null;
    }

    public void changedLibraryList()
    {
        libNames = null;
    }
    
    // returns a non-null library name iff there is one library exactly.
    private String singleLibName()
    {
        if(libNames == null) {
            synchronized (libManager) {
                try {
                    libNames = libManager.listLibraries();
                }
                catch (DataModelException e) {
                    context.log("error getting library names", e);
                }
            }
        }
        return (libNames != null && libNames.length == 1)? libNames[0] : null;
    }

    /*
     * Loads the configuration from the properties file located
     * at the root of the server,
     */
    private synchronized boolean loadConfiguration()
    {
        File configLoc = new File(serverRootDir, QIZX_CONFIG);
        try {
            configuration = FileUtil.loadProperties(configLoc);
        }
        catch (IOException e1) {
            context.log("FATAL: cannot load configuration at " + configLoc + ": " + e1);
            return false;
        }
        
        adminRoleName = configuration.getProperty(QizxDriver.CF_ADMIN_ROLE);
        String admins = configuration.getProperty(QizxDriver.CF_ADMIN_USER);
        if(admins != null) {
            adminUsers = admins.split("[ \t;,]+");
        }
        
        if(configuration.getProperty(CF_POST_LIMIT) != null) {
            long limit = Math.max(1, getIntProperty(CF_POST_LIMIT, -1));
            multipartConfig = new MultipartConfig(-1, limit * MB, 200000, null);
        }
    
        // where is the library group (normally same directory as the config)
        libGroupDir = getFileProperty(CF_LIBRARY_GROUP);
        if(libGroupDir == null) {
            context.log("WARNING: no property " + CF_LIBRARY_GROUP + " in Qizx configuration");
            return false;     // no lib access
        }
        
        lmFactory = LibraryManagerFactory.getInstance();
        
        // rest of config is read in start()
        
        return true;
    }

    private File getFileProperty(String name)
    {
        String path = configuration.getProperty(name);
        if(path == null)
            return null;
        // resolved relatively to server root:
        return new File(serverRootDir, path);
    }

    private String getProperty(String name, String defaultValue)
    {
        String value = configuration.getProperty(name);
        if(value == null)
            return defaultValue;
        value = value.trim();
        return (value.length() == 0)? null : value;
    }
    
    private long getIntProperty(String name, int defaultValue)
    {
        String value = configuration.getProperty(name);
        if(value == null)
            return defaultValue;
        value = value.trim();
        if(value.length() > 0)
            try {
                return Long.parseLong(value.trim());
            }
            catch (NumberFormatException e) {
                context.log("WARNING: parameter " + name
                            + " has invalid value '" + value + "'");
            }
        return defaultValue;
    }

    private Object instantiateClass(String className, Class type)
        throws Exception
    {
        if (className == null | className.length() == 0)
            return null;
        Object obj = java.beans.Beans.instantiate(getClass().getClassLoader(),
                                                  className);
        if(type != null && !type.isAssignableFrom(obj.getClass()))
            context.log("ERROR: class " + className + " is not a " + type);
        return obj;
    }

    public String getName()
    {
        return configuration.getProperty(CF_SERVER_NAME);
    }

    public LibraryManager getEngine()
    {
        return libManager;
    }

    public LibraryManager requireEngine()
        throws RequestException
    {
        if(libManager == null)
            throw new RequestException(Request.SERVER, "Qizx server is offline");
        return libManager;
    }

    /**
     * Gets a session.<p>
     * If sessions are pooled, look in the pool, otherwise simply create new session
     * @param qizxRequestBase 
     */
    public synchronized Library acquireSession (String libraryName,
                                                String userName,
                                                QizxRequestBase request)
        throws RequestException, DataModelException
    {
        requireEngine();
        if(libraryName == null || libraryName.length() == 0)
            libraryName = singleLibName();
        if(libraryName == null)
            throw new RequestException(Request.BAD_REQUEST,
                                       "unspecified XML Library name");
        User user = null;
        AccessControl acctrl = getAccessControl(libraryName);
        if(acctrl != null)
            user = new ServerUser(userName, request);
        
        Library lib = libManager.openLibrary(libraryName, acctrl, user);
        if(lib == null)
            throw new RequestException(Request.BAD_REQUEST,
                                       "no XML Library named '" + libraryName +"'");
        // init XQuery context:
        if (allowedClasses != null) {
            for(String cl : allowedClasses) {
                lib.enableJavaBinding(cl);
            }
        }
        return lib;
    }
    
    /**
     * Releases a session.<p>
     * If sessions are pooled, release it to the pool, otherwise simply 
     * close the session.
     */
    public synchronized void releaseSession(Library session)
    {
        // no pooling for now
        try {
            session.close();
        }
        catch (DataModelException e) { 
            // close can throw an exception if modifications occurred:
            // wants a rollback before closing
            try {
                session.rollback();
            }
            catch (DataModelException e1) {
                context.log("session close", e1);
            }
        }
    }
    
    private void initCatalogs()
    {
        catManager = new CatalogManager();
        catManager.setIgnoreMissingProperties(true);
        catManager.setUseStaticCatalog(false);
        String catalogs = configuration.getProperty(CF_CATALOGS);
        catManager.setCatalogFiles(catalogs);
        
        int catVerbosity = (int) getIntProperty(CF_CATALOGS_VERBOSITY, 0);
        catManager.setVerbosity(catVerbosity);
        
        String prefer = configuration.getProperty(CF_CATALOGS_PREFER);
        if(prefer != null)
            catManager.setPreferPublic("public".equalsIgnoreCase(prefer));
    }

    private AccessControl getAccessControl(String libraryName)
    {
        return (acMap == null)? null : acMap.get(libraryName);
    }

    public File getServicesRoot()
    {
        return servicesRoot;
    }

    public String getServicesDefaultLibrary()
    {
        return servicesDefaultLibrary;
    }

    public Expression getStoredQuery(String uri, Library session)
        throws IOException, CompilationException
    {
       /* TODO caching mechanism: For speed, it is important to use 
        * session-pooling + caching of pairs (session, expression). 
        * For simple but large templates, compilation is likely to be slower
        * than execution itself. */

        File location = new File(servicesRoot, uri);
        if(!location.exists() || !location.isFile())
            return null;
        String query = FileUtil.loadString(location);
        
        Expression ex = session.compileExpression(query);

        return ex;
    }
    
    // TODO could be cached and checked for update
    public Map<String,Expression> listStoredQueries(String uri, Library session)
    {
        File location = new File(servicesRoot, uri);
        if(!location.exists() || !location.isDirectory())
            return null;
        TreeMap<String,Expression> list = new TreeMap<String,Expression>();
        File[] scripts = location.listFiles();
        for(File f : scripts) {
            try {
                String query = FileUtil.loadString(f);
                list.put(f.getName(), session.compileExpression(query));
            }
            catch (Exception e) {
                ; // ignored
            }
        }
        return list;
    }

    public synchronized Templates getStoredXsltScript(String uri, User user)
    {
        return null;
    }
    
    public boolean isAdminUser(String userName)
    {
        if(adminUsers == null)
            return adminRoleName == null;   // both null => no control
        for(String aduser : adminUsers) {
            if(aduser.equals(userName))
                return true;
        }
        return false;
    }

    public String getAdminRoleName()
    {
        return adminRoleName;
    }

    static class ServerUser extends BaseUser implements ACLAccessControl.User
    {
        private HttpServletRequest request;

        public ServerUser(String name, Request req)
        {
            super(name);
            this.request = req.request;
        }

        public boolean isInRole(String roleName)
        {
            return request.isUserInRole(roleName);
        }
    }

    // -------------------- progress on long actions ------------------------
    
    public abstract class LongAction
        implements Runnable, LibraryProgressObserver
    {
        protected Library library;
        protected String id;
        protected String description;
        protected long startTime;
        protected long endTime;
        private double fractionDone;
        private Throwable error;
        private DecimalFormat fformat = 
            new DecimalFormat("0.000", new DecimalFormatSymbols(Locale.US)); // FIX

        public LongAction(Library lib, String description)
        {
            library = lib;
            this.description = description;
            if(lib != null)
                lib.setProgressObserver(this);
            addAction(this);            
        }

        public String getId()
        {
            return id;
        }

        public void start()
        {
            context.log("starting long action " + id +" ("+ description +")");
            // as long as this is used for rare operations (reindex, backup)
            // this is OK to create a thread each time:
            new Thread(this).start();
        }

        public String getProgress()
        {
            if (error == null)
                return description + "\n" + fformat.format(fractionDone) + "\n";
            
            StringWriter sw = new StringWriter();
            PrintWriter out = new PrintWriter(sw);
            error.printStackTrace(out);
            return description + "\nerror " + error + "\n" + sw.toString() + "\n";
        }

        protected void finishedAction()
        {
            context.log("finishing long action " + id +" ("+ description +")");
            fractionDone = 1;
            endTime = System.currentTimeMillis();
            if(library != null)
                releaseSession(library);
        }

        protected void abortedAction(Throwable e)
        {
            context.log("error in long action " + id +" ("+ description +")", e);
            fractionDone = 1;
            error = e;
            endTime = System.currentTimeMillis();
            if(library != null)
                releaseSession(library);
        }

        // creates a root directory containing all Libs
        public void backupAllLibraries(File location, BackupRequest request)
            throws RequestException, DataModelException
        {
            requireEngine();
            if(!location.isDirectory() && !location.mkdirs())
                throw new DataModelException("cannot create backup root " + location);
        
            // snapshot of all Libs:
            String[] libNames = libManager.listLibraries();
            Library[] libs = new Library[libNames.length];
            for (int i = 0; i < libs.length; i++) {
                libs[i] = libManager.openLibrary(libNames[i]);
            }
            
            // do backup:
            for (int i = 0; i < libs.length; i++) {
                String name = libNames[i];
                description = "backup " + name;
                libs[i].setProgressObserver(this);
                libs[i].backup(new File(location, name));
                libs[i].close();
            }
            
        }

        public void optimizationProgress(double fraction)
        {
            fractionDone = fraction;
        }

        public void reindexingProgress(double fraction)
        {
            fractionDone = fraction;
        }

        public void backupProgress(double fraction)
        {
            fractionDone = fraction;
        }

        // not yet used:
        
        public void importProgress(double size) { }

        public void commitProgress(double fraction) { }
    }

    public synchronized LongAction findAction(String id)
    {
        for(int a = actions.size(); --a >= 0; ) {
            LongAction act = (LongAction) actions.get(a);
            if(act.id.equals(id))
                return act;
        }
        return null;
    }

    public synchronized void addAction(LongAction action)
    {
        long now = System.currentTimeMillis();
        // cleanup very old finished actions
        for(int a = actions.size(); --a >= 0; ) {
            LongAction old = (LongAction) actions.get(a);
            // finished more than 10 minutes ago?
            if(old.endTime > 0 && old.endTime < now - 600000)
                actions.remove(a);
        }
        action.startTime = now;
        action.id = "A" + action.hashCode();
        actions.add(action);
    }
}
