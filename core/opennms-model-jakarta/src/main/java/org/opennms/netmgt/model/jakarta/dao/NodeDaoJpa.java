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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.deltav.core.daemon.common.AbstractDaoJpa;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.model.OnmsCategory;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.SurveillanceStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link NodeDao}.
 *
 * <p>Implements methods needed by Alarmd ({@link #get(Integer)}) and Provisiond
 * (hierarchy loading, foreign source/ID lookups, scan stamp management, etc.).
 * Methods not used by either daemon throw {@link UnsupportedOperationException}.</p>
 *
 */
@Repository
@Transactional
public class NodeDaoJpa extends AbstractDaoJpa<OnmsNode, Integer> implements NodeDao {

    public NodeDaoJpa() {
        super(OnmsNode.class);
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
        return find("from OnmsNode n where n.label = ?1", label);
    }

    @Override
    public List<OnmsNode> findByLabelForLocation(String label, String location) {
        throw new UnsupportedOperationException("findByLabelForLocation() is not used by Alarmd");
    }

    @Override
    public OnmsNode getHierarchy(Integer id) {
        OnmsNode node = get(id);
        if (node != null) {
            initialize(node.getIpInterfaces());
            initialize(node.getSnmpInterfaces());
            initialize(node.getCategories());
            for (OnmsIpInterface iface : node.getIpInterfaces()) {
                initialize(iface.getSnmpInterface());
            }
        }
        return node;
    }

    @Override
    public Map<String, Integer> getForeignIdToNodeIdMap(String foreignSource) {
        List<Object[]> rows = findObjects(Object[].class,
                "select n.foreignId, n.id from OnmsNode n where n.foreignSource = ?1", foreignSource);
        Map<String, Integer> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], (Integer) row[1]);
        }
        return map;
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
        return findUnique("from OnmsNode n where n.foreignSource = ?1 and n.foreignId = ?2",
                foreignSource, foreignId);
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
        return find("from OnmsNode n where n.foreignSource is not null");
    }

    @Override
    public List<OnmsIpInterface> findObsoleteIpInterfaces(Integer nodeId, Date scanStamp) {
        return findObjects(OnmsIpInterface.class,
                "from OnmsIpInterface iface where iface.node.id = ?1 " +
                "and iface.snmpPrimary != 'P' " +
                "and (iface.ipLastCapsdPoll is null or iface.ipLastCapsdPoll < ?2)",
                nodeId, scanStamp);
    }

    @Override
    public void deleteObsoleteInterfaces(Integer nodeId, Date scanStamp) {
        // Hibernate 7 bulk DELETE cannot resolve implicit joins through associations
        // when cascading to @ManyToMany join tables (application_service_map).
        // Use ms.id IN (subquery) so the cascade DELETE only sees a trivial WHERE clause.
        entityManager().createQuery(
                "delete from OnmsMonitoredService ms where ms.id in " +
                "(select ms2.id from OnmsMonitoredService ms2 " +
                "where ms2.ipInterface.node.id = ?1 " +
                "and ms2.ipInterface.snmpPrimary != 'P' " +
                "and (ms2.ipInterface.ipLastCapsdPoll is null or ms2.ipInterface.ipLastCapsdPoll < ?2))")
                .setParameter(1, nodeId)
                .setParameter(2, scanStamp)
                .executeUpdate();
        // Then delete the IP interfaces
        entityManager().createQuery(
                "delete from OnmsIpInterface iface where iface.node.id = ?1 " +
                "and iface.snmpPrimary != 'P' " +
                "and (iface.ipLastCapsdPoll is null or iface.ipLastCapsdPoll < ?2)")
                .setParameter(1, nodeId)
                .setParameter(2, scanStamp)
                .executeUpdate();
        // Finally delete the SNMP interfaces
        entityManager().createQuery(
                "delete from OnmsSnmpInterface snmp where snmp.node.id = ?1 " +
                "and (snmp.lastCapsdPoll is null or snmp.lastCapsdPoll < ?2)")
                .setParameter(1, nodeId)
                .setParameter(2, scanStamp)
                .executeUpdate();
    }

    @Override
    public void updateNodeScanStamp(Integer nodeId, Date scanStamp) {
        OnmsNode node = get(nodeId);
        if (node != null) {
            node.setLastCapsdPoll(scanStamp);
            update(node);
        }
    }

    @Override
    public Collection<Integer> getNodeIds() {
        throw new UnsupportedOperationException("getNodeIds() is not used by Alarmd");
    }

    @Override
    public List<OnmsNode> findByForeignSourceAndIpAddress(String foreignSource, String ipAddress) {
        return find("select distinct n from OnmsNode n join n.ipInterfaces iface " +
                "where n.foreignSource = ?1 and iface.ipAddress = ?2",
                foreignSource, InetAddressUtils.addr(ipAddress));
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
        var results = findAll();
        return results.isEmpty() ? null : results.get(0);
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
