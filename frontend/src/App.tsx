import React, { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from './context/ThemeContext';
import { MainLayout } from './components/layout/MainLayout';
import { Login } from './pages/Login';
import { Register } from './pages/Register';
import { VerifyEmail } from './pages/VerifyEmail';
import { PatientLanding } from './pages/patient/PatientLanding';
import { PatientDashboard } from './pages/patient/Dashboard';
import { CheckIn } from './pages/patient/CheckIn';
import { QueueStatus } from './pages/patient/QueueStatus';
import { PatientHistory } from './pages/patient/History';
import { PatientBilling } from './pages/patient/Billing';
import { PatientProfile } from './pages/patient/Profile';
import { DoctorDashboard } from './pages/doctor/Dashboard';
import { DistrictDashboard } from './pages/Admin/DistrictDashboard';
import { FacilityAdminDashboard } from './pages/Admin/FacilityAdminDashboard';
import { UserManagement } from './pages/Admin/UserManagement';
import { DepartmentManagement } from './pages/Admin/DepartmentManagement';
import { StaffManagement } from './pages/Admin/StaffManagement';
import { FinancialDashboard } from './pages/Admin/FinancialDashboard';
import { TransferManagement } from './pages/Admin/Transfers';
import { InsuranceProviderManagement } from './pages/Admin/InsuranceProviderManagement';
import { ServicePricingManagement } from './pages/Admin/ServicePricingManagement';
import { Facilities } from './pages/Admin/Facilities';
import { BookAppointment } from './pages/patient/BookAppointment';
import { MyAppointments } from './pages/patient/MyAppointments';
import { NotFound } from './pages/NotFound';
import { StaffDashboard } from './pages/staff/StaffDashboard';

import { useAuthStore } from './stores/authStore';

import './index.css';
import './styles/tokens.css';

const queryClient = new QueryClient();

// ===== PROTECTED ROUTE =====
const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const { user, isAuthenticated, hasHydrated } = useAuthStore();

    if (!hasHydrated) {
        return (
            <div className="flex justify-center items-center min-h-screen">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
            </div>
        );
    }

    if (!isAuthenticated || !user) {
        return <Navigate to="/login" replace />;
    }

    return <>{children}</>;
};

// ===== STAFF ROUTE WRAPPER =====
// StaffDashboard already has its own layout, so we don't wrap it in MainLayout
const StaffRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    return <ProtectedRoute>{children}</ProtectedRoute>;
};

function App() {
    return (
        <QueryClientProvider client={queryClient}>
            <ThemeProvider>
                <BrowserRouter>
                    <div className="min-h-screen">
                        <Routes>
                            {/* ===== PUBLIC AUTH ROUTES ===== */}
                            <Route path="/login" element={<Login />} />
                            <Route path="/register" element={<Register />} />
                            <Route path="/verify-email" element={<VerifyEmail />} />
                            <Route path="/" element={<Navigate to="/login" />} />

                            {/* ===== PATIENT ROUTES ===== */}
                            <Route path="/patient/landing" element={
                                <ProtectedRoute>
                                    <MainLayout title="Welcome">
                                        <PatientLanding />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            <Route path="/patient/dashboard" element={
                                <ProtectedRoute>
                                    <MainLayout title="Patient Dashboard">
                                        <PatientDashboard />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            <Route path="/patient/checkin" element={
                                <ProtectedRoute>
                                    <MainLayout title="Check In">
                                        <CheckIn />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            <Route path="/patient/queue" element={
                                <ProtectedRoute>
                                    <MainLayout title="Queue Status">
                                        <QueueStatus />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            <Route path="/patient/queue/:ticketNumber" element={
                                <ProtectedRoute>
                                    <MainLayout title="Queue Status">
                                        <QueueStatus />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            <Route path="/patient/history" element={
                                <ProtectedRoute>
                                    <MainLayout title="Medical History">
                                        <PatientHistory />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            <Route path="/patient/billing" element={
                                <ProtectedRoute>
                                    <MainLayout title="Billing">
                                        <PatientBilling />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            <Route path="/patient/profile" element={
                                <ProtectedRoute>
                                    <MainLayout title="My Profile">
                                        <PatientProfile />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            <Route path="/patient/appointments/book" element={
                                <ProtectedRoute>
                                    <MainLayout title="Book Appointment">
                                        <BookAppointment />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            <Route path="/patient/my-appointments" element={
                                <ProtectedRoute>
                                    <MainLayout title="My Appointments">
                                        <MyAppointments />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            {/* ===== DOCTOR ROUTES ===== */}
                            <Route path="/doctor/dashboard" element={
                                <ProtectedRoute>
                                    <MainLayout title="Doctor Dashboard">
                                        <DoctorDashboard />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            {/* ===== STAFF ROUTES ===== */}
                            {/* StaffDashboard has its own layout, so no MainLayout wrapper */}
                            <Route path="/staff/dashboard" element={
                                <StaffRoute>
                                    <StaffDashboard />
                                </StaffRoute>
                            } />

                            <Route path="/staff/queue" element={
                                <StaffRoute>
                                    <StaffDashboard />
                                </StaffRoute>
                            } />

                            <Route path="/staff/patients" element={
                                <StaffRoute>
                                    <StaffDashboard />
                                </StaffRoute>
                            } />

                            <Route path="/staff/billing" element={
                                <StaffRoute>
                                    <StaffDashboard />
                                </StaffRoute>
                            } />

                            {/* ===== ADMIN ROUTES ===== */}
                            <Route path="/admin/dashboard" element={
                                <ProtectedRoute>
                                    <MainLayout title="Admin Dashboard">
                                        <DistrictDashboard />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            <Route path="/facility-admin/dashboard" element={
                                <ProtectedRoute>
                                    <MainLayout title="Facility Admin Dashboard">
                                        <FacilityAdminDashboard />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            <Route path="/admin/users" element={
                                <ProtectedRoute>
                                    <MainLayout title="User Management">
                                        <UserManagement />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            <Route path="/admin/facility/:facilityId/departments" element={
                                <ProtectedRoute>
                                    <MainLayout title="Department Management">
                                        <DepartmentManagement />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            <Route path="/admin/facility/:facilityId/staff" element={
                                <ProtectedRoute>
                                    <MainLayout title="Staff Management">
                                        <StaffManagement />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            <Route path="/admin/financial" element={
                                <ProtectedRoute>
                                    <MainLayout title="Financial Dashboard">
                                        <FinancialDashboard />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            <Route path="/admin/transfers" element={
                                <ProtectedRoute>
                                    <MainLayout title="Transfer Management">
                                        <TransferManagement />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            <Route path="/admin/insurance-providers" element={
                                <ProtectedRoute>
                                    <MainLayout title="Insurance Providers">
                                        <InsuranceProviderManagement />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            <Route path="/admin/service-pricing" element={
                                <ProtectedRoute>
                                    <MainLayout title="Service Pricing">
                                        <ServicePricingManagement />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            <Route path="/admin/facilities" element={
                                <ProtectedRoute>
                                    <MainLayout title="Facilities">
                                        <Facilities />
                                    </MainLayout>
                                </ProtectedRoute>
                            } />

                            {/* ===== 404 ROUTE ===== */}
                            <Route path="*" element={<NotFound />} />
                        </Routes>

                        <Toaster
                            position="top-right"
                            toastOptions={{
                                duration: 4000,
                                style: {
                                    background: '#363636',
                                    color: '#fff',
                                },
                            }}
                        />
                    </div>
                </BrowserRouter>
            </ThemeProvider>
        </QueryClientProvider>
    );
}

export default App;