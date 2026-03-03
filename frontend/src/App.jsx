import { useEffect, useMemo, useState } from 'react'
import { API_URL, api, decodeJwtRole } from './api'

function formatMoney(value) {
  const amount = Number(value || 0)
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(amount)
}

function initialProductForm() {
  return {
    name: '',
    description: '',
    price: '',
    stock: ''
  }
}

function shortId(value) {
  return `${value.slice(0, 8)}...`
}

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem('store_token') || '')
  const [role, setRole] = useState(() => decodeJwtRole(localStorage.getItem('store_token') || ''))
  const [mode, setMode] = useState('login')

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const [products, setProducts] = useState([])
  const [orders, setOrders] = useState([])
  const [stats, setStats] = useState(null)
  const [cart, setCart] = useState({})

  const [adminForm, setAdminForm] = useState(initialProductForm)
  const [editingProductId, setEditingProductId] = useState(null)

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const isAuthorized = Boolean(token)
  const isAdmin = role === 'ADMIN'

  const productById = useMemo(() => {
    return new Map(products.map((product) => [product.id, product]))
  }, [products])

  const cartItems = useMemo(() => {
    return Object.entries(cart)
      .map(([productId, rawQuantity]) => {
        const quantity = Number(rawQuantity) || 0
        const maxStock = productById.get(productId)?.stock ?? 0
        const safeQuantity = Math.max(0, Math.min(quantity, maxStock))
        return { productId, quantity: safeQuantity }
      })
      .filter((item) => item.quantity > 0)
  }, [cart, productById])

  const cartTotal = useMemo(() => {
    return cartItems.reduce((acc, item) => {
      const product = productById.get(item.productId)
      if (!product) return acc
      return acc + Number(product.price) * item.quantity
    }, 0)
  }, [cartItems, productById])

  async function loadProducts() {
    const data = await api.products()
    setProducts(data)
  }

  async function loadOrders() {
    if (!token) return
    const data = await api.orders(token)
    setOrders(data)
  }

  async function loadStats() {
    if (!token || !isAdmin) return
    const data = await api.stats(token)
    setStats(data)
  }

  useEffect(() => {
    loadProducts().catch((e) => setError(e.message))
  }, [])

  useEffect(() => {
    if (!token) {
      setOrders([])
      setStats(null)
      return
    }

    Promise.all([loadOrders(), isAdmin ? loadStats() : Promise.resolve()]).catch((e) => setError(e.message))
  }, [token, role])

  function saveToken(newToken) {
    const newRole = decodeJwtRole(newToken)
    localStorage.setItem('store_token', newToken)
    setToken(newToken)
    setRole(newRole)
  }

  function logout() {
    localStorage.removeItem('store_token')
    setToken('')
    setRole(null)
    setNotice('Signed out')
  }

  async function handleAuthSubmit(event) {
    event.preventDefault()
    setBusy(true)
    setError('')
    setNotice('')

    try {
      const response = mode === 'login' ? await api.login(email, password) : await api.register(email, password)
      saveToken(response.token)
      setNotice(mode === 'login' ? 'Welcome back' : 'Registration successful')
      setPassword('')
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  function setProductQuantity(productId, value, maxStock) {
    const quantity = Math.max(0, Math.min(Number(value) || 0, Number(maxStock) || 0))
    setCart((prev) => ({
      ...prev,
      [productId]: quantity
    }))
  }

  async function createOrder() {
    if (!isAuthorized) {
      setError('Please login first')
      return
    }
    if (cartItems.length === 0) {
      setError('Add at least one product to order')
      return
    }

    setBusy(true)
    setError('')
    setNotice('')

    try {
      await api.createOrder(token, cartItems)
      setCart({})
      setNotice('Order created')
      await Promise.all([loadProducts(), loadOrders()])
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  async function cancelOrder(orderId) {
    setBusy(true)
    setError('')
    setNotice('')

    try {
      await api.cancelOrder(token, orderId)
      setNotice('Order cancelled')
      await loadOrders()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  function startEditProduct(product) {
    setEditingProductId(product.id)
    setAdminForm({
      name: product.name,
      description: product.description,
      price: String(product.price),
      stock: String(product.stock)
    })
  }

  async function submitProduct(event) {
    event.preventDefault()
    setBusy(true)
    setError('')
    setNotice('')

    const payload = {
      name: adminForm.name,
      description: adminForm.description,
      price: String(adminForm.price),
      stock: Number(adminForm.stock)
    }

    try {
      if (editingProductId) {
        await api.updateProduct(token, editingProductId, payload)
        setNotice('Product updated')
      } else {
        await api.createProduct(token, payload)
        setNotice('Product created')
      }
      setEditingProductId(null)
      setAdminForm(initialProductForm())
      await Promise.all([loadProducts(), loadStats()])
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  async function removeProduct(productId) {
    setBusy(true)
    setError('')
    setNotice('')

    try {
      await api.deleteProduct(token, productId)
      setNotice('Product deleted')
      await Promise.all([loadProducts(), loadStats()])
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <header className="topbar">
        <div>
          <h1>Store Abstract</h1>
          <p>Ktor + PostgreSQL + Redis + RabbitMQ demo UI</p>
        </div>
        <div className="topbar-actions">
          <a href={API_URL + '/swagger'} target="_blank" rel="noreferrer">Swagger</a>
          <a href={API_URL + '/health'} target="_blank" rel="noreferrer">Health</a>
          {isAuthorized ? (
            <button className="ghost" onClick={logout}>Logout ({role || 'USER'})</button>
          ) : null}
        </div>
      </header>

      {error ? <div className="alert error">{error}</div> : null}
      {notice ? <div className="alert ok">{notice}</div> : null}

      {!isAuthorized ? (
        <section className="panel auth-panel">
          <div className="tab-row">
            <button className={mode === 'login' ? 'tab active' : 'tab'} onClick={() => setMode('login')}>Login</button>
            <button className={mode === 'register' ? 'tab active' : 'tab'} onClick={() => setMode('register')}>Register</button>
          </div>
          <form onSubmit={handleAuthSubmit} className="form-grid">
            <label>
              Email
              <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" required />
            </label>
            <label>
              Password
              <input value={password} onChange={(e) => setPassword(e.target.value)} type="password" minLength={6} required />
            </label>
            <button disabled={busy}>{busy ? 'Please wait...' : mode === 'login' ? 'Sign in' : 'Create account'}</button>
          </form>
        </section>
      ) : null}

      <section className="panel">
        <div className="section-head">
          <h2>Products</h2>
          <span>{products.length} items</span>
        </div>
        <p className="muted helper">Qty means how many units of this product you want to add to your next order.</p>
        <div className="product-grid">
          {products.map((product) => (
            <article className="card" key={product.id}>
              <h3>{product.name}</h3>
              <p>{product.description}</p>
              <div className="card-meta">
                <strong>{formatMoney(product.price)}</strong>
                <span>Stock: {product.stock}</span>
              </div>
              {isAuthorized ? (
                <label className="qty">
                  Qty
                  <input
                    type="number"
                    min={0}
                    max={product.stock}
                    value={cart[product.id] ?? 0}
                    onChange={(e) => setProductQuantity(product.id, e.target.value, product.stock)}
                  />
                </label>
              ) : null}
              {isAdmin ? (
                <div className="actions-row">
                  <button type="button" className="ghost" onClick={() => startEditProduct(product)}>Edit</button>
                  <button type="button" className="danger" onClick={() => removeProduct(product.id)}>Delete</button>
                </div>
              ) : null}
            </article>
          ))}
        </div>
      </section>

      {isAuthorized ? (
        <section className="panel split">
          <div>
            <div className="section-head">
              <h2>Create order</h2>
              <span>{cartItems.length} selected</span>
            </div>
            <p className="muted">Total: {formatMoney(cartTotal)}</p>
            <button onClick={createOrder} disabled={busy || cartItems.length === 0}>Create order</button>
          </div>

          <div>
            <div className="section-head">
              <h2>My orders</h2>
              <button className="ghost" onClick={() => loadOrders().catch((e) => setError(e.message))}>Refresh</button>
            </div>
            <div className="orders">
              {orders.map((order) => (
                <article className="order-card" key={order.id}>
                  <div className="order-head">
                    <strong title={order.id}>Order {shortId(order.id)}</strong>
                    <span className={order.status === 'CANCELLED' ? 'status cancelled' : 'status created'}>{order.status}</span>
                  </div>
                  <p>{formatMoney(order.total)} - {new Date(order.createdAt).toLocaleString()}</p>
                  <ul className="order-lines">
                    {order.items.map((item) => {
                      const product = productById.get(item.productId)
                      const productName = item.productName || product?.name || `Product ${shortId(item.productId)}`
                      return (
                        <li key={item.id}>
                          <span>{productName}</span>
                          <span>x{item.quantity}</span>
                          <span>{formatMoney(item.priceAtPurchase)}</span>
                        </li>
                      )
                    })}
                  </ul>
                  {order.status === 'CREATED' ? (
                    <button className="ghost" onClick={() => cancelOrder(order.id)} disabled={busy}>Cancel</button>
                  ) : null}
                </article>
              ))}
            </div>
          </div>
        </section>
      ) : null}

      {isAuthorized && isAdmin ? (
        <>
          <section className="panel">
            <div className="section-head">
              <h2>Admin: order stats</h2>
              <button className="ghost" onClick={() => loadStats().catch((e) => setError(e.message))}>Refresh</button>
            </div>
            {stats ? (
              <div className="stats-grid">
                <div><span>Total</span><strong>{stats.totalOrders}</strong></div>
                <div><span>Created</span><strong>{stats.createdOrders}</strong></div>
                <div><span>Cancelled</span><strong>{stats.cancelledOrders}</strong></div>
                <div><span>Revenue</span><strong>{formatMoney(stats.totalRevenue)}</strong></div>
              </div>
            ) : (
              <p className="muted">No stats loaded yet.</p>
            )}
          </section>

          <section className="panel">
            <div className="section-head">
              <h2>Admin: products</h2>
              <span>{editingProductId ? 'Edit mode' : 'Create mode'}</span>
            </div>
            <form onSubmit={submitProduct} className="form-grid">
              <label>
                Name
                <input
                  value={adminForm.name}
                  onChange={(e) => setAdminForm((prev) => ({ ...prev, name: e.target.value }))}
                  required
                />
              </label>
              <label>
                Description
                <textarea
                  value={adminForm.description}
                  onChange={(e) => setAdminForm((prev) => ({ ...prev, description: e.target.value }))}
                  rows={4}
                  required
                />
              </label>
              <label>
                Price
                <input
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={adminForm.price}
                  onChange={(e) => setAdminForm((prev) => ({ ...prev, price: e.target.value }))}
                  required
                />
              </label>
              <label>
                Stock
                <input
                  type="number"
                  min="0"
                  step="1"
                  value={adminForm.stock}
                  onChange={(e) => setAdminForm((prev) => ({ ...prev, stock: e.target.value }))}
                  required
                />
              </label>
              <div className="actions-row">
                <button disabled={busy}>{editingProductId ? 'Update product' : 'Create product'}</button>
                {editingProductId ? (
                  <button
                    type="button"
                    className="ghost"
                    onClick={() => {
                      setEditingProductId(null)
                      setAdminForm(initialProductForm())
                    }}
                  >
                    Cancel edit
                  </button>
                ) : null}
              </div>
            </form>
          </section>
        </>
      ) : null}
    </div>
  )
}
