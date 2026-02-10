package main

import (
	"bytes"
	"crypto/tls"
	"encoding/xml"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"time"
)

const (
	ARIREG_ENDPOINT = "https://ariregxmlv6.rik.ee/"
	SERVER_PORT     = ":1236"
)

// SOAP Envelope structures
type SOAPEnvelope struct {
	XMLName xml.Name `xml:"http://schemas.xmlsoap.org/soap/envelope/ Envelope"`
	Header  SOAPHeader
	Body    SOAPBody
}

type SOAPHeader struct {
	XMLName xml.Name `xml:"http://schemas.xmlsoap.org/soap/envelope/ Header"`
	Content []byte   `xml:",innerxml"`
}

type SOAPBody struct {
	XMLName xml.Name `xml:"http://schemas.xmlsoap.org/soap/envelope/ Body"`
	Content []byte   `xml:",innerxml"`
}

// Estonian Business Registry specific structures
type EttevottegaSeotudIsikudRequest struct {
	XMLName xml.Name `xml:"http://arireg.x-road.eu/producer/ ettevottegaSeotudIsikudV1"`
	Keha    struct {
		AriregistriKood string `xml:"ariregistri_kood"`
	} `xml:"keha"`
}

func main() {
	http.HandleFunc("/", rootHandler)
	http.HandleFunc("/soap", soapProxyHandler)
	http.HandleFunc("/wsdl", wsdlHandler)
	http.HandleFunc("/health", healthHandler)

	log.Printf("INFO\tStarting Arireg SOAP Client on %s", SERVER_PORT)
	log.Printf("INFO\tSOAP endpoint: http://localhost%s/soap", SERVER_PORT)
	log.Printf("INFO\tWSDL endpoint: http://localhost%s/wsdl", SERVER_PORT)
	log.Printf("INFO\tTarget endpoint: %s", ARIREG_ENDPOINT)
	log.Fatal(http.ListenAndServe(SERVER_PORT, nil))
}

func rootHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/plain")
	fmt.Fprintf(w, "Arireg SOAP Client is running\n")
	fmt.Fprintf(w, "POST SOAP requests to /soap endpoint\n")
	fmt.Fprintf(w, "Target: %s\n", ARIREG_ENDPOINT)
}

// replaceServiceNames transforms service names between camelCase and underscore versions
// toCamelCase: true = convert to camelCase (for responses), false = convert to underscore (for requests)
func replaceServiceNames(xmlContent string, toCamelCase bool) string {
	if toCamelCase {
		// Backend response -> Client response (underscore to camelCase)
		result := bytes.ReplaceAll([]byte(xmlContent), []byte("ettevottegaSeotudIsikud_v1"), []byte("ettevottegaSeotudIsikudV1"))
		return string(bytes.ReplaceAll(result, []byte("ettevottegaSeotudIsikud_v1Response"), []byte("ettevottegaSeotudIsikudV1Response")))
	} else {
		// Client request -> Backend request (camelCase to underscore)
		result := bytes.ReplaceAll([]byte(xmlContent), []byte("ettevottegaSeotudIsikudV1Response"), []byte("ettevottegaSeotudIsikud_v1Response"))
		return string(bytes.ReplaceAll(result, []byte("ettevottegaSeotudIsikudV1"), []byte("ettevottegaSeotudIsikud_v1")))
	}
}

func healthHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	fmt.Fprintf(w, `{"status":"healthy","target":"%s"}`, ARIREG_ENDPOINT)
}

func wsdlHandler(w http.ResponseWriter, r *http.Request) {
	log.Printf("INFO\t%s - %s %s %s (WSDL)", r.RemoteAddr, r.Proto, r.Method, r.URL.Path)
	
	wsdlContent := `<?xml version="1.0" encoding="UTF-8"?>
<definitions name="AriregProxyService"
             targetNamespace="http://arireg.x-road.eu/producer/"
             xmlns="http://schemas.xmlsoap.org/wsdl/"
             xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
             xmlns:tns="http://arireg.x-road.eu/producer/"
             xmlns:xsd="http://www.w3.org/2001/XMLSchema">

    <types>
        <xsd:schema targetNamespace="http://arireg.x-road.eu/producer/">
            <xsd:element name="ettevottegaSeotudIsikudV1">
                <xsd:complexType>
                    <xsd:sequence>
                        <xsd:element name="keha">
                            <xsd:complexType>
                                <xsd:sequence>
                                    <xsd:element name="ariregistri_kood" type="xsd:string"/>
                                </xsd:sequence>
                            </xsd:complexType>
                        </xsd:element>
                    </xsd:sequence>
                </xsd:complexType>
            </xsd:element>
            
            <xsd:element name="ettevottegaSeotudIsikudV1Response">
                <xsd:complexType>
                    <xsd:sequence>
                        <xsd:element name="keha">
                            <xsd:complexType>
                                <xsd:sequence>
                                    <xsd:any minOccurs="0" maxOccurs="unbounded" processContents="lax"/>
                                </xsd:sequence>
                            </xsd:complexType>
                        </xsd:element>
                    </xsd:sequence>
                </xsd:complexType>
            </xsd:element>
        </xsd:schema>
    </types>

    <message name="ettevottegaSeotudIsikudV1Request">
        <part name="parameters" element="tns:ettevottegaSeotudIsikudV1"/>
    </message>
    
    <message name="ettevottegaSeotudIsikudV1Response">
        <part name="parameters" element="tns:ettevottegaSeotudIsikudV1Response"/>
    </message>

    <portType name="AriregProxyPortType">
        <operation name="ettevottegaSeotudIsikudV1">
            <input message="tns:ettevottegaSeotudIsikudV1Request"/>
            <output message="tns:ettevottegaSeotudIsikudV1Response"/>
        </operation>
    </portType>

    <binding name="AriregProxyBinding" type="tns:AriregProxyPortType">
        <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>
        
        <operation name="ettevottegaSeotudIsikudV1">
            <soap:operation soapAction="ettevottegaSeotudIsikudV1"/>
            <input>
                <soap:body use="literal"/>
            </input>
            <output>
                <soap:body use="literal"/>
            </output>
        </operation>
    </binding>

    <service name="AriregProxyService">
        <documentation>Proxy service for Estonian Business Registry (Äriregister)</documentation>
        <port name="AriregProxyPort" binding="tns:AriregProxyBinding">
            <soap:address location="http://` + r.Host + `/soap"/>
        </port>
    </service>

</definitions>`
	
	w.Header().Set("Content-Type", "text/xml; charset=utf-8")
	w.Write([]byte(wsdlContent))
}

func soapProxyHandler(w http.ResponseWriter, r *http.Request) {
	// Check if WSDL is requested (handle wsdl query parameter or GET requests)
	if r.URL.Query().Has("wsdl") || r.URL.Query().Has("WSDL") || r.Method == "GET" {
		wsdlHandler(w, r)
		return
	}
	
	startTime := time.Now()
	log.Printf("INFO\t%s - %s %s %s", r.RemoteAddr, r.Proto, r.Method, r.URL.Path)

	if r.Method != "POST" {
		http.Error(w, "Only POST method is allowed for SOAP requests", http.StatusMethodNotAllowed)
		return
	}

	// Read the incoming request body
	bodyBytes, err := ioutil.ReadAll(r.Body)
	if err != nil {
		log.Printf("ERROR\tFailed to read request body: %v", err)
		http.Error(w, "Error reading request body", http.StatusBadRequest)
		return
	}
	defer r.Body.Close()

	log.Printf("INFO\tReceived SOAP request (%d bytes)", len(bodyBytes))

	// Parse to validate it's a proper SOAP envelope
	var envelope SOAPEnvelope
	err = xml.Unmarshal(bodyBytes, &envelope)
	if err != nil {
		log.Printf("ERROR\tInvalid SOAP envelope: %v", err)
		http.Error(w, "Error parsing SOAP envelope", http.StatusBadRequest)
		return
	}

	// Transform request: Replace camelCase names with underscore versions for backend
	bodyString := replaceServiceNames(string(bodyBytes), false)
	bodyBytes = []byte(bodyString)

	// Try to identify the operation
	var reqData EttevottegaSeotudIsikudRequest
	if xml.Unmarshal(envelope.Body.Content, &reqData) == nil && 
	   reqData.XMLName.Local == "ettevottegaSeotudIsikudV1" {
		log.Printf("INFO\tOperation: ettevottegaSeotudIsikudV1 for code: %s", 
			reqData.Keha.AriregistriKood)
	}

	// Log the transformed request for debugging
	log.Printf("DEBUG\tTransformed request body (%d bytes)", len(bodyBytes))

	// Create HTTP client with TLS configuration
	tr := &http.Transport{
		TLSClientConfig: &tls.Config{
			InsecureSkipVerify: false,
			MinVersion:         tls.VersionTLS12,
		},
		MaxIdleConns:          10,
		MaxIdleConnsPerHost:   5,
		IdleConnTimeout:       90 * time.Second,
		TLSHandshakeTimeout:   10 * time.Second,
		ExpectContinueTimeout: 1 * time.Second,
		ResponseHeaderTimeout: 30 * time.Second,
		DisableCompression:    false,
		DisableKeepAlives:     false,
	}
	client := &http.Client{
		Transport: tr,
		Timeout:   60 * time.Second,
	}

	// Create the forwarding request
	proxyReq, err := http.NewRequest("POST", ARIREG_ENDPOINT, bytes.NewReader(bodyBytes))
	if err != nil {
		log.Printf("ERROR\tFailed to create proxy request: %v", err)
		http.Error(w, "Error creating proxy request", http.StatusInternalServerError)
		return
	}

	// Copy headers from original request
	proxyReq.Header.Set("Content-Type", "text/xml; charset=utf-8")
	proxyReq.Header.Set("Content-Length", fmt.Sprintf("%d", len(bodyBytes)))
	proxyReq.Header.Set("Connection", "keep-alive")
	
	if soapAction := r.Header.Get("SOAPAction"); soapAction != "" {
		proxyReq.Header.Set("SOAPAction", soapAction)
		log.Printf("DEBUG\tSOAPAction: %s", soapAction)
	}
	
	if userAgent := r.Header.Get("User-Agent"); userAgent != "" {
		proxyReq.Header.Set("User-Agent", userAgent)
	} else {
		proxyReq.Header.Set("User-Agent", "arireg-soap-client/1.0")
	}

	// Copy X-Road specific headers if present
	for key, values := range r.Header {
		if len(key) > 6 && (key[:6] == "X-Road" || key[:6] == "x-road") {
			for _, value := range values {
				proxyReq.Header.Add(key, value)
				log.Printf("DEBUG\tCopied header %s: %s", key, value)
			}
		}
	}

	log.Printf("INFO\tForwarding request to %s", ARIREG_ENDPOINT)

	// Make the request
	resp, err := client.Do(proxyReq)
	if err != nil {
		log.Printf("ERROR\tFailed to forward request: %v", err)
		log.Printf("ERROR\tRequest details - URL: %s, Body size: %d bytes", ARIREG_ENDPOINT, len(bodyBytes))
		
		// Provide more specific error information
		errorMsg := fmt.Sprintf("Error forwarding request to backend: %v", err)
		if bytes.Contains([]byte(err.Error()), []byte("tls")) {
			errorMsg = "TLS/SSL connection error. Check certificate configuration."
		} else if bytes.Contains([]byte(err.Error()), []byte("timeout")) {
			errorMsg = "Backend service timeout. Service may be unavailable."
		} else if bytes.Contains([]byte(err.Error()), []byte("connection refused")) {
			errorMsg = "Backend service refused connection. Service may be down."
		}
		
		http.Error(w, errorMsg, http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()
	
	log.Printf("DEBUG\tBackend response status: %d", resp.StatusCode)

	// Read the response
	respBody, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		log.Printf("ERROR\tFailed to read response: %v", err)
		http.Error(w, "Error reading response", http.StatusInternalServerError)
		return
	}

	duration := time.Since(startTime)
	log.Printf("INFO\tReceived response from %s: HTTP %d (%d bytes) in %v", 
		ARIREG_ENDPOINT, resp.StatusCode, len(respBody), duration)

	// Transform response: Replace underscore versions with camelCase for client
	respBodyString := replaceServiceNames(string(respBody), true)
	respBody = []byte(respBodyString)

	// Copy response headers
	for key, values := range resp.Header {
		// Skip Content-Length as we'll recalculate it after transformation
		if key == "Content-Length" {
			continue
		}
		for _, value := range values {
			w.Header().Add(key, value)
		}
	}

	// Ensure Content-Type is set
	if w.Header().Get("Content-Type") == "" {
		w.Header().Set("Content-Type", "text/xml; charset=utf-8")
	}
	
	// Set correct Content-Length after transformation
	w.Header().Set("Content-Length", fmt.Sprintf("%d", len(respBody)))

	// Write response
	w.WriteHeader(resp.StatusCode)
	w.Write(respBody)

	log.Printf("INFO\tCompleted request in %v", duration)
}
