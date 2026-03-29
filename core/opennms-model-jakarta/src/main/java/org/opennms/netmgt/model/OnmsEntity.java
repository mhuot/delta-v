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

/**
 * Abstract base for OpenNMS entity classes.
 *
 * <p>The legacy version includes {@code visit(EntityVisitor)} for the visitor
 * pattern used by provisioning traversal.  The Jakarta version intentionally
 * drops that method to avoid a circular dependency on opennms-model (which
 * owns EntityVisitor and the visitor implementations).  Spring Boot daemons
 * use DTO mapping and direct property access instead.</p>
 */
public abstract class OnmsEntity {

    /**
     * Returns {@code true} when a new value differs from the existing one.
     * Convenience for update-detection in entity merge logic.
     */
    protected static boolean hasNewValue(final Object newVal, final Object existingVal) {
        return newVal != null && !newVal.equals(existingVal);
    }
}
