import React, { useEffect, useState, useRef } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { authService } from '../services/auth';

export const VerifyEmail: React.FC = () => {
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token');

    const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
    const [message, setMessage] = useState('Verifying your email address...');
    const isMounted = useRef(true);

    useEffect(() => {
        isMounted.current = true;

        const verifyEmail = async () => {
            if (!token) {
                if (isMounted.current) {
                    setStatus('error');
                    setMessage('Missing or invalid verification token.');
                }
                return;
            }

            try {
                const response = await authService.verifyEmail(token);
                if (isMounted.current) {
                    setStatus('success');
                    setMessage(response.message || 'Email verified successfully!');
                }
            } catch (err: any) {
                if (isMounted.current) {
                    setStatus('error');
                    setMessage(
                        err.response?.data?.message || 'Verification failed. The link may have expired.'
                    );
                }
            }
        };

        verifyEmail();

        return () => {
            isMounted.current = false;
        };
    }, [token]);

    return (
        <div className="min-h-screen flex items-center justify-center bg-gray-50 p-4">
            <div className="max-w-md w-full bg-white p-8 rounded-lg shadow-md text-center">
                {status === 'loading' && (
                    <div>
                        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-green-600 mx-auto"></div>
                        <p className="mt-4 text-gray-600">{message}</p>
                    </div>
                )}

                {status === 'success' && (
                    <div>
                        <div className="text-5xl mb-4">✅</div>
                        <h2 className="text-2xl font-bold text-gray-800 mb-2">Email Verified!</h2>
                        <p className="text-gray-600 mb-6">{message}</p>
                        <Link
                            to="/login"
                            className="inline-block px-6 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 font-medium transition-colors"
                        >
                            Log In Now
                        </Link>
                    </div>
                )}

                {status === 'error' && (
                    <div>
                        <div className="text-5xl mb-4">❌</div>
                        <h2 className="text-2xl font-bold text-gray-800 mb-2">Verification Failed</h2>
                        <p className="text-red-600 mb-6">{message}</p>
                        <Link
                            to="/login"
                            className="inline-block px-6 py-2 bg-gray-200 text-gray-800 rounded-md hover:bg-gray-300 font-medium transition-colors"
                        >
                            Return to Login
                        </Link>
                    </div>
                )}
            </div>
        </div>
    );
};