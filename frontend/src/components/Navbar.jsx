import { NavLink } from 'react-router-dom'

export default function Navbar() {
  return (
    <nav className="navbar">
      <div className="navbar-brand">Blockchain Demo</div>
      <div className="navbar-links">
        <NavLink to="/" end className={({ isActive }) => isActive ? 'active' : ''}>
          Accounts
        </NavLink>
        <NavLink to="/create" className={({ isActive }) => isActive ? 'active' : ''}>
          Create
        </NavLink>
        <NavLink to="/transaction" className={({ isActive }) => isActive ? 'active' : ''}>
          Send TX
        </NavLink>
        <NavLink to="/blocks" className={({ isActive }) => isActive ? 'active' : ''}>
          Blocks
        </NavLink>
        <NavLink to="/transactions" className={({ isActive }) => isActive ? 'active' : ''}>
          Transactions
        </NavLink>
        <NavLink to="/network" className={({ isActive }) => isActive ? 'active' : ''}>
          Network
        </NavLink>
        <NavLink to="/experiments" className={({ isActive }) => isActive ? 'active' : ''}>
          Experiments
        </NavLink>
      </div>
    </nav>
  )
}
