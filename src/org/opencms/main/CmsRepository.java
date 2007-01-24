/*
 * File   : $Source: /alkacon/cvs/opencms/src/org/opencms/main/Attic/CmsRepository.java,v $
 * Date   : $Date: 2007/01/24 10:04:26 $
 * Version: $Revision: 1.1.2.1 $
 *
 * This library is part of OpenCms -
 * the Open Source Content Mananagement System
 *
 * Copyright (C) 2006 Alkacon Software GmbH (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software GmbH, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.main;

import org.opencms.file.CmsObject;
import org.opencms.repository.CmsRepositoryAuthorizationException;
import org.opencms.repository.I_CmsRepository;
import org.opencms.repository.I_CmsRepositorySession;

import javax.servlet.http.HttpServletRequest;

/**
 *
 */
public class CmsRepository implements I_CmsRepository {

    /**
     * 
     */
    public CmsRepository() {

        // TODO Auto-generated constructor stub
    }

    /**
     * @see org.opencms.repository.I_CmsRepository#login(javax.servlet.http.HttpServletRequest, java.lang.String, java.lang.String)
     */
    public I_CmsRepositorySession login(HttpServletRequest request, String siteName, String projectName)
    throws CmsRepositoryAuthorizationException {

        CmsObject cms;
        try {
            cms = OpenCmsCore.getInstance().initCmsObject(request, null);
        } catch (Exception e) {
            throw new CmsRepositoryAuthorizationException(e.getLocalizedMessage(), e);
        }
        return new CmsRepositorySession(cms);
    }

    /**
     * @see org.opencms.repository.I_CmsRepository#login(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public I_CmsRepositorySession login(String userName, String password, String siteName, String projectName)
    throws CmsRepositoryAuthorizationException {

        CmsObject cms;
        try {
            cms = OpenCms.initCmsObject(OpenCms.getDefaultUsers().getUserGuest());
            cms.loginUser(userName, password);
            if (siteName != null) {
                cms.getRequestContext().setSiteRoot(siteName);
            }
            if (projectName != null) {
                cms.getRequestContext().setCurrentProject(cms.readProject(projectName));
            }
        } catch (CmsException e) {
            throw new CmsRepositoryAuthorizationException(e.getLocalizedMessage());
        }
        return new CmsRepositorySession(cms);
    }
}
