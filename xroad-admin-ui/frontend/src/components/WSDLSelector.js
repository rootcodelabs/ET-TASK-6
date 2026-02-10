import React, { useState, useEffect } from 'react';
import axios from 'axios';
import './WSDLSelector.css';

function ServiceSelector({ onSelect }) {
  const [services, setServices] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedService, setSelectedService] = useState('');
  const [wsdlUrl, setWsdlUrl] = useState('https://ariregxmlv6.rik.ee/?wsdl');

  useEffect(() => {
    loadServices();
  }, []);

  const loadServices = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.get('/api/services/list', {
        params: { wsdl_url: wsdlUrl }
      });
      setServices(response.data.services);
    } catch (err) {
      setError('Failed to load services: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleSelect = async () => {
    if (!selectedService) return;
    
    setLoading(true);
    setError(null);
    try {
      // Get fields, input params, and endpoint info for the service from XSD
      const response = await axios.get(`/api/services/fields/${selectedService}`);
      onSelect(
        selectedService, 
        response.data.fields, 
        response.data.endpoint, 
        response.data.input_params
      );
    } catch (err) {
      setError('Failed to load service fields: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="service-selector">
      <h2>1. Select Service</h2>
      
      <div className="wsdl-config">
        <label>WSDL URL:</label>
        <div className="url-input-group">
          <input 
            type="text" 
            value={wsdlUrl} 
            onChange={(e) => setWsdlUrl(e.target.value)}
            placeholder="Enter WSDL URL"
          />
          <button onClick={loadServices} disabled={loading}>
            Refresh Services
          </button>
        </div>
      </div>

      <p className="help-text">Choose a service</p>
      
      {error && <div className="error">{error}</div>}
      
      {loading ? (
        <div className="loading">Loading service structure from XSD...</div>
      ) : (
        <div className="selector-content">
          <select 
            value={selectedService} 
            onChange={(e) => setSelectedService(e.target.value)}
          >
            <option value="">-- Select a service --</option>
            {services.map((service) => (
              <option key={service.name} value={service.name}>
                {service.name}
                {service.description && ` - ${service.description}`}
              </option>
            ))}
          </select>
          
          <button 
            onClick={handleSelect}
            disabled={!selectedService || loading}
          >
            Load Response Fields
          </button>
        </div>
      )}
    </div>
  );
}

export default ServiceSelector;
