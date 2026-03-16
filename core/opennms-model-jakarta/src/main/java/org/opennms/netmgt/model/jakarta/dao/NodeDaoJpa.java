/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.netmgt.model.jakarta.dao;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opennms.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.model.OnmsCategory;
import org.opennms.netmgt.model.OnmsCriteria;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.SurveillanceStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link NodeDao}.
 *
 * <p>Alarmd only uses {@link #get(Integer)} to load node state when processing alarms.
 * All other methods from the {@link NodeDao} interface are unused by Alarmd and throw
 * {@link UnsupportedOperationException}.</p>
 *
 * <p>The deprecated {@link #findMatching(OnmsCriteria)} and {@link #countMatching(OnmsCriteria)}
 * methods from {@link org.opennms.netmgt.dao.api.LegacyOnmsDao} also throw
 * {@link UnsupportedOperationException}.</p>
 */
@Repository
@Transactional
public class NodeDaoJpa extends AbstractDaoJpa<OnmsNode, Integer> implements NodeDao {

    public NodeDaoJpa() {
        super(OnmsNode.class);
    }

    // ---- LegacyOnmsDao methods ----

    @Override
    public List<OnmsNode> findMatching(OnmsCriteria criteria) {
        throw new UnsupportedOperationException(
                "findMatching(OnmsCriteria) is not supported in NodeDaoJpa — use HQL queries");
    }

    @Override
    public int countMatching(OnmsCriteria onmsCrit) {
        throw new UnsupportedOperationException(
                "countMatching(OnmsCriteria) is not supported in NodeDaoJpa — use HQL queries");
    }

    // ---- NodeDao methods — not used by Alarmd core ----

    @Override
    public OnmsNode get(String lookupCriteria) {
        throw new UnsupportedOperationException("get(String) is not used by Alarmd");
    }

    @Override
    public Map<Integer, String> getAllLabelsById() {
        throw new UnsupportedOperationException("getAllLabelsById() is not used by Alarmd");
    }

    @Override
    public String getLabelForId(Integer id) {
        throw new UnsupportedOperationException("getLabelForId() is not used by Alarmd");
    }

    @Override
    public String getLocationForId(Integer id) {
        throw new UnsupportedOperationException("getLocationForId() is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findByLabel(String label) {
        throw new UnsupportedOperationException("findByLabel() is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findByLabelForLocation(String label, String location) {
        throw new UnsupportedOperationException("findByLabelForLocation() is not used by Alarmd");
    }

    @Override
    public OnmsNode getHierarchy(Integer id) {
        throw new UnsupportedOperationException("getHierarchy() is not used by Alarmd");
    }

    @Override
    public Map<String, Integer> getForeignIdToNodeIdMap(String foreignSource) {
        throw new UnsupportedOperationException("getForeignIdToNodeIdMap() is not used by Alarmd");
    }

    @Override
    public Map<String, Set<String>> getForeignIdsPerForeignSourceMap() {
        throw new UnsupportedOperationException("getForeignIdsPerForeignSourceMap() is not used by Alarmd");
    }

    @Override
    public Set<String> getForeignIdsPerForeignSource(String foreignSource) {
        throw new UnsupportedOperationException("getForeignIdsPerForeignSource() is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findAllByVarCharAssetColumn(String columnName, String columnValue) {
        throw new UnsupportedOperationException("findAllByVarCharAssetColumn() is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findAllByVarCharAssetColumnCategoryList(String columnName, String columnValue,
            Collection<OnmsCategory> categories) {
        throw new UnsupportedOperationException("findAllByVarCharAssetColumnCategoryList() is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findByCategory(OnmsCategory category) {
        throw new UnsupportedOperationException("findByCategory() is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findAllByCategoryList(Collection<OnmsCategory> categories) {
        throw new UnsupportedOperationException("findAllByCategoryList() is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findAllByCategoryLists(Collection<OnmsCategory> rowCatNames,
            Collection<OnmsCategory> colCatNames) {
        throw new UnsupportedOperationException("findAllByCategoryLists() is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findByForeignSource(String foreignSource) {
        throw new UnsupportedOperationException("findByForeignSource() is not used by Alarmd");
    }

    @Override
    public OnmsNode findByForeignId(String foreignSource, String foreignId) {
        throw new UnsupportedOperationException("findByForeignId(source, id) is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findByForeignId(String foreignId) {
        throw new UnsupportedOperationException("findByForeignId(id) is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findByForeignIdForLocation(String foreignId, String location) {
        throw new UnsupportedOperationException("findByForeignIdForLocation() is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findByIpAddressAndService(InetAddress ipAddress, String serviceName) {
        throw new UnsupportedOperationException("findByIpAddressAndService() is not used by Alarmd");
    }

    @Override
    public int getNodeCountForForeignSource(String groupName) {
        throw new UnsupportedOperationException("getNodeCountForForeignSource() is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findAllProvisionedNodes() {
        throw new UnsupportedOperationException("findAllProvisionedNodes() is not used by Alarmd");
    }

    @Override
    public List<OnmsIpInterface> findObsoleteIpInterfaces(Integer nodeId, Date scanStamp) {
        throw new UnsupportedOperationException("findObsoleteIpInterfaces() is not used by Alarmd");
    }

    @Override
    public void deleteObsoleteInterfaces(Integer nodeId, Date scanStamp) {
        throw new UnsupportedOperationException("deleteObsoleteInterfaces() is not used by Alarmd");
    }

    @Override
    public void updateNodeScanStamp(Integer nodeId, Date scanStamp) {
        throw new UnsupportedOperationException("updateNodeScanStamp() is not used by Alarmd");
    }

    @Override
    public Collection<Integer> getNodeIds() {
        throw new UnsupportedOperationException("getNodeIds() is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findByForeignSourceAndIpAddress(String foreignSource, String ipAddress) {
        throw new UnsupportedOperationException("findByForeignSourceAndIpAddress() is not used by Alarmd");
    }

    @Override
    public Map<String, Long> getNumberOfNodesBySysOid() {
        throw new UnsupportedOperationException("getNumberOfNodesBySysOid() is not used by Alarmd");
    }

    @Override
    public SurveillanceStatus findSurveillanceStatusByCategoryLists(Collection<OnmsCategory> rowCategories,
            Collection<OnmsCategory> columnCategories) {
        throw new UnsupportedOperationException("findSurveillanceStatusByCategoryLists() is not used by Alarmd");
    }

    @Override
    public Integer getNextNodeId(Integer nodeId) {
        throw new UnsupportedOperationException("getNextNodeId() is not used by Alarmd");
    }

    @Override
    public Integer getPreviousNodeId(Integer nodeId) {
        throw new UnsupportedOperationException("getPreviousNodeId() is not used by Alarmd");
    }

    @Override
    public void markHavingFlows(Collection<Integer> ingressIds, Collection<Integer> egressIds) {
        throw new UnsupportedOperationException("markHavingFlows() is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findAllHavingFlows() {
        throw new UnsupportedOperationException("findAllHavingFlows() is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findAllHavingIngressFlows() {
        throw new UnsupportedOperationException("findAllHavingIngressFlows() is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findAllHavingEgressFlows() {
        throw new UnsupportedOperationException("findAllHavingEgressFlows() is not used by Alarmd");
    }

    @Override
    public OnmsNode getDefaultFocusPoint() {
        throw new UnsupportedOperationException("getDefaultFocusPoint() is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findNodeWithMetaData(String context, String key, String value,
            boolean matchEnumeration) {
        throw new UnsupportedOperationException("findNodeWithMetaData() is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findBySysNameOfLldpLinksOfNode(int nodeId) {
        throw new UnsupportedOperationException("findBySysNameOfLldpLinksOfNode() is not used by Alarmd");
    }
}
