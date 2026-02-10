import React, { useState } from 'react';
import './App.css';
import ServiceSelector from './components/WSDLSelector';
import FieldSelector from './components/FieldSelector';
import RequestForm from './components/RequestForm';
import ResponseViewer from './components/ResponseViewer';

function App() {
  const [selectedService, setSelectedService] = useState(null);
  const [fields, setFields] = useState([]);
  const [showFieldSelector, setShowFieldSelector] = useState(false);
  const [endpointInfo, setEndpointInfo] = useState(null);
  const [inputParams, setInputParams] = useState([]);
  const [response, setResponse] = useState(null);

  const handleServiceSelect = (service, initialFields, endpoint, params) => {
    setSelectedService(service);
    setFields(initialFields || []);
    setShowFieldSelector(initialFields && initialFields.length > 0);
    setEndpointInfo(endpoint);
    setInputParams(params || []);
    setResponse(null); // Clear previous response
  };

  const handleResponse = (responseData) => {
    setResponse(responseData);
  };

  return (
    <div className="App">
      <header className="App-header">
        <h1>X-Road Services Admin UI</h1>
        <p>Configure response field visibility of X-Road services</p>
      </header>
      
      <div className="container">
        <div className="panel">
          <ServiceSelector 
            onSelect={handleServiceSelect}
          />
        </div>

        {showFieldSelector && fields.length > 0 && (
          <div className="panel">
            <FieldSelector 
              service={selectedService}
              fields={fields}
              onFieldsUpdate={setFields}
            />
          </div>
        )}

        {selectedService && endpointInfo && (
          <div className="panel">
            <RequestForm 
              service={selectedService}
              fields={fields}
              endpoint={endpointInfo}
              inputParams={inputParams}
              onResponse={handleResponse}
            />
          </div>
        )}

        {response && (
          <div className="panel">
            <ResponseViewer response={response} />
          </div>
        )}
      </div>
    </div>
  );
}

export default App;
