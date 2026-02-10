import xml.etree.ElementTree as ET
import requests
import os
from xml.dom import minidom

def read_wsdl(file_path):
    with open(file_path, 'r', encoding='utf-8') as file:
        return file.read()

def extract_schema_urls(wsdl_content):
    root = ET.fromstring(wsdl_content)
    schemas = []
    for schema in root.findall('.//{http://schemas.xmlsoap.org/wsdl/schema}import'):
        schema_location = schema.get('schemaLocation')
        if schema_location:
            schemas.append(schema_location)
    return schemas

def extract_schema_namespaces(wsdl_content):
    root = ET.fromstring(wsdl_content)
    namespaces = {}
    for schema in root.findall('.//{http://schemas.xmlsoap.org/wsdl/schema}types/{http://www.w3.org/2001/XMLSchema}schema'):
        print("schema: ", schema)
        target_namespace = schema.get('targetNamespace')
        namespaces[target_namespace] = schema
    return namespaces

def download_xsd(schema_urls):
    for url in schema_urls:
        response = requests.get(url)
        if response.status_code == 200:
            file_name = url.split('/')[-1]
            with open(file_name, 'wb') as file:
                file.write(response.content)
            print(f"Downloaded: {file_name}")
        else:
            print(f"Failed to download: {url}")

def extract_parameters(operation_name, root):
    parameters = []
    for message in root.findall('.//{http://schemas.xmlsoap.org/wsdl/}message'):
        if message.get('name') == f"{operation_name}Request":
            for part in message.findall('{http://schemas.xmlsoap.org/wsdl/}part'):
                parameters.append(part.get('name'))
    return parameters

def build_custom_soap_envelope(operation_name, parameters):
    envelope = ET.Element('soapenv:Envelope', {
        'xmlns:soapenv': 'http://schemas.xmlsoap.org/soap/envelope/',
        'xmlns:prod': 'http://ariregistri.x-road.eu/producer/'  # Adjust the namespace as needed
    })
    
    header = ET.SubElement(envelope, 'soapenv:Header')
    
    body = ET.SubElement(envelope, 'soapenv:Body')
    operation = ET.SubElement(body, f'prod:{operation_name}')  # Using operation name with prod namespace

    keha = ET.SubElement(operation, 'prod:keha')

    # Add all parameters as placeholders in the body
    for param in parameters:
        ET.SubElement(keha, f'prod:{param}').text = f'{{{{ {param} }}}}'  # Placeholder for parameters

    return ET.tostring(envelope, encoding='utf-8')

def pretty_print(xml_bytes):
    # Parse the bytes to a DOM object and convert it back to a pretty-printed string
    dom = minidom.parseString(xml_bytes)
    return dom.toprettyxml(indent="    ")

def main(wsdl_file_path):
    wsdl_content = read_wsdl(wsdl_file_path)
    
    # Extract schema URLs
    schema_urls = extract_schema_urls(wsdl_content)
    print("Schemas found:", schema_urls)

    # Download XSD files
    download_xsd(schema_urls)

    # Extract schema namespaces
    schema_namespaces = extract_schema_namespaces(wsdl_content)
    print("Schema namespaces found:", schema_namespaces)

    # Parse WSDL for operations
    root = ET.fromstring(wsdl_content)
    operations = {}
    
    for operation in root.findall('.//{http://schemas.xmlsoap.org/wsdl/}operation'):
        op_name = operation.get('name')
        input_message = operation.find('.//{http://schemas.xmlsoap.org/wsdl/}input')
        if input_message is not None:
            message_name = input_message.get('message')
            # Extract parameters from the corresponding message
            parameters = extract_parameters(message_name, root)
            operations[op_name] = parameters

    # Build SOAP envelopes for each operation in the custom format
    for op_name, params in operations.items():
        soap_envelope_bytes = build_custom_soap_envelope(op_name, params)
        pretty_soap_envelope = pretty_print(soap_envelope_bytes)
        print(f"SOAP Envelope for operation '{op_name}':\n{pretty_soap_envelope}\n")

if __name__ == "__main__":
    wsdl_file_path = 'ar_97.wsdl'  # Update the path to your local WSDL file
    main(wsdl_file_path)