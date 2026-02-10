import React, { useState, useEffect } from 'react';
import axios from 'axios';
import './RequestForm.css';

function RequestForm({ service, fields, endpoint: endpointInfo, inputParams, onResponse }) {
  const [endpoint, setEndpoint] = useState('');
  const [paramValues, setParamValues] = useState({});
  const [sending, setSending] = useState(false);
  const [error, setError] = useState(null);

  // Set endpoint when endpointInfo changes
  useEffect(() => {
    if (endpointInfo && endpointInfo.endpoint) {
      setEndpoint(endpointInfo.endpoint);
    }
  }, [endpointInfo]);

  // Initialize parameter values as empty when inputParams change
  useEffect(() => {
    if (inputParams && inputParams.length > 0) {
      const initialValues = {};
      inputParams.forEach(param => {
        initialValues[param.name] = '';
      });
      setParamValues(initialValues);
    }
  }, [inputParams]);

  const handleParamChange = (paramName, value) => {
    setParamValues(prev => ({
      ...prev,
      [paramName]: value
    }));
  };

  const sendRequest = async () => {
    setSending(true);
    setError(null);
    
    try {
      // Filter out empty parameters - only send non-empty values
      const filteredParams = {};
      Object.entries(paramValues).forEach(([key, value]) => {
        // Only include if value is not empty string, null, or undefined
        if (value !== '' && value !== null && value !== undefined) {
          filteredParams[key] = value;
        }
      });

      const response = await axios.post('/api/request/send', {
        service: service,
        endpoint: endpoint,
        params: filteredParams
      });

      onResponse(response.data);
    } catch (err) {
      const errorMsg = err.response?.data?.error || err.message || 'Request failed';
      setError('Request failed: ' + errorMsg);
      onResponse(null);
    } finally {
      setSending(false);
    }
  };

  const resetTemplate = () => {
    if (inputParams && inputParams.length > 0) {
      const initialValues = {};
      inputParams.forEach(param => {
        initialValues[param.name] = '';
      });
      setParamValues(initialValues);
    }
  };

  return (
    <div className="request-form">
      <h2>3. Test Service (Optional)</h2>
      <p className="help-text">Send a test request to verify the configuration works correctly</p>
      
      {error && <div className="error">{error}</div>}

      <div className="form-group">
        <label>Service: <strong>{service}</strong></label>
      </div>

      <div className="form-group">
        <label>Endpoint</label>
        <input
          type="text"
          placeholder="e.g., http://localhost:8086/xtee-secure-gateway/..."
          value={endpoint}
          onChange={(e) => setEndpoint(e.target.value)}
        />
        <small>Auto-filled from DSL configuration. Edit if needed.</small>
      </div>

      {inputParams && inputParams.length > 0 && (
        <div className="form-group">
          <label>Request Parameters</label>
          <div className="params-grid">
            {inputParams.map(param => (
              <div key={param.name} className="param-field">
                <label htmlFor={param.name}>
                  {param.name}
                </label>
                {param.description && (
                  <small className="param-description">{param.description}</small>
                )}
                <input
                  id={param.name}
                  type={param.type || 'text'}
                  value={paramValues[param.name] || ''}
                  onChange={(e) => handleParamChange(param.name, e.target.value)}
                  placeholder={param.example || `Enter ${param.name}`}
                  required={param.required}
                />
              </div>
            ))}
          </div>
          <button 
            className="reset-btn secondary" 
            onClick={resetTemplate}
            type="button"
          >
            Reset to Examples
          </button>
        </div>
      )}

      {(!inputParams || inputParams.length === 0) && (
        <div className="form-group">
          <div className="info-message">
            No input parameters detected for this service.
          </div>
        </div>
      )}

      <button 
        onClick={sendRequest}
        disabled={sending || !endpoint}
        className="send-btn"
      >
        {sending ? 'Sending...' : 'Send Request'}
      </button>
    </div>
  );
}

export default RequestForm;
