/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
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
package org.deltav.minion.common;

import java.util.Collections;
import java.util.List;

import org.opennms.core.criteria.Criteria;
import org.opennms.netmgt.dao.api.DistPollerDao;
import org.opennms.netmgt.model.OnmsDistPoller;

/**
 * A database-free {@link DistPollerDao} for Minion deployments.
 *
 * <p>Minion does not have a local PostgreSQL instance, so this implementation
 * derives the poller identity solely from {@link SpringMinionIdentity}. All
 * mutating operations throw {@link UnsupportedOperationException}.</p>
 */
public class MinionDistPollerDao implements DistPollerDao {

    private final OnmsDistPoller self;

    public MinionDistPollerDao(SpringMinionIdentity identity) {
        this.self = new OnmsDistPoller();
        self.setId(identity.getId());
        self.setLabel(identity.getId());
        self.setLocation(identity.getLocation());
        self.setType("Minion");
    }

    @Override
    public OnmsDistPoller whoami() {
        return self;
    }

    // ------------------------------------------------------------------ //
    // Read-only / no-op operations                                         //
    // ------------------------------------------------------------------ //

    @Override
    public void lock() {
    }

    @Override
    public void initialize(Object obj) {
    }

    @Override
    public void flush() {
    }

    @Override
    public void clear() {
    }

    @Override
    public int countAll() {
        return 1;
    }

    @Override
    public List<OnmsDistPoller> findAll() {
        return Collections.singletonList(self);
    }

    @Override
    public List<OnmsDistPoller> findMatching(Criteria criteria) {
        return Collections.singletonList(self);
    }

    @Override
    public int countMatching(Criteria criteria) {
        return 1;
    }

    @Override
    public OnmsDistPoller get(String id) {
        return self.getId().equals(id) ? self : null;
    }

    @Override
    public OnmsDistPoller load(String id) {
        return get(id);
    }

    // ------------------------------------------------------------------ //
    // Mutating operations — not supported on Minion                       //
    // ------------------------------------------------------------------ //

    @Override
    public void delete(OnmsDistPoller entity) {
        throw new UnsupportedOperationException("MinionDistPollerDao is read-only");
    }

    @Override
    public void delete(String key) {
        throw new UnsupportedOperationException("MinionDistPollerDao is read-only");
    }

    @Override
    public String save(OnmsDistPoller entity) {
        throw new UnsupportedOperationException("MinionDistPollerDao is read-only");
    }

    @Override
    public void saveOrUpdate(OnmsDistPoller entity) {
        throw new UnsupportedOperationException("MinionDistPollerDao is read-only");
    }

    @Override
    public void update(OnmsDistPoller entity) {
        throw new UnsupportedOperationException("MinionDistPollerDao is read-only");
    }
}
