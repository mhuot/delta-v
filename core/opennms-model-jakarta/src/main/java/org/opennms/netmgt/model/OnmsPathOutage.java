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
package org.opennms.netmgt.model;

import java.io.Serializable;
import java.net.InetAddress;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import org.opennms.netmgt.model.jakarta.converter.InetAddressConverter;

/**
 * <p>OnmsPathOutage class</p>
 *
 * @author <a href="ryan@mail1.opennms.com"> Ryan Lambeth </a>
 */
@Entity
@Table(name="pathoutage")
public class OnmsPathOutage implements Serializable {

    private static final long serialVersionUID = 2180867754702562743L;

    private int m_nodeId;
    private InetAddress m_criticalPathIp;
    private String m_criticalPathServiceName;
    private OnmsNode m_node;

    /**
     * <p>Constructor for OnmsPathOutage</p>
     */
    public OnmsPathOutage(OnmsNode node, InetAddress criticalPathIp, String criticalPathServiceName) {
        m_nodeId = node.getId();
        m_node = node;
        m_criticalPathIp = criticalPathIp;
        m_criticalPathServiceName = criticalPathServiceName;
    }

    public OnmsPathOutage() {
    }

    /**
     * The node this path outage information belongs to.
     *
     * @return a {@link org.opennms.netmgt.model.OnmsNode} object.
     */
    @OneToOne
    @PrimaryKeyJoinColumn
    @MapsId
    public OnmsNode getNode() {
        return m_node;
    }

    public void setNode(OnmsNode node) {
        m_node = node;
    }

    /**
     * The pathOutage table uses the node ID as its primary key.
     * In Hibernate 7, the legacy "foreign" GenericGenerator is replaced by @MapsId
     * on the @OneToOne association, which derives the ID from the related entity.
     */
    @Id
    @Column(name="nodeId")
    public int getNodeId() {
        return m_nodeId;
    }

    public void setNodeId(int id) {
        m_nodeId = id;
    }

    @Column(name="criticalpathip", nullable = false)
    @Convert(converter = InetAddressConverter.class)
    public InetAddress getCriticalPathIp() {
        return m_criticalPathIp;
    }

    public void setCriticalPathIp(InetAddress criticalPathIp) {
        m_criticalPathIp = criticalPathIp;
    }

    @Column(name="criticalpathservicename")
    public String getCriticalPathServiceName() {
        return m_criticalPathServiceName;
    }

    public void setCriticalPathServiceName(String criticalPathServiceName) {
        m_criticalPathServiceName = criticalPathServiceName;
    }
}
