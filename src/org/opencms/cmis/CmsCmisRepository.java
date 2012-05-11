/*
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (C) Alkacon Software (http://www.alkacon.com)
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
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.cmis;

import static org.opencms.cmis.CmsCmisUtil.checkResourceName;
import static org.opencms.cmis.CmsCmisUtil.ensureLock;
import static org.opencms.cmis.CmsCmisUtil.handleCmsException;
import static org.opencms.cmis.CmsCmisUtil.splitFilter;

import org.opencms.configuration.CmsConfigurationException;
import org.opencms.configuration.CmsParameterConfiguration;
import org.opencms.file.CmsFile;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsProject;
import org.opencms.file.CmsProperty;
import org.opencms.file.CmsResource;
import org.opencms.file.CmsResourceFilter;
import org.opencms.file.CmsVfsResourceAlreadyExistsException;
import org.opencms.file.types.CmsResourceTypeFolder;
import org.opencms.file.types.I_CmsResourceType;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.relations.CmsRelation;
import org.opencms.relations.CmsRelationFilter;
import org.opencms.repository.CmsRepositoryFilter;
import org.opencms.repository.I_CmsRepository;
import org.opencms.util.CmsFileUtil;
import org.opencms.util.CmsStringUtil;
import org.opencms.util.CmsUUID;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.Acl;
import org.apache.chemistry.opencmis.commons.data.AllowableActions;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.data.FailedToDeleteData;
import org.apache.chemistry.opencmis.commons.data.ObjectData;
import org.apache.chemistry.opencmis.commons.data.ObjectInFolderContainer;
import org.apache.chemistry.opencmis.commons.data.ObjectInFolderData;
import org.apache.chemistry.opencmis.commons.data.ObjectInFolderList;
import org.apache.chemistry.opencmis.commons.data.ObjectList;
import org.apache.chemistry.opencmis.commons.data.ObjectParentData;
import org.apache.chemistry.opencmis.commons.data.PermissionMapping;
import org.apache.chemistry.opencmis.commons.data.Properties;
import org.apache.chemistry.opencmis.commons.data.PropertyData;
import org.apache.chemistry.opencmis.commons.data.RenditionData;
import org.apache.chemistry.opencmis.commons.data.RepositoryInfo;
import org.apache.chemistry.opencmis.commons.definitions.PermissionDefinition;
import org.apache.chemistry.opencmis.commons.definitions.TypeDefinition;
import org.apache.chemistry.opencmis.commons.definitions.TypeDefinitionContainer;
import org.apache.chemistry.opencmis.commons.definitions.TypeDefinitionList;
import org.apache.chemistry.opencmis.commons.enums.AclPropagation;
import org.apache.chemistry.opencmis.commons.enums.CapabilityAcl;
import org.apache.chemistry.opencmis.commons.enums.CapabilityChanges;
import org.apache.chemistry.opencmis.commons.enums.CapabilityContentStreamUpdates;
import org.apache.chemistry.opencmis.commons.enums.CapabilityJoin;
import org.apache.chemistry.opencmis.commons.enums.CapabilityQuery;
import org.apache.chemistry.opencmis.commons.enums.CapabilityRenditions;
import org.apache.chemistry.opencmis.commons.enums.IncludeRelationships;
import org.apache.chemistry.opencmis.commons.enums.RelationshipDirection;
import org.apache.chemistry.opencmis.commons.enums.SupportedPermissions;
import org.apache.chemistry.opencmis.commons.enums.UnfileObject;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.apache.chemistry.opencmis.commons.exceptions.CmisConstraintException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisContentAlreadyExistsException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisInvalidArgumentException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisNameConstraintViolationException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisNotSupportedException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisPermissionDeniedException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisRuntimeException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisStreamNotSupportedException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.AclCapabilitiesDataImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ContentStreamImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.FailedToDeleteDataImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ObjectInFolderContainerImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ObjectInFolderDataImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ObjectInFolderListImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ObjectListImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ObjectParentDataImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PermissionDefinitionDataImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PermissionMappingDataImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.RenditionDataImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.RepositoryCapabilitiesImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.RepositoryInfoImpl;
import org.apache.chemistry.opencmis.commons.server.CallContext;
import org.apache.chemistry.opencmis.commons.server.ObjectInfoHandler;
import org.apache.chemistry.opencmis.commons.spi.Holder;
import org.apache.commons.logging.Log;

/**
 * Repository instance for CMIS repositories.<p>
 */
public class CmsCmisRepository extends A_CmsCmisRepository implements I_CmsRepository {

    /**
     * Simple helper class to simplify creating a permission mapping.<p>
     */
    @SuppressWarnings("serial")
    private static class PermissionMappings extends HashMap<String, PermissionMapping> {

        /** Default constructor.<p> */
        public PermissionMappings() {

        }

        /**
         * Creates a single mapping entry.<p>
         * 
         * @param key the mapping key 
         * @param permission the permission 
         * 
         * @return the mapping entry 
         */
        private static PermissionMapping createMapping(String key, String permission) {

            PermissionMappingDataImpl pm = new PermissionMappingDataImpl();
            pm.setKey(key);
            pm.setPermissions(Collections.singletonList(permission));

            return pm;
        }

        /**
         * Adds a permission mapping.<p>
         * 
         * @param key the key 
         * @param permission the permissions
         *  
         * @return the instance itself  
         */
        public PermissionMappings add(String key, String permission) {

            put(key, createMapping(key, permission));
            return this;
        }

    }

    /** The logger instance for this class. */
    protected static final Log LOG = CmsLog.getLog(CmsCmisRepository.class);

    /** The internal admin CMS context. */
    private CmsObject m_adminCms;

    /** The repository description. */
    private String m_description;

    /** The repository filter. */
    private CmsRepositoryFilter m_filter;

    /** The repository id. */
    private String m_id;

    /**
     * Readonly flag to prevent write operations on the repository.<p>
     */
    private boolean m_isReadOnly;

    /** The parameter configuration map. */
    private CmsParameterConfiguration m_parameterConfiguration = new CmsParameterConfiguration();

    /** The project of the repository. */
    private CmsProject m_project;

    /** The root folder. */
    private CmsResource m_root;

    /**
     * Creates a permission definition.<p>
     * 
     * @param permission the permission name 
     * @param description the permission description 
     * 
     * @return the new permission definition 
     */
    private static PermissionDefinition createPermission(String permission, String description) {

        PermissionDefinitionDataImpl pd = new PermissionDefinitionDataImpl();
        pd.setPermission(permission);
        pd.setDescription(description);

        return pd;
    }

    /**
     * @see org.opencms.configuration.I_CmsConfigurationParameterHandler#addConfigurationParameter(java.lang.String, java.lang.String)
     */
    public void addConfigurationParameter(String paramName, String paramValue) {

        m_parameterConfiguration.add(paramName, paramValue);

    }

    /**
     * Creates a new document.<p>
     *  
     * @param context the call context 
     * @param propertiesObj the properties 
     * @param folderId the parent folder id 
     * @param contentStream the content stream 
     * @param versioningState the versioning state 
     * @param policies the policies 
     * @param addAces the access control entries 
     * @param removeAces the access control entries to remove
     *  
     * @return the object id of the new document
     */
    public synchronized String createDocument(
        CallContext context,
        Properties propertiesObj,
        String folderId,
        ContentStream contentStream,
        VersioningState versioningState,
        List<String> policies,
        Acl addAces,
        Acl removeAces) {

        checkWriteAccess();

        if ((addAces != null) || (removeAces != null)) {
            throw new CmisConstraintException("createDocument: ACEs not allowed");
        }

        if (contentStream == null) {
            throw new CmisConstraintException("createDocument: no content stream given");
        }

        try {
            CmsObject cms = getCmsObject(context);
            Map<String, PropertyData<?>> properties = propertiesObj.getProperties();
            String newDocName = (String)properties.get(PropertyIds.NAME).getFirstValue();
            String defaultType = OpenCms.getResourceManager().getDefaultTypeForName(newDocName).getTypeName();
            String resTypeName = getResourceTypeFromProperties(properties, defaultType);
            I_CmsResourceType cmsResourceType = OpenCms.getResourceManager().getResourceType(resTypeName);
            if (cmsResourceType.isFolder()) {
                throw new CmisConstraintException("Not a document type: " + resTypeName);
            }
            List<CmsProperty> cmsProperties = getOpenCmsProperties(properties);
            checkResourceName(newDocName);
            InputStream stream = contentStream.getStream();
            byte[] content = CmsFileUtil.readFully(stream);
            CmsUUID parentFolderId = new CmsUUID(folderId);
            CmsResource parentFolder = cms.readResource(parentFolderId);
            String newFolderPath = CmsStringUtil.joinPaths(parentFolder.getRootPath(), newDocName);
            try {
                CmsResource newDocument = cms.createResource(
                    newFolderPath,
                    cmsResourceType.getTypeId(),
                    content,
                    cmsProperties);
                cms.unlockResource(newDocument.getRootPath());
                return newDocument.getStructureId().toString();
            } catch (CmsVfsResourceAlreadyExistsException e) {
                throw new CmisNameConstraintViolationException(e.getLocalizedMessage(), e);
            }
        } catch (CmsException e) {
            handleCmsException(e);
            return null;
        } catch (IOException e) {
            throw new CmisRuntimeException(e.getLocalizedMessage(), e);
        }
    }

    /**
     * Copies a document.<p>
     * 
     * @param context the call context 
     * @param sourceId the source object id 
     * @param propertiesObj the properties 
     * @param folderId the target folder id 
     * @param versioningState the versioning state 
     * @param policies the policies 
     * @param addAces the ACEs to add 
     * @param removeAces the ACES to remove 
     * 
     * @return the object id of the new document 
     */
    public synchronized String createDocumentFromSource(
        CallContext context,
        String sourceId,
        Properties propertiesObj,
        String folderId,
        VersioningState versioningState,
        List<String> policies,
        Acl addAces,
        Acl removeAces) {

        checkWriteAccess();

        if ((addAces != null) || (removeAces != null)) {
            throw new CmisConstraintException("createDocument: ACEs not allowed");
        }

        try {
            CmsObject cms = getCmsObject(context);
            Map<String, PropertyData<?>> properties = new HashMap<String, PropertyData<?>>();
            if (propertiesObj != null) {
                properties = propertiesObj.getProperties();
            }
            List<CmsProperty> cmsProperties = getOpenCmsProperties(properties);
            CmsUUID parentFolderId = new CmsUUID(folderId);
            CmsResource parentFolder = cms.readResource(parentFolderId);
            CmsUUID sourceUuid = new CmsUUID(sourceId);
            CmsResource source = cms.readResource(sourceUuid);
            String sourcePath = source.getRootPath();

            PropertyData<?> nameProp = properties.get(PropertyIds.NAME);
            String newDocName;
            if (nameProp != null) {
                newDocName = (String)nameProp.getFirstValue();
                checkResourceName(newDocName);
            } else {
                newDocName = CmsResource.getName(source.getRootPath());
            }
            String targetPath = CmsStringUtil.joinPaths(parentFolder.getRootPath(), newDocName);

            try {
                cms.copyResource(sourcePath, targetPath);
            } catch (CmsVfsResourceAlreadyExistsException e) {
                throw new CmisNameConstraintViolationException(e.getLocalizedMessage(), e);
            }

            CmsResource targetResource = cms.readResource(targetPath);
            cms.setDateLastModified(targetResource.getRootPath(), targetResource.getDateCreated(), false);
            cms.unlockResource(targetResource);
            boolean wasLocked = ensureLock(cms, targetResource);
            cms.writePropertyObjects(targetResource, cmsProperties);
            if (wasLocked) {
                cms.unlockResource(targetResource);
            }
            return targetResource.getStructureId().toString();
        } catch (CmsException e) {
            handleCmsException(e);
            return null;
        }
    }

    /**
     * Creates a new folder.<p>
     *  
     * @param context the call context 
     * @param propertiesObj the properties 
     * @param folderId the parent folder id
     * @param policies the policies 
     * @param addAces the ACEs to add 
     * @param removeAces the ACEs to remove 
     * 
     * @return the object id of the created folder 
     */
    public synchronized String createFolder(
        CallContext context,
        Properties propertiesObj,
        String folderId,
        List<String> policies,
        Acl addAces,
        Acl removeAces) {

        checkWriteAccess();

        if ((addAces != null) || (removeAces != null)) {
            throw new CmisConstraintException("createFolder: ACEs not allowed");
        }

        try {
            CmsObject cms = getCmsObject(context);
            Map<String, PropertyData<?>> properties = propertiesObj.getProperties();
            String resTypeName = getResourceTypeFromProperties(properties, CmsResourceTypeFolder.getStaticTypeName());
            I_CmsResourceType cmsResourceType = OpenCms.getResourceManager().getResourceType(resTypeName);
            if (!cmsResourceType.isFolder()) {
                throw new CmisConstraintException("Invalid folder type: " + resTypeName);
            }
            List<CmsProperty> cmsProperties = getOpenCmsProperties(properties);
            String newFolderName = (String)properties.get(PropertyIds.NAME).getFirstValue();
            checkResourceName(newFolderName);
            CmsUUID parentFolderId = new CmsUUID(folderId);
            CmsResource parentFolder = cms.readResource(parentFolderId);
            String newFolderPath = CmsStringUtil.joinPaths(parentFolder.getRootPath(), newFolderName);
            try {
                CmsResource newFolder = cms.createResource(
                    newFolderPath,
                    cmsResourceType.getTypeId(),
                    null,
                    cmsProperties);
                cms.unlockResource(newFolder);
                return newFolder.getStructureId().toString();
            } catch (CmsVfsResourceAlreadyExistsException e) {
                throw new CmisNameConstraintViolationException(e.getLocalizedMessage(), e);
            }
        } catch (CmsException e) {
            handleCmsException(e);
            return null;
        }
    }

    /**
     * Creates a relationship.<p>
     * 
     * @param context the call context 
     * @param properties the properties 
     * @param policies the policies 
     * @param addAces the ACEs to add
     * @param removeAces the ACEs to remove 
     * 
     * @return the new relationship id 
     */
    public synchronized String createRelationship(
        CallContext context,
        Properties properties,
        List<String> policies,
        Acl addAces,
        Acl removeAces) {

        try {
            CmsObject cms = getCmsObject(context);
            Map<String, PropertyData<?>> propertyMap = properties.getProperties();
            String sourceProp = (String)(propertyMap.get(PropertyIds.SOURCE_ID).getFirstValue());
            String targetProp = (String)(propertyMap.get(PropertyIds.TARGET_ID).getFirstValue());
            String typeId = (String)(propertyMap.get(PropertyIds.OBJECT_TYPE_ID).getFirstValue());
            if (!typeId.startsWith("opencms:")) {
                throw new CmisConstraintException("Can't create this relationship type.");
            }
            String cmsTypeName = typeId.substring("opencms:".length());
            CmsUUID sourceId = new CmsUUID(sourceProp);
            CmsUUID targetId = new CmsUUID(targetProp);
            CmsResource sourceRes = cms.readResource(sourceId);
            boolean wasLocked = ensureLock(cms, sourceRes);
            try {
                CmsResource targetRes = cms.readResource(targetId);
                cms.addRelationToResource(sourceRes.getRootPath(), targetRes.getRootPath(), cmsTypeName);
                return "REL_" + sourceRes.getStructureId() + "_" + targetRes.getStructureId() + "_" + cmsTypeName;
            } finally {
                if (wasLocked) {
                    cms.unlockResource(sourceRes);
                }
            }
        } catch (CmsException e) {
            CmsCmisUtil.handleCmsException(e);
            return null;
        }
    }

    /**
     * Deletes the content stream of an object.<p>
     * 
     * @param context the call context 
     * @param objectId the object id 
     * @param changeToken the change token 
     */
    public synchronized void deleteContentStream(
        CallContext context,
        Holder<String> objectId,
        Holder<String> changeToken) {

        throw new CmisConstraintException("Content streams may not be deleted.");

    }

    /**
     * Deletes a CMIS object.<p>
     * 
     * @param context the call context 
     * @param objectId the id of the object to delete 
     * @param allVersions flag to delete all version 
     */
    public synchronized void deleteObject(CallContext context, String objectId, Boolean allVersions) {

        checkWriteAccess();
        createHelper(objectId, getCmsObject(context)).deleteObject(context, objectId, allVersions);
    }

    /**
     * Deletes a whole file tree.<p>
     * 
     * @param context the call context 
     * @param folderId the folder id 
     * @param allVersions flag to include all versions 
     * @param unfileObjects flag to unfile objects 
     * @param continueOnFailure flag to continue on failure 
     * 
     * @return data containing the objects which weren'T deleted successfully 
     */
    public synchronized FailedToDeleteData deleteTree(
        CallContext context,
        String folderId,
        Boolean allVersions,
        UnfileObject unfileObjects,
        Boolean continueOnFailure) {

        checkWriteAccess();

        try {

            FailedToDeleteDataImpl result = new FailedToDeleteDataImpl();
            result.setIds(new ArrayList<String>());
            CmsObject cms = getCmsObject(context);
            CmsUUID structureId = new CmsUUID(folderId);
            CmsResource folder = cms.readResource(structureId);
            if (!folder.isFolder()) {
                throw new CmisConstraintException("deleteTree can only be used on folders.");
            }
            ensureLock(cms, folder);
            cms.deleteResource(folder.getRootPath(), CmsResource.DELETE_PRESERVE_SIBLINGS);
            return result;
        } catch (CmsException e) {
            handleCmsException(e);
            return null;
        }
    }

    /**
     * Gets the ACL for an object.<p>
     * 
     * @param context the call context
     * @param objectId the object id 
     * @param onlyBasicPermissions flag to only get basic permissions 
     * 
     * @return the ACL for the object 
     */
    public synchronized Acl getAcl(CallContext context, String objectId, Boolean onlyBasicPermissions) {

        return createHelper(objectId, getCmsObject(context)).getAcl(context, objectId, onlyBasicPermissions);
    }

    /**
     * Gets the allowable actions for an object.<p>
     * 
     * @param context the call context 
     * @param objectId the object id 
     * @return the allowable actions 
     */
    public synchronized AllowableActions getAllowableActions(CallContext context, String objectId) {

        return createHelper(objectId, getCmsObject(context)).getAllowableActions(context, objectId);
    }

    /**
     * Corresponds to CMIS getCheckedOutDocs service method.<p>
     *  
     * @param context
     * @param folderId
     * @param filter
     * @param orderBy
     * @param includeAllowableActions
     * @param includeRelationships
     * @param renditionFilter
     * @param maxItems
     * @param skipCount
     * 
     * @return a list of CMIS objects 
     */
    public synchronized ObjectList getCheckedOutDocs(
        CallContext context,
        String folderId,
        String filter,
        String orderBy,
        Boolean includeAllowableActions,
        IncludeRelationships includeRelationships,
        String renditionFilter,
        BigInteger maxItems,
        BigInteger skipCount) {

        ObjectListImpl result = new ObjectListImpl();
        result.setObjects(new ArrayList<ObjectData>());
        return result;
    }

    /**
     * Gets the children of a folder.<p>
     *  
     * @param context the call context 
     * @param folderId the parent folder id 
     * @param filter the property filter 
     * @param orderBy the ordering clause
     * @param includeAllowableActions flag to include allowable actions 
     * @param includeRelationships flag to include relations 
     * @param renditionFilter the rendition filter string 
     * @param includePathSegment flag to include the path segment 
     * @param maxItems the maximum number of items 
     * @param skipCount the index from which to start 
     * 
     * @param objectInfos the combined object info for the children
     *  
     * @return the object information 
     */
    public synchronized ObjectInFolderList getChildren(
        CallContext context,
        String folderId,
        String filter,
        String orderBy,
        Boolean includeAllowableActions,
        IncludeRelationships includeRelationships,
        String renditionFilter,
        Boolean includePathSegment,
        BigInteger maxItems,
        BigInteger skipCount,
        ObjectInfoHandler objectInfos) {

        try {
            CmsCmisResourceHelper helper = new CmsCmisResourceHelper(this);

            // split filter
            Set<String> filterCollection = splitFilter(filter);

            // set defaults if values not set
            boolean iaa = (includeAllowableActions == null ? false : includeAllowableActions.booleanValue());
            boolean ips = (includePathSegment == null ? false : includePathSegment.booleanValue());

            // skip and max
            int skip = (skipCount == null ? 0 : skipCount.intValue());
            if (skip < 0) {
                skip = 0;
            }

            int max = (maxItems == null ? Integer.MAX_VALUE : maxItems.intValue());
            if (max < 0) {
                max = Integer.MAX_VALUE;
            }

            CmsObject cms = getCmsObject(context);
            CmsUUID structureId = new CmsUUID(folderId);
            CmsResource folder = cms.readResource(structureId);
            if (!folder.isFolder()) {
                throw new CmisObjectNotFoundException("Not a folder!");
            }

            // set object info of the the folder
            if (context.isObjectInfoRequired()) {
                helper.collectObjectData(context, cms, folder, null, false, false, includeRelationships, objectInfos);
            }

            // prepare result
            ObjectInFolderListImpl result = new ObjectInFolderListImpl();
            String folderSitePath = cms.getRequestContext().getSitePath(folder);
            List<CmsResource> children = cms.getResourcesInFolder(folderSitePath, CmsResourceFilter.DEFAULT);
            CmsObjectListLimiter<CmsResource> limiter = new CmsObjectListLimiter<CmsResource>(
                children,
                maxItems,
                skipCount);
            List<ObjectInFolderData> resultObjects = new ArrayList<ObjectInFolderData>();
            for (CmsResource child : limiter) {
                // build and add child object
                ObjectInFolderDataImpl objectInFolder = new ObjectInFolderDataImpl();
                objectInFolder.setObject(helper.collectObjectData(
                    context,
                    cms,
                    child,
                    filterCollection,
                    iaa,
                    false,
                    includeRelationships,
                    objectInfos));
                if (ips) {
                    objectInFolder.setPathSegment(child.getName());
                }
                resultObjects.add(objectInFolder);
            }
            result.setObjects(resultObjects);
            result.setNumItems(BigInteger.valueOf(children.size()));
            result.setHasMoreItems(Boolean.valueOf(limiter.hasMore()));
            return result;
        } catch (CmsException e) {
            handleCmsException(e);
            return null;
        }

    }

    /**
     * @see org.opencms.configuration.I_CmsConfigurationParameterHandler#getConfiguration()
     */
    public CmsParameterConfiguration getConfiguration() {

        return m_parameterConfiguration;
    }

    /**
     * Gets the content stream for a CMIS object.<p>
     *  
     * @param context the call context 
     * @param objectId the object id 
     * @param streamId the rendition stream id 
     * @param offset 
     * @param length
     * 
     * @return the content stream 
     */
    public synchronized ContentStream getContentStream(
        CallContext context,
        String objectId,
        String streamId,
        BigInteger offset,
        BigInteger length) {

        try {

            if ((offset != null) || (length != null)) {
                throw new CmisInvalidArgumentException("Offset and Length are not supported!");
            }
            CmsObject cms = getCmsObject(context);
            CmsResource resource = cms.readResource(new CmsUUID(objectId));
            if (resource.isFolder()) {
                throw new CmisStreamNotSupportedException("Not a file!");
            }
            CmsFile file = cms.readFile(resource);
            byte[] contents;
            contents = file.getContents();
            contents = extractRange(contents, offset, length);
            InputStream stream = new ByteArrayInputStream(contents);

            ContentStreamImpl result = new ContentStreamImpl();
            result.setFileName(file.getName());
            result.setLength(BigInteger.valueOf(contents.length));
            result.setMimeType(OpenCms.getResourceManager().getMimeType(file.getRootPath(), null, "text/plain"));
            result.setStream(stream);

            return result;
        } catch (CmsException e) {
            handleCmsException(e);
            return null;
        }
    }

    /**
     * 
     * @param context the call context 
     * @param folderId the folder id 
     * @param depth the maximum depth 
     * @param filter the property filter 
     * @param includeAllowableActions flag to include allowable actions 
     * @param includePathSegment flag to include path segments 
     * @param objectInfos object info handler 
     * @param foldersOnly flag to ignore documents and only return folders
     * 
     * @return the list of descendants 
     */
    public synchronized List<ObjectInFolderContainer> getDescendants(
        CallContext context,
        String folderId,
        BigInteger depth,
        String filter,
        Boolean includeAllowableActions,
        Boolean includePathSegment,
        ObjectInfoHandler objectInfos,
        boolean foldersOnly) {

        try {
            CmsCmisResourceHelper helper = new CmsCmisResourceHelper(this);

            // check depth
            int d = (depth == null ? 2 : depth.intValue());
            if (d == 0) {
                throw new CmisInvalidArgumentException("Depth must not be 0!");
            }
            if (d < -1) {
                d = -1;
            }

            // split filter
            Set<String> filterCollection = splitFilter(filter);

            // set defaults if values not set
            boolean iaa = (includeAllowableActions == null ? false : includeAllowableActions.booleanValue());
            boolean ips = (includePathSegment == null ? false : includePathSegment.booleanValue());

            CmsObject cms = getCmsObject(context);
            CmsUUID folderStructureId = new CmsUUID(folderId);
            CmsResource folder = cms.readResource(folderStructureId);
            if (!folder.isFolder()) {
                throw new CmisObjectNotFoundException("Not a folder!");
            }

            // set object info of the the folder
            if (context.isObjectInfoRequired()) {
                helper.collectObjectData(
                    context,
                    cms,
                    folder,
                    null,
                    false,
                    false,
                    IncludeRelationships.NONE,
                    objectInfos);
            }

            // get the tree
            List<ObjectInFolderContainer> result = new ArrayList<ObjectInFolderContainer>();
            gatherDescendants(context, cms, folder, result, foldersOnly, d, filterCollection, iaa, ips, objectInfos);

            return result;
        } catch (CmsException e) {
            handleCmsException(e);
            return null;
        }
    }

    /**
     * Gets the description of the repository.<p>
     * 
     * @return the repository description 
     */
    public String getDescription() {

        if (m_description != null) {
            return m_description;
        }
        if (m_project != null) {
            return m_project.getDescription();
        }
        return m_id;
    }

    /** 
     * @see org.opencms.repository.I_CmsRepository#getFilter()
     */
    public CmsRepositoryFilter getFilter() {

        return m_filter;
    }

    /**
     * Corresponds to CMIS getFolderParent service method.<p>
     * 
     * @param context the call context 
     * @param folderId the folder id 
     * @param filter the property filter 
     * @param objectInfos the object info handler 
     * 
     * @return the parent object data 
     */
    public synchronized ObjectData getFolderParent(
        CallContext context,
        String folderId,
        String filter,
        ObjectInfoHandler objectInfos) {

        List<ObjectParentData> parents = getObjectParents(
            context,
            folderId,
            filter,
            Boolean.FALSE,
            Boolean.FALSE,
            objectInfos);
        if (parents.size() == 0) {
            throw new CmisInvalidArgumentException("The root folder has no parent!");
        }
        return parents.get(0).getObject();
    }

    /**
     * Gets the repository id.<p>
     * 
     * @return the repository id 
     */
    public String getId() {

        return m_id;
    }

    /**
     * Gets the name of the repository.<p>
     * 
     * @return the name of the repository 
     */
    public String getName() {

        return m_id;
    }

    /**
     * Gets the data for a CMIS object.<p>
     *  
     * @param context the CMIS call context 
     * @param objectId the id of the object 
     * @param filter the property filter 
     * @param includeAllowableActions flag to include allowable actions 
     * @param includeRelationships flag to include relationships 
     * @param renditionFilter the rendition filter string 
     * @param includePolicyIds flag to include policy ids 
     * @param includeAcl flag to include ACLs 
     * @param objectInfos the object info handler 
     * 
     * @return the CMIS object data 
     */
    public synchronized ObjectData getObject(
        CallContext context,
        String objectId,
        String filter,
        Boolean includeAllowableActions,
        IncludeRelationships includeRelationships,
        String renditionFilter,
        Boolean includePolicyIds,
        Boolean includeAcl,
        ObjectInfoHandler objectInfos) {

        return createHelper(objectId, getCmsObject(context)).getObject(
            context,
            objectId,
            filter,
            includeAllowableActions,
            includeRelationships,
            renditionFilter,
            includePolicyIds,
            includeAcl,
            objectInfos);
    }

    /**
     * Reads a CMIS object by path.<p>
     * 
     * @param context the call context 
     * @param path the repository path 
     * @param filter the property filter string 
     * @param includeAllowableActions flag to include allowable actions 
     * @param includeRelationships flag to include relationships 
     * @param renditionFilter the rendition filter string 
     * @param includePolicyIds flag to include policy ids 
     * @param includeAcl flag to include ACLs 
     * @param objectInfos the object info handler 
     * 
     * @return the object data 
     */
    public synchronized ObjectData getObjectByPath(
        CallContext context,
        String path,
        String filter,
        Boolean includeAllowableActions,
        IncludeRelationships includeRelationships,
        String renditionFilter,
        Boolean includePolicyIds,
        Boolean includeAcl,

        ObjectInfoHandler objectInfos) {

        try {
            CmsCmisResourceHelper helper = new CmsCmisResourceHelper(this);

            // split filter
            Set<String> filterCollection = splitFilter(filter);

            // check path
            if (CmsStringUtil.isEmptyOrWhitespaceOnly(path)) {
                throw new CmisInvalidArgumentException("Invalid folder path!");
            }
            CmsObject cms = getCmsObject(context);
            CmsResource file = cms.readResource(path);

            return helper.collectObjectData(
                context,
                cms,
                file,
                filterCollection,
                includeAllowableActions.booleanValue(),
                includeAcl.booleanValue(),
                IncludeRelationships.NONE,
                objectInfos);

        } catch (CmsException e) {
            handleCmsException(e);
            return null;
        }
    }

    /**
     * Gets the parents of an object.<p>
     * 
     * @param context the call context 
     * @param objectId the object id 
     * @param filter
     * @param includeAllowableActions
     * @param includeRelativePathSegment
     * @param objectInfos
     * 
     * @return the data for the object parents 
     */
    public synchronized List<ObjectParentData> getObjectParents(
        CallContext context,
        String objectId,
        String filter,
        Boolean includeAllowableActions,
        Boolean includeRelativePathSegment,
        ObjectInfoHandler objectInfos) {

        try {
            CmsCmisResourceHelper helper = new CmsCmisResourceHelper(this);

            // split filter
            Set<String> filterCollection = splitFilter(filter);

            // set defaults if values not set
            boolean iaa = (includeAllowableActions == null ? false : includeAllowableActions.booleanValue());
            boolean irps = (includeRelativePathSegment == null ? false : includeRelativePathSegment.booleanValue());

            CmsObject cms = getCmsObject(context);
            CmsUUID structureId = new CmsUUID(objectId);
            CmsResource file = cms.readResource(structureId);
            // don't climb above the root folder

            if (m_root.equals(file)) {
                return Collections.emptyList();
            }

            // set object info of the the object
            if (context.isObjectInfoRequired()) {
                helper.collectObjectData(context, cms, file, null, false, false, IncludeRelationships.NONE, objectInfos);
            }

            // get parent folder
            CmsResource parent = cms.readParentFolder(file.getStructureId());
            ObjectData object = helper.collectObjectData(
                context,
                cms,
                parent,
                filterCollection,
                iaa,
                false,
                IncludeRelationships.NONE,
                objectInfos);

            ObjectParentDataImpl result = new ObjectParentDataImpl();
            result.setObject(object);
            if (irps) {
                result.setRelativePathSegment(file.getName());
            }

            return Collections.singletonList((ObjectParentData)result);
        } catch (CmsException e) {
            handleCmsException(e);
            return null;
        }

    }

    /**
     * Gets the relationships for an object.<p>
     * 
     * @param context the call context
     * @param objectId the object id 
     * @param includeSubRelationshipTypes flag to include relationship subtypes 
     * @param relationshipDirection the direction for the relations 
     * @param typeId the relation type id 
     * @param filter the property filter 
     * @param includeAllowableActions flag to include allowable actions  
     * @param maxItems the maximum number of items to return 
     * @param skipCount the number of items to skip 
     * @param objectInfos the object info handler
     * 
     * @return the relationships for the object
     */
    public synchronized ObjectList getObjectRelationships(
        CallContext context,
        String objectId,
        Boolean includeSubRelationshipTypes,
        RelationshipDirection relationshipDirection,
        String typeId,
        String filter,
        Boolean includeAllowableActions,
        BigInteger maxItems,
        BigInteger skipCount,
        ObjectInfoHandler objectInfos) {

        try {
            CmsObject cms = getCmsObject(context);
            ObjectListImpl result = new ObjectListImpl();
            CmsUUID structureId = new CmsUUID(objectId);
            CmsResource resource = cms.readResource(structureId);

            List<ObjectData> resultObjects = getRelationshipObjectData(
                context,
                cms,
                resource,
                relationshipDirection,
                CmsCmisUtil.splitFilter(filter),
                includeAllowableActions,
                objectInfos);
            CmsObjectListLimiter<ObjectData> limiter = new CmsObjectListLimiter<ObjectData>(
                resultObjects,
                maxItems,
                skipCount);
            List<ObjectData> limitedResults = new ArrayList<ObjectData>();
            for (ObjectData objectData : limiter) {
                limitedResults.add(objectData);
            }
            result.setNumItems(BigInteger.valueOf(resultObjects.size()));
            result.setHasMoreItems(Boolean.valueOf(limiter.hasMore()));
            result.setObjects(limitedResults);
            return result;
        } catch (CmsException e) {
            CmsCmisUtil.handleCmsException(e);
            return null;
        }
    }

    /**
     * Gets the properties for a CMIS object.<p>
     *  
     * @param context the call context 
     * @param objectId the CMIS object id 
     * @param filter the property filter string 
     * @param objectInfos the object info handler 
     * 
     * @return the set of properties 
     */
    public synchronized Properties getProperties(CallContext context, String objectId, String filter,

    ObjectInfoHandler objectInfos) {

        ObjectData object = getObject(
            context,
            objectId,
            null,
            Boolean.FALSE,
            null,
            null,
            Boolean.FALSE,
            Boolean.FALSE,
            objectInfos);
        return object.getProperties();
    }

    /**
     * Gets the renditions for a CMIS object.<p>
     *  
     * @param context the call context 
     * @param objectId the  object id 
     * @param renditionFilter the rendition filter 
     * @param maxItems the maximum number of renditions 
     * @param skipCount the number of renditions to skip 
     * 
     * @return the list of renditions 
     */
    public synchronized List<RenditionData> getRenditions(
        CallContext context,
        String objectId,
        String renditionFilter,
        BigInteger maxItems,
        BigInteger skipCount) {

        try {
            CmsObject cms = getCmsObject(context);
            CmsUUID structureId = new CmsUUID(objectId);
            CmsResource resource = cms.readResource(structureId);
            RenditionDataImpl rendition = new RenditionDataImpl();
            rendition.setKind("opencms:rendered");
            rendition.setMimeType(OpenCms.getResourceManager().getMimeType(resource.getRootPath(), "UTF-8"));
            rendition.setStreamId("rendered");
            List<RenditionData> result = Collections.singletonList((RenditionData)rendition);
            return result;
        } catch (CmsException e) {
            handleCmsException(e);
            return null;
        }
    }

    /**
     * Gets the repository information for this repository.<p>
     * 
     * @return the repository info
     */
    public synchronized RepositoryInfo getRepositoryInfo() {

        // compile repository info
        RepositoryInfoImpl repositoryInfo = new RepositoryInfoImpl();

        repositoryInfo.setId(m_id);
        repositoryInfo.setName(getName());
        repositoryInfo.setDescription(getDescription());

        repositoryInfo.setCmisVersionSupported("1.0");

        repositoryInfo.setProductName("OpenCms");
        repositoryInfo.setProductVersion(OpenCms.getSystemInfo().getVersion());
        repositoryInfo.setVendorName("Alkacon Software GmbH");
        repositoryInfo.setRootFolder(m_root.getStructureId().toString());
        repositoryInfo.setThinClientUri("");
        repositoryInfo.setPrincipalAnonymous(OpenCms.getDefaultUsers().getUserGuest());
        repositoryInfo.setChangesIncomplete(Boolean.TRUE);
        RepositoryCapabilitiesImpl capabilities = new RepositoryCapabilitiesImpl();
        capabilities.setCapabilityAcl(CapabilityAcl.DISCOVER);
        capabilities.setAllVersionsSearchable(Boolean.FALSE);
        capabilities.setCapabilityJoin(CapabilityJoin.NONE);
        capabilities.setSupportsMultifiling(Boolean.FALSE);
        capabilities.setSupportsUnfiling(Boolean.FALSE);
        capabilities.setSupportsVersionSpecificFiling(Boolean.FALSE);
        capabilities.setIsPwcSearchable(Boolean.FALSE);
        capabilities.setIsPwcUpdatable(Boolean.FALSE);
        capabilities.setCapabilityQuery(CapabilityQuery.FULLTEXTONLY);
        capabilities.setCapabilityChanges(CapabilityChanges.NONE);
        capabilities.setCapabilityContentStreamUpdates(CapabilityContentStreamUpdates.ANYTIME);
        capabilities.setSupportsGetDescendants(Boolean.TRUE);
        capabilities.setSupportsGetFolderTree(Boolean.TRUE);
        capabilities.setCapabilityRendition(CapabilityRenditions.NONE);
        repositoryInfo.setCapabilities(capabilities);

        AclCapabilitiesDataImpl aclCapability = new AclCapabilitiesDataImpl();
        aclCapability.setSupportedPermissions(SupportedPermissions.BOTH);
        aclCapability.setAclPropagation(AclPropagation.REPOSITORYDETERMINED);

        // permissions
        List<PermissionDefinition> permissions = new ArrayList<PermissionDefinition>();
        permissions.add(createPermission("cmis:read", "Read"));
        permissions.add(createPermission("cmis:write", "Write"));
        permissions.add(createPermission("cmis:all", "All"));
        aclCapability.setPermissionDefinitionData(permissions);

        // mappings
        PermissionMappings m = new PermissionMappings();
        m.add(PermissionMapping.CAN_CREATE_DOCUMENT_FOLDER, "cmis:write");
        m.add(PermissionMapping.CAN_CREATE_FOLDER_FOLDER, "cmis:write");
        m.add(PermissionMapping.CAN_DELETE_CONTENT_DOCUMENT, "cmis:write");
        m.add(PermissionMapping.CAN_DELETE_OBJECT, "cmis:write");
        m.add(PermissionMapping.CAN_DELETE_TREE_FOLDER, "cmis:write");
        m.add(PermissionMapping.CAN_GET_ACL_OBJECT, "cmis:read");
        m.add(PermissionMapping.CAN_GET_ALL_VERSIONS_VERSION_SERIES, "cmis:read");
        m.add(PermissionMapping.CAN_GET_CHILDREN_FOLDER, "cmis:read");
        m.add(PermissionMapping.CAN_GET_DESCENDENTS_FOLDER, "cmis:read");
        m.add(PermissionMapping.CAN_GET_FOLDER_PARENT_OBJECT, "cmis:read");
        m.add(PermissionMapping.CAN_GET_PARENTS_FOLDER, "cmis:read");
        m.add(PermissionMapping.CAN_GET_PROPERTIES_OBJECT, "cmis:read");
        m.add(PermissionMapping.CAN_MOVE_OBJECT, "cmis:write");
        m.add(PermissionMapping.CAN_MOVE_SOURCE, "cmis:write");
        m.add(PermissionMapping.CAN_MOVE_TARGET, "cmis:write");
        m.add(PermissionMapping.CAN_SET_CONTENT_DOCUMENT, "cmis:write");
        m.add(PermissionMapping.CAN_UPDATE_PROPERTIES_OBJECT, "cmis:write");
        m.add(PermissionMapping.CAN_VIEW_CONTENT_OBJECT, "cmis:read");
        aclCapability.setPermissionMappingData(m);
        repositoryInfo.setAclCapabilities(aclCapability);
        return repositoryInfo;
    }

    /**
     * Gets the children of a given type.<p>
     * 
     * @param context the call context 
     * @param typeId the parent type id 
     * @param includePropertyDefinitions flag to include property definitions 
     * @param maxItems the maximum number of items to return 
     * @param skipCount the number of items to skip 
     * 
     * @return the list of child type definitions 
     */
    public synchronized TypeDefinitionList getTypeChildren(
        CallContext context,
        String typeId,
        Boolean includePropertyDefinitions,
        BigInteger maxItems,
        BigInteger skipCount) {

        return m_typeManager.getTypeChildren(typeId, includePropertyDefinitions.booleanValue(), maxItems, skipCount);
    }

    /**
     * Gets a type definition by id.<p>
     * 
     * @param context the call context 
     * @param typeId the type id 
     * 
     * @return the type definition for the given id 
     */
    public synchronized TypeDefinition getTypeDefinition(CallContext context, String typeId) {

        return m_typeManager.getTypeDefinition(typeId);
    }

    /**
     * Gets the type descendants.<p>
     * 
     * @param context the call context 
     * @param typeId the parent type id 
     * @param depth the maximum type depth 
     * @param includePropertyDefinitions flag to include the property definitions for types 
     * 
     * @return the list of type definitions 
     */
    public synchronized List<TypeDefinitionContainer> getTypeDescendants(
        CallContext context,
        String typeId,
        BigInteger depth,
        Boolean includePropertyDefinitions) {

        return m_typeManager.getTypeDescendants(typeId, depth, includePropertyDefinitions);
    }

    /**
     * @see org.opencms.configuration.I_CmsConfigurationParameterHandler#initConfiguration()
     */
    public void initConfiguration() throws CmsConfigurationException {

        if (m_filter != null) {
            m_filter.initConfiguration();
        }
        m_description = m_parameterConfiguration.getString("description", null);
    }

    /**
     * @see org.opencms.repository.I_CmsRepository#initializeCms(org.opencms.file.CmsObject)
     */
    public void initializeCms(CmsObject cms) {

        m_adminCms = cms;
        try {
            m_typeManager = CmsCmisTypeManager.getDefaultInstance(m_adminCms);
            String projectName = m_parameterConfiguration.getString("project", "Online");
            CmsResource root = m_adminCms.readResource("/");
            CmsObject offlineCms = OpenCms.initCmsObject(m_adminCms);
            CmsProject project = m_adminCms.readProject(projectName);
            m_project = project;
            offlineCms.getRequestContext().setCurrentProject(project);
            m_adminCms = offlineCms;
            m_root = root;
            m_isReadOnly = project.isOnlineProject();
        } catch (CmsException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Moves an object.<p>
     *  
     * @param context the call context 
     * @param objectId the object id 
     * @param targetFolderId source source folder id 
     * @param sourceFolderId the target folder id 
     */
    public synchronized void moveObject(
        CallContext context,
        Holder<String> objectId,
        String targetFolderId,
        String sourceFolderId) {

        checkWriteAccess();

        try {
            CmsObject cms = getCmsObject(context);
            CmsUUID structureId = new CmsUUID(objectId.getValue());
            CmsUUID targetStructureId = new CmsUUID(targetFolderId);
            CmsResource targetFolder = cms.readResource(targetStructureId);
            CmsResource resourceToMove = cms.readResource(structureId);
            String name = CmsResource.getName(resourceToMove.getRootPath());
            String newPath = CmsStringUtil.joinPaths(targetFolder.getRootPath(), name);
            boolean wasLocked = ensureLock(cms, resourceToMove);
            try {
                cms.moveResource(resourceToMove.getRootPath(), newPath);
            } finally {
                if (wasLocked) {
                    CmsResource movedResource = cms.readResource(resourceToMove.getStructureId());
                    cms.unlockResource(movedResource);
                }
            }
        } catch (CmsException e) {
            handleCmsException(e);
        }
    }

    /**
     * Sets the content stream of an object.<p>
     *  
     * @param context the call context 
     * @param objectId the id of the object 
     * @param overwriteFlag flag to overwrite the content stream 
     * @param changeToken the change token 
     * @param contentStream the new content stream 
     */
    public synchronized void setContentStream(
        CallContext context,
        Holder<String> objectId,
        Boolean overwriteFlag,
        Holder<String> changeToken,
        ContentStream contentStream) {

        checkWriteAccess();

        try {
            CmsObject cms = getCmsObject(context);
            CmsUUID structureId = new CmsUUID(objectId.getValue());
            boolean overwrite = (overwriteFlag == null) || overwriteFlag.booleanValue();
            if (!overwrite) {
                throw new CmisContentAlreadyExistsException();
            }
            CmsResource resource = cms.readResource(structureId);
            if (resource.isFolder()) {
                throw new CmisStreamNotSupportedException("Folders may not have content streams.");
            }
            CmsFile file = cms.readFile(resource);
            InputStream contentInput = contentStream.getStream();
            byte[] newContent = CmsFileUtil.readFully(contentInput);
            file.setContents(newContent);
            boolean wasLocked = ensureLock(cms, resource);
            CmsFile newFile = cms.writeFile(file);
            if (wasLocked) {
                cms.unlockResource(newFile);
            }
        } catch (CmsException e) {
            handleCmsException(e);
        } catch (IOException e) {
            throw new CmisRuntimeException(e.getLocalizedMessage(), e);
        }
    }

    /**
     * @see org.opencms.repository.I_CmsRepository#setFilter(org.opencms.repository.CmsRepositoryFilter)
     */
    public void setFilter(CmsRepositoryFilter filter) {

        m_filter = filter;
    }

    /**
     * @see org.opencms.repository.I_CmsRepository#setName(java.lang.String)
     */
    public void setName(String name) {

        m_id = name;
    }

    /**
     * Updates the properties for an object.<p>
     * 
     * @param context the call context 
     * @param objectId the object id 
     * @param changeToken the change token 
     * @param properties the properties 
     */
    public synchronized void updateProperties(
        CallContext context,
        Holder<String> objectId,
        Holder<String> changeToken,
        Properties properties) {

        checkWriteAccess();

        try {

            CmsObject cms = getCmsObject(context);
            CmsUUID structureId = new CmsUUID(objectId.getValue());
            CmsResource resource = cms.readResource(structureId);
            Map<String, PropertyData<?>> propertyMap = properties.getProperties();
            List<CmsProperty> cmsProperties = getOpenCmsProperties(propertyMap);
            boolean wasLocked = ensureLock(cms, resource);
            try {
                cms.writePropertyObjects(resource, cmsProperties);
                @SuppressWarnings("unchecked")
                PropertyData<String> nameProperty = (PropertyData<String>)propertyMap.get(PropertyIds.NAME);
                if (nameProperty != null) {
                    String newName = nameProperty.getFirstValue();
                    checkResourceName(newName);
                    String parentFolder = CmsResource.getParentFolder(resource.getRootPath());
                    String newPath = CmsStringUtil.joinPaths(parentFolder, newName);
                    cms.moveResource(resource.getRootPath(), newPath);
                    resource = cms.readResource(resource.getStructureId());
                }
            } finally {
                if (wasLocked) {
                    cms.unlockResource(resource);
                }
            }
        } catch (CmsException e) {
            handleCmsException(e);
        }
    }

    /**
     * Checks whether we have write access to this repository and throws an exception otherwise.<p>
     */
    protected void checkWriteAccess() {

        if (m_isReadOnly) {
            throw new CmisNotSupportedException("Readonly repository '" + m_id + "' does not allow write operations.");
        }
    }

    /**
     * Initializes a CMS context for the authentication data contained in a call context.<p>
     * 
     * @param context the call context
     * @return the initialized CMS context 
     */
    protected CmsObject getCmsObject(CallContext context) {

        try {
            if (context.getUsername() == null) {
                // user name can be null 
                CmsObject cms = OpenCms.initCmsObject(OpenCms.getDefaultUsers().getUserGuest());
                cms.getRequestContext().setCurrentProject(m_adminCms.getRequestContext().getCurrentProject());
                return cms;
            } else {
                CmsObject cms = OpenCms.initCmsObject(m_adminCms);
                CmsProject projectBeforeLogin = cms.getRequestContext().getCurrentProject();
                cms.loginUser(context.getUsername(), context.getPassword());
                cms.getRequestContext().setCurrentProject(projectBeforeLogin);
                return cms;
            }
        } catch (CmsException e) {
            throw new CmisPermissionDeniedException(e.getLocalizedMessage(), e);

        }
    }

    /**
     *  Gets the relationship data for a given resource.<p>
     * 
     * @param context the call context 
     * @param cms the CMS context
     * @param resource the resource 
     * @param relationshipDirection the relationship direction 
     * @param filterSet the property filter 
     * @param includeAllowableActions true if allowable actions should be included 
     * @param objectInfos the object info handler 
     * @return the list of relationship data 
     * 
     * @throws CmsException if something goes wrong 
     */
    protected List<ObjectData> getRelationshipObjectData(
        CallContext context,
        CmsObject cms,
        CmsResource resource,
        RelationshipDirection relationshipDirection,
        Set<String> filterSet,
        Boolean includeAllowableActions,
        ObjectInfoHandler objectInfos) throws CmsException {

        List<ObjectData> resultObjects = new ArrayList<ObjectData>();
        CmsRelationFilter relationFilter;
        if (relationshipDirection == RelationshipDirection.SOURCE) {
            relationFilter = CmsRelationFilter.TARGETS;
        } else if (relationshipDirection == RelationshipDirection.TARGET) {
            relationFilter = CmsRelationFilter.SOURCES;
        } else {
            relationFilter = CmsRelationFilter.ALL;
        }

        List<CmsRelation> unfilteredRelations = cms.getRelationsForResource(resource.getRootPath(), relationFilter);
        List<CmsRelation> relations = new ArrayList<CmsRelation>();
        for (CmsRelation relation : unfilteredRelations) {
            if (relation.getTargetId().isNullUUID() || relation.getSourceId().isNullUUID()) {
                continue;
            }
            relations.add(relation);
        }
        CmsCmisRelationHelper helper = new CmsCmisRelationHelper(this);
        for (CmsRelation relation : relations) {
            ObjectData objData = helper.collectObjectData(
                context,
                cms,
                resource,
                relation,
                filterSet,
                includeAllowableActions.booleanValue(),
                false,
                objectInfos);
            resultObjects.add(objData);
        }
        return resultObjects;
    }

    /**
     * Extracts the resource type from a set of CMIS properties.<p>
     * 
     * @param properties the CMIS properties 
     * @param defaultValue the default value 
     * 
     * @return the resource type property, or the default value if the property was not found 
     */
    protected String getResourceTypeFromProperties(Map<String, PropertyData<?>> properties, String defaultValue) {

        PropertyData<?> typeProp = properties.get(CmsCmisTypeManager.PROPERTY_RESOURCE_TYPE);
        String resTypeName = defaultValue;
        if (typeProp != null) {
            resTypeName = (String)typeProp.getFirstValue();
        }
        return resTypeName;
    }

    /**
     * Gets the type manager instance.<p>
     * 
     * @return the type manager instance 
     */
    protected CmsCmisTypeManager getTypeManager() {

        return m_typeManager;
    }

    /**
     * Creates a helper object to perform CRUD operations for the given object id.<p>
     * 
     * @param objectId the object id 
     * @param cms the CMS context 
     * 
     * @return the helper object to use for the given object id 
     */
    I_CmsCmisObjectHelper createHelper(String objectId, CmsObject cms) {

        if (CmsUUID.isValidUUID(objectId)) {
            return new CmsCmisResourceHelper(this);
        } else if (CmsCmisRelationHelper.RELATION_PATTERN.matcher(objectId).matches()) {
            return new CmsCmisRelationHelper(this);
        } else {
            return null;
        }
    }

    /**
     * Helper method to collect the descendants of a given folder.<p>
     *  
     * @param context the call context 
     * @param cms the CMS context 
     * @param folder the parent folder  
     * @param list the list to which the descendants should be added 
     * @param foldersOnly flag to exclude files from the result 
     * @param depth the maximum depth 
     * @param filter the property filter 
     * @param includeAllowableActions flag to include allowable actions 
     * @param includePathSegments flag to include path segments 
     * @param objectInfos the object info handler  
     */
    private void gatherDescendants(
        CallContext context,
        CmsObject cms,
        CmsResource folder,
        List<ObjectInFolderContainer> list,
        boolean foldersOnly,
        int depth,
        Set<String> filter,
        boolean includeAllowableActions,
        boolean includePathSegments,
        ObjectInfoHandler objectInfos) {

        try {
            CmsCmisResourceHelper helper = new CmsCmisResourceHelper(this);
            List<CmsResource> children = cms.getResourcesInFolder(cms.getSitePath(folder), CmsResourceFilter.DEFAULT);
            Collections.sort(children, new Comparator<CmsResource>() {

                public int compare(CmsResource a, CmsResource b) {

                    return a.getName().compareTo(b.getName());
                }
            });
            // iterate through children
            for (CmsResource child : children) {

                // folders only?
                if (foldersOnly && !child.isFolder()) {
                    continue;
                }

                // add to list
                ObjectInFolderDataImpl objectInFolder = new ObjectInFolderDataImpl();
                objectInFolder.setObject(helper.collectObjectData(
                    context,
                    cms,
                    child,
                    filter,
                    includeAllowableActions,
                    false,
                    IncludeRelationships.NONE,
                    objectInfos));
                if (includePathSegments) {
                    objectInFolder.setPathSegment(child.getName());
                }

                ObjectInFolderContainerImpl container = new ObjectInFolderContainerImpl();
                container.setObject(objectInFolder);

                list.add(container);

                // move to next level
                if ((depth != 1) && child.isFolder()) {
                    container.setChildren(new ArrayList<ObjectInFolderContainer>());
                    gatherDescendants(
                        context,
                        cms,
                        child,
                        container.getChildren(),
                        foldersOnly,
                        depth - 1,
                        filter,
                        includeAllowableActions,
                        includePathSegments,
                        objectInfos);
                }
            }
        } catch (CmsException e) {
            handleCmsException(e);
        }
    }

    /**
     * Performs a query on the repository.<p>
     * 
     * @param context the call context
     * @param statement the query 
     * @param searchAllVersions flag to search all versions 
     * @param includeAllowableActions flag to include allowable actions 
     * @param includeRelationships flag to include relationships 
     * @param renditionFilter the filter string for renditions 
     * @param maxItems the maximum number of items to return 
     * @param skipCount the number of items to skip
     *  
     * @return the query result objects
     */
    public synchronized ObjectList query(
        CallContext context,
        String statement,
        Boolean searchAllVersions,
        Boolean includeAllowableActions,
        IncludeRelationships includeRelationships,
        String renditionFilter,
        BigInteger maxItems,
        BigInteger skipCount) {

        try {

            CmsObject cms = getCmsObject(context);
            CmsResource frotz = cms.readResource("/sites/default/frotz");
            CmsResource xyzzy = cms.readResource("/sites/default/xyzzy");
            CmsCmisResourceHelper helper = new CmsCmisResourceHelper(this);
            ObjectData frotzData = helper.collectObjectData(
                context,
                cms,
                frotz,
                null,
                includeAllowableActions.booleanValue(),
                false,
                includeRelationships,
                null);
            ObjectData xyzzyData = helper.collectObjectData(
                context,
                cms,
                xyzzy,
                null,
                includeAllowableActions.booleanValue(),
                false,
                includeRelationships,
                null);
            ObjectListImpl result = new ObjectListImpl();
            List<ObjectData> olist = new ArrayList<ObjectData>();
            olist.add(frotzData);
            olist.add(xyzzyData);
            result.setObjects(olist);
            result.setHasMoreItems(Boolean.FALSE);
            result.setNumItems(BigInteger.valueOf(2));
            return result;
        } catch (CmsException e) {
            CmsCmisUtil.handleCmsException(e);
            return null;
        }

    }

}
