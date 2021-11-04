/*
 *  Copyright 2015 Adobe Systems Incorporated
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package net.pandavalley.aem.extensions.core.servlets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Get Folder Size Servlet
 */
@Component(service = {Servlet.class})
@SlingServletResourceTypes(
        resourceTypes = {"sling:OrderedFolder", "sling:Folder"},
        methods = HttpConstants.METHOD_GET,
        selectors = GetFolderSizeServlet.FOLDER_SIZE_SELECTOR)
@ServiceDescription("Get Folder Size Servlet")
public class GetFolderSizeServlet extends SlingSafeMethodsServlet {

    /**
     * Serial Version UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * Total Assets Key
     */
    private static final String KEY_TOTAL_ASSETS = "totalAssets";

    /**
     * Counted Assets Key
     */
    private static final String KEY_COUNTED_ASSETS = "countedAssets";

    /**
     * Size Key
     */
    private static final String KEY_SIZE = "size";

    /**
     * Dam Size Key
     */
    private static final String KEY_DAM_SIZE = "dam:size";

    /**
     * JCR Content Metadata Key
     */
    private static final String KEY_CONTENT_METADATA = "jcr:content/metadata";

    /**
     * Folder Size Selector
     */
    public static final String FOLDER_SIZE_SELECTOR = "size";

    /**
     * Logger
     */
    private static final Logger log = LoggerFactory.getLogger(GetFolderSizeServlet.class);

    @Override
    protected void doGet(final SlingHttpServletRequest request,
                         final SlingHttpServletResponse response) throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        String folderPath = request.getRequestPathInfo().getResourcePath();

        try {
            ResourceResolver resolver = request.getResourceResolver();
            Map<String, Map<String, Integer>> folderMap = getFolderMapJSON(resolver.getResource(folderPath));

            String stmt = "SELECT * FROM [dam:Asset] WHERE ISDESCENDANTNODE('" + folderPath + "')";
            QueryManager qm = getQueryManager(request);
            Query query = qm.createQuery(stmt, Query.JCR_SQL2);

            NodeIterator results = query.execute().getNodes();

            while (results.hasNext()) {
                Node node = results.nextNode();

                String superParentPath = getSuperParentPath(folderPath, node);

                if (folderMap.get(superParentPath) == null) {
                    continue;
                }

                Map<String, Integer> folder = folderMap.get(superParentPath);
                if (node.hasNode(KEY_CONTENT_METADATA)) {
                    Node metaNode = node.getNode(KEY_CONTENT_METADATA);

                    if (metaNode.hasProperty(KEY_DAM_SIZE)) {
                        Long damSize = metaNode.getProperty(KEY_DAM_SIZE).getLong();

                        folder.put(KEY_SIZE, (int) (folder.get(KEY_SIZE) + damSize));
                        folder.put(KEY_COUNTED_ASSETS, folder.get(KEY_COUNTED_ASSETS) + 1);

                    }
                }
                folder.put(KEY_TOTAL_ASSETS, folder.get(KEY_TOTAL_ASSETS) + 1);

            }
            response.getWriter().print(new ObjectMapper().writeValueAsString(folderMap));
        } catch (Exception e) {
            log.error("Error getting size for - " + folderPath, e);
        }
    }

    /**
     * Get Folder Map JSON
     *
     * Gets a Map of child folders of the Folder Resource
     *
     * @param folderResource Folder Resource
     * @return Map
     */
    private Map<String, Map<String, Integer>> getFolderMapJSON(Resource folderResource) {
        Iterator<Resource> childrenItr = folderResource.listChildren();

        Map<String, Map<String, Integer>> folderMap = new HashMap<>();

        while (childrenItr.hasNext()) {
            Resource child = childrenItr.next();

            if (child.isResourceType("sling:OrderedFolder") || child.isResourceType("sling:Folder")) {
                folderMap.put(child.getPath(), getSizeObj());
            }
        }

        return folderMap;
    }

    /**
     * Get Size Object
     *
     * Create a Map Object of properties to be sent back for each Child Folder
     *
     * @return Map
     */
    private Map<String, Integer> getSizeObj() {

        Map<String, Integer> map = new HashMap<>();

        map.put(KEY_TOTAL_ASSETS, 0);
        map.put(KEY_COUNTED_ASSETS, 0);
        map.put(KEY_SIZE, 0);

        return map;
    }

    /**
     * Get Query Manager
     *
     * @param request Sling HTTP Servlet Request
     * @return Query Manager
     * @throws Exception Error getting Session
     */
    private QueryManager getQueryManager(SlingHttpServletRequest request) throws Exception {
        ResourceResolver resolver = request.getResourceResolver();

        Session session = resolver.adaptTo(Session.class);

        return session.getWorkspace().getQueryManager();
    }

    /**
     * Get Super Parent Path
     *
     * Get Path of Folder asset belongs to. This should match a Child Folder of the Folder Resource
     *
     * @param folderPath Folder Path
     * @param node       JCR Node
     * @return Folder Path
     * @throws Exception Node Exception
     */
    private String getSuperParentPath(String folderPath, Node node) throws Exception {
        String assetPath = node.getPath();

        if (node.getParent().getPath().equals(folderPath)) {
            return folderPath;
        }

        return folderPath + "/" + assetPath.substring(folderPath.length() + 1, assetPath.indexOf("/", folderPath.length() + 1));
    }
}
