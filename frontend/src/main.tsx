import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';

// ===== ADD: Error Boundary (optional but recommended) =====
// You can create a simple ErrorBoundary component or use react-error-boundary

ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
        <App />
    </React.StrictMode>
);