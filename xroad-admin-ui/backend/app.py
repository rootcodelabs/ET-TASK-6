from flask import Flask, jsonify, request
from flask_cors import CORS
from models import db, init_db, ServiceFieldConfig, ServiceConfig
import xml.etree.ElementTree as ET
import json
import os
import requests
import yaml
from pathlib import Path
from urllib.parse import urljoin

app = Flask(__name__)
CORS(app)

# Database configuration
DB_PATH = os.environ.get('DB_PATH', '/app/data')
os.makedirs(DB_PATH, exist_ok=True)
app.config['SQLALCHEMY_DATABASE_URI'] = os.environ.get(
    'SQLALCHEMY_DATABASE_URI', 
    f'sqlite:///{DB_PATH}/xroad_config.db'
)
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

# Initialize database
init_db(app)

# Remote WSDL and XSD configuration
WSDL_URL = 'https://ariregxmlv6.rik.ee/?wsdl'
XSD_BASE_URL = 'https://www2.rik.ee/schemas/xtee6/arireg/live/'

# DSL files path (Ruuter endpoints)
# Use Docker mounted path if available, otherwise use local path
DSL_BASE_PATH = Path('/app/dsl/Ruuter.public/xtee-secure-gateway')
if not DSL_BASE_PATH.exists():
    DSL_BASE_PATH = Path(__file__).parent.parent.parent / 'XTR' / 'RUUTER-DSL' / 'Ruuter.public' / 'xtee-secure-gateway'

# Ruuter base URL - use host.docker.internal when running in Docker
RUUTER_BASE_URL = os.environ.get('RUUTER_BASE_URL', 'http://host.docker.internal:8086/xtee-secure-gateway')

# Common namespaces
NAMESPACES = {
    'wsdl': 'http://schemas.xmlsoap.org/wsdl/',
    'xs': 'http://www.w3.org/2001/XMLSchema',
    'xsd': 'http://www.w3.org/2001/XMLSchema',
    'soap': 'http://schemas.xmlsoap.org/wsdl/soap/',
    'soap12': 'http://schemas.xmlsoap.org/wsdl/soap12/'
}


def fetch_wsdl(wsdl_url=None):
    """Fetch WSDL from remote URL"""
    try:
        url = wsdl_url if wsdl_url else WSDL_URL
        response = requests.get(url, timeout=10)
        response.raise_for_status()
        return ET.fromstring(response.content)
    except Exception as e:
        print(f"Error fetching WSDL: {e}")
        return None


def parse_services_from_wsdl(root):
    """Parse WSDL and extract available services/operations"""
    try:
        services = []
        
        # Find all operations in the WSDL
        for port_type in root.findall('.//wsdl:portType', NAMESPACES):
            for operation in port_type.findall('.//wsdl:operation', NAMESPACES):
                operation_name = operation.get('name')
                
                # Get documentation if available
                doc_elem = operation.find('.//wsdl:documentation', NAMESPACES)
                description = doc_elem.text if doc_elem is not None else ''
                
                if operation_name:
                    services.append({
                        'name': operation_name,
                        'description': description,
                        'xsd_file': f'xroad6_{operation_name}.xsd'
                    })
        
        return services
    except Exception as e:
        print(f"Error parsing services: {e}")
        return []


def fetch_xsd(xsd_filename):
    """Fetch XSD file from remote URL"""
    try:
        xsd_url = urljoin(XSD_BASE_URL, xsd_filename)
        response = requests.get(xsd_url, timeout=10)
        response.raise_for_status()
        return ET.fromstring(response.content)
    except Exception as e:
        print(f"Error fetching XSD {xsd_filename}: {e}")
        return None


def parse_response_fields_from_xsd(root, service_name):
    """
    Parse XSD and extract RESPONSE field definitions with hierarchy.
    Excludes request/input parameters and builds parent-child relationships.
    """
    try:
        fields = []
        type_cache = {}
        
        # Build a map of all complex types for reference
        for complex_type in root.findall('.//xs:complexType', NAMESPACES):
            type_name = complex_type.get('name')
            if type_name:
                type_cache[type_name] = complex_type
        
        # Find the response wrapper element (should end with 'Response')
        # These are typically at the end of XSD in a "wrapper declarations" section
        response_element = None
        all_elements = root.findall('./xs:element', NAMESPACES)  # Only root-level elements
        
        for element in all_elements:
            elem_name = element.get('name', '')
            # Look for Response element
            if elem_name.endswith('Response') or 'response' in elem_name.lower():
                response_element = element
                break
        
        if response_element is None:
            # Fallback: try to find by service name
            expected_name = f"{service_name}Response"
            for element in all_elements:
                if element.get('name') == expected_name:
                    response_element = element
                    break
        
        if response_element is None:
            print(f"No response element found in XSD for {service_name}")
            print(f"Available root elements: {[e.get('name') for e in all_elements]}")
            return []
        
        def extract_from_type(type_name, parent_path='', parent_name='', depth=0):
            """Recursively extract fields from a complex type"""
            if depth > 10:  # Prevent infinite recursion
                return
            
            # Clean type name (remove namespace prefix)
            clean_type_name = type_name.split(':')[-1] if ':' in type_name else type_name
            
            complex_type = type_cache.get(clean_type_name)
            if not complex_type:
                return
            
            # Find all sequence elements in this complex type
            sequences = complex_type.findall('.//xs:sequence', NAMESPACES)
            if not sequences:
                sequences = complex_type.findall('.//xs:all', NAMESPACES)
            
            for sequence in sequences:
                # Get direct child elements only (not nested ones)
                for element in sequence.findall('./xs:element', NAMESPACES):
                    field_name = element.get('name')
                    if not field_name:
                        continue
                    
                    field_type = element.get('type', 'string')
                    clean_field_type = field_type.split(':')[-1] if ':' in field_type else field_type
                    
                    # Build field path
                    field_path = f"{parent_path}.{field_name}" if parent_path else field_name
                    
                    # Check if this is a complex type (structural) or simple type (leaf)
                    is_complex = clean_field_type in type_cache or element.find('./xs:complexType', NAMESPACES) is not None
                    
                    # Check for maxOccurs (indicates array)
                    max_occurs = element.get('maxOccurs', '1')
                    is_array = max_occurs == 'unbounded' or (max_occurs.isdigit() and int(max_occurs) > 1)
                    
                    # Determine type for display
                    if is_array:
                        display_type = 'array'
                    elif is_complex:
                        display_type = 'object'
                    else:
                        # Map XSD types to common types
                        type_map = {
                            'string': 'string',
                            'int': 'number',
                            'integer': 'number',
                            'long': 'number',
                            'decimal': 'number',
                            'boolean': 'boolean',
                            'date': 'date',
                            'dateTime': 'datetime'
                        }
                        display_type = type_map.get(clean_field_type, clean_field_type)
                    
                    # Get documentation
                    title_elem = element.find('.//xrd:title', {'xrd': 'http://x-road.eu/xsd/xroad.xsd'})
                    if title_elem is None:
                        title_elem = element.find('.//xs:annotation/xs:documentation', NAMESPACES)
                    description = title_elem.text.strip() if title_elem is not None and title_elem.text else ''
                    
                    field_info = {
                        'name': field_name,
                        'path': field_path,
                        'type': display_type,
                        'is_structural': is_complex or is_array,
                        'has_children': is_complex,
                        'parent': parent_path if parent_path else None,
                        'description': description
                    }
                    
                    fields.append(field_info)
                    
                    # Recursively process complex types
                    if is_complex and clean_field_type in type_cache:
                        extract_from_type(clean_field_type, field_path, field_name, depth + 1)
                    
                    # Check for inline complex types
                    inline_complex = element.find('./xs:complexType', NAMESPACES)
                    if inline_complex:
                        # Process inline complex type sequences
                        inline_sequences = inline_complex.findall('.//xs:sequence', NAMESPACES)
                        for inline_seq in inline_sequences:
                            for inline_elem in inline_seq.findall('./xs:element', NAMESPACES):
                                inline_name = inline_elem.get('name')
                                if inline_name:
                                    inline_path = f"{field_path}.{inline_name}"
                                    inline_type = inline_elem.get('type', 'string').split(':')[-1]
                                    
                                    fields.append({
                                        'name': inline_name,
                                        'path': inline_path,
                                        'type': inline_type,
                                        'is_structural': False,
                                        'has_children': False,
                                        'parent': field_path,
                                        'description': ''
                                    })
        
        # Start extraction from the response element
        response_type = response_element.get('type')
        if response_type:
            extract_from_type(response_type)
        else:
            # Check for inline complex type
            inline_complex = response_element.find('./xs:complexType', NAMESPACES)
            if inline_complex:
                response_name = response_element.get('name', 'response')
                sequences = inline_complex.findall('.//xs:sequence', NAMESPACES)
                for sequence in sequences:
                    for element in sequence.findall('./xs:element', NAMESPACES):
                        field_name = element.get('name')
                        if field_name:
                            field_type = element.get('type', 'string').split(':')[-1]
                            fields.append({
                                'name': field_name,
                                'path': field_name,
                                'type': field_type,
                                'is_structural': field_type in type_cache,
                                'has_children': field_type in type_cache,
                                'parent': None,
                                'description': ''
                            })
                            if field_type in type_cache:
                                extract_from_type(field_type, field_name, field_name)
        
        return fields
        
    except Exception as e:
        print(f"Error parsing response fields from XSD: {e}")
        import traceback
        traceback.print_exc()
        return []


def parse_input_params_from_xsd(root, service_name):
    """
    Parse XSD and extract INPUT/REQUEST parameter definitions.
    Returns a list of input parameters with their types and descriptions.
    """
    try:
        params = []
        type_cache = {}
        
        # Build a map of all complex types for reference
        for complex_type in root.findall('.//xs:complexType', NAMESPACES):
            type_name = complex_type.get('name')
            if type_name:
                type_cache[type_name] = complex_type
        
        # Find the request wrapper element (should NOT end with 'Response')
        request_element = None
        all_elements = root.findall('./xs:element', NAMESPACES)
        
        for element in all_elements:
            elem_name = element.get('name', '')
            # Look for the service element (not Response)
            if elem_name.lower() == service_name.lower() or \
               (service_name.lower() in elem_name.lower() and not elem_name.endswith('Response')):
                request_element = element
                break
        
        if request_element is None:
            print(f"No request element found in XSD for {service_name}")
            return []
        
        def extract_input_fields(elem, prefix=''):
            """Recursively extract input fields from element"""
            local_params = []
            
            # Get the type of this element
            elem_type = elem.get('type')
            if elem_type:
                clean_type = elem_type.split(':')[-1]
                complex_type = type_cache.get(clean_type)
                if complex_type:
                    # Find sequences in the complex type
                    sequences = complex_type.findall('.//xs:sequence', NAMESPACES)
                    if not sequences:
                        sequences = complex_type.findall('.//xs:all', NAMESPACES)
                    
                    for sequence in sequences:
                        for child_elem in sequence.findall('./xs:element', NAMESPACES):
                            child_name = child_elem.get('name')
                            if not child_name:
                                continue
                            
                            child_type = child_elem.get('type', 'string')
                            clean_child_type = child_type.split(':')[-1] if ':' in child_type else child_type
                            
                            # Get documentation
                            doc_elem = child_elem.find('.//xs:annotation/xs:documentation', NAMESPACES)
                            description = doc_elem.text.strip() if doc_elem is not None and doc_elem.text else ''
                            
                            # Check if it's a complex type (nested structure)
                            if clean_child_type in type_cache:
                                # Recursively extract nested fields
                                local_params.extend(extract_input_fields(child_elem, f"{prefix}{child_name}."))
                            else:
                                # Simple field - add to params
                                field_path = f"{prefix}{child_name}" if prefix else child_name
                                
                                # Map XSD types to display types
                                type_map = {
                                    'string': 'text',
                                    'int': 'number',
                                    'integer': 'number',
                                    'long': 'number',
                                    'decimal': 'number',
                                    'boolean': 'checkbox',
                                    'date': 'date',
                                    'dateTime': 'datetime-local'
                                }
                                input_type = type_map.get(clean_child_type, 'text')
                                
                                # Generate example values based on field name
                                example = generate_example_value(child_name, clean_child_type)
                                
                                local_params.append({
                                    'name': child_name,
                                    'path': field_path,
                                    'type': input_type,
                                    'xsd_type': clean_child_type,
                                    'required': True,  # Default to required
                                    'description': description or f'Input parameter: {child_name}',
                                    'example': example
                                })
            
            # Check for inline complex type
            inline_complex = elem.find('./xs:complexType', NAMESPACES)
            if inline_complex:
                sequences = inline_complex.findall('.//xs:sequence', NAMESPACES)
                for sequence in sequences:
                    for child_elem in sequence.findall('./xs:element', NAMESPACES):
                        child_name = child_elem.get('name')
                        if child_name:
                            local_params.extend(extract_input_fields(child_elem, prefix))
            
            return local_params
        
        # Extract input fields from request element
        params = extract_input_fields(request_element)
        
        return params
        
    except Exception as e:
        print(f"Error parsing input params from XSD: {e}")
        import traceback
        traceback.print_exc()
        return []


def generate_example_value(field_name, field_type):
    """Generate example value based on field name and type"""
    name_lower = field_name.lower()
    
    if 'ariregistri' in name_lower or 'registrikood' in name_lower:
        return '12345678'
    elif 'isikukood' in name_lower or 'personal' in name_lower:
        return '38001010101'
    elif 'eesnimi' in name_lower or 'firstname' in name_lower:
        return 'Mari'
    elif 'perekonnanimi' in name_lower or 'lastname' in name_lower:
        return 'Maasikas'
    elif 'kood' in name_lower or 'code' in name_lower:
        return '12345678'
    elif 'nimi' in name_lower or 'name' in name_lower:
        return 'Sample Name'
    elif field_type in ['int', 'integer', 'long', 'decimal']:
        return '123'
    elif field_type == 'boolean':
        return 'true'
    elif field_type == 'date':
        return '2026-02-10'
    elif field_type == 'dateTime':
        return '2026-02-10T00:00:00'
    else:
        return ''


def find_dsl_endpoint(service_name):
    """Find DSL endpoint configuration for a service"""
    try:
        if not DSL_BASE_PATH.exists():
            return None
        
        # Search in POST and GET directories
        for method_dir in ['POST', 'GET']:
            search_path = DSL_BASE_PATH / method_dir
            if not search_path.exists():
                continue
            
            # Look for DSL files that match the service name
            for dsl_file in search_path.glob('*.yml'):
                file_name = dsl_file.stem.lower()
                service_lower = service_name.lower()
                
                # Match patterns like:
                # arireg-ettevottega-seotud-isikud.yml -> ettevottegaSeotudIsikud_v1
                # Remove hyphens, underscores, and version numbers for comparison
                file_clean = file_name.replace('-', '').replace('_', '').replace('arireg', '')
                service_clean = service_lower.replace('-', '').replace('_', '').replace('v1', '').replace('v2', '').replace('v3', '')
                
                # Check if significant part matches
                if service_clean in file_clean or file_clean in service_clean:
                    # Construct the endpoint URL
                    endpoint = f"{RUUTER_BASE_URL}/{dsl_file.stem}"
                    return {
                        'endpoint': endpoint,
                        'method': method_dir,
                        'file': str(dsl_file)
                    }
        
        # If no match found, try to construct a generic endpoint
        # Convert service name to kebab-case
        endpoint_path = service_name.lower().replace('_', '-')
        return {
            'endpoint': f"{RUUTER_BASE_URL}/{endpoint_path}",
            'method': 'POST',
            'file': None
        }
    except Exception as e:
        print(f"Error finding DSL endpoint: {e}")
        return None


def extract_input_params_from_dsl(service_name):
    """Extract input parameters from XTR DSL file (not Ruuter DSL)"""
    try:
        # Path to XTR DSL files (mounted in Docker)
        xtr_dsl_path = Path('/app/xtr_dsl/arireg')
        
        # Fallback to local path if not in Docker
        if not xtr_dsl_path.exists():
            xtr_dsl_path = Path(__file__).parent.parent.parent / 'XTR' / 'DSL' / 'arireg'
        
        if not xtr_dsl_path.exists():
            print(f"XTR DSL path not found: {xtr_dsl_path}")
            return []
        
        # Look for DSL file matching the service name
        for dsl_file in xtr_dsl_path.glob('*.yml'):
            file_name = dsl_file.stem.lower()
            service_lower = service_name.lower()
            
            # Match patterns
            file_clean = file_name.replace('-', '').replace('_', '')
            service_clean = service_lower.replace('-', '').replace('_', '')
            
            if service_clean in file_clean or file_clean in service_clean:
                # Read and parse the DSL file
                with open(dsl_file, 'r', encoding='utf-8') as f:
                    dsl_content = yaml.safe_load(f)
                    
                    # Extract params list
                    params = dsl_content.get('params', [])
                    
                    # Convert to parameter objects with metadata
                    param_list = []
                    for param in params:
                        param_obj = {
                            'name': param,
                            'type': 'string',  # Default type
                            'required': True,
                            'description': f'Input parameter: {param}'
                        }
                        
                        # Infer type and add better descriptions based on name
                        param_lower = param.lower()
                        if 'kood' in param_lower:
                            param_obj['description'] = 'Code/identifier (e.g., 12345678)'
                            param_obj['example'] = '12345678'
                        elif 'isikukood' in param_lower:
                            param_obj['description'] = 'Personal identification code'
                            param_obj['example'] = '38001010101'
                        elif 'nimi' in param_lower:
                            param_obj['description'] = 'Name'
                            param_obj['example'] = 'Sample Name'
                        elif 'eesnimi' in param_lower:
                            param_obj['description'] = 'First name'
                            param_obj['example'] = 'Mari'
                        elif 'perekonnanimi' in param_lower:
                            param_obj['description'] = 'Last name'
                            param_obj['example'] = 'Maasikas'
                        else:
                            param_obj['example'] = 'sample_value'
                        
                        param_list.append(param_obj)
                    
                    return param_list
        
        print(f"No DSL file found for service: {service_name}")
        return []
    except Exception as e:
        print(f"Error extracting input params from DSL: {e}")
        import traceback
        traceback.print_exc()
        return []


@app.route('/api/services/list', methods=['GET'])
def list_services():
    """List all available services from remote WSDL"""
    try:
        wsdl_url = request.args.get('wsdl_url')
        root = fetch_wsdl(wsdl_url)
        if root is None:
            return jsonify({'error': 'Failed to fetch WSDL from remote server'}), 500
        
        services = parse_services_from_wsdl(root)
        return jsonify({'services': services})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/services/fields/<service_name>', methods=['GET'])
def get_service_fields(service_name):
    """Get response fields from a service's XSD with hierarchical structure"""
    try:
        xsd_filename = f'xroad6_{service_name}.xsd'
        root = fetch_xsd(xsd_filename)
        
        if root is None:
            return jsonify({'error': f'Failed to fetch XSD for service: {service_name}'}), 404
        
        # Extract response fields with hierarchy
        fields = parse_response_fields_from_xsd(root, service_name)
        
        if not fields:
            return jsonify({'error': f'No response fields found in XSD for service: {service_name}'}), 404
        
        # Get saved configuration from database
        saved_configs = ServiceFieldConfig.query.filter_by(service_name=service_name).all()
        saved_config_map = {config.field_path: config for config in saved_configs}
        
        # Add configuration status to each field
        for field in fields:
            config = saved_config_map.get(field['path'])
            field['selected'] = config.selected if config else True
            field['sensitive'] = config.sensitive if config else False
            field['configured'] = config is not None
        
        # Find DSL endpoint for this service
        endpoint_info = find_dsl_endpoint(service_name)
        
        # Extract input parameters from XSD (preferred) or fallback to DSL
        input_params = parse_input_params_from_xsd(root, service_name)
        if not input_params:
            # Fallback to DSL if XSD parsing didn't find params
            input_params = extract_input_params_from_dsl(service_name)
        
        return jsonify({
            'fields': fields, 
            'service': service_name,
            'endpoint': endpoint_info,
            'input_params': input_params,
            'total_fields': len(fields)
        })
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({'error': str(e)}), 500


@app.route('/api/config/fields', methods=['POST'])
def save_field_config():
    """Save field configuration (selected fields and sensitive fields)"""
    try:
        data = request.json
        service_name = data.get('service')
        fields = data.get('fields', [])
        
        # Clear existing config for this service
        ServiceFieldConfig.query.filter_by(service_name=service_name).delete()
        db.session.commit()
        
        # Deduplicate fields by path (keep last occurrence)
        fields_by_path = {}
        for field in fields:
            field_path = field.get('path')
            if field_path:
                fields_by_path[field_path] = field
        
        # Save new config
        for field in fields_by_path.values():
            config = ServiceFieldConfig(
                service_name=service_name,
                field_name=field['name'],
                field_path=field['path'],
                field_type=field.get('type', 'string'),
                selected=field.get('selected', True),
                sensitive=field.get('sensitive', False)
            )
            db.session.add(config)
        
        db.session.commit()
        
        return jsonify({'message': 'Configuration saved successfully'})
    except Exception as e:
        db.session.rollback()
        return jsonify({'error': str(e)}), 500


@app.route('/api/config/fields/<service_name>', methods=['GET'])
def get_field_config(service_name):
    """Get saved field configuration for a service"""
    try:
        configs = ServiceFieldConfig.query.filter_by(service_name=service_name).all()
        
        fields = [config.to_dict() for config in configs]
        
        return jsonify({
            'fields': fields,
            'service': service_name
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/config/service/<service_name>/filter', methods=['GET'])
def get_service_filter_config(service_name):
    """Get filter configuration for XTR (selected and sensitive fields)"""
    try:
        configs = ServiceFieldConfig.query.filter_by(service_name=service_name).all()
        
        selected_fields = [config.field_path for config in configs if config.selected]
        sensitive_fields = [config.field_path for config in configs if config.sensitive]
        
        return jsonify({
            'service': service_name,
            'selected_fields': selected_fields,
            'sensitive_fields': sensitive_fields
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/request/send', methods=['POST'])
def send_request():
    """Send request through Ruuter and return formatted response"""
    try:
        data = request.json
        service_name = data.get('service')
        endpoint = data.get('endpoint')
        params = data.get('params', {})
        
        if not endpoint:
            return jsonify({'error': 'Endpoint is required'}), 400
        
        # Make actual request to the endpoint
        try:
            print(f"Sending request to: {endpoint}")
            print(f"Request body: {json.dumps(params)}")
            
            response = requests.post(
                endpoint,
                json=params,
                headers={'Content-Type': 'application/json'},
                timeout=30
            )
            
            print(f"Response status: {response.status_code}")
            print(f"Response body: {response.text[:1000]}")  # Log first 1000 chars
            
            # Try to parse response as JSON
            try:
                response_data = response.json()
            except:
                response_data = {'raw_response': response.text}
            
            # Extract actual data from Ruuter's nested response structure
            actual_data = response_data
            
            # Navigate through Ruuter's response structure:
            # response.response.body.data.{serviceName}Response or response.response.body
            if isinstance(response_data, dict):
                # Try to extract from nested Ruuter structure
                if 'response' in response_data:
                    ruuter_response = response_data['response']
                    if isinstance(ruuter_response, dict) and 'response' in ruuter_response:
                        inner_response = ruuter_response['response']
                        if isinstance(inner_response, dict) and 'body' in inner_response:
                            body = inner_response['body']
                            if isinstance(body, dict):
                                # If body has 'success' and 'data', extract data
                                if 'data' in body:
                                    actual_data = body['data']
                                else:
                                    actual_data = body
            
            print(f"Extracted data keys: {list(actual_data.keys()) if isinstance(actual_data, dict) else type(actual_data)}")
            
            # Get saved configuration from database for filtering
            saved_configs = ServiceFieldConfig.query.filter_by(service_name=service_name).all()
            selected_fields_set = {config.field_path for config in saved_configs if config.selected}
            sensitive_fields_set = {config.field_path for config in saved_configs if config.sensitive}
            
            print(f"Selected fields: {selected_fields_set}")
            
            # If the data has a service response wrapper (like ettevottegaSeotudIsikudV1Response),
            # we need to navigate into it and apply filtering
            filtered_data = actual_data
            if isinstance(actual_data, dict) and len(actual_data) == 1:
                # Check if the only key looks like a service response wrapper
                wrapper_key = list(actual_data.keys())[0]
                if 'Response' in wrapper_key or wrapper_key.endswith('_response'):
                    # Navigate into the wrapper
                    inner_data = actual_data[wrapper_key]
                    if selected_fields_set:
                        # Apply filtering to the inner data
                        filtered_inner = filter_response_fields(inner_data, selected_fields_set, sensitive_fields_set)
                        # Wrap it back
                        filtered_data = {wrapper_key: filtered_inner} if filtered_inner else {}
                    else:
                        filtered_data = actual_data
                else:
                    # No wrapper, apply filtering directly
                    if selected_fields_set:
                        filtered_data = filter_response_fields(actual_data, selected_fields_set, sensitive_fields_set)
            else:
                # Multiple keys or not a dict, apply filtering directly
                if selected_fields_set:
                    filtered_data = filter_response_fields(actual_data, selected_fields_set, sensitive_fields_set)
            
            print(f"Filtered data keys: {list(filtered_data.keys()) if isinstance(filtered_data, dict) else type(filtered_data)}")
            
            return jsonify({
                'status': 'success' if response.status_code == 200 else 'error',
                'status_code': response.status_code,
                'data': filtered_data,
                'service': service_name
            })
            
        except requests.exceptions.Timeout:
            return jsonify({
                'status': 'error',
                'error': 'Request timeout - the service took too long to respond',
                'service': service_name
            }), 504
        except requests.exceptions.ConnectionError:
            return jsonify({
                'status': 'error',
                'error': f'Connection error - could not reach {endpoint}',
                'service': service_name
            }), 503
        except Exception as e:
            import traceback
            traceback.print_exc()
            return jsonify({
                'status': 'error',
                'error': f'Request failed: {str(e)}',
                'service': service_name
            }), 500
            
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({'error': str(e)}), 500


def filter_response_fields(data, selected_fields, sensitive_fields, current_path=''):
    """
    Recursively filter response data based on selected fields configuration.
    Returns the full structure if no fields are configured, or filters to selected fields.
    """
    # If no fields configured, return everything
    if not selected_fields:
        return data
    
    if not isinstance(data, dict):
        return data
    
    filtered = {}
    for key, value in data.items():
        field_path = f"{current_path}.{key}" if current_path else key
        
        # Check if this field or any of its children are in selected fields
        # This handles nested structures like keha.seosed.isiku_tyyp
        is_parent_of_selected = any(
            path.startswith(field_path + '.') or path == field_path
            for path in selected_fields
        )
        
        # Always include structural elements that lead to selected fields
        if is_parent_of_selected:
            if isinstance(value, dict):
                # Recursively filter nested objects
                filtered_value = filter_response_fields(value, selected_fields, sensitive_fields, field_path)
                if filtered_value or filtered_value == {}:  # Include even if empty dict (structural)
                    filtered[key] = filtered_value
            elif isinstance(value, list):
                # Filter each item in the list
                filtered_list = []
                for item in value:
                    if isinstance(item, dict):
                        # For array items, filter each object
                        filtered_item = filter_response_fields(item, selected_fields, sensitive_fields, field_path)
                        if filtered_item:  # Only add non-empty items
                            filtered_list.append(filtered_item)
                    else:
                        # For primitive arrays, include all items if parent is selected
                        filtered_list.append(item)
                if filtered_list or value == []:  # Include even empty arrays
                    filtered[key] = filtered_list
            else:
                # Leaf field - check if it's explicitly selected
                if field_path in selected_fields:
                    # Mask sensitive fields
                    if field_path in sensitive_fields:
                        filtered[key] = '***MASKED***'
                    else:
                        filtered[key] = value
    
    return filtered


def extract_fields_from_json(data, parent_path='', parent_name=''):
    """
    Recursively extract fields from JSON response with hierarchical structure.
    Returns a list of field objects with parent-child relationships.
    """
    fields = []
    
    if isinstance(data, dict):
        for key, value in data.items():
            field_path = f"{parent_path}.{key}" if parent_path else key
            field_type = type(value).__name__
            
            # Determine if it's a structural node (object/array) or leaf node
            is_structural = isinstance(value, (dict, list))
            
            field = {
                'name': key,
                'path': field_path,
                'type': field_type,
                'parent': parent_path if parent_path else None,
                'is_structural': is_structural,
                'has_children': is_structural
            }
            fields.append(field)
            
            # Recursively process nested structures
            if isinstance(value, dict):
                fields.extend(extract_fields_from_json(value, field_path, key))
            elif isinstance(value, list) and len(value) > 0:
                # For arrays, analyze the first element to determine structure
                if isinstance(value[0], (dict, list)):
                    fields.extend(extract_fields_from_json(value[0], field_path, key))
    
    return fields


@app.route('/api/response/analyze', methods=['POST'])
def analyze_response():
    """
    Analyze a response to extract its field structure.
    Used after testing a service to discover available response fields.
    """
    try:
        data = request.json
        service_name = data.get('service')
        response_data = data.get('response')
        
        if not response_data:
            return jsonify({'error': 'Response data is required'}), 400
        
        # Extract fields from the actual response
        fields = extract_fields_from_json(response_data)
        
        # Get saved configuration from database
        saved_configs = ServiceFieldConfig.query.filter_by(service_name=service_name).all()
        saved_config_map = {config.field_path: config for config in saved_configs}
        
        # Mark which fields are already configured
        for field in fields:
            config = saved_config_map.get(field['path'])
            field['selected'] = config.selected if config else True
            field['sensitive'] = config.sensitive if config else False
            field['configured'] = config is not None
        
        return jsonify({
            'service': service_name,
            'fields': fields,
            'total_fields': len(fields)
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/health', methods=['GET'])
def health():
    """Health check endpoint"""
    return jsonify({'status': 'healthy', 'service': 'xroad-admin-ui'})


if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=5000)
