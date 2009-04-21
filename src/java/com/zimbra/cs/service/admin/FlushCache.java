/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2007, 2008, 2009 Zimbra, Inc.
 * 
 * The contents of this file are subject to the Yahoo! Public License
 * Version 1.0 ("License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 * http://www.zimbra.com/license.
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.service.admin;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.soap.SoapHttpTransport;
import com.zimbra.common.soap.Element.XMLElement;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;
import com.zimbra.cs.account.Provisioning.CacheEntry;
import com.zimbra.cs.account.Provisioning.CacheEntryBy;
import com.zimbra.cs.account.accesscontrol.AdminRight;
import com.zimbra.cs.account.accesscontrol.Rights.Admin;
import com.zimbra.cs.httpclient.URLUtil;
import com.zimbra.common.util.L10nUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.servlet.ZimbraServlet;
import com.zimbra.cs.util.SkinUtil;
import com.zimbra.soap.SoapServlet;
import com.zimbra.soap.ZimbraSoapContext;

public class FlushCache extends AdminDocumentHandler {

	public static final String FLUSH_CACHE = "flushCache";
    
    /**
     * must be careful and only allow deletes domain admin has access to
     */
    public boolean domainAuthSufficient(Map context) {
        return true;
    }
    
    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        
        Server localServer = Provisioning.getInstance().getLocalServer();
        checkRight(zsc, context, localServer, Admin.R_flushCache);
        
        Element eCache = request.getElement(AdminConstants.E_CACHE);
        String type = eCache.getAttribute(AdminConstants.A_TYPE);
        boolean nonEntry = false; 
	    if (type.contains("zimlet")) {
	        FlushCache.sendFlushRequest(context, "/service", "/zimlet/res/all.js");
	    }
        if (type.contains("skin")) {
        	nonEntry = true;
            SkinUtil.flushSkinCache();
            FlushCache.sendFlushRequest(context, "/zimbra", "/js/skin.js");
        }
        if (type.contains("locale")) {
        	nonEntry = true;
            L10nUtil.flushLocaleCache();
        }
        
        if(!nonEntry) {
            List<Element> eEntries = eCache.listElements(AdminConstants.E_ENTRY);
            CacheEntry[] entries = null;
            if (eEntries.size() > 0) {
                entries = new CacheEntry[eEntries.size()];
                int i = 0;
                for (Element eEntry : eEntries) {
                    entries[i++] = new CacheEntry(CacheEntryBy.valueOf(eEntry.getAttribute(AdminConstants.A_BY)),
                                                  eEntry.getText());
                }
            }
            Provisioning.getInstance().flushCache(type, entries);
        }

        Element response = zsc.createElement(AdminConstants.FLUSH_CACHE_RESPONSE);
        return response;
    }
    
	static void sendFlushRequest(Map<String,Object> context,
	                             String appContext, String resourceUri) throws ServiceException {
		ServletContext containerContext = (ServletContext)context.get(SoapServlet.SERVLET_CONTEXT);
		if (containerContext == null) {
			if (ZimbraLog.misc.isDebugEnabled()) {
				ZimbraLog.misc.debug("flushCache: no container context");
			}
			return;
		}
		ServletContext webappContext = containerContext.getContext(appContext);
		RequestDispatcher dispatcher = webappContext.getRequestDispatcher(resourceUri);
		if (dispatcher == null) {
			if (ZimbraLog.misc.isDebugEnabled()) {
				ZimbraLog.misc.debug("flushCache: no dispatcher for "+resourceUri);
			}
			return;
		}

		try {
			if (ZimbraLog.misc.isDebugEnabled()) {
				ZimbraLog.misc.debug("flushCache: sending flush request");
			}
			ServletRequest request = (ServletRequest)context.get(SoapServlet.SERVLET_REQUEST);
			request.setAttribute(FLUSH_CACHE, Boolean.TRUE);
			ServletResponse response = (ServletResponse)context.get(SoapServlet.SERVLET_RESPONSE);
			dispatcher.include(request, response);
		}
		catch (Throwable t) {
			// ignore error
			if (ZimbraLog.misc.isDebugEnabled()) {
				ZimbraLog.misc.debug("flushCache: "+t.getMessage());
			}
		}
	}

    @Override
    public void docRights(List<AdminRight> relatedRights, List<String> notes) {
        relatedRights.add(Admin.R_flushCache);
    }
    
    public static void flushChacheOnServer(Server server,ZimbraSoapContext zsc,String cacheType) throws ServiceException, IOException {
		String adminUrl = URLUtil.getAdminURL(server, ZimbraServlet.ADMIN_SERVICE_URI);
		SoapHttpTransport mTransport = new SoapHttpTransport(adminUrl);
		mTransport.setAuthToken(zsc.getRawAuthToken());
		XMLElement req = new XMLElement(AdminConstants.FLUSH_CACHE_REQUEST);
		req.addElement(AdminConstants.E_CACHE).addAttribute(AdminConstants.A_TYPE, cacheType);
		mTransport.invoke(req);		
    }
}