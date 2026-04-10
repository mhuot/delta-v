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
package org.deltav.flows.enricher.protocol;

/**
 * Stub interface for Phase 1.5 Commit 3. Commit 4 (Task 8) will define the
 * real {@code process(TelemetryMessageLog)} method and four concrete
 * implementations wrapping horizon's {@code AbstractFlowAdapter} subclasses
 * (Netflow5, Netflow9, IPFIX, sFlow).
 *
 * <p>This stub exists so {@link org.deltav.flows.enricher.FlowEnrichmentFunction}
 * can be refactored to accept a dispatch map in Commit 3 without blocking on
 * Commit 4's per-protocol processor classes.
 */
public interface ProtocolMessageProcessor {
}
