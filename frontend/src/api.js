export const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:18080'

export function decodeJwtRole(token) {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
    const json = JSON.parse(atob(normalized))
    return json.role || null
  } catch {
    return null
  }
}

export async function apiRequest(path, { method = 'GET', token, body } = {}) {
  const headers = {
    'Content-Type': 'application/json'
  }

  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${API_URL}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined
  })

  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('application/json')
    ? await response.json()
    : await response.text()

  if (!response.ok) {
    const message = typeof payload === 'object' && payload?.message ? payload.message : `HTTP ${response.status}`
    throw new Error(message)
  }

  return payload
}

export const api = {
  register: (email, password) => apiRequest('/auth/register', { method: 'POST', body: { email, password } }),
  login: (email, password) => apiRequest('/auth/login', { method: 'POST', body: { email, password } }),
  products: () => apiRequest('/products'),
  createOrder: (token, items) => apiRequest('/orders', { method: 'POST', token, body: { items } }),
  orders: (token) => apiRequest('/orders', { token }),
  cancelOrder: (token, id) => apiRequest(`/orders/${id}`, { method: 'DELETE', token }),
  createProduct: (token, body) => apiRequest('/products', { method: 'POST', token, body }),
  updateProduct: (token, id, body) => apiRequest(`/products/${id}`, { method: 'PUT', token, body }),
  deleteProduct: (token, id) => apiRequest(`/products/${id}`, { method: 'DELETE', token }),
  stats: (token) => apiRequest('/stats/orders', { token })
}
