import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
    LayoutDashboard,
    ClipboardList,
    Receipt,
    UserRound,
    History,
    Building2,
    Users,
    Wallet,
    Repeat,
    Landmark,
    Tag,
    FlaskConical,
    LogOut,
} from 'lucide-react';
import { useAuthStore } from '../../stores/authStore';
import { useTheme, ROLE_SCOPE_LABEL } from '../../context/ThemeContext';
import '../../styles/tokens.css';

interface SidebarItem {
    icon: React.ReactNode;
    label: string;
    path: string;
}

export const Sidebar: React.FC = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const { user, logout } = useAuthStore();
    const { role } = useTheme();

    const getNavItems = (): SidebarItem[] => {
        switch (role) {
            case 'DISTRICT_ADMIN':
                return [
                    { icon: <LayoutDashboard size={18} />, label: 'Dashboard', path: '/admin/dashboard' },
                    { icon: <Building2 size={18} />, label: 'Facilities', path: '/admin/facilities' },
                    { icon: <Users size={18} />, label: 'Users', path: '/admin/users' },
                    { icon: <Wallet size={18} />, label: 'Financial', path: '/admin/financial' },
                    { icon: <Repeat size={18} />, label: 'Transfers', path: '/admin/transfers' },
                    { icon: <Landmark size={18} />, label: 'Insurance', path: '/admin/insurance-providers' },
                    { icon: <Tag size={18} />, label: 'Pricing', path: '/admin/service-pricing' },
                ];
            case 'FACILITY_ADMIN':
                return [
                    { icon: <LayoutDashboard size={18} />, label: 'Dashboard', path: '/facility-admin/dashboard' },
                    { icon: <Users size={18} />, label: 'Staff', path: `/admin/facility/${user?.facilityId}/staff` },
                    { icon: <ClipboardList size={18} />, label: 'Departments', path: `/admin/facility/${user?.facilityId}/departments` },
                    { icon: <Wallet size={18} />, label: 'Financial', path: '/admin/financial' },
                ];
            case 'DOCTOR':
                return [
                    { icon: <ClipboardList size={18} />, label: 'Queue', path: '/doctor/dashboard' },
                ];
            case 'STAFF':
                return [
                    { icon: <ClipboardList size={18} />, label: 'Queue', path: '/staff/dashboard' },
                    { icon: <UserRound size={18} />, label: 'Patients', path: '/staff/patients' },
                    { icon: <Receipt size={18} />, label: 'Billing', path: '/staff/billing' },
                ];
            default: // PATIENT
                return [
                    { icon: <LayoutDashboard size={18} />, label: 'Dashboard', path: '/patient/dashboard' },
                    { icon: <ClipboardList size={18} />, label: 'Check In', path: '/patient/checkin' },
                    { icon: <History size={18} />, label: 'History', path: '/patient/history' },
                    { icon: <Receipt size={18} />, label: 'Billing', path: '/patient/billing' },
                    { icon: <UserRound size={18} />, label: 'Profile', path: '/patient/profile' },
                ];
        }
    };

    const navItems = getNavItems();
    const scopeLabel = ROLE_SCOPE_LABEL[role]; // only set for admin roles

    const handleLogout = async () => {
        await logout();
        navigate('/login');
    };

    const isActive = (path: string) => {
        return location.pathname === path || location.pathname.startsWith(path + '/');
    };

    return (
        <aside
            className="h-screen w-60 fixed left-0 top-0 flex flex-col"
            style={{ background: 'var(--accent-strong)', color: '#fff' }}
        >
            {/* Wordmark + scope */}
            <div className="flex items-center gap-3 px-5 py-5" style={{ borderBottom: '1px solid rgba(255,255,255,0.12)' }}>
                <FlaskConical size={22} />
                <div className="min-w-0">
                    <p className="text-base font-semibold leading-tight">Aura Care</p>
                    <p className="text-xs opacity-75 truncate">
                        {scopeLabel || role.replace('_', ' ').toLowerCase()}
                    </p>
                </div>
            </div>

            {/* Nav */}
            <nav className="flex-1 px-3 py-4 space-y-0.5 overflow-y-auto">
                {navItems.map((item) => (
                    <button
                        key={item.path}
                        onClick={() => navigate(item.path)}
                        className="w-full flex items-center gap-3 px-3 py-2.5 rounded-[var(--radius-md)] text-left text-sm font-medium transition-colors"
                        style={{
                            background: isActive(item.path) ? 'rgba(255,255,255,0.16)' : 'transparent'
                        }}
                    >
                        {item.icon}
                        {item.label}
                    </button>
                ))}
            </nav>

            {/* User + logout */}
            <div className="px-4 py-4 space-y-3" style={{ borderTop: '1px solid rgba(255,255,255,0.12)' }}>
                <div className="flex items-center gap-3">
                    <div
                        className="w-9 h-9 rounded-full flex items-center justify-center text-xs font-semibold shrink-0"
                        style={{ background: 'rgba(255,255,255,0.16)' }}
                    >
                        {user?.firstName?.charAt(0)}
                        {user?.lastName?.charAt(0)}
                    </div>
                    <div className="min-w-0">
                        <p className="text-sm font-medium truncate">
                            {user?.firstName} {user?.lastName}
                        </p>
                        <p className="text-xs opacity-70 truncate">{role.replace('_', ' ').toLowerCase()}</p>
                    </div>
                </div>
                <button
                    onClick={handleLogout}
                    className="w-full flex items-center justify-center gap-2 px-3 py-2 rounded-[var(--radius-md)] text-sm font-medium"
                    style={{ background: 'rgba(255,255,255,0.1)' }}
                >
                    <LogOut size={15} /> Log out
                </button>
            </div>
        </aside>
    );
};