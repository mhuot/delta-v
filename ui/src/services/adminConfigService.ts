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

import { rest } from '@/services/axiosInstances'

const getConfigNames = async () => {
  try {
    const response = await rest.get('/cm/')
    if (response.status === 200) {
      return response.data
    }
  } catch (err) {
    console.error('issue with getConfigNames api', err)
  }
}

const getConfigSchema = async (configName: string) => {
  try {
    const response = await rest.get(`/cm/schema/${configName}`, {
      headers: { Accept: 'application/json' }
    })
    if (response.status === 200) {
      return response.data
    }
  } catch (err) {
    console.error('issue with getConfigSchema api', err)
  }
}

const getConfigIds = async (configName: string) => {
  try {
    const response = await rest.get(`/cm/${configName}`)
    if (response.status === 200) {
      return response.data
    }
  } catch (err) {
    console.error('issue with getConfigIds api', err)
  }
}

const getConfig = async (configName: string, configId: string) => {
  try {
    const response = await rest.get(`/cm/${configName}/${configId}`)
    if (response.status === 200) {
      return response.data
    }
  } catch (err) {
    console.error('issue with getConfig api', err)
  }
}

const createConfig = async (configName: string, configId: string, data: Record<string, any>) => {
  try {
    const response = await rest.post(`/cm/${configName}/${configId}`, data)
    if (response.status === 200 || response.status === 201) {
      return response.data
    }
  } catch (err) {
    console.error('issue with createConfig api', err)
  }
}

const updateConfig = async (configName: string, configId: string, data: Record<string, any>) => {
  try {
    const response = await rest.put(`/cm/${configName}/${configId}`, data)
    if (response.status === 200) {
      return response.data
    }
  } catch (err) {
    console.error('issue with updateConfig api', err)
  }
}

const deleteConfig = async (configName: string, configId: string) => {
  try {
    const response = await rest.delete(`/cm/${configName}/${configId}`)
    if (response.status === 200 || response.status === 204) {
      return response.data
    }
  } catch (err) {
    console.error('issue with deleteConfig api', err)
  }
}

export {
  getConfigNames,
  getConfigSchema,
  getConfigIds,
  getConfig,
  createConfig,
  updateConfig,
  deleteConfig
}
