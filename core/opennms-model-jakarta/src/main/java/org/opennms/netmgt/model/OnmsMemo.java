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
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * <p>Generic memo for any element inside OpenNMS</p>
 *
 * @author <a href="mailto:Markus@OpenNMS.com">Markus Neumann</a>
 */
@Entity
@Table(name = "memos")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name="type", discriminatorType= DiscriminatorType.STRING)
@DiscriminatorValue(value="Memo")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class OnmsMemo implements Serializable {

    private static final long serialVersionUID = 7272348439687562161L;

    @Id
    @Column(name = "id", nullable = false)
    @SequenceGenerator(name = "memoSequence", sequenceName = "memoNxtId")
    @GeneratedValue(generator = "memoSequence")
    private Integer m_id;

    @Column(name = "body")
    private String m_body;

    @Column(name = "author")
    private String m_author;

    @Column(name = "updated")
    @Temporal(TemporalType.TIMESTAMP)
    private Date m_updated;

    @Column(name = "created")
    @Temporal(TemporalType.TIMESTAMP)
    private Date m_created;

    @PreUpdate
    private void preUpdate() {
        m_updated = new Date();
    }

    @PrePersist
    private void prePersist() {
        m_created = new Date();
    }

    public String getBody() {
        return m_body;
    }

    public void setBody(String body) {
        this.m_body = body;
    }

    public Date getCreated() {
        return m_created;
    }

    public Integer getId() {
        return m_id;
    }

    public void setId(final Integer id) {
        m_id = id;
    }

    public Date getUpdated() {
        return m_updated;
    }

    public void setCreated(Date created) {
        this.m_created = created;
    }

    public void setUpdated(Date updated) {
        this.m_updated = updated;
    }

    public String getAuthor() {
        return m_author;
    }

    public void setAuthor(String author) {
        this.m_author = author;
    }
}
