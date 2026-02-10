import React from 'react';
import './ResponseViewer.css';

function ResponseViewer({ response }) {
  if (!response) return null;

  const renderValue = (value, depth = 0) => {
    if (value === null || value === undefined) {
      return <span className="null-value">null</span>;
    }

    if (typeof value === 'object' && !Array.isArray(value)) {
      return (
        <div className="nested-object" style={{ marginLeft: `${depth * 20}px` }}>
          {Object.entries(value).map(([key, val]) => (
            <div key={key} className="nested-field">
              <span className="field-key">{key}:</span>
              <span className="field-value">{renderValue(val, depth + 1)}</span>
            </div>
          ))}
        </div>
      );
    }

    if (Array.isArray(value)) {
      return (
        <div className="array-value" style={{ marginLeft: `${depth * 20}px` }}>
          {value.length === 0 ? (
            <span className="empty-array">[]</span>
          ) : (
            value.map((item, index) => (
              <div key={index} className="array-item">
                <span className="array-index">[{index}]</span>
                {renderValue(item, depth + 1)}
              </div>
            ))
          )}
        </div>
      );
    }

    if (typeof value === 'boolean') {
      return <span className="boolean-value">{value.toString()}</span>;
    }

    if (typeof value === 'number') {
      return <span className="number-value">{value}</span>;
    }

    // String value - check if sensitive
    const strValue = String(value);
    if (strValue === '***MASKED***') {
      return <span className="sensitive-value">🔒 MASKED</span>;
    }

    return <span className="string-value">{strValue}</span>;
  };

  return (
    <div className="response-viewer">
      <h2>4. Response</h2>
      
      <div className={`status-badge ${response.status}`}>
        Status: {response.status.toUpperCase()}
        {response.status_code && ` (${response.status_code})`}
      </div>

      {response.error && (
        <div className="error-message">
          <strong>Error:</strong> {response.error}
        </div>
      )}

      {response.data && (
        <div className="response-data">
          <h3>Response Data</h3>
          <div className="data-content">
            {Object.keys(response.data).length === 0 ? (
              <div className="empty-response">
                <p>No data returned (empty response)</p>
              </div>
            ) : (
              <div className="data-fields">
                {Object.entries(response.data).map(([key, value]) => (
                  <div key={key} className="field-row">
                    <div className="field-header">{key}</div>
                    <div className="field-content">
                      {renderValue(value)}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      <details className="response-raw">
        <summary>View Raw JSON Response</summary>
        <pre>{JSON.stringify(response, null, 2)}</pre>
      </details>
    </div>
  );
}

export default ResponseViewer;
