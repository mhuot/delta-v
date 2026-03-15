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

import org.opennms.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.dao.api.DistPollerDao;
import org.opennms.netmgt.model.OnmsDistPoller;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link DistPollerDao}.
 *
 * <p>{@link #whoami()} returns the {@link OnmsDistPoller} representing the local OpenNMS system.
 * {@link OnmsDistPoller} uses {@code @DiscriminatorValue("OpenNMS")}, so any JPQL query against
 * {@code OnmsDistPoller} is already filtered to rows whose {@code type} column equals
 * {@code "OpenNMS"}. This mirrors the logic in {@code DistPollerDaoHibernate}.</p>
 */
@Repository
@Transactional
public class DistPollerDaoJpa extends AbstractDaoJpa<OnmsDistPoller, String> implements DistPollerDao {

    public DistPollerDaoJpa() {
        super(OnmsDistPoller.class);
    }

    /**
     * Returns the local OpenNMS monitoring system identity.
     *
     * <p>There is normally exactly one {@link OnmsDistPoller} row — the local system.
     * Returns the first result, or {@code null} if the table has not been initialised yet.</p>
     *
     * @return the local {@link OnmsDistPoller}, or {@code null} if not yet provisioned
     */
    @Override
    public OnmsDistPoller whoami() {
        return findUnique("SELECT d FROM OnmsDistPoller d");
    }
}
