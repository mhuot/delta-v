/*
 * Copyright (C) 1999-2024 The OpenNMS Group, Inc.
 * Copyright (C) 2026 BeaconStrategists, Inc. (Modifications)
 *
 * This file is part of OpenNMS(R) / Delta-V.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
    @SequenceGenerator(name = "memoSequence", sequenceName = "memoNxtId", allocationSize = 1)
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
