// src/pages/staff/StaffDashboard.tsx

import React from 'react';
import { useNavigate, Link, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';
import { staffService } from '../../services/staff';
import toast from 'react-hot-toast';

interface DashboardStats {
    totalPatients: number;
    waitingPatients: number;
    inConsultation: number;
    completedToday: number;
}

interface Notification {
    id: string;
    message: string;
    read: boolean;
    createdAt: string;
}

export const StaffDashboard: React.FC = () => {
    const { user, logout } = useAuthStore();
    const navigate = useNavigate();
    const location = useLocation();
    const [stats, setStats] = React.useState<DashboardStats | null>(null);
    const [notifications, setNotifications] = React.useState<Notification[]>([]);
    const [showNotifications, setShowNotifications] = React.useState(false);
    const [isLoading, setIsLoading] = React.useState(true);
    const [currentTime, setCurrentTime] = React.useState(new Date());
    const [isMobileMenuOpen, setIsMobileMenuOpen] = React.useState(false);

    // ===== UPDATE CLOCK =====
    React.useEffect(() => {
        const timer = setInterval(() => {
            setCurrentTime(new Date());
        }, 1000);

        return () => clearInterval(timer);
    }, []);

    // ===== CHECK AUTH =====
    React.useEffect(() => {
        if (!user) {
            navigate('/login');
            return;
        }

        if (user.role !== 'STAFF' && user.role !== 'DOCTOR' && user.role !== 'FACILITY_ADMIN') {
            navigate('/patient/dashboard');
            return;
        }

        loadDashboardData();
        loadNotifications();
    }, [user, navigate]);

    // ===== LOAD DATA =====
    const loadDashboardData = async () => {
        setIsLoading(true);
        try {
            const data = await staffService.getDashboardStats();
            setStats(data);
        } catch (error) {
            toast.error('Failed to load dashboard data');
            console.error('Dashboard error:', error);
        } finally {
            setIsLoading(false);
        }
    };

    const loadNotifications = async () => {
        try {
            const data = await staffService.getNotifications();
            setNotifications(data || []);
        } catch (error) {
            console.error('Failed to load notifications:', error);
        }
    };

    // ===== HANDLERS =====
    const handleLogout = () => {
        logout();
        navigate('/login');
        toast.success('Logged out successfully');
    };

    const handleMarkNotificationRead = async (id: string) => {
        try {
            await staffService.markNotificationRead(id);
            setNotifications(prev =>
                prev.map(n => n.id === id ? { ...n, read: true } : n)
            );
        } catch (error) {
            console.error('Failed to mark notification read:', error);
        }
    };

    const unreadCount = notifications.filter(n => !n.read).length;

    // ===== FORMATTERS =====
    const formatTime = (date: Date) => {
        return date.toLocaleTimeString('en-US', {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: true
        });
    };

    const formatDate = (date: Date) => {
        return date.toLocaleDateString('en-US', {
            weekday: 'long',
            year: 'numeric',
            month: 'long',
            day: 'numeric'
        });
    };

    const getInitials = () => {
        if (!user) return 'S';
        const first = user.firstName?.charAt(0) || '';
        const last = user.lastName?.charAt(0) || '';
        return (first + last).toUpperCase();
    };

    const getFullName = () => {
        if (!user) return 'Staff';
        return `${user.firstName || ''} ${user.lastName || ''}`.trim() || 'Staff';
    };

    // ===== NAVIGATION ITEMS =====
    const navItems = [
        { path: '/staff/dashboard', icon: '📊', label: 'Dashboard' },
        { path: '/staff/queue', icon: '📋', label: 'Queue' },
        { path: '/staff/patients', icon: '👥', label: 'Patients' },
        { path: '/staff/billing', icon: '💰', label: 'Billing' },
    ];

    const isActiveRoute = (path: string) => {
        if (path === '/staff/dashboard') {
            return location.pathname === path;
        }
        return location.pathname.startsWith(path);
    };

    // ===== LOADING =====
    if (isLoading) {
        return (
            <div className="flex justify-center items-center min-h-screen bg-gray-100">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
                    <p className="mt-4 text-gray-600">Loading dashboard...</p>
                </div>
            </div>
        );
    }

    // ===== RENDER =====
    return (
        <div className="flex h-screen bg-gray-100 overflow-hidden">
            {/* ============================================================ */}
            {/* MOBILE MENU OVERLAY */}
            {/* ============================================================ */}
            {isMobileMenuOpen && (
                <div
                    className="fixed inset-0 bg-black bg-opacity-50 z-40 lg:hidden"
                    onClick={() => setIsMobileMenuOpen(false)}
                />
            )}

            {/* ============================================================ */}
            {/* SIDEBAR */}
            {/* ============================================================ */}
            <aside
                className={`
                    fixed lg:static inset-y-0 left-0 z-50
                    w-72 bg-white shadow-2xl lg:shadow-lg
                    transform transition-transform duration-300 ease-in-out
                    ${isMobileMenuOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
                    flex flex-col
                `}
            >
                {/* Sidebar Header */}
                <div className="p-6 border-b bg-gradient-to-r from-primary-600 to-primary-700">
                    <div className="flex items-center space-x-3">
                        <div className="w-10 h-10 bg-white/20 rounded-lg flex items-center justify-center">
                            <span className="text-2xl">🏥</span>
                        </div>
                        <div>
                            <h1 className="text-xl font-bold text-white">Aura Hub</h1>
                            <p className="text-xs text-white/70">Staff Panel</p>
                        </div>
                    </div>
                    <button
                        onClick={() => setIsMobileMenuOpen(false)}
                        className="lg:hidden absolute top-4 right-4 text-white hover:text-white/80"
                    >
                        ✕
                    </button>
                </div>

                {/* Navigation */}
                <nav className="flex-1 p-4 space-y-1 overflow-y-auto">
                    {navItems.map((item) => (
                        <Link
                            key={item.path}
                            to={item.path}
                            onClick={() => setIsMobileMenuOpen(false)}
                            className={`
                                flex items-center space-x-3 px-4 py-3 rounded-lg transition-all duration-200
                                ${isActiveRoute(item.path)
                                ? 'bg-primary-50 text-primary-700 shadow-sm'
                                : 'text-gray-700 hover:bg-gray-100 hover:scale-[1.02]'
                            }
                            `}
                        >
                            <span className="text-xl w-8 text-center">{item.icon}</span>
                            <span className="font-medium">{item.label}</span>
                            {item.path === '/staff/queue' && stats && stats.waitingPatients > 0 && (
                                <span className="ml-auto bg-red-500 text-white text-xs px-2 py-0.5 rounded-full">
                                    {stats.waitingPatients}
                                </span>
                            )}
                        </Link>
                    ))}
                </nav>

                {/* User Profile */}
                <div className="p-4 border-t bg-gray-50">
                    <div className="flex items-center space-x-3 px-3 py-2 rounded-lg hover:bg-white transition-colors">
                        <div className="w-10 h-10 rounded-full bg-primary-100 flex items-center justify-center flex-shrink-0">
                            <span className="text-primary-600 font-semibold text-sm">
                                {getInitials()}
                            </span>
                        </div>
                        <div className="flex-1 min-w-0">
                            <p className="text-sm font-medium text-gray-900 truncate">
                                {getFullName()}
                            </p>
                            <p className="text-xs text-gray-500 truncate">
                                {user?.role || 'Staff'}
                            </p>
                        </div>
                        <button
                            onClick={handleLogout}
                            className="text-gray-400 hover:text-red-500 transition-colors p-1"
                            title="Logout"
                        >
                            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
                            </svg>
                        </button>
                    </div>
                </div>
            </aside>

            {/* ============================================================ */}
            {/* MAIN CONTENT */}
            {/* ============================================================ */}
            <div className="flex-1 flex flex-col min-w-0">
                {/* ========================================================== */}
                {/* TOP BAR */}
                {/* ========================================================== */}
                <header className="bg-white shadow-sm px-4 md:px-6 py-4 flex justify-between items-center flex-wrap gap-3">
                    <div className="flex items-center space-x-3">
                        <button
                            onClick={() => setIsMobileMenuOpen(true)}
                            className="lg:hidden p-2 rounded-lg hover:bg-gray-100 text-gray-600 transition-colors"
                            aria-label="Toggle menu"
                        >
                            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
                            </svg>
                        </button>

                        <div>
                            <h2 className="text-lg font-semibold text-gray-900">
                                Staff Dashboard
                            </h2>
                            <p className="text-sm text-gray-500 hidden sm:block">
                                {formatDate(currentTime)}
                            </p>
                        </div>
                    </div>

                    <div className="flex items-center space-x-4 md:space-x-6">
                        <div className="flex items-center space-x-2">
                            <div className="w-2.5 h-2.5 bg-green-500 rounded-full animate-pulse"></div>
                            <span className="text-sm text-gray-600 hidden sm:inline">Live</span>
                        </div>

                        <span className="text-sm text-gray-600 font-mono hidden md:inline">
                            {formatTime(currentTime)}
                        </span>

                        <button className="text-gray-600 hover:text-primary-600 transition-colors hidden sm:block">
                            <span className="text-sm font-medium">Aura Hub</span>
                        </button>

                        <div className="relative">
                            <button
                                onClick={() => setShowNotifications(!showNotifications)}
                                className="relative text-gray-600 hover:text-primary-600 transition-colors p-1"
                            >
                                <span className="text-xl">🔔</span>
                                {unreadCount > 0 && (
                                    <span className="absolute -top-1 -right-1 w-5 h-5 bg-red-500 text-white text-xs rounded-full flex items-center justify-center font-bold">
                                        {unreadCount > 9 ? '9+' : unreadCount}
                                    </span>
                                )}
                            </button>

                            {showNotifications && (
                                <div className="absolute right-0 mt-2 w-80 bg-white rounded-lg shadow-xl border z-50 max-h-96 overflow-hidden">
                                    <div className="p-3 border-b flex justify-between items-center">
                                        <span className="font-semibold text-gray-900">Notifications</span>
                                        <button
                                            onClick={() => setShowNotifications(false)}
                                            className="text-gray-400 hover:text-gray-600"
                                        >
                                            ✕
                                        </button>
                                    </div>
                                    <div className="overflow-y-auto max-h-64">
                                        {notifications.length === 0 ? (
                                            <p className="p-4 text-sm text-gray-500 text-center">
                                                No notifications
                                            </p>
                                        ) : (
                                            notifications.map((n) => (
                                                <div
                                                    key={n.id}
                                                    className={`p-3 border-b hover:bg-gray-50 cursor-pointer transition-colors ${
                                                        !n.read ? 'bg-blue-50' : ''
                                                    }`}
                                                    onClick={() => handleMarkNotificationRead(n.id)}
                                                >
                                                    <p className={`text-sm ${!n.read ? 'font-medium' : ''}`}>
                                                        {n.message}
                                                    </p>
                                                    <p className="text-xs text-gray-400 mt-1">
                                                        {new Date(n.createdAt).toLocaleString()}
                                                    </p>
                                                </div>
                                            ))
                                        )}
                                    </div>
                                </div>
                            )}
                        </div>

                        <button className="flex items-center space-x-2 text-gray-700 hover:text-primary-600 transition-colors">
                            <div className="w-8 h-8 rounded-full bg-primary-100 flex items-center justify-center">
                                <span className="text-sm font-semibold text-primary-600">
                                    {getInitials()}
                                </span>
                            </div>
                            <span className="text-sm font-medium hidden sm:inline">
                                {getFullName()}
                            </span>
                        </button>
                    </div>
                </header>

                {/* Stats Cards */}
                {location.pathname === '/staff/dashboard' && stats && (
                    <div className="p-4 md:p-6 grid grid-cols-2 lg:grid-cols-4 gap-4">
                        <div className="bg-white rounded-lg shadow p-4 border-l-4 border-blue-500">
                            <div className="flex items-center justify-between">
                                <div>
                                    <p className="text-sm text-gray-500">Total Patients</p>
                                    <p className="text-2xl font-bold text-gray-900">{stats.totalPatients}</p>
                                </div>
                                <span className="text-3xl">👥</span>
                            </div>
                        </div>

                        <div className="bg-white rounded-lg shadow p-4 border-l-4 border-yellow-500">
                            <div className="flex items-center justify-between">
                                <div>
                                    <p className="text-sm text-gray-500">Waiting</p>
                                    <p className="text-2xl font-bold text-yellow-600">{stats.waitingPatients}</p>
                                </div>
                                <span className="text-3xl">⏳</span>
                            </div>
                        </div>

                        <div className="bg-white rounded-lg shadow p-4 border-l-4 border-green-500">
                            <div className="flex items-center justify-between">
                                <div>
                                    <p className="text-sm text-gray-500">In Consultation</p>
                                    <p className="text-2xl font-bold text-green-600">{stats.inConsultation}</p>
                                </div>
                                <span className="text-3xl">👨‍⚕️</span>
                            </div>
                        </div>

                        <div className="bg-white rounded-lg shadow p-4 border-l-4 border-purple-500">
                            <div className="flex items-center justify-between">
                                <div>
                                    <p className="text-sm text-gray-500">Completed Today</p>
                                    <p className="text-2xl font-bold text-purple-600">{stats.completedToday}</p>
                                </div>
                                <span className="text-3xl">✅</span>
                            </div>
                        </div>
                    </div>
                )}

                {/* Main Content Area */}
                <main className="flex-1 overflow-y-auto p-4 md:p-6 pt-0">
                    <Outlet />
                </main>

                {/* Footer */}
                <footer className="bg-white border-t px-4 md:px-6 py-3 flex flex-col sm:flex-row justify-between items-center gap-2">
                    <div className="flex items-center space-x-4">
                        <span className="text-sm text-gray-400">© 2026 Aura Hub</span>
                        <span className="text-sm text-gray-400 hidden sm:inline">|</span>
                        <span className="text-sm text-gray-400 hidden sm:inline">v2.0.0</span>
                    </div>

                    <div className="flex items-center space-x-4 md:space-x-6">
                        <button className="text-sm text-gray-500 hover:text-primary-600 transition-colors">
                            AH
                        </button>
                        <button className="text-sm text-gray-500 hover:text-primary-600 transition-colors">
                            Aura Hub
                        </button>
                        <span className="text-sm text-gray-300 hidden sm:inline">|</span>
                        <span className="text-sm text-gray-500 hidden sm:inline">
                            {user?.role || 'staff'}
                        </span>
                        <button
                            onClick={handleLogout}
                            className="text-sm text-red-500 hover:text-red-700 transition-colors font-medium"
                        >
                            Log out
                        </button>
                    </div>
                </footer>
            </div>
        </div>
    );
};

export default StaffDashboard;