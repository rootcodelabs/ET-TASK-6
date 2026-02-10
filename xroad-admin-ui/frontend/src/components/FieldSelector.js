import React, { useState, useEffect } from 'react';
import axios from 'axios';
import './FieldSelector.css';

function FieldSelector({ service, fields, onFieldsUpdate }) {
  const [localFields, setLocalFields] = useState(fields);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [expandedNodes, setExpandedNodes] = useState(new Set());
  const [fieldPaths, setFieldPaths] = useState([]);

  useEffect(() => {
    // Check if the field structure has changed (not just selected/sensitive properties)
    const newPaths = fields.map(f => f.path).sort().join(',');
    const hasStructureChanged = newPaths !== fieldPaths.join(',');
    
    setLocalFields(fields);
    
    // Only reset expanded nodes if the field structure changed (e.g., new service selected)
    if (hasStructureChanged) {
      const rootNodes = fields.filter(f => !f.parent).map(f => f.path);
      setExpandedNodes(new Set(rootNodes));
      setFieldPaths(fields.map(f => f.path).sort());
    }
  }, [fields, fieldPaths]);

  const getFieldDepth = (field) => {
    return field.path.split('.').length - 1;
  };

  const getChildren = (parentPath) => {
    return localFields.filter(f => f.parent === parentPath);
  };

  const toggleNode = (path) => {
    const newExpanded = new Set(expandedNodes);
    if (newExpanded.has(path)) {
      newExpanded.delete(path);
    } else {
      newExpanded.add(path);
    }
    setExpandedNodes(newExpanded);
  };

  const toggleField = (index, checked) => {
    const updated = [...localFields];
    const field = updated[index];
    field.selected = checked;

    // Cascade to children: if unchecking parent, uncheck all children
    if (!checked && field.has_children) {
      const cascadeUncheck = (parentPath) => {
        updated.forEach((f, i) => {
          if (f.parent === parentPath) {
            f.selected = false;
            if (f.has_children) {
              cascadeUncheck(f.path);
            }
          }
        });
      };
      cascadeUncheck(field.path);
    }

    setLocalFields(updated);
    onFieldsUpdate(updated);
  };

  const toggleSensitive = (index) => {
    const updated = [...localFields];
    updated[index].sensitive = !updated[index].sensitive;
    setLocalFields(updated);
    onFieldsUpdate(updated);
  };

  const saveConfiguration = async () => {
    setSaving(true);
    setMessage(null);
    try {
      // Only save leaf fields (non-structural fields)
      const leafFields = localFields.filter(f => !f.is_structural);
      await axios.post('/api/config/fields', {
        service: service,
        fields: leafFields
      });
      setMessage({ type: 'success', text: 'Configuration saved successfully!' });
      setTimeout(() => setMessage(null), 3000);
    } catch (err) {
      setMessage({ type: 'error', text: 'Failed to save: ' + err.message });
    } finally {
      setSaving(false);
    }
  };

  const selectAll = () => {
    const updated = localFields.map(f => ({ ...f, selected: true }));
    setLocalFields(updated);
    onFieldsUpdate(updated);
  };

  const deselectAll = () => {
    const updated = localFields.map(f => ({ ...f, selected: false }));
    setLocalFields(updated);
    onFieldsUpdate(updated);
  };

  const expandAll = () => {
    const allPaths = localFields.filter(f => f.has_children).map(f => f.path);
    setExpandedNodes(new Set(allPaths));
  };

  const collapseAll = () => {
    setExpandedNodes(new Set());
  };

  // Build tree structure for rendering
  const buildTree = () => {
    const tree = [];
    const processed = new Set();

    const addNode = (field, depth = 0) => {
      if (processed.has(field.path)) return;
      processed.add(field.path);

      const isExpanded = expandedNodes.has(field.path);
      const children = getChildren(field.path);
      
      tree.push({
        ...field,
        depth,
        isExpanded,
        hasVisibleChildren: children.length > 0
      });

      if (isExpanded && children.length > 0) {
        children.forEach(child => addNode(child, depth + 1));
      }
    };

    // Start with root nodes (no parent)
    localFields
      .filter(f => !f.parent)
      .forEach(field => addNode(field, 0));

    return tree;
  };

  const filteredTree = buildTree().filter(field => 
    searchTerm === '' ||
    field.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    field.path.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="field-selector">
      <h2>2. Configure Response Fields</h2>
      
      {message && (
        <div className={`message ${message.type}`}>
          {message.text}
        </div>
      )}

      <div className="field-controls">
        <input
          type="text"
          placeholder="Search fields..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="search-input"
        />
        
        <div className="button-group">
          <button onClick={expandAll} className="secondary small">Expand All</button>
          <button onClick={collapseAll} className="secondary small">Collapse All</button>
          <button onClick={selectAll} className="secondary">Select All</button>
          <button onClick={deselectAll} className="secondary">Clear All</button>
          <button onClick={saveConfiguration} disabled={saving} className="primary">
            {saving ? 'Saving...' : 'Save Configuration'}
          </button>
        </div>
      </div>

      <div className="fields-tree">
        <div className="tree-header">
          <div className="col-field">Field Name</div>
          <div className="col-type">Type</div>
          <div className="col-show">Include</div>
          <div className="col-sensitive">Sensitive</div>
        </div>

        <div className="tree-body">
          {filteredTree.length === 0 ? (
            <div className="no-results">No fields found</div>
          ) : (
            filteredTree.map((field) => {
              const realIndex = localFields.findIndex(f => f.path === field.path);
              const indent = field.depth * 20;
              
              return (
                <div 
                  key={field.path} 
                  className={`tree-row ${field.is_structural ? 'structural' : 'leaf'}`}
                  style={{ paddingLeft: `${indent}px` }}
                >
                  <div className="col-field">
                    {field.hasVisibleChildren && (
                      <span 
                        className={`expand-icon ${field.isExpanded ? 'expanded' : ''}`}
                        onClick={() => toggleNode(field.path)}
                      >
                        ▶
                      </span>
                    )}
                    <span className="field-name" title={field.path}>
                      {field.name}
                      {field.is_structural && <span className="structural-badge">📁</span>}
                    </span>
                  </div>
                  <div className="col-type">{field.type}</div>
                  <div className="col-show">
                    {!field.is_structural && (
                      <input
                        type="checkbox"
                        checked={field.selected}
                        onChange={(e) => toggleField(realIndex, e.target.checked)}
                      />
                    )}
                  </div>
                  <div className="col-sensitive">
                    {!field.is_structural && (
                      <input
                        type="checkbox"
                        checked={field.sensitive}
                        onChange={() => toggleSensitive(realIndex)}
                        disabled={!field.selected}
                      />
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>

      <div className="field-stats">
        Showing {filteredTree.length} fields
        {' | '}
        Selected: {localFields.filter(f => f.selected && !f.is_structural).length}
        {' | '}
        Sensitive: {localFields.filter(f => f.sensitive && !f.is_structural).length}
      </div>
    </div>
  );
}

export default FieldSelector;
