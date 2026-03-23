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
package org.opennms.netmgt.enlinkd.model.jakarta.dao;

import java.util.List;

import org.opennms.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.enlinkd.model.jakarta.UserDefinedLink;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of UserDefinedLink data access.
 */
@Repository
@Transactional
public class UserDefinedLinkDaoJpa extends AbstractDaoJpa<UserDefinedLink, Integer> {

    public UserDefinedLinkDaoJpa() {
        super(UserDefinedLink.class);
    }

    public List<UserDefinedLink> getOutLinks(int nodeIdA) {
        return find("SELECT u FROM UserDefinedLink u WHERE u.nodeIdA = ?1", nodeIdA);
    }

    public List<UserDefinedLink> getInLinks(int nodeIdZ) {
        return find("SELECT u FROM UserDefinedLink u WHERE u.nodeIdZ = ?1", nodeIdZ);
    }

    public List<UserDefinedLink> getLinksWithLabel(String label) {
        return find("SELECT u FROM UserDefinedLink u WHERE u.linkLabel = ?1", label);
    }
}
