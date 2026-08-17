import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { doctorService, LabService } from '../../services/doctor';
import { sseService } from '../../services/sse';
import { useAuthStore } from '../../stores/authStore';
import { Ticket } from '../../types/ticket';
import api from '../../services/api';

export const DoctorDashboard: React.FC = () => {
  const navigate = useNavigate();
  const { user, logout, hasHydrated, updateUser } = useAuthStore();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [metrics, setMetrics] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingLabServices, setIsLoadingLabServices] = useState(false);
  const [emergencyMode, setEmergencyMode] = useState(false);
  const [emergencyDuration, setEmergencyDuration] = useState(30);
  const [selectedTicket, setSelectedTicket] = useState<Ticket | null>(null);
  const [showLabModal, setShowLabModal] = useState(false);
  const [labServiceCode, setLabServiceCode] = useState('');
  const [selectedLabService, setSelectedLabService] = useState<LabService | null>(null);
  const [labServices, setLabServices] = useState<LabService[]>([]);
  const [labResult, setLabResult] = useState('');
  const [showEmergencyModal, setShowEmergencyModal] = useState(false);

  // Multi-lab selection state
  const [selectedLabServices, setSelectedLabServices] = useState<LabService[]>([]);
  const [isOrderingLabs, setIsOrderingLabs] = useState(false);

  // Bill preview state
  const [showBillPreview, setShowBillPreview] = useState(false);
  const [billPreview, setBillPreview] = useState<any>(null);
  const [isPreviewLoading, setIsPreviewLoading] = useState(false);

  // DEBUG: Check what's in the user object
  useEffect(() => {
    console.log("🔍 User data:", user);
    console.log("📋 Department name:", user?.departmentName);
    console.log("🆔 Department ID:", user?.departmentId);
    console.log("🏷️ Department Code:", user?.departmentCode);
  }, [user]);

  // Fetch department data if missing
  useEffect(() => {
    if (user?.role === 'DOCTOR' && user?.id && !user?.departmentId) {
      const fetchDoctorProfile = async () => {
        try {
          console.log("🔄 Fetching doctor profile...");
          const response = await api.get(`/doctors/${user.id}`);
          const doctorData = response.data;

          console.log("📋 Doctor profile data:", doctorData);

          if (doctorData.departmentId) {
            updateUser({
              departmentId: doctorData.departmentId,
              departmentName: doctorData.departmentName,
              departmentCode: doctorData.departmentCode
            });
            console.log("✅ Department updated!");
            toast.success('Department loaded!');
          } else {
            console.log("ℹ️ Doctor has no department assigned");
          }
        } catch (error) {
          console.warn("Could not fetch doctor profile:", error);
        }
      };

      fetchDoctorProfile();
    }
  }, [user, updateUser]);

  // FETCH LAB SERVICES FROM DATABASE
  const fetchLabServices = async () => {
    setIsLoadingLabServices(true);
    try {
      const response = await doctorService.getLabServices();
      console.log('📋 Lab services from DB:', response);

      if (response.labServices && response.labServices.length > 0) {
        setLabServices(response.labServices);
      } else {
        setLabServices([]);
        toast.warning('No lab services configured. Please contact admin.');
      }
    } catch (error) {
      console.error('Failed to load lab services:', error);
      toast.error('Failed to load lab services');
    } finally {
      setIsLoadingLabServices(false);
    }
  };

  // Check emergency status on login
  useEffect(() => {
    if (!user?.facilityId || !user?.departmentId) return;

    const checkEmergencyStatus = async () => {
      try {
        const status = await doctorService.getEmergencyStatus(
            user.facilityId!,
            user.departmentId!
        );
        if (status.active) {
          setEmergencyMode(true);
          toast.info('🚨 Emergency mode is currently active');
        }
      } catch (error) {
        // Silent fail - emergency status is optional
      }
    };

    checkEmergencyStatus();
  }, [user?.facilityId, user?.departmentId]);

  useEffect(() => {
    if (!hasHydrated) {
      return;
    }

    if (!user) {
      navigate('/login');
      return;
    }

    if (user.role !== 'DOCTOR') {
      toast.error('You do not have access to this page');
      navigate('/login');
      return;
    }

    loadDashboard();
    connectSSE();
    fetchLabServices();

    return () => {
      sseService.disconnect();
    };
  }, [hasHydrated, user]);

  const loadDashboard = async () => {
    setIsLoading(true);
    try {
      const queueData = await doctorService.getDoctorQueue();
      setTickets(queueData.tickets || []);

      if (user?.facilityId && user?.departmentId) {
        try {
          const metricsData = await doctorService.getQueueMetrics(
              user.facilityId,
              user.departmentId
          );
          setMetrics(metricsData);
        } catch (metricError) {
          setMetrics({
            total: 0,
            emergency: 0,
            high: 0,
            medium: 0,
            low: 0,
            averageWaitMinutes: 0
          });
        }
      } else {
        setMetrics({
          total: 0,
          emergency: 0,
          high: 0,
          medium: 0,
          low: 0,
          averageWaitMinutes: 0
        });
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Failed to load dashboard');
    } finally {
      setIsLoading(false);
    }
  };

  const connectSSE = () => {
    if (!user?.facilityId || !user?.departmentId) {
      return;
    }

    sseService.connectToQueue(
        user.facilityId,
        user.departmentId,
        () => {
          loadDashboard();
        },
        () => {
          toast('🚨 Emergency alert received from another doctor!');
          setEmergencyMode(true);
        }
    );
  };

  // START CONSULTATION
  const handleStartConsultation = async (ticketId: string) => {
    try {
      await doctorService.startConsultation(ticketId);
      toast.success('✅ Consultation started');
      loadDashboard();
    } catch (error: any) {
      console.error('Start consultation error:', error);
      const errorMsg = error.response?.data?.message || 'Failed to start consultation';
      toast.error(errorMsg);
    }
  };

  // PREVIEW BILL
  const handlePreviewBill = async (ticketId: string) => {
    setIsPreviewLoading(true);
    try {
      const response = await api.get('/billing/preview', {
        params: {
          ticketId: ticketId,
          serviceCode: 'CONSULTATION'
        }
      });

      setBillPreview(response.data);
      setShowBillPreview(true);
      toast.success('📋 Bill preview loaded');
    } catch (error: any) {
      console.error('Preview error:', error);
      const errorMessage = error.response?.data?.error || 'Failed to preview bill';
      toast.error(errorMessage);
    } finally {
      setIsPreviewLoading(false);
    }
  };

  // GENERATE BILL
  const handleGenerateBill = async (ticketId: string) => {
    if (!ticketId) {
      toast.error('Invalid ticket ID');
      return;
    }

    try {
      await doctorService.completeConsultation(ticketId);
      toast.success('✅ Consultation completed! Patient moved to billing.');
      setShowBillPreview(false);
      setBillPreview(null);
      setSelectedTicket(null);
      loadDashboard();
    } catch (error: any) {
      console.error('Generate bill error:', error);
      const errorMsg = error.response?.data?.message || 'Failed to complete consultation';
      toast.error(errorMsg);
    }
  };

  // COMPLETE CONSULTATION
  const handleCompleteConsultation = async (ticketId: string) => {
    if (!ticketId || typeof ticketId !== 'string') {
      toast.error('Invalid ticket ID');
      return;
    }

    try {
      const ticket = tickets.find(t => t.id === ticketId);

      if (!ticket) {
        toast.error('Ticket not found');
        return;
      }

      const canComplete = ['IN_CONSULTATION', 'LAB_COMPLETED'].includes(ticket.status);

      if (!canComplete) {
        toast.error(`Cannot complete consultation. Current status: ${ticket.status}`);
        return;
      }

      if (ticket.status === 'LAB_PENDING') {
        const confirm = window.confirm(
            '⚠️ Lab tests have been ordered but results are not yet recorded.\n\n' +
            'The consultation cannot be completed until lab results are entered.\n\n' +
            'Do you want to go to the lab results page?'
        );
        if (confirm) {
          setSelectedTicket(ticket);
          setShowLabModal(true);
        }
        return;
      }

      await doctorService.completeConsultation(ticketId);
      toast.success('✅ Consultation completed! Patient moved to billing.');
      setSelectedTicket(null);
      setShowBillPreview(false);
      setBillPreview(null);
      loadDashboard();
    } catch (error: any) {
      console.error('Complete consultation error:', error);
      console.error('Error response:', error.response);
      console.error('Error data:', error.response?.data);

      const errorMsg = error.response?.data?.message ||
          error.response?.data?.error ||
          'Failed to complete consultation';

      if (errorMsg.toLowerCase().includes('lab tests have been ordered') ||
          errorMsg.toLowerCase().includes('pending labs')) {
        toast.error('⚠️ Lab tests are pending. Please enter lab results first.');
      } else {
        toast.error(errorMsg);
      }
    }
  };

  // ORDER MULTIPLE LABS
  const handleOrderMultipleLabs = async (ticketId: string) => {
    if (selectedLabServices.length === 0) {
      toast.error('Please select at least one lab service');
      return;
    }

    setIsOrderingLabs(true);
    try {
      const loadingToast = toast.loading(`Ordering ${selectedLabServices.length} lab(s)...`);
      const serviceCodes = selectedLabServices.map(s => s.serviceCode);

      try {
        const result = await doctorService.batchOrderLabs(ticketId, serviceCodes);

        if (result.successCount === result.totalCount) {
          toast.success(`✅ All ${result.successCount} lab test(s) ordered successfully!`, {
            id: loadingToast
          });
        } else {
          toast.warning(
              `⚠️ ${result.successCount} of ${result.totalCount} labs ordered. Failed: ${result.errors?.join(', ') || 'Some services failed'}`,
              { id: loadingToast }
          );
        }
      } catch (batchError) {
        console.warn('Batch order failed, falling back to sequential:', batchError);

        let successCount = 0;
        let failedCount = 0;
        const failedServices: string[] = [];

        for (const service of selectedLabServices) {
          try {
            await doctorService.orderLabTest(ticketId, service.serviceCode);
            successCount++;
          } catch (error: any) {
            failedCount++;
            failedServices.push(service.serviceName);
            console.error(`Failed to order ${service.serviceName}:`, error);
          }
        }

        if (failedCount === 0) {
          toast.success(`✅ All ${successCount} lab test(s) ordered successfully!`, {
            id: loadingToast
          });
        } else {
          toast.warning(
              `⚠️ ${successCount} of ${selectedLabServices.length} labs ordered. Failed: ${failedServices.join(', ')}`,
              { id: loadingToast }
          );
        }
      }

      setShowLabModal(false);
      setSelectedTicket(null);
      setSelectedLabServices([]);
      loadDashboard();
    } catch (error: any) {
      console.error('Order labs error:', error);
      toast.error(error.response?.data?.message || 'Failed to order labs');
    } finally {
      setIsOrderingLabs(false);
    }
  };

  // ORDER SINGLE LAB
  const handleOrderLab = async (ticketId: string) => {
    if (!labServiceCode) {
      toast.error('Please select a lab service');
      return;
    }

    try {
      await doctorService.orderLabTest(ticketId, labServiceCode);
      toast.success('🔬 Lab test ordered successfully');
      setShowLabModal(false);
      setLabServiceCode('');
      setSelectedLabService(null);
      setSelectedLabServices([]);
      loadDashboard();
    } catch (error: any) {
      console.error('Order lab error:', error);
      toast.error(error.response?.data?.message || 'Failed to order lab test');
    }
  };

  // COMPLETE LAB
  const handleCompleteLab = async (ticketId: string) => {
    if (!labResult.trim()) {
      toast.error('Please enter lab results');
      return;
    }

    try {
      await doctorService.completeLabTest(ticketId, labResult);
      toast.success('✅ Lab results added');
      setShowLabModal(false);
      setLabResult('');
      loadDashboard();
    } catch (error: any) {
      console.error('Complete lab error:', error);
      toast.error(error.response?.data?.message || 'Failed to complete lab test');
    }
  };

  // ACTIVATE EMERGENCY
  const handleEmergencyMode = async () => {
    const facilityId = user?.facilityId;
    const departmentId = user?.departmentId;

    if (!facilityId || !departmentId) {
      toast.error('Facility or department not found. Please contact admin.');
      return;
    }

    try {
      await doctorService.activateEmergency(
          facilityId,
          departmentId,
          emergencyDuration
      );
      setEmergencyMode(true);
      setShowEmergencyModal(false);
      toast.success(`🚨 Emergency mode activated for ${emergencyDuration} minutes!`);
      loadDashboard();
    } catch (error: any) {
      console.error('Activate emergency error:', error);
      toast.error(error.response?.data?.message || 'Failed to activate emergency mode');
    }
  };

  // DEACTIVATE EMERGENCY
  const handleDeactivateEmergency = async () => {
    if (!confirm('Are you sure you want to deactivate emergency mode?')) return;

    try {
      await doctorService.deactivateEmergency(
          user?.facilityId!,
          user?.departmentId!
      );
      setEmergencyMode(false);
      toast.success('Emergency mode deactivated');
      loadDashboard();
    } catch (error: any) {
      console.error('Deactivate emergency error:', error);
      toast.error(error.response?.data?.message || 'Failed to deactivate emergency');
    }
  };

  const getPriorityBadge = (priority: string) => {
    const colors: Record<string, string> = {
      EMERGENCY: 'bg-red-600 text-white',
      HIGH: 'bg-orange-500 text-white',
      MEDIUM: 'bg-yellow-500 text-gray-900',
      LOW: 'bg-gray-500 text-white'
    };
    return colors[priority] || colors.LOW;
  };

  const getStatusBadge = (status: string) => {
    const colors: Record<string, string> = {
      CHECKED_IN: 'bg-gray-400 text-white',
      TRIAGED: 'bg-indigo-500 text-white',
      IN_CONSULTATION: 'bg-green-500 text-white',
      LAB_PENDING: 'bg-purple-500 text-white',
      LAB_COMPLETED: 'bg-teal-500 text-white',
      CONSULTATION_DONE: 'bg-green-600 text-white',
      PAYMENT_PENDING: 'bg-yellow-500 text-gray-900',
      DISCHARGED: 'bg-gray-500 text-white'
    };
    return colors[status] || 'bg-gray-400 text-white';
  };

  const formatPrice = (price: number) => {
    return price.toLocaleString('rw-RW');
  };

  if (!hasHydrated || isLoading) {
    return (
        <div className="flex justify-center items-center min-h-screen">
          <div className="text-center">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
            <p className="mt-4 text-gray-600">Loading doctor dashboard...</p>
          </div>
        </div>
    );
  }

  return (
      <div className="min-h-screen bg-gray-50">
        {/* Header */}
        <div className="bg-white shadow-md border-b border-gray-200">
          <div className="max-w-7xl mx-auto px-6 py-4">
            <div className="flex justify-between items-center flex-wrap gap-4">
              <div>
                <h1 className="text-2xl font-bold text-gray-900">
                  🏥 Doctor Dashboard
                </h1>
                <p className="text-sm text-gray-500">
                  Welcome, Dr. {user?.lastName}
                </p>
                <div className="flex items-center gap-2 mt-1 flex-wrap">
                  <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-800">
                    🏥 {user?.facilityName || 'No facility assigned'}
                  </span>

                  {user?.departmentName ? (
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                        🩺 {user.departmentName}
                      </span>
                  ) : user?.departmentId ? (
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800">
                        ⚠️ Department ID: {user.departmentId}
                      </span>
                  ) : (
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-400">
                        📋 No department assigned
                      </span>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-4 flex-wrap">
                <div className="flex items-center gap-4">
                  {emergencyMode && (
                      <div className="flex items-center gap-4">
                        <div className="px-4 py-2 bg-red-100 text-red-800 rounded-md flex items-center animate-pulse">
                          <div className="w-2 h-2 bg-red-600 rounded-full mr-2"></div>
                          Emergency Mode Active
                        </div>
                        <button
                            onClick={handleDeactivateEmergency}
                            className="px-4 py-2 bg-gray-600 text-white rounded-md hover:bg-gray-700"
                        >
                          Deactivate Emergency
                        </button>
                      </div>
                  )}
                  <div className="text-sm text-gray-500">
                    Patients: <span className="font-bold">{tickets.length}</span>
                  </div>
                </div>
                <button
                    onClick={loadDashboard}
                    className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50 text-sm"
                >
                  🔄 Refresh
                </button>
                <button
                    onClick={() => {
                      logout();
                      navigate('/login');
                    }}
                    className="px-4 py-2 border border-red-300 rounded-md text-red-600 hover:bg-red-50 text-sm"
                >
                  Logout
                </button>
              </div>
            </div>
          </div>
        </div>

        <div className="max-w-7xl mx-auto px-6 py-6">
          {/* Metrics */}
          {metrics && (
              <div className="grid grid-cols-2 md:grid-cols-5 gap-4 mb-6">
                <div className="bg-white rounded-lg shadow p-4 text-center">
                  <p className="text-sm text-gray-500">Total</p>
                  <p className="text-2xl font-bold text-gray-900">{metrics.total || 0}</p>
                </div>
                <div className="bg-red-50 rounded-lg shadow p-4 text-center">
                  <p className="text-sm text-red-600">Emergency</p>
                  <p className="text-2xl font-bold text-red-700">{metrics.emergency || 0}</p>
                </div>
                <div className="bg-orange-50 rounded-lg shadow p-4 text-center">
                  <p className="text-sm text-orange-600">High</p>
                  <p className="text-2xl font-bold text-orange-700">{metrics.high || 0}</p>
                </div>
                <div className="bg-yellow-50 rounded-lg shadow p-4 text-center">
                  <p className="text-sm text-yellow-600">Medium</p>
                  <p className="text-2xl font-bold text-yellow-700">{metrics.medium || 0}</p>
                </div>
                <div className="bg-gray-50 rounded-lg shadow p-4 text-center">
                  <p className="text-sm text-gray-500">Avg Wait</p>
                  <p className="text-2xl font-bold text-gray-900">{metrics.averageWaitMinutes || 0}m</p>
                </div>
              </div>
          )}

          {/* Emergency Mode Button */}
          {!emergencyMode && (
              <div className="mb-6 p-4 bg-yellow-50 rounded-lg border border-yellow-200">
                <div className="flex items-center justify-between flex-wrap gap-4">
                  <div>
                    <h4 className="text-sm font-medium text-yellow-800">🚨 Emergency Mode</h4>
                    <p className="text-xs text-yellow-700">Notify all patients of emergency</p>
                  </div>
                  <div className="flex items-center space-x-3">
                    <input
                        type="number"
                        value={emergencyDuration}
                        onChange={(e) => setEmergencyDuration(Number(e.target.value))}
                        className="w-20 rounded-md border-yellow-300 shadow-sm focus:border-yellow-500 focus:ring-yellow-500"
                        min={5}
                        max={60}
                    />
                    <span className="text-sm text-gray-600">minutes</span>
                    <button
                        onClick={() => setShowEmergencyModal(true)}
                        className="px-4 py-2 bg-red-600 text-white rounded-md hover:bg-red-700 transition-colors"
                    >
                      Activate Emergency Mode
                    </button>
                  </div>
                </div>
              </div>
          )}

          {/* Emergency Confirmation Modal */}
          {showEmergencyModal && (
              <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
                <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
                  <div className="text-center">
                    <div className="mx-auto flex items-center justify-center h-12 w-12 rounded-full bg-red-100 mb-4">
                      <svg className="h-6 w-6 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                      </svg>
                    </div>
                    <h3 className="text-lg font-semibold text-gray-900 mb-2">🚨 Activate Emergency Mode</h3>
                    <p className="text-sm text-gray-600 mb-4">
                      This will notify all patients in your department that you are in emergency.
                      Patients will be given options to wait or transfer.
                    </p>
                    <p className="text-sm text-yellow-600 mb-4">
                      Duration: <strong>{emergencyDuration} minutes</strong>
                    </p>
                    <div className="flex space-x-3">
                      <button
                          onClick={handleEmergencyMode}
                          className="flex-1 py-2 px-4 bg-red-600 text-white rounded-md hover:bg-red-700"
                      >
                        Confirm Emergency
                      </button>
                      <button
                          onClick={() => setShowEmergencyModal(false)}
                          className="flex-1 py-2 px-4 border border-gray-300 rounded-md hover:bg-gray-50"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                </div>
              </div>
          )}

          {/* Patient Queue */}
          <div className="bg-white rounded-lg shadow-lg">
            <div className="p-6 border-b border-gray-200">
              <h2 className="text-lg font-semibold text-gray-900">📋 Patient Queue</h2>
              <p className="text-sm text-gray-500">
                {tickets.length} patient{tickets.length !== 1 ? 's' : ''} waiting
              </p>
            </div>

            <div className="p-6">
              {tickets.length === 0 ? (
                  <div className="text-center py-8">
                    <div className="text-6xl mb-4">📋</div>
                    <h3 className="text-lg font-semibold text-gray-900">No Patients in Queue</h3>
                    <p className="text-gray-500 mt-2">Waiting for patients to check in...</p>
                  </div>
              ) : (
                  <div className="space-y-4">
                    {tickets.map((ticket) => (
                        <div
                            key={ticket.id}
                            className="border rounded-lg p-4 hover:shadow-md transition-shadow"
                        >
                          <div className="flex flex-wrap justify-between items-start gap-4">
                            <div>
                              <div className="flex items-center gap-3 flex-wrap">
                                <span className="font-mono text-lg font-bold text-gray-900">
                                  #{ticket.ticketNumber}
                                </span>
                                <span className={`px-2 py-1 rounded-full text-xs font-medium ${getPriorityBadge(ticket.priority)}`}>
                                  {ticket.priority}
                                </span>
                                <span className={`px-2 py-1 rounded-full text-xs font-medium ${getStatusBadge(ticket.status)}`}>
                                  {ticket.status.replace('_', ' ')}
                                </span>
                              </div>
                              <div className="mt-2 text-sm text-gray-600">
                                <p><span className="font-medium">Symptoms:</span> {ticket.symptoms}</p>
                                {ticket.age && <p><span className="font-medium">Age:</span> {ticket.age}</p>}
                                {ticket.triageScore && (
                                    <p><span className="font-medium">Triage Score:</span> {ticket.triageScore}/100</p>
                                )}
                              </div>
                            </div>

                            <div className="flex flex-wrap gap-2">
                              {ticket.status === 'TRIAGED' && (
                                  <button
                                      onClick={() => handleStartConsultation(ticket.id)}
                                      className="px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700 transition-colors text-sm"
                                  >
                                    Start Consultation
                                  </button>
                              )}

                              {ticket.status === 'IN_CONSULTATION' && (
                                  <>
                                    <button
                                        onClick={() => {
                                          setSelectedTicket(ticket);
                                          setSelectedLabServices([]);
                                          setShowLabModal(true);
                                        }}
                                        className="px-4 py-2 bg-purple-600 text-white rounded-md hover:bg-purple-700 transition-colors text-sm"
                                    >
                                      Order Lab
                                    </button>
                                    <button
                                        onClick={() => handlePreviewBill(ticket.id)}
                                        disabled={isPreviewLoading}
                                        className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors text-sm"
                                    >
                                      {isPreviewLoading ? 'Loading...' : '📋 Preview Bill'}
                                    </button>
                                    <button
                                        onClick={() => handleCompleteConsultation(ticket.id)}
                                        className="px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 transition-colors text-sm"
                                    >
                                      Complete
                                    </button>
                                  </>
                              )}

                              {ticket.status === 'LAB_PENDING' && (
                                  <button
                                      onClick={() => {
                                        setSelectedTicket(ticket);
                                        setShowLabModal(true);
                                      }}
                                      className="px-4 py-2 bg-teal-600 text-white rounded-md hover:bg-teal-700 transition-colors text-sm"
                                  >
                                    Enter Results
                                  </button>
                              )}

                              {ticket.status === 'LAB_COMPLETED' && (
                                  <button
                                      onClick={() => handleStartConsultation(ticket.id)}
                                      className="px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 transition-colors text-sm"
                                  >
                                    Review & Consult
                                  </button>
                              )}
                            </div>
                          </div>
                        </div>
                    ))}
                  </div>
              )}
            </div>
          </div>
        </div>

        {/* Lab Order Modal */}
        {showLabModal && selectedTicket && (
            <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
              <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6 max-h-[90vh] overflow-y-auto">
                <div className="flex justify-between items-center mb-4">
                  <h3 className="text-lg font-semibold text-gray-900">
                    {selectedTicket.status === 'IN_CONSULTATION' ? 'Order Lab Tests' : 'Enter Lab Results'}
                  </h3>
                  <button
                      onClick={() => {
                        setShowLabModal(false);
                        setSelectedTicket(null);
                        setLabServiceCode('');
                        setSelectedLabService(null);
                        setSelectedLabServices([]);
                        setLabResult('');
                      }}
                      className="text-gray-400 hover:text-gray-600"
                  >
                    ✕
                  </button>
                </div>

                {selectedTicket.status === 'IN_CONSULTATION' ? (
                    <div>
                      <div className="mb-4">
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                          Select Lab Services <span className="text-xs text-gray-400">(select multiple)</span>
                        </label>
                        {isLoadingLabServices ? (
                            <div className="flex items-center justify-center py-4">
                              <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-primary-600"></div>
                              <span className="ml-2 text-sm text-gray-500">Loading lab services...</span>
                            </div>
                        ) : labServices.length === 0 ? (
                            <div className="p-3 bg-yellow-50 rounded-md border border-yellow-200">
                              <p className="text-sm text-yellow-700">
                                ⚠️ No lab services configured. Please contact administrator.
                              </p>
                            </div>
                        ) : (
                            <div className="space-y-2 max-h-60 overflow-y-auto border rounded-md p-2">
                              {labServices.map((service) => {
                                const isSelected = selectedLabServices.some(s => s.serviceCode === service.serviceCode);
                                return (
                                    <label
                                        key={service.serviceCode}
                                        className={`flex items-center justify-between p-2 rounded-md cursor-pointer transition-colors ${
                                            isSelected
                                                ? 'bg-primary-50 border border-primary-300'
                                                : 'hover:bg-gray-50 border border-transparent'
                                        }`}
                                    >
                                      <div className="flex items-center space-x-3">
                                        <input
                                            type="checkbox"
                                            checked={isSelected}
                                            onChange={(e) => {
                                              if (e.target.checked) {
                                                setSelectedLabServices([...selectedLabServices, service]);
                                              } else {
                                                setSelectedLabServices(
                                                    selectedLabServices.filter(s => s.serviceCode !== service.serviceCode)
                                                );
                                              }
                                            }}
                                            className="rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                                        />
                                        <div>
                                          <p className="text-sm font-medium text-gray-900">
                                            {service.serviceName}
                                          </p>
                                          <p className="text-xs text-gray-500">
                                            {service.serviceCode} - {formatPrice(service.basePrice)} RWF
                                          </p>
                                        </div>
                                      </div>
                                      {service.description && (
                                          <span className="text-xs text-gray-400 max-w-[100px] truncate">
                                            {service.description}
                                          </span>
                                      )}
                                    </label>
                                );
                              })}
                            </div>
                        )}
                      </div>

                      {selectedLabServices.length > 0 && (
                          <div className="mb-4 p-3 bg-blue-50 rounded-md border border-blue-200">
                            <p className="text-sm font-medium text-blue-800">
                              Selected Labs: {selectedLabServices.length}
                            </p>
                            <div className="mt-1 flex flex-wrap gap-1">
                              {selectedLabServices.map((service) => (
                                  <span
                                      key={service.serviceCode}
                                      className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800"
                                  >
                                    {service.serviceName}
                                    <button
                                        onClick={() => {
                                          setSelectedLabServices(
                                              selectedLabServices.filter(s => s.serviceCode !== service.serviceCode)
                                          );
                                        }}
                                        className="ml-1 text-blue-600 hover:text-blue-800"
                                    >
                                      ✕
                                    </button>
                                  </span>
                              ))}
                            </div>
                            <p className="text-xs text-blue-600 mt-1">
                              Total: {formatPrice(selectedLabServices.reduce((sum, s) => sum + s.basePrice, 0))} RWF
                            </p>
                          </div>
                      )}

                      <div className="flex space-x-3">
                        <button
                            onClick={() => handleOrderMultipleLabs(selectedTicket.id)}
                            disabled={selectedLabServices.length === 0 || isOrderingLabs}
                            className="flex-1 py-2 px-4 bg-primary-600 text-white rounded-md hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                          {isOrderingLabs ? (
                              <span className="flex items-center justify-center">
                                <svg className="animate-spin -ml-1 mr-2 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                </svg>
                                Ordering...
                              </span>
                          ) : (
                              `Order ${selectedLabServices.length > 0 ? `(${selectedLabServices.length})` : ''} Lab${selectedLabServices.length !== 1 ? 's' : ''}`
                          )}
                        </button>
                        <button
                            onClick={() => {
                              setShowLabModal(false);
                              setSelectedTicket(null);
                              setLabServiceCode('');
                              setSelectedLabService(null);
                              setSelectedLabServices([]);
                            }}
                            className="flex-1 py-2 px-4 border border-gray-300 rounded-md hover:bg-gray-50"
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                ) : (
                    <div>
                      <div className="mb-4">
                        <label className="block text-sm font-medium text-gray-700">Lab Results</label>
                        <textarea
                            value={labResult}
                            onChange={(e) => setLabResult(e.target.value)}
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-primary-500 focus:ring-primary-500"
                            rows={4}
                            placeholder="Enter lab results..."
                        />
                      </div>
                      <div className="flex space-x-3">
                        <button
                            onClick={() => handleCompleteLab(selectedTicket.id)}
                            disabled={!labResult.trim()}
                            className="flex-1 py-2 px-4 bg-primary-600 text-white rounded-md hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                          Save Results
                        </button>
                        <button
                            onClick={() => {
                              setShowLabModal(false);
                              setSelectedTicket(null);
                              setLabResult('');
                            }}
                            className="flex-1 py-2 px-4 border border-gray-300 rounded-md hover:bg-gray-50"
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                )}
              </div>
            </div>
        )}

        {/* Bill Preview Modal */}
        {showBillPreview && billPreview && (
            <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
              <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full p-6 max-h-[90vh] overflow-y-auto">
                <div className="flex justify-between items-center mb-4">
                  <h3 className="text-lg font-semibold text-gray-900">📋 Bill Preview</h3>
                  <button
                      onClick={() => {
                        setShowBillPreview(false);
                        setBillPreview(null);
                      }}
                      className="text-gray-400 hover:text-gray-600"
                  >
                    ✕
                  </button>
                </div>

                <div className="space-y-4">
                  <div className="flex justify-between items-start p-4 bg-gray-50 rounded-lg">
                    <div>
                      <p className="text-sm text-gray-500">Ticket</p>
                      <p className="font-semibold">{billPreview.ticketNumber}</p>
                    </div>
                    <div>
                      <p className="text-sm text-gray-500">Patient</p>
                      <p className="font-semibold">{billPreview.patientName}</p>
                    </div>
                    <div>
                      <p className="text-sm text-gray-500">Insurance</p>
                      <p className={`font-semibold ${
                          billPreview.insuranceType !== 'UNINSURED' ? 'text-green-600' : 'text-gray-600'
                      }`}>
                        {billPreview.insuranceType}
                      </p>
                    </div>
                  </div>

                  <div className={`p-3 rounded-lg ${
                      billPreview.willLabsBeIncluded
                          ? 'bg-green-50 border border-green-200'
                          : 'bg-yellow-50 border border-yellow-200'
                  }`}>
                    <div className="flex items-center">
                      <span className="text-lg mr-2">
                        {billPreview.willLabsBeIncluded ? '✅' : '⚠️'}
                      </span>
                      <p className={`text-sm font-medium ${
                          billPreview.willLabsBeIncluded
                              ? 'text-green-800'
                              : 'text-yellow-800'
                      }`}>
                        {billPreview.message}
                      </p>
                    </div>
                    {billPreview.labCount > 0 && (
                        <p className="text-xs text-gray-600 mt-1 ml-7">
                          Labs: {billPreview.labCodes?.join(', ')}
                        </p>
                    )}
                  </div>

                  <div>
                    <p className="text-sm font-medium text-gray-700 mb-2">Items:</p>
                    <div className="space-y-2 max-h-60 overflow-y-auto">
                      {billPreview.items?.map((item: any, index: number) => (
                          <div
                              key={index}
                              className={`flex justify-between items-center p-3 rounded-md ${
                                  item.category === 'LAB'
                                      ? 'bg-blue-50 border border-blue-100'
                                      : 'bg-gray-50 border border-gray-200'
                              }`}
                          >
                            <div className="flex items-center">
                              <span className="text-sm mr-2">
                                {item.category === 'LAB' ? '🔬' : '🏥'}
                              </span>
                              <div>
                                <p className="text-sm font-medium text-gray-900">
                                  {item.serviceName}
                                </p>
                                <p className="text-xs text-gray-500">
                                  {item.serviceCode} • {item.category}
                                </p>
                              </div>
                            </div>
                            <div className="text-right">
                              <p className="text-sm font-medium text-gray-900">
                                {item.amount.toLocaleString()} RWF
                              </p>
                              {item.originalPrice && item.originalPrice !== item.amount && (
                                  <p className="text-xs text-gray-400 line-through">
                                    {item.originalPrice.toLocaleString()} RWF
                                  </p>
                              )}
                            </div>
                          </div>
                      ))}
                    </div>
                  </div>

                  <div className="border-t pt-4 space-y-2">
                    <div className="flex justify-between">
                      <span className="text-sm text-gray-600">Original Total</span>
                      <span className="text-sm font-medium">{billPreview.originalTotal.toLocaleString()} RWF</span>
                    </div>
                    {billPreview.insuranceType !== 'UNINSURED' && (
                        <div className="flex justify-between">
                          <span className="text-sm text-green-600">Insurance Discount</span>
                          <span className="text-sm text-green-600">
                            - {(billPreview.originalTotal - billPreview.patientAmount).toLocaleString()} RWF
                          </span>
                        </div>
                    )}
                    <div className="flex justify-between pt-2 border-t">
                      <span className="text-lg font-bold text-gray-900">Patient Pays</span>
                      <span className="text-lg font-bold text-primary-600">
                        {billPreview.patientAmount.toLocaleString()} RWF
                      </span>
                    </div>
                  </div>

                  <div className="flex space-x-3 pt-4 border-t">
                    <button
                        onClick={() => handleGenerateBill(selectedTicket?.id || '')}
                        className="flex-1 py-2 px-4 bg-green-600 text-white rounded-md hover:bg-green-700"
                    >
                      ✅ Confirm & Complete
                    </button>
                    <button
                        onClick={() => {
                          setShowBillPreview(false);
                          setBillPreview(null);
                        }}
                        className="flex-1 py-2 px-4 border border-gray-300 rounded-md hover:bg-gray-50"
                    >
                      Cancel
                    </button>
                  </div>

                  <p className="text-xs text-gray-400 text-center">
                    This is a preview. The bill will be generated when you confirm.
                  </p>
                </div>
              </div>
            </div>
        )}
      </div>
  );
};