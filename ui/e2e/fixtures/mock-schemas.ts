///
/// Licensed to The OpenNMS Group, Inc (TOG) under one or more
/// contributor license agreements.  See the LICENSE.md file
/// distributed with this work for additional information
/// regarding copyright ownership.
///
/// TOG licenses this file to You under the GNU Affero General
/// Public License Version 3 (the "License") or (at your option)
/// any later version.  You may not use this file except in
/// compliance with the License.  You may obtain a copy of the
/// License at:
///
///      https://www.gnu.org/licenses/agpl-3.0.txt
///
/// Unless required by applicable law or agreed to in writing,
/// software distributed under the License is distributed on an
/// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
/// either express or implied.  See the License for the specific
/// language governing permissions and limitations under the
/// License.
///

export const mockProvisiondSchema = {
  type: 'object',
  properties: {
    importThreads: {
      type: 'integer',
      description: 'Number of threads for importing',
      default: 8,
      minimum: 1,
      maximum: 128
    },
    scanThreads: {
      type: 'integer',
      description: 'Number of threads for scanning',
      default: 10,
      minimum: 1,
      maximum: 128
    },
    rescanThreads: {
      type: 'integer',
      description: 'Number of threads for rescanning',
      default: 10,
      minimum: 1,
      maximum: 128
    },
    writeThreads: {
      type: 'integer',
      description: 'Number of threads for writing',
      default: 8,
      minimum: 1,
      maximum: 128
    },
    importSchedule: {
      type: 'string',
      description: 'Cron schedule for imports',
      default: '0 0 0 * * ? *'
    },
    enableDiscovery: {
      type: 'boolean',
      description: 'Enable discovery integration',
      default: true
    }
  },
  required: ['importThreads', 'scanThreads']
}

export const mockCollectdSchema = {
  type: 'object',
  properties: {
    threads: {
      type: 'integer',
      description: 'Number of collector threads',
      default: 50,
      minimum: 1
    },
    collectors: {
      type: 'array',
      description: 'List of configured collectors',
      items: {
        type: 'object',
        properties: {
          service: { type: 'string', description: 'Service name' },
          className: { type: 'string', description: 'Collector class' },
          parameters: {
            type: 'object',
            description: 'Collector parameters',
            properties: {}
          }
        },
        required: ['service', 'className']
      }
    }
  },
  required: ['threads']
}

export const mockEventdSchema = {
  type: 'object',
  properties: {
    tcpAddress: {
      type: 'string',
      description: 'TCP listen address',
      default: '127.0.0.1'
    },
    tcpPort: {
      type: 'integer',
      description: 'TCP listen port',
      default: 5817,
      minimum: 1,
      maximum: 65535
    },
    udpAddress: {
      type: 'string',
      description: 'UDP listen address',
      default: '127.0.0.1'
    },
    udpPort: {
      type: 'integer',
      description: 'UDP listen port',
      default: 5817,
      minimum: 1,
      maximum: 65535
    },
    receivers: {
      type: 'integer',
      description: 'Number of receiver threads',
      default: 5,
      minimum: 1
    },
    logEventSummaries: {
      type: 'boolean',
      description: 'Log event summaries',
      default: true
    }
  }
}
